package com.fylnx.lelegram;

import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import com.fylnx.lelegram.settings.BaseLeleSettingsActivity;

public class MessageEditHistoryActivity extends BaseLeleSettingsActivity {

    private final MessageObject messageObject;

    public MessageEditHistoryActivity(MessageObject messageObject) {
        this.messageObject = messageObject;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        int itemId = rowId;
        ArrayList<TLRPC.MessageEditHistoryEntry> history = messageObject.messageOwner.editHistory;
        if (history != null) {
            for (int i = 0; i < history.size(); i++) {
                TLRPC.MessageEditHistoryEntry entry = history.get(i);
                items.add(TextDetailSettingsCellFactory.of(itemId++, getEntryTitle(entry.edit_date == 0, false), getEntryValue(entry.date, entry.edit_date, entry.text)));
            }
        }
        items.add(TextDetailSettingsCellFactory.of(itemId, getEntryTitle(false, true), getEntryValue(messageObject.messageOwner.date, messageObject.messageOwner.edit_date, messageObject.messageOwner.message)));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
    }

    @Override
    protected boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        if (item.viewType == UniversalAdapter.VIEW_TYPE_SHADOW || !(view instanceof TextDetailSettingsCell)) {
            return false;
        }
        AndroidUtilities.addToClipboard(((TextDetailSettingsCell) view).getValueTextView().getText());
        BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.ViewHistory);
    }

    private CharSequence getEntryTitle(boolean original, boolean current) {
        if (current) {
            return LocaleController.getString(R.string.MessageEditHistoryCurrent);
        }
        return original ? LocaleController.getString(R.string.EventLogOriginalMessages) : LocaleController.getString(R.string.MessageEditHistoryEdited);
    }

    private CharSequence getEntryValue(int date, int editDate, String text) {
        StringBuilder builder = new StringBuilder(editDate != 0 ? LocaleController.formatPmEditedDate(editDate) : LocaleController.formatPmFwdDate(date));
        builder.append("\n\n");
        builder.append(TextUtils.isEmpty(text) ? LocaleController.getString(R.string.EventLogOriginalCaptionEmpty) : text);
        return builder;
    }
}
