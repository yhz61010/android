@file:Suppress("unused")

package com.leovp.reflection

import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import java.util.*

/**
 * Author: Michael Leo
 * Date: 2022/7/28 09:49
 */
class ReflectManager private constructor() {

    private lateinit var type: Class<*>
    private lateinit var obj: Any

    private constructor(type: Class<*>, obj: Any? = null) : this() {
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
                val clazz = if (classLoader == null) Class.forName(className) else Class.forName(className, true, classLoader)
                return reflect(clazz)
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
         * @param clazz The class.
         * @return The single [ReflectManager] instance.
         */
        @Suppress("WeakerAccess")
        fun reflect(clazz: Class<*>): ReflectManager {
            return ReflectManager(clazz)
        }

        /**
         * Reflect the class.
         *
         * @param obj The object.
         * @return The single [ReflectManager] instance.
         */
        fun reflect(obj: Any?): ReflectManager {
            return ReflectManager((if (obj == null) Any::class else obj::class).java, obj)
        }
    }

    // =================================
    // ========== newInstance ==========
    // =================================

    /**
     * Create and initialize a new instance.
     *
     * @return the single [ReflectManager] instance
     */
    @Suppress("unused")
    fun newInstance(): ReflectManager {
        return newInstance(*arrayOfNulls<Any>(0))
    }

    /**
     * Create and initialize a new instance.
     *
     * @param args The args.
     * @return the single [ReflectManager] instance
     */
    @Suppress("WeakerAccess")
    fun newInstance(vararg args: Any?): ReflectManager {
        val types = getArgsType(*args)
        return try {
            val constructor = type.getDeclaredConstructor(*types)
            newInstance(constructor, *args)
        } catch (e: NoSuchMethodException) {
            val list: MutableList<Constructor<*>> = ArrayList()
            for (constructor in type.declaredConstructors) {
                if (match(constructor.parameterTypes, types)) {
                    list.add(constructor)
                }
            }
            if (list.isEmpty()) {
                throw ReflectException(e)
            } else {
                sortConstructors(list)
                newInstance(list[0], *args)
            }
        }
    }

    private fun getArgsType(vararg args: Any?): Array<Class<*>?> {
        val result: Array<Class<*>?> = arrayOfNulls(args.size)
        for ((i, value) in args.withIndex()) {
            result[i] = value?.javaClass ?: NULL::class.java
        }
        return result
    }

    private fun sortConstructors(list: List<Constructor<*>>) {
        Collections.sort(list, object : Comparator<Constructor<*>> {
            override fun compare(o1: Constructor<*>, o2: Constructor<*>): Int {
                val types1 = o1.parameterTypes
                val types2 = o2.parameterTypes
                val len = types1.size
                for (i in 0 until len) {
                    if (types1[i] != types2[i]) {
                        val cls1: Class<*>? = wrapper(types1[i])
                        val cls2: Class<*>? = wrapper(types2[i])
                        return if (cls2 != null && cls1?.isAssignableFrom(cls2) == true) 1 else -1
                    }
                }
                return 0
            }
        })
    }

    private fun newInstance(constructor: Constructor<*>, vararg args: Any?): ReflectManager {
        return try {
            ReflectManager(
                constructor.declaringClass,
                accessible(constructor)!!.newInstance(*args)
            )
        } catch (e: Exception) {
            throw ReflectException(e)
        }
    }

    private fun <T : AccessibleObject?> accessible(accessible: T?): T? {
        if (accessible == null) return null
        if (accessible is Member) {
            val member = accessible as Member
            if (Modifier.isPublic(member.modifiers)
                && Modifier.isPublic(member.declaringClass.modifiers)) {
                return accessible
            }
        }
        if (!accessible.isAccessible) accessible.isAccessible = true
        return accessible
    }

    private fun match(declaredTypes: Array<Class<*>?>, actualTypes: Array<Class<*>?>): Boolean {
        return if (declaredTypes.size == actualTypes.size) {
            for (i in actualTypes.indices) {
                val actualType: Class<*>? = wrapper(actualTypes[i])
                val declaredType: Class<*>? = wrapper(declaredTypes[i])
                if (actualTypes[i] == NULL::class.java ||
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
        } else if (type.isPrimitive) {
            if (Boolean::class.javaPrimitiveType == type) {
                return Boolean::class.javaObjectType
            } else if (Int::class.javaPrimitiveType == type) {
                return Int::class.javaObjectType
            } else if (Long::class.javaPrimitiveType == type) {
                return Long::class.javaObjectType
            } else if (Short::class.javaPrimitiveType == type) {
                return Short::class.javaObjectType
            } else if (Byte::class.javaPrimitiveType == type) {
                return Byte::class.javaObjectType
            } else if (Double::class.javaPrimitiveType == type) {
                return Double::class.javaObjectType
            } else if (Float::class.javaPrimitiveType == type) {
                return Float::class.javaObjectType
            } else if (Char::class.javaPrimitiveType == type) {
                return Char::class.javaObjectType
            } else if (Void.TYPE == type) {
                return Void::class.javaObjectType
            }
        }
        return type
    }

    /**
     * Get the result.
     *
     * @param <T> The value type.
     * @return the result
     */
    fun <T> get(): T {
        @Suppress("UNCHECKED_CAST")
        return obj as T
    }

    private class NULL

    // =================================
    // ========== Exception ==========
    // =================================

    class ReflectException : RuntimeException {
        constructor(cause: Throwable?) : super(cause)

        @Suppress("unused")
        constructor(message: String?, cause: Throwable? = null) : super(message, cause)
    }
}
