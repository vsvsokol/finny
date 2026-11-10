// Аннотации Android и Room, которыми размечены общие исходники app/. На вебе они
// ничего не делают — нужны только чтобы те же файлы компилировались без правок.

@file:Suppress("unused")

package androidx.annotation

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.TYPE)
annotation class DrawableRes

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.TYPE)
annotation class RawRes
