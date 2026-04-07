package com.fylnx.lelegram.settings;

import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.Collections;

import com.fylnx.lelegram.LeleConfig;
import com.fylnx.lelegram.automation.AutomationModels;
import com.fylnx.lelegram.automation.AutomationRuleValidator;

public class LeleAutomationSettingsActivity extends BaseLeleSettingsActivity {

    private final int addRuleRow = rowId++;
    private AutomationModels.AutomationConfig config = AutomationModels.emptyConfig();
    private int rulesStartRow = -1;

    @Override
    public void onResume() {
        super.onResume();
        reloadConfig();
        if (listView != null) {
            listView.post(() -> {
                if (listView != null) {
                    listView.adapter.update(false);
                }
            });
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        reloadConfig();
        items.add(UItem.asHeader(LocaleController.getString(R.string.AutomationRules)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.AutomationRulesInfo)));
        items.add(UItem.asButton(addRuleRow, R.drawable.msg_edit, LocaleController.getString(R.string.AutomationNewRule)).slug("newRule"));
        items.add(UItem.asShadow(null));

        rulesStartRow = rowId;
        if (config.rules.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.AutomationNoRules)));
            return;
        }

        items.add(UItem.asHeader(LocaleController.getString(R.string.AutomationRuleList)));
        for (int i = 0; i < config.rules.size(); i++) {
            AutomationModels.AutomationRule rule = config.rules.get(i);
            items.add(TextDetailSettingsCellFactory.of(ruleRowId(i), rule.name, buildRuleSubtitle(rule)).slug("rule_" + i));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.AutomationRuleLongPressHint)));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == addRuleRow) {
            presentFragment(new LeleAutomationRuleEditorActivity(-1));
        } else if (isRuleRow(item.id)) {
            presentFragment(new LeleAutomationRuleEditorActivity(ruleIndexFromId(item.id)));
        }
    }

    @Override
    protected boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        if (!isRuleRow(item.id)) {
            return super.onItemLongClick(item, view, position, x, y);
        }
        int index = ruleIndexFromId(item.id);
        ItemOptions.makeOptions(this, view)
                .setScrimViewBackground(listView.getClipBackground(view))
                .add(R.drawable.msg_edit, LocaleController.getString(R.string.Edit), () -> presentFragment(new LeleAutomationRuleEditorActivity(index)))
                .add(R.drawable.msg_forward, LocaleController.getString(R.string.AutomationDuplicate), () -> duplicateRule(index))
                .add(R.drawable.msg_addcontact, LocaleController.getString(R.string.AutomationMoveUp), () -> moveRule(index, -1))
                .add(R.drawable.msg_addcontact, LocaleController.getString(R.string.AutomationMoveDown), () -> moveRule(index, 1))
                .add(R.drawable.msg_delete, LocaleController.getString(R.string.Delete), true, () -> deleteRule(index))
                .setMinWidth(AndroidUtilities.dp(220))
                .show();
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.AutomationRules);
    }

    @Override
    protected String getKey() {
        return "automation";
    }

    private void reloadConfig() {
        try {
            config = AutomationModels.fromJson(LeleConfig.automationRulesJson);
            AutomationRuleValidator.ValidationResult validation = AutomationRuleValidator.validateConfig(config);
            if (!validation.isValid()) {
                config = AutomationModels.emptyConfig();
            }
        } catch (Exception ignore) {
            config = AutomationModels.emptyConfig();
        }
    }

    private void saveConfig() {
        LeleConfig.setAutomationRulesJson(AutomationModels.toJson(config));
        if (listView != null) {
            listView.adapter.update(false);
        }
    }

    private String buildRuleSubtitle(AutomationModels.AutomationRule rule) {
        ArrayList<String> parts = new ArrayList<>();
        parts.add(describeTrigger(rule.trigger));
        parts.add(rule.enabled ? LocaleController.getString(R.string.AutomationStatusEnabled) : LocaleController.getString(R.string.AutomationStatusDisabled));
        parts.add((rule.filters == null ? 0 : rule.filters.size()) + " " + LocaleController.getString(R.string.AutomationFilters).toLowerCase());
        parts.add((rule.actions == null ? 0 : rule.actions.size()) + " " + LocaleController.getString(R.string.AutomationActions).toLowerCase());
        return TextUtils.join(" • ", parts);
    }

    private String describeTrigger(String trigger) {
        if (AutomationModels.TRIGGER_MESSAGE_RECEIVED.equals(trigger)) {
            return LocaleController.getString(R.string.AutomationTriggerMessageReceived);
        } else if (AutomationModels.TRIGGER_MESSAGE_SENT.equals(trigger)) {
            return LocaleController.getString(R.string.AutomationTriggerMessageSent);
        } else if (AutomationModels.TRIGGER_MESSAGE_DELETED.equals(trigger)) {
            return LocaleController.getString(R.string.AutomationTriggerMessageDeleted);
        } else if (AutomationModels.TRIGGER_MEMBER_CHANGED.equals(trigger)) {
            return LocaleController.getString(R.string.AutomationTriggerMemberChanged);
        }
        return trigger;
    }

    private boolean isRuleRow(int itemId) {
        return itemId >= rulesStartRow && itemId < rulesStartRow + config.rules.size();
    }

    private int ruleRowId(int index) {
        return rulesStartRow + index;
    }

    private int ruleIndexFromId(int itemId) {
        return itemId - rulesStartRow;
    }

    private void duplicateRule(int index) {
        if (index < 0 || index >= config.rules.size()) {
            return;
        }
        AutomationModels.AutomationRule copy = AutomationModels.copyRule(config.rules.get(index));
        copy.id = AutomationModels.newRuleId();
        copy.name = copy.name + " (" + LocaleController.getString(R.string.Copy) + ")";
        config.rules.add(index + 1, copy);
        saveConfig();
    }

    private void moveRule(int index, int delta) {
        int target = index + delta;
        if (index < 0 || index >= config.rules.size() || target < 0 || target >= config.rules.size()) {
            return;
        }
        Collections.swap(config.rules, index, target);
        saveConfig();
    }

    private void deleteRule(int index) {
        if (index < 0 || index >= config.rules.size()) {
            return;
        }
        config.rules.remove(index);
        saveConfig();
    }
}
