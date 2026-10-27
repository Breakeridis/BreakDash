# MapLibre ships its JNI keep rules. The radio bridge uses raw Binder, not reflection.
-keepattributes Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-dontwarn java.lang.invoke.StringConcatFactory
