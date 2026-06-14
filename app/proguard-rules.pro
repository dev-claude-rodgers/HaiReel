# Gson: モデルクラスのフィールド名を保持（JSON デシリアライズに必要）
-keep class com.rodgers.routist.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# BuildConfig（APIキーアクセスに使用）
-keep class com.rodgers.routist.BuildConfig { *; }

# ViewBinding（R8がクラスを削除しないよう保護）
-keep class com.rodgers.routist.databinding.** { *; }

# Room Entity（フィールド名を保持）
-keep class com.rodgers.routist.model.WorkRecord { *; }
-keep class com.rodgers.routist.model.TenkoRecord { *; }

# Enum（R8がordinal/nameを削除しないよう保護）
-keepclassmembers enum * { *; }

# Coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
