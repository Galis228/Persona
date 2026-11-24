package com.persona.app

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val TAG_SUGGESTIONS = listOf("фэнтези", "романтика", "приключения", "аниме", "юмор", "хоррор", "детектив", "фантастика", "друг", "наставник", "игра", "история")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(nav: Nav, charId: String?) {
    val ctx = LocalContext.current
    val repo = ctx.app.repo
    val original = remember(charId) { charId?.let { repo.char(it) } }
    var c by remember(charId) { mutableStateOf(original ?: Character()) }
    var advanced by remember { mutableStateOf(original != null && (original.definition.isNotBlank() || original.relation.isNotBlank())) }
    var showCard by remember { mutableStateOf(false) }
    var showVoice by remember { mutableStateOf(false) }
    var showLink by remember { mutableStateOf(false) }
    var editBook by remember { mutableStateOf<String?>(null) }
    var tagInput by remember { mutableStateOf("") }
    var visMenu by remember { mutableStateOf(false) }
    var avatarMenu by remember { mutableStateOf(false) }
    var showGen by remember { mutableStateOf(false) }
    var emotionSlot by remember { mutableStateOf<String?>(null) }
    val pickEmotion = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val slot = emotionSlot
        if (uri != null && slot != null) repo.saveImage(uri)?.let { f -> c = c.copy(emotions = c.emotions + (slot to f)) }
        emotionSlot = null
    }
    val books by repo.books.collectAsStateWithLifecycle()

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) repo.saveAvatar(c.id, uri)?.let { c = c.copy(avatar = it) }
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(ctx, "Без разрешения на уведомления персонаж не сможет написать первым", Toast.LENGTH_LONG).show()
    }

    fun addTag(t: String) {
        val tag = t.trim().trim(',').lowercase()
        if (tag.isNotBlank() && tag !in c.tags && c.tags.size < 10) c = c.copy(tags = c.tags + tag)
        tagInput = ""
    }

    fun save() {
        if (c.name.isBlank()) { Toast.makeText(ctx, "Дай персонажу имя", Toast.LENGTH_SHORT).show(); return }
        if (tagInput.isNotBlank()) addTag(tagInput)
        val final = c.copy(greetings = c.greetings.map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf("") })
        repo.saveChar(final)
        if (original == null) {
            val chat = ctx.app.runner.newChat(final.id)
            nav.replace(Route.ChatR(chat.id))
        } else nav.back()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton({ nav.back() }) { Icon(Icons.Rounded.Close, "Закрыть") } },
                title = { Text(if (original == null) "Новый персонаж" else "Персонаж", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton({ showCard = true }) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Карточка")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Аватар
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Avatar(c, 180.dp, RoundedCornerShape(28.dp))
                    Box(Modifier.align(Alignment.BottomCenter).offset(y = 24.dp)) {
                        FilledIconButton({ avatarMenu = true }, Modifier.size(56.dp)) { Icon(Icons.Rounded.Edit, "Изменить аватар") }
                        DropdownMenu(avatarMenu, { avatarMenu = false }) {
                            DropdownMenuItem({ Text("Выбрать из галереи") }, {
                                avatarMenu = false
                                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                            if (c.avatar != null) DropdownMenuItem({ Text("Убрать аватар") }, { avatarMenu = false; c = c.copy(avatar = null) })
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }

            Surface(onClick = { showGen = true }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Accent)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Создать по описанию", fontWeight = FontWeight.Bold)
                        Text("Опиши идею одной фразой — ИИ заполнит поля", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LimitedField("Имя персонажа", c.name, 20, "Например: Шерлок Холмс") { c = c.copy(name = it) }
            LimitedField("Слоган", c.tagline, 50, "Например: Детектив, который раскрывает дела… за чаем") { c = c.copy(tagline = it) }
            LimitedField("Описание", c.description, 500, "Например: Бывшая королева пиратов, а теперь хозяйка уютной таверны у моря. Но неприятности всё равно её находят.", lines = 4) { c = c.copy(description = it) }

            // Приветствия
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                c.greetings.forEachIndexed { i, g ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1f)) {
                            LimitedField(if (i == 0) "Приветствие" else "Приветствие ${i + 1}", g, 4096, "Например: Долго же ты шёл. Я уже заждалась.", lines = 4) { v ->
                                c = c.copy(greetings = c.greetings.toMutableList().also { it[i] = v })
                            }
                        }
                        if (i > 0) IconButton({ c = c.copy(greetings = c.greetings.filterIndexed { j, _ -> j != i }) }, Modifier.padding(top = 28.dp)) {
                            Icon(Icons.Rounded.Delete, "Убрать приветствие")
                        }
                    }
                }
                if (c.greetings.size < 5) ActionTile("Добавить приветствие (${c.greetings.size}/5)", Icons.Rounded.Add) {
                    c = c.copy(greetings = c.greetings + "")
                }
                CardBox { SwitchRow("ИИ-приветствие в новых чатах", "Модель сама придумает первое сообщение", c.aiGreeting) { c = c.copy(aiGreeting = it) } }
                Text("Новый чат начинается с одного из приветствий — их можно листать, как варианты ответа.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Голос
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Голос", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                val v = c.voice
                if (v == null) ActionTile("Добавить голос персонажа", Icons.Rounded.Add, chevron = true) { c = c.copy(voice = Voice()); showVoice = true }
                else ActionTile("Голос: тон ${"%.1f".format(v.pitch)}, скорость ${"%.1f".format(v.rate)}" + if (v.autoRead) " · читает вслух" else "", Icons.Rounded.Edit, chevron = true) { showVoice = true }
            }

            // Лорбук
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Лорбук", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary) {
                        Text("Новое", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Text("Детали мира, которые персонаж приносит в чат, когда звучат ключевые слова.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                val linked = books.filter { it.id in c.lorebookIds }
                linked.forEach { b ->
                    CardBox { NavRow(b.name.ifBlank { "Без названия" }, "${b.entries.size} записей", { editBook = b.id }) {
                        IconButton({ c = c.copy(lorebookIds = c.lorebookIds - b.id) }) { Icon(Icons.Rounded.Close, "Отвязать") }
                    } }
                }
                ActionTile("Привязать лорбук", Icons.Rounded.Link, chevron = true) { showLink = true }
                ActionTile("Создать лорбук", Icons.Rounded.Edit, chevron = true) {
                    val b = Lorebook(name = if (c.name.isNotBlank()) "Мир: ${c.name}" else "Новый лорбук")
                    repo.saveBook(b); c = c.copy(lorebookIds = c.lorebookIds + b.id); editBook = b.id
                }
            }

            // Теги
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Теги", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(tagInput, { v -> if (v.endsWith(",")) addTag(v) else tagInput = v }, Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск тегов") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions { addTag(tagInput) })
                if (c.tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    c.tags.forEach { t -> InputChip(true, { c = c.copy(tags = c.tags - t) }, { Text(t) }, trailingIcon = { Icon(Icons.Rounded.Close, "Убрать", Modifier.size(16.dp)) }) }
                }
                val sugg = TAG_SUGGESTIONS.filter { it !in c.tags && (tagInput.isBlank() || it.startsWith(tagInput.trim().lowercase())) }.take(8)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sugg.forEach { t -> FilterChip(false, { addTag(t) }, { Text(t) }) }
                }
            }

            // Расширенное создание
            TextButton({ advanced = !advanced }) {
                Text("Расширенное создание", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.size(6.dp))
                Icon(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurface)
            }
            AnimatedVisibility(advanced) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    LimitedField("Определение (характер, история, примеры диалогов)", c.definition, 32000,
                        "{{char}}: Ой, только не начинай.\n{{user}}: Я же ничего не сказал!\n{{char}}: *прищуривается* Вот именно.", lines = 6) { c = c.copy(definition = it) }
                    Text("{{char}} — имя персонажа, {{user}} — твоё имя. Подставятся сами.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CardBox {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Черты характера", fontWeight = FontWeight.Bold)
                            TraitSlider("Мягкий", "Дерзкий", c.soft) { c = c.copy(soft = it) }
                            TraitSlider("Серьёзный", "Шутливый", c.playful) { c = c.copy(playful = it) }
                            TraitSlider("Сдержанный", "Эмоциональный", c.emotional) { c = c.copy(emotional = it) }
                            Prompt.traits(c).takeIf { it.isNotBlank() }?.let {
                                Text("Модель увидит: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    LimitedField("Кто ты для персонажа", c.relation, 1000, "Например: старый друг, вместе сбежали из гильдии бардов", lines = 2) { c = c.copy(relation = it) }
                    Text("Живость", fontWeight = FontWeight.Bold)
                    CardBox {
                        Column {
                            SwitchRow("Настроение и отношение", "Персонаж обижается, радуется и запоминает, как к тебе относится", c.moodEnabled) { c = c.copy(moodEnabled = it) }
                            if (c.moodEnabled) Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Аватарки-эмоции — меняются по настроению. Долгое нажатие убирает.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Prompt.EMOTIONS.keys.forEach { e ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(Modifier.size(56.dp).combinedClickableCompat(
                                                onClick = { emotionSlot = e; pickEmotion.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                                onLong = { c = c.copy(emotions = c.emotions - e) })) {
                                                if (c.emotions[e] != null) Avatar(c, 56.dp, emotion = e)
                                                else Surface(Modifier.size(56.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                                }
                                            }
                                            Text(e, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            SwitchRow("Дневник", "Раз в ~20 сообщений пишет личную запись о вас — читать в «Памяти»", c.diaryEnabled) { c = c.copy(diaryEnabled = it) }
                            SwitchRow("Распорядок дня", "Знает, который час, и живёт по своему расписанию", c.routineEnabled) { c = c.copy(routineEnabled = it) }
                            if (c.routineEnabled) Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                                OutlinedTextField(c.routine, { c = c.copy(routine = it.take(1000)) }, Modifier.fillMaxWidth(), minLines = 3, shape = RoundedCornerShape(14.dp),
                                    label = { Text("Распорядок") }, placeholder = { Text("7–9 завтрак и тренировка\n9–18 работает в книжной лавке\n19–23 свободна, гуляет\n23–7 спит") })
                            }
                        }
                    }
                    CardBox {
                        SwitchRow("Пишет первым", "Если ты молчишь больше 6 часов, может прислать сообщение (10:00–22:00)", c.writesFirst) { on ->
                            c = c.copy(writesFirst = on)
                            if (on && Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    BackgroundPicker(c.background) { c = c.copy(background = it) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Стиль ответов персонажа", fontWeight = FontWeight.Bold)
                        val options = listOf<Preset?>(null) + Preset.entries
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            options.forEachIndexed { i, p ->
                                SegmentedButton(c.preset == p, { c = c.copy(preset = p) }, SegmentedButtonDefaults.itemShape(i, options.size)) {
                                    Text(p?.title ?: "Общий", maxLines = 1)
                                }
                            }
                        }
                        Text("«Общий» — как во вкладке «Модель».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Видимость
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Видимость", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Box {
                    OutlinedButton({ visMenu = true }, shape = RoundedCornerShape(12.dp)) {
                        Icon(if (c.shareable) Icons.Rounded.Public else Icons.Rounded.Lock, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(if (c.shareable) "Можно делиться карточкой" else "Только у меня")
                        Spacer(Modifier.size(6.dp))
                        Icon(Icons.Rounded.UnfoldMore, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(visMenu, { visMenu = false }) {
                        DropdownMenuItem({ Text("Только у меня") }, { c = c.copy(shareable = false); visMenu = false }, leadingIcon = { Icon(Icons.Rounded.Lock, null) })
                        DropdownMenuItem({ Text("Можно делиться карточкой") }, { c = c.copy(shareable = true); visMenu = false }, leadingIcon = { Icon(Icons.Rounded.Public, null) })
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.End) {
                Button(::save, Modifier.height(56.dp), enabled = c.name.isNotBlank(), shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors()) {
                    Text(if (original == null) "Создать персонажа" else "Сохранить", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }

    if (showVoice) c.voice?.let { v ->
        VoiceDialog(v, { c = c.copy(voice = it) }, onRemove = { c = c.copy(voice = null); showVoice = false }) { showVoice = false }
    }
    if (showLink) AlertDialog({ showLink = false },
        title = { Text("Привязать лорбук") },
        text = {
            Column {
                if (books.isEmpty()) Text("Лорбуков пока нет — нажми «Создать лорбук».")
                books.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(b.id in c.lorebookIds, { on -> c = c.copy(lorebookIds = if (on) c.lorebookIds + b.id else c.lorebookIds - b.id) })
                        Text(b.name.ifBlank { "Без названия" })
                    }
                }
            }
        },
        confirmButton = { TextButton({ showLink = false }) { Text("Готово") } })
    editBook?.let { id ->
        androidx.compose.ui.window.Dialog({ editBook = null }, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { LoreEditor(id, onClose = { editBook = null }) }
        }
    }
    if (showCard) CardPreview(c) { showCard = false }
    if (showGen) GenerateDialog(c, onResult = { gen -> c = gen; advanced = true; showGen = false }) { showGen = false }
}

@Composable
private fun GenerateDialog(base: Character, onResult: (Character) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var idea by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val examples = listOf("циничная ведьма-аптекарь из портового города", "одногруппник-программист, который вечно не спит",
        "капитан звездолёта на пенсии", "детектив 1920-х с тёмным прошлым")
    AlertDialog({ if (!busy) onClose() },
        title = { Text("Создать по описанию") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(idea, { idea = it.take(300) }, Modifier.fillMaxWidth(), minLines = 2, enabled = !busy,
                    placeholder = { Text("Кто это? Например: застенчивая библиотекарша-вампир") })
                if (!busy) examples.forEach { e ->
                    Text("• $e", Modifier.clickable { idea = e }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp)); Text("Придумываю… на телефоне это может занять минуту")
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text("Заполненные поля заменятся, аватарка и голос останутся.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton({
                busy = true; err = null
                scope.launch {
                    runCatching { ctx.app.runner.generateCharacter(idea, base) }
                        .onSuccess { onResult(it) }
                        .onFailure { err = it.message ?: "Не получилось"; ErrorLog.add("gen-char", it) }
                    busy = false
                }
            }, enabled = idea.isNotBlank() && !busy) { Text("Создать") }
        },
        dismissButton = { TextButton(onClose, enabled = !busy) { Text("Отмена") } })
}

@Composable
fun LimitedField(label: String, value: String, max: Int, placeholder: String, lines: Int = 1, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value, { if (it.length <= max) onChange(it) else onChange(it.take(max)) }, Modifier.fillMaxWidth().then(if (lines > 1) Modifier.heightIn(min = (lines * 26 + 28).dp) else Modifier),
            placeholder = { Text(placeholder) }, singleLine = lines == 1, minLines = lines, shape = RoundedCornerShape(16.dp),
        )
        Text("${value.length}/$max", Modifier.align(Alignment.End), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ActionTile(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, chevron: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, maxLines = 1)
            if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TraitSlider(left: String, right: String, v: Float, onChange: (Float) -> Unit) {
    Column {
        Row { Text(left, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(right, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Slider(v, onChange)
    }
}

@Composable
private fun VoiceDialog(v: Voice, onChange: (Voice) -> Unit, onRemove: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var cur by remember { mutableStateOf(v) }
    AlertDialog(onClose,
        title = { Text("Голос персонажа") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Тон: ${"%.1f".format(cur.pitch)}")
                Slider(cur.pitch, { cur = cur.copy(pitch = it); onChange(cur) }, valueRange = 0.5f..2f)
                Text("Скорость: ${"%.1f".format(cur.rate)}")
                Slider(cur.rate, { cur = cur.copy(rate = it); onChange(cur) }, valueRange = 0.5f..2f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(cur.autoRead, { cur = cur.copy(autoRead = it); onChange(cur) })
                    Text("Читать ответы вслух автоматически")
                }
                OutlinedButton({ ctx.app.voice.speak("Привет! Так звучит мой голос.", cur) }) { Text("Прослушать") }
                Text("Используется синтез речи телефона.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClose) { Text("Готово") } },
        dismissButton = { TextButton(onRemove) { Text("Убрать голос") } })
}

@Composable
private fun CardPreview(c: Character, onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(onClose,
        title = null,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Avatar(c, 120.dp, RoundedCornerShape(24.dp))
                Text(c.name.ifBlank { "Без имени" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                if (c.tagline.isNotBlank()) Text(c.tagline, color = MaterialTheme.colorScheme.primary)
                if (c.description.isNotBlank()) Text(c.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.tags.isNotEmpty()) Text(c.tags.joinToString(" · ") { "#$it" }, style = MaterialTheme.typography.bodySmall)
                c.greetings.firstOrNull { it.isNotBlank() }?.let {
                    Surface(shape = RoundedCornerShape(16.dp), color = BubbleChar) { Text(rpText(it, Accent), Modifier.padding(12.dp)) }
                }
            }
        },
        confirmButton = {
            if (c.shareable && c.name.isNotBlank()) TextButton({ shareCard(ctx, c) }) { Text("Экспорт JSON") }
            else TextButton(onClose) { Text("Закрыть") }
        },
        dismissButton = { if (c.shareable) TextButton(onClose) { Text("Закрыть") } else Text("Чтобы делиться, выбери видимость «Можно делиться карточкой».", style = MaterialTheme.typography.bodySmall) })
}

@Suppress("unused") private val unusedShape = CircleShape


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLong: () -> Unit) =
    this.then(Modifier.combinedClickable(onClick = onClick, onLongClick = onLong))
