# ---------------------------------------------------------------- gomobile / Xray core
# Keep gomobile bindings for the Xray core (JNI calls into Go)
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-dontwarn libv2ray.**
-dontwarn go.**

# ---------------------------------------------------------------- kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.nobodyiran.nobodyvpn.**$$serializer { *; }
-keepclassmembers class com.nobodyiran.nobodyvpn.** {
    *** Companion;
}
-keepclasseswithmembers class com.nobodyiran.nobodyvpn.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------- misc
# Keep enum values (used in reflection by some androidx components)
-keepclassmembers enum com.nobodyiran.nobodyvpn.** {
    *;
}
# ZXing / CameraX ship their own consumer rules; silence optional deps
-dontwarn com.google.zxing.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
