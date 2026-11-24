package com.persona.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ======================== Проверка телефона ========================

object PhoneCheck {
    fun ramGb(ctx: Context): Double {
        val mi = ActivityManager.MemoryInfo(); ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        return mi.totalMem / 1_000_000_000.0
    }

    data class Advice(val title: String, val text: String, val model: ModelInfo?)

    fun advice(ctx: Context): Advice {
        val gb = ramGb(ctx)
        val ram = String.format(java.util.Locale.US, "%.0f", gb)
        val e2b = ModelCatalog.all.first { it.file.contains("E2B") }
        val lfm = ModelCatalog.all.first { it.file.startsWith("LFM2") }
        return when {
            gb >= 11 -> Advice("Мощный телефон · $ram ГБ памяти", "Потянет любую модель. Советую Gemma 4 E2B на каждый день, а E4B — когда нужно качество.", e2b)
            gb >= 7 -> Advice("Хороший телефон · $ram ГБ памяти", "Gemma 4 E2B будет работать нормально. E4B лучше не брать — может вылетать.", e2b)
            gb >= 5 -> Advice("Средний телефон · $ram ГБ памяти", "Подойдёт лёгкая LFM2 2.6B. Для лучшего качества подключи API — это бесплатно через OpenRouter.", lfm)
            else -> Advice("Слабый телефон · $ram ГБ памяти", "Модели на телефоне будут тормозить. Лучше подключить API — например, бесплатно через OpenRouter.", null)
        }
    }
}

@Composable
fun PhoneCheckDialog(nav: Nav, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val a = remember { PhoneCheck.advice(ctx) }
    AlertDialog(onClose,
        title = { Text("Добро пожаловать в Персону") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Чтобы персонажи отвечали, нужна модель: на телефоне (офлайн) или через API.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(a.title, fontWeight = FontWeight.Bold)
                        Text(a.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                a.model?.let { m -> TextButton({ Downloads.start(ctx, m); onClose(); nav.go(Route.SettingsPage("local")) }) { Text("Скачать ${m.title} (${bytesLabel(m.sizeBytes)})") } }
                TextButton({ onClose(); nav.go(Route.SettingsPage("api")) }) { Text("Подключить API") }
                TextButton(onClose) { Text("Позже") }
            }
        })
}

// ======================== Шаблоны персонажей ========================

@Composable
fun TemplatesDialog(onPick: (Character) -> Unit, onClose: () -> Unit) {
    AlertDialog(onClose,
        title = { Text("Шаблоны персонажей") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Templates.all) { t ->
                    Surface(onClick = { onPick(t.c.copy(id = newId(), created = System.currentTimeMillis())) }, shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(t.c, 44.dp)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.genre.uppercase(), style = MaterialTheme.typography.labelSmall, color = Amber, fontWeight = FontWeight.Bold)
                                Text(t.c.name, fontWeight = FontWeight.Bold)
                                Text(t.c.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Закрыть") } })
}

// ======================== Поиск ========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: Nav) {
    val repo = LocalContext.current.app.repo
    val chats by repo.chats.collectAsStateWithLifecycle()
    val chars by repo.chars.collectAsStateWithLifecycle()
    val scenes by repo.scenes.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    data class Hit(val chat: Chat, val msg: Msg?, val title: String, val c: Character?)
    val byId = chars.associateBy { it.id }
    val hits = remember(q, chats) {
        val t = q.trim()
        if (t.length < 2) emptyList() else buildList {
            chars.filter { it.name.contains(t, true) || it.tagline.contains(t, true) }.forEach { c ->
                chats.filter { it.charId == c.id && it.sceneId == null }.maxByOrNull { it.updated }?.let { add(Hit(it, null, c.name, c)) }
            }
            chats.filterNot { it.incognito }.sortedByDescending { it.updated }.forEach { ch ->
                val title = ch.sceneId?.let { id -> scenes.firstOrNull { it.id == id }?.name } ?: byId[ch.charId]?.name ?: "Чат"
                ch.messages.asReversed().filter { it.text.contains(t, true) }.take(20).forEach { m ->
                    add(Hit(ch, m, title, if (ch.sceneId == null) byId[ch.charId] else m.speaker?.let { byId[it] }))
                }
            }
        }.take(200)
    }
    Scaffold(topBar = {
        TopAppBar(navigationIcon = { IconButton({ nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
            title = {
                TextField(q, { q = it }, Modifier.fillMaxWidth().focusRequester(focus), placeholder = { Text("Поиск по чатам и персонажам") }, singleLine = true,
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Rounded.Close, "Очистить") } })
            })
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (q.trim().length >= 2 && hits.isEmpty()) item { Text("Ничего не нашлось", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(hits) { h ->
                Row(Modifier.fillMaxWidth().clickable { nav.go(Route.ChatR(h.chat.id, h.msg?.id)) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                    Avatar(h.c ?: Character(name = h.title), 40.dp)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(h.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(timeLabel(h.msg?.time ?: h.chat.updated), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val text = h.msg?.text ?: h.c?.tagline.orEmpty()
                        val i = text.indexOf(q.trim(), ignoreCase = true)
                        val start = (i - 40).coerceAtLeast(0)
                        val snippet = (if (start > 0) "…" else "") + text.substring(start).replace('\n', ' ')
                        Text(buildAnnotatedString {
                            val j = snippet.indexOf(q.trim(), ignoreCase = true)
                            if (j < 0) append(snippet) else {
                                append(snippet.substring(0, j))
                                withStyle(SpanStyle(color = Accent, fontWeight = FontWeight.Bold)) { append(snippet.substring(j, j + q.trim().length)) }
                                append(snippet.substring(j + q.trim().length))
                            }
                        }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ======================== Режим книги ========================

object BookExport {
    data class Para(val who: String?, val text: String, val narration: Boolean)

    fun paragraphs(ctx: Context, chat: Chat): List<Para> {
        val repo = ctx.app.repo
        val s = repo.settings.value.forChat(chat)
        val byId = repo.chars.value.associateBy { it.id }
        return chat.messages.filter { it.kind != PICTURE && it.text.isNotBlank() && it.speaker != DICE }.map { m ->
            val who = when {
                m.speaker == NARRATOR -> null
                m.fromUser -> s.user
                m.speaker != null -> byId[m.speaker]?.name
                else -> byId[chat.charId]?.name
            }
            Para(who, m.text.trim(), m.speaker == NARRATOR)
        }
    }

    fun title(ctx: Context, chat: Chat): String {
        val repo = ctx.app.repo
        return repo.scene(chat.sceneId)?.name ?: repo.char(chat.charId)?.name?.let { "История с $it" } ?: "История"
    }

    fun text(ctx: Context, chat: Chat): String = buildString {
        append(title(ctx, chat).uppercase()).append("\n\n")
        paragraphs(ctx, chat).forEach { p ->
            if (p.who != null) append(p.who).append(":\n")
            append(p.text.replace("*", "")).append("\n\n")
        }
    }

    /** Простая вёрстка в PDF формата A5 с переносами строк. */
    fun pdf(ctx: Context, chat: Chat, out: java.io.OutputStream) {
        val doc = PdfDocument()
        val w = 420; val h = 595; val margin = 40
        val body = TextPaint().apply { isAntiAlias = true; textSize = 11f; typeface = Typeface.SERIF; color = android.graphics.Color.BLACK }
        val head = TextPaint(body).apply { textSize = 18f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
        val blocks = mutableListOf<Pair<CharSequence, TextPaint>>(title(ctx, chat) to head)
        paragraphs(ctx, chat).forEach { p ->
            val sb = SpannableStringBuilder()
            if (p.who != null) { sb.append(p.who + ": "); sb.setSpan(StyleSpan(Typeface.BOLD), 0, p.who.length + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            // *действия* — курсивом
            var i = 0; val t = p.text
            while (i < t.length) {
                val a = t.indexOf('*', i); if (a < 0) { sb.append(t.substring(i)); break }
                val b = t.indexOf('*', a + 1); if (b < 0) { sb.append(t.substring(i)); break }
                sb.append(t.substring(i, a)); val st = sb.length; sb.append(t.substring(a + 1, b))
                sb.setSpan(StyleSpan(Typeface.ITALIC), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i = b + 1
            }
            if (p.narration) sb.setSpan(StyleSpan(Typeface.ITALIC), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            blocks += sb to body
        }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        var y = margin.toFloat()
        for ((text, paint) in blocks) {
            var layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, w - margin * 2).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()
            var from = 0
            while (from < layout.lineCount) {
                val room = h - margin - y
                var to = from
                while (to < layout.lineCount && layout.getLineBottom(to) - layout.getLineTop(from) <= room) to++
                if (to == from) {
                    doc.finishPage(page); pageNo++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create()); y = margin.toFloat(); continue
                }
                val c = page.canvas
                c.save(); c.translate(margin.toFloat(), y - layout.getLineTop(from)); c.clipRect(0f, layout.getLineTop(from).toFloat(), (w - margin * 2).toFloat(), layout.getLineTop(to).toFloat())
                layout.draw(c); c.restore()
                y += layout.getLineTop(to) - layout.getLineTop(from)
                from = to
            }
            y += 10f
        }
        doc.finishPage(page)
        doc.writeTo(out); doc.close()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(nav: Nav, chatId: String) {
    val ctx = LocalContext.current
    val chats by ctx.app.repo.chats.collectAsStateWithLifecycle()
    val chat = chats.firstOrNull { it.id == chatId }
    if (chat == null) { LaunchedEffect(Unit) { nav.back() }; return }
    val paras = remember(chat.messages.size) { BookExport.paragraphs(ctx, chat) }
    val title = remember { BookExport.title(ctx, chat) }
    val scope = rememberCoroutineScope()
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { BookExport.pdf(ctx, chat, it) } }.onFailure { ErrorLog.add("pdf", it) }.isSuccess }
            Toast.makeText(ctx, if (ok) "PDF сохранён" else "Не удалось сохранить PDF", Toast.LENGTH_SHORT).show()
        }
    }
    Scaffold(containerColor = Color(0xFFF4EEE3), topBar = {
        TopAppBar(navigationIcon = { IconButton({ nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = Color(0xFF2B2520)) } },
            title = { Text(title, color = Color(0xFF2B2520), fontFamily = FontFamily.Serif, maxLines = 1) },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF4EEE3)),
            actions = {
                IconButton({
                    val f = File(File(ctx.cacheDir, "cards").apply { mkdirs() }, title.replace(Regex("[^\\p{L}\\p{N}_-]"), "_") + ".txt")
                    f.writeText(BookExport.text(ctx, chat))
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Текст истории"))
                }) { Icon(Icons.Rounded.IosShare, "Поделиться текстом", tint = Color(0xFF2B2520)) }
                IconButton({ savePdf.launch("$title.pdf") }) { Icon(Icons.Rounded.PictureAsPdf, "Сохранить PDF", tint = Color(0xFF2B2520)) }
            })
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text(title, fontFamily = FontFamily.Serif, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2520)) }
            items(paras) { p ->
                Text(buildAnnotatedString {
                    if (p.who != null) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF7A3B4E))) { append(p.who); append(". ") }
                    append(rpText(p.text, Color(0xFF6B5E52)))
                }, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 26.sp, color = Color(0xFF2B2520),
                    fontStyle = if (p.narration) androidx.compose.ui.text.font.FontStyle.Italic else null)
            }
        }
    }
}

// ======================== Сравнение моделей ========================

@Composable
fun CompareDialog(chatId: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.app
    val repo = app.repo
    val s0 by repo.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val localOk = app.engines.local.hasModel(s0)
    val apiOk = app.engines.api.ready(s0)
    var modelA by remember { mutableStateOf(if (localOk) "local" else s0.apiModel) }
    var modelB by remember { mutableStateOf(if (localOk && apiOk) s0.apiModel else "") }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var errA by remember { mutableStateOf<String?>(null) }
    var errB by remember { mutableStateOf<String?>(null) }

    fun label(m: String) = if (m == "local") ModelCatalog.title(s0.activeModel) + " (телефон)" else m.substringAfterLast('/')

    fun run() {
        val ch = repo.chat(chatId) ?: return
        val c = repo.char(ch.charId) ?: return
        val msgs = ch.messages.dropLastWhile { !it.fromUser }
        if (msgs.isEmpty()) { errA = "Сначала напиши персонажу что-нибудь"; return }
        val st = repo.settings.value.forChat(ch)
        running = true; a = ""; b = ""; errA = null; errB = null
        scope.launch {
            fun flowFor(m: String) = if (m == "local") app.engines.local.generate(st, Prompt.reply(c, ch, st, repo.books.value, msgs, true))
                else app.engines.api.generate(st.copy(apiModel = m), Prompt.reply(c, ch, st, repo.books.value, msgs, false))
            val ja = async { runCatching { flowFor(modelA).collect { a += it } }.onFailure { errA = it.message } }
            val jb = async { runCatching { flowFor(modelB).collect { b += it } }.onFailure { errB = it.message } }
            ja.await(); jb.await(); running = false
        }
    }
    fun take(text: String, m: String) {
        val clean = Prompt.parseMood(text).first.trim()
        if (clean.isBlank()) return
        repo.updateChat(chatId) { ch ->
            val last = ch.messages.lastOrNull()
            if (last != null && !last.fromUser) ch.copy(messages = ch.messages.dropLast(1) + last.copy(variants = last.variants + clean, sel = last.variants.size, info = label(m)))
            else ch.copy(messages = ch.messages + Msg(fromUser = false, variants = listOf(clean), info = label(m)))
        }
        onClose()
    }

    AlertDialog({ if (!running) onClose() },
        title = { Text("Сравнить модели") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Один и тот же ответ на твоё последнее сообщение от двух моделей. Лучший можно взять в чат.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!localOk && !apiOk) Text("Нужна модель на телефоне или API-ключ.", color = MaterialTheme.colorScheme.error)
                listOf(Triple("A", modelA, a) to errA, Triple("B", modelB, b) to errB).forEach { (t, err) ->
                    val (slot, m, text) = t
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (m == "local") Text("$slot · ${label(m)}", fontWeight = FontWeight.Bold)
                            else OutlinedTextField(m, { v -> if (slot == "A") modelA = v.trim() else modelB = v.trim() }, Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text("$slot · модель API") }, placeholder = { Text("например, ${s0.apiModel.ifBlank { "openrouter/auto" }}") }, enabled = !running)
                            if (slot == "A" && localOk && apiOk) TextButton({ modelA = if (modelA == "local") s0.apiModel else "local" }, enabled = !running) {
                                Text(if (modelA == "local") "Взять API вместо телефона" else "Взять модель телефона")
                            }
                            when {
                                err != null -> Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                text.isNotBlank() -> Text(rpText(Prompt.hideTags(text), Accent), style = MaterialTheme.typography.bodyMedium)
                                running -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                            if (!running && text.isNotBlank()) Button({ take(text, m) }) { Text("Взять этот ответ") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(::run, enabled = !running && modelA.isNotBlank() && modelB.isNotBlank() && (modelA != modelB)) { Text(if (a.isEmpty() && b.isEmpty()) "Сравнить" else "Ещё раз") } },
        dismissButton = { TextButton(onClose, enabled = !running) { Text("Закрыть") } })
}

// ======================== Фон чата ========================

@Composable
fun ChatBackground(name: String?) {
    if (name == null) return
    Box(Modifier.fillMaxSize()) {
        val ctx = LocalContext.current
        val bmp by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, name) {
            value = withContext(Dispatchers.IO) { ctx.app.repo.imageFile(name)?.let { android.graphics.BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
        }
        bmp?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Color(0xCC14121A)))
    }
}

@Composable
fun BackgroundPicker(current: String?, onChange: (String?) -> Unit) {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) ctx.app.repo.saveImage(uri)?.let(onChange) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Фон чата", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (current != null) MsgImage(current, Modifier.size(72.dp))
            TextButton({ pick.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (current == null) "Выбрать картинку" else "Сменить") }
            if (current != null) TextButton({ onChange(null) }) { Text("Убрать") }
        }
    }
}
