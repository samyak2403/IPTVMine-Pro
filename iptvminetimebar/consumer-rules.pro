# Keep the public time bar views and their View constructors so they survive R8 in release
# builds (they are inflated from XML and bound by Media3 via the TimeBar interface).
-keep class com.samyak.iptvminetimebar.** { *; }
