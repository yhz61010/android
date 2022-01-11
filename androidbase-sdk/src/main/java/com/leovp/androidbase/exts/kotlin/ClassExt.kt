@file:Suppress("unused")

package com.leovp.androidbase.exts.kotlin

/** Get all field names */
val <T> Class<out T>.fieldsName get() = Array(fields.size) { i -> fields[i].name }

/** Get all declared field names */
val <T> Class<out T>.declaredFieldsName get() = Array(declaredFields.size) { i -> declaredFields[i].name }

/** Get all method names */
val <T> Class<out T>.methodsName get() = Array(methods.size) { i -> methods[i].name }

/** Get all declared method names */
val <T> Class<out T>.declaredMethodsName get() = Array(declaredMethods.size) { i -> declaredMethods[i].name }