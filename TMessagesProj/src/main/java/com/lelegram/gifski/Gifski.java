package com.lelegram.gifski;

import android.graphics.Bitmap;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

public final class Gifski {
    static {
        System.loadLibrary("gifski_jni");
    }

    private long handle;

    public static final class Settings {
        private int width;
        private int height;
        private int quality = 90;
        private boolean fast;
        private short repeat = -1;

        public Settings() {
        }

        public Settings(int width, int height, @IntRange(from = 1, to = 100) int quality, boolean fast, short repeat) {
            this.width = width;
            this.height = height;
            this.quality = quality;
            this.fast = fast;
            this.repeat = repeat;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        @IntRange(from = 1, to = 100)
        public int getQuality() {
            return quality;
        }

        public void setQuality(@IntRange(from = 1, to = 100) int quality) {
            this.quality = quality;
        }

        public boolean isFast() {
            return fast;
        }

        public void setFast(boolean fast) {
            this.fast = fast;
        }

        public short getRepeat() {
            return repeat;
        }

        public void setRepeat(short repeat) {
            this.repeat = repeat;
        }
    }

    public Gifski(Settings settings) {
        handle = nativeNew(settings.width, settings.height, settings.quality, settings.fast, settings.repeat);
        if (handle == 0) {
            throw new IllegalStateException("Failed to create gifski encoder");
        }
    }

    public void setFileOutput(@NonNull String destinationPath) {
        ensureActive();
        GifskiException.throwIfError(nativeSetFileOutput(handle, destinationPath));
    }

    public void addFrameBitmap(int frameNumber, @NonNull Bitmap bitmap, double pts) {
        ensureActive();
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            throw new IllegalArgumentException("Bitmap must be ARGB_8888");
        }
        GifskiException.throwIfError(nativeAddFrameBitmap(handle, frameNumber, bitmap, pts));
    }

    public void finish() {
        if (handle != 0) {
            GifskiException.throwIfError(nativeFinish(handle));
            handle = 0;
        }
    }

    private void ensureActive() {
        if (handle == 0) {
            throw new IllegalStateException("Gifski has already been finished");
        }
    }

    private static native long nativeNew(int width, int height, int quality, boolean fast, short repeat);

    private static native int nativeSetFileOutput(long handle, String path);

    private static native int nativeAddFrameBitmap(long handle, int frameNumber, Bitmap bitmap, double pts);

    private static native int nativeFinish(long handle);

    public enum GifskiError {
        OK(0),
        NULL_ARG(1),
        INVALID_STATE(2),
        QUANT(3),
        GIF(4),
        THREAD_LOST(5),
        NOT_FOUND(6),
        PERMISSION_DENIED(7),
        ALREADY_EXISTS(8),
        INVALID_INPUT(9),
        TIMED_OUT(10),
        WRITE_ZERO(11),
        INTERRUPTED(12),
        UNEXPECTED_EOF(13),
        ABORTED(14),
        OTHER(15);

        private final int code;

        GifskiError(int code) {
            this.code = code;
        }

        static GifskiError fromCode(int code) {
            for (GifskiError error : values()) {
                if (error.code == code) {
                    return error;
                }
            }
            return OTHER;
        }

        public boolean isOk() {
            return this == OK;
        }
    }

    public static final class GifskiException extends RuntimeException {
        private final GifskiError error;

        GifskiException(GifskiError error) {
            super("Gifski error: " + error + " (code=" + error.code + ")");
            this.error = error;
        }

        public GifskiError getError() {
            return error;
        }

        static void throwIfError(int code) {
            GifskiError error = GifskiError.fromCode(code);
            if (!error.isOk()) {
                throw new GifskiException(error);
            }
        }
    }
}
