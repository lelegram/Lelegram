package com.fylnx.lelegram.copy;

import androidx.annotation.Nullable;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;

import com.fylnx.lelegram.protection.ContentProtectionHelper;

public final class CopyRestrictionsHelper {

    private CopyRestrictionsHelper() {
    }

    public static boolean isMessageNoForwards(@Nullable MessageObject messageObject) {
        return ContentProtectionHelper.isMessageProtected(messageObject);
    }

    public static boolean isPeerNoForwards(@Nullable MessagesController messagesController, long dialogId) {
        return ContentProtectionHelper.isPeerProtected(messagesController, dialogId);
    }

    public static boolean shouldBlockCopy(boolean peerNoForwards, boolean messageNoForwards) {
        return ContentProtectionHelper.shouldBlockProtectedAction(peerNoForwards, messageNoForwards);
    }

    public static boolean shouldBlockCopy(@Nullable MessagesController messagesController, long dialogId, @Nullable MessageObject messageObject) {
        return shouldBlockCopy(isPeerNoForwards(messagesController, dialogId), isMessageNoForwards(messageObject));
    }
}
