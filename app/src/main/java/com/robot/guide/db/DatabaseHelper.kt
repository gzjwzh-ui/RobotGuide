package com.robot.guide.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.robot.guide.data.QAItem

/**
 * SQLite数据库帮助类
 * 管理固定问答库的持久化存储
 */
class DatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DB_NAME, null, DB_VERSION
) {

    companion object {
        private const val DB_NAME = "robot_guide.db"
        private const val DB_VERSION = 1

        // 问答表
        private const val TABLE_QA = "qa_items"
        private const val COL_ID = "id"
        private const val COL_QUESTION = "question"
        private const val COL_ANSWER = "answer"
        private const val COL_KEYWORDS = "keywords"       // JSON数组字符串
        private const val COL_MEDIA_REFS = "media_refs"    // JSON数组字符串
        private const val COL_CREATED_AT = "created_at"
        private const val COL_UPDATED_AT = "updated_at"

        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createQA = """
            CREATE TABLE $TABLE_QA (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_QUESTION TEXT NOT NULL,
                $COL_ANSWER TEXT NOT NULL,
                $COL_KEYWORDS TEXT DEFAULT '[]',
                $COL_MEDIA_REFS TEXT DEFAULT '[]',
                $COL_CREATED_AT INTEGER,
                $COL_UPDATED_AT INTEGER
            )
        """.trimIndent()
        db.execSQL(createQA)

        // 创建索引
        db.execSQL("CREATE INDEX idx_qa_question ON $TABLE_QA($COL_QUESTION)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_QA")
        onCreate(db)
    }

    // ==================== QA 操作 ====================

    fun insertQA(qa: QAItem): Long {
        val values = ContentValues().apply {
            put(COL_QUESTION, qa.question)
            put(COL_ANSWER, qa.answer)
            put(COL_KEYWORDS, keywordsToJson(qa.keywords))
            put(COL_MEDIA_REFS, keywordsToJson(qa.mediaRefs))
            put(COL_CREATED_AT, qa.createdAt)
            put(COL_UPDATED_AT, System.currentTimeMillis())
        }
        return writableDatabase.insert(TABLE_QA, null, values)
    }

    fun updateQA(qa: QAItem): Int {
        val values = ContentValues().apply {
            put(COL_QUESTION, qa.question)
            put(COL_ANSWER, qa.answer)
            put(COL_KEYWORDS, keywordsToJson(qa.keywords))
            put(COL_MEDIA_REFS, keywordsToJson(qa.mediaRefs))
            put(COL_UPDATED_AT, System.currentTimeMillis())
        }
        return writableDatabase.update(TABLE_QA, values, "$COL_ID = ?", arrayOf(qa.id.toString()))
    }

    fun deleteQA(id: Long): Int {
        return writableDatabase.delete(TABLE_QA, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun deleteAll(): Int {
        return writableDatabase.delete(TABLE_QA, null, null)
    }

    fun getAllQA(): List<QAItem> {
        val list = mutableListOf<QAItem>()
        val cursor = readableDatabase.query(
            TABLE_QA, null, null, null, null, null, "$COL_UPDATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToQA(it))
            }
        }
        return list
    }

    fun getQAById(id: Long): QAItem? {
        val cursor = readableDatabase.query(
            TABLE_QA, null, "$COL_ID = ?", arrayOf(id.toString()),
            null, null, null
        )
        cursor.use {
            if (it.moveToNext()) {
                return cursorToQA(it)
            }
        }
        return null
    }

    fun getCount(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_QA", null)
        cursor.use {
            if (it.moveToFirst()) return it.getInt(0)
        }
        return 0
    }

    private fun cursorToQA(cursor: Cursor): QAItem {
        return QAItem(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            question = cursor.getString(cursor.getColumnIndexOrThrow(COL_QUESTION)),
            answer = cursor.getString(cursor.getColumnIndexOrThrow(COL_ANSWER)),
            keywords = jsonToKeywords(cursor.getString(cursor.getColumnIndexOrThrow(COL_KEYWORDS))),
            mediaRefs = jsonToKeywords(cursor.getString(cursor.getColumnIndexOrThrow(COL_MEDIA_REFS))),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT)),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPDATED_AT))
        )
    }

    // ==================== JSON 转换 ====================

    private fun keywordsToJson(keywords: List<String>): String {
        if (keywords.isEmpty()) return "[]"
        return keywords.joinToString(",", "[", "]") { "\"${it.replace("\"", "\\\"")}\"" }
    }

    private fun jsonToKeywords(json: String): List<String> {
        if (json == "[]" || json.isBlank()) return emptyList()
        // 简单JSON数组解析
        return json.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"", "\"").replace("\\\"", "\"") }
            .filter { it.isNotBlank() }
    }
}
