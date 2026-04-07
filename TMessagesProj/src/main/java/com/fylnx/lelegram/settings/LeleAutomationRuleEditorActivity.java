package com.fylnx.lelegram.settings;

import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import com.fylnx.lelegram.LeleConfig;
import com.fylnx.lelegram.automation.AutomationModels;
import com.fylnx.lelegram.automation.AutomationRuleValidator;
import com.fylnx.lelegram.helpers.PopupHelper;

public class LeleAutomationRuleEditorActivity extends BaseLeleSettingsActivity {

    private static final int MENU_DONE = 1;

    private static final int FIELD_TYPE_TEXT = 0;
    private static final int FIELD_TYPE_BOOLEAN = 1;
    private static final int FIELD_TYPE_NUMBER = 2;
    private static final int FIELD_TYPE_LIST = 3;

    private final int nameRow = rowId++;
    private final int enabledRow = rowId++;
    private final int triggerRow = rowId++;
    private final int filtersRow = rowId++;
    private final int actionsRow = rowId++;
    private final int fieldsRow = rowId++;
    private final int validateRow = rowId++;

    private final int ruleIndex;
    private AutomationModels.AutomationRule workingRule;

    public LeleAutomationRuleEditorActivity(int ruleIndex) {
        this.ruleIndex = ruleIndex;
        loadRule();
    }

    @Override
    public View createView(android.content.Context context) {
        View view = super.createView(context);
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem doneItem = menu.addItem(MENU_DONE, R.drawable.ic_ab_done);
        doneItem.setOnClickListener(v -> saveRule());
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.AutomationRuleEditor)));
        items.add(TextSettingsCellFactory.of(nameRow, LocaleController.getString(R.string.AutomationRuleName), safeName()).slug("name"));
        items.add(UItem.asCheck(enabledRow, LocaleController.getString(R.string.AutomationEnabled)).slug("enabled").setChecked(workingRule.enabled));
        items.add(TextSettingsCellFactory.of(triggerRow, LocaleController.getString(R.string.AutomationTrigger), describeTrigger(workingRule.trigger)).slug("trigger"));
        items.add(UItem.asShadow(LocaleController.getString(R.string.AutomationRuleFormatHint)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.AutomationRuleLogic)));
        items.add(TextDetailSettingsCellFactory.of(filtersRow, LocaleController.getString(R.string.AutomationFilters), summarizeFilters(workingRule.filters)).slug("filters"));
        items.add(TextDetailSettingsCellFactory.of(actionsRow, LocaleController.getString(R.string.AutomationActions), summarizeActions(workingRule.actions)).slug("actions"));
        items.add(TextDetailSettingsCellFactory.of(fieldsRow, LocaleController.getString(R.string.AutomationSupportedFields), summarizeAllowedFields()).slug("fields"));
        items.add(UItem.asButton(validateRow, LocaleController.getString(R.string.AutomationValidate)).slug("validate"));
        items.add(UItem.asShadow(LocaleController.getString(R.string.AutomationRuleValidationHint)));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == nameRow) {
            editTextValue(LocaleController.getString(R.string.AutomationRuleName), safeName(), false, value -> {
                workingRule.name = value;
                refreshEditorRows();
            });
        } else if (id == enabledRow) {
            workingRule.enabled = !workingRule.enabled;
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(workingRule.enabled);
            }
        } else if (id == triggerRow) {
            showTriggerPicker(view);
        } else if (id == filtersRow) {
            showFiltersDialog(LocaleController.getString(R.string.AutomationFilters), workingRule.filters, this::refreshEditorRows);
        } else if (id == actionsRow) {
            showActionsDialog(LocaleController.getString(R.string.AutomationActions), workingRule.actions, this::refreshEditorRows);
        } else if (id == fieldsRow) {
            showAllowedFields();
        } else if (id == validateRow) {
            showValidation();
        }
    }

    @Override
    protected String getActionBarTitle() {
        return ruleIndex >= 0 ? LocaleController.getString(R.string.AutomationRuleEditor) : LocaleController.getString(R.string.AutomationNewRule);
    }

    @Override
    protected String getKey() {
        return "automation";
    }

    private void loadRule() {
        try {
            AutomationModels.AutomationConfig config = AutomationModels.fromJson(LeleConfig.automationRulesJson);
            if (ruleIndex >= 0 && ruleIndex < config.rules.size()) {
                workingRule = AutomationModels.copyRule(config.rules.get(ruleIndex));
            } else {
                workingRule = AutomationModels.defaultRule();
            }
        } catch (Exception ignore) {
            workingRule = AutomationModels.defaultRule();
        }
        AutomationModels.ensureDefaults(workingRule);
    }

    private void saveRule() {
        try {
            AutomationRuleValidator.ValidationResult validation = AutomationRuleValidator.validateRule(workingRule);
            if (!validation.isValid()) {
                AlertsCreator.showSimpleAlert(this, LocaleController.getString(R.string.ErrorOccurred), validation.toDisplayString());
                return;
            }
            AutomationModels.AutomationConfig config;
            try {
                config = AutomationModels.fromJson(LeleConfig.automationRulesJson);
            } catch (Exception ignore) {
                config = AutomationModels.emptyConfig();
            }
            if (ruleIndex >= 0 && ruleIndex < config.rules.size()) {
                config.rules.set(ruleIndex, AutomationModels.copyRule(workingRule));
            } else {
                config.rules.add(AutomationModels.copyRule(workingRule));
            }
            AutomationRuleValidator.ValidationResult configValidation = AutomationRuleValidator.validateConfig(config);
            if (!configValidation.isValid()) {
                AlertsCreator.showSimpleAlert(this, LocaleController.getString(R.string.ErrorOccurred), configValidation.toDisplayString());
                return;
            }
            LeleConfig.setAutomationRulesJson(AutomationModels.toJson(config));
            finishFragment();
            BulletinFactory.global().createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.AutomationRuleSaved)).show();
        } catch (Exception e) {
            AlertsCreator.showSimpleAlert(this, LocaleController.getString(R.string.ErrorOccurred), String.valueOf(e.getMessage()));
        }
    }

    private void showValidation() {
        AutomationRuleValidator.ValidationResult validation = AutomationRuleValidator.validateRule(workingRule);
        if (validation.isValid()) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.AutomationRuleValid)).show();
        } else {
            AlertsCreator.showSimpleAlert(this, LocaleController.getString(R.string.ErrorOccurred), validation.toDisplayString());
        }
    }

    private void showTriggerPicker(View anchor) {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> values = new ArrayList<>();
        labels.add(LocaleController.getString(R.string.AutomationTriggerMessageReceived));
        values.add(AutomationModels.TRIGGER_MESSAGE_RECEIVED);
        labels.add(LocaleController.getString(R.string.AutomationTriggerMessageSent));
        values.add(AutomationModels.TRIGGER_MESSAGE_SENT);
        labels.add(LocaleController.getString(R.string.AutomationTriggerMessageDeleted));
        values.add(AutomationModels.TRIGGER_MESSAGE_DELETED);
        labels.add(LocaleController.getString(R.string.AutomationTriggerMemberChanged));
        values.add(AutomationModels.TRIGGER_MEMBER_CHANGED);
        PopupHelper.show(labels, LocaleController.getString(R.string.AutomationTrigger), values.indexOf(workingRule.trigger), getParentActivity(), anchor, i -> {
            workingRule.trigger = values.get(i);
            refreshEditorRows();
        }, resourcesProvider);
    }

    private void showAllowedFields() {
        ArrayList<String> labels = new ArrayList<>();
        for (String field : AutomationRuleValidator.getAllowedFields(workingRule.trigger)) {
            labels.add(describeField(field));
        }
        AlertsCreator.showSimpleAlert(this, LocaleController.getString(R.string.AutomationSupportedFields), TextUtils.join("\n", labels));
    }

    private void showFiltersDialog(String title, ArrayList<AutomationModels.AutomationFilter> filters, Runnable onChanged) {
        ArrayList<CharSequence> items = new ArrayList<>();
        if (filters != null) {
            for (int i = 0; i < filters.size(); i++) {
                items.add(describeFilter(filters.get(i)));
            }
        }
        items.add(LocaleController.getString(R.string.AutomationAddFilter));
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
        builder.setTitle(title);
        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
            if (filters != null && which < filters.size()) {
                showFilterOptions(title, filters, which, onChanged);
            } else {
                editFilter(filters, -1, onChanged);
            }
        });
        showDialog(builder.create());
    }

    private void showFilterOptions(String title, ArrayList<AutomationModels.AutomationFilter> filters, int index, Runnable onChanged) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
        builder.setTitle(describeFilter(filters.get(index)));
        builder.setItems(new CharSequence[]{
                LocaleController.getString(R.string.Edit),
                LocaleController.getString(R.string.Delete)
        }, (dialog, which) -> {
            if (which == 0) {
                editFilter(filters, index, onChanged);
            } else {
                filters.remove(index);
                onChanged.run();
            }
        });
        showDialog(builder.create());
    }

    private void editFilter(ArrayList<AutomationModels.AutomationFilter> filters, int index, Runnable onChanged) {
        AutomationModels.AutomationFilter current = index >= 0 ? filters.get(index) : null;
        ArrayList<String> fields = new ArrayList<>(AutomationRuleValidator.getAllowedFields(workingRule.trigger));
        Collections.sort(fields);
        ArrayList<String> labels = new ArrayList<>();
        for (String field : fields) {
            labels.add(describeField(field));
        }
        int selectedIndex = current == null ? -1 : fields.indexOf(current.field);
        PopupHelper.show(labels, LocaleController.getString(R.string.AutomationChooseField), selectedIndex, getParentActivity(), getFragmentView(), which -> {
            String field = fields.get(which);
            showOperatorPicker(field, current == null ? null : current.operator, operator -> {
                if (requiresValue(operator)) {
                    editFilterValue(field, operator, current == null ? null : current.value, value -> {
                        saveFilter(filters, index, field, operator, value);
                        onChanged.run();
                    });
                } else {
                    saveFilter(filters, index, field, operator, null);
                    onChanged.run();
                }
            });
        }, resourcesProvider);
    }

    private void showOperatorPicker(String field, String currentOperator, StringCallback callback) {
        if (isVisualSelectionField(field)) {
            ArrayList<String> operators = new ArrayList<>();
            ArrayList<String> labels = new ArrayList<>();
            addOperator(labels, operators, AutomationModels.OP_EQUALS);
            addOperator(labels, operators, AutomationModels.OP_NOT_EQUALS);
            PopupHelper.show(labels, LocaleController.getString(R.string.AutomationChooseOperator), operators.indexOf(currentOperator), getParentActivity(), getFragmentView(), i -> callback.onValue(operators.get(i)), resourcesProvider);
            return;
        }
        int fieldType = getFieldType(field);
        ArrayList<String> operators = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        if (fieldType == FIELD_TYPE_BOOLEAN) {
            addOperator(labels, operators, AutomationModels.OP_IS_TRUE);
            addOperator(labels, operators, AutomationModels.OP_IS_FALSE);
            addOperator(labels, operators, AutomationModels.OP_EQUALS);
            addOperator(labels, operators, AutomationModels.OP_NOT_EQUALS);
        } else if (fieldType == FIELD_TYPE_NUMBER) {
            addOperator(labels, operators, AutomationModels.OP_EQUALS);
            addOperator(labels, operators, AutomationModels.OP_NOT_EQUALS);
            addOperator(labels, operators, AutomationModels.OP_GREATER_THAN);
            addOperator(labels, operators, AutomationModels.OP_LESS_THAN);
        } else if (fieldType == FIELD_TYPE_LIST) {
            addOperator(labels, operators, AutomationModels.OP_CONTAINS);
            addOperator(labels, operators, AutomationModels.OP_NOT_CONTAINS);
            addOperator(labels, operators, AutomationModels.OP_EXISTS);
            addOperator(labels, operators, AutomationModels.OP_NOT_EXISTS);
        } else {
            addOperator(labels, operators, AutomationModels.OP_CONTAINS);
            addOperator(labels, operators, AutomationModels.OP_NOT_CONTAINS);
            addOperator(labels, operators, AutomationModels.OP_EQUALS);
            addOperator(labels, operators, AutomationModels.OP_NOT_EQUALS);
            addOperator(labels, operators, AutomationModels.OP_EXISTS);
            addOperator(labels, operators, AutomationModels.OP_NOT_EXISTS);
        }
        PopupHelper.show(labels, LocaleController.getString(R.string.AutomationChooseOperator), operators.indexOf(currentOperator), getParentActivity(), getFragmentView(), i -> callback.onValue(operators.get(i)), resourcesProvider);
    }

    private void editFilterValue(String field, String operator, Object currentValue, ValueObjectCallback callback) {
        if (isDialogSelectionField(field)) {
            chooseDialogValue(DialogsActivity.DIALOGS_TYPE_DEFAULT, currentValue, callback);
            return;
        }
        if (isChatIdSelectionField(field)) {
            chooseDialogValue(DialogsActivity.DIALOGS_TYPE_DEFAULT, currentValue, value -> {
                Long dialogId = asLongValue(value);
                if (dialogId == null) {
                    callback.onValue(value);
                } else if (DialogObject.isChatDialog(dialogId)) {
                    callback.onValue(-dialogId);
                } else {
                    callback.onValue(dialogId);
                }
            });
            return;
        }
        if (isUserSelectionField(field)) {
            chooseUserValue(callback);
            return;
        }
        int fieldType = getFieldType(field);
        if (fieldType == FIELD_TYPE_BOOLEAN) {
            ArrayList<String> labels = new ArrayList<>();
            labels.add(LocaleController.getString(R.string.AutomationValueTrue));
            labels.add(LocaleController.getString(R.string.AutomationValueFalse));
            int index = Boolean.TRUE.equals(currentValue) ? 0 : 1;
            PopupHelper.show(labels, LocaleController.getString(R.string.AutomationChooseValue), index, getParentActivity(), getFragmentView(), i -> callback.onValue(i == 0), resourcesProvider);
            return;
        }
        editTextValue(LocaleController.getString(R.string.AutomationChooseValue), currentValue == null ? "" : String.valueOf(currentValue), false, value -> {
            if (fieldType == FIELD_TYPE_NUMBER) {
                try {
                    callback.onValue(Integer.parseInt(value));
                } catch (Exception e) {
                    AlertsCreator.showSimpleAlert(this, LocaleController.getString(R.string.ErrorOccurred), LocaleController.getString(R.string.AutomationValueNumberHint));
                }
            } else {
                callback.onValue(value);
            }
        });
    }

    private void saveFilter(ArrayList<AutomationModels.AutomationFilter> filters, int index, String field, String operator, Object value) {
        AutomationModels.AutomationFilter filter = new AutomationModels.AutomationFilter();
        filter.field = field;
        filter.operator = operator;
        filter.value = value;
        if (index >= 0) {
            filters.set(index, filter);
        } else {
            filters.add(filter);
        }
    }

    private void chooseDialogValue(int dialogsType, Object currentValue, ValueObjectCallback callback) {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", dialogsType);
        DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate((dialogFragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids != null && !dids.isEmpty()) {
                MessagesStorage.TopicKey key = dids.get(0);
                callback.onValue(key.dialogId);
            }
            dialogFragment.finishFragment();
            return true;
        });
        presentFragment(fragment);
    }

    private void chooseUserValue(ValueObjectCallback callback) {
        Bundle args = new Bundle();
        args.putBoolean("destroyAfterSelect", true);
        args.putBoolean("returnAsResult", true);
        args.putBoolean("onlyUsers", true);
        args.putBoolean("needForwardCount", false);
        ContactsActivity fragment = new ContactsActivity(args);
        fragment.setDelegate((user, param, activity) -> callback.onValue(user.id));
        presentFragment(fragment);
    }

    private void showActionsDialog(String title, ArrayList<AutomationModels.AutomationAction> actions, Runnable onChanged) {
        ArrayList<CharSequence> items = new ArrayList<>();
        if (actions != null) {
            for (int i = 0; i < actions.size(); i++) {
                items.add(describeAction(actions.get(i)));
            }
        }
        items.add(LocaleController.getString(R.string.AutomationAddAction));
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
        builder.setTitle(title);
        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
            if (actions != null && which < actions.size()) {
                showActionOptions(title, actions, which, onChanged);
            } else {
                chooseActionType(actions, -1, onChanged);
            }
        });
        showDialog(builder.create());
    }

    private void showActionOptions(String title, ArrayList<AutomationModels.AutomationAction> actions, int index, Runnable onChanged) {
        AutomationModels.AutomationAction action = actions.get(index);
        ArrayList<CharSequence> items = new ArrayList<>();
        ArrayList<Runnable> callbacks = new ArrayList<>();
        if (AutomationModels.ACTION_IF.equals(action.type)) {
            items.add(LocaleController.getString(R.string.AutomationEditCondition));
            callbacks.add(() -> showFiltersDialog(LocaleController.getString(R.string.AutomationEditCondition), action.condition, onChanged));
            items.add(LocaleController.getString(R.string.AutomationEditThenActions));
            callbacks.add(() -> showActionsDialog(LocaleController.getString(R.string.AutomationEditThenActions), action.thenActions, onChanged));
            items.add(LocaleController.getString(R.string.AutomationEditElseActions));
            callbacks.add(() -> showActionsDialog(LocaleController.getString(R.string.AutomationEditElseActions), action.elseActions, onChanged));
        } else {
            items.add(LocaleController.getString(R.string.Edit));
            callbacks.add(() -> editAction(actions, index, onChanged));
        }
        if (index > 0) {
            items.add(LocaleController.getString(R.string.AutomationMoveUp));
            callbacks.add(() -> {
                Collections.swap(actions, index, index - 1);
                onChanged.run();
            });
        }
        if (index < actions.size() - 1) {
            items.add(LocaleController.getString(R.string.AutomationMoveDown));
            callbacks.add(() -> {
                Collections.swap(actions, index, index + 1);
                onChanged.run();
            });
        }
        items.add(LocaleController.getString(R.string.Delete));
        callbacks.add(() -> {
            actions.remove(index);
            onChanged.run();
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
        builder.setTitle(describeAction(action));
        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> callbacks.get(which).run());
        showDialog(builder.create());
    }

    private void chooseActionType(ArrayList<AutomationModels.AutomationAction> actions, int index, Runnable onChanged) {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> values = new ArrayList<>();
        addActionType(labels, values, AutomationModels.ACTION_SEND_MESSAGE);
        addActionType(labels, values, AutomationModels.ACTION_FORWARD_MESSAGE);
        addActionType(labels, values, AutomationModels.ACTION_DELETE_MESSAGE);
        addActionType(labels, values, AutomationModels.ACTION_ADD_LOCAL_MARK);
        addActionType(labels, values, AutomationModels.ACTION_REMOVE_LOCAL_MARK);
        addActionType(labels, values, AutomationModels.ACTION_IF);
        PopupHelper.show(labels, LocaleController.getString(R.string.AutomationAddAction), -1, getParentActivity(), getFragmentView(), which -> {
            AutomationModels.AutomationAction action = new AutomationModels.AutomationAction();
            action.type = values.get(which);
            AutomationModels.ensureDefaults(action);
            if (AutomationModels.ACTION_SEND_MESSAGE.equals(action.type)) {
                editSendMessageAction(actions, index, action, onChanged);
            } else if (AutomationModels.ACTION_FORWARD_MESSAGE.equals(action.type)) {
                editForwardAction(actions, index, action, onChanged);
            } else if (AutomationModels.ACTION_DELETE_MESSAGE.equals(action.type)) {
                saveAction(actions, index, action);
                onChanged.run();
            } else if (AutomationModels.ACTION_ADD_LOCAL_MARK.equals(action.type) || AutomationModels.ACTION_REMOVE_LOCAL_MARK.equals(action.type)) {
                editMarkAction(actions, index, action, onChanged);
            } else if (AutomationModels.ACTION_IF.equals(action.type)) {
                saveAction(actions, index, action);
                onChanged.run();
            }
        }, resourcesProvider);
    }

    private void editAction(ArrayList<AutomationModels.AutomationAction> actions, int index, Runnable onChanged) {
        AutomationModels.AutomationAction action = actions.get(index);
        if (AutomationModels.ACTION_SEND_MESSAGE.equals(action.type)) {
            editSendMessageAction(actions, index, action, onChanged);
        } else if (AutomationModels.ACTION_FORWARD_MESSAGE.equals(action.type)) {
            editForwardAction(actions, index, action, onChanged);
        } else if (AutomationModels.ACTION_DELETE_MESSAGE.equals(action.type)) {
            onChanged.run();
        } else if (AutomationModels.ACTION_ADD_LOCAL_MARK.equals(action.type) || AutomationModels.ACTION_REMOVE_LOCAL_MARK.equals(action.type)) {
            editMarkAction(actions, index, action, onChanged);
        }
    }

    private void editSendMessageAction(ArrayList<AutomationModels.AutomationAction> actions, int index, AutomationModels.AutomationAction action, Runnable onChanged) {
        chooseTarget(action.target, action.dialogId, (target, dialogId) -> editTextValue(LocaleController.getString(R.string.AutomationActionSendMessage), action.text, false, text -> {
            action.target = target;
            action.dialogId = dialogId;
            action.text = text;
            saveAction(actions, index, action);
            onChanged.run();
        }));
    }

    private void editForwardAction(ArrayList<AutomationModels.AutomationAction> actions, int index, AutomationModels.AutomationAction action, Runnable onChanged) {
        chooseTarget(action.target, action.dialogId, (target, dialogId) -> chooseBoolean(LocaleController.getString(R.string.AutomationForwardFromMyName), Boolean.TRUE.equals(action.forwardFromMyName), forwardFromMyName -> chooseBoolean(LocaleController.getString(R.string.AutomationHideCaption), Boolean.TRUE.equals(action.hideCaption), hideCaption -> {
            action.target = target;
            action.dialogId = dialogId;
            action.forwardFromMyName = forwardFromMyName;
            action.hideCaption = hideCaption;
            saveAction(actions, index, action);
            onChanged.run();
        })));
    }

    private void editMarkAction(ArrayList<AutomationModels.AutomationAction> actions, int index, AutomationModels.AutomationAction action, Runnable onChanged) {
        editTextValue(LocaleController.getString(R.string.AutomationMarkName), action.mark, false, mark -> {
            action.mark = mark;
            saveAction(actions, index, action);
            onChanged.run();
        });
    }

    private void chooseTarget(String currentTarget, Long currentDialogId, TargetCallback callback) {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> values = new ArrayList<>();
        labels.add(LocaleController.getString(R.string.AutomationTargetCurrentDialog));
        values.add(AutomationModels.TARGET_CURRENT_DIALOG);
        labels.add(LocaleController.getString(R.string.AutomationTargetSavedMessages));
        values.add(AutomationModels.TARGET_SAVED_MESSAGES);
        labels.add(LocaleController.getString(R.string.AutomationTargetDialogId));
        values.add(AutomationModels.TARGET_DIALOG_ID);
        PopupHelper.show(labels, LocaleController.getString(R.string.AutomationChooseTarget), values.indexOf(currentTarget), getParentActivity(), getFragmentView(), which -> {
            String target = values.get(which);
            if (AutomationModels.TARGET_DIALOG_ID.equals(target)) {
                chooseDialogValue(DialogsActivity.DIALOGS_TYPE_DEFAULT, currentDialogId, value -> {
                    Long dialogId = asLongValue(value);
                    if (dialogId == null) {
                        AlertsCreator.showSimpleAlert(this, LocaleController.getString(R.string.ErrorOccurred), LocaleController.getString(R.string.AutomationDialogIdHint));
                        return;
                    }
                    callback.onTarget(target, dialogId);
                });
            } else {
                callback.onTarget(target, null);
            }
        }, resourcesProvider);
    }

    private void chooseBoolean(String title, boolean currentValue, BooleanCallback callback) {
        ArrayList<String> labels = new ArrayList<>();
        labels.add(LocaleController.getString(R.string.AutomationValueTrue));
        labels.add(LocaleController.getString(R.string.AutomationValueFalse));
        PopupHelper.show(labels, title, currentValue ? 0 : 1, getParentActivity(), getFragmentView(), which -> callback.onValue(which == 0), resourcesProvider);
    }

    private void saveAction(ArrayList<AutomationModels.AutomationAction> actions, int index, AutomationModels.AutomationAction action) {
        AutomationModels.ensureDefaults(action);
        if (index >= 0) {
            actions.set(index, action);
        } else {
            actions.add(action);
        }
    }

    private void addOperator(ArrayList<String> labels, ArrayList<String> operators, String operator) {
        labels.add(describeOperator(operator));
        operators.add(operator);
    }

    private void addActionType(ArrayList<String> labels, ArrayList<String> values, String value) {
        labels.add(describeActionType(value));
        values.add(value);
    }

    private boolean requiresValue(String operator) {
        return !AutomationModels.OP_EXISTS.equals(operator)
                && !AutomationModels.OP_NOT_EXISTS.equals(operator)
                && !AutomationModels.OP_IS_TRUE.equals(operator)
                && !AutomationModels.OP_IS_FALSE.equals(operator);
    }

    private int getFieldType(String field) {
        if (field == null) {
            return FIELD_TYPE_TEXT;
        }
        if (field.endsWith("is_reply") || field.endsWith("is_service") || field.endsWith("is_self") || field.endsWith("is_bot")) {
            return FIELD_TYPE_BOOLEAN;
        }
        if (field.endsWith("count") || field.endsWith("channel_id") || field.endsWith("id")) {
            return FIELD_TYPE_NUMBER;
        }
        if (field.endsWith("local_marks")) {
            return FIELD_TYPE_LIST;
        }
        return FIELD_TYPE_TEXT;
    }

    private String summarizeFilters(ArrayList<AutomationModels.AutomationFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return LocaleController.getString(R.string.AutomationNoFilters);
        }
        ArrayList<String> parts = new ArrayList<>();
        for (int i = 0; i < filters.size(); i++) {
            parts.add(describeFilter(filters.get(i)));
            if (i == 2 && filters.size() > 3) {
                parts.add(LocaleController.formatString(R.string.AutomationMoreItems, filters.size() - 3));
                break;
            }
        }
        return TextUtils.join("\n", parts);
    }

    private String summarizeActions(ArrayList<AutomationModels.AutomationAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return LocaleController.getString(R.string.AutomationNoActions);
        }
        ArrayList<String> parts = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            parts.add(describeAction(actions.get(i)));
            if (i == 2 && actions.size() > 3) {
                parts.add(LocaleController.formatString(R.string.AutomationMoreItems, actions.size() - 3));
                break;
            }
        }
        return TextUtils.join("\n", parts);
    }

    private String summarizeAllowedFields() {
        ArrayList<String> labels = new ArrayList<>();
        int count = 0;
        for (String field : AutomationRuleValidator.getAllowedFields(workingRule.trigger)) {
            labels.add(describeField(field));
            count++;
            if (count == 4) {
                break;
            }
        }
        return TextUtils.join("\n", labels);
    }

    private String describeFilter(AutomationModels.AutomationFilter filter) {
        if (filter == null) {
            return LocaleController.getString(R.string.AutomationUnknownItem);
        }
        if ("sender.is_self".equals(filter.field) && AutomationModels.OP_IS_FALSE.equals(filter.operator)) {
            return LocaleController.getString(R.string.AutomationFilterSenderNotSelf);
        }
        if ("message.is_reply".equals(filter.field) && AutomationModels.OP_IS_TRUE.equals(filter.operator)) {
            return LocaleController.getString(R.string.AutomationFilterMessageIsReply);
        }
        if ("message.is_service".equals(filter.field) && AutomationModels.OP_IS_TRUE.equals(filter.operator)) {
            return LocaleController.getString(R.string.AutomationFilterMessageIsService);
        }
        StringBuilder builder = new StringBuilder();
        builder.append(describeField(filter.field)).append(' ').append(describeOperator(filter.operator));
        if (requiresValue(filter.operator) && filter.value != null) {
            builder.append(' ').append(describeFilterValue(filter.field, filter.value));
        }
        return builder.toString();
    }

    private String describeFilterValue(String field, Object value) {
        if (value == null) {
            return LocaleController.getString(R.string.AutomationEmptyText);
        }
        if (isDialogSelectionField(field)) {
            Long dialogId = asLongValue(value);
            if (dialogId != null) {
                return describeDialog(dialogId);
            }
        }
        if (isChatIdSelectionField(field)) {
            Long chatId = asLongValue(value);
            if (chatId != null) {
                return describeChatId(chatId);
            }
        }
        if (isUserSelectionField(field)) {
            Long userId = asLongValue(value);
            if (userId != null) {
                return describeUser(userId);
            }
        }
        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value)
                    ? LocaleController.getString(R.string.AutomationValueTrue)
                    : LocaleController.getString(R.string.AutomationValueFalse);
        }
        if (value instanceof Number) {
            double doubleValue = ((Number) value).doubleValue();
            long longValue = ((Number) value).longValue();
            if (doubleValue == longValue) {
                return String.valueOf(longValue);
            }
        }
        return String.valueOf(value);
    }

    private String describeAction(AutomationModels.AutomationAction action) {
        if (action == null || action.type == null) {
            return LocaleController.getString(R.string.AutomationUnknownItem);
        }
        if (AutomationModels.ACTION_SEND_MESSAGE.equals(action.type)) {
            return LocaleController.formatString(R.string.AutomationActionSendMessageSummary, describeTarget(action), shorten(action.text));
        } else if (AutomationModels.ACTION_FORWARD_MESSAGE.equals(action.type)) {
            return LocaleController.formatString(R.string.AutomationActionForwardMessageSummary, describeTarget(action));
        } else if (AutomationModels.ACTION_DELETE_MESSAGE.equals(action.type)) {
            return LocaleController.getString(R.string.AutomationActionDeleteMessageSummary);
        } else if (AutomationModels.ACTION_ADD_LOCAL_MARK.equals(action.type)) {
            return LocaleController.formatString(R.string.AutomationActionAddMarkSummary, action.mark == null ? "" : action.mark);
        } else if (AutomationModels.ACTION_REMOVE_LOCAL_MARK.equals(action.type)) {
            return LocaleController.formatString(R.string.AutomationActionRemoveMarkSummary, action.mark == null ? "" : action.mark);
        } else if (AutomationModels.ACTION_IF.equals(action.type)) {
            String condition = action.condition == null || action.condition.isEmpty() ? LocaleController.getString(R.string.AutomationNoFilters) : TextUtils.join(LocaleController.getString(R.string.AutomationAndSeparator), buildFilterDescriptions(action.condition));
            return LocaleController.formatString(R.string.AutomationActionIfSummary, condition, action.thenActions == null ? 0 : action.thenActions.size(), action.elseActions == null ? 0 : action.elseActions.size());
        }
        return action.type;
    }

    private ArrayList<String> buildFilterDescriptions(ArrayList<AutomationModels.AutomationFilter> filters) {
        ArrayList<String> descriptions = new ArrayList<>();
        if (filters == null) {
            return descriptions;
        }
        for (int i = 0; i < filters.size(); i++) {
            descriptions.add(describeFilter(filters.get(i)));
        }
        return descriptions;
    }

    private String describeActionType(String type) {
        if (AutomationModels.ACTION_SEND_MESSAGE.equals(type)) {
            return LocaleController.getString(R.string.AutomationActionSendMessage);
        } else if (AutomationModels.ACTION_FORWARD_MESSAGE.equals(type)) {
            return LocaleController.getString(R.string.AutomationActionForwardMessage);
        } else if (AutomationModels.ACTION_DELETE_MESSAGE.equals(type)) {
            return LocaleController.getString(R.string.AutomationActionDeleteMessage);
        } else if (AutomationModels.ACTION_ADD_LOCAL_MARK.equals(type)) {
            return LocaleController.getString(R.string.AutomationActionAddLocalMark);
        } else if (AutomationModels.ACTION_REMOVE_LOCAL_MARK.equals(type)) {
            return LocaleController.getString(R.string.AutomationActionRemoveLocalMark);
        } else if (AutomationModels.ACTION_IF.equals(type)) {
            return LocaleController.getString(R.string.AutomationActionIf);
        }
        return type;
    }

    private String describeTarget(AutomationModels.AutomationAction action) {
        if (AutomationModels.TARGET_SAVED_MESSAGES.equals(action.target)) {
            return LocaleController.getString(R.string.AutomationTargetSavedMessages);
        } else if (AutomationModels.TARGET_DIALOG_ID.equals(action.target)) {
            String chatName = action.dialogId == null ? LocaleController.getString(R.string.AutomationUnknownItem) : describeDialog(action.dialogId);
            return LocaleController.formatString(R.string.AutomationTargetDialogIdSummary, chatName);
        }
        return LocaleController.getString(R.string.AutomationTargetCurrentDialog);
    }

    private String describeField(String field) {
        if ("chat.id".equals(field)) return LocaleController.getString(R.string.AutomationFieldChatId);
        if ("chat.type".equals(field)) return LocaleController.getString(R.string.AutomationFieldChatType);
        if ("chat.title".equals(field)) return LocaleController.getString(R.string.AutomationFieldChatTitle);
        if ("message.id".equals(field)) return LocaleController.getString(R.string.AutomationFieldMessageId);
        if ("message.text".equals(field)) return LocaleController.getString(R.string.AutomationFieldMessageText);
        if ("message.is_reply".equals(field)) return LocaleController.getString(R.string.AutomationFieldMessageIsReply);
        if ("message.is_service".equals(field)) return LocaleController.getString(R.string.AutomationFieldMessageIsService);
        if ("message.local_marks".equals(field)) return LocaleController.getString(R.string.AutomationFieldMessageLocalMarks);
        if ("sender.id".equals(field)) return LocaleController.getString(R.string.AutomationFieldSenderId);
        if ("sender.name".equals(field)) return LocaleController.getString(R.string.AutomationFieldSenderName);
        if ("sender.is_self".equals(field)) return LocaleController.getString(R.string.AutomationFieldSenderIsSelf);
        if ("sender.is_bot".equals(field)) return LocaleController.getString(R.string.AutomationFieldSenderIsBot);
        if ("sender.type".equals(field)) return LocaleController.getString(R.string.AutomationFieldSenderType);
        if ("actor.id".equals(field)) return LocaleController.getString(R.string.AutomationFieldActorId);
        if ("actor.name".equals(field)) return LocaleController.getString(R.string.AutomationFieldActorName);
        if ("actor.is_self".equals(field)) return LocaleController.getString(R.string.AutomationFieldActorIsSelf);
        if ("target.id".equals(field)) return LocaleController.getString(R.string.AutomationFieldTargetId);
        if ("target.name".equals(field)) return LocaleController.getString(R.string.AutomationFieldTargetName);
        if ("target.is_self".equals(field)) return LocaleController.getString(R.string.AutomationFieldTargetIsSelf);
        if ("change.type".equals(field)) return LocaleController.getString(R.string.AutomationFieldChangeType);
        if ("deleted.count".equals(field)) return LocaleController.getString(R.string.AutomationFieldDeletedCount);
        if ("deleted.channel_id".equals(field)) return LocaleController.getString(R.string.AutomationFieldDeletedChannelId);
        return field;
    }

    private String describeOperator(String operator) {
        if (AutomationModels.OP_EQUALS.equals(operator)) return LocaleController.getString(R.string.AutomationOperatorEquals);
        if (AutomationModels.OP_NOT_EQUALS.equals(operator)) return LocaleController.getString(R.string.AutomationOperatorNotEquals);
        if (AutomationModels.OP_CONTAINS.equals(operator)) return LocaleController.getString(R.string.AutomationOperatorContains);
        if (AutomationModels.OP_NOT_CONTAINS.equals(operator)) return LocaleController.getString(R.string.AutomationOperatorNotContains);
        if (AutomationModels.OP_EXISTS.equals(operator)) return LocaleController.getString(R.string.AutomationOperatorExists);
        if (AutomationModels.OP_NOT_EXISTS.equals(operator)) return LocaleController.getString(R.string.AutomationOperatorNotExists);
        if (AutomationModels.OP_IS_TRUE.equals(operator)) return LocaleController.getString(R.string.AutomationOperatorIsTrue);
        if (AutomationModels.OP_IS_FALSE.equals(operator)) return LocaleController.getString(R.string.AutomationOperatorIsFalse);
        if (AutomationModels.OP_GREATER_THAN.equals(operator)) return LocaleController.getString(R.string.AutomationOperatorGreaterThan);
        if (AutomationModels.OP_LESS_THAN.equals(operator)) return LocaleController.getString(R.string.AutomationOperatorLessThan);
        return operator;
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

    private boolean isVisualSelectionField(String field) {
        return isDialogSelectionField(field) || isChatIdSelectionField(field) || isUserSelectionField(field);
    }

    private boolean isDialogSelectionField(String field) {
        return "chat.id".equals(field);
    }

    private boolean isChatIdSelectionField(String field) {
        return "deleted.channel_id".equals(field);
    }

    private boolean isUserSelectionField(String field) {
        return "sender.id".equals(field)
                || "actor.id".equals(field)
                || "target.id".equals(field);
    }

    private Long asLongValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }

    private String describeDialog(long dialogId) {
        if (DialogObject.isUserDialog(dialogId)) {
            if (dialogId == getUserConfig().getClientUserId()) {
                return LocaleController.getString(R.string.SavedMessages);
            }
            TLRPC.User user = getMessagesController().getUser(dialogId);
            if (user != null) {
                return UserObject.getUserName(user);
            }
        } else if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            if (chat != null && !TextUtils.isEmpty(chat.title)) {
                return chat.title;
            }
        }
        return String.valueOf(dialogId);
    }

    private String describeChatId(long chatId) {
        TLRPC.Chat chat = getMessagesController().getChat(chatId);
        if (chat != null && !TextUtils.isEmpty(chat.title)) {
            return chat.title;
        }
        return String.valueOf(chatId);
    }

    private String describeUser(long userId) {
        if (userId == getUserConfig().getClientUserId()) {
            return LocaleController.getString(R.string.SavedMessages);
        }
        TLRPC.User user = getMessagesController().getUser(userId);
        if (user != null) {
            return UserObject.getUserName(user);
        }
        return String.valueOf(userId);
    }

    private String shorten(String value) {
        if (TextUtils.isEmpty(value)) {
            return LocaleController.getString(R.string.AutomationEmptyText);
        }
        if (value.length() <= 24) {
            return value;
        }
        return value.substring(0, 24) + "…";
    }

    private String safeName() {
        return TextUtils.isEmpty(workingRule.name) ? LocaleController.getString(R.string.AutomationNewRule) : workingRule.name;
    }

    private void refreshEditorRows() {
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private void editTextValue(String title, String value, boolean multiline, ValueCallback callback) {
        android.content.Context context = getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(title);
        builder.setCustomViewOffset(0);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setText(value == null ? "" : value);
        editText.setHintText(title);
        editText.setTransformHintToHeader(true);
        editText.setHeaderHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
        editText.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        editText.setLineColors(
                Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider),
                Theme.getColor(Theme.key_text_RedRegular, resourcesProvider)
        );
        editText.setBackground(null);
        editText.setPadding(0, 0, 0, 0);
        editText.setSingleLine(!multiline);
        if (multiline) {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editText.setMinLines(8);
            editText.setImeOptions(EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        } else {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        }
        layout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 0));

        builder.setView(layout);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                callback.onValue(editText.getText() == null ? "" : editText.getText().toString());
                dialog.dismiss();
            });
        }
    }

    private interface ValueCallback {
        void onValue(String value);
    }

    private interface StringCallback {
        void onValue(String value);
    }

    private interface ValueObjectCallback {
        void onValue(Object value);
    }

    private interface TargetCallback {
        void onTarget(String target, Long dialogId);
    }

    private interface BooleanCallback {
        void onValue(boolean value);
    }
}
