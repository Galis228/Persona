package com.persona.app

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: Nav) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val ctx = LocalContext.current
    var createMenu by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    val snack = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(Unit) {
        val st0 = ctx.app.repo.settings.value
        if (st0.updates.repo.isNotBlank() && System.currentTimeMillis() - st0.updates.lastCheck > 6 * 3_600_000L) {
            Updates.check(ctx).getOrNull()?.let { u -> if (u.auto && Updates.hasNew(ctx, u) && u.url != null) runCatching { Updater.download(ctx) } }
        }
        if (AutoBackup.due(st0.autoBackup)) AutoBackup.run(ctx)
    }
    LaunchedEffect(Unit) {
        UndoBus.flow.collect { (text, undo) ->
            val r = snack.showSnackbar(text, actionLabel = "Отменить", duration = androidx.compose.material3.SnackbarDuration.Short)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) undo()
        }
    }
    val st by ctx.app.repo.settings.collectAsStateWithLifecycle()
    val importBook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { Cards.importLorebook(ctx, uri) }
            .onSuccess { b -> ctx.app.repo.saveBook(b); Toast.makeText(ctx, "Лорбук «${b.name}»: записей ${b.entries.size}", Toast.LENGTH_SHORT).show(); nav.go(Route.Lore(b.id)) }
            .onFailure { Toast.makeText(ctx, it.message ?: "Не удалось прочитать лорбук", Toast.LENGTH_LONG).show() }
    }
    val importCard = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { Cards.import(ctx, uri) }
            .onSuccess { c -> ctx.app.repo.saveChar(c); Toast.makeText(ctx, "Персонаж «${c.name}» добавлен", Toast.LENGTH_SHORT).show(); nav.go(Route.Editor(c.id)) }
            .onFailure { Toast.makeText(ctx, it.message ?: "Не удалось прочитать карточку", Toast.LENGTH_LONG).show() }
    }
    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(listOf("Персона", "Лорбуки", "Настройки")[tab], fontWeight = FontWeight.ExtraBold) },
                actions = {
                    if (tab == 0) {
                        IconButton({ nav.go(Route.Search) }) { Icon(Icons.Rounded.Search, "Поиск") }
                        IconButton({ importCard.launch(arrayOf("image/png", "application/json", "text/plain", "application/octet-stream")) }) {
                            Icon(Icons.Rounded.FileOpen, "Импорт карточки")
                        }
                    }
                    if (tab == 1) IconButton({ importBook.launch(arrayOf("application/json", "text/plain", "image/png", "application/octet-stream")) }) {
                        Icon(Icons.Rounded.FileOpen, "Импорт лорбука SillyTavern")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Rounded.ChatBubble, null) }, label = { Text("Чаты") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.AutoMirrored.Rounded.MenuBook, null) }, label = { Text("Лорбуки") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Rounded.Settings, null) }, label = { Text("Настройки") })
            }
        },
        floatingActionButton = {
            when (tab) {
                0 -> Box {
                    ExtendedFloatingActionButton(onClick = { createMenu = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Создать") })
                    DropdownMenu(createMenu, { createMenu = false }) {
                        DropdownMenuItem({ Text("Персонажа") }, { createMenu = false; nav.go(Route.Editor(null)) }, leadingIcon = { Icon(Icons.Rounded.Person, null) })
                        DropdownMenuItem({ Text("Групповой чат") }, { createMenu = false; nav.go(Route.SceneEditor(null)) }, leadingIcon = { Icon(Icons.Rounded.Groups, null) })
                        DropdownMenuItem({ Text("Из шаблона") }, { createMenu = false; showTemplates = true }, leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) })
                    }
                }
                1 -> ExtendedFloatingActionButton(onClick = {
                    val b = Lorebook(name = "Новый лорбук"); ctx.app.repo.saveBook(b); nav.go(Route.Lore(b.id))
                }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Лорбук") })
            }
        },
    ) { pad ->
        if (!st.onboarded) PhoneCheckDialog(nav) { ctx.app.repo.settings { it.copy(onboarded = true) } }
        if (showTemplates) TemplatesDialog({ c -> showTemplates = false; ctx.app.repo.saveChar(c); nav.go(Route.ChatR(ctx.app.runner.newChat(c.id).id)) }) { showTemplates = false }
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> CharacterList(nav, { showTemplates = true }) { tab = 2 }
                1 -> LorebookList(nav)
                2 -> SettingsHome(nav)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CharacterList(nav: Nav, openTemplates: () -> Unit, openModel: () -> Unit) {
    val ctx = LocalContext.current
    val repo = ctx.app.repo
    val chars by repo.chars.collectAsStateWithLifecycle()
    val chats by repo.chats.collectAsStateWithLifecycle()
    val s by repo.settings.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<Character?>(null) }
    val scenes by repo.scenes.collectAsStateWithLifecycle()

    val lastChat = chats.filter { it.sceneId == null && !it.incognito }.groupBy { it.charId }.mapValues { (_, l) -> l.maxBy { it.updated } }
    val lastGroup = chats.filter { it.sceneId != null }.groupBy { it.sceneId!! }.mapValues { (_, l) -> l.maxBy { it.updated } }
    // Персонажи и групповые чаты — в одном списке, сверху свежие
    var filter by remember { mutableStateOf<String?>(null) }
    var showArchive by remember { mutableStateOf(false) }
    val compact = LocalLook.current.compact
    fun pinned(e: Any) = (e as? Character)?.pinned ?: (e as? Scene)?.pinned ?: false
    fun archived(e: Any) = (e as? Character)?.archived ?: (e as? Scene)?.archived ?: false
    val all: List<Any> = (chars.map { it to (lastChat[it.id]?.updated ?: it.created) } + scenes.map { it to (lastGroup[it.id]?.updated ?: it.created) })
        .sortedWith(compareByDescending<Pair<Any, Long>> { pinned(it.first) }.thenByDescending { it.second }).map { it.first }
    val matches: (Any) -> Boolean = { e -> when (filter) {
        null -> true
        "#groups" -> e is Scene
        else -> (e as? Character)?.tags?.contains(filter) == true
    } }
    val entries = all.filter { !archived(it) && matches(it) }
    val archivedList = all.filter { archived(it) }
    val tags = chars.filterNot { it.archived }.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(10).map { it.key }
    fun deleteWithUndo(c: Character) {
        val gone = repo.chats.value.filter { it.charId == c.id }
        repo.deleteChar(c.id)
        UndoBus.post("«${c.name}» удалён") { repo.restore(listOf(c), emptyList(), gone) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item { ModelStatus(s, openModel) }
        if (Updates.hasNew(ctx, s.updates)) item {
            Surface(onClick = { UpdateRequest.installNow = true; nav.go(Route.SettingsPage("updates")) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Доступна Персона ${s.updates.latest}", fontWeight = FontWeight.Bold)
                        Text(s.updates.notes?.lines()?.firstOrNull { it.isNotBlank() }?.trim()?.take(80) ?: "Нажми, чтобы обновиться", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    Text("Обновить", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (tags.isNotEmpty() || scenes.isNotEmpty()) item {
            androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(filter == null, { filter = null }, { Text("Все") }) }
                if (scenes.isNotEmpty()) item { FilterChip(filter == "#groups", { filter = if (filter == "#groups") null else "#groups" }, { Text("Группы") }) }
                items(tags) { t -> FilterChip(filter == t, { filter = if (filter == t) null else t }, { Text(t) }) }
            }
        }
        if (chars.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Пока никого нет", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Создай персонажа кнопкой «Создать» или импортируй карточку SillyTavern (PNG или JSON) кнопкой сверху. Когда персонажей будет двое и больше, можно собрать групповой чат.",
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.Button(openTemplates) { Text("Выбрать из шаблонов") }
            }
        }
        val shown = entries + if (showArchive) archivedList else emptyList()
        items(shown, key = { e -> if (e is Scene) "g:" + e.id else "c:" + (e as Character).id }) { e ->
            if (e is Scene) { GroupRow(nav, e, lastGroup[e.id]); return@items }
            val c = e as Character
            val chat = lastChat[c.id]
            Box {
                Row(
                    Modifier.fillMaxWidth().combinedClickable(
                        onClick = {
                            val id = chat?.id ?: ctx.app.runner.newChat(c.id).id
                            nav.go(Route.ChatR(id))
                        },
                        onLongClick = { menuFor = c.id },
                    ).padding(horizontal = 16.dp, vertical = if (compact) 5.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(c, if (compact) 44.dp else 56.dp)
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (c.pinned) { Icon(Icons.Rounded.PushPin, "Закреплён", Modifier.size(14.dp), tint = Accent); Spacer(Modifier.size(4.dp)) }
                            Text(c.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            chat?.let { Text(timeLabel(it.updated), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        val preview = chat?.messages?.lastOrNull()?.let { (if (it.fromUser) "Ты: " else "") + it.text } ?: c.tagline
                        Text(preview.replace('\n', ' '), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DropdownMenu(menuFor == c.id, { menuFor = null }) {
                    DropdownMenuItem({ Text("Новый чат") }, { menuFor = null; nav.go(Route.ChatR(ctx.app.runner.newChat(c.id).id)) })
                    DropdownMenuItem({ Text("Изменить") }, { menuFor = null; nav.go(Route.Editor(c.id)) })
                    DropdownMenuItem({ Text("Экспорт карточки") }, { menuFor = null; shareCard(ctx, c) })
                    DropdownMenuItem({ Text("Инкогнито-чат") }, { menuFor = null; nav.go(Route.ChatR(ctx.app.runner.newChat(c.id, incognito = true).id)) })
                    DropdownMenuItem({ Text(if (c.pinned) "Открепить" else "Закрепить") }, { menuFor = null; repo.saveChar(c.copy(pinned = !c.pinned)) })
                    DropdownMenuItem({ Text(if (c.archived) "Вернуть из архива" else "В архив") }, { menuFor = null; repo.saveChar(c.copy(archived = !c.archived, pinned = false)) })
                    DropdownMenuItem({ Text("Удалить", color = MaterialTheme.colorScheme.error) }, { menuFor = null; deleteWithUndo(c) })
                }
            }
        }
        if (archivedList.isNotEmpty()) item {
            TextButton({ showArchive = !showArchive }, Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Rounded.Archive, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp))
                Text(if (showArchive) "Скрыть архив" else "Архив · ${archivedList.size}")
            }
        }
    }
    confirmDelete?.let { c ->
        AlertDialog({ confirmDelete = null },
            title = { Text("Удалить «${c.name}»?") },
            text = { Text("Персонаж и все его чаты удалятся без возможности восстановления.") },
            confirmButton = { TextButton({ repo.deleteChar(c.id); confirmDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Отмена") } })
    }
}

fun shareCard(ctx: android.content.Context, c: Character) {
    val f = Cards.shareFile(ctx, c)
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json")
        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Карточка ${c.name}"))
}

@Composable
private fun ModelStatus(s: Settings, openModel: () -> Unit) {
    val ctx = LocalContext.current
    val e = ctx.app.engines
    val mode = e.pick(s)
    val ok = if (mode == Mode.LOCAL) e.local.hasModel(s) else e.api.ready(s)
    Surface(onClick = openModel, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) Mint else Amber))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (ok) e.label(s, mode) else "Модель не настроена", fontWeight = FontWeight.Bold)
                Text(
                    when {
                        !ok -> "Скачай Gemma 4 или введи API-ключ"
                        mode == Mode.LOCAL -> "на телефоне · контекст ${s.contextTokens} · офлайн"
                        else -> "через API · ${s.provider.title}"
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Сменить", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LorebookList(nav: Nav) {
    val repo = LocalContext.current.app.repo
    val books by repo.books.collectAsStateWithLifecycle()
    val chars by repo.chars.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Лорбук — это детали мира. Запись попадает в разговор, только когда в чате звучит её ключевое слово, поэтому не занимает память модели зря.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }
        if (books.isEmpty()) item { Text("Лорбуков пока нет.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(books, key = { it.id }) { b ->
            val users = chars.filter { b.id in it.lorebookIds }.joinToString { it.name }
            CardBox {
                NavRow(b.name.ifBlank { "Без названия" }, "${b.entries.size} записей" + if (users.isNotBlank()) " · $users" else "", { nav.go(Route.Lore(b.id)) })
            }
        }
    }
}
