/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

public class StartupThemeHelper {
    private static final int AUTO_NIGHT_TYPE_NONE = 0;
    private static final int AUTO_NIGHT_TYPE_AUTOMATIC = 2;
    private static final int AUTO_NIGHT_TYPE_SYSTEM = 3;

    public static final int STARTUP_NIGHT_BACKGROUND_COLOR = 0xff1f2732;

    public static boolean shouldUseDarkStartupBackground(Context context) {
        SharedPreferences preferences = getMainConfigPreferences(context);
        return preferences != null
                && preferences.getInt("selectedAutoNightType", getDefaultAutoNightType()) == AUTO_NIGHT_TYPE_AUTOMATIC
                && preferences.getBoolean("autoNightLastSwitchToNight", false);
    }

    public static void syncApplicationNightModeForStartup() {
        syncApplicationNightModeForStartup(ApplicationLoader.applicationContext);
    }

    public static void syncApplicationNightModeForStartup(Context context) {
        if (Build.VERSION.SDK_INT < 31 || context == null) {
            return;
        }
        SharedPreferences preferences = getMainConfigPreferences(context);
        if (preferences == null) {
            return;
        }
        int autoNightType = preferences.getInt("selectedAutoNightType", getDefaultAutoNightType());
        boolean automaticNight = autoNightType == AUTO_NIGHT_TYPE_AUTOMATIC
                && preferences.getBoolean("autoNightLastSwitchToNight", false);
        int mode;
        if (autoNightType == AUTO_NIGHT_TYPE_AUTOMATIC) {
            mode = automaticNight ? UiModeManager.MODE_NIGHT_YES : UiModeManager.MODE_NIGHT_NO;
        } else if (autoNightType == AUTO_NIGHT_TYPE_SYSTEM) {
            mode = UiModeManager.MODE_NIGHT_AUTO;
        } else {
            mode = UiModeManager.MODE_NIGHT_NO;
        }
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (uiModeManager != null) {
            try {
                uiModeManager.setApplicationNightMode(mode);
            } catch (Throwable ignore) {

            }
        }
    }

    private static int getDefaultAutoNightType() {
        return Build.VERSION.SDK_INT >= 29 ? AUTO_NIGHT_TYPE_SYSTEM : AUTO_NIGHT_TYPE_NONE;
    }

    private static SharedPreferences getMainConfigPreferences(Context context) {
        return context != null ? context.getSharedPreferences("mainconfig", Context.MODE_PRIVATE) : null;
    }
}
