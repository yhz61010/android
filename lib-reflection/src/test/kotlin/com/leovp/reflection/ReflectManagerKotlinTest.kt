@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.reflection

import com.leovp.reflection.testclass.Creature
import com.leovp.reflection.testclass.DEPT_ID_DEV
import com.leovp.reflection.testclass.DataClassOneArg
import com.leovp.reflection.testclass.Employee
import com.leovp.reflection.testclass.Person
import com.leovp.reflection.testclass.PrivateConstructor
import com.leovp.reflection.testclass.TestClass
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertThrows

/**
 * https://www.baeldung.com/kotlin/kclass-new-instance
 * https://dwz.win/azW6
 *
 * Author: Michael Leo
 * Date: 2022/9/26 16:52
 */
// https://www.baeldung.com/junit-5-test-order
@TestMethodOrder(MethodOrderer.MethodName::class)
class ReflectManagerKotlinTest {

    @Test
    fun newInstancePrivateConstructor() {
        val pcClass1: PrivateConstructor = ReflectManager
            .reflect(PrivateConstructor::class)
            .newInstance()
            .get()
        assertEquals("Get a PrivateConstructor with paramA=-1 paramB=NA.", pcClass1.toString())

        val pcClass1Case2: PrivateConstructor = ReflectManager
            .reflect(PrivateConstructor::class.java)
            .newInstance()
            .get()
        assertEquals("Get a PrivateConstructor with paramA=-1 paramB=NA.", pcClass1Case2.toString())

        val pcClass1Case3: PrivateConstructor = ReflectManager
            .reflect(pcClass1)
            .newInstance()
            .get()
        assertEquals("Get a PrivateConstructor with paramA=-1 paramB=NA.", pcClass1Case3.toString())

        assertNotEquals(pcClass1, pcClass1Case3)

        val pcClass2: PrivateConstructor = ReflectManager
            .reflect(PrivateConstructor::class)
            .newInstance(1, "HM")
            .get()
        assertEquals("Get a PrivateConstructor with paramA=1 paramB=HM.", pcClass2.toString())

        val pcClass2Case2: PrivateConstructor = ReflectManager
            .reflect(PrivateConstructor::class.java)
            .newInstance(1, "HM")
            .get()
        assertEquals("Get a PrivateConstructor with paramA=1 paramB=HM.", pcClass2Case2.toString())

        val pcClass3: Any = ReflectManager
            .reflect("com.leovp.reflection.testclass.PrivateConstructor")
            .newInstance(100, "LeoVP")
            .get()
        assertEquals("Get a PrivateConstructor with paramA=100 paramB=LeoVP.", pcClass3.toString())

        val pcClass4: Any = ReflectManager
            .reflect("com.leovp.reflection.testclass.PrivateConstructor", ClassLoader.getSystemClassLoader())
            .newInstance(123, "Baidu")
            .get()
        assertEquals("Get a PrivateConstructor with paramA=123 paramB=Baidu.", pcClass4.toString())

        val exception1 = assertThrows<ReflectManager.ReflectException>("Should throw ReflectException") {
            ReflectManager.reflect(PrivateConstructor::class.java).newInstance(1).get()
        }
        assertIs<ReflectManager.ReflectException>(exception1)

        val exception2 = assertThrows<ReflectManager.ReflectException>("Should throw ReflectException") {
            ReflectManager
                .reflect("com.leovp.reflection.testclass.PrivateConstructor")
                .newInstance(1)
                .get()
        }
        assertIs<ReflectManager.ReflectException>(exception2)

        val exception3 = assertThrows<ReflectManager.ReflectException>("Should throw ReflectException") {
            ReflectManager.reflect("com.leovp.reflection.DummyClass").newInstance().get()
        }
        assertIs<ReflectManager.ReflectException>(exception3)
    }

    @Test
    fun newInstanceNoArgument() {
        val exception = assertThrows<ReflectManager.ReflectException>("Should throw ReflectException") {
            ReflectManager.reflect(Creature::class).newInstance(1, 2, 3, 4, 5).get()
        }
        assertIs<ReflectManager.ReflectException>(exception)

        val exception2 = assertThrows<ReflectManager.ReflectException>("Should throw ReflectException") {
            ReflectManager.reflect("com.leovp.reflection.DummyClass").newInstance().get()
        }
        assertIs<ReflectManager.ReflectException>(exception2)

        val rfltCreature1: Creature = ReflectManager.reflect(Creature::class).newInstance().get()
        assertEquals("Get a new creature.", rfltCreature1.toString())

        val rfltCreature2 = ReflectManager.reflect(Creature::class.java).newInstance().get<Creature>()
        assertEquals("Get a new creature.", rfltCreature2.toString())

        val rfltCreature3: Any = ReflectManager
            .reflect("com.leovp.reflection.testclass.Creature")
            .newInstance()
            .get()
        assertEquals("Get a new creature.", rfltCreature3.toString())

        val rfltCreature4: Any = ReflectManager
            .reflect("com.leovp.reflection.testclass.Creature", ClassLoader.getSystemClassLoader())
            .newInstance()
            .get()
        assertEquals("Get a new creature.", rfltCreature4.toString())

        val rfltCreature5: Creature = ReflectManager.reflect(rfltCreature1).newInstance().get()
        assertEquals("Get a new creature.", rfltCreature5.toString())
    }

    @Test
    fun newInstanceWithArgument() {
        val exception = assertThrows<ReflectManager.ReflectException>("Should throw ReflectException") {
            ReflectManager.reflect(Person::class).newInstance(1, 2, 3).get()
        }
        assertIs<ReflectManager.ReflectException>(exception)

        val rfltPerson1: Person = ReflectManager.reflect(Person::class).newInstance("Man1", 'M', 38).get()
        assertEquals("Man1[M] is 38 years old.", rfltPerson1.toString())

        val rfltPerson2 = ReflectManager.reflect(Person::class.java).newInstance("Man2", 'M', 28).get<Person>()
        assertEquals("Man2[M] is 28 years old.", rfltPerson2.toString())

        val rfltPerson3: Any = ReflectManager
            .reflect("com.leovp.reflection.testclass.Person")
            .newInstance("Woman1", 'F', 20)
            .get()
        assertEquals("Woman1[F] is 20 years old.", rfltPerson3.toString())

        val rfltPerson3Case2: Any = ReflectManager
            .reflect("com.leovp.reflection.testclass.Person", ClassLoader.getSystemClassLoader())
            .newInstance("Woman2", 'F', 21)
            .get()
        assertEquals("Woman2[F] is 21 years old.", rfltPerson3Case2.toString())

        val exception2 = assertThrows<ReflectManager.ReflectException>("Should throw ReflectException") {
            ReflectManager.reflect("com.leovp.reflection.DummyClass").newInstance(1, 2, 3).get()
        }
        assertIs<ReflectManager.ReflectException>(exception2)

        val rfltPerson4: Person = ReflectManager.reflect(rfltPerson1).newInstance("Woman2", 'F', 19).get()
        assertEquals("Woman2[F] is 19 years old.", rfltPerson4.toString())

        val tc1: TestClass = ReflectManager.reflect(TestClass::class).newInstance(1, "str2", "str3", 2).get()
        assertEquals("1 str2 str3 2", tc1.toString())

        val tc2: TestClass = ReflectManager.reflect(TestClass::class).newInstance(11, "str33").get()
        assertEquals("11 -2 str33 0", tc2.toString())
    }

    @Test
    fun newInstanceDataClass() {
        val exception = assertThrows<ReflectManager.ReflectException>("Should throw ReflectException") {
            ReflectManager.reflect(DataClassOneArg::class).newInstance(1, 2, 3).get()
        }
        assertIs<ReflectManager.ReflectException>(exception)

        val dataClass1: DataClassOneArg = ReflectManager.reflect(DataClassOneArg::class).newInstance("DC1").get()
        assertEquals("DataClassOneArg(arg1=DC1)", dataClass1.toString())

        val dataClass2: Any = ReflectManager
            .reflect("com.leovp.reflection.testclass.DataClassTwoArg")
            .newInstance("DC2", 20021008)
            .get()
        println("dataClass2=${dataClass2::class.java}")
        assertEquals("DataClassTwoArg(arg1=DC2, num=20021008)", dataClass2.toString())

        val dataClass3: Any = ReflectManager
            .reflect("com.leovp.reflection.testclass.DataClassTwoArg", ClassLoader.getSystemClassLoader())
            .newInstance("DC3", 20221013)
            .get()
        println("dataClass2=${dataClass3::class.java}")
        assertEquals("DataClassTwoArg(arg1=DC3, num=20221013)", dataClass3.toString())
    }

    // ==============================
    // ==============================
    // ==============================

    @Test
    fun propertyGet() {
        val privateConstructor = PrivateConstructor.of(10, "Hello")
        val privateParamA: Int = ReflectManager.reflect(privateConstructor).property("paramA").get()
        assertEquals(10, privateParamA)
        val privateParamB = ReflectManager.reflect(privateConstructor).property("paramB").get<String>()
        assertEquals("Hello", privateParamB)

        val person: Person = ReflectManager.reflect(Person::class).newInstance("Michael", 'M', 22).get()
        val name: String = ReflectManager.reflect(person).property("name").get()
        assertEquals("Michael", name)
        val sex: Char = ReflectManager.reflect(person).property("sex").get()
        assertEquals('M', sex)
        val age: Int = ReflectManager
            .reflect(Person::class)
            .newInstance("World", 'F', 18)
            .property("age")
            .get()
        assertEquals(18, age)
    }

    @Test
    fun propertySet() {
        val privateClass = PrivateConstructor.of(10, "Hello")
        val person: Person = ReflectManager.reflect(Person::class).newInstance("Michael", 'M', 22).get()

        ReflectManager.reflect(privateClass).property("paramA", 666)
        // Change `val` value.
        assertEquals(666, ReflectManager.reflect(privateClass).property("paramA").get())

        ReflectManager.reflect(person).property("age", 24)
        assertEquals(24, person.age)

        val reflectedPerson: Person = ReflectManager.reflect(Person::class).newInstance("Jim", 'M', 23).get()
        val employee: Employee = ReflectManager
            .reflect(Employee::class)
            .newInstance("e2003241067", reflectedPerson)
            .property("salary", 3500)
            .property("deptId", DEPT_ID_DEV)
            .property("comment", "No comment.")
            .get()
        assertEquals(
            "[Leo Group] Employee(Jim[M] is 23 years old.) with ID e2003241067 works in 1000 departure. Salary: 3500.",
            employee.toString()
        )
        assertEquals("No comment.", employee.comment)
        ReflectManager.reflect(employee).property("comment", null)
        assertEquals(null, employee.comment)

        val company: String = ReflectManager.reflect(employee).property("company").get()
        assertEquals("Leo Group", company)
    }

    // ==============================
    // ==============================
    // ==============================

    @Test
    fun function() {
        val person = Person("Michael", 'M', 24)
        val sayResult: String = ReflectManager.reflect(person).method("say", "Hello World.").get()
        assertEquals("Michael says: Hello World.", sayResult)

        val actionMethodResult: String = ReflectManager
            .reflect(person)
            .method("action", "Count Number", 888)
            .get()
        assertEquals("Michael do [Count Number] with exceptResult: 888.", actionMethodResult)

        ReflectManager.reflect(person).method("increaseAge")
        assertEquals(25, person.age)

        val stopWorkingResult: String = ReflectManager
            .reflect(Employee::class)
            .newInstance("e00000001", DEPT_ID_DEV, person)
            .method("stopWorking", 20221008100549).get()
        assertEquals(
            "Michael do [Michael[e00000001] stops working at 20221008100549.] with exceptResult: -10.",
            stopWorkingResult
        )

        val privateMethod: String = ReflectManager
            .reflect(Person::class)
            .newInstance("Tom", 'M', 26)
            .method("secretMethod", "<SECRET>").get()
        assertEquals("Tom does [secret method](<SECRET>).", privateMethod)

        val showClothesResult: String = ReflectManager
            .reflect(Person::class)
            .newInstance("Michael", 'M', 22)
            .method("showClothes", true).get()
        assertEquals("Someone shows all(true) clothes.", showClothesResult)

        ReflectManager
            .reflect(Person::class)
            .newInstance("John", 'M', 24)
            .method("sayHi")

        val objInstanceShowClothesResult: String = ReflectManager
            .reflect(Person::class)
            .method("showClothes", false).get()
        assertEquals("Someone shows all(false) clothes.", objInstanceShowClothesResult)

        val employeeInstanceSayHi: String = ReflectManager
            .reflect(Employee::class)
            .method("sayHi").get()
        assertEquals("Employee said: Hi.", employeeInstanceSayHi)

        val employeeInstanceGlobalSay: String = ReflectManager
            .reflect(Employee::class)
            .method("globalSay", "New World").get()
        assertEquals("Employee said to global: New World", employeeInstanceGlobalSay)
    }
}
