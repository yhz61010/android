package com.leovp.reflection

import org.junit.jupiter.api.Test
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * https://www.baeldung.com/kotlin/kclass-new-instance
 *
 * Author: Michael Leo
 * Date: 2022/9/26 16:52
 */
class ReflectManagerTest {

    class Student(
        @Suppress("WeakerAccess")
        var classNo: Int,
        @Suppress("WeakerAccess")
        var num: String,
        @Suppress("WeakerAccess")
        val p: Person
    ) : Person(p.userName, p.age) {
        @Suppress("unused")
        constructor(p: Person) : this(0, "", p)

        @Suppress("unused")
        constructor(num: String, p: Person) : this(0, num, p)

        @Suppress("unused")
        constructor(classNo: Int, p: Person) : this(classNo, "", p)

        @Suppress("unused")
        constructor(userName: String, age: Int, classNo: Int, num: String) : this(classNo, num, Person(userName, age))

        override fun toString(): String = "Student ($p) In class $classNo with No. $num"
    }

    open class Person(var userName: String, var age: Int) : Human() {
        override fun toString(): String = "$userName is $age years old."
    }

    open class Human {
        override fun toString(): String = "Get a human."
    }

    @Test
    fun newInstance() {
        val human: Human = Human::class.createInstance()
        assertIs<Human>(human)
        assertEquals("Get a human.", human.toString())

        val person: Person? = Person::class.primaryConstructor?.call("Putao", 9)
        assertNotNull(person)
        assertIs<Person>(person)
        assertEquals("Putao is 9 years old.", person.toString())

        // Primary Constructor
        val student: Student? = Student::class.primaryConstructor?.call(5, "2020010525", person)
        assertNotNull(student)
        assertIs<Student>(student)
        assertEquals("Student (Putao is 9 years old.) In class 5 with No. 2020010525", student.toString())

        // Secondary Constructor
        val personAmy = Person("Amy", 8)
        val studentAmyConstructors = Student::class.constructors
        val studentAmy = studentAmyConstructors.first { constructor ->
            constructor.parameters.size == 2 &&
                constructor.parameters[0].type.classifier == String::class &&
                constructor.parameters[1].type.classifier == Person::class
        }.call("20200105ab", personAmy)
        assertIs<Student>(studentAmy)
        assertEquals("Student (Amy is 8 years old.) In class 0 with No. 20200105ab", studentAmy.toString())

        // studentAmyConstructors.forEach { constructor ->
        //     println("constructor param size=${constructor.parameters.size} " +
        //         "${constructor.typeParameters.map { tp -> "typeParameters name=${tp.name}" }}")
        // }
        // Result:
        // constructor param size=1 []
        // constructor param size=2 []
        // constructor param size=2 []
        // constructor param size=3 []

        // studentAmyConstructors.forEach { constructor ->
        //     println("constructor param size=${constructor.parameters.size} name=${constructor.name}")
        // }
        // Result:
        // constructor param size=1 name=<init>
        // constructor param size=2 name=<init>
        // constructor param size=2 name=<init>
        // constructor param size=3 name=<init>

        // studentAmyConstructors.forEach { constructor ->
        //     println("constructor param size=${constructor.parameters.size} " +
        //         "${constructor.parameters.map { p -> "property type=${p.type} -> property name=${p.name}" }}")
        // }
        // Result:
        // constructor param size=1 [property type=com.leovp.reflection.ReflectManagerTest.Person -> property name=p]
        // constructor param size=2 [property type=kotlin.String -> property name=num, property type=com.leovp.reflection.ReflectManagerTest.Person -> property name=p]
        // constructor param size=2 [property type=kotlin.Int -> property name=classNo, property type=com.leovp.reflection.ReflectManagerTest.Person -> property name=p]
        // constructor param size=3 [property type=kotlin.Int -> property name=classNo, property type=kotlin.String -> property name=num, property type=com.leovp.reflection.ReflectManagerTest.Person -> property name=p]
    }
}
