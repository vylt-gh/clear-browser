# R8 rules for release builds.

-keep class dev.clearbrowser.core.engine.WebContainer { *; }

# Kotlin metadata is used by reflection in serialization-free code paths and is
# cheap to keep relative to debugging value lost.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations
