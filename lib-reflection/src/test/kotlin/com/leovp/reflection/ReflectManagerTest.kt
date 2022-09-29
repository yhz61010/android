@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.reflection

import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

/**
 * https://www.baeldung.com/kotlin/kclass-new-instance
 * https://dwz.win/azW6
 *
 * Author: Michael Leo
 * Date: 2022/9/26 16:52
 */
// https://www.baeldung.com/junit-5-test-order
@TestMethodOrder(MethodOrderer.MethodName::class)
class ReflectManagerTest {

    @Test
    fun newInstance() {
        // https://stackoverflow.com/a/64742576/1685062
        println("=====>Unit: ${Unit::class.javaPrimitiveType}") // null
        println("=====>Unit: ${Unit::class.javaObjectType}") // class kotlin.Unit

        // https://stackoverflow.com/a/64742576/1685062
        println("=====>Void: ${Void::class.javaPrimitiveType}") // void
        println("=====>Void: ${Void::class.javaObjectType}") // class java.lang.Void

        // https://stackoverflow.com/a/64742576/1685062
        println("=====>Nothing: ${Nothing::class.javaPrimitiveType}") // void
        println("=====>Nothing: ${Nothing::class.javaObjectType}") // class java.lang.Void

        // ----------

        val rfltPrivateCls1: PrivateClass = ReflectManager.reflect(PrivateClass::class).newInstance().get()
        assertEquals("Get a PrivateClass with paramA=-1 paramB=NA.", rfltPrivateCls1.toString())

        val rfltPrivateCls2 = ReflectManager.reflect(PrivateClass::class).newInstance(1, "HM").get<PrivateClass>()
        assertEquals("Get a PrivateClass with paramA=1 paramB=HM.", rfltPrivateCls2.toString())

        // ----------

        val rfltCreature1: Creature = ReflectManager.reflect(Creature::class).newInstance().get()
        assertEquals("Get a new creature.", rfltCreature1.toString())

        val rfltCreature2 = ReflectManager.reflect(Creature::class.java).newInstance().get<Creature>()
        assertEquals("Get a new creature.", rfltCreature2.toString())

        val rfltCreature3 = ReflectManager.reflect("com.leovp.reflection.ReflectManagerTest\$Creature").newInstance().get<Creature>()
        assertEquals("Get a new creature.", rfltCreature3.toString())

        val rfltCreature4: Creature = ReflectManager.reflect(rfltCreature1).newInstance().get()
        assertEquals("Get a new creature.", rfltCreature4.toString())

        // ----------

        val rfltPerson1: Person = ReflectManager.reflect(Person::class).newInstance("Man1", 'M', 38).get()
        assertEquals("Man1[M] is 38 years old.", rfltPerson1.toString())

        val rfltPerson2 = ReflectManager.reflect(Person::class.java).newInstance("Man2", 'M', 28).get<Person>()
        assertEquals("Man2[M] is 28 years old.", rfltPerson2.toString())

        val rfltPerson3: Person = ReflectManager
            .reflect("com.leovp.reflection.ReflectManagerTest\$Person")
            .newInstance("Woman1", 'F', 20)
            .get()
        assertEquals("Woman1[F] is 20 years old.", rfltPerson3.toString())

        val rfltPerson4: Person = ReflectManager.reflect(rfltPerson1).newInstance("Woman2", 'F', 19).get()
        assertEquals("Woman2[F] is 19 years old.", rfltPerson4.toString())
    }

    @Test
    fun property() {
        val privateClass = PrivateClass.of(10, "Hello")
        val paramA: Int = ReflectManager.reflect(privateClass).property("paramA").get()
        assertEquals(10, paramA)
        val paramB = ReflectManager.reflect(privateClass).property("paramB").get<String>()
        assertEquals("Hello", paramB)

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
    fun newInstanceByKotlinReflect() {
        //  ==============================

        val kclass: KClass<*> = Creature::class
        kclass.primaryConstructor

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
        assertEquals("[Leo Group] Employee(Harry[M] is 21 years old.) with ID e0000001 works in 1000 departure.", employeeHarry.toString())

        // Secondary Constructor
        val amy = Person("Amy", 'F', 19)
        val employeeAmyConstructors = Employee::class.constructors
        val employeeAmy = employeeAmyConstructors.first { constructor ->
            constructor.parameters.size == 2 &&
                constructor.parameters[0].type.classifier == String::class &&
                constructor.parameters[1].type.classifier == Person::class
        }.call("e0000002", amy)
        assertIs<Employee>(employeeAmy)
        assertEquals("[Leo Group] Employee(Amy[F] is 19 years old.) with ID e0000002 works in 0 departure.", employeeAmy.toString())

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

    @Test
    fun propertyByKotlinReflection() {
        val hrPerson = Person("Chris", 'F', 20)
        val hr = HR("e2021041910000", hrPerson)

        val employee = Employee("e2021041910194", DEPT_ID_DEV, Person("Michael", 'M', 38))
        employee.assignSalary(2200, hr)
        println("employee=$employee")

        // Returns non-extension properties declared in this class and all of its superclasses.
        val employeeAllProperties = employee::class.memberProperties
        employeeAllProperties.forEach { prop ->
            // Allow to get private property value.
            if (!prop.isAccessible) prop.isAccessible = true
            println("${prop.visibility} ${prop.name}: ${prop.returnType} --> ${prop.getter.call(employee)}")
        }
        // How to change a kotlin private val property?
        // https://stackoverflow.com/a/58361516/1685062
        val companyPropery = Employee::class.java.getDeclaredField("company")
        companyPropery.isAccessible = true
        companyPropery.set(employee, "NEW Company")
        println("Reflection employee=$employee")
        // val salaryProp = employeeAllProperties.first { it.name == "salary" }

        // Result:
        // PUBLIC company: kotlin.String --> Leo Group
        // PUBLIC deptId: kotlin.Int --> 1000
        // PUBLIC employeeId: kotlin.String --> e2021041910194
        // PUBLIC p: com.leovp.reflection.ReflectManagerTest.Person --> Michael[M] is 38 years old.
        // PRIVATE salary: kotlin.Int --> 2200
        // PUBLIC age: kotlin.Int --> 38
        // PUBLIC name: kotlin.String --> Michael
        // PUBLIC sex: kotlin.Char --> M

        println("==============================")

        // Returns non-extension properties declared in this class.
        val employeeOnlySelfProperties = employee::class.declaredMemberProperties
        employeeOnlySelfProperties.forEach { prop ->
            // Allow to get private property value.
            if (!prop.isAccessible) prop.isAccessible = true
            println("${prop.visibility} ${prop.name}: ${prop.returnType} --> ${prop.getter.call(employee)}")
        }
        // Result:
        // PUBLIC company: kotlin.String --> Leo Group
        // PUBLIC deptId: kotlin.Int --> 1000
        // PUBLIC employeeId: kotlin.String --> e2021041910194
        // PUBLIC p: com.leovp.reflection.ReflectManagerTest.Person --> Michael[M] is 38 years old.
        // PRIVATE salary: kotlin.Int --> 2200

        println("==============================")

        // Returns all functions and properties declared in this class. Does not include members declared in supertypes.
        val employeeAllMembers = employee::class.declaredMembers
        employeeAllMembers.forEach { callable ->
            // Allow to get private property value.
            if (!callable.isAccessible) callable.isAccessible = true
            println("${callable.visibility} ${callable.name}: ${callable.returnType}\n" +
                "\ninstanceParameter--> ${callable.instanceParameter}" +
                "\nvalueParameters  -->${callable.valueParameters}" +
                "\nparameters       -->${callable.parameters}")
            println("--------------------")
        }
    }

    // ==================================================
    // ==================================================
    // ==================================================

    companion object {
        const val DEPT_ID_HR = 100
        const val DEPT_ID_DEV = 1000
    }

    class PrivateClass private constructor(private val paramA: Int, private var paramB: String) {
        private constructor() : this(-1, "NA")

        companion object {
            fun of(paramA: Int, paramB: String): PrivateClass {
                return PrivateClass(paramA, paramB)
            }
        }

        override fun toString(): String {
            return "Get a PrivateClass with paramA=$paramA paramB=$paramB."
        }
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

        companion object {
            const val COMPANY: String = "Leo Group"
        }

        val company: String = COMPANY

        private var salary: Int = 0

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

        fun assignSalary(newSalary: Int, assigner: Employee) {
            salary = newSalary
            println("The ${assigner.name} assigns salary $newSalary to $name.")
        }

        fun startWorking() {
            action("${p.name}[$employeeId] starts working at ${System.currentTimeMillis()}.")
        }

        fun stopWorking(time: Long) {
            action("${p.name}[$employeeId] stops working at $time.")
        }

        override fun toString(): String = "[$company] Employee($p) with ID $employeeId works in $deptId departure."
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
}
