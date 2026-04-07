package com.fylnx.lelegram.settings;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

import com.fylnx.lelegram.LeleConfig;
import com.fylnx.lelegram.helpers.EmojiHelper;
import com.fylnx.lelegram.helpers.PopupHelper;

public class LeleAppearanceSettingsActivity extends BaseLeleSettingsActivity implements NotificationCenter.NotificationCenterDelegate {

    private final int emojiSetsRow = rowId++;
    private final int predictiveBackAnimationRow = rowId++;
    private final int appBarShadowRow = rowId++;
    private final int formatTimeWithSecondsRow = rowId++;
    private final int disableNumberRoundingRow = rowId++;
    private final int hideBottomNavigationBarRow = rowId++;
    private final int tabletModeRow = rowId++;

    private final int hideStoriesRow = rowId++;
    private final int mediaPreviewRow = rowId++;

    private final int hideAllTabRow = rowId++;
    private final int tabsTitleTypeRow = rowId++;
    private final int tabsPositionRow = rowId++;

    private final int strokeOnViewsRow = rowId++;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded && listView != null) {
            notifyItemChanged(emojiSetsRow, PARTIAL);
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.ChangeChannelNameColor2)));
        items.add(EmojiSetCellFactory.of(emojiSetsRow, LocaleController.getString(R.string.EmojiSets)).slug("emojiSets"));
        items.add(UItem.asCheck(predictiveBackAnimationRow, LocaleController.getString(R.string.PredictiveBackAnimation)).slug("predictiveBackAnimation").setChecked(LeleConfig.predictiveBackAnimation));
        items.add(UItem.asCheck(appBarShadowRow, LocaleController.getString(R.string.DisableAppBarShadow)).slug("appBarShadow").setChecked(LeleConfig.disableAppBarShadow));
        items.add(UItem.asCheck(formatTimeWithSecondsRow, LocaleController.getString(R.string.FormatWithSeconds)).slug("formatTimeWithSeconds").setChecked(LeleConfig.formatTimeWithSeconds));
        items.add(UItem.asCheck(disableNumberRoundingRow, LocaleController.getString(R.string.DisableNumberRounding), "4.8K -> 4777").slug("disableNumberRounding").setChecked(LeleConfig.disableNumberRounding));
        items.add(UItem.asCheck(hideBottomNavigationBarRow, LocaleController.getString(R.string.HideBottomNavigationBar)).setChecked(LeleConfig.hideBottomNavigationBar).slug("hideBottomNavigationBar"));
        items.add(TextSettingsCellFactory.of(tabletModeRow, LocaleController.getString(R.string.TabletMode), switch (LeleConfig.tabletMode) {
            case LeleConfig.TABLET_AUTO -> LocaleController.getString(R.string.TabletModeAuto);
            case LeleConfig.TABLET_ENABLE -> LocaleController.getString(R.string.Enable);
            default -> LocaleController.getString(R.string.Disable);
        }).slug("tabletMode"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.SavedDialogsTab)));
        items.add(UItem.asCheck(hideStoriesRow, LocaleController.getString(R.string.HideStories)).slug("hideStories").setChecked(LeleConfig.hideStories));
        items.add(UItem.asCheck(mediaPreviewRow, LocaleController.getString(R.string.MediaPreview)).slug("mediaPreview").setChecked(LeleConfig.mediaPreview));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.Filters)));
        items.add(UItem.asCheck(hideAllTabRow, LocaleController.getString(R.string.HideAllTab)).slug("hideAllTab").setChecked(LeleConfig.hideAllTab));
        items.add(TextSettingsCellFactory.of(tabsTitleTypeRow, LocaleController.getString(R.string.TabTitleType), switch (LeleConfig.tabsTitleType) {
            case LeleConfig.TITLE_TYPE_TEXT ->
                    LocaleController.getString(R.string.TabTitleTypeText);
            case LeleConfig.TITLE_TYPE_ICON ->
                    LocaleController.getString(R.string.TabTitleTypeIcon);
            default -> LocaleController.getString(R.string.TabTitleTypeMix);
        }).slug("tabsTitleType"));
        items.add(TextSettingsCellFactory.of(tabsPositionRow, LocaleController.getString(R.string.TabsPosition), LocaleController.getString(LeleConfig.bottomFilterTabs ? R.string.TabsPositionBottom : R.string.TabsPositionTop)).slug("tabsPosition"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.LiteOptionsBlur2)));
        items.add(UItem.asCheck(strokeOnViewsRow, LocaleController.getString(R.string.StrokeOnViews)).setChecked(LeleConfig.strokeOnViews).slug("strokeOnViews"));
        items.add(UItem.asShadow(null));

    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        var id = item.id;
        if (id == tabletModeRow) {
            ArrayList<String> arrayList = new ArrayList<>();
            ArrayList<Integer> types = new ArrayList<>();
            arrayList.add(LocaleController.getString(R.string.TabletModeAuto));
            types.add(LeleConfig.TABLET_AUTO);
            arrayList.add(LocaleController.getString(R.string.Enable));
            types.add(LeleConfig.TABLET_ENABLE);
            arrayList.add(LocaleController.getString(R.string.Disable));
            types.add(LeleConfig.TABLET_DISABLE);
            PopupHelper.show(arrayList, LocaleController.getString(R.string.TabletMode), types.indexOf(LeleConfig.tabletMode), getParentActivity(), view, i -> {
                LeleConfig.setTabletMode(types.get(i));
                item.textValue = arrayList.get(i);
                listView.adapter.notifyItemChanged(position, PARTIAL);
                AndroidUtilities.resetTabletFlag();
                if (getParentActivity() instanceof LaunchActivity) {
                    ((LaunchActivity) getParentActivity()).invalidateTabletMode();
                }
            }, resourcesProvider);
        } else if (id == emojiSetsRow) {
            presentFragment(new LeleEmojiSettingsActivity());
        } else if (id == disableNumberRoundingRow) {
            LeleConfig.toggleDisableNumberRounding();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LeleConfig.disableNumberRounding);
            }
        } else if (id == appBarShadowRow) {
            LeleConfig.toggleDisableAppBarShadow();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LeleConfig.disableAppBarShadow);
            }
            parentLayout.setHeaderShadow(LeleConfig.disableAppBarShadow ? null : parentLayout.getParentActivity().getDrawable(R.drawable.header_shadow).mutate());
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (id == mediaPreviewRow) {
            LeleConfig.toggleMediaPreview();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LeleConfig.mediaPreview);
            }
        } else if (id == hideStoriesRow) {
            LeleConfig.toggleHideStories();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LeleConfig.hideStories);
            }
            getNotificationCenter().postNotificationName(NotificationCenter.storiesEnabledUpdate);
        } else if (id == formatTimeWithSecondsRow) {
            LeleConfig.toggleFormatTimeWithSeconds();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LeleConfig.formatTimeWithSeconds);
            }
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (id == hideAllTabRow) {
            LeleConfig.toggleHideAllTab();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LeleConfig.hideAllTab);
            }
            getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
        } else if (id == tabsTitleTypeRow) {
            ArrayList<String> arrayList = new ArrayList<>();
            ArrayList<Integer> types = new ArrayList<>();
            arrayList.add(LocaleController.getString(R.string.TabTitleTypeText));
            types.add(LeleConfig.TITLE_TYPE_TEXT);
            arrayList.add(LocaleController.getString(R.string.TabTitleTypeIcon));
            types.add(LeleConfig.TITLE_TYPE_ICON);
            arrayList.add(LocaleController.getString(R.string.TabTitleTypeMix));
            types.add(LeleConfig.TITLE_TYPE_MIX);
            PopupHelper.show(arrayList, LocaleController.getString(R.string.TabTitleType), types.indexOf(LeleConfig.tabsTitleType), getParentActivity(), view, i -> {
                LeleConfig.setTabsTitleType(types.get(i));
                item.textValue = arrayList.get(i);
                listView.adapter.notifyItemChanged(position, PARTIAL);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
            }, resourcesProvider);
        } else if (id == predictiveBackAnimationRow) {
            LeleConfig.togglePredictiveBackAnimation();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LeleConfig.predictiveBackAnimation);
            }
            showRestartBulletin();
        } else if (id == hideBottomNavigationBarRow) {
            LeleConfig.toggleHideBottomNavigationBar();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LeleConfig.hideBottomNavigationBar);
            }
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (id == tabsPositionRow) {
            ArrayList<String> arrayList = new ArrayList<>();
            arrayList.add(LocaleController.getString(R.string.TabsPositionTop));
            arrayList.add(LocaleController.getString(R.string.TabsPositionBottom));
            PopupHelper.show(arrayList, LocaleController.getString(R.string.TabsPosition), LeleConfig.bottomFilterTabs ? 1 : 0, getParentActivity(), view, i -> {
                LeleConfig.setBottomFilterTabs(i == 1);
                item.textValue = arrayList.get(i);
                listView.adapter.notifyItemChanged(position, PARTIAL);
                parentLayout.rebuildAllFragmentViews(false, false);
            }, resourcesProvider);
        } else if (id == strokeOnViewsRow) {
            LeleConfig.toggleStrokeOnViews();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LeleConfig.strokeOnViews);
            }
        }
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.ChangeChannelNameColor2);
    }

    @Override
    protected String getKey() {
        return "a";
    }

    private static class EmojiSetCellFactory extends UItem.UItemFactory<EmojiSetCell> {
        static {
            setup(new EmojiSetCellFactory());
        }

        @Override
        public EmojiSetCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new EmojiSetCell(context, false, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            var cell = (EmojiSetCell) view;
            var pack = cell.getPack();
            var newPack = EmojiHelper.getInstance().getCurrentEmojiPackInfo();
            cell.setData(newPack, pack != null && !pack.getPackId().equals(newPack.getPackId()), divider);
        }

        public static UItem of(int id, String title) {
            var item = UItem.ofFactory(EmojiSetCellFactory.class);
            item.id = id;
            item.text = title;
            return item;
        }
    }
}
