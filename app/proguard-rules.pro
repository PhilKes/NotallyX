# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile
-dontobfuscate
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable
-printmapping obfuscation/mapping.txt

-keep class ** extends androidx.navigation.Navigator
-keep class ** implements org.ocpsoft.prettytime.TimeUnit

# SQLCipher
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.* { *; }

# SimpleXML
-keepattributes Signature
-keepattributes *Annotation
-keep interface org.simpleframework.xml.core.Label {
   public *;
}
-keep class * implements org.simpleframework.xml.core.Label {
   public *;
}
-keep interface org.simpleframework.xml.core.Parameter {
   public *;
}
-keep class * implements org.simpleframework.xml.core.Parameter {
   public *;
}
-keep interface org.simpleframework.xml.core.Extractor {
   public *;
}
-keep class * implements org.simpleframework.xml.core.Extractor {
   public *;
}

-keep class * implements java.io.Serializable