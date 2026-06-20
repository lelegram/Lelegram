package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fylnx.lelegram.helpers.MessageHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class LeleRecallMessagesActivity extends BaseFragment implements DialogsActivity.DialogsActivityDelegate {

    private static final int ACTION_COPY = 0;
    private static final int ACTION_FORWARD = 1;
    private static final int ACTION_SAVE_TO_DOWNLOADS = 2;

    private final ArrayList<MessageObject> messages = new ArrayList<>();
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private MessageObject pendingForwardMessage;

    public LeleRecallMessagesActivity(ArrayList<MessageObject> recalledMessages) {
        if (recalledMessages != null) {
            for (int i = 0; i < recalledMessages.size(); i++) {
                MessageObject message = createDisplayMessage(recalledMessages.get(i));
                if (message != null) {
                    messages.add(message);
                }
            }
            Collections.sort(messages, Comparator
                    .comparingInt((MessageObject message) -> message.messageOwner != null ? message.messageOwner.date : 0)
                    .thenComparingInt(MessageObject::getId));
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.RecalledMessagesTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new WallpaperFrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setClipToPadding(false);
        listView.setPadding(0, dp(8), 0, dp(12));
        listView.setVerticalScrollBarEnabled(false);
        listView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        layoutManager.setStackFromEnd(true);
        listView.setAdapter(new MessagesAdapter(context));
        listView.setOnItemLongClickListener((view, position, x, y) -> {
            if (position < 0 || position >= messages.size()) {
                return false;
            }
            MessageObject message = view instanceof ChatMessageCell ? ((ChatMessageCell) view).getMessageObject() : null;
            showMessageActions(message != null ? message : messages.get(position));
            return true;
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.post(() -> {
            if (!messages.isEmpty()) {
                listView.scrollToPosition(messages.size() - 1);
            }
        });
        return fragmentView;
    }

    private MessageObject createDisplayMessage(MessageObject source) {
        if (source == null || source.messageOwner == null) {
            return null;
        }
        TLRPC.Message messageOwner = cloneMessageOwner(source.messageOwner);
        if (messageOwner == null) {
            messageOwner = createFallbackMessageOwner(source.messageOwner);
        }
        messageOwner.deleted = false;
        if (hasUnavailableMedia(source, messageOwner) && TextUtils.isEmpty(messageOwner.message)) {
            messageOwner.message = LocaleController.getString(R.string.RecalledMediaUnavailable);
        }
        MessageObject displayMessage = new MessageObject(currentAccount, messageOwner, true, true);
        displayMessage.deleted = false;
        displayMessage.messageOwner.deleted = false;
        displayMessage.replyMessageObject = source.replyMessageObject;
        return displayMessage;
    }

    private TLRPC.Message cloneMessageOwner(TLRPC.Message source) {
        NativeByteBuffer buffer = null;
        try {
            buffer = new NativeByteBuffer(source.getObjectSize());
            source.serializeToStream(buffer);
            buffer.position(0);
            return TLRPC.Message.TLdeserialize(buffer, buffer.readInt32(false), false);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        } finally {
            if (buffer != null) {
                buffer.reuse();
            }
        }
    }

    private TLRPC.Message createFallbackMessageOwner(TLRPC.Message source) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = source.id;
        message.realId = source.realId;
        message.local_id = source.local_id;
        message.dialog_id = source.dialog_id;
        message.peer_id = source.peer_id;
        message.from_id = source.from_id;
        message.saved_peer_id = source.saved_peer_id;
        message.date = source.date;
        message.flags = source.flags;
        message.flags2 = source.flags2;
        message.message = source.message;
        message.media = source.media;
        message.entities = source.entities;
        message.attachPath = source.attachPath;
        message.reply_to = source.reply_to;
        message.replyMessage = source.replyMessage;
        message.reply_markup = source.reply_markup;
        message.fwd_from = source.fwd_from;
        message.grouped_id = source.grouped_id;
        message.out = source.out;
        message.unread = source.unread;
        message.mentioned = source.mentioned;
        message.media_unread = source.media_unread;
        message.silent = source.silent;
        message.post = source.post;
        message.from_scheduled = source.from_scheduled;
        message.edit_date = source.edit_date;
        message.edit_hide = source.edit_hide;
        message.noforwards = source.noforwards;
        message.invert_media = source.invert_media;
        message.ttl_period = source.ttl_period;
        message.reactions = source.reactions;
        message.restriction_reason = source.restriction_reason;
        message.params = source.params;
        return message;
    }

    private boolean hasUnavailableMedia(MessageObject source, TLRPC.Message messageOwner) {
        TLRPC.MessageMedia media = MessageObject.getMedia(messageOwner);
        return media != null
                && !(media instanceof TLRPC.TL_messageMediaEmpty)
                && !(media instanceof TLRPC.TL_messageMediaWebPage)
                && !MessageHelper.isRecallMediaOpenable(source);
    }

    private boolean canPinToPrevious(MessageObject previous, MessageObject current) {
        return previous != null
                && current != null
                && previous.isOutOwner() == current.isOutOwner()
                && previous.getFromChatId() == current.getFromChatId()
                && Math.abs(previous.messageOwner.date - current.messageOwner.date) <= 5 * 60;
    }

    private void openPhotoViewerForMessage(MessageObject message) {
        if (message == null || !MessageHelper.isRecallMediaOpenable(message)) {
            return;
        }
        PhotoViewer.getInstance().setParentActivity(this, getResourceProvider());
        ArrayList<MessageObject> mediaMessages = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            MessageObject item = messages.get(i);
            if (MessageHelper.isRecallMediaOpenable(item)) {
                mediaMessages.add(item);
            }
        }
        int index = mediaMessages.indexOf(message);
        if (index < 0) {
            return;
        }
        long dialogId = message.type != 0 ? message.getDialogId() : 0;
        PhotoViewer.getInstance().openPhoto(mediaMessages, index, dialogId, 0, 0, new PhotoViewer.EmptyPhotoViewerProvider());
    }

    private class MessagesAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        private MessagesAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ChatMessageCell cell = new ChatMessageCell(context, currentAccount, false, null, getResourceProvider());
            cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                @Override
                public boolean canPerformActions() {
                    return true;
                }

                @Override
                public void didPressImage(ChatMessageCell cell, float x, float y, boolean fullPreview) {
                    openPhotoViewerForMessage(cell.getMessageObject());
                }

                @Override
                public void didLongPress(ChatMessageCell cell, float x, float y) {
                    showMessageActions(cell.getMessageObject());
                }

                @Override
                public boolean needPlayMessage(ChatMessageCell cell, MessageObject messageObject, boolean muted) {
                    if (messageObject == null) {
                        return false;
                    }
                    if (messageObject.isVoice() || messageObject.isRoundVideo()) {
                        boolean result = MediaController.getInstance().playMessage(messageObject, muted);
                        MediaController.getInstance().setVoiceMessagesPlaylist(null, false);
                        return result;
                    } else if (messageObject.isMusic()) {
                        return MediaController.getInstance().setPlaylist(messages, messageObject, 0);
                    }
                    return false;
                }
            });
            cell.setFullyDraw(true);
            cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MessageObject message = messages.get(position);
            ChatMessageCell cell = (ChatMessageCell) holder.itemView;
            MessageObject previous = position > 0 ? messages.get(position - 1) : null;
            MessageObject next = position < messages.size() - 1 ? messages.get(position + 1) : null;
            cell.isChat = message.getDialogId() < 0;
            cell.setFullyDraw(true);
            cell.setMessageObject(message, null, canPinToPrevious(next, message), canPinToPrevious(previous, message), position == 0, position == messages.size() - 1);
            cell.setHighlighted(false);
        }
    }

    private void showMessageActions(MessageObject message) {
        if (message == null || message.messageOwner == null || getParentActivity() == null) {
            return;
        }
        ArrayList<CharSequence> items = new ArrayList<>();
        ArrayList<Integer> icons = new ArrayList<>();
        ArrayList<Integer> actions = new ArrayList<>();

        if (canCopyMessage(message)) {
            items.add(LocaleController.getString(R.string.Copy));
            icons.add(R.drawable.msg_copy);
            actions.add(ACTION_COPY);
        }
        items.add(LocaleController.getString(R.string.Forward));
        icons.add(R.drawable.msg_forward);
        actions.add(ACTION_FORWARD);

        if (canSaveToDownloads(message)) {
            items.add(LocaleController.getString(R.string.SaveToDownloads));
            icons.add(R.drawable.msg_download);
            actions.add(ACTION_SAVE_TO_DOWNLOADS);
        }
        if (items.isEmpty()) {
            return;
        }

        BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity(), false, getResourceProvider());
        builder.setItems(items.toArray(new CharSequence[0]), toIntArray(icons), (dialog, which) -> {
            dialog.dismiss();
            if (which < 0 || which >= actions.size()) {
                return;
            }
            int action = actions.get(which);
            if (action == ACTION_COPY) {
                copyMessage(message);
            } else if (action == ACTION_FORWARD) {
                forwardMessage(message);
            } else if (action == ACTION_SAVE_TO_DOWNLOADS) {
                saveMessageToDownloads(message);
            }
        });
        showDialog(builder.create());
    }

    private int[] toIntArray(ArrayList<Integer> items) {
        int[] result = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            result[i] = items.get(i);
        }
        return result;
    }

    private boolean canCopyMessage(MessageObject message) {
        return !TextUtils.isEmpty(getCopyText(message));
    }

    private CharSequence getCopyText(MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return null;
        }
        if (message.isDice()) {
            return message.getDiceEmoji();
        }
        if (message.richLayout != null && !TextUtils.isEmpty(message.richLayout.joinedText)) {
            return message.richLayout.joinedText;
        }
        CharSequence caption = ChatActivity.getMessageCaption(message, null, null);
        if (!TextUtils.isEmpty(caption)) {
            return caption;
        }
        CharSequence content = ChatActivity.getMessageContent(message, 0, false);
        return TextUtils.isEmpty(content) ? null : content;
    }

    private void copyMessage(MessageObject message) {
        CharSequence text = getCopyText(message);
        if (TextUtils.isEmpty(text)) {
            return;
        }
        AndroidUtilities.addToClipboard(text.toString());
        BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied), getResourceProvider()).show();
    }

    private void forwardMessage(MessageObject message) {
        if (message == null || message.messageOwner == null || getParentActivity() == null) {
            return;
        }
        pendingForwardMessage = message;
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putInt("messagesCount", 1);
        args.putInt("hasPoll", message.isTodo() ? 3 : message.isPoll() ? (message.isPublicPoll() ? 2 : 1) : 0);
        args.putBoolean("hasInvoice", message.isInvoice());
        args.putBoolean("canSelectTopics", true);
        DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate(this);
        presentFragment(fragment);
    }

    @Override
    public boolean didSelectDialogs(DialogsActivity fragment, ArrayList<MessagesStorage.TopicKey> dids, CharSequence message, boolean param, boolean notify, int scheduleDate, int scheduleRepeatPeriod, TopicsFragment topicsFragment) {
        if (pendingForwardMessage == null || dids == null || dids.isEmpty()) {
            return false;
        }
        MessageObject messageToForward = pendingForwardMessage;
        for (int i = 0; i < dids.size(); i++) {
            long dialogId = dids.get(i).dialogId;
            TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            if (chat != null) {
                int sendError = org.telegram.messenger.SendMessagesHelper.canSendMessageToChat(chat, messageToForward);
                if (sendError != 0) {
                    AlertsCreator.showSendMediaAlert(sendError, fragment, getResourceProvider());
                    return false;
                }
            }
        }
        for (int i = 0; i < dids.size(); i++) {
            long dialogId = dids.get(i).dialogId;
            getSendMessagesHelper().processForwardFromMyName(messageToForward, dialogId, 0, 0, null);
        }
        pendingForwardMessage = null;
        fragment.finishFragment();
        showForwardedBulletin(dids);
        return true;
    }

    private void showForwardedBulletin(ArrayList<MessagesStorage.TopicKey> dids) {
        if (dids == null || dids.isEmpty() || getParentActivity() == null) {
            return;
        }
        if (dids.size() == 1) {
            long dialogId = dids.get(0).dialogId;
            if (BulletinFactory.of(this).showForwardedBulletinWithTag(dialogId, 1)) {
                return;
            }
            CharSequence text;
            if (dialogId == getUserConfig().getClientUserId()) {
                text = LocaleController.getString(R.string.FwdMessageToSavedMessages);
            } else if (DialogObject.isChatDialog(dialogId)) {
                TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                text = LocaleController.formatString("FwdMessageToGroup", R.string.FwdMessageToGroup, chat != null ? chat.title : "");
            } else {
                TLRPC.User user = getMessagesController().getUser(dialogId);
                text = LocaleController.formatString("FwdMessageToUser", R.string.FwdMessageToUser, user != null ? UserObject.getFirstName(user) : "");
            }
            BulletinFactory.of(this).createSimpleBulletin(R.raw.forward, AndroidUtilities.replaceTags(text)).show();
        } else {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.forward, AndroidUtilities.replaceTags(LocaleController.formatPluralString("FwdMessageToManyChats", dids.size(), dids.size()))).show();
        }
    }

    private boolean canSaveToDownloads(MessageObject message) {
        return message != null
                && !message.isVoiceOnce()
                && !message.isRoundOnce()
                && (message.isDocument()
                || message.isMusic()
                || message.isPhoto()
                || message.isVideo()
                || message.isGif()
                || message.isLivePhoto());
    }

    private void saveMessageToDownloads(MessageObject message) {
        if (message == null || message.messageOwner == null || getParentActivity() == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && (Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE)
                && getParentActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            getParentActivity().requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
            return;
        }

        if (message.isMusic() || message.isDocument() || message.isLivePhoto()) {
            ArrayList<MessageObject> messageObjects = new ArrayList<>();
            messageObjects.add(message);
            final boolean isMusic = message.isMusic();
            final boolean isLivePhoto = message.isLivePhoto();
            MediaController.saveFilesFromMessages(getParentActivity(), getAccountInstance(), messageObjects, count -> {
                if (getParentActivity() == null || fragmentView == null || count <= 0) {
                    return;
                }
                BulletinFactory.FileType fileType = isLivePhoto ? BulletinFactory.FileType.LIVEPHOTO : isMusic ? BulletinFactory.FileType.AUDIO : BulletinFactory.FileType.UNKNOWN;
                BulletinFactory.of(this).createDownloadBulletin(fileType, count, getResourceProvider()).show();
            });
            return;
        }

        String path = getExistingFilePath(message);
        if (TextUtils.isEmpty(path)) {
            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.PleaseDownload), getResourceProvider()).show();
            return;
        }
        boolean photo = message.isPhoto();
        boolean video = message.isVideo();
        boolean gif = message.isGif();
        String fileName = FileLoader.getDocumentFileName(message.getDocument());
        if (TextUtils.isEmpty(fileName)) {
            fileName = message.getFileName();
        }
        MediaController.saveFile(path, getParentActivity(), 2, fileName, message.getMimeType(), uri -> {
            if (getParentActivity() == null) {
                return;
            }
            BulletinFactory.FileType fileType;
            if (photo) {
                fileType = BulletinFactory.FileType.PHOTO_TO_DOWNLOADS;
            } else if (video) {
                fileType = BulletinFactory.FileType.VIDEO_TO_DOWNLOADS;
            } else if (gif) {
                fileType = BulletinFactory.FileType.GIF_TO_DOWNLOADS;
            } else {
                fileType = BulletinFactory.FileType.UNKNOWN;
            }
            BulletinFactory.of(this).createDownloadBulletin(fileType, getResourceProvider()).show();
        });
    }

    private String getExistingFilePath(MessageObject message) {
        String path = message.messageOwner.attachPath;
        if (!TextUtils.isEmpty(path)) {
            File temp = new File(path);
            if (!temp.exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            File file = FileLoader.getInstance(currentAccount).getPathToMessage(message.messageOwner);
            if (file != null && file.exists()) {
                path = file.getPath();
            }
        }
        if (TextUtils.isEmpty(path) && message.cachedQuality != null && message.cachedQuality.isCached()) {
            File file = new File(message.cachedQuality.uri.getPath());
            if (file.exists()) {
                path = file.getPath();
            }
        }
        if (TextUtils.isEmpty(path) && message.qualityToSave != null) {
            File file = FileLoader.getInstance(currentAccount).getPathToAttach(message.qualityToSave, null, false, true);
            if (file != null && file.exists()) {
                path = file.getPath();
            }
        }
        return path;
    }

    @SuppressLint("ViewConstructor")
    private static class WallpaperFrameLayout extends FrameLayout {

        private BackgroundGradientDrawable.Disposable backgroundGradientDisposable;

        private WallpaperFrameLayout(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            Drawable drawable = Theme.getCachedWallpaperNonBlocking();
            if (drawable == null) {
                canvas.drawColor(Theme.getColor(Theme.key_chat_wallpaper));
                return;
            }
            drawable.setAlpha(255);
            if (drawable instanceof ColorDrawable || drawable instanceof GradientDrawable || drawable instanceof MotionBackgroundDrawable) {
                drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                if (drawable instanceof BackgroundGradientDrawable) {
                    backgroundGradientDisposable = ((BackgroundGradientDrawable) drawable).drawExactBoundsSize(canvas, this);
                } else {
                    drawable.draw(canvas);
                }
            } else if (drawable instanceof BitmapDrawable) {
                if (((BitmapDrawable) drawable).getTileModeX() == Shader.TileMode.REPEAT) {
                    canvas.save();
                    float scale = 2.0f / AndroidUtilities.density;
                    canvas.scale(scale, scale);
                    drawable.setBounds(0, 0, (int) Math.ceil(getMeasuredWidth() / scale), (int) Math.ceil(getMeasuredHeight() / scale));
                    drawable.draw(canvas);
                    canvas.restore();
                } else {
                    float scaleX = (float) getMeasuredWidth() / (float) drawable.getIntrinsicWidth();
                    float scaleY = (float) getMeasuredHeight() / (float) drawable.getIntrinsicHeight();
                    float scale = Math.max(scaleX, scaleY);
                    int width = (int) Math.ceil(drawable.getIntrinsicWidth() * scale);
                    int height = (int) Math.ceil(drawable.getIntrinsicHeight() * scale);
                    int x = (getMeasuredWidth() - width) / 2;
                    int y = (getMeasuredHeight() - height) / 2;
                    drawable.setBounds(x, y, x + width, y + height);
                    drawable.draw(canvas);
                }
            } else {
                drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                drawable.draw(canvas);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            if (backgroundGradientDisposable != null) {
                backgroundGradientDisposable.dispose();
                backgroundGradientDisposable = null;
            }
            super.onDetachedFromWindow();
        }
    }
}
