-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
    @com.google.android.gms.common.annotation.KeepName *;
}

-keep @interface androidx.annotation.Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * { @androidx.annotation.Keep *; }

-keep class org.webrtc.* { *; }
-keep class org.webrtc.audio.* { *; }
-keep class org.webrtc.voiceengine.* { *; }
-keep class org.telegram.tgnet.RequestTimeDelegate { *; }
-keep class org.telegram.tgnet.RequestDelegate { *; }

# JNI symbols and native registration rely on exact Java class/member names.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Native video and TgNet code look up these classes by exact JNI names.
-keep class org.telegram.messenger.AnimatedFileDrawableStream { *; }
-keep class org.telegram.tgnet.ConnectionsManager { *; }
-keep class org.telegram.tgnet.NativeByteBuffer { *; }
-keep class org.telegram.messenger.voip.** { *; }
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class org.telegram.ui.Stories.recorder.FfmpegAudioWaveformLoader { *; }
-keep class androidx.mediarouter.app.MediaRouteButton { *; }
-keepclassmembers class ** {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class org.telegram.utils.code.highlight.PrismaHighlighter {
    private static native <methods>;
}

# https://developers.google.com/ml-kit/known-issues#android_issues
-keep class com.google.mlkit.nl.languageid.internal.LanguageIdentificationJni { *; }

# Used by AtomicReferenceFieldUpdater and sun.misc.Unsafe.
-keepclassmembers class com.google.common.util.concurrent.AbstractFuture** {
    *** waiters;
    *** value;
    *** listeners;
    *** thread;
    *** next;
}
-keep,allowshrinking,allowobfuscation class com.google.common.util.concurrent.AbstractFuture** {
    <fields>;
}

-keep class io.nano.tex.** {*;}
-keep class org.telegram.tgnet.** { *; }

# JLatexMath macro/atom classes are loaded reflectively by Class.forName.
-keep class org.scilab.forge.jlatexmath.** { *; }
-keep class ru.noties.jlatexmath.** { *; }
-dontwarn org.scilab.forge.jlatexmath.**

-dontoptimize
-dontwarn android.support.annotation.*
-dontwarn androidx.compose.**
