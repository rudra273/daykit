# DayKit ProGuard / R8 rules.
# Release builds are minified + obfuscated (M1). Keep only what breaks under
# shrinking/obfuscation because it is reached via reflection, JNI, or codegen.

# ---- Kotlin metadata / coroutines ----
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-dontwarn kotlinx.coroutines.**

# ---- Room (entities/DAOs are referenced via generated code + reflection) ----
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# ---- SQLCipher (net.zetetic) — loads native library, JNI + reflection ----
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.zetetic.database.**
-dontwarn net.sqlcipher.**

# ---- Google Tink (vault streaming crypto) — key managers via reflection ----
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.api.client.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# ---- argon2kt (JNI-backed password hashing) ----
-keep class com.lambdapioneer.argon2kt.** { *; }
-dontwarn com.lambdapioneer.argon2kt.**

# ---- Google Play Services auth / Identity (Drive backup) ----
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ---- media3 / ExoPlayer (custom DataSource loaded reflectively in places) ----
-dontwarn androidx.media3.**
-keep class androidx.media3.**  { *; }

# ---- Our custom media DataSource is referenced by media3 factories ----
-keep class com.daykit.feature.filelocker.ui.VaultMediaDataSource { *; }
-keep class com.daykit.feature.filelocker.ui.VaultMediaDataSource$* { *; }

# ---- org.json (used by backup) is part of the platform; nothing to keep ----

# Preserve line numbers for readable crash reports, but hide original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
