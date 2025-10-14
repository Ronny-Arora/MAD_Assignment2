package com.example.mad_assignment2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mad_assignment2.data.model.Book
import com.example.mad_assignment2.ui.BookViewModel

class MainActivity : ComponentActivity() {

    // Use your ViewModel (must exist in com.example.mad_assignment2.ui)
    private val vm: BookViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Use your Material3 app theme from themes.xml
            MaterialTheme {
                App(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: BookViewModel) {
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pocket Library") }) }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Search") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("My Library") })
            }
            when (tab) {
                0 -> SearchScreen(vm)
                1 -> LibraryScreen(vm)
            }
        }
    }
}

/* ───────────────────────────────────────────────────────────────
   Your composables (unchanged, just kept in the same file for now)
   You can move them to separate files later if you want.
   ─────────────────────────────────────────────────────────────── */

@Composable
fun SearchScreen(vm: BookViewModel) {
    val query by vm.query.collectAsState()
    val results by vm.remote.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            label = { Text("Title or Author") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row { Button(onClick = vm::doRemoteSearch) { Text("Search Online") } }

        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(results) { book -> BookRow(book, onSave = { vm.save(book) }) }
        }

        // Manual entry (fallback when offline) — simplified quick add
        var manualOpen by remember { mutableStateOf(false) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { manualOpen = true }) { Text("Add Manually (offline ok)") }
        if (manualOpen) ManualAddDialog(onDismiss = { manualOpen = false }, onAdd = {
            vm.save(it); manualOpen = false
        })
    }
}

@Composable
fun BookRow(book: Book, onSave: (Book) -> Unit, onRemove: ((Book) -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.padding(12.dp)) {
            AsyncImage(model = book.coverUrl, contentDescription = book.title, modifier = Modifier.size(64.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.author, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(book.year, style = MaterialTheme.typography.bodySmall)
            }
            if (onRemove == null) Button(onClick = { onSave(book) }) { Text("Save") }
            else OutlinedButton(onClick = { onRemove(book) }) { Text("Remove") }
        }
    }
}

@Composable
fun LibraryScreen(vm: BookViewModel) {
    val q by vm.localQuery.collectAsState()
    val libraryItems by vm.localBooks.collectAsState()   // ← renamed

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = q,
            onValueChange = { vm.localQuery.value = it },
            label = { Text("Search my library") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(libraryItems) { book ->                // ← now calls the DSL function
                BookRow(book, onSave = {}, onRemove = { vm.remove(it) })
            }
        }
    }
}

@Composable
fun ManualAddDialog(onDismiss: () -> Unit, onAdd: (Book) -> Unit) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && author.isNotBlank(),
                onClick = {
                    val id = (title + author + year).hashCode().toString()
                    onAdd(Book(id, title.trim(), author.trim(), year.trim().ifBlank { "—" }))
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add Book Manually") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Author") })
                OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year (optional)") })
            }
        }
    )
}
