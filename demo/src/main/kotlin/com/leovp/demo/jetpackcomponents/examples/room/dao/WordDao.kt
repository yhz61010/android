package com.leovp.demo.jetpackcomponents.examples.room.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.leovp.demo.jetpackcomponents.examples.room.entity.Word

/**
 * Author: Michael Leo
 * Date: 2020/9/4 上午11:36
 */
@Dao
interface WordDao {
    // By default, to avoid poor UI performance, Room doesn't allow you
    // to issue queries on the main thread. When Room queries return LiveData,
    // the queries are automatically run asynchronously on a background thread.
    @Query("SELECT * from word_table ORDER BY word ASC")
    fun getAlphabetizedWords(): LiveData<List<Word>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: Word)

    @Query("DELETE FROM word_table")
    suspend fun deleteAll()

    /**
     * @param word The `word` to be modified. Attention, the `word` must be with the primary key that to be modified.
     *
     * Or you can do update like this below:
     * ```kotlin
     * @Query("UPDATE word_table SET word=:word WHERE id=:id")
     * suspend fun delete(id: Int, word: String)
     * ```
     */
    @Update
    suspend fun update(word: Word)

    /**
     * @param word The `word` to be modified. Attention, the `word` must be with the primary key that to be modified.
     *
     * Or you can do delete like this below:
     * ```kotlin
     * @Query("DELETE FROM word_table WHERE id = :id")
     * suspend fun delete(id: Int)
     * ```
     */
    @Delete
    suspend fun delete(word: Word)
}
