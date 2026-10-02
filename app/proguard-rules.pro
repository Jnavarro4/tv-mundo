# kotlinx.serialization: conservar los serializadores generados de nuestros modelos.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.josenavarro.tvmundo.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.josenavarro.tvmundo.**$$serializer { *; }

# Decodificador FFmpeg: DefaultRenderersFactory lo carga por reflexión y usa JNI.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
