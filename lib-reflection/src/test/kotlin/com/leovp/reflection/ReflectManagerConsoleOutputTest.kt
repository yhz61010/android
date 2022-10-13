package com.leovp.reflection

import com.leovp.reflection.testclass.Creature
import com.leovp.reflection.testclass.DEPT_ID_DEV
import com.leovp.reflection.testclass.Employee
import com.leovp.reflection.testclass.HR
import com.leovp.reflection.testclass.Person
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberExtensionFunctions
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.functions
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberExtensionFunctions
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.staticFunctions
import kotlin.reflect.full.staticProperties
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Author: Michael Leo
 * Date: 2022/10/13 09:59
 */
class ReflectManagerConsoleOutputTest {

    @Test
    fun consoleOutput() {
        // https://stackoverflow.com/a/64742576/1685062
        println("=====>Unit: ${Unit::class.javaPrimitiveType}") // null
        println("=====>Unit: ${Unit::class.javaObjectType}") // class kotlin.Unit

        // https://stackoverflow.com/a/64742576/1685062
        println("=====>Void: ${Void::class.javaPrimitiveType}") // void
        println("=====>Void: ${Void::class.javaObjectType}") // class java.lang.Void

        // https://stackoverflow.com/a/64742576/1685062
        println("=====>Nothing: ${Nothing::class.javaPrimitiveType}") // void
        println("=====>Nothing: ${Nothing::class.javaObjectType}") // class java.lang.Void
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
        assertEquals(
            "[Leo Group] Employee(Harry[M] is 21 years old.) " +
                "with ID e0000001 works in 1000 departure. Salary: 0.",
            employeeHarry.toString()
        )

        // Secondary Constructor
        val amy = Person("Amy", 'F', 19)
        val employeeAmyConstructors = Employee::class.constructors
        val employeeAmy = employeeAmyConstructors.first { constructor ->
            constructor.parameters.size == 2 &&
                constructor.parameters[0].type.classifier == String::class &&
                constructor.parameters[1].type.classifier == Person::class
        }.call("e0000002", amy)
        assertIs<Employee>(employeeAmy)
        assertEquals(
            "[Leo Group] Employee(Amy[F] is 19 years old.) with ID e0000002 works in 0 departure. Salary: 0.",
            employeeAmy.toString()
        )

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

        employeeAmyConstructors.forEach { constructor ->
            println(
                "constructor param size=${constructor.parameters.size} " +
                    "name=${constructor.name} " +
                    "${constructor.parameters.map { p -> "argument type=${p.type} -> argument name=${p.name}" }}"
            )
        }
        // Result:
        // constructor param size=1 name=<init> [argument type=com.leovp.reflection.ReflectManagerTest.Person -> argument name=p]
        // constructor param size=2 name=<init> [argument type=kotlin.String -> argument name=employeeId, argument type=com.leovp.reflection.ReflectManagerTest.Person -> argument name=p]
        // constructor param size=2 name=<init> [argument type=kotlin.Int -> argument name=deptId, argument type=com.leovp.reflection.ReflectManagerTest.Person -> argument name=p]
        // constructor param size=5 name=<init> [argument type=kotlin.String -> argument name=userName, argument type=kotlin.Char -> argument name=sex, argument type=kotlin.Int -> argument name=age, argument type=kotlin.String -> argument name=employeeId, argument type=kotlin.Int -> argument name=deptId]
        // constructor param size=3 name=<init> [argument type=kotlin.String -> argument name=employeeId, argument type=kotlin.Int -> argument name=deptId, argument type=com.leovp.reflection.ReflectManagerTest.Person -> argument name=p]
    }

    @Test
    fun propertyByKotlinReflection() {
        val hrPerson = Person("Chris", 'F', 20)
        val hr = HR("e2021041910000", hrPerson)

        val employee = Employee("e2021041910194", DEPT_ID_DEV, Person("Michael", 'M', 38))
        employee.assignSalary(2200, hr)
        assertEquals(
            "[Leo Group] Employee(Michael[M] is 38 years old.) with ID e2021041910194 works in 1000 departure. Salary: 2200.",
            employee.toString()
        )

        // Returns non-extension properties declared in this class and all of its superclasses.
        val employeeAllProperties = employee::class.memberProperties
        employeeAllProperties.forEach { prop ->
            // Allow to get private property value.
            if (!prop.isAccessible) prop.isAccessible = true
            println("${prop.visibility} ${prop.name}: ${prop.returnType} --> ${prop.getter.call(employee)}")
        }
        // How to change a kotlin private val property?
        // https://stackoverflow.com/a/58361516/1685062
        val companyProperty = Employee::class.java.getDeclaredField("company")
        companyProperty.isAccessible = true
        companyProperty.set(employee, "NEW Company")
        assertEquals(
            "[NEW Company] Employee(Michael[M] is 38 years old.) with ID e2021041910194 works in 1000 departure. Salary: 2200.",
            employee.toString()
        )
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

        println("====================")
        println("= staticProperties =")
        println("====================")

        Person::class.staticProperties.forEach { prop ->
            // Allow to get private property value.
            if (!prop.isAccessible) prop.isAccessible = true
            println("${prop.visibility} ${prop.name}: ${prop.returnType}")
        }
    }

    @Test
    fun memberByKotlinReflection() {
        // Returns a Method object that reflects the specified public member method of the class or
        // interface represented by this Class object.
        // The name parameter is a String specifying the simple name of the desired method.
        // The parameterTypes parameter is an array of Class objects that identify the method's formal parameter types,
        // in declared order. If parameterTypes is null, it is treated as if it were an empty array.
        // If the name is " " or " " a NoSuchMethodException is raised. Otherwise,
        // the method to be reflected is determined by the algorithm that follows.
        // Let C be the class or interface represented by this object:
        Person::class.java.getMethod("say", String::class.java)
        // Returns a Method object that reflects the specified declared method of the class or
        // interface represented by this Class object.
        // The name parameter is a String that specifies the simple name of the desired method,
        // and the parameterTypes parameter is an array of Class objects that
        // identify the method's formal parameter types,
        // in declared order. If more than one method with the same parameter types is declared in a class,
        // and one of these methods has a return type that is more specific than any of the others,
        // that method is returned; otherwise one of the methods is chosen arbitrarily.
        // If the name is "<init>"or "<clinit>" a NoSuchMethodException is raised.
        // If this Class object represents an array type, then this method does not find the clone() method.
        Person::class.java.getDeclaredMethod("say", String::class.java)

        // All functions and properties accessible in this class,
        // including those declared in this class and all of its superclasses.
        // Does not include constructors.
        Person::class.members
        // Returns all functions and properties declared in this class.
        // Does not include members declared in supertypes.
        Person::class.declaredMembers

        // Returns all functions declared in this class,
        // including all non-static methods declared in the class and the superclasses,
        // as well as static methods declared in the class.
        Person::class.functions
        // Returns all functions declared in this class. If this is a Java class,
        // it includes all non-static methods (both extensions and non-extensions) declared
        // in the class and the superclasses, as well as static methods declared in the class.
        Person::class.declaredFunctions

        // Returns non-extension non-static functions declared in this class and all of its superclasses.
        Person::class.memberFunctions
        // Returns non-extension non-static functions declared in this class.
        Person::class.declaredMemberFunctions

        // Returns extension functions declared in this class and all of its superclasses.
        Person::class.memberExtensionFunctions
        Person::class.declaredMemberExtensionFunctions

        // Returns static functions declared in this class.
        Person::class.staticFunctions

        println("=====================")
        println("== companionObject ==")
        println("=====================")

        // Returns a KClass instance representing the companion object of a given class,
        // or null if the class doesn't have a companion object.
        val co: KClass<*>? = Person::class.companionObject
        co?.let {
            co.declaredFunctions.forEach { func ->
                if (!func.isAccessible) func.isAccessible = true
                println("declaredFunctions-> ${func.visibility} ${func.name}: ${func.returnType}")
            }
            println(">>>>>=====================<<<<<")

            co.functions.forEach { func ->
                if (!func.isAccessible) func.isAccessible = true
                println("functions-> ${func.visibility} ${func.name}: ${func.returnType}")
            }
            println(">>>>>=====================<<<<<")

            co.declaredMembers.forEach { func ->
                if (!func.isAccessible) func.isAccessible = true
                println("declaredMembers-> ${func.visibility} ${func.name}: ${func.returnType}")
            }
            println(">>>>>=====================<<<<<")

            co.members.forEach { func ->
                if (!func.isAccessible) func.isAccessible = true
                println("members-> ${func.visibility} ${func.name}: ${func.returnType}")
            }
            println(">>>>>=====================<<<<<")
        }

        // Returns an instance of the companion object of a given class,
        // or null if the class doesn't have a companion object.
        Person::class.companionObjectInstance

        println("=====================")
        println("== staticFunctions ==")
        println("=====================")

        Person::class.staticFunctions.forEach { func ->
            if (!func.isAccessible) func.isAccessible = true
            println("${func.visibility} ${func.name}: ${func.returnType}")
        }

        println("=== End of staticFunctions ===")

        val hrPerson = Person("Chris", 'F', 20)
        val hr = HR("e2021041910000", hrPerson)

        val employee = Employee("e2021041910194", DEPT_ID_DEV, Person("Michael", 'M', 38))
        employee.assignSalary(2200, hr)

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
        println("===== declaredMembers ========")
        println("==============================")

        // Returns all functions and properties declared in this class. Does not include members declared in supertypes.
        val employeeAllMembers = Employee::class.declaredMembers
        employeeAllMembers.forEach { callable ->
            // Allow to get private property value.
            if (!callable.isAccessible) callable.isAccessible = true
            println("${callable.visibility} ${callable.name}: ${callable.returnType} " +
                "-> Unit: ${callable.returnType == Unit::class.createType()}" +
                "\ninstanceParameter--> ${callable.instanceParameter}" +
                "\nvalueParameters  -->${callable.valueParameters}" +
                "\nparameters       -->${callable.parameters}")
            println("--------------------\n")
        }

        println("==============================")
        println("========== members ===========")
        println("==============================")

        // All functions and properties accessible in this class,
        // including those declared in this class and all of its superclasses.
        // Does not include constructors.
        val employeeMembers = Employee::class.members
        employeeMembers.forEach { member ->
            if (!member.isAccessible) member.isAccessible = true
            println("${member.visibility} ${member.name}: ${member.returnType}" +
                "\ninstanceParameter--> ${member.instanceParameter}" +
                "\nvalueParameters  -->${member.valueParameters}" +
                "\nparameters       -->${member.parameters}")
            println("--------------------\n")
        }

        println("==============================")
        println("==== declaredFunctions =======")
        println("==============================")

        // Returns all functions and properties declared in this class. Does not include members declared in supertypes.
        val employeeDeclaredFunctions = Employee::class.declaredFunctions
        employeeDeclaredFunctions.forEach { callable ->
            // Allow to get private property value.
            if (!callable.isAccessible) callable.isAccessible = true
            println("${callable.visibility} ${callable.name}: ${callable.returnType}" +
                "\ninstanceParameter--> ${callable.instanceParameter}" +
                "\nvalueParameters  -->${callable.valueParameters}" +
                "\nparameters       -->${callable.parameters}")
            println("--------------------\n")
        }

        println("==============================")
        println("======== functions ===========")
        println("==============================")

        // Returns all functions declared in this class,
        // including all non-static methods declared in the class and the superclasses,
        // as well as static methods declared in the class.
        val employeeFunctions = Employee::class.functions
        employeeFunctions.forEach { func ->
            if (!func.isAccessible) func.isAccessible = true
            println("${func.visibility} ${func.name}: ${func.returnType} isCompanion: ${Employee::class.isCompanion}" +
                "\ntypeParameters--> ${func.typeParameters}" +
                "\ninstanceParameter--> ${func.instanceParameter}" +
                "\nvalueParameters  -->${func.valueParameters}" +
                "\nparameters       -->${func.parameters}")
            println("--------------------\n")
        }

        println("===============================")
        println("===== Person functions ========")
        println("===============================")

        val personFunctions = Person::class.functions
        personFunctions.forEach { func ->
            if (!func.isAccessible) func.isAccessible = true
            println("${func.visibility} ${func.name}: ${func.returnType} isCompanion: ${Person::class.isCompanion}" +
                "\ntypeParameters--> ${func.typeParameters}" +
                "\ninstanceParameter--> ${func.instanceParameter}" +
                "\nvalueParameters  -->${func.valueParameters}" +
                "\nparameters       -->${func.parameters}")
            println("--------------------\n")
        }

        println("==============================")
        println("== companionObjectInstance ===")
        println("==============================")

        // Returns an instance of the companion object of a given class,
        // or null if the class doesn't have a companion object.
        val companionObjectInstance: Person.Companion = Person::class.companionObjectInstance as Person.Companion
        println("companionObjectInstance=$companionObjectInstance")
        companionObjectInstance.sayHi()
        companionObjectInstance.showClothes(true)

        val companionObject: KClass<*>? = Person::class.companionObject
        companionObject?.let { companion ->
            val func = companion.functions.first { it.name == "showClothes" }
            val companionInstance = Person::class.companionObjectInstance
            func.call(companionInstance, false)
        }
    }
}
