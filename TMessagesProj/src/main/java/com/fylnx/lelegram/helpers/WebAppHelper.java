package com.fylnx.lelegram.helpers;

import android.app.Activity;
import android.content.SharedPreferences;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.bots.WebViewRequestProps;
import org.telegram.ui.web.BotWebViewContainer;

import java.util.function.Consumer;

import com.fylnx.lelegram.LeleConfig;

public class WebAppHelper {
    public static final int INTERNAL_BOT_TLV = 1;

    public static boolean isInternalBot(WebViewRequestProps props) {
        return props.internalType > 0;
    }

    public static String getInternalBotName(WebViewRequestProps props) {
        switch (props.internalType) {
            case INTERNAL_BOT_TLV:
                return LocaleController.getString(R.string.ViewAsJson);
            default:
                return "";
        }
    }

    public static void openTLViewer(BaseFragment fragment, TLObject object) {
    }

    public static class CleanSerializedData extends SerializedData {
        public CleanSerializedData(int size) {
            super(size);
        }
    }

    private static JsonObject warpInEvent(String event, JsonObject data) {
        var callback = new JsonObject();
        callback.addProperty("event", event);
        if (data != null) {
            callback.add("data", data);
        }
        return callback;
    }

    public static void processBotEvents(BotWebViewContainer.Delegate delegate, String eventData, Consumer<String> eventCallback) {
        var element = JsonParser.parseString(eventData);
        if (!element.isJsonObject()) {
            return;
        }
        var eventObject = element.getAsJsonObject();
        if (!eventObject.has("event")) {
            return;
        }
        var event = eventObject.get("event").getAsString();
        if (event.equals("get_config")) {
            var data = new JsonObject();
            data.addProperty("trust", !LeleConfig.shouldNOTTrustMe);
            eventCallback.accept(warpInEvent("config", data).toString());
        } else if (event.equals("set_config")) {
            var data = eventObject.get("data").getAsJsonObject();
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("leleconfig", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            switch (data.get("key").getAsString()) {
                case "trust":
                    LeleConfig.shouldNOTTrustMe = !data.get("value").getAsBoolean();
                    editor.putBoolean("shouldNOTTrustMe", LeleConfig.shouldNOTTrustMe);
                    break;
            }
            editor.apply();
        } else if (delegate != null && event.equals("copy_text")) {
            var data = eventObject.get("data").getAsJsonObject();
            var json = data.get("text").getAsString();
            delegate.onGetTextToCopy(json);
        }
    }
}
