# Keep AIDL-generated classes for Google Feed bridge
-keep class com.android.launcher3.** { *; }
-keep interface com.android.launcher3.** { *; }

# Keep Hilt
-keepclasseswithmembers class * { @dagger.hilt.* <methods>; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Keep Serializable models
-keepclassmembers class com.launcher360v2.** implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.launcher360v2.** {
    *** Companion;
}
-keepclasseswithmembers class com.launcher360v2.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Coil
-dontwarn coil.**
