-flattenpackagehierarchy
-allowaccessmodification
-keepattributes Exceptions,InnerClasses,Signature,SourceFile,LineNumberTable,
-dontskipnonpubliclibraryclassmembers
-ignorewarnings
#kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}
-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}
-keep class **.R$* {*;}
-keepclassmembers enum * { *;}

#mars
-keep class com.tencent.mars.** { *; }

#leovp
-keep class com.leovp.camera2live.** { *; }

#rx
-keep class rx.internal.util.unsafe.** { *; }
-keep class android.databinding.** { *; }

#Gson
-keepclassmembers public class com.google.gson.**
-keepclassmembers public class com.google.gson.** {public private protected *;}
-keepclassmembers public class com.project.mocha_patient.login.SignResponseData { private *; }
-keepclassmembers class sun.misc.Unsafe { *; }
-keep @interface com.google.gson.annotations.SerializedName
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

#bean
-keep class com.yidejia.net.data.bean.** { *; }
-keep class com.yidejia.net.data.db.entity.** { *; }
#greenDAO
-keepclassmembers class * extends org.greenrobot.greendao.AbstractDao {
public static java.lang.String TABLENAME;
}
-keep class **$Properties {*;}

#Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
# for DexGuard only
#-keepresourcexmlelements manifest/application/meta-data@value=GlideModule
-dontwarn com.bumptech.glide.**

# ProGuard configurations for Bugtags
-keepattributes LineNumberTable,SourceFile
-keep class com.bugtags.library.** {*;}
-dontwarn com.bugtags.library.**
-keep class io.bugtags.** {*;}
-dontwarn io.bugtags.**
-dontwarn org.apache.http.**
-dontwarn android.net.http.AndroidHttpClient


# ==================================================================================================
# EventBus ----- Start
# http://greenrobot.org/eventbus/documentation/proguard/
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Only required if you use AsyncExecutor
-keepclassmembers class * extends org.greenrobot.eventbus.util.ThrowableFailureEvent {
    <init>(java.lang.Throwable);
}
# EventBus ----- End

# Retrofit ----- Start
#-keepclassmembernames,allowobfuscation interface * {
#    @retrofit2.http.* <methods>;
#}
#-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
#

-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes Exceptions
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
# Retrofit ----- End

# okhttp ----- Start
-dontwarn com.squareup.okhttp.**
-keep class com.squareup.okhttp.{*;}

-dontwarn com.squareup.okhttp3.**
-keep class com.squareup.okhttp3.** { *;}
-dontwarn okio.**

-dontwarn okhttp3.logging.**
-keep class okhttp3.internal.**{*;}
# okhttp ----- End

# rxjava rxandroid ----- Start
-dontwarn sun.misc.**
-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
    long producerIndex;
    long consumerIndex;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}
-dontnote rx.internal.util.PlatformDependent
# rxjava rxandroid ----- End

# Gson ----- Start
-keep class com.google.gson.** {*;}
-keep class com.google.**{*;}
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.examples.android.model.** { *; }
# Gson ----- End

# netty ----- Start
-keepattributes Signature,InnerClasses
-keepclasseswithmembers class io.netty.** {
    *;
}
-dontwarn io.netty.**
-dontwarn sun.**
# netty ----- End

# HuaWei - Push - Start
-ignorewarnings
-keep class com.huawei.agconnect.**{*;}
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keep class com.hianalytics.android.**{*;}
-keep class com.huawei.updatesdk.**{*;}
-keep class com.huawei.hms.**{*;}
# HuaWei - Push - End

# OPPO - Push - Start
-keep public class * extends android.app.Service
-keep class com.heytap.msp.** { *;}
# OPPO - Push - End