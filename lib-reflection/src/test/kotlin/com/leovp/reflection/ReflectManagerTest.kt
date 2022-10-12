@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.reflection

import com.leovp.reflection.testclass.Creature
import com.leovp.reflection.testclass.DEPT_ID_DEV
import com.leovp.reflection.testclass.DataClassOneArg
import com.leovp.reflection.testclass.Employee
import com.leovp.reflection.testclass.HR
import com.leovp.reflection.testclass.JavaTestClass
import com.leovp.reflection.testclass.JavaTestClass.NoArgClass
import com.leovp.reflection.testclass.Person
import com.leovp.reflection.testclass.PrivateClass
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

        val rfltCreature3: Any = ReflectManager
            .reflect("com.leovp.reflection.testclass.Creature")
            .newInstance()
            .get()
        assertEquals("Get a new creature.", rfltCreature3.toString())

        val rfltCreature4: Creature = ReflectManager.reflect(rfltCreature1).newInstance().get()
        assertEquals("Get a new creature.", rfltCreature4.toString())

        // ----------

        val rfltPerson1: Person = ReflectManager.reflect(Person::class).newInstance("Man1", 'M', 38).get()
        assertEquals("Man1[M] is 38 years old.", rfltPerson1.toString())

        val rfltPerson2 = ReflectManager.reflect(Person::class.java).newInstance("Man2", 'M', 28).get<Person>()
        assertEquals("Man2[M] is 28 years old.", rfltPerson2.toString())

        val rfltPerson3: Any = ReflectManager
            .reflect("com.leovp.reflection.testclass.Person")
            .newInstance("Woman1", 'F', 20)
            .get()
        assertEquals("Woman1[F] is 20 years old.", rfltPerson3.toString())

        val rfltPerson4: Person = ReflectManager.reflect(rfltPerson1).newInstance("Woman2", 'F', 19).get()
        assertEquals("Woman2[F] is 19 years old.", rfltPerson4.toString())

        val dataClass1: DataClassOneArg = ReflectManager.reflect(DataClassOneArg::class).newInstance("DC1").get()
        assertEquals("DataClassOneArg(arg1=DC1)", dataClass1.toString())

        val dataClass2: Any = ReflectManager
            .reflect("com.leovp.reflection.testclass.DataClassTwoArg")
            .newInstance("DC2", 20021008)
            .get()
        println("dataClass2=${dataClass2::class.java}")
        assertEquals("DataClassTwoArg(arg1=DC2, num=20021008)", dataClass2.toString())
    }

    @Test
    fun property() {
        // ====================
        // ===== get
        // ====================
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

        // ====================
        // ===== set
        // ====================
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

    // ========================================
    // ========================================
    // ========================================

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

        // ReflectManager.reflect(javaPerson1).property("PUBLIC_NAME", "Modified PUBLIC NAME")
        // assertEquals("Modified PUBLIC NAME", JavaTestClass.JavaPerson.PUBLIC_NAME)
        // will cause the following exception:
        // Can not set static final java.lang.String field com.leovp.reflection.testclass.JavaTestClass$JavaPerson.PUBLIC_NAME to java.lang.String
        // java.lang.IllegalAccessException: Can not set static final java.lang.String field com.leovp.reflection.testclass.JavaTestClass$JavaPerson.PUBLIC_NAME to java.lang.String
        // 	at java.base/jdk.internal.reflect.UnsafeFieldAccessorImpl.throwFinalFieldIllegalAccessException(UnsafeFieldAccessorImpl.java:76)
        // 	at java.base/jdk.internal.reflect.UnsafeFieldAccessorImpl.throwFinalFieldIllegalAccessException(UnsafeFieldAccessorImpl.java:80)
        // 	at java.base/jdk.internal.reflect.UnsafeQualifiedStaticObjectFieldAccessorImpl.set(UnsafeQualifiedStaticObjectFieldAccessorImpl.java:77)

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

    // ========================================
    // ========================================
    // ========================================

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
