package com.leovp.demo.jetpack_components.examples.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Author: Michael Leo
 * Date: 2020/9/4 上午11:31
 *
 * You can find a complete list of annotations in the [Room package summary reference](https://developer.android.com/reference/kotlin/androidx/room/package-summary.html).
 */
@Entity(tableName = "word_table")
class Word(@ColumnInfo(name = "word") val word: String) {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
}
