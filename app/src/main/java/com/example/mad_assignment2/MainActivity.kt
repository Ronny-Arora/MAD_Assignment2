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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mad_assignment2.auth.AuthViewModel
import com.example.mad_assignment2.data.model.Book
import com.example.mad_assignment2.ui.BookViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import androidx.core.net.toUri
import android.content.Intent
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.widget.Toast
import android.provider.ContactsContract
import androidx.compose.material.icons.outlined.Share
import android.app.Activity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

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
            onValueChange = { vm.localQuery.value = it },   // <- .value (StateFlow)
            label = { Text("Search my library") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No books saved yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(items, key = { it.id }) { book ->
                    // pass vm so the camera icon shows in My Library rows
                    BookRow(
                        book = book,
                        isSaved = true,
                        onToggleSave = { saved -> if (saved) vm.remove(book) },
                        onRemove = { vm.remove(it) },
                        compact = true,
                        vm = vm,
                        enableShare = true,
                        showRemoveOnly = true
                    )
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

    // TABLET / LARGE SCREENS: master–detail
    if (isTablet) {
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        val selected = results.firstOrNull { it.id == selectedId } ?: results.firstOrNull() ?: Book.EMPTY

        Row(Modifier.fillMaxSize()) {

            Column(
                Modifier
                    .weight(0.65f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.onSearchQueryChanged(it) },
                        label = { Text("Title or Author") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.doRemoteSearch() })
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = vm::doRemoteSearch, enabled = query.isNotBlank()) { Text("Search") }
                }

                if (err != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(err!!, color = MaterialTheme.colorScheme.error)
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
                            onClick = { selectedId = book.id }   // ← FIX: update details pane selection
                            // (no vm here so camera icons stay hidden in Search)
                        )
                    }
                }
            }

            // Right: detail card (Unsave/Save button is always visible now)
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

    // PHONE LANDSCAPE (single pane, no details)
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.onSearchQueryChanged(it) },
                label = { Text("Title or Author") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.doRemoteSearch() })
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = vm::doRemoteSearch, enabled = query.isNotBlank()) { Text("Search") }
        }

        if (err != null) {
            Spacer(Modifier.height(6.dp))
            Text(err!!, color = MaterialTheme.colorScheme.error)
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
                    onToggleSave = { saved -> if (saved) vm.remove(book) else vm.save(book) }
                    // no onClick here (no details pane in phone landscape)
                )
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
            // LEFT: search + grid (no overlay actions)
            Column(
                Modifier
                    .weight(0.65f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = q,
                    onValueChange = { vm.localQuery.value = it },
                    label = { Text("Search my library") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                        // vm = null and enableShare = false -> hides camera/share overlay in grid
                        BookTile(
                            book = book,
                            isSaved = true,
                            onToggleSave = { saved -> if (saved) vm.remove(book) },
                            onClick = { selectedId = book.id }
                        )
                    }
                }
            }

            // RIGHT: details pane WITH camera + share next to Remove
            Box(Modifier.weight(0.35f).fillMaxHeight().padding(8.dp)) {
                BookDetailCard(
                    book = selected,
                    isSaved = selected.id.isNotBlank(),
                    onToggleSave = { saved -> if (saved) vm.remove(selected) else Unit },
                    onRemove = { vm.remove(selected) },
                    vm = vm,                 // pass vm so details can attach photo
                    enableShare = true       // show share in details
                )
            }
        }
        return
    }

    // PHONE landscape (single pane): keep overlay actions on tiles as before
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = q,
            onValueChange = { vm.localQuery.value = it },
            label = { Text("Search my library") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
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
                // Keep overlay actions on phone-landscape
                BookTile(
                    book = book,
                    isSaved = true,
                    onToggleSave = { saved -> if (saved) vm.remove(book) },
                    vm = vm,
                    enableShare = true
                )
            }
        }
    }
}

/* --------------------------- REUSABLE COMPOSABLE --------------------- */

@Composable
fun BookTile(
    book: Book,
    modifier: Modifier = Modifier,
    isSaved: Boolean = false,
    onToggleSave: (Boolean) -> Unit = {},
    onRemove: ((Book) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    vm: BookViewModel? = null,
    enableShare: Boolean = false
) {
    val clickMod = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    val normalText = MaterialTheme.colorScheme.onSurface
    val imageModel: Any? = book.photoUri?.toUri() ?: book.coverUrl

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .then(clickMod),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(12.dp)) {

            Box {
                AsyncImage(
                    model = imageModel,
                    contentDescription = "Cover of ${book.title}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )

                // Top-right action row (camera + share) on saved items
                if (isSaved) {
                    Row(
                        Modifier.fillMaxWidth().padding(4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (vm != null) {
                            AttachPhotoButton(book = book, onCaptured = { uri -> vm.attachPhoto(book, uri) })
                        }
                        if (enableShare) {
                            ShareBookButton(book = book)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            if (book.author.isNotBlank()) {
                Text(book.author, style = MaterialTheme.typography.bodyMedium, color = normalText.copy(alpha = 0.85f))
            }
            if (book.year.isNotBlank()) {
                Text(book.year, style = MaterialTheme.typography.labelMedium, color = normalText.copy(alpha = 0.7f))
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onToggleSave(isSaved) }) { Text(if (isSaved) "Unsave" else "Save") }
                if (onRemove != null && isSaved) {
                    OutlinedButton(onClick = { onRemove(book) }) { Text("Remove") }
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
    compact: Boolean = false,
    vm: BookViewModel? = null,
    enableShare: Boolean = false,
    // When true, hides Save/Unsave and shows only a single “Remove” button
    showRemoveOnly: Boolean = false
) {
    val clickMod = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    val imageSize = if (compact) 72.dp else 64.dp
    val cardPadding = if (compact) 10.dp else 12.dp
    val titleStyle = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
    val bodyStyle = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    val yearStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    val normalText = MaterialTheme.colorScheme.onSurface

    // Prefer personal cover if available
    val imageModel: Any? = book.photoUri?.toUri() ?: book.coverUrl

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 4.dp else 6.dp)
            .then(clickMod),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(cardPadding), verticalAlignment = Alignment.CenterVertically) {

            AsyncImage(
                model = imageModel,
                contentDescription = "Cover of ${book.title}",
                modifier = Modifier.size(imageSize).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(if (compact) 10.dp else 12.dp))

            Column(Modifier.weight(1f)) {
                Text(book.title, style = titleStyle, maxLines = 2)
                if (book.author.isNotBlank()) {
                    Text(book.author, style = bodyStyle, color = normalText.copy(alpha = 0.85f))
                }
                if (book.year.isNotBlank()) {
                    Text(book.year, style = yearStyle, color = normalText.copy(alpha = 0.7f))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Camera (saved items only)
                if (isSaved && vm != null) {
                    AttachPhotoButton(
                        book = book,
                        onCaptured = { uriStr -> vm.attachPhoto(book, uriStr) },
                        contentDescription = if (book.photoUri != null) "Change cover photo" else "Attach cover photo"
                    )
                }

                // Share (opt-in)
                if (enableShare && isSaved) {
                    ShareBookButton(book = book, contentDescription = "Share details with a contact")
                }

                // Actions
                if (showRemoveOnly) {
                    // PORTRAIT My Library: show only a single “Remove”
                    if (onRemove != null) {
                        TextButton(onClick = { onRemove(book) }) { Text("Remove") }
                    }
                } else {
                    // Default behavior everywhere else (landscape / search)
                    OutlinedButton(onClick = { onToggleSave(isSaved) }) {
                        Text(if (isSaved) "Unsave" else "Save")
                    }
                    if (onRemove != null && isSaved) {
                        TextButton(onClick = { onRemove(book) }) { Text("Remove") }
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
    onRemove: (() -> Unit)? = null,
    // Enable attaching photo & sharing from details
    vm: BookViewModel? = null,
    enableShare: Boolean = false
) {
    if (book.id.isBlank()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a book", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    val bigImage: Any? = book.photoUri?.toUri() ?: book.coverUrl

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxH = this.maxHeight
            val imageHeight: Dp = maxOf(140.dp, maxH * 0.38f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                AsyncImage(
                    model = bigImage,
                    contentDescription = "Large cover of ${book.title}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(imageHeight)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.author.isNotBlank()) {
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (book.year.isNotBlank()) {
                    Text(
                        text = book.year,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.weight(1f))

                // Actions pinned at bottom
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (onRemove != null) {
                        // My Library details: show camera + share + Remove
                        if (vm != null) {
                            AttachPhotoButton(
                                book = book,
                                onCaptured = { uri -> vm.attachPhoto(book, uri) },
                                contentDescription = if (book.photoUri != null) "Change cover photo" else "Attach cover photo"
                            )
                        }
                        if (enableShare) {
                            ShareBookButton(book = book)
                        }
                        OutlinedButton(onClick = onRemove, shape = RoundedCornerShape(24.dp)) {
                            Text("Remove")
                        }
                    } else {
                        // Search details: Save/Unsave only
                        Button(onClick = { onToggleSave(isSaved) }, shape = RoundedCornerShape(24.dp)) {
                            Text(if (isSaved) "Unsave" else "Save")
                        }
                    }
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

@Composable
fun AttachPhotoButton(
    book: Book,
    onCaptured: (String) -> Unit,
    modifier: Modifier = Modifier,
    // callers like BookRow can pass a label
    contentDescription: String = "Attach cover photo"
) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Uri?>(null) }

    fun safeSlug(raw: String): String =
        raw.trim('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "book" }
            .take(40)

    fun createImageUri(): Uri {
        fun buildIn(base: File?): File? {
            if (base == null) return null
            val dir = File(base, "covers").apply { mkdirs() }
            val name = "cover_${safeSlug(book.id)}_${System.currentTimeMillis()}.jpg"
            return try { File(dir, name).apply { createNewFile() } } catch (_: Throwable) { null }
        }
        val file = buildIn(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES))
            ?: buildIn(context.cacheDir)
            ?: error("No writable storage directory")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun cameraAvailable(): Boolean =
        context.packageManager.queryIntentActivities(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            PackageManager.MATCH_DEFAULT_ONLY
        ).isNotEmpty()

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) pending?.let { onCaptured(it.toString()) }
    }

    IconButton(
        onClick = {
            if (!cameraAvailable()) {
                Toast.makeText(context, "No camera app available on this device/emulator", Toast.LENGTH_SHORT).show()
                return@IconButton
            }
            try {
                val uri = createImageUri()
                pending = uri
                takePicture.launch(uri)
            } catch (t: Throwable) {
                Toast.makeText(context, "Couldn't create image file (${t.localizedMessage})", Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Outlined.PhotoCamera,
            contentDescription = contentDescription   // uses the param above
        )
    }
}

@Composable
fun ShareBookButton(
    book: Book,
    modifier: Modifier = Modifier,
    contentDescription: String = "Share book via email"
) {
    val context = LocalContext.current

    // Contact picker (if available) - returns an Email row URI
    val pickEmailLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val dataUri: Uri? = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || dataUri == null) return@rememberLauncherForActivityResult

        val email = context.contentResolver.query(
            dataUri,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

        launchEmailComposer(context, book, email)
    }

    IconButton(
        onClick = {
            // Build a PICK intent that targets email addresses
            val pickIntent = Intent(Intent.ACTION_PICK).apply {
                type = ContactsContract.CommonDataKinds.Email.CONTENT_TYPE
            }

            // If there is a contacts app that can handle this: pick - otherwise fallback to email compose
            val hasPicker = context.packageManager
                .queryIntentActivities(pickIntent, PackageManager.MATCH_DEFAULT_ONLY)
                .isNotEmpty()

            if (hasPicker) {
                try {
                    pickEmailLauncher.launch(pickIntent)
                } catch (_: Throwable) {
                    launchEmailComposer(context, book, null)
                }
            } else {
                // Tablet images sometimes don’t ship with a contacts app: fallback
                launchEmailComposer(context, book, null)
            }
        },
        modifier = modifier
    ) {
        Icon(imageVector = Icons.Outlined.Share, contentDescription = contentDescription)
    }
}

// Helper to open the email app with prefilled subject/body (email may be null)
private fun launchEmailComposer(context: android.content.Context, book: Book, email: String?) {
    val subject = "Book: ${book.title}"
    val body = buildString {
        appendLine("I'd like to share a book with you:")
        appendLine("• Title: ${book.title}")
        if (book.author.isNotBlank()) appendLine("• Author: ${book.author}")
        if (book.year.isNotBlank()) appendLine("• Year: ${book.year}")
        if (book.id.isNotBlank()) appendLine("• Open Library: https://openlibrary.org${book.id}")
    }

    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        if (!email.isNullOrBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Send with"))
    } catch (_: Throwable) {
        Toast.makeText(context, "No email app installed", Toast.LENGTH_SHORT).show()
    }
}