package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserKeyPairEntity::class,
        ContactKeyEntity::class,
        ChatEntity::class,
        MessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CipherGramDatabase : RoomDatabase() {
    abstract fun cipherGramDao(): CipherGramDao

    companion object {
        @Volatile
        private var INSTANCE: CipherGramDatabase? = null

        fun getDatabase(context: Context): CipherGramDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CipherGramDatabase::class.java,
                    "cipher_gram_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
