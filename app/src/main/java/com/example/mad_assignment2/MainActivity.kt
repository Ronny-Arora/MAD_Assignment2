@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mad_assignment2

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow   // <-- correct package for snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mad_assignment2.auth.AuthViewModel
import com.example.mad_assignment2.data.model.Book
import com.example.mad_assignment2.ui.BookViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
class MainActivity : ComponentActivity() {
    private val bookVm: BookViewModel by viewModels()
    private val authVm: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseFirestore.setLoggingEnabled(true)

        setContent {
            MaterialTheme {
                AppWithAuth(bookVm, authVm)
            }
        }
    }
}

/* ----------------------------- AUTH SHELL ----------------------------- */

@Composable
fun AppWithAuth(bookVm: BookViewModel, authVm: AuthViewModel) {
    val ui by authVm.ui.collectAsState()
    var showAuthDialog by rememberSaveable { mutableStateOf(false) }

    if (ui.loading && ui.userEmail == null && !ui.isAnon) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        AnonymousBanner(authVm) { showAuthDialog = true }
        App(bookVm, authVm)
    }

    if (showAuthDialog) {
        AuthDialog(authVm = authVm, onDismiss = { showAuthDialog = false })
    }
}

@Composable
fun AnonymousBanner(authVm: AuthViewModel, onCreateAccountClick: () -> Unit) {
    val ui by authVm.ui.collectAsState()
    if (!ui.isAnon) return

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("You're browsing as a guest", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Create an account to sync your library across reinstalls",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onCreateAccountClick) { Text("Create Account") }
        }
    }
}

@Composable
fun AuthDialog(authVm: AuthViewModel, onDismiss: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isCreateMode by remember { mutableStateOf(true) }
    val ui by authVm.ui.collectAsState()

    LaunchedEffect(ui.userEmail, ui.isAnon) {
        if (ui.userEmail != null && !ui.isAnon) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCreateMode) "Create Account" else "Sign In") },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    enabled = !ui.loading,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    enabled = !ui.loading,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (ui.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(ui.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { isCreateMode = !isCreateMode }, enabled = !ui.loading) {
                    Text(if (isCreateMode) "Already have an account? Sign in" else "Don't have an account? Create one")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isCreateMode && ui.isAnon) authVm.linkAnonToEmail(email, password)
                    else authVm.signIn(email, password)
                },
                enabled = !ui.loading && email.isNotBlank() && password.isNotBlank()
            ) {
                if (ui.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (isCreateMode) "Create" else "Sign In")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !ui.loading) { Text("Cancel") } }
    )
}

/* --------------------------- MAIN APP SHELL --------------------------- */

@Composable
fun App(bookVm: BookViewModel, authVm: AuthViewModel) {
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLandscape) LandscapeHome(bookVm) else PortraitHome(bookVm, authVm)
}

@Composable
private fun PortraitHome(bookVm: BookViewModel, authVm: AuthViewModel) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    val ui by authVm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pocket Library") },
                actions = {
                    IconButton(onClick = { showMenu = true }) { Text("⋮", style = MaterialTheme.typography.headlineMedium) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (ui.isAnon) {
                            DropdownMenuItem(text = { Text("Create Account…") }, onClick = { showMenu = false; /* open banner button */ })
                        } else {
                            DropdownMenuItem(text = { Text("Signed in as ${ui.userEmail}") }, onClick = {}, enabled = false)
                            DropdownMenuItem(text = { Text("Sign Out") }, onClick = { showMenu = false; authVm.signOut() })
                        }
                    }
                }
            )
        }
    ) { inner ->
        Column(Modifier.padding(inner)) {
            TabRow(selectedTabIndex = selected) {
                Tab(selected = selected == 0, onClick = { selected = 0 }, text = { Text("Search") })
                Tab(selected = selected == 1, onClick = { selected = 1 }, text = { Text("My Library") })
            }
            if (selected == 0) SearchTab(bookVm) else LibraryTab(bookVm)
        }
    }
}

/* ------------------------------ SEARCH UI ----------------------------- */

@Composable
fun SearchTab(vm: BookViewModel) {
    val query by vm.query.collectAsState()
    val results by vm.remote.collectAsState()
    val savedIds by vm.savedIds.collectAsState()
    val err by vm.error.collectAsState()
    var manualOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        // IME action triggers remote search too
        OutlinedTextField(
            value = query,
            onValueChange = { vm.onSearchQueryChanged(it) },
            label = { Text("Title or Author") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { vm.doRemoteSearch() }
            )
        )

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::doRemoteSearch, enabled = query.isNotBlank(), modifier = Modifier.weight(1f)) {
                Text("Search Online")
            }
            OutlinedButton(onClick = { manualOpen = true }, modifier = Modifier.weight(1f)) {
                Text("Add Manually")
            }
        }

        if (err != null) {
            Spacer(Modifier.height(6.dp))
            Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(12.dp))

        // Simple, reliable list with stable keys
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(results, key = { it.id }) { book ->
                val isSaved = book.id in savedIds
                BookRow(
                    book = book,
                    isSaved = isSaved,
                    onToggleSave = { currentlySaved -> if (currentlySaved) vm.remove(book) else vm.save(book) }
                )
            }
        }
    }

    if (manualOpen) {
        ManualAddDialog(
            onDismiss = { manualOpen = false },
            onAdd = { vm.save(it); manualOpen = false }
        )
    }
}


/* ------------------------------ LIBRARY UI ---------------------------- */

@Composable
fun LibraryTab(vm: BookViewModel) {
    val q by vm.localQuery.collectAsState()
    val items by vm.localBooks.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        OutlinedTextField(
            value = q,
            onValueChange = { vm.localQuery.value = it },
            label = { Text("Search my library") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No books saved yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(items, key = { it.id }) { book ->
                    BookRow(book = book, onRemove = { vm.remove(it) })
                }
            }
        }
    }
}

/* -------------------------- LANDSCAPE/TABLET UI ----------------------- */

@Composable
private fun LandscapeHome(vm: BookViewModel) {
    val config = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Scaffold { inner ->
        Row(Modifier.padding(inner).fillMaxSize()) {
            NavigationRail(Modifier.fillMaxHeight()) {
                NavigationRailItem(selected = selected == 0, onClick = { selected = 0 }, icon = { Text("S") }, label = { Text("Search") })
                NavigationRailItem(selected = selected == 1, onClick = { selected = 1 }, icon = { Text("L") }, label = { Text("My Library") })
            }
            if (selected == 0) SearchLandscape(vm, isTablet) else LibraryLandscape(vm, isTablet)
        }
    }
}

@Composable
private fun SearchLandscape(vm: BookViewModel, isTablet: Boolean) {
    val query by vm.query.collectAsState()
    val results by vm.remote.collectAsState()
    val savedIds by vm.savedIds.collectAsState()
    val err by vm.error.collectAsState()

    if (isTablet) {
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        val selected = results.firstOrNull { it.id == selectedId } ?: results.firstOrNull() ?: Book.EMPTY

        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(0.65f).fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("Pocket Library", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query, onValueChange = { vm.onSearchQueryChanged(it) },
                        label = { Text("Title or Author") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.doRemoteSearch() })
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = vm::doRemoteSearch, enabled = query.isNotBlank()) { Text("Search") }
                }
                if (err != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))

                val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
                LaunchedEffect(gridState) {
                    snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                        .distinctUntilChanged()
                        .collect { (i, o) -> vm.saveSearchScroll(i, o) }
                }
                var restored by remember { mutableStateOf(false) }
                LaunchedEffect(results) {
                    if (!restored && results.isNotEmpty()) {
                        val (i, o) = vm.readSearchScroll()
                        if (i in 0 until results.size) gridState.scrollToItem(i, o)
                        restored = true
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    state = gridState,
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(results, key = { idx, b -> "${b.id}#$idx" }) { _, book ->
                        val isSaved = book.id in savedIds
                        BookTile(
                            book = book,
                            isSaved = isSaved,
                            onToggleSave = { saved -> if (saved) vm.remove(book) else vm.save(book) },
                            onClick = { selectedId = book.id }
                        )
                    }
                }
            }

            Box(Modifier.weight(0.35f).fillMaxHeight().padding(8.dp)) {
                BookDetailCard(
                    book = selected,
                    isSaved = selected.id in savedIds,
                    onToggleSave = { saved -> if (saved) vm.remove(selected) else vm.save(selected) }
                )
            }
        }
        return
    }

    // Phone landscape fallback: compact grid
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { vm.onSearchQueryChanged(it) },
                label = { Text("Title or Author") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.doRemoteSearch() })
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = vm::doRemoteSearch, enabled = query.isNotBlank()) { Text("Search") }
        }
        if (err != null) {
            Spacer(Modifier.height(6.dp))
            Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))

        val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
        LaunchedEffect(gridState) {
            snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .collect { (i, o) -> vm.saveSearchScroll(i, o) }
        }
        var restored by remember { mutableStateOf(false) }
        LaunchedEffect(results) {
            if (!restored && results.isNotEmpty()) {
                val (i, o) = vm.readSearchScroll()
                if (i in 0 until results.size) gridState.scrollToItem(i, o)
                restored = true
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            state = gridState,
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(results, key = { idx, b -> "${b.id}#$idx" }) { _, book ->
                val isSaved = book.id in savedIds
                BookTile(book = book, isSaved = isSaved, onToggleSave = { saved ->
                    if (saved) vm.remove(book) else vm.save(book)
                })
            }
        }
    }
}

@Composable
private fun LibraryLandscape(vm: BookViewModel, isTablet: Boolean) {
    val q by vm.localQuery.collectAsState()
    val items by vm.localBooks.collectAsState()

    if (isTablet) {
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        val selected = items.firstOrNull { it.id == selectedId } ?: items.firstOrNull() ?: Book.EMPTY

        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(0.65f).fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp)) {
                OutlinedTextField(value = q, onValueChange = { vm.localQuery.value = it }, label = { Text("Search my library") }, singleLine = true)
                Spacer(Modifier.height(8.dp))

                val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
                LaunchedEffect(gridState) {
                    snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                        .distinctUntilChanged()
                        .collect { (i, o) -> vm.saveLibScroll(i, o) }
                }
                var restored by remember { mutableStateOf(false) }
                LaunchedEffect(items) {
                    if (!restored && items.isNotEmpty()) {
                        val (i, o) = vm.readLibScroll()
                        if (i in 0 until items.size) gridState.scrollToItem(i, o)
                        restored = true
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    state = gridState,
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(items, key = { idx, b -> "${b.id}#$idx" }) { _, book ->
                        BookRow(book = book, onRemove = { vm.remove(it) }, onClick = { selectedId = book.id }, compact = true)
                    }
                }
            }

            Box(Modifier.weight(0.35f).fillMaxHeight().padding(8.dp)) {
                BookDetailCard(
                    book = selected,
                    isSaved = true,
                    onToggleSave = { saved -> if (!saved) vm.remove(selected) },
                    onRemove = { vm.remove(selected) }
                )
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        OutlinedTextField(value = q, onValueChange = { vm.localQuery.value = it }, label = { Text("Search my library") }, singleLine = true)
        Spacer(Modifier.height(8.dp))

        val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
        LaunchedEffect(gridState) {
            snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .collect { (i, o) -> vm.saveLibScroll(i, o) }
        }
        var restored by remember { mutableStateOf(false) }
        LaunchedEffect(items) {
            if (!restored && items.isNotEmpty()) {
                val (i, o) = vm.readLibScroll()
                if (i in 0 until items.size) gridState.scrollToItem(i, o)
                restored = true
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            state = gridState,
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items, key = { idx, b -> "${b.id}#$idx" }) { _, book ->
                BookRow(book = book, onRemove = { vm.remove(it) }, compact = true)
            }
        }
    }
}

/* --------------------------- REUSABLE COMPOSABLES --------------------- */

@Composable
fun BookTile(
    book: Book,
    isSaved: Boolean = false,
    onToggleSave: (Boolean) -> Unit = {},
    onRemove: ((Book) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            val cd = buildString {
                append("Cover of ${book.title}")
                if (book.author.isNotBlank()) append(" by ${book.author}")
            }
            AsyncImage(
                model = book.coverUrl,
                contentDescription = cd,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(8.dp))
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (book.author.isNotBlank()) {
                Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (book.year.isNotBlank()) {
                Text(book.year, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when {
                    onRemove != null -> AssistChip(onClick = { onRemove(book) }, label = { Text("Remove") })
                    isSaved -> AssistChip(onClick = { onToggleSave(true) }, label = { Text("Unsave") })
                    else -> AssistChip(onClick = { onToggleSave(false) }, label = { Text("Save") })
                }
            }
        }
    }
}

@Composable
fun BookRow(
    book: Book,
    modifier: Modifier = Modifier,
    isSaved: Boolean = false,
    onToggleSave: (Boolean) -> Unit = {},
    onRemove: ((Book) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false
) {
    val clickMod = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    val imageSize = if (compact) 72.dp else 64.dp
    val cardPadding = if (compact) 10.dp else 12.dp
    val titleStyle = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
    val bodyStyle = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    val yearStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium

    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = if (compact) 4.dp else 6.dp).then(clickMod),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(cardPadding), verticalAlignment = Alignment.CenterVertically) {
            val coverCd = buildString {
                append("Cover of ${book.title}")
                if (book.author.isNotBlank()) append(" by ${book.author}")
            }
            AsyncImage(
                model = book.coverUrl,
                contentDescription = coverCd,
                modifier = Modifier.size(imageSize).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(if (compact) 10.dp else 12.dp))

            Column(Modifier.weight(1f)) {
                Text(book.title, style = titleStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) {
                    Text(book.author, style = bodyStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (book.year.isNotBlank()) {
                    Text(book.year, style = yearStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.width(if (compact) 10.dp else 12.dp))

            when {
                onRemove != null -> {
                    OutlinedButton(
                        onClick = { onRemove(book) },
                        modifier = Modifier.heightIn(min = if (compact) 40.dp else 48.dp),
                        contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) { Text("Remove", maxLines = 1, softWrap = false) }
                }
                compact && isSaved -> {
                    OutlinedButton(
                        onClick = { onToggleSave(true) },
                        modifier = Modifier.heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) { Text("Saved ✓", maxLines = 1, softWrap = false) }
                }
                else -> {
                    val savedBlue = Color(0xFF1E88E5)
                    val normalContainer = MaterialTheme.colorScheme.secondary
                    val normalText = MaterialTheme.colorScheme.onSecondary
                    Button(
                        onClick = { onToggleSave(isSaved) },
                        modifier = Modifier.heightIn(min = if (compact) 40.dp else 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSaved) savedBlue else normalContainer,
                            contentColor = if (isSaved) Color.White else normalText
                        ),
                        contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(if (isSaved) "Unsave" else "Save", style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
fun BookDetailCard(
    book: Book,
    isSaved: Boolean,
    onToggleSave: (Boolean) -> Unit,
    onRemove: (() -> Unit)? = null
) {
    if (book.id.isBlank()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a book", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    Card(Modifier.fillMaxSize().padding(12.dp), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            AsyncImage(
                model = book.coverUrl,
                contentDescription = "Large cover of ${book.title}",
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(16.dp))
            Text(book.title, style = MaterialTheme.typography.headlineSmall)
            if (book.author.isNotBlank()) {
                Text(book.author, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (book.year.isNotBlank()) {
                Text(book.year, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onRemove != null) {
                    OutlinedButton(onClick = onRemove, shape = RoundedCornerShape(24.dp)) { Text("Remove") }
                } else {
                    val savedBlue = Color(0xFF1E88E5)
                    val normalContainer = MaterialTheme.colorScheme.secondary
                    val normalText = MaterialTheme.colorScheme.onSecondary
                    Button(
                        onClick = { onToggleSave(isSaved) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSaved) savedBlue else normalContainer,
                            contentColor = if (isSaved) Color.White else normalText
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) { Text(if (isSaved) "Unsave" else "Save") }
                }
            }
        }
    }
}

/* ---------------------------- MANUAL ADD ------------------------------ */

@Composable
fun ManualAddDialog(onDismiss: () -> Unit, onAdd: (Book) -> Unit) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Book Manually") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Author") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year (optional)") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && author.isNotBlank(),
                onClick = {
                    val id = (title + author + year).hashCode().toString()
                    onAdd(Book(id, title.trim(), author.trim(), year.trim().ifBlank { "—" }))
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
