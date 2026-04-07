package org.telegram.messenger;

import android.text.TextUtils;
import android.util.Base64;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;

public class MessageCustomParamsHelper {
    private static final int VECTOR_MAGIC = 0x1cb5c415;
    private static final int MAX_LOCAL_EDIT_HISTORY = 10;
    static final String PREVIOUS_MEDIA_PARAM = "prevMedia";
    static final String PREVIOUS_EDIT_HISTORY_PARAM = "prevEditHistory";

    public static boolean isEmpty(TLRPC.Message message) {
        return (
            message.voiceTranscription == null &&
            message.translatedVoiceTranscription == null &&
            !message.voiceTranscriptionOpen &&
            !message.summarizedOpen &&
            message.summaryText == null &&
            message.translatedSummaryLanguage == null &&
            message.translatedSummaryText == null &&
            !message.voiceTranscriptionFinal &&
            !message.voiceTranscriptionRated &&
            !message.voiceTranscriptionForce &&
            message.voiceTranscriptionId == 0 &&
            !message.premiumEffectWasPlayed &&
            message.originalLanguage == null &&
            message.translatedToLanguage == null &&
            message.translatedPoll == null &&
            message.translatedText == null &&
            message.errorAllowedPriceStars == 0 &&
            message.errorNewPriceStars == 0 &&
            !hasLocalEditHistory(message)
        );
    }

    public static void copyParams(TLRPC.Message fromMessage, TLRPC.Message toMessage) {
        toMessage.voiceTranscription = fromMessage.voiceTranscription;
        toMessage.voiceTranscriptionOpen = fromMessage.voiceTranscriptionOpen;
        toMessage.voiceTranscriptionFinal = fromMessage.voiceTranscriptionFinal;
        toMessage.voiceTranscriptionForce = fromMessage.voiceTranscriptionForce;
        toMessage.voiceTranscriptionRated = fromMessage.voiceTranscriptionRated;
        toMessage.voiceTranscriptionId = fromMessage.voiceTranscriptionId;
        toMessage.premiumEffectWasPlayed = fromMessage.premiumEffectWasPlayed;
        toMessage.originalLanguage = fromMessage.originalLanguage;
        toMessage.translatedToLanguage = fromMessage.translatedToLanguage;
        toMessage.translatedPoll = fromMessage.translatedPoll;
        toMessage.translatedText = fromMessage.translatedText;
        toMessage.errorAllowedPriceStars = fromMessage.errorAllowedPriceStars;
        toMessage.errorNewPriceStars = fromMessage.errorNewPriceStars;
        toMessage.translatedVoiceTranscription = fromMessage.translatedVoiceTranscription;
        toMessage.summarizedOpen = fromMessage.summarizedOpen;
        toMessage.summaryText = fromMessage.summaryText;
        toMessage.translatedSummaryText = fromMessage.translatedSummaryText;
        toMessage.translatedSummaryLanguage = fromMessage.translatedSummaryLanguage;
        toMessage.editHistory = copyEditHistory(fromMessage.editHistory);
    }

    public static boolean hasLocalEditHistory(TLRPC.Message message) {
        return message != null && message.editHistory != null && !message.editHistory.isEmpty();
    }

    public static void addLocalEditHistory(TLRPC.Message message, TLRPC.Message previousMessage) {
        if (message == null || previousMessage == null) {
            return;
        }
        mergeLocalEditHistoryEntries(message, copyAvailableEditHistory(previousMessage));
        if (isSameLocalVersion(message, previousMessage)) {
            normalizeLocalEditHistory(message);
            return;
        }
        TLRPC.MessageEditHistoryEntry entry = createLocalEditHistoryEntry(previousMessage);
        if (entry == null || TextUtils.equals(message.message, entry.text)) {
            normalizeLocalEditHistory(message);
            return;
        }
        appendLocalEditHistory(message, entry);
        normalizeLocalEditHistory(message);
    }

    public static void addCurrentVersionToLocalEditHistory(TLRPC.Message message) {
        addCurrentVersionToLocalEditHistory(message, message != null ? message.message : null);
    }

    public static void addCurrentVersionToLocalEditHistory(TLRPC.Message message, String text) {
        appendLocalEditHistory(message, createCurrentEditHistoryEntry(message, text));
        normalizeLocalEditHistory(message);
    }

    public static void mergeLocalEditHistory(TLRPC.Message targetMessage, TLRPC.Message sourceMessage) {
        addLocalEditHistory(targetMessage, sourceMessage);
    }


    public static void readLocalParams(TLRPC.Message message, NativeByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            return;
        }
        int version = byteBuffer.readInt32(true);
        TLObject params;
        switch (version) {
            case 1:
                params = new Params_v1(message);
                break;
            case 2:
                params = new Params_v2(message);
                break;
            default:
                throw new RuntimeException("can't read params version = " + version);
        }
        params.readParams(byteBuffer, true);
        normalizeLocalEditHistory(message);
    }

    public static NativeByteBuffer writeLocalParams(TLRPC.Message message) {
        if (isEmpty(message)) {
            return null;
        }
        normalizeLocalEditHistory(message);
        TLObject params = new Params_v2(message);
        try {
            NativeByteBuffer nativeByteBuffer = new NativeByteBuffer(params.getObjectSize());
            params.serializeToStream(nativeByteBuffer);
            return nativeByteBuffer;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    static ArrayList<TLRPC.MessageEditHistoryEntry> copyEditHistory(ArrayList<TLRPC.MessageEditHistoryEntry> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        ArrayList<TLRPC.MessageEditHistoryEntry> result = new ArrayList<>(history.size());
        for (int i = 0; i < history.size(); i++) {
            TLRPC.MessageEditHistoryEntry source = history.get(i);
            if (source == null) {
                continue;
            }
            TLRPC.MessageEditHistoryEntry entry = new TLRPC.MessageEditHistoryEntry();
            entry.date = source.date;
            entry.edit_date = source.edit_date;
            entry.text = source.text;
            result.add(entry);
        }
        return result.isEmpty() ? null : result;
    }

    private static ArrayList<TLRPC.MessageEditHistoryEntry> copyAvailableEditHistory(TLRPC.Message message) {
        ArrayList<TLRPC.MessageEditHistoryEntry> history = null;
        if (message != null && message.params != null) {
            history = mergeEditHistorySequences(
                    history,
                    decodeEditHistory(message.params.get(PREVIOUS_EDIT_HISTORY_PARAM))
            );
        }
        history = mergeEditHistorySequences(
                history,
                copyEditHistory(message != null ? message.editHistory : null)
        );
        return history;
    }

    static String encodeEditHistory(ArrayList<TLRPC.MessageEditHistoryEntry> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        SerializedData serializedData = null;
        try {
            serializedData = new SerializedData();
            writeEditHistory(serializedData, history);
            return Base64.encodeToString(serializedData.toByteArray(), Base64.DEFAULT);
        } catch (Exception ignore) {
            return null;
        } finally {
            if (serializedData != null) {
                serializedData.cleanup();
            }
        }
    }

    static ArrayList<TLRPC.MessageEditHistoryEntry> decodeEditHistory(String encodedHistory) {
        if (TextUtils.isEmpty(encodedHistory)) {
            return null;
        }
        SerializedData serializedData = null;
        try {
            serializedData = new SerializedData(Base64.decode(encodedHistory, Base64.DEFAULT));
            return readEditHistory(serializedData, false);
        } catch (Exception ignore) {
            return null;
        } finally {
            if (serializedData != null) {
                serializedData.cleanup();
            }
        }
    }

    static ArrayList<TLRPC.MessageEditHistoryEntry> copyEditHistoryWithoutLastEntry(ArrayList<TLRPC.MessageEditHistoryEntry> history) {
        ArrayList<TLRPC.MessageEditHistoryEntry> result = copyEditHistory(history);
        if (result == null || result.isEmpty()) {
            return null;
        }
        result.remove(result.size() - 1);
        return result.isEmpty() ? null : result;
    }

    private static TLRPC.MessageEditHistoryEntry createLocalEditHistoryEntry(TLRPC.Message message) {
        if (message == null) {
            return null;
        }
        TLRPC.MessageEditHistoryEntry entry = new TLRPC.MessageEditHistoryEntry();
        entry.date = message.date;
        entry.edit_date = message.edit_date;
        entry.text = getPreviousVersionText(message);
        return entry;
    }

    private static TLRPC.MessageEditHistoryEntry createCurrentEditHistoryEntry(TLRPC.Message message) {
        return createCurrentEditHistoryEntry(message, message != null ? message.message : null);
    }

    private static TLRPC.MessageEditHistoryEntry createCurrentEditHistoryEntry(TLRPC.Message message, String text) {
        if (message == null) {
            return null;
        }
        TLRPC.MessageEditHistoryEntry entry = new TLRPC.MessageEditHistoryEntry();
        entry.date = message.date;
        entry.edit_date = message.edit_date;
        entry.text = text;
        return entry;
    }

    private static String getPreviousVersionText(TLRPC.Message message) {
        if (message == null) {
            return null;
        }
        if (message.params != null) {
            String previousData = message.params.get(PREVIOUS_MEDIA_PARAM);
            if (!TextUtils.isEmpty(previousData)) {
                SerializedData serializedData = null;
                try {
                    serializedData = new SerializedData(Base64.decode(previousData, Base64.DEFAULT));
                    TLRPC.MessageMedia.TLdeserialize(serializedData, serializedData.readInt32(false), false);
                    return serializedData.readString(false);
                } catch (Exception ignore) {
                } finally {
                    if (serializedData != null) {
                        serializedData.cleanup();
                    }
                }
            }
        }
        return message.message;
    }

    private static boolean isSameLocalVersion(TLRPC.Message currentMessage, TLRPC.Message previousMessage) {
        return currentMessage.edit_date == previousMessage.edit_date && TextUtils.equals(currentMessage.message, previousMessage.message);
    }

    private static boolean isSameHistoryEntry(TLRPC.MessageEditHistoryEntry first, TLRPC.MessageEditHistoryEntry second) {
        return first != null && second != null &&
                first.date == second.date &&
                first.edit_date == second.edit_date &&
                TextUtils.equals(first.text, second.text);
    }

    private static int getMatchedHistoryPrefixCount(ArrayList<TLRPC.MessageEditHistoryEntry> targetHistory, ArrayList<TLRPC.MessageEditHistoryEntry> sourceHistory) {
        if (targetHistory == null || targetHistory.isEmpty() || sourceHistory == null || sourceHistory.isEmpty()) {
            return 0;
        }
        int count = Math.min(targetHistory.size(), sourceHistory.size());
        int index = 0;
        while (index < count && isSameHistoryEntry(targetHistory.get(index), sourceHistory.get(index))) {
            index++;
        }
        return index;
    }

    private static void mergeLocalEditHistoryEntries(TLRPC.Message targetMessage, ArrayList<TLRPC.MessageEditHistoryEntry> history) {
        if (targetMessage == null || history == null || history.isEmpty()) {
            return;
        }
        int startIndex = getMatchedHistoryPrefixCount(targetMessage.editHistory, history);
        for (int i = startIndex; i < history.size(); i++) {
            appendLocalEditHistory(targetMessage, history.get(i));
        }
    }

    private static ArrayList<TLRPC.MessageEditHistoryEntry> mergeEditHistorySequences(ArrayList<TLRPC.MessageEditHistoryEntry> firstHistory, ArrayList<TLRPC.MessageEditHistoryEntry> secondHistory) {
        ArrayList<TLRPC.MessageEditHistoryEntry> first = copyEditHistory(firstHistory);
        ArrayList<TLRPC.MessageEditHistoryEntry> second = copyEditHistory(secondHistory);
        if (first == null || first.isEmpty()) {
            return second;
        }
        if (second == null || second.isEmpty()) {
            return first;
        }
        if (indexOfHistorySequence(first, second) >= 0) {
            return first;
        }
        if (indexOfHistorySequence(second, first) >= 0) {
            return second;
        }

        int firstToSecondOverlap = getHistorySuffixPrefixOverlap(first, second);
        int secondToFirstOverlap = getHistorySuffixPrefixOverlap(second, first);
        if (secondToFirstOverlap > firstToSecondOverlap) {
            ArrayList<TLRPC.MessageEditHistoryEntry> merged = second;
            appendHistoryRange(merged, first, secondToFirstOverlap);
            return merged;
        }
        if (firstToSecondOverlap > 0) {
            ArrayList<TLRPC.MessageEditHistoryEntry> merged = first;
            appendHistoryRange(merged, second, firstToSecondOverlap);
            return merged;
        }

        ArrayList<TLRPC.MessageEditHistoryEntry> merged = first;
        appendHistoryRange(merged, second, 0);
        return merged;
    }

    private static int indexOfHistorySequence(ArrayList<TLRPC.MessageEditHistoryEntry> targetHistory, ArrayList<TLRPC.MessageEditHistoryEntry> sourceHistory) {
        if (targetHistory == null || sourceHistory == null || sourceHistory.isEmpty() || targetHistory.size() < sourceHistory.size()) {
            return -1;
        }
        int lastStart = targetHistory.size() - sourceHistory.size();
        for (int start = 0; start <= lastStart; start++) {
            boolean matched = true;
            for (int i = 0; i < sourceHistory.size(); i++) {
                if (!isSameHistoryEntry(targetHistory.get(start + i), sourceHistory.get(i))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return start;
            }
        }
        return -1;
    }

    private static int getHistorySuffixPrefixOverlap(ArrayList<TLRPC.MessageEditHistoryEntry> leadingHistory, ArrayList<TLRPC.MessageEditHistoryEntry> trailingHistory) {
        if (leadingHistory == null || trailingHistory == null || leadingHistory.isEmpty() || trailingHistory.isEmpty()) {
            return 0;
        }
        int maxOverlap = Math.min(leadingHistory.size(), trailingHistory.size());
        for (int overlap = maxOverlap; overlap > 0; overlap--) {
            boolean matched = true;
            int leadingStart = leadingHistory.size() - overlap;
            for (int i = 0; i < overlap; i++) {
                if (!isSameHistoryEntry(leadingHistory.get(leadingStart + i), trailingHistory.get(i))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return overlap;
            }
        }
        return 0;
    }

    private static void appendHistoryRange(ArrayList<TLRPC.MessageEditHistoryEntry> targetHistory, ArrayList<TLRPC.MessageEditHistoryEntry> sourceHistory, int startIndex) {
        if (targetHistory == null || sourceHistory == null || sourceHistory.isEmpty()) {
            return;
        }
        for (int i = Math.max(0, startIndex); i < sourceHistory.size(); i++) {
            TLRPC.MessageEditHistoryEntry entry = sourceHistory.get(i);
            if (entry != null && (targetHistory.isEmpty() || !isSameHistoryEntry(targetHistory.get(targetHistory.size() - 1), entry))) {
                targetHistory.add(copyHistoryEntry(entry));
            }
        }
    }

    private static void normalizeLocalEditHistory(TLRPC.Message message) {
        if (message == null || message.editHistory == null || message.editHistory.isEmpty()) {
            return;
        }
        ArrayList<TLRPC.MessageEditHistoryEntry> history = dedupeHistoryByLastOccurrence(message.editHistory);
        if (history.isEmpty()) {
            message.editHistory = null;
            return;
        }
        if (hasHistoryOrderInversion(history)) {
            ArrayList<TLRPC.MessageEditHistoryEntry> originalOrder = new ArrayList<>(history);
            Collections.sort(history, (first, second) -> {
                int compare = Integer.compare(first.edit_date, second.edit_date);
                if (compare != 0) {
                    return compare;
                }
                return Integer.compare(indexOfHistoryEntry(originalOrder, first), indexOfHistoryEntry(originalOrder, second));
            });
        }
        while (history.size() > MAX_LOCAL_EDIT_HISTORY) {
            history.remove(0);
        }
        message.editHistory = history;
    }

    private static ArrayList<TLRPC.MessageEditHistoryEntry> dedupeHistoryByLastOccurrence(ArrayList<TLRPC.MessageEditHistoryEntry> history) {
        ArrayList<TLRPC.MessageEditHistoryEntry> normalized = new ArrayList<>();
        if (history == null || history.isEmpty()) {
            return normalized;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            TLRPC.MessageEditHistoryEntry entry = history.get(i);
            if (entry != null && !containsHistoryEntry(normalized, entry)) {
                normalized.add(0, copyHistoryEntry(entry));
            }
        }
        return normalized;
    }

    private static boolean hasHistoryOrderInversion(ArrayList<TLRPC.MessageEditHistoryEntry> history) {
        if (history == null || history.size() < 2) {
            return false;
        }
        int lastEditDate = history.get(0).edit_date;
        for (int i = 1; i < history.size(); i++) {
            int currentEditDate = history.get(i).edit_date;
            if (currentEditDate < lastEditDate) {
                return true;
            }
            lastEditDate = currentEditDate;
        }
        return false;
    }

    private static int indexOfHistoryEntry(ArrayList<TLRPC.MessageEditHistoryEntry> history, TLRPC.MessageEditHistoryEntry entry) {
        if (history == null || entry == null) {
            return -1;
        }
        for (int i = 0; i < history.size(); i++) {
            if (isSameHistoryEntry(history.get(i), entry)) {
                return i;
            }
        }
        return -1;
    }

    private static TLRPC.MessageEditHistoryEntry copyHistoryEntry(TLRPC.MessageEditHistoryEntry source) {
        if (source == null) {
            return null;
        }
        TLRPC.MessageEditHistoryEntry entry = new TLRPC.MessageEditHistoryEntry();
        entry.date = source.date;
        entry.edit_date = source.edit_date;
        entry.text = source.text;
        return entry;
    }

    private static boolean containsHistoryEntry(ArrayList<TLRPC.MessageEditHistoryEntry> history, TLRPC.MessageEditHistoryEntry entry) {
        if (history == null || history.isEmpty() || entry == null) {
            return false;
        }
        for (int i = 0; i < history.size(); i++) {
            if (isSameHistoryEntry(history.get(i), entry)) {
                return true;
            }
        }
        return false;
    }

    private static void appendLocalEditHistory(TLRPC.Message message, TLRPC.MessageEditHistoryEntry entry) {
        if (message == null || entry == null) {
            return;
        }
        if (message.editHistory == null) {
            message.editHistory = new ArrayList<>();
        }
        int count = message.editHistory.size();
        if (count > 0 && isSameHistoryEntry(message.editHistory.get(count - 1), entry)) {
            return;
        }
        message.editHistory.add(entry);
        while (message.editHistory.size() > MAX_LOCAL_EDIT_HISTORY) {
            message.editHistory.remove(0);
        }
    }

    private static void writeEditHistory(OutputSerializedData stream, TLRPC.Message message) {
        writeEditHistory(stream, message.editHistory);
    }

    private static void writeEditHistory(OutputSerializedData stream, ArrayList<TLRPC.MessageEditHistoryEntry> history) {
        stream.writeInt32(VECTOR_MAGIC);
        int count = history != null ? history.size() : 0;
        stream.writeInt32(count);
        for (int i = 0; i < count; i++) {
            history.get(i).serializeToStream(stream);
        }
    }

    private static void readEditHistory(InputSerializedData stream, TLRPC.Message message, boolean exception) {
        message.editHistory = readEditHistory(stream, exception);
    }

    private static ArrayList<TLRPC.MessageEditHistoryEntry> readEditHistory(InputSerializedData stream, boolean exception) {
        int magic = stream.readInt32(exception);
        if (magic != VECTOR_MAGIC) {
            if (exception) {
                throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
            }
            return null;
        }
        int count = stream.readInt32(exception);
        if (count <= 0) {
            return null;
        }
        ArrayList<TLRPC.MessageEditHistoryEntry> history = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            TLRPC.MessageEditHistoryEntry entry = TLRPC.MessageEditHistoryEntry.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (entry != null) {
                history.add(entry);
            }
        }
        return history.isEmpty() ? null : history;
    }

    private static class Params_v1 extends TLObject {

        private final static int VERSION = 1;
        final TLRPC.Message message;
        int flags = 0;

        private Params_v1(TLRPC.Message message) {
            this.message = message;
            flags |= message.voiceTranscription != null ? 1 : 0;
            flags |= message.voiceTranscriptionForce ? 2 : 0;

            flags |= message.originalLanguage != null ? 4 : 0;
            flags |= message.translatedToLanguage != null ? 8 : 0;
            flags |= message.translatedText != null ? 16 : 0;

            flags |= message.translatedPoll != null ? 32 : 0;

            flags |= message.errorAllowedPriceStars != 0 ? 64 : 0;
            flags |= message.errorNewPriceStars != 0 ? 128 : 0;

            flags |= message.translatedVoiceTranscription != null ? 256 : 0;

            flags = setFlag(flags, FLAG_10, message.summaryText != null);
            flags = setFlag(flags, FLAG_11, message.translatedSummaryText != null);
            flags = setFlag(flags, FLAG_12, message.translatedSummaryLanguage != null);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(VERSION);
            flags = message.voiceTranscriptionForce ? (flags | 2) : (flags &~ 2);
            flags = message.summarizedOpen ? (flags | 512) : (flags &~ 512);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeString(message.voiceTranscription);
            }
            stream.writeBool(message.voiceTranscriptionOpen);
            stream.writeBool(message.voiceTranscriptionFinal);
            stream.writeBool(message.voiceTranscriptionRated);
            stream.writeInt64(message.voiceTranscriptionId);

            stream.writeBool(message.premiumEffectWasPlayed);

            if ((flags & 4) != 0) {
                stream.writeString(message.originalLanguage);
            }
            if ((flags & 8) != 0) {
                stream.writeString(message.translatedToLanguage);
            }
            if ((flags & 16) != 0) {
                message.translatedText.serializeToStream(stream);
            }
            if ((flags & 32) != 0) {
                message.translatedPoll.serializeToStream(stream);
            }

            if ((flags & 64) != 0) {
                stream.writeInt64(message.errorAllowedPriceStars);
            }
            if ((flags & 128) != 0) {
                stream.writeInt64(message.errorNewPriceStars);
            }
            if ((flags & 256) != 0) {
                message.translatedVoiceTranscription.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_10)) {
                message.summaryText.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                message.translatedSummaryText.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_12)) {
                stream.writeString(message.translatedSummaryLanguage);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(true);
            if ((flags & 1) != 0) {
                message.voiceTranscription = stream.readString(exception);
            }
            message.voiceTranscriptionForce = (flags & 2) != 0;
            message.summarizedOpen = (flags & 512) != 0;
            message.voiceTranscriptionOpen = stream.readBool(exception);
            message.voiceTranscriptionFinal = stream.readBool(exception);
            message.voiceTranscriptionRated = stream.readBool(exception);
            message.voiceTranscriptionId = stream.readInt64(exception);

            message.premiumEffectWasPlayed = stream.readBool(exception);

            if ((flags & 4) != 0) {
                message.originalLanguage = stream.readString(exception);
            }
            if ((flags & 8) != 0) {
                message.translatedToLanguage = stream.readString(exception);
            }
            if ((flags & 16) != 0) {
                message.translatedText = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32) != 0) {
                message.translatedPoll = TranslateController.PollText.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 64) != 0) {
                message.errorAllowedPriceStars = stream.readInt64(exception);
            }
            if ((flags & 128) != 0) {
                message.errorNewPriceStars = stream.readInt64(exception);
            }
            if ((flags & 256) != 0) {
                message.translatedVoiceTranscription = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                message.summaryText = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                message.translatedSummaryText = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_12)) {
                message.translatedSummaryLanguage = stream.readString(exception);
            }
        }

    }

    private static class Params_v2 extends TLObject {

        private final static int VERSION = 2;
        final TLRPC.Message message;
        int flags = 0;

        private Params_v2(TLRPC.Message message) {
            this.message = message;
            flags |= message.voiceTranscription != null ? 1 : 0;
            flags |= message.voiceTranscriptionForce ? 2 : 0;

            flags |= message.originalLanguage != null ? 4 : 0;
            flags |= message.translatedToLanguage != null ? 8 : 0;
            flags |= message.translatedText != null ? 16 : 0;

            flags |= message.translatedPoll != null ? 32 : 0;

            flags |= message.errorAllowedPriceStars != 0 ? 64 : 0;
            flags |= message.errorNewPriceStars != 0 ? 128 : 0;

            flags |= message.translatedVoiceTranscription != null ? 256 : 0;

            flags = setFlag(flags, FLAG_10, message.summaryText != null);
            flags = setFlag(flags, FLAG_11, message.translatedSummaryText != null);
            flags = setFlag(flags, FLAG_12, message.translatedSummaryLanguage != null);
            flags = setFlag(flags, FLAG_13, hasLocalEditHistory(message));
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(VERSION);
            flags = message.voiceTranscriptionForce ? (flags | 2) : (flags &~ 2);
            flags = message.summarizedOpen ? (flags | 512) : (flags &~ 512);
            flags = setFlag(flags, FLAG_13, hasLocalEditHistory(message));
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeString(message.voiceTranscription);
            }
            stream.writeBool(message.voiceTranscriptionOpen);
            stream.writeBool(message.voiceTranscriptionFinal);
            stream.writeBool(message.voiceTranscriptionRated);
            stream.writeInt64(message.voiceTranscriptionId);

            stream.writeBool(message.premiumEffectWasPlayed);

            if ((flags & 4) != 0) {
                stream.writeString(message.originalLanguage);
            }
            if ((flags & 8) != 0) {
                stream.writeString(message.translatedToLanguage);
            }
            if ((flags & 16) != 0) {
                message.translatedText.serializeToStream(stream);
            }
            if ((flags & 32) != 0) {
                message.translatedPoll.serializeToStream(stream);
            }

            if ((flags & 64) != 0) {
                stream.writeInt64(message.errorAllowedPriceStars);
            }
            if ((flags & 128) != 0) {
                stream.writeInt64(message.errorNewPriceStars);
            }
            if ((flags & 256) != 0) {
                message.translatedVoiceTranscription.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_10)) {
                message.summaryText.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                message.translatedSummaryText.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_12)) {
                stream.writeString(message.translatedSummaryLanguage);
            }
            if (hasFlag(flags, FLAG_13)) {
                writeEditHistory(stream, message);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(true);
            if ((flags & 1) != 0) {
                message.voiceTranscription = stream.readString(exception);
            }
            message.voiceTranscriptionForce = (flags & 2) != 0;
            message.summarizedOpen = (flags & 512) != 0;
            message.voiceTranscriptionOpen = stream.readBool(exception);
            message.voiceTranscriptionFinal = stream.readBool(exception);
            message.voiceTranscriptionRated = stream.readBool(exception);
            message.voiceTranscriptionId = stream.readInt64(exception);

            message.premiumEffectWasPlayed = stream.readBool(exception);

            if ((flags & 4) != 0) {
                message.originalLanguage = stream.readString(exception);
            }
            if ((flags & 8) != 0) {
                message.translatedToLanguage = stream.readString(exception);
            }
            if ((flags & 16) != 0) {
                message.translatedText = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32) != 0) {
                message.translatedPoll = TranslateController.PollText.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 64) != 0) {
                message.errorAllowedPriceStars = stream.readInt64(exception);
            }
            if ((flags & 128) != 0) {
                message.errorNewPriceStars = stream.readInt64(exception);
            }
            if ((flags & 256) != 0) {
                message.translatedVoiceTranscription = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                message.summaryText = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                message.translatedSummaryText = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_12)) {
                message.translatedSummaryLanguage = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_13)) {
                readEditHistory(stream, message, exception);
            }
        }
    }
}
