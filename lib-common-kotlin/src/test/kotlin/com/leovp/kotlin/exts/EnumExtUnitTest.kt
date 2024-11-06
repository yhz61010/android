package com.leovp.kotlin.exts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Author: Michael Leo
 * Date: 2024/11/6 09:47
 */
class EnumExtUnitTest {

    enum class TouchType(val touchVal: Int) {
        DOWN(0), UP(1), MOVE(2)
    }

    enum class ColorName(val shortName: String) {
        RED("r"),
        GREEN("g"),
        BLUE("b"),
    }

    @Test
    fun findBy() {
        val typeDown = TouchType::touchVal findBy 0
        assertEquals(TouchType.DOWN, typeDown)
        assertNotEquals(TouchType.UP, typeDown)

        val redColor = ColorName::shortName findBy "r"
        assertEquals(ColorName.RED, redColor)
        assertNotEquals(ColorName.GREEN, redColor)
    }
}
