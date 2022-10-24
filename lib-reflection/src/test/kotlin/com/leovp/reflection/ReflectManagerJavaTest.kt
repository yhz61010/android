@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.reflection

import com.leovp.reflection.testclass.JavaTestClass
import com.leovp.reflection.testclass.JavaTestClass.NoArgClass
import com.leovp.reflection.util.ReflectManager
import kotlin.test.assertEquals
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

/**
 * Author: Michael Leo
 * Date: 2022/10/13 09:52
 */
// https://www.baeldung.com/junit-5-test-order
@TestMethodOrder(MethodOrderer.MethodName::class)
class ReflectManagerJavaTest {

    @Test
    fun javaNewInstance() {
        val javaPerson1: JavaTestClass.JavaPerson = ReflectManager
            .reflect(JavaTestClass.JavaPerson::class)
            .newInstance("Java", 'M', 24)
            .get()
        assertEquals("""{name: "Java", sex: "M", age: 24}""", javaPerson1.toString())

        val javaPerson2: JavaTestClass.JavaPerson = ReflectManager
            .reflect(JavaTestClass.JavaPerson::class)
            .newInstance("Girl", 'F')
            .get()
        assertEquals("""{name: "Girl", sex: "F", age: -1}""", javaPerson2.toString())

        val noArgClass: NoArgClass = ReflectManager.reflect(NoArgClass::class).newInstance().get()
        assertEquals("Got a NoArgClass.", noArgClass.toString())
    }

    @Test
    fun javaField() {
        val javaPerson1: JavaTestClass.JavaPerson = ReflectManager
            .reflect(JavaTestClass.JavaPerson::class)
            .newInstance("Kotlin", 'M', 23)
            .get()
        assertEquals("""{name: "Kotlin", sex: "M", age: 23}""", javaPerson1.toString())

        val javaPerson1Name: String = ReflectManager.reflect(javaPerson1).property("name").get()
        assertEquals("Kotlin", javaPerson1Name)

        val javaPerson1OpenField: String = ReflectManager.reflect(javaPerson1).property("openField").get()
        assertEquals("Open Field", javaPerson1OpenField)

        val rfltPerson1: JavaTestClass.JavaPerson = ReflectManager
            .reflect(JavaTestClass.JavaPerson::class)
            .newInstance("Reflected Person", 'F', 18)
            .get()
        val javaPerson1PublicStatic: String = ReflectManager.reflect(rfltPerson1).property("PUBLIC_NAME").get()
        assertEquals("Public Name", javaPerson1PublicStatic)

        val javaPerson1PrivateStatic: String = ReflectManager.reflect(rfltPerson1).property("NO_NAME").get()
        assertEquals("No Name", javaPerson1PrivateStatic)

        val rfltPersonName: String = ReflectManager.reflect(rfltPerson1).property("name").get()
        assertEquals("Reflected Person", rfltPersonName)

        val javaPerson1PublicFinal: String = ReflectManager.reflect(rfltPerson1).property("PUBLIC_FINAL").get()
        assertEquals("Public Final", javaPerson1PublicFinal)

        val javaPerson1PrivateFinal: String = ReflectManager.reflect(rfltPerson1).property("PRIVATE_FINAL").get()
        assertEquals("Private Final", javaPerson1PrivateFinal)

        val publicStaticFinalInt: Int = ReflectManager
            .reflect(JavaTestClass.JavaPerson::class)
            .property("PUBLIC_STATIC_FINAL_INT")
            .get()
        assertEquals(10, publicStaticFinalInt)
        ReflectManager.reflect(JavaTestClass.JavaPerson::class).property("PUBLIC_STATIC_FINAL_INT", 10086)
        assertEquals(10086, JavaTestClass.JavaPerson.PUBLIC_STATIC_FINAL_INT)

        val rfltPerson2: JavaTestClass.JavaPerson = ReflectManager
            .reflect(JavaTestClass.JavaPerson::class)
            .newInstance("Reflected Person", 'F', 18)
            .get()

        ReflectManager.reflect(rfltPerson2).property("PUBLIC_FINAL", "Modified public final")
        assertEquals("Modified public final", rfltPerson2.PUBLIC_FINAL)

        ReflectManager.reflect(rfltPerson2).property("PRIVATE_FINAL", "Modified private final")
        val privateFinal: String = ReflectManager.reflect(rfltPerson2).property("PRIVATE_FINAL").get()
        assertEquals("Modified private final", privateFinal)
    }

    @Test
    fun javaFunction() {
        val javaPerson1: JavaTestClass.JavaPerson = ReflectManager
            .reflect(JavaTestClass.JavaPerson::class)
            .newInstance("Java", 'M', 24)
            .get()

        val getSexInString1: String = ReflectManager
            .reflect(javaPerson1)
            .newInstance("JavaGirl", 'F', 18)
            .method("getSexInString")
            .get()
        assertEquals("Female", getSexInString1)

        val getSexInString2: String = ReflectManager
            .reflect(JavaTestClass.JavaPerson::class)
            .newInstance("Java", 'M', 24)
            .method("getSexInString")
            .get()
        assertEquals("Male", getSexInString2)

        val noArgClass: NoArgClass = ReflectManager.reflect(NoArgClass::class).newInstance().get()
        noArgClass.secret = 10010
        assertEquals(10010, noArgClass.secret)

        val fixedCode = ReflectManager.reflect(NoArgClass::class).newInstance().method("getFixedCode").get<Int>()
        assertEquals(10086, fixedCode)

        val javaPerson2: JavaTestClass.JavaPerson = ReflectManager
            .reflect(JavaTestClass.JavaPerson::class)
            .newInstance("Man", 'M', 23)
            .method("setSex", 'F')
            .get()
        assertEquals('F', javaPerson2.sex)
        assertEquals("""{name: "Man", sex: "F", age: 23}""", javaPerson2.toString())

        val newPrivateVal: Int = ReflectManager
            .reflect(JavaTestClass.JavaPerson::class)
            .newInstance("Man", 'M')
            .method("changeOnlyPrivate", 6789)
            .get()
        assertEquals(6789, newPrivateVal)

        ReflectManager.reflect(NoArgClass::class.java).method("print", "Hello World.")

        val javaStaticMethodResult: String = ReflectManager
            .reflect(NoArgClass::class.java)
            .method("say", "I'm sorry.")
            .get()
        assertEquals("NoArgClass say: I'm sorry.", javaStaticMethodResult)

        val javaPrivateStaticMethodResult: String = ReflectManager
            .reflect(NoArgClass::class.java)
            .method("privateMessage", "Phone me later.")
            .get()
        assertEquals("PM: Phone me later.", javaPrivateStaticMethodResult)
    }
}
