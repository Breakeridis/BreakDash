# MapLibre ships its JNI keep rules. The radio bridge uses raw Binder, not reflection.
-keepattributes Signature,InnerClasses,EnclosingMethod
-dontwarn java.lang.invoke.StringConcatFactory
