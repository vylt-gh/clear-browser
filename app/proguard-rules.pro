# R8 rules for release builds.
#
# The debug build relies on no reflection, but two things do need declaring, and
# both fail in the same silent way if missed: a stripped class shows up as
# "class not found" at runtime, and a stripped callback simply never fires.

# WebView resolves the JS interfaces and bridge by name, so the annotated members
# and their declaring classes must survive shrinking.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class dev.clearbrowser.core.engine.WebContainer { *; }

# androidx.webkit is consumed reflectively for feature detection in places.
-keep class androidx.webkit.** { *; }
-dontwarn androidx.webkit.**

# Kotlin metadata is used by reflection in serialization-free code paths and is
# cheap to keep relative to debugging value lost.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations

# The color pipeline is pure and unit-tested; the tests reference it directly, so
# stripping its internals would make the tests untestable against a release build.
-keep class dev.clearbrowser.core.color.** { *; }
