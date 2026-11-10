// @Volatile из ui/motion/Portal.kt. Wasm однопоточный — аннотация ничего не значит.
package ru.finney.pet.ui.motion

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
internal annotation class Volatile
