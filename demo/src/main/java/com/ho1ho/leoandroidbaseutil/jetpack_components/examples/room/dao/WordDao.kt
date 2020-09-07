package com.ho1ho.leoandroidbaseutil.jetpack_components.examples.room.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.ho1ho.leoandroidbaseutil.jetpack_components.examples.room.entity.Word

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
     */
    @Update
    suspend fun update(word: Word)

    /**
     * @param word The `word` to be modified. Attention, the `word` must be with the primary key that to be modified.
     */
    @Delete
    suspend fun delete(word: Word)
}