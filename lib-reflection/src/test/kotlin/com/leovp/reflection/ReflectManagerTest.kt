@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.reflection

import kotlin.reflect.full.createInstance
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test

/**
 * https://www.baeldung.com/kotlin/kclass-new-instance
 *
 * Author: Michael Leo
 * Date: 2022/9/26 16:52
 */
class ReflectManagerTest {

    companion object {
        const val DEPT_ID_HR = 100
        const val DEPT_ID_DEV = 1000
    }

    class HR(employeeId: String, p: Person) : Employee(employeeId, DEPT_ID_HR, p) {
        fun hirePerson(p: Person) {
            println("HR $name hires $p.")
        }
    }

    open class Employee(employeeId: String, deptId: Int, val p: Person) : Person(p.name, p.sex, p.age) {
        constructor(p: Person) : this("", 0, p)
        constructor(employeeId: String, p: Person) : this(employeeId, 0, p)
        constructor(deptId: Int, p: Person) : this("", deptId, p)
        constructor(userName: String, sex: Char, age: Int, employeeId: String, deptId: Int) : this(employeeId,
            deptId,
            Person(userName, sex, age))

        var employeeId: String = employeeId
            private set

        var deptId: Int = deptId
            private set

        fun assignEmployeeId(newId: String) {
            employeeId = newId
        }

        fun assignDeptId(newDeptId: Int) {
            deptId = newDeptId
        }

        fun startWorking() {
            action("${p.name}[$employeeId] starts working.")
        }

        fun stopWorking() {
            action("${p.name}[$employeeId] stops working.")
        }

        override fun toString(): String = "Employee($p) with ID $employeeId works in $deptId departure."
    }

    open class Person(name: String, sex: Char, age: Int) : Creature() {
        var name: String = name
            private set
        var sex: Char = sex
            private set
        var age: Int = age
            private set

        fun say(content: String) {
            println("$name says: $content")
        }

        fun action(action: String) {
            println("$name do $action.")
        }

        fun changeName(newName: String) {
            name = newName
        }

        fun setAge(newAge: Int) {
            age = newAge
        }

        fun increaseAge() {
            age++
        }

        override fun alive(): Boolean = true

        override fun toString(): String = "$name[$sex] is $age years old."
    }

    open class Creature {
        open fun alive(): Boolean = true

        override fun toString(): String = "Get a new creature."
    }

    @Test
    fun newInstance() {
        val newCreature: Creature = Creature::class.createInstance()
        assertIs<Creature>(newCreature)
        assertEquals("Get a new creature.", newCreature.toString())

        val harry: Person? = Person::class.primaryConstructor?.call("Harry", 'M', 21)
        assertNotNull(harry)
        assertIs<Person>(harry)
        assertEquals("Harry[M] is 21 years old.", harry.toString())

        // Primary Constructor
        val employeeHarry: Employee? = Employee::class.primaryConstructor?.call("e0000001", DEPT_ID_DEV, harry)
        assertNotNull(employeeHarry)
        assertIs<Employee>(employeeHarry)
        assertEquals("Employee(Harry[M] is 21 years old.) with ID e0000001 works in 1000 departure.", employeeHarry.toString())

        // Secondary Constructor
        val amy = Person("Amy", 'F', 19)
        val employeeAmyConstructors = Employee::class.constructors
        val employeeAmy = employeeAmyConstructors.first { constructor ->
            constructor.parameters.size == 2 &&
                constructor.parameters[0].type.classifier == String::class &&
                constructor.parameters[1].type.classifier == Person::class
        }.call("e0000002", amy)
        assertIs<Employee>(employeeAmy)
        assertEquals("Employee(Amy[F] is 19 years old.) with ID e0000002 works in 0 departure.", employeeAmy.toString())

        // employeeAmyConstructors.forEach { constructor ->
        //     println("constructor param size=${constructor.parameters.size} " +
        //         "${constructor.typeParameters.map { tp -> "typeParameters name=${tp.name}" }}")
        // }
        // Result:
        // constructor param size=1 []
        // constructor param size=2 []
        // constructor param size=2 []
        // constructor param size=5 []
        // constructor param size=3 []

        // employeeAmyConstructors.forEach { constructor ->
        //     println("constructor param size=${constructor.parameters.size} name=${constructor.name}")
        // }
        // Result:
        // constructor param size=1 name=<init>
        // constructor param size=2 name=<init>
        // constructor param size=2 name=<init>
        // constructor param size=5 name=<init>
        // constructor param size=3 name=<init>

        // employeeAmyConstructors.forEach { constructor ->
        //     println("constructor param size=${constructor.parameters.size} " +
        //         "${constructor.parameters.map { p -> "property type=${p.type} -> property name=${p.name}" }}")
        // }
        // Result:
        // constructor param size=1 [property type=com.leovp.reflection.ReflectManagerTest.Person -> property name=p]
        // constructor param size=2 [property type=kotlin.String -> property name=employeeId, property type=com.leovp.reflection.ReflectManagerTest.Person -> property name=p]
        // constructor param size=2 [property type=kotlin.Int -> property name=deptId, property type=com.leovp.reflection.ReflectManagerTest.Person -> property name=p]
        // constructor param size=5 [property type=kotlin.String -> property name=userName, property type=kotlin.Char -> property name=sex, property type=kotlin.Int -> property name=age, property type=kotlin.String -> property name=employeeId, property type=kotlin.Int -> property name=deptId]
        // constructor param size=3 [property type=kotlin.String -> property name=employeeId, property type=kotlin.Int -> property name=deptId, property type=com.leovp.reflection.ReflectManagerTest.Person -> property name=p]
    }
}
