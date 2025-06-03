package com.leovp.androidbase

import android.util.Log
import com.leovp.androidbase.exts.kotlin.TreeElement
import com.leovp.androidbase.exts.kotlin.printTree
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

/**
 * Author: Michael Leo
 * Date: 2021/8/2 14:07
 */
@RunWith(PowerMockRunner::class)
@PrepareForTest(Log::class)
class PrintUExtTest {

    @Test
    fun testTreePrint() {
        val level3Sub1 = TreeElement("level3_1", null)
        val level3Sub2 = TreeElement("level3_2", null)
        val level3Sub3 = TreeElement("level3_1", null)
        val level3 = arrayListOf(level3Sub1, level3Sub2, level3Sub3)

        val level2Sub1 = TreeElement("level2_1", level3)
        val level2Sub2 = TreeElement("level2_2", level3)
        val level2 = arrayListOf(level2Sub1, level2Sub2)

        val level1Sub1 = TreeElement("level1_1", level2)
        val level1Sub2 = TreeElement("level1_2", level2)
        val level1 = arrayListOf(level1Sub1, level1Sub2)

        val root = TreeElement("root", level1)

        printTree(root, "", true) { println(it) }
    }
}
