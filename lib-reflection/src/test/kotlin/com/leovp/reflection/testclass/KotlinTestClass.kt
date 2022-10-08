package com.leovp.reflection.testclass

/**
 * Author: Michael Leo
 * Date: 2022/10/8 10:23
 */
internal const val DEPT_ID_HR = 100
internal const val DEPT_ID_DEV = 1000

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

        fun sayHi(): String {
            return "Employee said: Hi."
        }

        fun globalSay(content: String): String {
            return "Employee said to global: $content"
        }
    }

    val company: String = COMPANY

    private var salary: Int = 0

    var comment: String? = null

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

    fun startWorking(): String {
        return action(
            "${p.name}[$employeeId] starts working at ${System.currentTimeMillis()}.",
            10
        )
    }

    fun stopWorking(time: Long): String {
        return action("${p.name}[$employeeId] stops working at $time.", -10)
    }

    override fun toString(): String =
        "[$company] Employee($p) with ID $employeeId works in $deptId departure. Salary: $salary."
}

open class Person(name: String, sex: Char, age: Int) : Creature() {
    var name: String = name
        private set
    var sex: Char = sex
        private set
    var age: Int = age
        private set

    private fun secretMethod(content: String): String {
        return "$name does [secret method]($content)."
    }

    fun say(content: String): String {
        return "$name says: $content"
    }

    fun action(action: String, exceptResult: Int): String {
        return "$name do [$action] with exceptResult: $exceptResult."
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

    companion object {
        const val NATION = "China"

        private const val SECRET_ID = "Secret ID"

        fun showClothes(all: Boolean): String = "Someone shows all($all) clothes."

        fun sayHi() {
            println("Someone says hi.")
        }
    }
}

open class Creature {
    open fun alive(): Boolean = true

    override fun toString(): String = "Get a new creature."
}
