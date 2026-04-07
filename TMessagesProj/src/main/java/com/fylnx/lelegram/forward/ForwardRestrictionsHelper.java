package com.fylnx.lelegram.forward;

import androidx.annotation.Nullable;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;

import com.fylnx.lelegram.protection.ContentProtectionHelper;

public final class ForwardRestrictionsHelper {

    private ForwardRestrictionsHelper() {
    }

    public static boolean isMessageNoForwards(@Nullable MessageObject messageObject) {
        return ContentProtectionHelper.isMessageProtected(messageObject);
    }

    public static boolean isPeerNoForwards(@Nullable MessagesController messagesController, long dialogId) {
        return ContentProtectionHelper.isPeerProtected(messagesController, dialogId);
    }

    public static boolean hasNoForwardsRestriction(@Nullable MessagesController messagesController, long dialogId, @Nullable MessageObject messageObject) {
        return ContentProtectionHelper.hasProtectedContent(messagesController, dialogId, messageObject);
    }

    public static boolean shouldBlockForward(boolean peerNoForwards, boolean messageNoForwards) {
        return ContentProtectionHelper.shouldBlockProtectedAction(peerNoForwards, messageNoForwards);
    }

    public static boolean shouldBlockForward(@Nullable MessagesController messagesController, long dialogId, @Nullable MessageObject messageObject) {
        return shouldBlockForward(isPeerNoForwards(messagesController, dialogId), isMessageNoForwards(messageObject));
    }

    public static boolean shouldUseCopiedForward(@Nullable MessageObject messageObject) {
        return ContentProtectionHelper.isProtectedContentActionsEnabled()
                && messageObject != null
                && hasNoForwardsRestriction(MessagesController.getInstance(messageObject.currentAccount), messageObject.getDialogId(), messageObject);
    }
}
