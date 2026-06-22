package com.fylnx.lelegram.settings;

import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import com.fylnx.lelegram.LeleConfig;
import com.fylnx.lelegram.helpers.PopupHelper;

public class LeleExperimentalSettingsActivity extends BaseLeleSettingsActivity {

    private final int downloadSpeedBoostRow = rowId++;
    private final int keepFormattingRow = rowId++;
    private final int autoInlineBotRow = rowId++;
    private final int forceFontWeightFallbackRow = rowId++;
    private final int mapDriftingFixRow = rowId++;
    private final int contentRestrictionRow = rowId++;
    private final int showRPCErrorRow = rowId++;

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.Experiment)));
        if (!MessagesController.getInstance(currentAccount).getfileExperimentalParams) {
            items.add(TextSettingsCellFactory.of(downloadSpeedBoostRow, LocaleController.getString(R.string.DownloadSpeedBoost), switch (LeleConfig.downloadSpeedBoost) {
                case LeleConfig.BOOST_NONE ->
                        LocaleController.getString(R.string.DownloadSpeedBoostNone);
                case LeleConfig.BOOST_EXTREME ->
                        LocaleController.getString(R.string.DownloadSpeedBoostExtreme);
                default -> LocaleController.getString(R.string.DownloadSpeedBoostAverage);
            }).slug("downloadSpeedBoost"));
        }
        items.add(UItem.asCheck(keepFormattingRow, LocaleController.getString(R.string.TranslationKeepFormatting)).slug("keepFormatting").setChecked(LeleConfig.keepFormatting));
        items.add(UItem.asCheck(autoInlineBotRow, LocaleController.getString(R.string.AutoInlineBot), LocaleController.getString(R.string.AutoInlineBotDesc)).slug("autoInlineBot").setChecked(LeleConfig.autoInlineBot));
        items.add(UItem.asCheck(forceFontWeightFallbackRow, LocaleController.getString(R.string.ForceFontWeightFallback)).slug("forceFontWeightFallback").setChecked(LeleConfig.forceFontWeightFallback));
        items.add(UItem.asCheck(mapDriftingFixRow, LocaleController.getString(R.string.MapDriftingFix)).slug("mapDriftingFix").setChecked(LeleConfig.mapDriftingFix));
        items.add(UItem.asCheck(contentRestrictionRow, LocaleController.getString(R.string.IgnoreContentRestriction)).slug("contentRestriction").setChecked(LeleConfig.ignoreContentRestriction));
        items.add(UItem.asCheck(showRPCErrorRow, LocaleController.getString(R.string.ShowRPCError), LocaleController.formatString(R.string.ShowRPCErrorException, "FILE_REFERENCE_EXPIRED")).slug("showRPCError").setChecked(LeleConfig.showRPCError));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == mapDriftingFixRow) {
            LeleConfig.toggleMapDriftingFix();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LeleConfig.mapDriftingFix);
            }
        } else if (id == showRPCErrorRow) {
            LeleConfig.toggleShowRPCError();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LeleConfig.showRPCError);
            }
        } else if (id == downloadSpeedBoostRow) {
            ArrayList<String> arrayList = new ArrayList<>();
            ArrayList<Integer> types = new ArrayList<>();
            arrayList.add(LocaleController.getString(R.string.DownloadSpeedBoostNone));
            types.add(LeleConfig.BOOST_NONE);
            arrayList.add(LocaleController.getString(R.string.DownloadSpeedBoostAverage));
            types.add(LeleConfig.BOOST_AVERAGE);
            arrayList.add(LocaleController.getString(R.string.DownloadSpeedBoostExtreme));
            types.add(LeleConfig.BOOST_EXTREME);
            PopupHelper.show(arrayList, LocaleController.getString(R.string.DownloadSpeedBoost), types.indexOf(LeleConfig.downloadSpeedBoost), getParentActivity(), view, i -> {
                LeleConfig.setDownloadSpeedBoost(types.get(i));
                item.textValue = arrayList.get(i);
                listView.adapter.notifyItemChanged(position, PARTIAL);
            }, resourcesProvider);
        } else if (id == contentRestrictionRow) {
            LeleConfig.toggleIgnoreContentRestriction();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LeleConfig.ignoreContentRestriction);
            }
        } else if (id == autoInlineBotRow) {
            LeleConfig.toggleAutoInlineBot();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LeleConfig.autoInlineBot);
            }
        } else if (id == forceFontWeightFallbackRow) {
            LeleConfig.toggleForceFontWeightFallback();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LeleConfig.forceFontWeightFallback);
            }
            showRestartBulletin();
        } else if (id == keepFormattingRow) {
            LeleConfig.toggleKeepFormatting();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LeleConfig.keepFormatting);
            }
        }
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.NotificationsOther);
    }

    @Override
    protected String getKey() {
        return "e";
    }
}
