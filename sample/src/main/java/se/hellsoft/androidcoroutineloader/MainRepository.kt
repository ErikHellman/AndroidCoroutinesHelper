package se.hellsoft.androidcoroutineloader

import android.app.Application
import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*

@Entity(tableName = "note")
data class Note(@PrimaryKey(autoGenerate = true) val id: Int, val text: String)

@Dao
interface NotesDao {
    @Query("SELECT * FROM note")
    fun fetchAll(): LiveData<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveNote(note: Note)

    @Delete
    fun deleteNote(note: Note)
}

@Database(entities = [Note::class], version = 1, exportSchema = false)
abstract class NotesDb : RoomDatabase() {
    abstract fun notesDao(): NotesDao
}

class MainRepository(application: Application) {
    private val notesDao: NotesDao by lazy {
        val db = Room.databaseBuilder(application, NotesDb::class.java, "notes.db").build()
        return@lazy db.notesDao()
    }

    fun loadAll() = notesDao.fetchAll()

    fun saveNote(note: Note) = notesDao.saveNote(note)

    fun deleteNote(note: Note) = notesDao.deleteNote(note)
}