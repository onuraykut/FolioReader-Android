# Preserve generic signatures and annotations for Retrofit, Jackson, and reflection
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep JavaScript interface methods for WebViews
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Retrofit rules
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# EventBus
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Jackson & Gson JSON serialization
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <methods>;
    @com.google.gson.annotations.* <fields>;
    @com.google.gson.annotations.* <methods>;
}

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Spring ReflectionUtils
-dontwarn org.springframework.**
-keep class org.springframework.util.ReflectionUtils { *; }

# Keep Folioreader network API interfaces and models
-keep interface com.folioreader.network.** { *; }
-keep class com.folioreader.network.** { *; }
-keep class com.folioreader.model.** { *; }
-keep class org.readium.r2.shared.** { *; }
-keep class org.readium.r2.streamer.** { *; }

