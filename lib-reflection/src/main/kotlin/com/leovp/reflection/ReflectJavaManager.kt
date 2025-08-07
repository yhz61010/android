@file:Suppress("unused", "WeakerAccess")

package com.leovp.reflection

import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.Locale
import kotlin.reflect.KClass

/**
 * https://dwz.win/azW6
 */
class ReflectJavaManager private constructor(private var type: Class<*>, private var obj: Any? = type) {
    companion object {
        /**
         * Reflect the class.
         *
         * @param className The name of class.
         * @param classLoader The loader of class.
         * @return The single [ReflectJavaManager] instance.
         * @throws ReflectException If reflect unsuccessfully.
         */
        fun reflect(className: String, classLoader: ClassLoader? = null): ReflectJavaManager {
            try {
                val clazz = if (classLoader == null) {
                    Class.forName(className)
                } else {
                    Class.forName(className, true, classLoader)
                }
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
         * @param kclass The KClass.
         * @return The single [ReflectJavaManager] instance.
         */
        fun reflect(kclass: KClass<*>): ReflectJavaManager = ReflectJavaManager(kclass.java)

        /**
         * Reflect the class.
         *
         * @param clazz The class.
         * @return The single [ReflectJavaManager] instance.
         */
        fun reflect(clazz: Class<*>): ReflectJavaManager = ReflectJavaManager(clazz)

        /**
         * Reflect the class.
         *
         * @param obj The object.
         * @return The single [ReflectJavaManager] instance.
         */
        fun reflect(obj: Any?): ReflectJavaManager = ReflectJavaManager(
            if (obj == null) Any::class.java else obj::class.java,
            obj
        )

        /**
         * Get the POJO field name of an getter/setter
         */
        private fun getPOJOFieldName(string: String): String = when (string.length) {
            0 -> ""
            1 -> string.lowercase(Locale.getDefault())
            else ->
                string
                    .substring(0, 1)
                    .lowercase(Locale.getDefault()) + string.substring(1)
        }
    }

    // =================================
    // ========== newInstance ==========
    // =================================

    /**
     * Create and initialize a new instance.
     *
     * @param args The args.
     * @return The single [ReflectJavaManager] instance.
     */
    fun newInstance(vararg args: Any? = arrayOfNulls<Any>(0)): ReflectJavaManager {
        val types = getArgsType(*args)
        return try {
            val constructor = type().getDeclaredConstructor(*types)
            newInstance(constructor, *args)
        } catch (e: NoSuchMethodException) {
            val list = mutableListOf<Constructor<*>>()
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
        for (i in args.indices) {
            val value = args[i]
            result[i] = value?.javaClass ?: Unit::class.java
        }
        return result
    }

    private fun sortConstructors(list: List<Constructor<*>>) {
        Collections.sort(
            list,
            Comparator { o1, o2 ->
                val types1: Array<out Class<*>> = o1.parameterTypes
                val types2: Array<out Class<*>> = o2.parameterTypes
                val type1: Class<*>?
                val type2: Class<*>?
                for (i in types1.indices) {
                    if (types1[i] == types2[i]) {
                        type1 = wrapper(types1[i])
                        type2 = wrapper(types2[i])
                        return@Comparator if (type2 != null &&
                            type1?.isAssignableFrom(type2) == true
                        ) {
                            1
                        } else {
                            -1
                        }
                    }
                }
                0
            }
        )
    }

    private fun newInstance(constructor: Constructor<*>, vararg args: Any?): ReflectJavaManager = try {
        ReflectJavaManager(
            constructor.declaringClass,
            accessible(constructor)!!.newInstance(*args)
        )
    } catch (e: Exception) {
        throw ReflectException(e)
    }

    // ==================================
    // ============ property ============
    // ==================================

    /**
     * Get the property.
     *
     * @param name The name of property.
     * @return The single [ReflectJavaManager] instance.
     * @throws [ReflectException] If name of property doesn't exist.
     */
    fun property(name: String): ReflectJavaManager = try {
        val field = getField(name)
        ReflectJavaManager(field.type, field.get(obj))
    } catch (e: IllegalAccessException) {
        throw ReflectException(e)
    }

    /**
     * Set the property with specified value.
     * This method can also set `val` value or `final` filed field for Java.
     *
     * **Attention:**
     *
     * This [post](https://stackoverflow.com/a/14102192/1685062)
     * gives you the reason that why only specified Java Static Field can be modified.
     *
     * @param name The name of property.
     * @param value The value.
     * @throws ReflectException If name of property doesn't exist.
     * @return The single [ReflectJavaManager] instance.
     */
    fun property(name: String, value: Any?): ReflectJavaManager = try {
        val field = getField(name)
        field.set(obj, unwrap(value))
        this
    } catch (e: Exception) {
        throw ReflectException(e)
    }

    @Throws(IllegalAccessException::class)
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
        var objType: Class<*>? = type()
        requireNotNull(objType)
        return try {
            accessible(objType.getField(name))!!
        } catch (e: NoSuchFieldException) {
            do {
                runCatching { return accessible(objType!!.getDeclaredField(name))!! }
                objType = objType!!.superclass
            } while (objType != null)
            throw ReflectException(e)
        }
    }

    private fun unwrap(obj: Any?): Any? = if (obj is ReflectJavaManager) obj.get() else obj

    // ==================================
    // ============= method =============
    // ==================================

    /**
     * Invoke the method.
     *
     * @param name The name of method.
     * @param args The args.
     * @return The single [ReflectJavaManager] instance.
     * @throws [ReflectException] if reflect unsuccessfully.
     */
    fun method(name: String, vararg args: Any? = arrayOfNulls<Any>(0)): ReflectJavaManager {
        val types = getArgsType(*args)
        return runCatching {
            val exactMethod: Method = exactMethod(name, types)
            method(exactMethod, obj, *args)
        }.getOrElse {
            try {
                val similarMethod = similarMethod(name, types)
                method(similarMethod, obj, *args)
            } catch (e1: NoSuchMethodException) {
                throw ReflectException(e1)
            }
        }
    }

    private fun method(method: Method, obj: Any?, vararg args: Any?): ReflectJavaManager = try {
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

    @Throws(NoSuchMethodException::class)
    private fun exactMethod(name: String, types: Array<Class<*>?>): Method {
        var objType: Class<*>? = type()
        return runCatching {
            objType!!.getMethod(name, *types)
        }.getOrElse {
            do {
                runCatching { return objType!!.getDeclaredMethod(name, *types) }
                objType = objType!!.superclass
            } while (objType != null)
            throw NoSuchMethodException()
        }
    }

    @Throws(NoSuchMethodException::class)
    private fun similarMethod(name: String, types: Array<Class<*>?>): Method {
        var objType: Class<*>? = type()
        requireNotNull(objType)
        val methods = mutableListOf<Method>()
        for (method in objType.methods) {
            if (isSimilarSignature(method, name, types)) {
                methods.add(method)
            }
        }
        if (methods.isNotEmpty()) {
            sortMethods(methods)
            return methods[0]
        }
        do {
            requireNotNull(objType)
            for (method in objType.declaredMethods) {
                if (isSimilarSignature(method, name, types)) methods.add(method)
            }
            if (methods.isNotEmpty()) {
                sortMethods(methods)
                return methods[0]
            }
            objType = objType.superclass
        } while (objType != null)
        throw NoSuchMethodException(
            "No similar method $name with params " +
                "${types.contentToString()} could be found on type ${type()}."
        )
    }

    private fun sortMethods(methods: List<Method>) {
        Collections.sort(
            methods,
            Comparator { o1, o2 ->
                val types1: Array<out Class<*>> = o1.parameterTypes
                val types2: Array<out Class<*>> = o2.parameterTypes
                val type1: Class<*>?
                val type2: Class<*>?
                for (i in types1.indices) {
                    if (types1[i] != types2[i]) {
                        type1 = wrapper(types1[i])
                        type2 = wrapper(types2[i])
                        return@Comparator if (type2 != null &&
                            type1?.isAssignableFrom(type2) == true
                        ) {
                            1
                        } else {
                            -1
                        }
                    }
                }
                0
            }
        )
    }

    private fun isSimilarSignature(
        possiblyMatchingMethod: Method,
        desiredMethodName: String,
        desiredParamTypes: Array<Class<*>?>,
    ): Boolean = possiblyMatchingMethod.name == desiredMethodName &&
        match(possiblyMatchingMethod.parameterTypes, desiredParamTypes)

    private fun match(declaredTypes: Array<Class<*>?>, actualTypes: Array<Class<*>?>): Boolean {
        return if (declaredTypes.size == actualTypes.size) {
            var actualType: Class<*>?
            var declaredType: Class<*>?
            for (i in actualTypes.indices) {
                actualType = wrapper(actualTypes[i])?.kotlin?.javaObjectType
                declaredType = wrapper(declaredTypes[i])?.kotlin?.javaObjectType
                if (actualType == Unit::class.java ||
                    (actualType != null && declaredType?.isAssignableFrom(actualType) == true)
                ) {
                    continue
                }
                return false
            }
            true
        } else {
            false
        }
    }

    /** Allow to access private field. */
    private fun <T : AccessibleObject?> accessible(accessible: T): T? {
        if (accessible == null) return null
        // if (accessible is Member) {
        //     if (Modifier.isPublic(accessible.modifiers)
        //         && Modifier.isPublic(accessible.declaringClass.modifiers)) {
        //         // Check the following case:
        //         // ReflectJavaManager.reflect(JavaTestClass.JavaPerson::class).property("PUBLIC_STATIC_FINAL_INT", 10086)
        //         // If you set Java Public Static Final field as above, when run into here,
        //         // although its a public filed, its `accessible` is still `false`.
        //         // So I need to set `accessible` to `true` anyway.
        //         return accessible
        //     }
        // }
        // Allow to access private field.
        if (!accessible.isAccessible) accessible.isAccessible = true
        return accessible
    }

    // /////////////////////////////////////////////////////////////////////////
    // proxy
    // /////////////////////////////////////////////////////////////////////////
    /**
     * Create a proxy for the wrapped object allowing to type safely invoke
     * methods on it using a custom interface.
     *
     * @param proxyType The interface type that is implemented by the proxy.
     * @return A proxy for the wrapped object.
     */
    @Suppress("UNCHECKED_CAST")
    fun <P> proxy(proxyType: Class<P>): P {
        val isMap = obj is Map<*, *>
        val handler = InvocationHandler { _, method, args ->
            val name = method.name
            try {
                return@InvocationHandler reflect(obj).method(name, *args).get<Any>()
            } catch (e: ReflectException) {
                if (isMap) {
                    val map = obj as MutableMap<String, Any>?
                    val length = args?.size ?: 0
                    if (length == 0 && name.startsWith("get")) {
                        return@InvocationHandler map?.get(getPOJOFieldName(name.substring(3)))
                    } else if (length == 0 && name.startsWith("is")) {
                        return@InvocationHandler map?.get(getPOJOFieldName(name.substring(2)))
                    } else if (length == 1 && name.startsWith("set")) {
                        map?.put(getPOJOFieldName(name.substring(3)), args[0])
                        return@InvocationHandler null
                    }
                }
                throw e
            }
        }
        return Proxy.newProxyInstance(
            proxyType.classLoader,
            arrayOf<Class<*>>(proxyType), handler
        ) as P
    }

    private fun type(): Class<*> = type

    private fun wrapper(type: Class<*>?): Class<*>? {
        if (type == null) {
            return null
        } else if (type.isPrimitive) {
            return if (Boolean::class.javaPrimitiveType == type) {
                Boolean::class.java
            } else if (Int::class.javaPrimitiveType == type) {
                Int::class.java
            } else if (Long::class.javaPrimitiveType == type) {
                Long::class.java
            } else if (Short::class.javaPrimitiveType == type) {
                Short::class.java
            } else if (Byte::class.javaPrimitiveType == type) {
                Byte::class.java
            } else if (Double::class.javaPrimitiveType == type) {
                Double::class.java
            } else if (Float::class.javaPrimitiveType == type) {
                Float::class.java
            } else if (Char::class.javaPrimitiveType == type) {
                Char::class.java
            } else if (Void.TYPE == type) {
                Void::class.java
            } else {
                type
            }
        }
        return type
    }

    /**
     * Get the result.
     *
     * @param <T> The value type.
     * @return The result.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(): T = obj as T

    override fun hashCode(): Int = obj.hashCode()

    override fun equals(other: Any?): Boolean = other is ReflectJavaManager && obj == other.get()

    override fun toString(): String = obj.toString()

    class ReflectException : RuntimeException {
        constructor(message: String?) : super(message)
        constructor(message: String?, cause: Throwable?) : super(message, cause)
        constructor(cause: Throwable?) : super(cause)
    }
}
