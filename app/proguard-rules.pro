# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============ Glide ============
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ============ MediaStore / ContentProvider ============
-keep class android.provider.MediaStore { *; }
-keep class android.provider.MediaStore$Audio { *; }
-keep class android.provider.MediaStore$Audio$Media { *; }

# ============ Custom Views ============
-keep class com.codetrio.spatialflow.ui.custom.** { *; }

# ============ Palette ============
-keep class androidx.palette.graphics.** { *; }

# ============ Navigation ============
-keep class androidx.navigation.** { *; }

# ============ Lifecycle ============
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.LiveData { *; }

# ============ Lyrics & JSON Models ============
# Keep lyrics data classes (Gson needs field names to match JSON)
-keep class com.codetrio.spatialflow.data.lyrics.** { *; }
-keepattributes *Annotation*
-keepattributes Signature

# ============ Kuromoji Japanese Tokenizer ============
-keep class com.atilika.kuromoji.** { *; }
-dontwarn com.atilika.kuromoji.**

# ============ Retrofit & OkHttp ============
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# ============ Gson ============
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# ============ Jaudiotagger ============
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# ============ InnerTube & Models ============
-keep class com.codetrio.spatialflow.data.innertube.** { *; }
-keep class com.codetrio.spatialflow.model.** { *; }

# ============ NewPipe Extractor & Jsoup ============
-keep class org.schabi.newpipe.extractor.** { *; }
-keep interface org.schabi.newpipe.extractor.** { *; }
-keepclassmembers class org.schabi.newpipe.extractor.** { *; }
-keeppackagenames org.schabi.newpipe.extractor**
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ============ FFmpegKit ============
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# ============ Rhino (org.mozilla.javascript) ============
-keep class org.mozilla.javascript.** { *; }
-keep interface org.mozilla.javascript.** { *; }
-keep enum org.mozilla.javascript.** { *; }
-keepclassmembers class org.mozilla.javascript.** { *; }
-keepclassmembers enum org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keepclassmembers class org.mozilla.classfile.** { *; }
-keep class * extends org.mozilla.javascript.VMBridge { <init>(); }
-keeppackagenames org.mozilla.javascript**
-dontwarn java.beans.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**

# ============ SLF4J ============
-dontwarn org.slf4j.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

# ============ Ktor ============
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ============ Kotlinx Serialization ============
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembernames class * {
    @kotlinx.serialization.SerialName <fields>;
}

# ============ Media3 / ExoPlayer ============
-dontwarn androidx.media3.**

# ============ Kyant Backdrop & Shapes ============
-keep class com.kyant.backdrop.** { *; }
-dontwarn com.kyant.backdrop.**
-keep class com.kyant.shapes.** { *; }
-dontwarn com.kyant.shapes.**

# ============ Haze ============
-keep class dev.chrisbanes.haze.** { *; }
-dontwarn dev.chrisbanes.haze.**

# ============ AndroidX Security Crypto & KeyStore ============
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
-keep class com.codetrio.spatialflow.data.sourcepool.SourcePoolSecurity {
    public static *** *(...);
}
-keep class com.codetrio.spatialflow.data.sourcepool.SourcePoolStatusResponse { *; }
-keep class com.codetrio.spatialflow.data.sourcepool.SourcesFeedResponse { *; }
-keep class com.codetrio.spatialflow.data.sourcepool.DiscoveryResponse { *; }
-keep class com.codetrio.spatialflow.data.sourcepool.ServiceSources { *; }
-keep class com.codetrio.spatialflow.data.sourcepool.SourceCategoryStatus { *; }
-keep class com.codetrio.spatialflow.data.sourcepool.SourceApiEntry { *; }
-keep class com.codetrio.spatialflow.data.sourcepool.SourceAccountEntry { *; }
-keep class com.codetrio.spatialflow.data.sourcepool.SourcePoolSummary { *; }


