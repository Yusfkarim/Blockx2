# -------------------------------------------------------------------- coding: utf-8
# ProGuard/R8 rules — BlockX LaAbrah (release + bundle).
#
# R8 full mode is active; these rules protect the classes the OS and native/NDK
# runtimes reach by name (reflection, manifest registration, AIDL, JNI, Room/Hilt
# generated owners and the androidx datastore/serialization holders). Everything
# else stays minified — nothing here enlarges the artifact meaningfully.
# --------------------------------------------------------------------

# ── Platform-bound service & receiver entry points (manifest-contract classes).
-keep class com.agon.app.accessibility.ShieldAccessibilityService { *; }
-keep class com.agon.app.vpn.FamilyVpnService { *; }
-keep class com.agon.app.services.** { *; }
-keep class com.agon.app.admin.** { *; }
# Settings-protection surfaces, watchdog scheduler glue, device-admin receiver chain.
-keep class com.agon.app.settingsprotection.** { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keepclassmembers class * extends android.content.BroadcastReceiver { public <init>(); }
-keep class com.agon.app.ShieldApplication { *; }
-keep class com.agon.app.MainActivity { *; }
-keep class com.agon.app.BlockedAppActivity { *; }
-keep class com.agon.app.BlockedWebsiteActivity { *; }
-keep class com.agon.app.settingsprotection.ui.PinLockActivity { *; }

# ── JNI / native-bridge carriers (page-aligned .so holders; DataStore counter lib).
# (The native libraries themselves live uncompressed-aligned inside the bundle;
# these keeps only guard the Java surfaces they bind.)
-keepclasseswithmembers class * {
    native <methods>;
}

# ── DataStore + Robot/serialization surface the engine writes through.
-keep class com.agon.app.data.model.** { *; }
-keep class com.agon.app.blocklist.data.* { *; }
-keepclassmembers class com.agon.app.blocklist.data.*Entity { <fields>; }

# ── kotlinx-serialization: generated companions + accessors.
-keepclasseswithmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers @kotlinx.serialization.Serializable class * { *; }

# ── Metadata needed by annotation-driven components (Hilt/Room/House-keeping).
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# ── Third-party noise that R8 legitimately does not need warnings for.
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn org.bouncycastle.jsse.**
