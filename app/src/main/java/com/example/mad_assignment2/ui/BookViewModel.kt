package com.example.mad_assignment2.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mad_assignment2.data.BooksRepository
import com.example.mad_assignment2.data.model.Book
import com.example.mad_assignment2.di.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookViewModel (app: Application) : AndroidViewModel(app) {
    private val repo = BooksRepository(app, NetworkModule.openLibrary)

    // Search (remote)
    val query = MutableStateFlow("")
    private val _remote = MutableStateFlow<List<Book>>(emptyList())
    val remote: StateFlow<List<Book>> = _remote
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun doRemoteSearch() = viewModelScope.launch {
        val q = query.value.trim()
        if (q.isEmpty()) { _remote.value = emptyList(); return@launch }
        _remote.value = repo.searchRemote(q)
    }

    val localQuery = MutableStateFlow("")
    val localBooks: StateFlow<List<Book>> =
        combine(localQuery, repo.allBooks) { q, all ->
            if (q.isBlank()) all else all.filter {
                it.title.contains(q, true) || it.author.contains(
                    q,
                    true
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun save(book: Book) = viewModelScope.launch {repo.save(book)}
    fun update(book: Book) = viewModelScope.launch { repo.update(book) }
    fun remove(book: Book) = viewModelScope.launch {repo.remove(book)}

    fun restoreCloudOnce() {repo.restoreFromCloudOnce()}
}