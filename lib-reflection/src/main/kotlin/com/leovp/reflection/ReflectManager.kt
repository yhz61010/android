@file:Suppress("unused")

package com.leovp.reflection

import kotlin.reflect.KClass
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure

/**
 * Author: Michael Leo
 * Date: 2022/7/28 09:49
 */
class ReflectManager private constructor() {

    // https://stackoverflow.com/a/41905907/1685062
    private lateinit var type: KClass<*>

    // https://stackoverflow.com/a/41905907/1685062
    private lateinit var obj: Any

    private constructor(type: KClass<*>, obj: Any? = null) : this() {
        this.type = type
        this.obj = obj ?: type
    }

    companion object {
        /**
         * @param className The name of class.
         * @param classLoader The loader of class.
         * @return The single [ReflectManager] instance.
         * @throws ReflectException If reflect unsuccessfully.
         */
        @Suppress("unused")
        fun reflect(className: String, classLoader: ClassLoader? = null): ReflectManager {
            try {
                val clazz = if (classLoader == null)
                    Class.forName(className)
                else
                    Class.forName(className, true, classLoader)
                return reflect(clazz.kotlin)
            } catch (le: LinkageError) {
                throw ReflectException(le)
            } catch (ee: ExceptionInInitializerError) {
                throw ReflectException(ee)
            } catch (cnfe: ClassNotFoundException) {
                throw ReflectException(cnfe)
            }
        }

        /**
         * Reflect the class.
         *
         * @param kclass The KClass.
         * @return The single [ReflectManager] instance.
         */
        @Suppress("WeakerAccess")
        fun reflect(kclass: KClass<*>): ReflectManager {
            return ReflectManager(kclass)
        }

        /**
         * Reflect the class.
         *
         * @param clazz The class.
         * @return The single [ReflectManager] instance.
         */
        @Suppress("WeakerAccess")
        fun reflect(clazz: Class<*>): ReflectManager {
            return ReflectManager(clazz.kotlin)
        }

        /**
         * Reflect the class.
         *
         * @param obj The object.
         * @return The single [ReflectManager] instance.
         */
        fun <T : Any> reflect(obj: T): ReflectManager {
            return ReflectManager(obj::class, obj)
        }
    }

    // =================================
    // ========== newInstance ==========
    // =================================

    /**
     * Create and initialize a new instance.
     *
     * @param args The args.
     * @return the single [ReflectManager] instance
     */
    @Suppress("WeakerAccess")
    fun newInstance(vararg args: Any? = arrayOfNulls<Any>(0)): ReflectManager {
        val types = getArgsType(*args)
        try {
            for (constructor in type.constructors) {
                if (matchParameterTypes(constructor.parameters.map { it.type.jvmErasure.java }.toTypedArray(), types)) {
                    if (!constructor.isAccessible) constructor.isAccessible = true
                    return ReflectManager(type, constructor.call(*args))
                }
            }
            throw ReflectException("Not found any constructor.")
        } catch (e: NoSuchMethodException) {
            throw ReflectException(e)
        }
    }

    private fun getArgsType(vararg args: Any?): Array<KClass<*>?> {
        val result: Array<KClass<*>?> = arrayOfNulls(args.size)
        for ((i, value) in args.withIndex()) {
            result[i] = (value?.javaClass ?: Unit::class.java).kotlin
        }
        return result
    }

    private fun matchParameterTypes(declaredTypes: Array<Class<*>?>, actualTypes: Array<KClass<*>?>): Boolean {
        return if (declaredTypes.size == actualTypes.size) {
            for (i in actualTypes.indices) {
                val actualType: Class<*>? = wrapper(actualTypes[i]?.java)
                val declaredType: Class<*>? = wrapper(declaredTypes[i])
                if (actualTypes[i] == Unit::class.java ||
                    (actualType != null && declaredType?.isAssignableFrom(actualType) == true)) {
                    continue
                }
                return false
            }
            true
        } else {
            false
        }
    }

    private fun wrapper(type: Class<*>?): Class<*>? {
        if (type == null) {
            return null
        }
        return if (type.isPrimitive) {
            when (type) {
                Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
                Char::class.javaPrimitiveType -> Char::class.javaObjectType
                Byte::class.javaPrimitiveType -> Byte::class.javaObjectType
                Short::class.javaPrimitiveType -> Short::class.javaObjectType
                Int::class.javaPrimitiveType -> Int::class.javaObjectType
                Float::class.javaPrimitiveType -> Float::class.javaObjectType
                Long::class.javaPrimitiveType -> Long::class.javaObjectType
                Double::class.javaPrimitiveType -> Double::class.javaObjectType
                Void.TYPE -> Void::class.javaObjectType
                else -> throw IllegalArgumentException("Unknown primitive type: $type")
            }
        } else type
    }

    /**
     * Get the result.
     *
     * @param <T> The value type.
     * @return the result
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(): T {
        return obj as T
    }

    // =================================
    // ========== Exception ==========
    // =================================

    class ReflectException : RuntimeException {
        constructor(cause: Throwable?) : super(cause)

        @Suppress("unused")
        constructor(message: String?, cause: Throwable? = null) : super(message, cause)
    }
}
