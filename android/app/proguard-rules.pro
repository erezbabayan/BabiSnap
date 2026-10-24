# Rhino: runs parser.js via reflection/interpreter; keep it intact.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
# App classes reached from JS or via manifest are kept by AAPT; keep JSON/model fields used by name.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
# Google Sign-In / Play services ship consumer rules in their AARs; silence optional refs only.
-dontwarn com.google.android.gms.**
# WorkManager workers are instantiated by class name.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
# No Gson in this app (org.json + JSON strings from Rhino); add Gson rules here if it is ever introduced.
# Parser bridge: Java -> JS only via strings (HebrewParser.call); no Java objects are exposed to parser.js.
-keep class com.babisnap.app.HebrewParser { public *; }
# Tink (via androidx.security EncryptedSharedPreferences): optional compile-time annotations.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
