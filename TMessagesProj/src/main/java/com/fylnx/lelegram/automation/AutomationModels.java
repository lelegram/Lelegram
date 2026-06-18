package com.fylnx.lelegram.automation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.UUID;

public final class AutomationModels {

    public static final int SCHEMA_VERSION = 1;

    public static final String TRIGGER_MESSAGE_RECEIVED = "message_received";
    public static final String TRIGGER_MESSAGE_SENT = "message_sent";
    public static final String TRIGGER_MESSAGE_DELETED = "message_deleted";
    public static final String TRIGGER_MEMBER_CHANGED = "member_changed";

    public static final String CHANGE_JOINED = "joined";
    public static final String CHANGE_LEFT = "left";
    public static final String CHANGE_KICKED = "kicked";
    public static final String CHANGE_JOINED_BY_LINK = "joined_by_link";
    public static final String CHANGE_JOINED_BY_REQUEST = "joined_by_request";

    public static final String OP_EQUALS = "equals";
    public static final String OP_NOT_EQUALS = "not_equals";
    public static final String OP_CONTAINS = "contains";
    public static final String OP_NOT_CONTAINS = "not_contains";
    public static final String OP_EXISTS = "exists";
    public static final String OP_NOT_EXISTS = "not_exists";
    public static final String OP_IS_TRUE = "is_true";
    public static final String OP_IS_FALSE = "is_false";
    public static final String OP_GREATER_THAN = "greater_than";
    public static final String OP_LESS_THAN = "less_than";

    public static final String ACTION_SEND_MESSAGE = "send_message";
    public static final String ACTION_FORWARD_MESSAGE = "forward_message";
    public static final String ACTION_DELETE_MESSAGE = "delete_message";
    public static final String ACTION_ADD_LOCAL_MARK = "add_local_mark";
    public static final String ACTION_REMOVE_LOCAL_MARK = "remove_local_mark";
    public static final String ACTION_IF = "if";

    public static final String TARGET_CURRENT_DIALOG = "current_dialog";
    public static final String TARGET_SAVED_MESSAGES = "saved_messages";
    public static final String TARGET_DIALOG_ID = "dialog_id";

    public static final String PARAM_AUTOMATION_TOKEN = "lele_automation_token";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AutomationModels() {
    }

    public static final class AutomationConfig {
        @SerializedName("version")
        public int version = SCHEMA_VERSION;
        @SerializedName("rules")
        public ArrayList<AutomationRule> rules = new ArrayList<>();
    }

    public static final class AutomationRule {
        @SerializedName("id")
        public String id;
        @SerializedName("name")
        public String name;
        @SerializedName("enabled")
        public boolean enabled = true;
        @SerializedName("trigger")
        public String trigger = TRIGGER_MESSAGE_RECEIVED;
        @SerializedName("filters")
        public ArrayList<AutomationFilter> filters = new ArrayList<>();
        @SerializedName("actions")
        public ArrayList<AutomationAction> actions = new ArrayList<>();
    }

    public static final class AutomationFilter {
        @SerializedName("field")
        public String field;
        @SerializedName("operator")
        public String operator = OP_EQUALS;
        @SerializedName("value")
        public Object value;
    }

    public static final class AutomationAction {
        @SerializedName("type")
        public String type;
        @SerializedName("text")
        public String text;
        @SerializedName("target")
        public String target = TARGET_CURRENT_DIALOG;
        @SerializedName("dialogId")
        public Long dialogId;
        @SerializedName("mark")
        public String mark;
        @SerializedName("forwardFromMyName")
        public Boolean forwardFromMyName;
        @SerializedName("hideCaption")
        public Boolean hideCaption;
        @SerializedName("condition")
        public ArrayList<AutomationFilter> condition = new ArrayList<>();
        @SerializedName("thenActions")
        public ArrayList<AutomationAction> thenActions = new ArrayList<>();
        @SerializedName("elseActions")
        public ArrayList<AutomationAction> elseActions = new ArrayList<>();
    }

    public static AutomationConfig emptyConfig() {
        return new AutomationConfig();
    }

    public static AutomationRule defaultRule() {
        AutomationRule rule = new AutomationRule();
        rule.id = newRuleId();
        rule.name = "New Rule";
        return rule;
    }

    public static String newRuleId() {
        return UUID.randomUUID().toString();
    }

    public static AutomationConfig ensureDefaults(AutomationConfig config) {
        if (config == null) {
            config = emptyConfig();
        }
        if (config.version == 0) {
            config.version = SCHEMA_VERSION;
        }
        if (config.rules == null) {
            config.rules = new ArrayList<>();
        }
        for (int i = config.rules.size() - 1; i >= 0; i--) {
            AutomationRule rule = config.rules.get(i);
            if (rule == null) {
                config.rules.remove(i);
            } else {
                config.rules.set(i, ensureDefaults(rule));
            }
        }
        return config;
    }

    public static AutomationRule ensureDefaults(AutomationRule rule) {
        if (rule == null) {
            rule = defaultRule();
        }
        if (rule.id == null || rule.id.trim().isEmpty()) {
            rule.id = newRuleId();
        }
        if (rule.name == null || rule.name.trim().isEmpty()) {
            rule.name = "Rule";
        }
        if (rule.trigger == null || rule.trigger.trim().isEmpty()) {
            rule.trigger = TRIGGER_MESSAGE_RECEIVED;
        }
        if (rule.filters == null) {
            rule.filters = new ArrayList<>();
        }
        for (int i = rule.filters.size() - 1; i >= 0; i--) {
            if (rule.filters.get(i) == null) {
                rule.filters.remove(i);
            }
        }
        if (rule.actions == null) {
            rule.actions = new ArrayList<>();
        }
        for (int i = rule.actions.size() - 1; i >= 0; i--) {
            AutomationAction action = rule.actions.get(i);
            if (action == null) {
                rule.actions.remove(i);
            } else {
                rule.actions.set(i, ensureDefaults(action));
            }
        }
        return rule;
    }

    public static AutomationAction ensureDefaults(AutomationAction action) {
        if (action == null) {
            action = new AutomationAction();
        }
        if (action.target == null || action.target.trim().isEmpty()) {
            action.target = TARGET_CURRENT_DIALOG;
        }
        if (action.condition == null) {
            action.condition = new ArrayList<>();
        }
        for (int i = action.condition.size() - 1; i >= 0; i--) {
            if (action.condition.get(i) == null) {
                action.condition.remove(i);
            }
        }
        if (action.thenActions == null) {
            action.thenActions = new ArrayList<>();
        }
        for (int i = action.thenActions.size() - 1; i >= 0; i--) {
            AutomationAction nestedAction = action.thenActions.get(i);
            if (nestedAction == null) {
                action.thenActions.remove(i);
            } else {
                action.thenActions.set(i, ensureDefaults(nestedAction));
            }
        }
        if (action.elseActions == null) {
            action.elseActions = new ArrayList<>();
        }
        for (int i = action.elseActions.size() - 1; i >= 0; i--) {
            AutomationAction nestedAction = action.elseActions.get(i);
            if (nestedAction == null) {
                action.elseActions.remove(i);
            } else {
                action.elseActions.set(i, ensureDefaults(nestedAction));
            }
        }
        return action;
    }

    public static AutomationConfig fromJson(String json) throws JsonSyntaxException {
        if (json == null || json.trim().isEmpty()) {
            return emptyConfig();
        }
        return ensureDefaults(GSON.fromJson(json, AutomationConfig.class));
    }

    public static String toJson(AutomationConfig config) {
        return GSON.toJson(ensureDefaults(config));
    }

    public static String toPrettyJson(Object value) {
        if (value == null) {
            return "[]";
        }
        return GSON.toJson(value);
    }

    public static <T> T fromJson(String json, Class<T> clazz) throws JsonSyntaxException {
        return GSON.fromJson(json, clazz);
    }

    public static ArrayList<AutomationFilter> parseFilters(String json) throws JsonSyntaxException {
        Type type = new TypeToken<ArrayList<AutomationFilter>>() {
        }.getType();
        ArrayList<AutomationFilter> filters = GSON.fromJson(json, type);
        return filters == null ? new ArrayList<>() : filters;
    }

    public static ArrayList<AutomationAction> parseActions(String json) throws JsonSyntaxException {
        Type type = new TypeToken<ArrayList<AutomationAction>>() {
        }.getType();
        ArrayList<AutomationAction> actions = GSON.fromJson(json, type);
        if (actions == null) {
            return new ArrayList<>();
        }
        for (AutomationAction action : actions) {
            ensureDefaults(action);
        }
        return actions;
    }

    public static AutomationRule copyRule(AutomationRule rule) {
        AutomationRule copy = fromJson(GSON.toJson(rule), AutomationRule.class);
        return ensureDefaults(copy);
    }
}
