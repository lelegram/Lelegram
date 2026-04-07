package com.fylnx.lelegram.automation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AutomationRuleValidator {

    private static final int MAX_ACTION_DEPTH = 8;

    private static final Set<String> SUPPORTED_TRIGGERS = new LinkedHashSet<>(Arrays.asList(
            AutomationModels.TRIGGER_MESSAGE_RECEIVED,
            AutomationModels.TRIGGER_MESSAGE_SENT,
            AutomationModels.TRIGGER_MESSAGE_DELETED,
            AutomationModels.TRIGGER_MEMBER_CHANGED
    ));

    private static final Set<String> SUPPORTED_OPERATORS = new LinkedHashSet<>(Arrays.asList(
            AutomationModels.OP_EQUALS,
            AutomationModels.OP_NOT_EQUALS,
            AutomationModels.OP_CONTAINS,
            AutomationModels.OP_NOT_CONTAINS,
            AutomationModels.OP_EXISTS,
            AutomationModels.OP_NOT_EXISTS,
            AutomationModels.OP_IS_TRUE,
            AutomationModels.OP_IS_FALSE,
            AutomationModels.OP_GREATER_THAN,
            AutomationModels.OP_LESS_THAN
    ));

    private static final Set<String> SUPPORTED_ACTIONS = new LinkedHashSet<>(Arrays.asList(
            AutomationModels.ACTION_SEND_MESSAGE,
            AutomationModels.ACTION_FORWARD_MESSAGE,
            AutomationModels.ACTION_DELETE_MESSAGE,
            AutomationModels.ACTION_ADD_LOCAL_MARK,
            AutomationModels.ACTION_REMOVE_LOCAL_MARK,
            AutomationModels.ACTION_IF
    ));

    private static final Set<String> SUPPORTED_TARGETS = new LinkedHashSet<>(Arrays.asList(
            AutomationModels.TARGET_CURRENT_DIALOG,
            AutomationModels.TARGET_SAVED_MESSAGES,
            AutomationModels.TARGET_DIALOG_ID
    ));

    private static final Map<String, Set<String>> ALLOWED_FIELDS_BY_TRIGGER = new LinkedHashMap<>();

    static {
        ALLOWED_FIELDS_BY_TRIGGER.put(AutomationModels.TRIGGER_MESSAGE_RECEIVED, linkedSet(
                "chat.id", "chat.type", "chat.title",
                "message.id", "message.text", "message.is_reply", "message.is_service", "message.local_marks",
                "sender.id", "sender.name", "sender.is_self", "sender.is_bot", "sender.type"
        ));
        ALLOWED_FIELDS_BY_TRIGGER.put(AutomationModels.TRIGGER_MESSAGE_SENT, linkedSet(
                "chat.id", "chat.type", "chat.title",
                "message.id", "message.text", "message.is_reply", "message.is_service", "message.local_marks",
                "sender.id", "sender.name", "sender.is_self", "sender.is_bot", "sender.type"
        ));
        ALLOWED_FIELDS_BY_TRIGGER.put(AutomationModels.TRIGGER_MEMBER_CHANGED, linkedSet(
                "chat.id", "chat.type", "chat.title",
                "message.id", "message.text", "message.is_service", "message.local_marks",
                "actor.id", "actor.name", "actor.is_self",
                "target.id", "target.name", "target.is_self",
                "change.type"
        ));
        ALLOWED_FIELDS_BY_TRIGGER.put(AutomationModels.TRIGGER_MESSAGE_DELETED, linkedSet(
                "deleted.count", "deleted.channel_id"
        ));
    }

    private AutomationRuleValidator() {
    }

    public static final class ValidationError {
        public final String path;
        public final String message;

        public ValidationError(String path, String message) {
            this.path = path;
            this.message = message;
        }
    }

    public static final class ValidationResult {
        public final ArrayList<ValidationError> errors = new ArrayList<>();

        public boolean isValid() {
            return errors.isEmpty();
        }

        public void add(String path, String message) {
            errors.add(new ValidationError(path, message));
        }

        public String toDisplayString() {
            if (errors.isEmpty()) {
                return "OK";
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < errors.size(); i++) {
                ValidationError error = errors.get(i);
                if (i > 0) {
                    builder.append('\n');
                }
                builder.append("- ").append(error.path).append(": ").append(error.message);
            }
            return builder.toString();
        }
    }

    public static ValidationResult validateConfig(AutomationModels.AutomationConfig config) {
        ValidationResult result = new ValidationResult();
        if (config == null) {
            result.add("config", "Config is missing");
            return result;
        }
        if (config.version != AutomationModels.SCHEMA_VERSION) {
            result.add("config.version", "Unsupported schema version");
        }
        if (config.rules == null) {
            result.add("config.rules", "Rules list is missing");
            return result;
        }
        for (int i = 0; i < config.rules.size(); i++) {
            validateRuleInto(config.rules.get(i), i, result);
        }
        return result;
    }

    public static ValidationResult validateRule(AutomationModels.AutomationRule rule) {
        ValidationResult result = new ValidationResult();
        validateRuleInto(rule, 0, result);
        return result;
    }

    public static Set<String> getAllowedFields(String trigger) {
        Set<String> fields = ALLOWED_FIELDS_BY_TRIGGER.get(trigger);
        if (fields == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(fields);
    }

    public static boolean supportsTrigger(String trigger) {
        return SUPPORTED_TRIGGERS.contains(trigger);
    }

    private static void validateRuleInto(AutomationModels.AutomationRule rule, int index, ValidationResult result) {
        String path = "rules[" + index + "]";
        if (rule == null) {
            result.add(path, "Rule is missing");
            return;
        }
        AutomationModels.ensureDefaults(rule);
        if (rule.name == null || rule.name.trim().isEmpty()) {
            result.add(path + ".name", "Rule name is required");
        }
        if (!SUPPORTED_TRIGGERS.contains(rule.trigger)) {
            result.add(path + ".trigger", "Unsupported trigger");
        }
        validateFilters(rule.trigger, rule.filters, path + ".filters", result);
        if (rule.actions == null || rule.actions.isEmpty()) {
            result.add(path + ".actions", "At least one action is required");
        } else {
            validateActions(rule.trigger, rule.actions, path + ".actions", 1, result);
        }
    }

    private static void validateFilters(String trigger, List<AutomationModels.AutomationFilter> filters, String path, ValidationResult result) {
        if (filters == null) {
            return;
        }
        Set<String> allowedFields = ALLOWED_FIELDS_BY_TRIGGER.get(trigger);
        for (int i = 0; i < filters.size(); i++) {
            AutomationModels.AutomationFilter filter = filters.get(i);
            String filterPath = path + "[" + i + "]";
            if (filter == null) {
                result.add(filterPath, "Filter is missing");
                continue;
            }
            if (filter.field == null || filter.field.trim().isEmpty()) {
                result.add(filterPath + ".field", "Field is required");
            } else if (allowedFields == null || !allowedFields.contains(filter.field)) {
                result.add(filterPath + ".field", "Field is not available for trigger " + trigger);
            }
            if (filter.operator == null || !SUPPORTED_OPERATORS.contains(filter.operator)) {
                result.add(filterPath + ".operator", "Unsupported operator");
            }
            if (!AutomationModels.OP_EXISTS.equals(filter.operator)
                    && !AutomationModels.OP_NOT_EXISTS.equals(filter.operator)
                    && !AutomationModels.OP_IS_TRUE.equals(filter.operator)
                    && !AutomationModels.OP_IS_FALSE.equals(filter.operator)
                    && filter.value == null) {
                result.add(filterPath + ".value", "Value is required");
            }
        }
    }

    private static void validateActions(String trigger, List<AutomationModels.AutomationAction> actions, String path, int depth, ValidationResult result) {
        if (depth > MAX_ACTION_DEPTH) {
            result.add(path, "Action tree is too deep");
            return;
        }
        for (int i = 0; i < actions.size(); i++) {
            AutomationModels.AutomationAction action = actions.get(i);
            String actionPath = path + "[" + i + "]";
            if (action == null) {
                result.add(actionPath, "Action is missing");
                continue;
            }
            AutomationModels.ensureDefaults(action);
            if (action.type == null || !SUPPORTED_ACTIONS.contains(action.type)) {
                result.add(actionPath + ".type", "Unsupported action type");
                continue;
            }
            if (AutomationModels.ACTION_IF.equals(action.type)) {
                if (action.condition == null || action.condition.isEmpty()) {
                    result.add(actionPath + ".condition", "If action requires a condition");
                } else {
                    validateFilters(trigger, action.condition, actionPath + ".condition", result);
                }
                if (action.thenActions == null || action.thenActions.isEmpty()) {
                    result.add(actionPath + ".thenActions", "If action requires at least one then action");
                } else {
                    validateActions(trigger, action.thenActions, actionPath + ".thenActions", depth + 1, result);
                }
                if (action.elseActions != null && !action.elseActions.isEmpty()) {
                    validateActions(trigger, action.elseActions, actionPath + ".elseActions", depth + 1, result);
                }
                continue;
            }

            if (requiresMessageContext(action.type) && AutomationModels.TRIGGER_MESSAGE_DELETED.equals(trigger)) {
                result.add(actionPath, "Selected trigger does not provide a message context for this action");
            }

            if (AutomationModels.ACTION_SEND_MESSAGE.equals(action.type)) {
                if (action.text == null || action.text.trim().isEmpty()) {
                    result.add(actionPath + ".text", "Send message action requires text");
                }
                validateTarget(action, actionPath, result);
            } else if (AutomationModels.ACTION_FORWARD_MESSAGE.equals(action.type)) {
                validateTarget(action, actionPath, result);
            } else if (AutomationModels.ACTION_ADD_LOCAL_MARK.equals(action.type)
                    || AutomationModels.ACTION_REMOVE_LOCAL_MARK.equals(action.type)) {
                if (action.mark == null || action.mark.trim().isEmpty()) {
                    result.add(actionPath + ".mark", "Local mark action requires a mark name");
                }
            }
        }
    }

    private static void validateTarget(AutomationModels.AutomationAction action, String actionPath, ValidationResult result) {
        if (action.target == null || !SUPPORTED_TARGETS.contains(action.target)) {
            result.add(actionPath + ".target", "Unsupported action target");
            return;
        }
        if (AutomationModels.TARGET_DIALOG_ID.equals(action.target) && action.dialogId == null) {
            result.add(actionPath + ".dialogId", "dialogId is required when target is dialog_id");
        }
    }

    private static boolean requiresMessageContext(String actionType) {
        return AutomationModels.ACTION_FORWARD_MESSAGE.equals(actionType)
                || AutomationModels.ACTION_DELETE_MESSAGE.equals(actionType)
                || AutomationModels.ACTION_ADD_LOCAL_MARK.equals(actionType)
                || AutomationModels.ACTION_REMOVE_LOCAL_MARK.equals(actionType);
    }

    private static Set<String> linkedSet(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
