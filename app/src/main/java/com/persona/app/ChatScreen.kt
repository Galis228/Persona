package com.persona.app

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(nav: Nav, chatId: String, focus: String? = null) {
    val ctx = LocalContext.current
    val app = ctx.app
    val repo = app.repo
    val runner = app.runner
    val chats by repo.chats.collectAsStateWithLifecycle()
    val chars by repo.chars.collectAsStateWithLifecycle()
    val s by repo.settings.collectAsStateWithLifecycle()
    val running by runner.running.collectAsStateWithLifecycle()
    val chat = chats.firstOrNull { it.id == chatId }
    val c = chat?.let { ch -> chars.firstOrNull { it.id == ch.charId } }
    if (chat == null || c == null) { LaunchedEffect(Unit) { nav.back() }; return }

    var input by remember(chatId) { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Msg?>(null) }
    var menu by remember { mutableStateOf(false) }
    var showChats by remember { mutableStateOf(false) }
    var remembered by remember { mutableStateOf<List<String>>(emptyList()) }
    var moodNote by remember { mutableStateOf<String?>(null) }
    var personaPick by remember { mutableStateOf(false) }
    var noteEdit by remember { mutableStateOf(false) }
    var momentFor by remember { mutableStateOf<Msg?>(null) }
    var compare by remember { mutableStateOf(false) }
    var apiError by remember { mutableStateOf<ChatEvent.Error?>(null) }
    var undoMsg by remember { mutableStateOf<(() -> Unit)?>(null) }
    var highlight by remember { mutableStateOf(focus) }
    var photo by remember(chatId) { mutableStateOf<String?>(null) }
    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photo = repo.saveImage(uri)
    }
    val voice = rememberVoiceInput { t -> input = if (input.isBlank()) t else "$input $t" }
    val user = s.forChat(chat).user
    val emotion = if (c.moodEnabled) Prompt.emotionFor(chat.mood) else null
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(undoMsg) {
        undoMsg?.let { u ->
            val r = snack.showSnackbar("Сообщение удалено", actionLabel = "Отменить", duration = androidx.compose.material3.SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) u()
            undoMsg = null
        }
    }
    val list = rememberLazyListState()
    val busy = running?.chatId == chatId
    val busyElsewhere = running != null && !busy
    var dots by remember { mutableStateOf(1) }
    LaunchedEffect(busy) { while (busy) { delay(400); dots = dots % 3 + 1 } }

    LaunchedEffect(chatId) {
        runner.events.collect { e ->
            when (e) {
                is ChatEvent.Error -> if (e.chatId == chatId) {
                    if (e.kind != ErrKind.OTHER && e.kind != ErrKind.RATE) { apiError = e; return@collect }
                    val r = snack.showSnackbar(e.message, actionLabel = "Модель", withDismissAction = true)
                    if (r == SnackbarResult.ActionPerformed) nav.go(Route.SettingsPage(if (app.engines.isLocal()) "local" else "api"))
                }
                is ChatEvent.Remembered -> if (e.chatId == chatId) remembered = e.facts
                is ChatEvent.Mood -> if (e.chatId == chatId && e.delta != 0) moodNote = "Отношение ${if (e.delta > 0) "+" else ""}${e.delta * 2}" + (e.mood?.let { " · $it" } ?: "")
            }
        }
    }
    LaunchedEffect(remembered) { if (remembered.isNotEmpty()) { delay(6000); remembered = emptyList() } }
    LaunchedEffect(moodNote) { if (moodNote != null) { delay(4000); moodNote = null } }
    val lastText = chat.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(chat.messages.size, lastText / 80) {
        if (highlight != null) {
            val i = chat.messages.indexOfFirst { it.id == highlight }
            if (i >= 0) { list.scrollToItem(i + if (chat.summarizedUpTo > 0 || chat.facts.isNotEmpty()) 1 else 0); delay(2500); highlight = null; return@LaunchedEffect }
        }
        if (chat.messages.isNotEmpty()) list.animateScrollToItem(chat.messages.size)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                navigationIcon = { IconButton({ nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { nav.go(Route.Editor(c.id)) }) {
                        Avatar(c, 40.dp, emotion = emotion)
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(c.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(when {
                                busy -> "печатает" + ".".repeat(dots)
                                moodNote != null -> moodNote!!
                                c.moodEnabled -> "${chat.mood.ifBlank { "спокойное" }} · отношение ${chat.affinity}"
                                else -> app.engines.label(s)
                            }, style = MaterialTheme.typography.labelMedium,
                                color = if (busy || moodNote != null) Amber else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                actions = {
                    IconButton({ nav.go(Route.Call(chatId)) }) { Icon(Icons.Rounded.Call, "Позвонить") }
                    IconButton({ nav.go(Route.Memory(chatId)) }) { Icon(Icons.Rounded.Psychology, "Память") }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Ещё") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Новый чат") }, { menu = false; nav.replace(Route.ChatR(runner.newChat(c.id, chat.personaId).id)) })
                            DropdownMenuItem({ Text("Новый инкогнито-чат") }, { menu = false; nav.replace(Route.ChatR(runner.newChat(c.id, chat.personaId, incognito = true).id)) })
                            DropdownMenuItem({ Text("Все чаты с ${c.name}") }, { menu = false; showChats = true })
                            DropdownMenuItem({ Text("Я в этом чате: $user") }, { menu = false; personaPick = true })
                            DropdownMenuItem({ Text(if (chat.authorNote.isBlank()) "Заметка автора" else "Заметка автора ✓") }, { menu = false; noteEdit = true })
                            DropdownMenuItem({ Text("Нарисовать сцену") }, { menu = false; runner.illustrate(chatId) }, enabled = running == null)
                            DropdownMenuItem({ Text("Сравнить модели") }, { menu = false; compare = true }, enabled = running == null)
                            DropdownMenuItem({ Text("Режим книги") }, { menu = false; nav.go(Route.Book(chatId)) })
                            DropdownMenuItem({ Text("Изменить персонажа") }, { menu = false; nav.go(Route.Editor(c.id)) })
                            DropdownMenuItem({ Text("Экспорт карточки") }, { menu = false; shareCard(ctx, c) })
                        }
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
        ChatBackground(c.background)
        Column(Modifier.fillMaxSize().imePadding()) {
            if (chat.incognito) Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VisibilityOff, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp))
                Text("Инкогнито: без памяти, ничего не сохраняется", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                TextButton({ repo.deleteChat(chatId); nav.back() }) { Text("Завершить") }
            }
            MemoryBar(chat, c, s, app.engines.isLocal(s))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (chat.summarizedUpTo > 0 || chat.facts.isNotEmpty()) item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Surface(onClick = { nav.go(Route.Memory(chatId)) }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Text("Сжато ${chat.summarizedUpTo} старых сообщений · ${chat.facts.size} фактов в памяти",
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(chat.messages, key = { it.id }) { m ->
                    val isLastChar = !m.fromUser && m.id == chat.messages.last().id
                    val generatingThis = busy && running?.msgId == m.id
                    Box(Modifier.fillMaxWidth().background(if (highlight == m.id) Accent.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(16.dp))) {
                    MessageBubble(
                        m, c, generatingThis, showControls = isLastChar || selected == m.id,
                        onTap = { selected = if (selected == m.id) null else m.id },
                        onSwipe = { d -> runner.swipe(chatId, m.id, d) },
                        onRegen = { runner.regenerate(chatId, m.id) },
                        onEdit = { editing = m },
                        onBranch = { runner.branch(chatId, m.id)?.let { nav.replace(Route.ChatR(it.id)) } },
                        onSpeak = { app.voice.speak(m.text, c.voice ?: Voice()) },
                        onCopy = {
                            ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("msg", m.text))
                            Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = { runner.deleteWithUndo(chatId, m.id)?.let { undo -> undoMsg = undo } },
                        onMoment = { momentFor = m },
                        emotion = emotion,
                        onReact = { e -> runner.react(chatId, m.id, e) },
                        canAct = !busy && !busyElsewhere,
                        isLast = m.id == chat.messages.last().id,
                    )
                    }
                }
                if (chat.messages.isEmpty() && !busy) item {
                    Text("Напиши первое сообщение, чтобы начать.", Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    AnimatedVisibility(remembered.isNotEmpty()) {
                        Row(Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Lightbulb, null, tint = Amber, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("${c.name} запомнил(а): " + remembered.joinToString("; "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Поле ввода
            Column(Modifier.background(if (c.background != null) Color.Transparent else MaterialTheme.colorScheme.background).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (chat.authorNote.isNotBlank()) Surface(onClick = { noteEdit = true }, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("Заметка: " + chat.authorNote, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                photo?.let { ph ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MsgImage(ph, Modifier.size(72.dp))
                        IconButton({ photo = null }) { Icon(Icons.Rounded.Close, "Убрать фото") }
                        Text(if (app.engines.isLocal()) "Фото понимают Gemma 4 E2B/E4B" else "Нужна модель, которая видит картинки",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip({ input = if (input.isBlank()) "**" else "$input **" }, { Text("*действие*") }, enabled = !busy)
                    AssistChip({ runner.continueLast(chatId) }, { Text("Продолжи") }, enabled = !busy && !busyElsewhere && chat.messages.isNotEmpty())
                    AssistChip({ runner.impersonate(chatId) { input = it } }, { Text("Подскажи ответ") }, enabled = !busy && !busyElsewhere && chat.messages.isNotEmpty())
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton({ pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) {
                        Icon(Icons.Rounded.AddPhotoAlternate, "Фото")
                    }
                    TextField(
                        input, { input = it }, Modifier.weight(1f),
                        placeholder = { Text("$user → ${c.name}…") }, maxLines = 6, shape = RoundedCornerShape(26.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    if (busy) FilledIconButton({ runner.stop() }, Modifier.size(52.dp)) { Icon(Icons.Rounded.Stop, "Остановить") }
                    else if (input.isBlank() && photo == null) FilledIconButton(voice, Modifier.size(52.dp), enabled = !busyElsewhere) { Icon(Icons.Rounded.Mic, "Сказать голосом") }
                    else FilledIconButton({
                        runner.send(chatId, input, photo); input = ""; photo = null; selected = null
                    }, Modifier.size(52.dp), enabled = !busyElsewhere) { Icon(Icons.AutoMirrored.Rounded.Send, "Отправить") }
                }
                if (busyElsewhere) Text("Модель занята ответом в другом чате…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
    }

    editing?.let { m ->
        var text by remember(m.id) { mutableStateOf(m.text) }
        AlertDialog({ editing = null },
            title = { Text("Изменить сообщение") },
            text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 4, maxLines = 12) },
            confirmButton = { TextButton({ runner.edit(chatId, m.id, text); editing = null }) { Text("Сохранить") } },
            dismissButton = { TextButton({ editing = null }) { Text("Отмена") } })
    }
    if (compare) CompareDialog(chatId) { compare = false }
    apiError?.let { ApiErrorDialog(it, nav) { apiError = null } }
    if (noteEdit) AuthorNoteDialog(chat.authorNote, { runner.setNote(chatId, it) }) { noteEdit = false }
    momentFor?.let { m ->
        var t by remember(m.id) { mutableStateOf(m.text.replace(Regex("\\*[^*]*\\*"), "").trim().take(140)) }
        AlertDialog({ momentFor = null }, title = { Text("Ключевой момент") },
            text = { OutlinedTextField(t, { t = it.take(300) }, Modifier.fillMaxWidth(), minLines = 2, placeholder = { Text("Например: первая ссора из-за карты") }) },
            confirmButton = { TextButton({ if (t.isNotBlank()) { runner.addMoment(chatId, t); Toast.makeText(ctx, "Момент сохранён в «Памяти»", Toast.LENGTH_SHORT).show() }; momentFor = null }) { Text("Сохранить") } },
            dismissButton = { TextButton({ momentFor = null }) { Text("Отмена") } })
    }
    if (personaPick) PersonaPicker(chat.personaId) { id -> runner.setPersona(chatId, id); personaPick = false }
    if (showChats) ChatsDialog(c, chatId, onOpen = { id -> showChats = false; if (id != chatId) nav.replace(Route.ChatR(id)) }) { showChats = false }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    m: Msg, c: Character, generating: Boolean, showControls: Boolean,
    onTap: () -> Unit, onSwipe: (Int) -> Unit, onRegen: () -> Unit, onEdit: () -> Unit, onBranch: () -> Unit,
    onSpeak: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit, onMoment: () -> Unit, canAct: Boolean, isLast: Boolean,
    emotion: String? = null, onReact: (String?) -> Unit = {},
) {
    val user = m.fromUser
    if (m.kind == PICTURE) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (m.image != null) MsgImage(m.image) else Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.size(8.dp))
                Text(if (m.text.isBlank()) "Придумываю сцену…" else m.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (m.image != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.info, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onDelete, enabled = canAct) { Icon(Icons.Rounded.Delete, "Удалить", Modifier.size(18.dp)) }
            }
        }
        return
    }
    val look = LocalLook.current
    var reactMenu by remember { mutableStateOf(false) }
    val flat = look.bubbles == BubbleStyle.FLAT
    val compact = look.bubbles == BubbleStyle.COMPACT
    val r = if (compact) 12.dp else 18.dp
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user && !flat) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.Top) {
            if (!user && look.chatAvatars) { Avatar(c, if (compact) 28.dp else 34.dp, emotion = emotion); Spacer(Modifier.size(8.dp)) }
            Box {
                Surface(
                    shape = if (flat) RoundedCornerShape(0.dp) else if (user) RoundedCornerShape(r, r, 6.dp, r) else RoundedCornerShape(r, r, r, 6.dp),
                    color = when { flat -> Color.Transparent; user -> MaterialTheme.colorScheme.primary.copy(alpha = look.bubbleAlpha.coerceIn(0.5f, 1f)); else -> BubbleChar },
                    modifier = Modifier.widthIn(max = if (flat) 600.dp else 320.dp).combinedClickable(onClick = onTap, onLongClick = { if (user) onCopy() else reactMenu = true }),
                ) {
                    Column(Modifier.padding(horizontal = if (flat) 2.dp else if (compact) 11.dp else 14.dp, vertical = if (compact || flat) 7.dp else 11.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (flat) Text(if (user) "Ты" else c.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = if (user) MaterialTheme.colorScheme.onSurfaceVariant else Accent)
                        if (m.image != null) MsgImage(m.image)
                        val onBubble = if (user && !flat) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        if (m.text.isBlank() && m.image != null) Unit
                        else if (m.text.isBlank() && generating) TypingDots()
                        else Text(
                            rpText(m.text, if (user && !flat) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f) else Accent),
                            color = onBubble, style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                DropdownMenu(reactMenu, { reactMenu = false }) {
                    Row(Modifier.padding(horizontal = 8.dp)) {
                        REACTIONS.forEach { e -> TextButton({ onReact(if (m.reaction == e) null else e); reactMenu = false }) { Text(e, fontSize = 22.sp) } }
                    }
                    DropdownMenuItem({ Text("Копировать") }, { reactMenu = false; onCopy() })
                }
            }
        }
        m.reaction?.let { e ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(start = if (look.chatAvatars) 48.dp else 8.dp).offset(y = (-6).dp).clickable { onReact(null) }) {
                Text(e, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), fontSize = 14.sp)
            }
        }
        if (showControls && !generating) {
            Row(Modifier.padding(start = if (!user && look.chatAvatars) 36.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!user && (m.variants.size > 1 || isLast)) {
                    IconButton({ onSwipe(-1) }, enabled = canAct && m.sel > 0) { Icon(Icons.Rounded.ChevronLeft, "Предыдущий вариант") }
                    Text("${m.sel + 1} / ${m.variants.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton({ onSwipe(1) }, enabled = canAct && (m.sel < m.variants.size - 1 || isLast)) { Icon(Icons.Rounded.ChevronRight, "Следующий вариант") }
                    if (isLast) IconButton(onRegen, enabled = canAct) { Icon(Icons.Rounded.Refresh, "Перегенерировать") }
                }
                IconButton(onEdit, enabled = canAct) { Icon(Icons.Rounded.Edit, "Изменить", Modifier.size(20.dp)) }
                if (!user) IconButton(onSpeak) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Озвучить", Modifier.size(20.dp)) }
                IconButton(onBranch, enabled = canAct) { Icon(Icons.Rounded.AccountTree, "Ветка отсюда", Modifier.size(20.dp)) }
                IconButton(onMoment) { Icon(Icons.Rounded.Star, "Отметить момент", Modifier.size(20.dp)) }
                IconButton(onCopy) { Icon(Icons.Rounded.ContentCopy, "Копировать", Modifier.size(20.dp)) }
                IconButton(onDelete, enabled = canAct) { Icon(Icons.Rounded.Delete, "Удалить", Modifier.size(20.dp)) }
            }
        }
        if (!user && m.info.isNotBlank() && !generating) {
            Text(m.info, Modifier.padding(start = if (look.chatAvatars) 48.dp else 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChatsDialog(c: Character, current: String, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val repo = LocalContext.current.app.repo
    val chats by repo.chats.collectAsStateWithLifecycle()
    val mine = chats.filter { it.charId == c.id && it.sceneId == null }.sortedByDescending { it.updated }
    var confirm by remember { mutableStateOf<Chat?>(null) }
    AlertDialog(onClose,
        title = { Text("Чаты с ${c.name}") },
        text = {
            LazyColumn {
                items(mine, key = { it.id }) { ch ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(ch.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text((ch.title.ifBlank { "Чат" }) + " · " + timeLabel(ch.updated) + if (ch.id == current) " · открыт" else "", fontWeight = FontWeight.Bold)
                            Text(ch.messages.lastOrNull()?.text?.replace('\n', ' ') ?: "пусто", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        if (ch.id != current) IconButton({ confirm = ch }) { Icon(Icons.Rounded.Delete, "Удалить") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Закрыть") } })
    confirm?.let { ch ->
        AlertDialog({ confirm = null }, title = { Text("Удалить чат?") },
            confirmButton = { TextButton({ repo.deleteChat(ch.id); confirm = null }) { Text("Удалить") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Отмена") } })
    }
}

@Suppress("unused") private val keepIcon = Icons.AutoMirrored.Rounded.ArrowForward


/** Шкала «память модели»: сколько контекста занимает разговор и когда начнётся сжатие. */
@Composable
private fun MemoryBar(chat: Chat, c: Character, s: Settings, local: Boolean) {
    val books by LocalContext.current.app.repo.books.collectAsStateWithLifecycle()
    var info by remember { mutableStateOf(false) }
    val total = if (local) s.contextTokens else 24000
    val used = remember(chat.messages.size, chat.summary, chat.facts.size, local, s.contextTokens) {
        val st = s.forChat(chat)
        val sys = Prompt.system(c, chat, st, books, chat.messages.takeLast(6).joinToString("\n") { it.text })
        val visible = chat.messages.drop(chat.summarizedUpTo)
        Prompt.estimateTokens(sys) + visible.sumOf { Prompt.estimateTokens(it.text) } + st.length.maxOut
    }
    val frac = (used.toFloat() / total).coerceIn(0f, 1f)
    val untilSum = if (s.summarize && !chat.incognito) (s.summarizeEvery - (chat.messages.size - 8 - chat.summarizedUpTo)).coerceAtLeast(0) else null
    Column(Modifier.fillMaxWidth().clickable { info = !info }.padding(horizontal = 16.dp, vertical = 3.dp)) {
        LinearProgressIndicator({ frac }, Modifier.fillMaxWidth().height(3.dp),
            color = when { frac > 0.9f -> MaterialTheme.colorScheme.error; frac > 0.7f -> Amber; else -> Accent },
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        if (info) Text(
            "Память модели: ≈$used из $total токенов (${(frac * 100).toInt()}%). " +
                (if (frac >= 1f) "Самые старые сообщения уже не помещаются. " else "") +
                (untilSum?.let { if (it == 0) "Сжатие истории — после следующего ответа." else "Сжатие истории через $it сообщ." } ?: "Сжатие выключено."),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}
