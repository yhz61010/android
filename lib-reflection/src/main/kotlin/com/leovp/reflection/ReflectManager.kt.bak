@file:Suppress("unused")

package com.leovp.reflection

import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
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
                val clazz = if (classLoader == null)
                    Class.forName(className)
                else
                    Class.forName(className, true, classLoader)
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
            return ReflectManager(obj?.javaClass ?: Any::class.java, obj)
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
        return newInstance(null)
    }

    /**
     * Create and initialize a new instance.
     *
     * @param args The args.
     * @return the single [ReflectManager] instance
     */
    @Suppress("WeakerAccess")
    fun newInstance(vararg args: Any?): ReflectManager {
        val types = getArgsType(args)
        return try {
            val constructor = type().getDeclaredConstructor(*types)
            newInstance(constructor, *args)
        } catch (e: NoSuchMethodException) {
            val list: MutableList<Constructor<*>> = ArrayList()
            for (constructor in type().declaredConstructors) {
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

    ///////////////////////////////////////////////////////////////////////////
    // field
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // field
    ///////////////////////////////////////////////////////////////////////////
    /**
     * Get the field.
     *
     * @param name The name of field.
     * @return the single [ReflectManager] instance
     */
    @Suppress("unused")
    fun field(name: String): ReflectManager? {
        return try {
            val field = getField(name)
            ReflectManager(field.type, field[obj])
        } catch (e: IllegalAccessException) {
            throw ReflectException(e)
        }
    }

    /**
     * Set the field.
     *
     * @param name  The name of field.
     * @param value The value.
     * @return the single [ReflectManager] instance
     */
    @Suppress("unused")
    fun field(name: String, value: Any): ReflectManager? {
        return try {
            val field = getField(name)
            field[obj] = unwrap(value)
            this
        } catch (e: Exception) {
            throw ReflectException(e)
        }
    }

    private fun getField(name: String): Field {
        val field = getAccessibleField(name)
        if (field.modifiers and Modifier.FINAL == Modifier.FINAL) {
            try {
                val modifiersField = Field::class.java.getDeclaredField("modifiers")
                modifiersField.isAccessible = true
                modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
            } catch (ignore: NoSuchFieldException) {
                // runs in android will happen
                field.isAccessible = true
            }
        }
        return field
    }

    private fun getAccessibleField(name: String): Field {
        var type: Class<*>? = type()
        return try {
            accessible(type!!.getField(name))!!
        } catch (e: NoSuchFieldException) {
            do {
                try {
                    return accessible(type!!.getDeclaredField(name))!!
                } catch (ignore: NoSuchFieldException) {
                }
                type = type!!.superclass
            } while (type != null)
            throw ReflectException(e)
        }
    }

    private fun unwrap(obj: Any): Any = if (obj is ReflectManager) obj.get() else obj

    ///////////////////////////////////////////////////////////////////////////
    // method
    ///////////////////////////////////////////////////////////////////////////
    /**
     * Invoke the method.
     *
     * @param name The name of method.
     * @return the single [ReflectManager] instance
     * @throws ReflectException if reflect unsuccessfully
     */
    fun method(name: String): ReflectManager {
        return method(name, Any())
    }

    /**
     * Invoke the method.
     *
     * @param name The name of method.
     * @param args The args.
     * @return the single [ReflectManager] instance
     * @throws ReflectException if reflect unsuccessfully
     */
    fun method(name: String, vararg args: Any?): ReflectManager {
        val types = getArgsType(*args)
        return try {
            method(exactMethod(name, types), obj, *args)
        } catch (e: NoSuchMethodException) {
            try {
                method(similarMethod(name, types), obj, *args)
            } catch (e1: NoSuchMethodException) {
                throw ReflectException(e1)
            }
        }
    }

    private fun method(method: Method, obj: Any, vararg args: Any?): ReflectManager {
        return try {
            accessible(method)
            if (method.returnType == Void.TYPE) {
                method.invoke(obj, *args)
                reflect(obj)
            } else {
                reflect(method.invoke(obj, *args))
            }
        } catch (e: Exception) {
            throw ReflectException(e)
        }
    }

    private fun exactMethod(name: String, types: Array<Class<*>?>): Method {
        var type: Class<*>? = type()
        return try {
            type!!.getMethod(name, *types)
        } catch (e: NoSuchMethodException) {
            do {
                try {
                    return type!!.getDeclaredMethod(name, *types)
                } catch (ignore: NoSuchMethodException) {
                }
                type = type!!.superclass
            } while (type != null)
            throw NoSuchMethodException()
        }
    }

    private fun similarMethod(name: String, types: Array<Class<*>?>): Method {
        var type: Class<*>? = type()
        val methods: MutableList<Method> = ArrayList()
        for (method in type!!.methods) {
            if (isSimilarSignature(method, name, types)) {
                methods.add(method)
            }
        }
        if (methods.isNotEmpty()) {
            sortMethods(methods)
            return methods[0]
        }
        do {
            for (method in type!!.declaredMethods) {
                if (isSimilarSignature(method, name, types)) {
                    methods.add(method)
                }
            }
            if (methods.isNotEmpty()) {
                sortMethods(methods)
                return methods[0]
            }
            type = type.superclass
        } while (type != null)
        throw NoSuchMethodException("No similar method " + name + " with params "
            + types.contentToString() + " could be found on type " + type() + ".")
    }

    private fun sortMethods(methods: List<Method>) {
        Collections.sort(methods, object : Comparator<Method> {
            override fun compare(o1: Method, o2: Method): Int {
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

    private fun isSimilarSignature(possiblyMatchingMethod: Method,
        desiredMethodName: String,
        desiredParamTypes: Array<Class<*>?>): Boolean {
        return possiblyMatchingMethod.name == desiredMethodName &&
            match(possiblyMatchingMethod.parameterTypes, desiredParamTypes)
    }

    private fun match(declaredTypes: Array<Class<*>?>, actualTypes: Array<Class<*>?>): Boolean {
        return if (declaredTypes.size == actualTypes.size) {
            for (i in actualTypes.indices) {
                val cls: Class<*>? = wrapper(actualTypes[i])
                if (actualTypes[i] == NULL::class.java ||
                    (cls != null && wrapper(declaredTypes[i])?.isAssignableFrom(cls) == true)) {
                    continue
                }
                return false
            }
            true
        } else {
            false
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

    ///////////////////////////////////////////////////////////////////////////
    // proxy
    ///////////////////////////////////////////////////////////////////////////
    /**
     * Create a proxy for the wrapped object allowing to type-safely invoke
     * methods on it using a custom interface.
     *
     * @param proxyType The interface type that is implemented by the proxy.
     * @return a proxy for the wrapped object
     */
    @Suppress("unused")
    fun <P> proxy(proxyType: Class<P>): P {
        val isMap = obj is Map<*, *>
        val handler = InvocationHandler { _, method, args ->
            val name = method.name
            try {
                return@InvocationHandler reflect(obj).method(name, *args).get<Any>()
            } catch (e: ReflectException) {
                if (isMap) {
                    @Suppress("UNCHECKED_CAST")
                    val map = obj as MutableMap<String, Any> // <String, Any>
                    val length = args?.size ?: 0
                    if (length == 0 && name.startsWith("get")) {
                        return@InvocationHandler map[property(name.substring(3))]!!
                    } else if (length == 0 && name.startsWith("is")) {
                        return@InvocationHandler map[property(name.substring(2))]!!
                    } else if (length == 1 && name.startsWith("set")) {
                        map[property(name.substring(3))] = args[0]
                        return@InvocationHandler null
                    }
                }
                throw e
            }
        }
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(proxyType.classLoader, arrayOf<Class<*>>(proxyType),
            handler) as P
    }

    /**
     * Get the POJO property name of an getter/setter
     */
    private fun property(string: String): String {
        return when (string.length) {
            0 -> ""
            1 -> string.lowercase(Locale.getDefault())
            else -> string.substring(0, 1).lowercase(Locale.getDefault()) + string.substring(1)
        }
    }

    private fun type(): Class<*> {
        return type
    }

    private fun wrapper(type: Class<*>?): Class<*>? {
        if (type == null) {
            return null
        } else if (type.isPrimitive) {
            if (Boolean::class.javaPrimitiveType == type) {
                return Boolean::class.java
            } else if (Int::class.javaPrimitiveType == type) {
                return Int::class.java
            } else if (Long::class.javaPrimitiveType == type) {
                return Long::class.java
            } else if (Short::class.javaPrimitiveType == type) {
                return Short::class.java
            } else if (Byte::class.javaPrimitiveType == type) {
                return Byte::class.java
            } else if (Double::class.javaPrimitiveType == type) {
                return Double::class.java
            } else if (Float::class.javaPrimitiveType == type) {
                return Float::class.java
            } else if (Char::class.javaPrimitiveType == type) {
                return Char::class.java
            } else if (Void.TYPE == type) {
                return Void::class.java
            }
        }
        return type
    }

    /**
     * Get the result.
     *
     * @param <T> The value type.
     * @return the result
    </T> */
    fun <T> get(): T {
        @Suppress("UNCHECKED_CAST")
        return obj as T
    }

    override fun hashCode(): Int {
        return obj.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is ReflectManager && other == other.get<Any>()
    }

    override fun toString(): String {
        return obj.toString()
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
