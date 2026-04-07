package com.fylnx.lelegram.protection;

import androidx.annotation.Nullable;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;

import com.fylnx.lelegram.LeleConfig;

public final class ContentProtectionHelper {

    private ContentProtectionHelper() {
    }

    public static boolean isProtectedContentActionsEnabled() {
        return LeleConfig.allowProtectedContentActions;
    }

    public static boolean isMessageProtected(@Nullable MessageObject messageObject) {
        return messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.noforwards;
    }

    public static boolean isPeerProtected(@Nullable MessagesController messagesController, long dialogId) {
        return messagesController != null && messagesController.isPeerNoForwards(dialogId);
    }

    public static boolean hasProtectedContent(boolean peerProtected, boolean messageProtected) {
        return peerProtected || messageProtected;
    }

    public static boolean hasProtectedContent(@Nullable MessagesController messagesController, long dialogId, @Nullable MessageObject messageObject) {
        return hasProtectedContent(isPeerProtected(messagesController, dialogId), isMessageProtected(messageObject));
    }

    public static boolean shouldBlockProtectedAction(boolean peerProtected, boolean messageProtected) {
        return hasProtectedContent(peerProtected, messageProtected) && !isProtectedContentActionsEnabled();
    }

    public static boolean shouldBlockProtectedAction(@Nullable MessagesController messagesController, long dialogId, @Nullable MessageObject messageObject) {
        return shouldBlockProtectedAction(isPeerProtected(messagesController, dialogId), isMessageProtected(messageObject));
    }
}
