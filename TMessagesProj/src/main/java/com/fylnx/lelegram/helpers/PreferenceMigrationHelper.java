package com.fylnx.lelegram.helpers;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PreferenceMigrationHelper {

    private PreferenceMigrationHelper() {
    }

    public static SharedPreferences migrate(String targetName, String legacyName, int mode) {
        SharedPreferences target = ApplicationLoader.applicationContext.getSharedPreferences(targetName, mode);
        SharedPreferences legacy = ApplicationLoader.applicationContext.getSharedPreferences(legacyName, mode);
        if (!target.getAll().isEmpty() || legacy.getAll().isEmpty()) {
            return target;
        }

        SharedPreferences.Editor editor = target.edit();
        for (Map.Entry<String, ?> entry : legacy.getAll().entrySet()) {
            putValue(editor, entry.getKey(), entry.getValue());
        }
        editor.apply();
        return target;
    }

    private static void putValue(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof String stringValue) {
            editor.putString(key, stringValue);
        } else if (value instanceof Boolean booleanValue) {
            editor.putBoolean(key, booleanValue);
        } else if (value instanceof Integer intValue) {
            editor.putInt(key, intValue);
        } else if (value instanceof Long longValue) {
            editor.putLong(key, longValue);
        } else if (value instanceof Float floatValue) {
            editor.putFloat(key, floatValue);
        } else if (value instanceof Set<?> setValue) {
            HashSet<String> strings = new HashSet<>();
            for (Object item : setValue) {
                if (item instanceof String stringItem) {
                    strings.add(stringItem);
                }
            }
            editor.putStringSet(key, strings);
        } else if (value != null) {
            FileLog.e("Unsupported preference type for " + key + ": " + value.getClass().getName());
        }
    }
}
