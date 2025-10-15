@file:OptIn(ExperimentalMaterial3Api::class)
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.flow.distinctUntilChanged
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AssistChip

class MainActivity : ComponentActivity() {

    // ViewModel (exists in com.example.mad_assignment2.ui)
    private val vm: BookViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Material3 app theme from themes.xml
            MaterialTheme {
                App(vm)
            }
        }
    }
}

@Composable
fun App(vm: BookViewModel) {
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        LandscapeHome(vm)   // NavigationRail layout
    } else {
        PortraitHome(vm)    // Current top tabs layout kept intact
    }
}

@Composable
private fun PortraitHome(vm: BookViewModel) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            // keeping normal TopAppBar when in portrait
            TopAppBar(title = { Text("Pocket Library") })
        }
    ) { inner ->
        Column(Modifier.padding(inner)) {
            // keep current TabRow
            TabRow(selectedTabIndex = selected) {
                Tab(selected = selected == 0, onClick = { selected = 0 }, text = { Text("Search") })
                Tab(selected = selected == 1, onClick = { selected = 1 }, text = { Text("My Library") })
            }
            if (selected == 0) SearchTab(vm) else LibraryTab(vm)
        }
    }
}

@Composable
private fun LandscapeHome(vm: BookViewModel) {
    val config = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Scaffold {
        inner ->
        Row(Modifier.padding(inner).fillMaxSize()) {

            // Left-side NavigationRail replaces the top TabRow
            NavigationRail(modifier = Modifier.fillMaxHeight()) {
                NavigationRailItem(
                    selected = selected == 0,
                    onClick = { selected = 0 },
                    icon = { Text("S") },
                    label = { Text("Search") }
                )
                NavigationRailItem(
                    selected = selected == 1,
                    onClick = { selected = 1 },
                    icon = { Text("L") },
                    label = { Text("My Library") }
                )
            }

            // Main content area
            if (selected == 0) {
                SearchLandscape(vm, isTablet)
            } else {
                LibraryLandscape(vm, isTablet)
            }
        }
    }
}

@Composable
private fun SearchLandscape(vm: BookViewModel, isTablet: Boolean) {
    val query    by vm.query.collectAsState()
    val results  by vm.remote.collectAsState()
    val savedIds by vm.savedIds.collectAsState()
    val err      by vm.error.collectAsState()

    // TABLET (split pane: grid | details)
    if (isTablet) {
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        val selected = results.firstOrNull { it.id == selectedId } ?: results.firstOrNull() ?: Book.EMPTY

        Row(Modifier.fillMaxSize()) {

            // LEFT: grid area
            Column(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Small inline title
                Text(
                    "Pocket Library",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Horizontal query row — saves vertical space
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.onSearchQueryChanged(it) },
                        label = { Text("Title or Author") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = vm::doRemoteSearch, enabled = query.isNotBlank()) {
                        Text("Search")
                    }
                }
                if (err != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))

                // Scroll state with save/restore to persist position
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
                            onToggleSave = { saved ->
                                if (saved) vm.remove(book) else vm.save(book)
                            },
                            onClick = { selectedId = book.id }  // select for details
                        )
                    }
                }
            }

            // RIGHT: details
            Box(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                BookDetailCard(
                    book = selected,
                    isSaved = selected.id in savedIds,
                    onToggleSave = { saved ->
                        if (saved) vm.remove(selected) else vm.save(selected)
                    }
                )
            }
        }
        return
    }

    // PHONE LANDSCAPE (no details pane; just compact grid)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            "Pocket Library",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.onSearchQueryChanged(it) },
                label = { Text("Title or Author") },
                singleLine = true,
                modifier = Modifier.weight(1f)
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
                    onToggleSave = { saved ->
                        if (saved) vm.remove(book) else vm.save(book)
                    }
                )
            }
        }
    }
}

@Composable
private fun LibraryLandscape(vm: BookViewModel, isTablet: Boolean) {
    val q     by vm.localQuery.collectAsState()
    val items by vm.localBooks.collectAsState()

    if (isTablet) {
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        val selected = items.firstOrNull { it.id == selectedId } ?: items.firstOrNull() ?: Book.EMPTY

        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = q,
                        onValueChange = { vm.localQuery.value = it },
                        label = { Text("Search my library") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
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
                        BookRow(
                            book = book,
                            onRemove = { vm.remove(it) },
                            onClick = { selectedId = book.id },
                            compact = true
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
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

    // Phone landscape (no details pane)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = q,
                onValueChange = { vm.localQuery.value = it },
                label = { Text("Search my library") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
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

@Suppress("unused")     // function not used - switched to SearchTab (suppresses warning)
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
            items(results) { book ->
                BookRow(
                    book = book,
                    onToggleSave = { vm.save(book) }
                )
            }
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
fun BookTile(
    book: Book,
    isSaved: Boolean = false,
    onToggleSave: (Boolean) -> Unit = {},
    onRemove: ((Book) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    // Tile layout: cover on top, text lines below
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)   // Readable cover
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (book.author.isNotBlank()) {
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (book.year.isNotBlank()) {
                Text(
                    text = book.year,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            // Text never wraps/cuts off.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when {
                    onRemove != null -> {
                        AssistChip(onClick = { onRemove(book) }, label = { Text("Remove") })
                    }
                    isSaved -> {
                        AssistChip(onClick = { onToggleSave(true) }, label = { Text("Unsave") })
                    }
                    else -> {
                        AssistChip(onClick = { onToggleSave(false) }, label = { Text("Save") })
                    }
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
    // Compact = true for landscape layouts (smaller paddings & controls)
    compact: Boolean = false
) {
    val clickMod = if (onClick != null) Modifier.clickable { onClick() } else Modifier

    // Sizes tuned for readability; compact gets smaller image & paddings
    val imageSize = if (compact) 72.dp else 64.dp
    val cardPadding = if (compact) 10.dp else 12.dp
    val titleStyle = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
    val bodyStyle  = if (compact) MaterialTheme.typography.bodySmall  else MaterialTheme.typography.bodyMedium
    val yearStyle  = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 4.dp else 6.dp)
            .then(clickMod),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val coverCd = buildString {
                append("Cover of ${book.title}")
                if (book.author.isNotBlank()) append(" by ${book.author}")
            }
            AsyncImage(
                model = book.coverUrl,
                contentDescription = coverCd,
                modifier = Modifier
                    .size(imageSize)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(if (compact) 10.dp else 12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.author.isNotBlank()) {
                    Text(
                        text = book.author,
                        style = bodyStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (book.year.isNotBlank()) {
                    Text(
                        text = book.year,
                        style = yearStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(if (compact) 10.dp else 12.dp))

            when {
                onRemove != null -> {
                    // Library action
                    OutlinedButton(
                        onClick = { onRemove(book) },
                        modifier = Modifier.heightIn(min = if (compact) 40.dp else 48.dp),
                        contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) { Text("Remove", maxLines = 1, softWrap = false) }
                }
                // In compact landscape, if saved, show a tight "Saved ✓"
                compact && isSaved -> {
                    OutlinedButton(
                        onClick = { onToggleSave(true) },   // acts as "Unsave" on press
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
                            contentColor   = if (isSaved) Color.White else normalText
                        ),
                        contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = if (isSaved) "Unsave" else "Save",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false
                        )
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

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            AsyncImage(
                model = book.coverUrl,
                contentDescription = "Large cover of ${book.title}",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)              // 🔹 clear & readable image
                    .clip(RoundedCornerShape(12.dp)),
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
                            contentColor   = if (isSaved) Color.White else normalText
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) { Text(if (isSaved) "Unsave" else "Save") }
                }
            }
        }
    }
}


// Search tab: shows online results in a LazyColumn with stable keys (smooth scroll)
@Composable
fun SearchTab(vm: BookViewModel) {
    val query    by vm.query.collectAsState()
    val results  by vm.remote.collectAsState()
    val savedIds by vm.savedIds.collectAsState()
    val err      by vm.error.collectAsState()

    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = config.screenWidthDp >= 600

    // Tablet: split layout (list/grid + details)
    if (isTablet) {
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        val selected = results.firstOrNull { it.id == selectedId } ?: results.firstOrNull() ?: Book.EMPTY

        Row(Modifier.fillMaxSize()) {
            // LEFT PANE: list or grid depending on orientation, with scroll persistence
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.onSearchQueryChanged(it) }, // persists query
                    label = { Text("Title or Author") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = vm::doRemoteSearch, enabled = query.isNotBlank()) {
                        Text("Search Online")
                    }
                }

                if (err != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(8.dp))

                if (isLandscape) {
                    // --- GRID (landscape tablet) ---
                    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }

                    // Save scroll
                    LaunchedEffect(gridState) {
                        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                            .distinctUntilChanged()
                            .collect { (i, o) -> vm.saveSearchScroll(i, o) }
                    }
                    // Restore after data loads
                    var restored by remember { mutableStateOf(false) }
                    LaunchedEffect(results) {
                        if (!restored && results.isNotEmpty()) {
                            val (i, o) = vm.readSearchScroll()
                            if (i in 0 until results.size) gridState.scrollToItem(i, o)
                            restored = true
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(results, key = { idx, b -> "${b.id}#$idx" }) { _, book ->
                            val isSaved = book.id in savedIds
                            BookRow(
                                book = book,
                                isSaved = isSaved,
                                onToggleSave = { saved -> if (saved) vm.remove(book) else vm.save(book) },
                                onClick = { selectedId = book.id } // open in right pane
                            )
                        }
                    }
                } else {
                    // LIST (portrait tablet)
                    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

                    LaunchedEffect(listState) {
                        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                            .distinctUntilChanged()
                            .collect { (i, o) -> vm.saveSearchScroll(i, o) }
                    }
                    var restored by remember { mutableStateOf(false) }
                    LaunchedEffect(results) {
                        if (!restored && results.isNotEmpty()) {
                            val (i, o) = vm.readSearchScroll()
                            if (i in 0 until results.size) listState.scrollToItem(i, o)
                            restored = true
                        }
                    }

                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(results, key = { idx, b -> "${b.id}#$idx" }) { _, book ->
                            val isSaved = book.id in savedIds
                            BookRow(
                                book = book,
                                isSaved = isSaved,
                                onToggleSave = { saved -> if (saved) vm.remove(book) else vm.save(book) },
                                onClick = { selectedId = book.id }
                            )
                        }
                    }
                }
            }

            // RIGHT PANE: details
            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                BookDetailCard(
                    book = selected,
                    isSaved = selected.id in savedIds,
                    onToggleSave = { saved -> if (saved) vm.remove(selected) else vm.save(selected) }
                )
            }
        }
        return
    }

    // PHONE: portrait = list, landscape = two-column grid
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.onSearchQueryChanged(it) }, // persists query
            label = { Text("Title or Author") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Row { Button(onClick = vm::doRemoteSearch, enabled = query.isNotBlank()) { Text("Search Online") } }

        if (err != null) {
            Spacer(Modifier.height(8.dp))
            Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(8.dp))

        if (isLandscape) {
            // GRID on phone landscape
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
                columns = GridCells.Fixed(2),
                state = gridState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(results, key = { idx, b -> "${b.id}#$idx" }) { _, book ->
                    val isSaved = book.id in savedIds
                    BookRow(
                        book = book,
                        isSaved = isSaved,
                        onToggleSave = { saved -> if (saved) vm.remove(book) else vm.save(book) }
                    )
                }
            }
        } else {
            // LIST on phone portrait
            val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                    .distinctUntilChanged()
                    .collect { (i, o) -> vm.saveSearchScroll(i, o) }
            }
            var restored by remember { mutableStateOf(false) }
            LaunchedEffect(results) {
                if (!restored && results.isNotEmpty()) {
                    val (i, o) = vm.readSearchScroll()
                    if (i in 0 until results.size) listState.scrollToItem(i, o)
                    restored = true
                }
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(results, key = { idx, b -> "${b.id}#$idx" }) { _, book ->
                    val isSaved = book.id in savedIds
                    BookRow(
                        book = book,
                        isSaved = isSaved,
                        onToggleSave = { saved -> if (saved) vm.remove(book) else vm.save(book) }
                    )
                }
            }
        }
    }
}

// Library tab: shows local (Room) books in a LazyColumn with stable keys
@Composable
fun LibraryTab(vm: BookViewModel) {
    val q      by vm.localQuery.collectAsState()
    val items  by vm.localBooks.collectAsState()

    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = config.screenWidthDp >= 600

    if (isTablet) {
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        val selected = items.firstOrNull { it.id == selectedId } ?: items.firstOrNull() ?: Book.EMPTY

        Row(Modifier.fillMaxSize()) {
            // LEFT PANE: filter + list/grid with scroll persistence
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = q,
                    onValueChange = { vm.localQuery.value = it },
                    label = { Text("Search my library") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                if (isLandscape) {
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
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(items, key = { idx, b -> "${b.id}#$idx" }) { _, book ->
                            BookRow(
                                book = book,
                                onRemove = { vm.remove(it) },
                                onClick = { selectedId = book.id }
                            )
                        }
                    }
                } else {
                    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
                    LaunchedEffect(listState) {
                        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                            .distinctUntilChanged()
                            .collect { (i, o) -> vm.saveLibScroll(i, o) }
                    }
                    var restored by remember { mutableStateOf(false) }
                    LaunchedEffect(items) {
                        if (!restored && items.isNotEmpty()) {
                            val (i, o) = vm.readLibScroll()
                            if (i in 0 until items.size) listState.scrollToItem(i, o)
                            restored = true
                        }
                    }

                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(items, key = { idx, b -> "${b.id}#$idx" }) { _, book ->
                            BookRow(
                                book = book,
                                onRemove = { vm.remove(it) },
                                onClick = { selectedId = book.id }
                            )
                        }
                    }
                }
            }

            // RIGHT PANE: details (Library uses "Remove")
            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                BookDetailCard(
                    book = selected,
                    isSaved = true, // it’s in My Library by definition
                    onToggleSave = { saved -> if (!saved) vm.remove(selected) }, // Unsave acts like Remove
                    onRemove = { vm.remove(selected) }
                )
            }
        }
        return
    }

    // PHONE
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = q,
            onValueChange = { vm.localQuery.value = it },
            label = { Text("Search my library") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        if (isLandscape) {
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
                columns = GridCells.Fixed(2),
                state = gridState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(items, key = { idx, b -> "${b.id}#$idx" }) { _, book ->
                    BookRow(book, onRemove = { vm.remove(it) })
                }
            }
        } else {
            val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                    .distinctUntilChanged()
                    .collect { (i, o) -> vm.saveLibScroll(i, o) }
            }
            var restored by remember { mutableStateOf(false) }
            LaunchedEffect(items) {
                if (!restored && items.isNotEmpty()) {
                    val (i, o) = vm.readLibScroll()
                    if (i in 0 until items.size) listState.scrollToItem(i, o)
                    restored = true
                }
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(items, key = { idx, b -> "${b.id}#$idx" }) { _, book ->
                    BookRow(book, onRemove = { vm.remove(it) })
                }
            }
        }
    }
}


@Suppress("unused")     // function not used - switched to LibraryTab (suppresses warning)
@Composable
fun LibraryScreen(vm: BookViewModel) {
    val q by vm.localQuery.collectAsState()
    val libraryItems by vm.localBooks.collectAsState()

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
            items(libraryItems) { book ->                // calls the DSL function
                BookRow(book, onRemove = { vm.remove(it) })
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
