# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class jp.co.nse.worker.data.** {
    *** Companion;
}
-keepclasseswithmembers class jp.co.nse.worker.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
