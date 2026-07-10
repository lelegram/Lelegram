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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.URLSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fylnx.lelegram.MessageDetailsActivity;
import com.fylnx.lelegram.helpers.MessageHelper;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.EmbedBottomSheet;
import org.telegram.ui.Components.PhonebookShareAlert;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.Components.URLSpanUserMention;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RecyclerListView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LeleRecallMessagesActivity extends BaseFragment implements DialogsActivity.DialogsActivityDelegate {

    private static final int ACTION_COPY = 0;
    private static final int ACTION_FORWARD = 1;
    private static final int ACTION_SAVE_TO_DOWNLOADS = 2;
    private static final int ACTION_DETAILS = 3;

    private final ArrayList<MessageObject> messages = new ArrayList<>();
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private MessageObject pendingForwardMessage;
    private Browser.Progress webPageProgress;
    private MessageObject webPageProgressMessage;
    private ChatMessageCell webPageProgressCell;

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

        listView = new RecalledMessagesListView(context);
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

    @Override
    public void onFragmentDestroy() {
        if (webPageProgress != null) {
            webPageProgress.cancel();
        }
        webPageProgress = null;
        webPageProgressMessage = null;
        webPageProgressCell = null;
        super.onFragmentDestroy();
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
        messageOwner.dialog_id = source.messageOwner.dialog_id;
        if (hasUnavailableMedia(source) && TextUtils.isEmpty(messageOwner.message)) {
            messageOwner.message = LocaleController.getString(R.string.RecalledMediaUnavailable);
        }
        messageOwner.replyMessage = source.replyMessageObject != null && source.replyMessageObject.messageOwner != null ? source.replyMessageObject.messageOwner : source.messageOwner.replyMessage;
        MessageObject displayMessage = new MessageObject(currentAccount, messageOwner, true, true);
        displayMessage.deleted = false;
        displayMessage.messageOwner.deleted = false;
        if (source.replyMessageObject != null) {
            displayMessage.replyMessageObject = source.replyMessageObject;
        }
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
        message.rich_message = source.rich_message;
        return message;
    }

    private boolean hasUnavailableMedia(MessageObject messageObject) {
        return MessageHelper.isRecallFileBackedMedia(messageObject)
                && !MessageHelper.isRecallMediaAvailable(messageObject);
    }

    private void showRecallMediaUnavailable() {
        if (getParentActivity() != null) {
            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.RecalledMediaUnavailable), getResourceProvider()).show();
        }
    }

    private void showRecallUnsupportedMessage() {
        if (getParentActivity() != null) {
            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.RecalledUnsupportedMessage), getResourceProvider()).show();
        }
    }

    private boolean canPinToPrevious(MessageObject previous, MessageObject current) {
        return previous != null
                && current != null
                && previous.isOutOwner() == current.isOutOwner()
                && previous.getFromChatId() == current.getFromChatId()
                && Math.abs(previous.messageOwner.date - current.messageOwner.date) <= 5 * 60;
    }

    private boolean openArticlePhoto(ChatMessageCell cell, TL_iv.PageBlock targetBlock) {
        if (getParentActivity() == null || cell == null || targetBlock == null) {
            return false;
        }
        MessageObject messageObject = cell.getMessageObject();
        if (messageObject == null || messageObject.messageOwner == null || messageObject.richLayout == null) {
            return false;
        }
        TL_iv.RichMessage richMessage = messageObject.messageOwner.rich_message;
        if (richMessage == null) {
            return false;
        }

        ArrayList<TL_iv.PageBlock> candidates = new ArrayList<>();
        messageObject.richLayout.collectMediaBlocks(candidates);
        ArrayList<TL_iv.PageBlock> pageBlocks = new ArrayList<>();
        int index = -1;
        for (int i = 0; i < candidates.size(); i++) {
            TL_iv.PageBlock candidate = candidates.get(i);
            if (ArticleViewer.WebPageUtils.getMedia(richMessage, candidate) == null) {
                continue;
            }
            File file = getRecallArticleMediaFile(richMessage, candidate);
            if (file == null || !file.exists()) {
                continue;
            }
            if (candidate == targetBlock) {
                index = pageBlocks.size();
            }
            pageBlocks.add(candidate);
        }
        if (index < 0) {
            showRecallMediaUnavailable();
            return false;
        }

        PhotoViewer.getInstance().setParentActivity(this, getResourceProvider());
        boolean opened = PhotoViewer.getInstance().openPhoto(index, new RecallArticlePageBlocksAdapter(richMessage, pageBlocks), new RecallArticlePhotoViewerProvider(pageBlocks));
        if (!opened) {
            showRecallMediaUnavailable();
        }
        return opened;
    }

    private File getRecallArticleMediaFile(TL_iv.RichMessage page, TL_iv.PageBlock block) {
        TLObject media = ArticleViewer.WebPageUtils.getMedia(page, block);
        if (media instanceof TLRPC.Photo) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(((TLRPC.Photo) media).sizes, AndroidUtilities.getPhotoSize());
            return size != null ? FileLoader.getInstance(currentAccount).getPathToAttach(size, true) : null;
        }
        if (media instanceof TLRPC.Document) {
            return FileLoader.getInstance(currentAccount).getPathToAttach((TLRPC.Document) media, true);
        }
        return null;
    }
    private void openPhotoViewerForMessage(MessageObject message) {
        if (message == null || !MessageHelper.isRecallMediaOpenable(message)) {
            return;
        }
        if (!MessageHelper.isRecallMediaAvailable(message)) {
            showRecallMediaUnavailable();
            return;
        }
        PhotoViewer.getInstance().setParentActivity(this, getResourceProvider());
        ArrayList<MessageObject> mediaMessages = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            MessageObject item = messages.get(i);
            if (MessageHelper.isRecallMediaOpenable(item) && MessageHelper.isRecallMediaAvailable(item)) {
                mediaMessages.add(item);
            }
        }
        int index = mediaMessages.indexOf(message);
        if (index < 0) {
            return;
        }
        long dialogId = message.type != 0 ? message.getDialogId() : 0;
        if (!PhotoViewer.getInstance().openPhoto(mediaMessages, index, dialogId, 0, 0, new RecallPhotoViewerProvider())) {
            showRecallMediaUnavailable();
        }
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
            ChatMessageCell cell = new ChatMessageCell(context, currentAccount, true, null, getResourceProvider());
            cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                @Override
                public boolean canPerformActions() {
                    return true;
                }

                @Override
                public void didPressImage(ChatMessageCell cell, float x, float y, boolean fullPreview) {
                    handleMessagePress(cell.getMessageObject());
                }


                @Override
                public void didPressOther(ChatMessageCell cell, float otherX, float otherY) {
                    handleMessagePress(cell.getMessageObject());
                }

                @Override
                public void didPressInstantButton(ChatMessageCell cell, int type) {
                    handleInstantButton(cell, cell.getMessageObject(), type);
                }

                @Override
                public void didPressUrl(ChatMessageCell cell, CharacterStyle url, boolean longPress) {
                    handleUrlPress(cell, url, longPress);
                }

                @Override
                public void needOpenWebView(MessageObject message, String url, String title, String description, String originalUrl, int w, int h) {
                    if (getParentActivity() == null || TextUtils.isEmpty(url)) {
                        return;
                    }
                    try {
                        EmbedBottomSheet.show(LeleRecallMessagesActivity.this, message, new RecallPhotoViewerProvider(), title, description, originalUrl, url, w, h, false);
                    } catch (Throwable e) {
                        FileLog.e(e);
                        showRecallUnsupportedMessage();
                    }
                }

                @Override
                public void didPressWebPage(ChatMessageCell cell, TLRPC.WebPage webpage, String url, boolean safe) {
                    openWebPage(cell, webpage, url, safe);
                }

                @Override
                public boolean isProgressLoading(ChatMessageCell cell, int type) {
                    return type == ChatActivity.PROGRESS_INSTANT
                            && cell != null
                            && cell.getMessageObject() == webPageProgressMessage;
                }

                @Override
                public boolean openArticlePhoto(ChatMessageCell cell, TL_iv.PageBlock block) {
                    return LeleRecallMessagesActivity.this.openArticlePhoto(cell, block);
                }

                @Override
                public void didPressReplyMessage(ChatMessageCell cell, int id, float x, float y, boolean longpress) {
                    handleReplyPress(cell.getMessageObject(), id);
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
                    if ((messageObject.isVoice() || messageObject.isRoundVideo() || messageObject.isMusic())
                            && !MessageHelper.isRecallMediaAvailable(messageObject)) {
                        showRecallMediaUnavailable();
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

                @Override
                public void didPressUserAvatar(ChatMessageCell cell, TLRPC.User user, float touchX, float touchY, boolean asForward) {
                    openProfile(user);
                }

                @Override
                public void didPressChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId, float touchX, float touchY, boolean asForward) {
                    openChannel(cell, chat, postId);
                }

                @Override
                public void didPressHiddenForward(ChatMessageCell cell) {
                    BulletinFactory.of(LeleRecallMessagesActivity.this).createSimpleBulletin(R.raw.info, LocaleController.getString(R.string.HidAccount)).show();
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
            cell.isChat = shouldShowAuthor(message);
            cell.setFullyDraw(true);
            cell.setMessageObject(message, null, canPinToPrevious(next, message), canPinToPrevious(previous, message), position == 0, position == messages.size() - 1);
            cell.setHighlighted(false);
        }
    }

    private static boolean shouldShowAuthor(MessageObject message) {
        return message != null && message.getDialogId() < 0;
    }

    private void openProfile(TLRPC.User user) {
        if (user == null || user.id == UserObject.VERIFY || getParentActivity() == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong("user_id", user.id);
        presentFragment(new ProfileActivity(args));
    }

    private void openChannel(ChatMessageCell cell, TLRPC.Chat chat, int postId) {
        if (cell == null || chat == null || getParentActivity() == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong("chat_id", chat.id);
        if (postId != 0) {
            args.putInt("message_id", postId);
        }
        if (getMessagesController().checkCanOpenChat(args, this, cell.getMessageObject())) {
            presentFragment(new ChatActivity(args));
        }
    }

    private void handleMessagePress(MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return;
        }
        if (message.type == MessageObject.TYPE_GEO) {
            openLocation(message);
            return;
        }
        if (message.type == MessageObject.TYPE_CONTACT) {
            openContact(message);
            return;
        }
        if (MessageHelper.isRecallMediaOpenable(message)) {
            if (!MessageHelper.isRecallMediaAvailable(message)) {
                showRecallMediaUnavailable();
            } else {
                openPhotoViewerForMessage(message);
            }
            return;
        }
        if (message.type == MessageObject.TYPE_FILE || message.type == MessageObject.TYPE_TEXT && message.getDocument() != null) {
            if (!MessageHelper.isRecallMediaAvailable(message)) {
                showRecallMediaUnavailable();
            } else {
                openDocument(message);
            }
            return;
        }
        if (MessageHelper.isRecallFileBackedMedia(message) && !MessageHelper.isRecallMediaAvailable(message)) {
            showRecallMediaUnavailable();
        }
    }

    private void handleInstantButton(ChatMessageCell cell, MessageObject message, int type) {
        TLRPC.MessageMedia media = message != null && message.messageOwner != null ? MessageObject.getMedia(message.messageOwner) : null;
        if (media == null) {
            return;
        }
        if (type == ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_VIEW) {
            openContact(message);
        } else if (type == ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_SEND_MESSAGE) {
            long uid = media.user_id;
            if (uid == 0) {
                showRecallUnsupportedMessage();
                return;
            }
            Bundle args = new Bundle();
            args.putLong("user_id", uid);
            if (getMessagesController().checkCanOpenChat(args, this, message)) {
                presentFragment(new ChatActivity(args));
            }
        } else if (type == ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_ADD) {
            addContact(message);
        } else if (media.webpage != null && !TextUtils.isEmpty(media.webpage.url)) {
            openWebPage(cell, media.webpage, media.webpage.url, media.safe);
        }
    }

    private void openWebPage(ChatMessageCell cell, TLRPC.WebPage webpage, String url, boolean safe) {
        if (TextUtils.isEmpty(url) || getParentActivity() == null) {
            return;
        }
        if (!safe && Browser.isTelegraphUrl(url, false)) {
            safe = true;
        }
        Uri uri = Uri.parse(url);
        Browser.Progress progress = createWebPageProgress(cell);
        if (safe || Browser.isInternalUri(uri, null)) {
            Browser.openUrl(getContext(), uri, true, true, false, progress, null, false, true, false);
        } else {
            AlertsCreator.showOpenUrlAlert(this, url, true, true, true, true, progress, webpage, getResourceProvider());
        }
    }

    private Browser.Progress createWebPageProgress(ChatMessageCell cell) {
        if (cell == null || cell.getMessageObject() == null) {
            return null;
        }
        if (webPageProgress != null) {
            Browser.Progress previous = webPageProgress;
            previous.cancel(true);
            clearWebPageProgress(previous);
        }
        MessageObject message = cell.getMessageObject();
        webPageProgress = new Browser.Progress() {
            @Override
            public void init() {
                webPageProgressMessage = message;
                webPageProgressCell = cell;
                cell.invalidate();
            }

            @Override
            public void end(boolean replaced) {
                if (!replaced) {
                    AndroidUtilities.runOnUIThread(() -> clearWebPageProgress(this), 250);
                }
            }
        };
        return webPageProgress;
    }

    private void clearWebPageProgress(Browser.Progress expected) {
        ChatMessageCell oldCell = webPageProgressCell;
        if (webPageProgress != expected) {
            return;
        }
        webPageProgress = null;
        webPageProgressMessage = null;
        webPageProgressCell = null;
        if (oldCell != null) {
            oldCell.invalidate();
        }
    }

    private void handleUrlPress(ChatMessageCell cell, CharacterStyle url, boolean longPress) {
        try {
            if (url == null || getParentActivity() == null) {
                return;
            }
            if (url instanceof URLSpanMono) {
                ((URLSpanMono) url).copyToClipboard();
                BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied), getResourceProvider()).show();
            } else if (url instanceof URLSpanUserMention) {
                openUserMention(((URLSpanUserMention) url).getURL());
            } else if (url instanceof URLSpanNoUnderline) {
                String str = ((URLSpanNoUnderline) url).getURL();
                if (TextUtils.isEmpty(str)) {
                    return;
                }
                if (str.startsWith("video?") || str.startsWith("audio?")) {
                    handleMediaTimestamp(cell, str, longPress);
                    return;
                }
                if (str.startsWith("card:")) {
                    showRecallUnsupportedMessage();
                    return;
                }
                if (str.startsWith("@")) {
                    getMessagesController().openByUserName(str.substring(1), this, 0);
                } else if (str.startsWith("#")) {
                    DialogsActivity fragment = new DialogsActivity(null);
                    fragment.setSearchString(str);
                    presentFragment(fragment);
                } else {
                    openUrl(str, longPress, false);
                }
            } else if (url instanceof URLSpan) {
                openUrl(((URLSpan) url).getURL(), longPress, url instanceof URLSpanReplacement);
            }
        } finally {
            if (longPress && cell != null) {
                cell.resetPressedLink(-1);
            }
        }
    }

    private void handleMediaTimestamp(ChatMessageCell cell, String value, boolean longPress) {
        if (getParentActivity() == null) {
            return;
        }
        int seconds = Utilities.parseInt(value);
        if (seconds < 0) {
            showRecallUnsupportedMessage();
            return;
        }
        if (longPress) {
            BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity(), false, getResourceProvider());
            builder.setTitle(AndroidUtilities.formatDuration(seconds, false));
            builder.setItems(new CharSequence[]{LocaleController.getString(R.string.Open)}, (dialog, which) -> {
                if (which == 0) {
                    handleMediaTimestamp(cell, value, false);
                }
            });
            showDialog(builder.create());
            return;
        }

        boolean videoTimestamp = value.startsWith("video?");
        boolean audioTimestamp = value.startsWith("audio?");
        if (!videoTimestamp && !audioTimestamp) {
            showRecallUnsupportedMessage();
            return;
        }
        MessageObject source = cell != null ? cell.getMessageObject() : null;
        MessageObject target = null;
        if (source != null) {
            if (videoTimestamp && (source.isYouTubeVideo() || source.isVideo())
                    || audioTimestamp && (source.isVoice() || source.isMusic())) {
                target = source;
            } else if (source.replyMessageObject != null
                    && (videoTimestamp && (source.replyMessageObject.isYouTubeVideo() || source.replyMessageObject.isVideo())
                    || audioTimestamp && (source.replyMessageObject.isVoice() || source.replyMessageObject.isMusic()))) {
                target = source.replyMessageObject;
            }
        }
        if (target == null) {
            showRecallUnsupportedMessage();
            return;
        }

        int position = findMessagePosition(target, target.getId(), target.getDialogId());
        boolean inList = position >= 0;
        if (inList) {
            target = messages.get(position);
        }
        if (videoTimestamp && target.isYouTubeVideo()) {
            TLRPC.MessageMedia media = target.messageOwner != null ? MessageObject.getMedia(target.messageOwner) : null;
            TLRPC.WebPage webPage = media != null ? media.webpage : null;
            if (webPage == null || TextUtils.isEmpty(webPage.embed_url)) {
                showRecallUnsupportedMessage();
                return;
            }
            EmbedBottomSheet.show(this, target, new RecallPhotoViewerProvider(), webPage.site_name, webPage.title, webPage.url, webPage.embed_url, webPage.embed_width, webPage.embed_height, seconds, false);
            return;
        }

        double duration = target.getDuration();
        if (duration <= 0) {
            showRecallUnsupportedMessage();
            return;
        }
        if (!MessageHelper.isRecallMediaAvailable(target)) {
            showRecallMediaUnavailable();
            return;
        }
        if (videoTimestamp) {
            target.forceSeekTo = (float) Math.max(0.0, Math.min(1.0, seconds / duration));
            if (inList) {
                openPhotoViewerForMessage(target);
                return;
            }
            long targetDialogId = target.getDialogId();
            if (targetDialogId == 0) {
                showRecallUnsupportedMessage();
                return;
            }
            PhotoViewer.getInstance().setParentActivity(this, getResourceProvider());
            if (!PhotoViewer.getInstance().openPhoto(target, targetDialogId, 0, 0, new RecallPhotoViewerProvider(), true)) {
                showRecallMediaUnavailable();
            }
            return;
        }

        float progress = (float) (seconds / duration);
        MediaController mediaController = getMediaController();
        if (mediaController.isPlayingMessage(target)) {
            target.audioProgress = progress;
            mediaController.seekToProgress(target, progress);
            if (mediaController.isMessagePaused()) {
                mediaController.playMessage(target);
            }
        } else {
            target.forceSeekTo = progress;
            if (!mediaController.playMessage(target)) {
                showRecallUnsupportedMessage();
                return;
            }
        }
        if (target.isMusic() && getParentActivity() != null) {
            showDialog(new AudioPlayerAlert(getContext(), getResourceProvider()));
        }
    }

    private void openUserMention(String rawId) {
        long peerId = Utilities.parseLong(rawId);
        if (peerId > 0) {
            TLRPC.User user = getMessagesController().getUser(peerId);
            if (user != null) {
                getMessagesController().openChatOrProfileWith(user, null, this, 0, false);
            } else {
                presentFragment(ProfileActivity.of(peerId));
            }
        } else if (peerId < 0) {
            TLRPC.Chat chat = getMessagesController().getChat(-peerId);
            if (chat != null) {
                getMessagesController().openChatOrProfileWith(null, chat, this, 0, false);
            } else {
                presentFragment(ProfileActivity.of(peerId));
            }
        }
    }

    private void openUrl(String url, boolean longPress, boolean forceAlert) {
        if (TextUtils.isEmpty(url) || getParentActivity() == null) {
            return;
        }
        if (longPress) {
            BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity(), false, getResourceProvider());
            builder.setTitle(url);
            builder.setItems(new CharSequence[]{LocaleController.getString(R.string.Open), LocaleController.getString(R.string.Copy)}, (dialog, which) -> {
                if (which == 0) {
                    AlertsCreator.showOpenUrlAlert(this, url, true, true, getResourceProvider());
                } else if (which == 1) {
                    copyUrl(url);
                }
            });
            showDialog(builder.create());
            return;
        }
        AlertsCreator.showOpenUrlAlert(this, url, true, forceAlert || AndroidUtilities.shouldShowUrlInAlert(url), getResourceProvider());
    }

    private void copyUrl(String url) {
        if (url.startsWith("mailto:")) {
            url = url.substring(7);
        } else if (url.startsWith("tel:")) {
            url = url.substring(4);
        }
        AndroidUtilities.addToClipboard(url);
        BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied), getResourceProvider()).show();
    }

    private void handleReplyPress(MessageObject messageObject, int id) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return;
        }
        TLRPC.MessageReplyHeader replyTo = messageObject.messageOwner.reply_to;
        int targetMessageId = id != 0 ? id : replyTo != null ? replyTo.reply_to_msg_id : 0;
        long targetDialogId = messageObject.getDialogId();
        MessageObject replyMessageObject = messageObject.replyMessageObject;
        boolean matchedReplyObject = targetMessageId != 0
                && replyMessageObject != null
                && (replyMessageObject.getId() == targetMessageId || replyMessageObject.getRealId() == targetMessageId);
        if (matchedReplyObject) {
            targetDialogId = replyMessageObject.getDialogId();
        }


        boolean canNavigate = true;
        if (!matchedReplyObject && replyTo != null) {
            if (replyTo.reply_to_peer_id != null) {
                long replyDialogId = DialogObject.getPeerDialogId(replyTo.reply_to_peer_id);
                if (!(replyTo.reply_to_peer_id instanceof TLRPC.TL_peerUser)) {
                    targetDialogId = replyDialogId;
                } else if (replyDialogId == messageObject.getDialogId()) {
                    targetDialogId = replyDialogId;
                } else {
                    canNavigate = false;
                }
            } else if (replyTo.reply_from != null) {
                TLRPC.MessageFwdHeader replyFrom = replyTo.reply_from;
                if (replyFrom.from_id != null) {
                    if (!(replyFrom.from_id instanceof TLRPC.TL_peerUser)) {
                        targetDialogId = DialogObject.getPeerDialogId(replyFrom.from_id);
                        if (replyFrom.channel_post != 0) {
                            targetMessageId = replyFrom.channel_post;
                        }
                    } else {
                        canNavigate = false;
                    }
                } else if (replyFrom.saved_from_peer != null) {
                    targetDialogId = DialogObject.getPeerDialogId(replyFrom.saved_from_peer);
                    if (replyFrom.saved_from_msg_id != 0) {
                        targetMessageId = replyFrom.saved_from_msg_id;
                    }
                }
            }
        }

        if (canNavigate) {
            int position = findMessagePosition(matchedReplyObject ? replyMessageObject : null, targetMessageId, targetDialogId);
            if (position >= 0) {
                highlightMessageAt(position);
                return;
            }
        }

        String quote = null;
        int quoteOffset = -1;
        Integer taskId = null;
        byte[] pollOption = null;
        if (replyTo != null) {
            if ((replyTo.flags & 2048) != 0) {
                taskId = replyTo.todo_item_id;
            } else if (replyTo.poll_option != null) {
                pollOption = replyTo.poll_option;
            } else if (replyTo.quote) {
                quote = replyTo.quote_text;
                if ((replyTo.flags & 1024) != 0) {
                    quoteOffset = replyTo.quote_offset;
                }
            }
        }
        if (!canNavigate || LaunchActivity.instance == null || targetDialogId == 0 || DialogObject.isEncryptedDialog(targetDialogId) || targetMessageId == 0) {
            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.SourceMessageDeleted), getResourceProvider()).show();
            return;
        }
        LaunchActivity.instance.openMessage(targetDialogId, targetMessageId, quote, null, messageObject.getId(), quoteOffset, taskId, pollOption);
    }

    private int findMessagePosition(MessageObject target, int messageId, long dialogId) {
        for (int i = 0; i < messages.size(); i++) {
            MessageObject item = messages.get(i);
            if (item == null) {
                continue;
            }
            if (target != null && item.getDialogId() == target.getDialogId() && (item.getId() == target.getId() || target.getRealId() != 0 && item.getRealId() == target.getRealId())) {
                return i;
            }
            if (messageId != 0 && dialogId != 0 && item.getDialogId() == dialogId && (item.getId() == messageId || item.getRealId() == messageId)) {
                return i;
            }
        }
        return -1;
    }

    private void highlightMessageAt(int position) {
        if (listView == null || layoutManager == null || position < 0 || position >= messages.size()) {
            return;
        }
        layoutManager.scrollToPositionWithOffset(position, dp(48));
        listView.post(() -> {
            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(position);
            if (!(holder != null && holder.itemView instanceof ChatMessageCell cell)) {
                return;
            }
            MessageObject highlighted = cell.getMessageObject();
            cell.setHighlighted(true);
            cell.setHighlightedAnimated();
            AndroidUtilities.runOnUIThread(() -> {
                if (cell.getMessageObject() == highlighted) {
                    cell.setHighlighted(false);
                }
            }, 1200);
        });
    }


    private void openLocation(MessageObject message) {
        if (getParentActivity() == null || !AndroidUtilities.isMapsInstalled(this)) {
            return;
        }
        LocationActivity fragment = new LocationActivity(message.isLiveLocation() ? LocationActivity.LOCATION_TYPE_LIVE_VIEW : 3);
        fragment.setSharingAllowed(false);
        fragment.setMessageObject(message);
        presentFragment(fragment);
    }

    private void openDocument(MessageObject message) {
        if (getParentActivity() == null) {
            return;
        }
        if (!MessageHelper.isRecallMediaAvailable(message)) {
            showRecallMediaUnavailable();
            return;
        }
        try {
            if (!AndroidUtilities.openForView(message, getParentActivity(), getResourceProvider(), false)) {
                showRecallMediaUnavailable();
            }
        } catch (Exception e) {
            FileLog.e(e);
            alertUserOpenError(message);
        }
    }

    private void alertUserOpenError(MessageObject message) {
        String text;
        if (message != null && message.type == MessageObject.TYPE_VIDEO) {
            text = LocaleController.getString(R.string.NoPlayerInstalled);
        } else {
            TLRPC.Document document = message != null ? message.getDocument() : null;
            String mimeType = document != null && !TextUtils.isEmpty(document.mime_type) ? document.mime_type : "application/octet-stream";
            text = LocaleController.formatString(R.string.NoHandleAppInstalled, mimeType);
        }
        AlertsCreator.showSimpleAlert(this, LocaleController.getString(R.string.AppName), text, getResourceProvider());
    }

    private void openContact(MessageObject message) {
        TLRPC.MessageMedia media = message != null && message.messageOwner != null ? MessageObject.getMedia(message.messageOwner) : null;
        if (media == null) {
            showRecallUnsupportedMessage();
            return;
        }
        openVCard(getContactUser(media), media.phone_number, media.vcard, media.first_name, media.last_name);
    }

    private TLRPC.User getContactUser(TLRPC.MessageMedia media) {
        if (media == null || media.user_id == 0) {
            return null;
        }
        return getMessagesController().getUser(media.user_id);
    }

    private void openVCard(TLRPC.User user, String phone, String vcard, String firstName, String lastName) {
        if (user != null) {
            Bundle args = new Bundle();
            args.putLong("user_id", user.id);
            args.putBoolean("show_add_to_contacts", true);
            args.putString("vcard", vcard);
            args.putString("vcard_phone", phone);
            args.putString("vcard_first_name", firstName);
            args.putString("vcard_last_name", lastName);
            presentFragment(new ProfileActivity(args));
            return;
        }
        try {
            File file = null;
            if (!TextUtils.isEmpty(vcard)) {
                file = AndroidUtilities.getSharingDirectory();
                file.mkdirs();
                file = new File(file, "vcard.vcf");
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write(vcard);
                writer.close();
            }
            String normalizedPhone = TextUtils.isEmpty(phone) ? null : PhoneFormat.stripExceptNumbers(phone);
            showDialog(new PhonebookShareAlert(this, null, null, null, file, normalizedPhone, firstName, lastName, getResourceProvider()));
        } catch (Exception e) {
            FileLog.e(e);
            showRecallUnsupportedMessage();
        }
    }

    private void addContact(MessageObject message) {
        TLRPC.MessageMedia media = message != null && message.messageOwner != null ? MessageObject.getMedia(message.messageOwner) : null;
        if (media == null) {
            return;
        }
        TLRPC.User user = getContactUser(media);
        if (user == null) {
            openVCard(null, media.phone_number, media.vcard, media.first_name, media.last_name);
            return;
        }
        String phone;
        if (!TextUtils.isEmpty(message.vCardData)) {
            phone = message.vCardData.toString();
        } else if (!TextUtils.isEmpty(user.phone)) {
            phone = PhoneFormat.getInstance().format("+" + user.phone);
        } else if (!TextUtils.isEmpty(media.phone_number)) {
            phone = PhoneFormat.getInstance().format(media.phone_number);
        } else {
            phone = LocaleController.getString(R.string.NumberUnknown);
        }
        Bundle args = new Bundle();
        args.putLong("user_id", user.id);
        args.putString("phone", phone);
        args.putBoolean("addContact", true);
        presentFragment(new ContactAddActivity(args));
    }

    private void showMessageDetails(MessageObject message) {
        if (message != null) {
            presentFragment(new MessageDetailsActivity(message));
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
        items.add(LocaleController.getString(R.string.MessageDetails));
        icons.add(R.drawable.msg_info);
        actions.add(ACTION_DETAILS);
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
            } else if (action == ACTION_DETAILS) {
                showMessageDetails(message);
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
        if (!MessageHelper.isRecallMediaAvailable(message)) {
            showRecallMediaUnavailable();
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
            showRecallMediaUnavailable();
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

    private class RecallArticlePageBlocksAdapter implements PhotoViewer.PageBlocksAdapter {
        private final TL_iv.RichMessage page;
        private final List<TL_iv.PageBlock> blocks;

        private RecallArticlePageBlocksAdapter(TL_iv.RichMessage page, List<TL_iv.PageBlock> blocks) {
            this.page = page;
            this.blocks = blocks;
        }

        @Override
        public int getItemsCount() {
            return blocks.size();
        }

        @Override
        public TL_iv.PageBlock get(int index) {
            return blocks.get(index);
        }

        @Override
        public List<TL_iv.PageBlock> getAll() {
            return blocks;
        }

        @Override
        public boolean isVideo(int index) {
            return index >= 0 && index < blocks.size() && ArticleViewer.WebPageUtils.isVideo(page, blocks.get(index));
        }

        @Override
        public TLObject getMedia(int index) {
            return index >= 0 && index < blocks.size() ? ArticleViewer.WebPageUtils.getMedia(page, blocks.get(index)) : null;
        }

        @Override
        public File getFile(int index) {
            return index >= 0 && index < blocks.size() ? getRecallArticleMediaFile(page, blocks.get(index)) : null;
        }

        @Override
        public String getFileName(int index) {
            TLObject media = getMedia(index);
            if (media instanceof TLRPC.Photo) {
                media = FileLoader.getClosestPhotoSizeWithSize(((TLRPC.Photo) media).sizes, AndroidUtilities.getPhotoSize());
            }
            return media != null ? FileLoader.getAttachFileName(media) : null;
        }

        @Override
        public CharSequence getCaption(int index) {
            return null;
        }

        @Override
        public TLRPC.PhotoSize getFileLocation(TLObject media, int[] size) {
            if (media instanceof TLRPC.Photo) {
                TLRPC.PhotoSize fullSize = FileLoader.getClosestPhotoSizeWithSize(((TLRPC.Photo) media).sizes, AndroidUtilities.getPhotoSize());
                if (fullSize != null) {
                    if (size != null && size.length > 0) {
                        size[0] = fullSize.size != 0 ? fullSize.size : -1;
                    }
                    return fullSize;
                }
                if (size != null && size.length > 0) {
                    size[0] = -1;
                }
            } else if (media instanceof TLRPC.Document) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(((TLRPC.Document) media).thumbs, 90);
                if (thumb != null) {
                    if (size != null && size.length > 0) {
                        size[0] = thumb.size != 0 ? thumb.size : -1;
                    }
                    return thumb;
                }
            }
            return null;
        }

        @Override
        public void updateSlideshowCell(TL_iv.PageBlock currentPageBlock) {
        }

        @Override
        public Object getParentObject() {
            return page;
        }

        @Override
        public boolean isHardwarePlayer(int index) {
            return false;
        }
    }

    private class RecallArticlePhotoViewerProvider extends PhotoViewer.EmptyPhotoViewerProvider {
        private final List<TL_iv.PageBlock> blocks;
        private final int[] tempCoords = new int[2];

        private RecallArticlePhotoViewerProvider(List<TL_iv.PageBlock> blocks) {
            this.blocks = blocks;
        }

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            if (listView == null || index < 0 || index >= blocks.size()) {
                return null;
            }
            TL_iv.PageBlock targetBlock = blocks.get(index);
            for (int i = 0; i < listView.getChildCount(); i++) {
                View view = listView.getChildAt(i);
                if (!(view instanceof ChatMessageCell cell)) {
                    continue;
                }
                MessageObject item = cell.getMessageObject();
                if (item == null || item.richLayout == null) {
                    continue;
                }
                int[] offset = new int[2];
                ImageReceiver imageReceiver = item.richLayout.findMediaImageReceiver(targetBlock, offset);
                if (imageReceiver == null) {
                    continue;
                }
                view.getLocationInWindow(tempCoords);
                tempCoords[0] += cell.getTextX() + offset[0];
                tempCoords[1] += cell.getTextY() + offset[1];
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = tempCoords[0];
                object.viewY = tempCoords[1];
                object.parentView = listView;
                object.imageReceiver = imageReceiver;
                object.thumb = imageReceiver.getBitmapSafe();
                object.radius = imageReceiver.getRoundRadius(true);
                return object;
            }
            return null;
        }
    }

    private class RecallPhotoViewerProvider extends PhotoViewer.EmptyPhotoViewerProvider {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            if (listView == null || messageObject == null) {
                return null;
            }
            for (int i = 0; i < listView.getChildCount(); i++) {
                View view = listView.getChildAt(i);
                if (!(view instanceof ChatMessageCell cell)) {
                    continue;
                }
                MessageObject cellMessage = cell.getMessageObject();
                if (cellMessage == null || cellMessage.getId() != messageObject.getId() || cellMessage.getDialogId() != messageObject.getDialogId()) {
                    continue;
                }
                ImageReceiver imageReceiver = cell.getPhotoImage(index);
                if (imageReceiver == null) {
                    return null;
                }
                int[] coords = new int[2];
                view.getLocationInWindow(coords);
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1] + view.getPaddingTop();
                object.parentView = listView;
                object.imageReceiver = imageReceiver;
                object.radius = imageReceiver.getRoundRadius(true);
                if (needPreview) {
                    object.thumb = imageReceiver.getBitmapSafe();
                }
                return object;
            }
            return null;
        }
    }

    private static class RecalledMessagesListView extends RecyclerListView {

        private RecalledMessagesListView(Context context) {
            super(context);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!(child instanceof ChatMessageCell cell)) {
                    continue;
                }
                if (!shouldShowAuthor(cell.getMessageObject())) {
                    continue;
                }
                ImageReceiver avatarImage = cell.getAvatarImage();
                if (avatarImage == null) {
                    continue;
                }
                int adapterPosition = getChildAdapterPosition(child);
                boolean updateVisibility = cell.getMessageObject() != null && !cell.getMessageObject().deleted && adapterPosition != RecyclerView.NO_POSITION;
                int top = child.getTop() + child.getPaddingTop();
                int y = (int) (child.getY() + cell.getPaddingTopAnimated() + cell.getLayoutHeight() + cell.getTransitionParams().deltaBottom);
                int maxY = getMeasuredHeight() - getPaddingBottom();
                if (y > maxY) {
                    y = maxY;
                }
                if (y - dp(48) < top) {
                    y = top + dp(48);
                }
                if (!cell.drawPinnedBottom()) {
                    int cellBottom = (int) (cell.getY() + cell.getMeasuredHeight() + cell.getDeltaBottom());
                    if (y > cellBottom) {
                        y = cellBottom;
                    }
                }
                if (updateVisibility) {
                    avatarImage.setImageY(y - dp(44));
                    avatarImage.setVisible(true, false);
                }
                avatarImage.setAlpha(cell.getAlpha());
                avatarImage.draw(canvas);
            }
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
