package com.example.shaketilt

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Score(
    val levelId: Int,
    val timeMs: Long,
    val timestamp: Long
)

class LocalDBHelper(context: Context) :

    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "game_scores.db"
        private const val DATABASE_VERSION = 3
        private const val TABLE_NAME = "scores"
        private const val COL_ID = "id"
        private const val COL_LEVEL_ID = "level_id"
        private const val COL_TIME_MS = "time_ms"
        private const val COL_TIMESTAMP = "timestamp_ms"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_LEVEL_ID INTEGER,
                $COL_TIME_MS INTEGER,
                $COL_TIMESTAMP INTEGER
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun saveScore(levelId: Int, timeMs: Long) {
        val now = System.currentTimeMillis()
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put(COL_LEVEL_ID, levelId)
                put(COL_TIME_MS, timeMs)
                put(COL_TIMESTAMP, now)
            }
            db.insert(TABLE_NAME, null, values)

            val cursor = db.rawQuery("SELECT $COL_ID, $COL_LEVEL_ID, $COL_TIME_MS, $COL_TIMESTAMP FROM $TABLE_NAME", null)
            println("=== Current Scores in DB ===")
            while (cursor.moveToNext()) {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID))
                val lvl = cursor.getInt(cursor.getColumnIndexOrThrow(COL_LEVEL_ID))
                val score = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIME_MS))
                val ts = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP))
                println("ID $id | Level $lvl : $score ms, saved at $ts")
            }
            cursor.close()
            println("============================")
        }
    }

    fun getTopScores(levelId: Int): List<Score> {
        val scores = mutableListOf<Score>()
        readableDatabase.use { db ->
            val cursor = db.query(
                TABLE_NAME,
                arrayOf(COL_LEVEL_ID, COL_TIME_MS, COL_TIMESTAMP),
                "$COL_LEVEL_ID = ?", arrayOf(levelId.toString()),
                null, null,
                "$COL_TIME_MS ASC"
            )
            while (cursor.moveToNext()) {
                val level = cursor.getInt(cursor.getColumnIndexOrThrow(COL_LEVEL_ID))
                val timeMs = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIME_MS))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP))
                scores.add(Score(level, timeMs, timestamp))
            }
            cursor.close()
        }
        return scores
    }

    fun clearAllScores() {
        writableDatabase.use { db ->
            db.delete(TABLE_NAME, null, null)
        }
    }
}