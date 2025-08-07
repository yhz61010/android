package com.leovp.demo.jetpackcomponents.examples.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.leovp.demo.jetpackcomponents.examples.room.dao.WordDao
import com.leovp.demo.jetpackcomponents.examples.room.entity.Word
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 2020/9/4 上午11:42
 */
// Annotates class to be a Room Database with a table (entity) of the Word class
@Database(entities = [Word::class], version = 1, exportSchema = false)
abstract class WordRoomDatabase : RoomDatabase() {

    abstract fun wordDao(): WordDao

    private class WordDatabaseCallback(private val scope: CoroutineScope) : RoomDatabase.Callback() {

        //        override fun onCreate(db: SupportSQLiteDatabase) {
        //            super.onCreate(db)
        //            // Note: If you only want to populate the database the first time the app is launched,
        //            // you can override the onCreate() method within the RoomDatabase.Callback.
        //        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            instance?.let { database ->
                scope.launch {
                    populateDatabase(database.wordDao())
                }
            }
        }

        suspend fun populateDatabase(wordDao: WordDao) {
            // Delete all content here.
            wordDao.deleteAll()

            // Add sample words.
            var word = Word("Hello")
            wordDao.insert(word)
            word = Word("World!")
            wordDao.insert(word)

            word = Word("TODO!")
            wordDao.insert(word)
        }
    }

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var instance: WordRoomDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): WordRoomDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return instance ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WordRoomDatabase::class.java,
                    "word_database"
                )
                    .addCallback(WordDatabaseCallback(scope))
                    .build()
                Companion.instance = instance
                // return instance
                instance
            }
        }
    }
}
