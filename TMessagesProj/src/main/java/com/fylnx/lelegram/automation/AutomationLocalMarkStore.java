package com.fylnx.lelegram.automation;

import android.app.Activity;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AutomationLocalMarkStore {

    private static final String PREFS_NAME = "lele_automation_marks";

    private AutomationLocalMarkStore() {
    }

    public static Set<String> getMarks(int account, long channelId, int messageId) {
        Set<String> marks = prefs().getStringSet(key(account, channelId, messageId), null);
        if (marks == null || marks.isEmpty()) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(marks);
    }

    public static boolean hasMark(int account, long channelId, int messageId, String mark) {
        return getMarks(account, channelId, messageId).contains(mark);
    }

    public static void addMark(int account, long channelId, int messageId, String mark) {
        if (mark == null || mark.trim().isEmpty()) {
            return;
        }
        LinkedHashSet<String> marks = new LinkedHashSet<>(getMarks(account, channelId, messageId));
        marks.add(mark.trim());
        prefs().edit().putStringSet(key(account, channelId, messageId), marks).apply();
    }

    public static void removeMark(int account, long channelId, int messageId, String mark) {
        if (mark == null || mark.trim().isEmpty()) {
            return;
        }
        LinkedHashSet<String> marks = new LinkedHashSet<>(getMarks(account, channelId, messageId));
        if (!marks.remove(mark.trim())) {
            return;
        }
        SharedPreferences.Editor editor = prefs().edit();
        String key = key(account, channelId, messageId);
        if (marks.isEmpty()) {
            editor.remove(key);
        } else {
            editor.putStringSet(key, marks);
        }
        editor.apply();
    }

    public static void cleanupDeletedMessages(int account, long channelId, ArrayList<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }
        SharedPreferences.Editor editor = prefs().edit();
        for (int i = 0; i < messageIds.size(); i++) {
            Integer messageId = messageIds.get(i);
            if (messageId != null) {
                editor.remove(key(account, channelId, messageId));
            }
        }
        editor.apply();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
    }

    private static String key(int account, long channelId, int messageId) {
        return account + ":" + channelId + ":" + messageId;
    }
}
