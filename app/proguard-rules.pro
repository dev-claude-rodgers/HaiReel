# Kotlin メタデータ・アノテーション保持
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Gson: モデルクラスのフィールド名を保持（JSON デシリアライズに必要）
-keep class com.rodgers.routist.model.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# BuildConfig（APIキーアクセスに使用）
-keep class com.rodgers.routist.BuildConfig { *; }

# ViewBinding（R8がクラスを削除しないよう保護）
-keep class com.rodgers.routist.databinding.** { *; }

# Room Entity・DAO（フィールド名・クエリ名を保持）
-keep class com.rodgers.routist.model.WorkRecord { *; }
-keep class com.rodgers.routist.model.TenkoRecord { *; }
-keep class com.rodgers.routist.db.** { *; }

# Enum（R8がordinal/nameを削除しないよう保護）
-keepclassmembers enum * { *; }

# Coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Hilt（DI 生成クラスを保護）
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}

# Firebase Crashlytics（スタックトレース保持）
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Security Crypto（EncryptedSharedPreferences）
-keep class androidx.security.crypto.** { *; }
