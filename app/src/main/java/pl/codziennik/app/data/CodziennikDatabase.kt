package pl.codziennik.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

data class Habit(val id: String, val title: String, val icon: String, val active: Boolean, val position: Int)
data class DayRecord(val note: String, val photoUri: String?)
data class ShoppingItem(val id: Long, val title: String, val checked: Boolean)
data class Subtask(val id: Long, val title: String, val completed: Boolean)

/** Local source of truth. It deliberately keeps user data on-device. */
class CodziennikDatabase(context: Context) : SQLiteOpenHelper(context, "codziennik-2.db", null, 2) {
    private val legacy = context.getSharedPreferences("codziennik", Context.MODE_PRIVATE)
    private val legacyTasks = listOf(
        "Dzień bez nałogów", "30 min ćwiczeń siłowych", "Coś dla ogrodu",
        "Fotografia", "Spacer", "Suplementacja", "Własne zadanie dnia"
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE habits (id TEXT PRIMARY KEY, title TEXT NOT NULL, icon TEXT NOT NULL, active INTEGER NOT NULL, position INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE day_entries (day TEXT PRIMARY KEY, note TEXT NOT NULL DEFAULT '', photo_uri TEXT)")
        db.execSQL("CREATE TABLE completions (day TEXT NOT NULL, habit_id TEXT NOT NULL, completed INTEGER NOT NULL, PRIMARY KEY(day, habit_id), FOREIGN KEY(habit_id) REFERENCES habits(id))")
        createV2Tables(db)
        seedAndMigrate(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { if (oldVersion < 2) createV2Tables(db) }
    private fun createV2Tables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS shopping_items (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, checked INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS subtasks (id INTEGER PRIMARY KEY AUTOINCREMENT, day TEXT NOT NULL, habit_id TEXT NOT NULL, title TEXT NOT NULL, completed INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS habit_notes (day TEXT NOT NULL, habit_id TEXT NOT NULL, note TEXT NOT NULL, PRIMARY KEY(day, habit_id))")
    }

    private fun seedAndMigrate(db: SQLiteDatabase) {
        val icons = listOf("🚫", "🏃", "🌿", "📸", "🚶", "💊", "✅")
        val selected = legacyTasks.indices.map { index ->
            if (legacy.contains("tile-$index")) legacy.getBoolean("tile-$index", true) else true
        }.let { flags -> if (flags.none { it }) List(legacyTasks.size) { true } else flags }
        legacyTasks.forEachIndexed { index, title ->
            val active = selected[index]
            val id = "legacy-$index"
            db.insert("habits", null, ContentValues().apply {
                put("id", id); put("title", title); put("icon", icons[index]); put("active", if (active) 1 else 0); put("position", index)
            })
        }
        legacy.all.forEach { (key, value) ->
            val match = Regex("(\\d{4}-\\d{2}-\\d{2})-(\\d+|note|photo)").matchEntire(key) ?: return@forEach
            val day = match.groupValues[1]; val field = match.groupValues[2]
            when (field) {
                "note" -> upsertDay(db, day, value as? String ?: "", null, false)
                "photo" -> upsertDay(db, day, "", value as? String, true)
                else -> if (value as? Boolean == true && field.toIntOrNull() in legacyTasks.indices) {
                    db.insert("completions", null, ContentValues().apply { put("day", day); put("habit_id", "legacy-$field"); put("completed", 1) })
                }
            }
        }
    }

    private fun upsertDay(db: SQLiteDatabase, day: String, value: String, photo: String?, isPhoto: Boolean) {
        val existing = db.query("day_entries", arrayOf("note", "photo_uri"), "day=?", arrayOf(day), null, null, null).use {
            if (it.moveToFirst()) Pair(it.getString(0), it.getString(1)) else Pair("", null)
        }
        db.insertWithOnConflict("day_entries", null, ContentValues().apply {
            put("day", day); put("note", if (isPhoto) existing.first else value); put("photo_uri", if (isPhoto) photo else existing.second)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun habits(): List<Habit> = readableDatabase.query("habits", null, "active=1", null, null, null, "position").use { c ->
        buildList { while (c.moveToNext()) add(Habit(c.getString(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("title")), c.getString(c.getColumnIndexOrThrow("icon")), c.getInt(c.getColumnIndexOrThrow("active")) == 1, c.getInt(c.getColumnIndexOrThrow("position")))) }
    }
    fun allHabits(): List<Habit> = readableDatabase.query("habits", null, null, null, null, null, "position").use { c -> buildList { while (c.moveToNext()) add(Habit(c.getString(0), c.getString(1), c.getString(2), c.getInt(3)==1, c.getInt(4))) } }
    fun record(day: String): DayRecord = readableDatabase.query("day_entries", arrayOf("note", "photo_uri"), "day=?", arrayOf(day), null, null, null).use { if (it.moveToFirst()) DayRecord(it.getString(0), it.getString(1)) else DayRecord("", null) }
    fun completed(day: String): Set<String> = readableDatabase.query("completions", arrayOf("habit_id"), "day=? AND completed=1", arrayOf(day), null, null, null).use { c -> buildSet { while(c.moveToNext()) add(c.getString(0)) } }
    fun setCompleted(day: String, habitId: String, value: Boolean) { if (value) writableDatabase.insertWithOnConflict("completions", null, ContentValues().apply { put("day",day); put("habit_id",habitId); put("completed",1) }, SQLiteDatabase.CONFLICT_REPLACE) else writableDatabase.delete("completions", "day=? AND habit_id=?", arrayOf(day,habitId)) }
    fun saveNote(day: String, note: String) = upsertDay(writableDatabase, day, note, null, false)
    fun savePhoto(day: String, uri: String?) = upsertDay(writableDatabase, day, "", uri, true)
    fun addHabit(title: String) { val position = allHabits().size; writableDatabase.insert("habits", null, ContentValues().apply { put("id", UUID.randomUUID().toString()); put("title", title.trim()); put("icon", "✦"); put("active",1); put("position",position) }) }
    fun setHabitActive(id: String, active: Boolean) { writableDatabase.update("habits", ContentValues().apply { put("active", if(active) 1 else 0) }, "id=?", arrayOf(id)) }
    fun renameHabit(id: String, title: String) = writableDatabase.update("habits", ContentValues().apply { put("title", title.trim()) }, "id=?", arrayOf(id))
    fun deleteHabit(id: String) { writableDatabase.delete("subtasks", "habit_id=?", arrayOf(id)); writableDatabase.delete("completions", "habit_id=?", arrayOf(id)); writableDatabase.delete("habits", "id=?", arrayOf(id)) }
    fun subtasks(day: String, habitId: String): List<Subtask> = readableDatabase.query("subtasks", null, "day=? AND habit_id=?", arrayOf(day,habitId), null,null,"id").use { c -> buildList { while(c.moveToNext()) add(Subtask(c.getLong(0),c.getString(3),c.getInt(4)==1)) } }
    fun addSubtask(day: String, habitId: String, title: String): Boolean { if(subtasks(day,habitId).size >= 5) return false; return writableDatabase.insert("subtasks",null,ContentValues().apply { put("day",day);put("habit_id",habitId);put("title",title.trim()) }) != -1L }
    fun setSubtask(id: Long, completed: Boolean) { writableDatabase.update("subtasks",ContentValues().apply { put("completed",if(completed) 1 else 0) },"id=?",arrayOf(id.toString())) }
    fun shopping(): List<ShoppingItem> = readableDatabase.query("shopping_items",null,null,null,null,null,"checked, id").use { c -> buildList { while(c.moveToNext()) add(ShoppingItem(c.getLong(0),c.getString(1),c.getInt(2)==1)) } }
    fun addShopping(title: String) { writableDatabase.insert("shopping_items",null,ContentValues().apply { put("title",title.trim()) }) }
    fun setShopping(id: Long, checked: Boolean) { writableDatabase.update("shopping_items",ContentValues().apply { put("checked",if(checked) 1 else 0) },"id=?",arrayOf(id.toString())) }
    fun deleteShopping(id: Long) { writableDatabase.delete("shopping_items","id=?",arrayOf(id.toString())) }
    fun clearCheckedShopping() { writableDatabase.delete("shopping_items","checked=1",null) }
}
