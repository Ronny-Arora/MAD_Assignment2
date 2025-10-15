package com.example.mad_assignment2.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mad_assignment2.data.BooksRepository
import com.example.mad_assignment2.data.model.Book
import com.example.mad_assignment2.di.NetworkModule
import com.example.mad_assignment2.data.local.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.net.UnknownHostException
import kotlinx.coroutines.flow.first

class BookViewModel (app: Application) : AndroidViewModel(app) {
    private val repo = BooksRepository(app, NetworkModule.openLibrary)

    // DataStore-backed settings (last query + scroll positions)
    private val settings = SettingsStore.get(app)

    // Search query and results and error
    val query = MutableStateFlow("")
    private val _remote = MutableStateFlow<List<Book>>(emptyList())
    val remote: StateFlow<List<Book>> = _remote
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val error: MutableStateFlow<String?> = MutableStateFlow(null)

    // Local Library (filter, list and saved IDs)
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

    // Set of IDs currently saved in local Room (updates automatically)
    val savedIds = repo.allBooks
        .map { list -> list.map { it.id }.toSet() }        // List<Book> -> Set<String>
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    // Load last query on app start; auto-search if not blank
    init {
        viewModelScope.launch {
            val last = settings.lastQuery.first()
            query.value = last
            if (last.isNotBlank()) doRemoteSearch()
        }
        // Fetch any cloud data at startup (fresh install / second device)
        repo.restoreFromCloudOnce()

        // Small loop to auto-push dirty rows when network is back
        viewModelScope.launch {
            while (true) {
                try {
                    repo.syncDirtyNow()
                } catch (_: Throwable) {
                    // ignore; offline or  error
                }
                delay(15_000) // every ~15s
            }
        }
    }

    // Called by UI on every text change; persists query for relaunch
    fun onSearchQueryChanged(new: String) {
        query.value = new
        viewModelScope.launch { settings.setLastQuery(new) }
    }

    // Fire remote search safely; de-duplicate to avoid LazyColumn key collisions
    fun doRemoteSearch() = viewModelScope.launch {
            val q = query.value.trim()
            if (q.isEmpty()) {
                _remote.value = emptyList()
                error.value = null
                return@launch
            }
            try {
                val list = repo.searchRemote(q)
                _remote.value = list.distinctBy { it.id }   // keep first of same-id results
                error.value = null
            } catch (e: UnknownHostException) {
                _remote.value = emptyList()
                error.value = "No internet connection."
            } catch (e: Exception) {
                _remote.value = emptyList()
                error.value = "Search failed: ${e.javaClass.simpleName}"
            }
        }

    // Persist scroll (Search & Library)
    // Called by UI as the list scrolls
    suspend fun saveSearchScroll(index: Int, offset: Int) {
        settings.setSearchScroll(index, offset)
    }
    // Called by UI after data loads to restore position
    suspend fun readSearchScroll(): Pair<Int, Int> = settings.searchScroll.first()

    suspend fun saveLibScroll(index: Int, offset: Int) {
        settings.setLibScroll(index, offset)
    }
    suspend fun readLibScroll(): Pair<Int, Int> = settings.libScroll.first()

    fun save(book: Book) = viewModelScope.launch {repo.save(book)}
    fun update(book: Book) = viewModelScope.launch { repo.update(book) }

    fun remove(book: Book) = viewModelScope.launch {
        try {
            repo.remove(book)   // calls Room delete + CloudSync.delete()
        } catch (_: Throwable) {
            // swallow – UI should never crash because of a best-effort cloud op
        }
    }

    fun restoreCloudOnce() {repo.restoreFromCloudOnce()}
}