# OpenCV's Java wrappers are called from JNI by name; R8 must not rename or strip them.
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**
