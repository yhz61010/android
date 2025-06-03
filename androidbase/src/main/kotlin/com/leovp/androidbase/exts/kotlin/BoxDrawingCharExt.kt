@file:Suppress("unused")

package com.leovp.androidbase.exts.kotlin

import java.io.File

/**
 * Author: Michael Leo
 * Date: 2023/6/21 08:50
 */

private const val BOX_DRAWING_CHAR_LAST = "└── "
private const val BOX_DRAWING_CHAR_MIDDLE = "├── "
private const val BOX_DRAWING_CHAR_SPACE = "    "
private const val BOX_DRAWING_CHAR_LINE = "│   "

data class TreeElement(val name: String, var children: List<TreeElement>?)

/**
 * printTree(root, "", true) { LogContext.log.i(it) }
 */
fun printTree(root: TreeElement, indent: String, isLast: Boolean, printCallback: (String) -> Unit) {
    printCallback(indent + (if (isLast) BOX_DRAWING_CHAR_LAST else BOX_DRAWING_CHAR_MIDDLE) + root.name)

    val fixedChildren = root.children
    if (fixedChildren?.isNotEmpty() == true) {
        fixedChildren.forEachIndexed { index, child ->
            val isLastChild = index == fixedChildren.size - 1
            val childIndent = indent + (if (isLast) BOX_DRAWING_CHAR_SPACE else BOX_DRAWING_CHAR_LINE)
            printTree(child, childIndent, isLastChild, printCallback)
        }
    }
}

/**
 * printFileTree(rootDir, "", true) { println(it) }
 */
fun printFileTree(file: File, indent: String, isLast: Boolean, printCallback: (String) -> Unit) {
    printCallback(indent + (if (isLast) BOX_DRAWING_CHAR_LAST else BOX_DRAWING_CHAR_MIDDLE) + file.name)

    if (file.isDirectory) {
        val children = file.listFiles()
        children?.forEachIndexed { index, child ->
            val isLastChild = index == children.size - 1
            val childIndent = indent + (if (isLast) BOX_DRAWING_CHAR_SPACE else BOX_DRAWING_CHAR_LINE)
            printFileTree(child, childIndent, isLastChild, printCallback)
        }
    }
}
