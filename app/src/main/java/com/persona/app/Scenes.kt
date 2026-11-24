package com.persona.app

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

// ======================== Строка группового чата в общем списке ========================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupRow(nav: Nav, sc: Scene, chat: Chat?) {
    val ctx = LocalContext.current
    val repo = ctx.app.repo
    val chars by repo.chars.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val members = sc.charIds.mapNotNull { id -> chars.firstOrNull { it.id == id } }
    Box {
        Row(Modifier.fillMaxWidth().combinedClickable(
            onClick = { nav.go(Route.ChatR(chat?.id ?: ctx.app.runner.newSceneChat(sc.id).id)) },
            onLongClick = { menu = true },
        ).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp)) {
                members.take(3).forEachIndexed { i, c ->
                    Box(Modifier.offset(x = (i * 12).dp, y = (if (i == 1) 16 else 4).dp)
                        .border(2.dp, MaterialTheme.colorScheme.background, androidx.compose.foundation.shape.CircleShape)) { Avatar(c, 32.dp) }
                }
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sc.pinned) { Icon(androidx.compose.material.icons.Icons.Rounded.PushPin, "Закреплён", Modifier.size(14.dp), tint = Accent); Spacer(Modifier.width(4.dp)) }
                    Text(sc.name.ifBlank { "Групповой чат" }, Modifier.weight(1f, fill = false), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = if (sc.mode == SceneMode.ADVENTURE) Amber.copy(alpha = 0.18f) else Accent.copy(alpha = 0.18f)) {
                        Text(if (sc.mode == SceneMode.ADVENTURE) "приключение" else "общий чат", Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall, color = if (sc.mode == SceneMode.ADVENTURE) Amber else Accent)
                    }
                    Spacer(Modifier.weight(1f))
                    chat?.let { Text(timeLabel(it.updated), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                val preview = chat?.messages?.lastOrNull()?.let { m -> Prompt.sceneTranscriptName(m, chars.associateBy { it.id }, "Ты") + ": " + m.text }
                    ?: members.joinToString { it.name }
                Text(preview.replace('\n', ' '), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("Начать заново") }, { menu = false; nav.go(Route.ChatR(ctx.app.runner.newSceneChat(sc.id).id)) })
            DropdownMenuItem({ Text("Изменить") }, { menu = false; nav.go(Route.SceneEditor(sc.id)) })
            DropdownMenuItem({ Text(if (sc.pinned) "Открепить" else "Закрепить") }, { menu = false; repo.saveScene(sc.copy(pinned = !sc.pinned)) })
            DropdownMenuItem({ Text(if (sc.archived) "Вернуть из архива" else "В архив") }, { menu = false; repo.saveScene(sc.copy(archived = !sc.archived, pinned = false)) })
            DropdownMenuItem({ Text("Удалить", color = MaterialTheme.colorScheme.error) }, { menu = false
                val gone = repo.chats.value.filter { it.sceneId == sc.id }
                repo.deleteScene(sc.id)
                UndoBus.post("«${sc.name}» удалён") { repo.restore(emptyList(), listOf(sc), gone) }
            })
        }
    }
    if (confirm) AlertDialog({ confirm = false }, title = { Text("Удалить «${sc.name}»?") }, text = { Text("Вся переписка в этом групповом чате удалится. Персонажи останутся.") },
        confirmButton = { TextButton({ repo.deleteScene(sc.id); confirm = false }) { Text("Удалить") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Отмена") } })
}

// ======================== Редактор сцены ========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneEditorScreen(nav: Nav, sceneId: String?) {
    val ctx = LocalContext.current
    val repo = ctx.app.repo
    val original = remember(sceneId) { repo.scene(sceneId) }
    var sc by remember(sceneId) { mutableStateOf(original ?: Scene(mode = SceneMode.HANGOUT)) }
    val chars by repo.chars.collectAsStateWithLifecycle()
    val books by repo.books.collectAsStateWithLifecycle()

    fun save() {
        if (sc.name.isBlank()) { Toast.makeText(ctx, "Дай чату название", Toast.LENGTH_SHORT).show(); return }
        if (sc.charIds.isEmpty()) { Toast.makeText(ctx, "Выбери хотя бы одного участника", Toast.LENGTH_SHORT).show(); return }
        repo.saveScene(sc)
        if (original == null) nav.replace(Route.ChatR(ctx.app.runner.newSceneChat(sc.id).id)) else nav.back()
    }

    Scaffold(topBar = {
        TopAppBar(navigationIcon = { IconButton({ nav.back() }) { Icon(Icons.Rounded.Close, "Закрыть") } },
            title = { Text(if (original == null) "Новый групповой чат" else "Групповой чат", fontWeight = FontWeight.Bold) })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LimitedField("Название", sc.name, 40, if (sc.mode == SceneMode.HANGOUT) "Например: Болталка" else "Например: Таверна «Гусь»") { sc = sc.copy(name = it) }
            ModeSwitch(sc.mode) { sc = sc.copy(mode = it) }
            if (sc.mode == SceneMode.HANGOUT)
                LimitedField("Где вы общаетесь (необязательно)", sc.world, 2000, "Например: общий чат в мессенджере, вечер пятницы", lines = 2) { sc = sc.copy(world = it) }
            else
                LimitedField("Мир и обстановка", sc.world, 2000, "Например: портовый город на краю империи, вечер, за окном шторм. В таверне собираются контрабандисты.", lines = 4) { sc = sc.copy(world = it) }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Участники", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                if (chars.isEmpty()) Text("Сначала создай персонажей во вкладке «Персонажи».", color = MaterialTheme.colorScheme.onSurfaceVariant)
                chars.forEach { c ->
                    val on = c.id in sc.charIds
                    Surface(onClick = { sc = sc.copy(charIds = if (on) sc.charIds - c.id else sc.charIds + c.id) }, shape = RoundedCornerShape(16.dp),
                        color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(c, 40.dp)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.Bold)
                                if (c.tagline.isNotBlank()) Text(c.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Checkbox(on, { sc = sc.copy(charIds = if (it) sc.charIds + c.id else sc.charIds - c.id) })
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Кто отвечает после твоей реплики", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TurnMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(sc.turnMode == m, { sc = sc.copy(turnMode = m) }, SegmentedButtonDefaults.itemShape(i, TurnMode.entries.size)) { Text(m.title, maxLines = 1, fontSize = 13.sp) }
                    }
                }
                Text(when (sc.turnMode) {
                    TurnMode.MENTION -> if (sc.mode == SceneMode.HANGOUT) "Отвечает тот, кого ты назвал по имени, иначе — один-два участника. Персонажи отвечают и друг другу." else "Отвечает тот, кого ты назвал по имени, иначе — последний собеседник."
                    TurnMode.ROUND -> "Персонажи отвечают по кругу. Имя в реплике всё равно имеет приоритет."
                    TurnMode.RANDOM -> "Отвечает случайный участник. Имя в реплике всё равно имеет приоритет."
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (sc.mode == SceneMode.ADVENTURE) CardBox {
                Column {
                    SwitchRow("Рассказчик", "Описывает мир и последствия действий (*в звёздочках*)", sc.narrator) { sc = sc.copy(narrator = it) }
                    if (sc.narrator) SwitchRow("Случайные события", "Иногда рассказчик вбрасывает неожиданный поворот", sc.randomEvents) { sc = sc.copy(randomEvents = it) }
                    SwitchRow("Кубики и статы героя", "Бросок d20, здоровье, золото, инвентарь", sc.dice) { sc = sc.copy(dice = it) }
                }
            }
            if (sc.mode == SceneMode.ADVENTURE && sc.dice) CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Твой герой в начале игры", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("Здоровье", sc.hero.maxHp, Modifier.weight(1f)) { sc = sc.copy(hero = sc.hero.copy(maxHp = it.coerceAtLeast(1), hp = it.coerceAtLeast(1))) }
                        NumField("Золото", sc.hero.gold, Modifier.weight(1f)) { sc = sc.copy(hero = sc.hero.copy(gold = it)) }
                    }
                    OutlinedTextField(sc.hero.inventory, { sc = sc.copy(hero = sc.hero.copy(inventory = it.take(300))) }, Modifier.fillMaxWidth(),
                        label = { Text("Инвентарь") }, placeholder = { Text("кинжал, фонарь, карта") })
                }
            }
            if (sc.mode == SceneMode.ADVENTURE)
                LimitedField("Начало сцены (необязательно)", sc.opening, 2000, "Если пусто — рассказчик сам опишет начало.", lines = 3) { sc = sc.copy(opening = it) }

            BackgroundPicker(sc.background) { sc = sc.copy(background = it) }
            if (books.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Лорбуки мира", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                books.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(b.id in sc.lorebookIds, { on -> sc = sc.copy(lorebookIds = if (on) sc.lorebookIds + b.id else sc.lorebookIds - b.id) })
                        Text(b.name.ifBlank { "Без названия" })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.End) {
                Button(::save, Modifier.height(56.dp), enabled = sc.name.isNotBlank() && sc.charIds.isNotEmpty(), shape = RoundedCornerShape(28.dp)) {
                    Text(if (original == null) (if (sc.mode == SceneMode.HANGOUT) "Начать чат" else "Начать приключение") else "Сохранить", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun NumField(label: String, v: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    var t by remember { mutableStateOf(v.toString()) }
    OutlinedTextField(t, { s -> t = s.filter { it.isDigit() }.take(5); t.toIntOrNull()?.let(onChange) }, modifier, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

// ======================== Игра в сцене ========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneChatScreen(nav: Nav, chatId: String, focus: String? = null) {
    val ctx = LocalContext.current
    val app = ctx.app
    val repo = app.repo
    val runner = app.runner
    val chats by repo.chats.collectAsStateWithLifecycle()
    val chars by repo.chars.collectAsStateWithLifecycle()
    val scenes by repo.scenes.collectAsStateWithLifecycle()
    val s by repo.settings.collectAsStateWithLifecycle()
    val running by runner.running.collectAsStateWithLifecycle()
    val chat = chats.firstOrNull { it.id == chatId }
    val sc = chat?.let { ch -> scenes.firstOrNull { it.id == ch.sceneId } }
    if (chat == null || sc == null) { LaunchedEffect(Unit) { nav.back() }; return }
    val members = sc.charIds.mapNotNull { id -> chars.firstOrNull { it.id == id } }
    val byId = chars.associateBy { it.id }
    val user = s.forChat(chat).user

    var input by remember(chatId) { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Msg?>(null) }
    var menu by remember { mutableStateOf(false) }
    var heroEdit by remember { mutableStateOf(false) }
    var personaPick by remember { mutableStateOf(false) }
    var noteEdit by remember { mutableStateOf(false) }
    var apiError by remember { mutableStateOf<ChatEvent.Error?>(null) }
    val scope2 = androidx.compose.runtime.rememberCoroutineScope()
    val voice = rememberVoiceInput { t -> input = if (input.isBlank()) t else "$input $t" }
    val snack = remember { SnackbarHostState() }
    val list = rememberLazyListState()
    val busy = running?.chatId == chatId
    val busyElsewhere = running != null && !busy

    LaunchedEffect(chatId) {
        runner.events.collect { e ->
            if (e is ChatEvent.Error && e.chatId == chatId) {
                if (e.kind != ErrKind.OTHER && e.kind != ErrKind.RATE) apiError = e else snack.showSnackbar(e.message, withDismissAction = true)
            }
        }
    }
    val lastLen = chat.messages.lastOrNull()?.text?.length ?: 0
    var highlight by remember { mutableStateOf(focus) }
    LaunchedEffect(chat.messages.size, lastLen / 80) {
        if (highlight != null) {
            val i = chat.messages.indexOfFirst { it.id == highlight }
            if (i >= 0) { list.scrollToItem(i); kotlinx.coroutines.delay(2500); highlight = null; return@LaunchedEffect }
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
                    Column {
                        Text(sc.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (busy) "${running?.msgId?.let { id -> chat.messages.firstOrNull { it.id == id } }?.let { Prompt.sceneTranscriptName(it, byId, user) } ?: "Кто-то"} печатает…"
                            else members.joinToString { it.name } + " · ты: $user", style = MaterialTheme.typography.labelMedium,
                            color = if (busy) Amber else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Ещё") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Начать заново") }, { menu = false; nav.replace(Route.ChatR(runner.newSceneChat(sc.id, chat.personaId).id)) })
                            DropdownMenuItem({ Text("Я в этом чате: $user") }, { menu = false; personaPick = true })
                            DropdownMenuItem({ Text(if (chat.authorNote.isBlank()) "Заметка автора" else "Заметка автора ✓") }, { menu = false; noteEdit = true })
                            DropdownMenuItem({ Text("Нарисовать сцену") }, { menu = false; runner.illustrate(chatId) }, enabled = running == null)
                            DropdownMenuItem({ Text("Режим книги") }, { menu = false; nav.go(Route.Book(chatId)) })
                            DropdownMenuItem({ Text("Настройки группы") }, { menu = false; nav.go(Route.SceneEditor(sc.id)) })
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 6.dp)) {
                ModeSwitch(sc.mode, compact = true, enabled = running == null) { runner.setSceneMode(sc.id, it) }
            }
            val adv = sc.mode == SceneMode.ADVENTURE
            if (adv && sc.dice) chat.hero?.let { h ->
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).clickable { heroEdit = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Favorite, null, Modifier.size(16.dp), tint = Color(0xFFFF8A80)); Spacer(Modifier.width(4.dp))
                        Text("${h.hp}/${h.maxHp}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Paid, null, Modifier.size(16.dp), tint = Amber); Spacer(Modifier.width(4.dp))
                        Text("${h.gold}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                    Text(h.inventory.ifBlank { "инвентарь пуст" }, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            ChatBackground(sc.background)
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(chat.messages, key = { it.id }) { m ->
                    val generating = busy && running?.msgId == m.id
                    if (highlight == m.id) Box(Modifier.fillMaxWidth().height(2.dp).background(Accent))
                    SceneMessage(m, m.speaker?.let { byId[it] }, user, generating, selected == m.id && !generating,
                        onTap = { selected = if (selected == m.id) null else m.id },
                        canAct = running == null,
                        isLast = m.id == chat.messages.last().id,
                        onRegen = { runner.regenerate(chatId, m.id) },
                        onSwipe = { runner.swipe(chatId, m.id, it) },
                        onEdit = { editing = m },
                        onDelete = { runner.deleteWithUndo(chatId, m.id)?.let { undo -> scope2.launch {
                            val r = snack.showSnackbar("Сообщение удалено", actionLabel = "Отменить", duration = androidx.compose.material3.SnackbarDuration.Short)
                            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) undo()
                        } } },
                        onCopy = {
                            ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("msg", m.text))
                            Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
                        },
                        onSpeak = { app.voice.speak(m.text, m.speaker?.let { byId[it] }?.voice ?: Voice()) })
                }
            }
            }
            Column(Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (chat.authorNote.isNotBlank()) Surface(onClick = { noteEdit = true }, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("Заметка: " + chat.authorNote, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (adv && sc.dice) AssistChip({ runner.rollDice(chatId) }, { Text("d20") }, leadingIcon = { Icon(Icons.Rounded.Casino, null, Modifier.size(18.dp)) }, enabled = running == null)
                    members.forEach { c -> AssistChip({ runner.sceneSpeak(chatId, c.id) }, { Text(c.name) }, leadingIcon = { Avatar(c, 20.dp) }, enabled = running == null) }
                    if (adv && sc.narrator) AssistChip({ runner.sceneSpeak(chatId, NARRATOR) }, { Text("Рассказчик") }, enabled = running == null)
                    if (members.size > 1) AssistChip({ runner.banter(chatId) }, { Text("Пусть поболтают") }, enabled = running == null)
                    if (adv || s.italicActions) AssistChip({ input = if (input.isBlank()) "**" else "$input **" }, { Text("*действие*") })
                    AssistChip({ runner.impersonate(chatId) { input = it } }, { Text("Подскажи") }, enabled = running == null && chat.messages.isNotEmpty())
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text(if (adv) "Что делает $user?" else "Сообщение в чат…") }, maxLines = 6, shape = RoundedCornerShape(26.dp),
                        colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                    if (busy) FilledIconButton({ runner.stop() }, Modifier.size(52.dp)) { Icon(Icons.Rounded.Stop, "Остановить") }
                    else if (input.isBlank()) FilledIconButton(voice, Modifier.size(52.dp), enabled = !busyElsewhere) { Icon(Icons.Rounded.Mic, "Сказать голосом") }
                    else FilledIconButton({ runner.send(chatId, input); input = ""; selected = null },
                        Modifier.size(52.dp), enabled = !busyElsewhere) { Icon(Icons.AutoMirrored.Rounded.Send, "Отправить") }
                }
                Text(if (adv) "Имя персонажа в реплике — и ответит именно он. Кнопки с именами заставляют персонажа сказать что-то самому."
                    else "Назови персонажа по имени — ответит он. Персонажи отвечают и друг другу; «Пусть поболтают» — разговор без тебя.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    editing?.let { m ->
        var text by remember(m.id) { mutableStateOf(m.text) }
        AlertDialog({ editing = null }, title = { Text("Изменить сообщение") },
            text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 4, maxLines = 12) },
            confirmButton = { TextButton({ runner.edit(chatId, m.id, text); editing = null }) { Text("Сохранить") } },
            dismissButton = { TextButton({ editing = null }) { Text("Отмена") } })
    }
    if (heroEdit) chat.hero?.let { h ->
        var hp by remember { mutableStateOf(h.hp.toString()) }
        var gold by remember { mutableStateOf(h.gold.toString()) }
        var inv by remember { mutableStateOf(h.inventory) }
        var notes by remember { mutableStateOf(h.notes) }
        AlertDialog({ heroEdit = false }, title = { Text("Твой герой") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(hp, { hp = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text("Здоровье из ${h.maxHp}") }, singleLine = true)
                        OutlinedTextField(gold, { gold = it.filter(Char::isDigit).take(6) }, Modifier.weight(1f), label = { Text("Золото") }, singleLine = true)
                    }
                    OutlinedTextField(inv, { inv = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Инвентарь") })
                    OutlinedTextField(notes, { notes = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Заметки (раны, проклятия, навыки)") })
                    Text("Рассказчик сам меняет здоровье и золото по ходу игры.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton({
                runner.setHero(chatId, h.copy(hp = (hp.toIntOrNull() ?: h.hp).coerceIn(0, h.maxHp), gold = gold.toIntOrNull() ?: h.gold, inventory = inv.trim(), notes = notes.trim()))
                heroEdit = false
            }) { Text("Сохранить") } },
            dismissButton = { TextButton({ heroEdit = false }) { Text("Отмена") } })
    }
    if (personaPick) PersonaPicker(chat.personaId) { id -> runner.setPersona(chatId, id); personaPick = false }
    if (noteEdit) AuthorNoteDialog(chat.authorNote, { runner.setNote(chatId, it) }) { noteEdit = false }
    apiError?.let { ApiErrorDialog(it, nav) { apiError = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SceneMessage(
    m: Msg, c: Character?, user: String, generating: Boolean, showControls: Boolean, onTap: () -> Unit, canAct: Boolean, isLast: Boolean,
    onRegen: () -> Unit, onSwipe: (Int) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onCopy: () -> Unit, onSpeak: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = when { m.speaker == DICE || m.kind == PICTURE -> Alignment.CenterHorizontally; m.fromUser -> Alignment.End; else -> Alignment.Start }) {
        when {
            m.kind == PICTURE -> if (m.image != null) MsgImage(m.image) else Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp))
                Text(if (m.text.isBlank()) "Придумываю сцену…" else m.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            m.speaker == DICE -> Surface(shape = RoundedCornerShape(14.dp), color = Amber.copy(alpha = 0.12f), modifier = Modifier.border(1.dp, Amber.copy(alpha = 0.4f), RoundedCornerShape(14.dp)).combinedClickable(onClick = onTap, onLongClick = onCopy)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Casino, null, Modifier.size(18.dp), tint = Amber); Spacer(Modifier.width(8.dp))
                    Text(m.text, fontWeight = FontWeight.Bold)
                }
            }
            m.speaker == NARRATOR -> Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).combinedClickable(onClick = onTap, onLongClick = onCopy)) {
                Column(Modifier.padding(14.dp)) {
                    Text("РАССКАЗЧИК", style = MaterialTheme.typography.labelSmall, color = Amber, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(4.dp))
                    BodyText(m, generating, MaterialTheme.colorScheme.onSurface, Accent)
                }
            }
            m.fromUser -> Surface(shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp), color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.widthIn(max = 320.dp).combinedClickable(onClick = onTap, onLongClick = onCopy)) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) { Text(rpText(m.text, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyLarge) }
            }
            else -> Row(verticalAlignment = Alignment.Top) {
                if (LocalLook.current.chatAvatars) { Avatar(c, 32.dp); Spacer(Modifier.width(8.dp)) }
                Surface(shape = RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp), color = BubbleChar,
                    modifier = Modifier.widthIn(max = 290.dp).combinedClickable(onClick = onTap, onLongClick = onCopy)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(c?.name ?: "?", style = MaterialTheme.typography.labelMedium, color = Accent, fontWeight = FontWeight.Bold)
                        BodyText(m, generating, MaterialTheme.colorScheme.onSurface, Accent)
                    }
                }
            }
        }
        if (showControls && m.speaker != DICE && m.kind != PICTURE) Row(verticalAlignment = Alignment.CenterVertically) {
            if (!m.fromUser && m.variants.size > 1) {
                IconButton({ onSwipe(-1) }, enabled = canAct && m.sel > 0) { Text("‹", fontSize = 22.sp) }
                Text("${m.sel + 1}/${m.variants.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton({ onSwipe(1) }, enabled = canAct && (m.sel < m.variants.size - 1 || isLast)) { Text("›", fontSize = 22.sp) }
            }
            if (!m.fromUser && isLast) IconButton(onRegen, enabled = canAct) { Icon(Icons.Rounded.Refresh, "Перегенерировать", Modifier.size(20.dp)) }
            IconButton(onEdit, enabled = canAct) { Icon(Icons.Rounded.Edit, "Изменить", Modifier.size(20.dp)) }
            if (!m.fromUser) IconButton(onSpeak) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Озвучить", Modifier.size(20.dp)) }
            IconButton(onCopy) { Icon(Icons.Rounded.ContentCopy, "Копировать", Modifier.size(20.dp)) }
            IconButton(onDelete, enabled = canAct) { Icon(Icons.Rounded.Delete, "Удалить", Modifier.size(20.dp)) }
        }
        if (!m.fromUser && m.speaker != DICE && m.info.isNotBlank() && !generating && showControls)
            Text(m.info, Modifier.padding(start = 40.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BodyText(m: Msg, generating: Boolean, color: Color, action: Color) {
    if (m.text.isBlank() && generating) TypingDots()
    else Text(rpText(m.text, action), color = color, style = MaterialTheme.typography.bodyLarge)
}

/** Выбор персоны для чата или сцены. null — основная. */
@Composable
fun PersonaPicker(current: String?, onPick: (String?) -> Unit) {
    val s by LocalContext.current.app.repo.settings.collectAsStateWithLifecycle()
    AlertDialog({ onPick(current) },
        title = { Text("Кто ты в этом чате") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (s.personas.isEmpty()) Text("Персон пока нет. Создай их в «Настройки → Мои персоны».", color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.personas.forEach { p ->
                    val on = (current ?: s.defaultPersonaId) == p.id
                    Surface(onClick = { onPick(p.id) }, shape = RoundedCornerShape(14.dp),
                        color = if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(Character(name = p.name), 36.dp); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                if (p.about.isNotBlank()) Text(p.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onPick(current) }) { Text("Закрыть") } })
}


/** Переключатель «Общий чат / Приключение». */
@Composable
fun ModeSwitch(mode: SceneMode, compact: Boolean = false, enabled: Boolean = true, onChange: (SceneMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SceneMode.entries.forEachIndexed { i, m ->
                SegmentedButton(mode == m, { onChange(m) }, SegmentedButtonDefaults.itemShape(i, SceneMode.entries.size), enabled = enabled,
                    modifier = if (compact) Modifier.height(36.dp) else Modifier) { Text(m.title, fontSize = if (compact) 13.sp else 14.sp) }
            }
        }
        if (!compact) Text(if (mode == SceneMode.HANGOUT) "Просто общение: персонажи болтают с тобой и друг с другом, без сюжета и кубиков."
            else "Ролевая игра: рассказчик, мир, кубики d20, здоровье и золото героя.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
