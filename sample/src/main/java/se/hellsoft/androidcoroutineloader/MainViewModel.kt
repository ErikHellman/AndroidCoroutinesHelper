package se.hellsoft.androidcoroutineloader

import android.app.Application
import android.arch.lifecycle.AndroidViewModel

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MainRepository(application)

    fun loadNotes() = repository.loadAll()

    fun saveNote(note: Note) = repository.saveNote(note)
}