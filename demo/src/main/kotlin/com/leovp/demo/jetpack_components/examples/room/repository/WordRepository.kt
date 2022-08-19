package com.leovp.demo.jetpack_components.examples.room.repository

import androidx.lifecycle.LiveData
import com.leovp.demo.jetpack_components.examples.room.dao.WordDao
import com.leovp.demo.jetpack_components.examples.room.entity.Word

/**
 * Author: Michael Leo
 * Date: 2020/9/4 上午11:47
 */
// Declares the DAO as a private property in the constructor. Pass in the DAO
// instead of the whole database, because you only need access to the DAO
class WordRepository(private val wordDao: WordDao) {

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    val allWords: LiveData<List<Word>> = wordDao.getAlphabetizedWords()

    suspend fun insert(word: Word) {
        wordDao.insert(word)
    }

    suspend fun update(word: Word) {
        wordDao.update(word)
    }

    suspend fun delete(word: Word) {
        wordDao.delete(word)
    }
}
