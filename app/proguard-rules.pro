# R8 pravila za release build (isMinifyEnabled = true u app/build.gradle.kts).
# Retrofit, OkHttp, Room, Hilt, Coil, Firebase i Compose donose sopstvena (consumer) pravila -
# ovde je samo ono što je specifično za ovu aplikaciju.

# Bolji stack trace-ovi u Play Console-u (mapping.txt ih prevodi nazad u prava imena).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Gson ---
# Svi JSON modeli (backend odgovori i Room/SharedPreferences keš koji ide preko Gson-a) se pune
# refleksijom: R8 ne sme da preimenuje ni ukloni polja, inače parsiranje tiho vrati prazne/null
# vrednosti samo u release build-u.
-keep class dev.milinko.movielens.data.remote.** { *; }
-keep class dev.milinko.movielens.data.auth.** { *; }

# Generički tipovi (TypeToken<List<...>> u UserRepository/RecentSearchesStore) traže Signature.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- WebView JavaScript most (YouTube plejer za trejlere) ---
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.** { *; }
