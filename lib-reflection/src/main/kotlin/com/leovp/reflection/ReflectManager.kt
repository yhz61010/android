@file:Suppress("unused")

package com.leovp.reflection

import java.lang.reflect.Field
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
     * @return the single [ReflectManager] instance
     */
    @Suppress("WeakerAccess")
    fun newInstance(vararg args: Any? = arrayOfNulls<Any>(0)): ReflectManager {
        val types = getArgsType(*args)
        try {
            for (constructor in type.constructors) {
                if (matchArgsType(constructor.parameters.map { it.type.jvmErasure.java }.toTypedArray(), types)) {
                    if (!constructor.isAccessible) constructor.isAccessible = true
                    return ReflectManager(type, constructor.call(*args))
                }
            }
            throw ReflectException("Not found any constructor with arguments: $types")
        } catch (e: NoSuchMethodException) {
            throw ReflectException(e)
        }
    }

    // ==================================
    // ============ property ============
    // ==================================

    /**
     * Get the property.
     *
     * @param name The name of property.
     * @return The single {@link ReflectManager} instance.
     */
    fun property(name: String): ReflectManager {
        val prop = getProperty(name)
        return ReflectManager(prop.returnType.jvmErasure, prop.getter.call(obj))
    }

    /**
     * Set the property with specified value.
     * This method can also set `val` value.
     *
     * @param name The name of property.
     * @param value The value.
     * @return The single {@link ReflectManager} instance.
     */
    fun property(name: String, value: Any?): ReflectManager {
        val prop = getProperty(name)
        if (prop is KMutableProperty<*>) {
            prop.setter.call(obj, value)
        } else {
            getFinalField(name).set(obj, value)
        }
        return this
    }

    // ==================================
    // ============= method =============
    // ==================================

    /**
     * Invoke the method.
     *
     * @param name The name of method.
     * @param args The args.
     * @return The single {@link ReflectManager} instance.
     * @throws ReflectException If reflect unsuccessfully.
     */
    fun method(name: String, vararg args: Any? = arrayOfNulls<Any>(0)): ReflectManager {
        try {
            type.companionObject?.let { companion ->
                val result = getFunctions(type.companionObjectInstance, companion.declaredFunctions, name, *args)
                if (result != null) return result
            }
            return getFunctions(obj, type.declaredFunctions, name, *args) ?: throw ReflectException("Not found any method named [$name].")
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
     * @return the result
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

    private fun getProperty(name: String): KProperty1<out Any, *> {
        // Returns non-extension properties declared in this class and all of its superclasses.
        val prop = type.memberProperties.firstOrNull { prop -> prop.name == name }
        requireNotNull(prop) { "Can't find property $name." }
        // Allow to get private property value.
        if (!prop.isAccessible) prop.isAccessible = true
        return prop
    }

    private fun getFinalField(name: String): Field {
        val finalField = type.java.getDeclaredField(name)
        // Allow to get private property value.
        if (!finalField.isAccessible) finalField.isAccessible = true
        return finalField
    }

    private fun getFunctions(
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