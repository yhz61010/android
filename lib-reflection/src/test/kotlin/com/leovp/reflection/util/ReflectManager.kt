@file:Suppress("unused")

package com.leovp.reflection.util

import java.lang.reflect.AccessibleObject
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure


/**
 * https://dwz.win/azW6
 *
 * Need to add `implementation(libs.kotlin.reflect)`
 *
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
        private val unitType = Unit::class.createType()

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
        fun <T : Any> reflect(obj: T?): ReflectManager {
            return ReflectManager(if (obj == null) Any::class else obj::class, obj)
        }
    }

    // =================================
    // ========== newInstance ==========
    // =================================

    /**
     * Create and initialize a new instance.
     *
     * @param args The args.
     * @return The single [ReflectManager] instance.
     */
    @Suppress("WeakerAccess")
    fun newInstance(vararg args: Any? = arrayOfNulls<Any>(0)): ReflectManager {
        val types = getArgsType(*args)
        for (constructor in type.constructors) {
            if (matchArgsType(constructor.parameters.map { it.type.jvmErasure.java }.toTypedArray(), types)) {
                if (!constructor.isAccessible) constructor.isAccessible = true
                return ReflectManager(type, constructor.call(*args))
            }
        }
        throw ReflectException("Not found any constructor with arguments: $types")
    }

    // ==================================
    // ============ property ============
    // ==================================

    /**
     * Get the property.
     *
     * @param name The name of property.
     * @return The single [ReflectManager] instance.
     * @throws ReflectException If name of property doesn't exist.
     */
    fun property(name: String): ReflectManager {
        val prop = getProperty(name)
        return try {
            if (prop != null) {
                ReflectManager(prop.returnType.jvmErasure, prop.getter.call(obj))
            } else {
                // Get field by Java reflection.
                val javaField = getFieldByJavaReflection(name)
                ReflectManager(javaField.type.kotlin, javaField.get(obj))
            }
        } catch (e: IllegalAccessException) {
            throw ReflectException(e)
        } catch (e: IllegalArgumentException) {
            throw ReflectException(e)
        } catch (e: NullPointerException) {
            throw ReflectException(e)
        } catch (e: ExceptionInInitializerError) {
            throw ReflectException(e)
        }
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
     * @return The single [ReflectManager] instance.
     */
    fun property(name: String, value: Any?): ReflectManager {
        val prop: KProperty1<out Any, *>? = getProperty(name)
        try {
            if (prop == null || prop !is KMutableProperty<*>) {
                // If property not found in that class, try to find it in its all super classes.
                // Note that, we can also change the `val` property value.
                getFieldByJavaReflection(name).set(obj, value)
                return this
            }
            // Set property if its type is KMutableProperty
            prop.setter.call(obj, value)
            return this
        } catch (e: IllegalAccessException) {
            throw ReflectException(e)
        } catch (e: IllegalArgumentException) {
            throw ReflectException(e)
        } catch (e: NullPointerException) {
            throw ReflectException(e)
        } catch (e: ExceptionInInitializerError) {
            throw ReflectException(e)
        }
    }

    // ==================================
    // ============= method =============
    // ==================================

    /**
     * Invoke the method.
     *
     * @param name The name of method.
     * @param args The args.
     * @return The single [ReflectManager] instance.
     * @throws ReflectException If reflect unsuccessfully.
     */
    fun method(name: String, vararg args: Any? = arrayOfNulls<Any>(0)): ReflectManager {
        try {
            // Call Kotlin class companion object method.
            type.companionObject?.let { companion ->
                val result = callFunctions(type.companionObjectInstance, companion.declaredFunctions, name, *args)
                if (result != null) return result
            }
            // Only call Java static method.
            return try {
                callJavaStaticFunction(name, *args)
                    ?: throw NoSuchMethodException("Not found any Java static method [$name]")
            } catch (e: NoSuchMethodException) {
                // Call Kotlin class instance normal method.
                callFunctions(obj, type.declaredFunctions, name, *args)
                    ?: throw ReflectException("Not found any method named [$name].")
            }
        } catch (e: NoSuchMethodException) {
            throw ReflectException(e)
        }
    }

    // ==================================
    // ====== Get reflected object ======
    // ==================================

    /**
     * Get the result.
     *
     * @param <T> The value type.
     * @return The result.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(): T {
        return obj as T
    }

    // ==================================
    // ======== Internal methods ========
    // ==================================

    private fun getArgsType(vararg args: Any?): Array<KClass<*>?> {
        val result: Array<KClass<*>?> = arrayOfNulls(args.size)
        for ((i, value) in args.withIndex()) {
            result[i] = (value?.javaClass ?: Unit::class.java).kotlin
        }
        return result
    }

    private fun matchArgsType(declaredTypes: Array<Class<*>?>, actualTypes: Array<KClass<*>?>): Boolean {
        return if (declaredTypes.size == actualTypes.size) {
            for (i in actualTypes.indices) {
                val actualType: Class<*>? = actualTypes[i]?.javaObjectType
                val declaredType: Class<*>? = declaredTypes[i]?.kotlin?.javaObjectType
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

    private fun getProperty(name: String): KProperty1<out Any, *>? {
        // Returns non-extension properties declared in this class and all of its superclasses.
        val prop = type.memberProperties.firstOrNull { prop -> prop.name == name } ?: return null
        // Allow to get private property value.
        if (!prop.isAccessible) prop.isAccessible = true
        return prop
    }

    /**
     * @param name The field name.
     * @throws ReflectException If [name] doesn't exist.
     */
    private fun getFieldByJavaReflection(name: String): Field {
        val field: Field = getAccessibleFieldByJavaReflection(name)
        if ((field.modifiers and Modifier.FINAL) == Modifier.FINAL) {
            runCatching {
                val modifiersField = field.javaClass.getDeclaredField("modifiers")
                modifiersField.isAccessible = true
                modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
            }/*.onFailure {
                // runs in android will happen
                field.isAccessible = true
            }*/
        }
        return field
    }

    /**
     * Allow to get field in this class or all super classes.
     *
     * @param name The field name.
     * @throws ReflectException If [name] doesn't exist.
     */
    private fun getAccessibleFieldByJavaReflection(name: String): Field {
        return try {
            accessibleForJava(type.java.getField(name))
        } catch (e: NoSuchFieldException) {
            var objTypeInJava: Class<*>? = type.java
            do {
                requireNotNull(objTypeInJava)
                try {
                    return accessibleForJava(objTypeInJava.getDeclaredField(name))
                } catch (ignore: NoSuchFieldException) {
                }
                objTypeInJava = objTypeInJava.superclass
            } while (objTypeInJava != null)
            throw ReflectException("Can't find field $name.", e)
        }
    }

    /** Allow to access private field. */
    private fun <T : AccessibleObject> accessibleForJava(accessible: T): T {
        // if (accessible == null) return null
        // if (accessible is Member) {
        //     if (Modifier.isPublic(accessible.modifiers)
        //         && Modifier.isPublic(accessible.declaringClass.modifiers)) {
        //         // Check the following case:
        //         // ReflectManager.reflect(JavaTestClass.JavaPerson::class).property("PUBLIC_STATIC_FINAL_INT", 10086)
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

    private fun callJavaStaticFunction(name: String, vararg args: Any? = arrayOfNulls<Any>(0)): ReflectManager? {
        type.java.declaredMethods
            .filter { method ->
                if (!method.isAccessible) method.isAccessible = true
                method.name == name && Modifier.isStatic(method.modifiers)
            }
            .firstOrNull { method ->
                val types = getArgsType(*args)
                matchArgsType(method.parameterTypes, types)
            }?.let { javaMethod ->
                return if (javaMethod.returnType == Void::class.java) {
                    javaMethod.invoke(obj, *args)
                    reflect(obj)
                } else {
                    reflect(javaMethod.invoke(obj, *args))
                }
            }

        return null
    }

    private fun callFunctions(
        `object`: Any?,
        functions: Collection<KFunction<*>>,
        name: String,
        vararg args: Any? = arrayOfNulls<Any>(0)
    ): ReflectManager? {
        val types = getArgsType(*args)
        functions.firstOrNull { func ->
            func.name == name && matchArgsType(func.valueParameters.map { it.type.jvmErasure.java }.toTypedArray(), types)
        }?.let { func ->
            if (!func.isAccessible) func.isAccessible = true
            return if (func.returnType == unitType) {
                func.call(`object`, *args)
                reflect(`object`)
            } else {
                reflect(func.call(`object`, *args))
            }
        }
        return null
    }

    // =================================
    // =========== Exception ===========
    // =================================

    class ReflectException : RuntimeException {
        constructor(cause: Throwable?) : super(cause)

        @Suppress("unused")
        constructor(message: String?, cause: Throwable? = null) : super(message, cause)
    }
}
