package com.ho1ho.leoandroidbaseutil.jetpack_components.examples.room

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.ho1ho.leoandroidbaseutil.jetpack_components.examples.room.entity.Word
import com.ho1ho.leoandroidbaseutil.jetpack_components.examples.room.repository.WordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 2020/9/4 上午11:50
 */
class WordViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: WordRepository

    // Using LiveData and caching what getAlphabetizedWords returns has several benefits:
    // - We can put an observer on the data (instead of polling for changes) and only update the
    //   the UI when the data actually changes.
    // - Repository is completely separated from the UI through the ViewModel.
    val allWords: LiveData<List<Word>>

    init {
        val wordsDao = WordRoomDatabase.getDatabase(application, viewModelScope).wordDao()
        repository = WordRepository(wordsDao)
        allWords = repository.allWords
    }

    /**
     * Launching a new coroutine to insert the data in a non-blocking way
     */
    fun insert(word: Word) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(word)
    }

    fun update(word: Word) = viewModelScope.launch(Dispatchers.IO) {
        repository.update(word)
    }

    fun delete(word: Word) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(word)
    }
}