package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fylnx.lelegram.helpers.MessageHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class LeleRecallMessagesActivity extends BaseFragment {

    private final ArrayList<MessageObject> messages = new ArrayList<>();
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;

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
