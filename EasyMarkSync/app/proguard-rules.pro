# === 通用 ===
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
# 枚举按名称保留（序列化/对比策略依赖 name）
-keepclassmembers enum * { *; }
# WebView JS 接口
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-dontwarn kotlinx.coroutines.**

# === Room（实体 + 生成的 DAO 实现）===
-keep class me.tshine.easymarksync.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }

# === OkHttp / Okio（自带 consumer 规则，仅抑制警告）===
-dontwarn okhttp3.**
-dontwarn okio.**

# === flexmark（扩展经反射/ServiceLoader 发现，全保留）===
-keep class com.vladsch.flexmark.** { *; }
-dontwarn com.vladsch.flexmark.**

# === 加密存储（自带 consumer 规则）===
-dontwarn androidx.security.**
# Tink（security-crypto 传递依赖）引用 errorprone 注解，仅编译期使用
-dontwarn com.google.errorprone.annotations.**
