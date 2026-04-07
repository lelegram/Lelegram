package com.fylnx.lelegram.automation;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class AutomationSuppressionRegistry {

    private static final long ENTRY_TTL_MS = 5 * 60 * 1000L;

    private final HashMap<String, Long> sendTokens = new HashMap<>();
    private final HashMap<String, Long> pendingSentOldIds = new HashMap<>();
    private final HashMap<String, Long> suppressedNewIds = new HashMap<>();
    private final HashMap<String, Long> pendingDeleteIds = new HashMap<>();

    public synchronized String registerSendToken() {
        purgeExpiredLocked();
        String token = UUID.randomUUID().toString();
        sendTokens.put(token, expiresAt());
        return token;
    }

    public synchronized void registerForwardOldId(int account, long dialogId, int oldId) {
        purgeExpiredLocked();
        pendingSentOldIds.put(oldIdKey(account, dialogId, oldId), expiresAt());
    }

    public synchronized void registerDeleteIds(int account, long channelId, ArrayList<Integer> messageIds) {
        purgeExpiredLocked();
        if (messageIds == null) {
            return;
        }
        for (int i = 0; i < messageIds.size(); i++) {
            Integer id = messageIds.get(i);
            if (id != null) {
                pendingDeleteIds.put(messageKey(account, channelId, id), expiresAt());
            }
        }
    }

    public synchronized boolean consumeSentMessage(int account, long dialogId, int oldId, int newId, TLRPC.Message message) {
        purgeExpiredLocked();
        boolean suppressed = false;
        if (message != null && message.params != null) {
            String token = message.params.get(AutomationModels.PARAM_AUTOMATION_TOKEN);
            if (token != null && sendTokens.remove(token) != null) {
                suppressed = true;
            }
        }
        if (pendingSentOldIds.remove(oldIdKey(account, dialogId, oldId)) != null) {
            suppressed = true;
        }
        if (suppressed && message != null) {
            suppressedNewIds.put(messageKey(account, MessageObject.getChannelId(message), newId), expiresAt());
        }
        return suppressed;
    }

    public synchronized boolean isSuppressedReceivedMessage(int account, MessageObject messageObject) {
        purgeExpiredLocked();
        if (messageObject == null || messageObject.messageOwner == null) {
            return false;
        }
        if (messageObject.messageOwner.params != null) {
            String token = messageObject.messageOwner.params.get(AutomationModels.PARAM_AUTOMATION_TOKEN);
            if (token != null && sendTokens.remove(token) != null) {
                return true;
            }
        }
        return suppressedNewIds.remove(messageKey(account, messageObject.getChannelId(), messageObject.getId())) != null;
    }

    public synchronized ArrayList<Integer> stripSuppressedDeletedIds(int account, long channelId, ArrayList<Integer> messageIds) {
        purgeExpiredLocked();
        if (messageIds == null || messageIds.isEmpty()) {
            return new ArrayList<>();
        }
        ArrayList<Integer> remaining = new ArrayList<>(messageIds.size());
        for (int i = 0; i < messageIds.size(); i++) {
            Integer id = messageIds.get(i);
            if (id == null) {
                continue;
            }
            if (pendingDeleteIds.remove(messageKey(account, channelId, id)) == null) {
                remaining.add(id);
            }
        }
        return remaining;
    }

    public synchronized int getPendingSuppressionCount() {
        purgeExpiredLocked();
        return sendTokens.size() + pendingSentOldIds.size() + suppressedNewIds.size() + pendingDeleteIds.size();
    }

    private long expiresAt() {
        return System.currentTimeMillis() + ENTRY_TTL_MS;
    }

    private void purgeExpiredLocked() {
        long now = System.currentTimeMillis();
        purgeMap(sendTokens, now);
        purgeMap(pendingSentOldIds, now);
        purgeMap(suppressedNewIds, now);
        purgeMap(pendingDeleteIds, now);
    }

    private void purgeMap(HashMap<String, Long> map, long now) {
        Iterator<Map.Entry<String, Long>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() < now) {
                iterator.remove();
            }
        }
    }

    private static String oldIdKey(int account, long dialogId, int oldId) {
        return account + ":" + dialogId + ":" + oldId;
    }

    private static String messageKey(int account, long channelId, int messageId) {
        return account + ":" + channelId + ":" + messageId;
    }
}
