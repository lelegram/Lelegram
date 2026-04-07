package com.fylnx.lelegram.automation;

import com.fylnx.lelegram.LeleConfig;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AutomationManager implements NotificationCenter.NotificationCenterDelegate {

    private static final AutomationManager INSTANCE = new AutomationManager();

    private final AutomationSuppressionRegistry suppressionRegistry = new AutomationSuppressionRegistry();
    private AutomationModels.AutomationConfig config = AutomationModels.emptyConfig();
    private boolean started;

    private AutomationManager() {
    }

    public static AutomationManager getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        reloadRules();
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            NotificationCenter center = NotificationCenter.getInstance(account);
            center.addObserver(this, NotificationCenter.didReceiveNewMessages);
            center.addObserver(this, NotificationCenter.messageReceivedByServer2);
            center.addObserver(this, NotificationCenter.messagesDeleted);
        }
    }

    public synchronized void reloadRules() {
        try {
            AutomationModels.AutomationConfig parsedConfig = AutomationModels.fromJson(LeleConfig.automationRulesJson);
            AutomationRuleValidator.ValidationResult validation = AutomationRuleValidator.validateConfig(parsedConfig);
            if (!validation.isValid()) {
                FileLog.d("Automation config invalid, fallback to empty: " + validation.toDisplayString());
                config = AutomationModels.emptyConfig();
            } else {
                config = parsedConfig;
            }
        } catch (Exception e) {
            FileLog.e(e);
            config = AutomationModels.emptyConfig();
        }
    }

    public synchronized String getValidationReport() {
        return AutomationRuleValidator.validateConfig(config).toDisplayString();
    }

    public int getPendingSuppressionCount() {
        return suppressionRegistry.getPendingSuppressionCount();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        try {
            if (id == NotificationCenter.didReceiveNewMessages) {
                boolean scheduled = (Boolean) args[2];
                if (!scheduled) {
                    handleIncomingMessages(account, (ArrayList<MessageObject>) args[1]);
                }
            } else if (id == NotificationCenter.messageReceivedByServer2) {
                boolean scheduled = (Boolean) args[6];
                if (!scheduled) {
                    handleSentConfirmed(account, args);
                }
            } else if (id == NotificationCenter.messagesDeleted) {
                boolean scheduled = (Boolean) args[2];
                if (!scheduled) {
                    handleDeletedMessages(account, (Long) args[1], (ArrayList<Integer>) args[0]);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void handleIncomingMessages(int account, ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (messageObject == null || messageObject.messageOwner == null) {
                continue;
            }
            ArrayList<AutomationEvent> memberEvents = buildMemberChangedEvents(account, messageObject);
            if (!memberEvents.isEmpty()) {
                for (int j = 0; j < memberEvents.size(); j++) {
                    dispatchEvent(memberEvents.get(j));
                }
                continue;
            }
            if (messageObject.isOutOwner()) {
                continue;
            }
            if (suppressionRegistry.isSuppressedReceivedMessage(account, messageObject)) {
                continue;
            }
            dispatchEvent(buildMessageEvent(AutomationModels.TRIGGER_MESSAGE_RECEIVED, account, messageObject));
        }
    }

    private void handleSentConfirmed(int account, Object... args) {
        int oldId = (Integer) args[0];
        int newId = (Integer) args[1];
        TLRPC.Message message = (TLRPC.Message) args[2];
        long dialogId = (Long) args[3];
        if (suppressionRegistry.consumeSentMessage(account, dialogId, oldId, newId, message)) {
            return;
        }
        if (message == null) {
            return;
        }
        dispatchEvent(buildMessageEvent(AutomationModels.TRIGGER_MESSAGE_SENT, account, new MessageObject(account, message, false, false)));
    }

    private void handleDeletedMessages(int account, long channelId, ArrayList<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }
        ArrayList<Integer> remainingIds = suppressionRegistry.stripSuppressedDeletedIds(account, channelId, messageIds);
        AutomationLocalMarkStore.cleanupDeletedMessages(account, channelId, messageIds);
        if (!remainingIds.isEmpty()) {
            dispatchEvent(buildDeletedEvent(account, channelId, remainingIds));
        }
    }

    private void dispatchEvent(AutomationEvent event) {
        if (event == null || config.rules == null || config.rules.isEmpty()) {
            return;
        }
        for (int i = 0; i < config.rules.size(); i++) {
            AutomationModels.AutomationRule rule = config.rules.get(i);
            if (rule == null || !rule.enabled || !event.trigger.equals(rule.trigger)) {
                continue;
            }
            if (!evaluateFilters(rule.filters, event)) {
                continue;
            }
            executeActions(event, rule.actions, 1);
        }
    }

    private void executeActions(AutomationEvent event, ArrayList<AutomationModels.AutomationAction> actions, int depth) {
        if (actions == null || actions.isEmpty() || depth > 8) {
            return;
        }
        for (int i = 0; i < actions.size(); i++) {
            AutomationModels.AutomationAction action = actions.get(i);
            if (action == null || action.type == null) {
                continue;
            }
            if (AutomationModels.ACTION_IF.equals(action.type)) {
                executeActions(event, evaluateFilters(action.condition, event) ? action.thenActions : action.elseActions, depth + 1);
            } else if (AutomationModels.ACTION_SEND_MESSAGE.equals(action.type)) {
                executeSendMessage(event, action);
            } else if (AutomationModels.ACTION_FORWARD_MESSAGE.equals(action.type)) {
                executeForwardMessage(event, action);
            } else if (AutomationModels.ACTION_DELETE_MESSAGE.equals(action.type)) {
                executeDeleteMessage(event);
            } else if (AutomationModels.ACTION_ADD_LOCAL_MARK.equals(action.type)) {
                executeMarkAction(event, action.mark, true);
            } else if (AutomationModels.ACTION_REMOVE_LOCAL_MARK.equals(action.type)) {
                executeMarkAction(event, action.mark, false);
            }
        }
    }

    private void executeSendMessage(AutomationEvent event, AutomationModels.AutomationAction action) {
        long targetDialogId = resolveTargetDialogId(event, action);
        if (targetDialogId == 0 || action.text == null || action.text.trim().isEmpty()) {
            return;
        }
        HashMap<String, String> params = new HashMap<>();
        params.put(AutomationModels.PARAM_AUTOMATION_TOKEN, suppressionRegistry.registerSendToken());
        SendMessagesHelper.getInstance(event.account).sendMessage(SendMessagesHelper.SendMessageParams.of(
                action.text,
                targetDialogId,
                null,
                null,
                null,
                true,
                null,
                null,
                params,
                true,
                0,
                0,
                null,
                false
        ));
    }

    private void executeForwardMessage(AutomationEvent event, AutomationModels.AutomationAction action) {
        if (event.messageObject == null) {
            return;
        }
        long targetDialogId = resolveTargetDialogId(event, action);
        if (targetDialogId == 0) {
            return;
        }
        ArrayList<MessageObject> messages = new ArrayList<>(1);
        messages.add(event.messageObject);
        int oldId = SendMessagesHelper.getInstance(event.account).sendMessage(
                messages,
                targetDialogId,
                Boolean.TRUE.equals(action.forwardFromMyName),
                Boolean.TRUE.equals(action.hideCaption),
                true,
                0,
                0L
        );
        if (oldId != 0) {
            suppressionRegistry.registerForwardOldId(event.account, targetDialogId, oldId);
        }
    }

    private void executeDeleteMessage(AutomationEvent event) {
        if (event.messageObject == null) {
            return;
        }
        ArrayList<Integer> ids = new ArrayList<>(1);
        ids.add(event.messageObject.getId());
        suppressionRegistry.registerDeleteIds(event.account, event.messageObject.getChannelId(), ids);
        MessagesController.getInstance(event.account).deleteMessages(ids, null, null, event.messageObject.getDialogId(), (int) event.messageObject.getTopicId(), false, 0);
    }

    private void executeMarkAction(AutomationEvent event, String mark, boolean add) {
        if (event.messageObject == null || mark == null || mark.trim().isEmpty()) {
            return;
        }
        if (add) {
            AutomationLocalMarkStore.addMark(event.account, event.messageObject.getChannelId(), event.messageObject.getId(), mark);
        } else {
            AutomationLocalMarkStore.removeMark(event.account, event.messageObject.getChannelId(), event.messageObject.getId(), mark);
        }
    }

    private long resolveTargetDialogId(AutomationEvent event, AutomationModels.AutomationAction action) {
        if (AutomationModels.TARGET_SAVED_MESSAGES.equals(action.target)) {
            return UserConfig.getInstance(event.account).getClientUserId();
        }
        if (AutomationModels.TARGET_DIALOG_ID.equals(action.target)) {
            return action.dialogId == null ? 0 : action.dialogId;
        }
        return event.dialogId;
    }

    private boolean evaluateFilters(ArrayList<AutomationModels.AutomationFilter> filters, AutomationEvent event) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (int i = 0; i < filters.size(); i++) {
            if (!evaluateFilter(filters.get(i), event)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateFilter(AutomationModels.AutomationFilter filter, AutomationEvent event) {
        if (filter == null || filter.field == null || filter.operator == null) {
            return false;
        }
        Object fieldValue = resolveField(event.context, filter.field);
        switch (filter.operator) {
            case AutomationModels.OP_EXISTS:
                return fieldValue != null;
            case AutomationModels.OP_NOT_EXISTS:
                return fieldValue == null;
            case AutomationModels.OP_IS_TRUE:
                return asBoolean(fieldValue);
            case AutomationModels.OP_IS_FALSE:
                return !asBoolean(fieldValue);
            case AutomationModels.OP_EQUALS:
                return equalsValue(fieldValue, filter.value);
            case AutomationModels.OP_NOT_EQUALS:
                return !equalsValue(fieldValue, filter.value);
            case AutomationModels.OP_CONTAINS:
                return containsValue(fieldValue, filter.value);
            case AutomationModels.OP_NOT_CONTAINS:
                return !containsValue(fieldValue, filter.value);
            case AutomationModels.OP_GREATER_THAN:
                return compareNumbers(fieldValue, filter.value) > 0;
            case AutomationModels.OP_LESS_THAN:
                return compareNumbers(fieldValue, filter.value) < 0;
            default:
                return false;
        }
    }

    private Object resolveField(Map<String, Object> context, String fieldPath) {
        Object current = context;
        String[] parts = fieldPath.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(parts[i]);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private boolean equalsValue(Object fieldValue, Object expectedValue) {
        if (fieldValue == null || expectedValue == null) {
            return fieldValue == expectedValue;
        }
        if (fieldValue instanceof Number || expectedValue instanceof Number) {
            return compareNumbers(fieldValue, expectedValue) == 0;
        }
        if (fieldValue instanceof Boolean || expectedValue instanceof Boolean) {
            return asBoolean(fieldValue) == asBoolean(expectedValue);
        }
        return String.valueOf(fieldValue).equals(String.valueOf(expectedValue));
    }

    private boolean containsValue(Object fieldValue, Object expectedValue) {
        if (fieldValue == null || expectedValue == null) {
            return false;
        }
        if (fieldValue instanceof CharSequence) {
            return String.valueOf(fieldValue).toLowerCase(Locale.US).contains(String.valueOf(expectedValue).toLowerCase(Locale.US));
        }
        if (fieldValue instanceof Collection) {
            for (Object value : (Collection<?>) fieldValue) {
                if (value != null && String.valueOf(value).equalsIgnoreCase(String.valueOf(expectedValue))) {
                    return true;
                }
            }
        }
        return false;
    }

    private int compareNumbers(Object left, Object right) {
        Double leftValue = asDouble(left);
        Double rightValue = asDouble(right);
        if (leftValue == null || rightValue == null) {
            return -1;
        }
        return Double.compare(leftValue, rightValue);
    }

    private Double asDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private AutomationEvent buildMessageEvent(String trigger, int account, MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return null;
        }
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("chat", buildChatMap(account, messageObject.getDialogId()));
        context.put("message", buildMessageMap(account, messageObject));
        context.put("sender", buildPeerMap(account, DialogObject.getPeerDialogId(messageObject.messageOwner.from_id)));
        return new AutomationEvent(trigger, account, messageObject.getDialogId(), messageObject, context);
    }

    private AutomationEvent buildDeletedEvent(int account, long channelId, ArrayList<Integer> messageIds) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        LinkedHashMap<String, Object> deleted = new LinkedHashMap<>();
        deleted.put("count", messageIds.size());
        deleted.put("channel_id", channelId);
        context.put("deleted", deleted);
        return new AutomationEvent(AutomationModels.TRIGGER_MESSAGE_DELETED, account, 0L, null, context);
    }

    private ArrayList<AutomationEvent> buildMemberChangedEvents(int account, MessageObject messageObject) {
        ArrayList<AutomationEvent> events = new ArrayList<>();
        if (messageObject.messageOwner == null || messageObject.messageOwner.action == null) {
            return events;
        }
        TLRPC.Message message = messageObject.messageOwner;
        long actorId = DialogObject.getPeerDialogId(message.from_id);
        if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
            long targetId = message.action.user_id;
            events.add(buildMemberChangedEvent(account, messageObject, actorId == targetId ? AutomationModels.CHANGE_LEFT : AutomationModels.CHANGE_KICKED, actorId, targetId));
        } else if (message.action instanceof TLRPC.TL_messageActionChatAddUser) {
            for (int i = 0; i < message.action.users.size(); i++) {
                Long targetId = message.action.users.get(i);
                if (targetId != null) {
                    events.add(buildMemberChangedEvent(account, messageObject, AutomationModels.CHANGE_JOINED, actorId, targetId));
                }
            }
        } else if (message.action instanceof TLRPC.TL_messageActionChatJoinedByLink) {
            events.add(buildMemberChangedEvent(account, messageObject, AutomationModels.CHANGE_JOINED_BY_LINK, actorId, actorId));
        } else if (message.action instanceof TLRPC.TL_messageActionChatJoinedByRequest) {
            events.add(buildMemberChangedEvent(account, messageObject, AutomationModels.CHANGE_JOINED_BY_REQUEST, actorId, actorId));
        }
        return events;
    }

    private AutomationEvent buildMemberChangedEvent(int account, MessageObject messageObject, String changeType, long actorId, long targetId) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("chat", buildChatMap(account, messageObject.getDialogId()));
        context.put("message", buildMessageMap(account, messageObject));
        context.put("actor", buildPeerMap(account, actorId));
        context.put("target", buildPeerMap(account, targetId));
        LinkedHashMap<String, Object> change = new LinkedHashMap<>();
        change.put("type", changeType);
        context.put("change", change);
        return new AutomationEvent(AutomationModels.TRIGGER_MEMBER_CHANGED, account, messageObject.getDialogId(), messageObject, context);
    }

    private LinkedHashMap<String, Object> buildChatMap(int account, long dialogId) {
        LinkedHashMap<String, Object> chat = new LinkedHashMap<>();
        chat.put("id", dialogId);
        chat.put("type", resolveChatType(account, dialogId));
        chat.put("title", resolveDialogTitle(account, dialogId));
        return chat;
    }

    private LinkedHashMap<String, Object> buildMessageMap(int account, MessageObject messageObject) {
        LinkedHashMap<String, Object> message = new LinkedHashMap<>();
        message.put("id", messageObject.getId());
        message.put("text", messageObject.messageText == null ? "" : messageObject.messageText.toString());
        message.put("is_reply", messageObject.isReply());
        message.put("is_service", messageObject.messageOwner.action != null);
        Set<String> marks = AutomationLocalMarkStore.getMarks(account, messageObject.getChannelId(), messageObject.getId());
        message.put("local_marks", new ArrayList<>(marks));
        return message;
    }

    private LinkedHashMap<String, Object> buildPeerMap(int account, long dialogId) {
        LinkedHashMap<String, Object> peer = new LinkedHashMap<>();
        peer.put("id", dialogId);
        peer.put("name", resolveDialogTitle(account, dialogId));
        peer.put("is_self", dialogId == UserConfig.getInstance(account).getClientUserId());
        peer.put("is_bot", isBot(account, dialogId));
        peer.put("type", resolvePeerType(account, dialogId));
        return peer;
    }

    private String resolveDialogTitle(int account, long dialogId) {
        if (dialogId == 0) {
            return "";
        }
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
            return user == null ? "" : UserObject.getUserName(user);
        }
        if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
            return chat == null ? "" : chat.title;
        }
        return "";
    }

    private String resolveChatType(int account, long dialogId) {
        if (DialogObject.isEncryptedDialog(dialogId)) {
            return "secret";
        }
        if (DialogObject.isUserDialog(dialogId)) {
            return "private";
        }
        if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
            if (chat != null && ChatObject.isChannel(chat) && !ChatObject.isMegagroup(chat)) {
                return "channel";
            }
            return "group";
        }
        return "unknown";
    }

    private String resolvePeerType(int account, long dialogId) {
        if (DialogObject.isUserDialog(dialogId)) {
            return "user";
        }
        if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
            if (chat != null && ChatObject.isChannel(chat) && !ChatObject.isMegagroup(chat)) {
                return "channel";
            }
            return "chat";
        }
        return "unknown";
    }

    private boolean isBot(int account, long dialogId) {
        if (!DialogObject.isUserDialog(dialogId)) {
            return false;
        }
        TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
        return user != null && user.bot;
    }

    private static final class AutomationEvent {
        final String trigger;
        final int account;
        final long dialogId;
        final MessageObject messageObject;
        final LinkedHashMap<String, Object> context;

        AutomationEvent(String trigger, int account, long dialogId, MessageObject messageObject, LinkedHashMap<String, Object> context) {
            this.trigger = trigger;
            this.account = account;
            this.dialogId = dialogId;
            this.messageObject = messageObject;
            this.context = context;
        }
    }
}
