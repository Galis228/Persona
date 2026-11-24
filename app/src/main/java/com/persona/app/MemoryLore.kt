package com.persona.app

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(nav: Nav, chatId: String) {
    val ctx = LocalContext.current
    val repo = ctx.app.repo
    val chats by repo.chats.collectAsStateWithLifecycle()
    val s by repo.settings.collectAsStateWithLifecycle()
    val chat = chats.firstOrNull { it.id == chatId }
    val c = chat?.let { repo.char(it.charId) }
    if (chat == null || c == null) { LaunchedEffect(Unit) { nav.back() }; return }
    var summary by remember(chatId) { mutableStateOf(chat.summary) }
    LaunchedEffect(chat.summary) { summary = chat.summary }
    var editFact by remember { mutableStateOf<Fact?>(null) }
    var adding by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var writing by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton({ if (summary != chat.summary) repo.updateChat(chatId) { it.copy(summary = summary) }; nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
            title = { Text("Память: ${c.name}", fontWeight = FontWeight.Bold) },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (c.moodEnabled) item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Настроение", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(chat.mood.ifBlank { "спокойное" }.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            }
                            TextButton({ repo.updateChat(chatId) { it.copy(mood = "", affinity = 50) } }) { Text("Сбросить") }
                        }
                        Text("Отношение к тебе: ${Prompt.affinityLabel(chat.affinity)} · ${chat.affinity}/100", style = MaterialTheme.typography.bodyMedium)
                        androidx.compose.material3.LinearProgressIndicator({ chat.affinity / 100f }, Modifier.fillMaxWidth(), color = Amber)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(tab == 0, { tab = 0 }, { Text("Память") })
                    androidx.compose.material3.FilterChip(tab == 2, { tab = 2 }, { Text("Моменты · ${chat.moments.size}") })
                    if (c.diaryEnabled || chat.diary.isNotEmpty()) androidx.compose.material3.FilterChip(tab == 1, { tab = 1 }, { Text("Дневник · ${chat.diary.size}") })
                }
            }
            if (tab == 2) {
                item {
                    Text("Важные события ваших отношений. Добавляются сами при сжатии памяти или вручную — звёздочкой под сообщением.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (chat.moments.isEmpty()) item { Text("Моментов пока нет.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(chat.moments.reversed(), key = { it.id }) { mo ->
                    Row(verticalAlignment = Alignment.Top) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.foundation.layout.Box(Modifier.size(12.dp).padding(top = 4.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Amber))
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(java.text.SimpleDateFormat("d MMMM", java.util.Locale("ru")).format(java.util.Date(mo.created)), style = MaterialTheme.typography.labelMedium, color = Amber)
                            Text(mo.text, style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton({ repo.updateChat(chatId) { ch -> ch.copy(moments = ch.moments.filterNot { it.id == mo.id }) } }) { Icon(Icons.Rounded.Close, "Удалить", Modifier.size(18.dp)) }
                    }
                }
            } else if (tab == 1) {
                item {
                    Text("Личные записи ${c.name}. Персонаж пишет их сам примерно раз в 20 сообщений — то, что не сказал(а) вслух.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(6.dp))
                    OutlinedButton({
                        writing = true
                        ctx.app.runner.diaryNow(chatId) { err -> writing = false; if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_LONG).show() }
                    }, enabled = !writing) {
                        if (writing) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.size(8.dp)) }
                        Text("Написать запись сейчас")
                    }
                }
                if (chat.diary.isEmpty()) item { Text("Записей пока нет.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(chat.diary.reversed(), key = { it.id }) { d ->
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(java.text.SimpleDateFormat("d MMMM, HH:mm", java.util.Locale("ru")).format(java.util.Date(d.created)), Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelMedium, color = Amber, fontWeight = FontWeight.Bold)
                                IconButton({ repo.updateChat(chatId) { ch -> ch.copy(diary = ch.diary.filterNot { it.id == d.id }) } }) { Icon(Icons.Rounded.Close, "Удалить запись", Modifier.size(18.dp)) }
                            }
                            Text(rpText(d.text, Accent), style = MaterialTheme.typography.bodyLarge, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        }
                    }
                }
            } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Факты о тебе", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    TextButton({ adding = true }) { Icon(Icons.Rounded.Add, null); Text("Добавить") }
                }
                if (chat.facts.isEmpty()) Text("Пока пусто. Факты появляются сами, когда переписка становится длинной, или добавь их вручную.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            items(chat.facts, key = { it.id }) { f ->
                CardBox {
                    Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(f.text, Modifier.weight(1f).padding(vertical = 12.dp))
                        Text(timeLabel(f.created), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton({ editFact = f }) { Icon(Icons.Rounded.Edit, "Изменить", Modifier.size(20.dp)) }
                        IconButton({ repo.updateChat(chatId) { ch -> ch.copy(facts = ch.facts.filterNot { it.id == f.id }) } }) { Icon(Icons.Rounded.Close, "Удалить") }
                    }
                }
            }
            item {
                Spacer(Modifier.size(8.dp))
                Text("Краткое содержание истории", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }
            item {
                OutlinedTextField(summary, { summary = it }, Modifier.fillMaxWidth(), minLines = 4,
                    placeholder = { Text("Появится, когда сообщений станет больше ${s.summarizeEvery}.") }, shape = RoundedCornerShape(16.dp))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (summary != chat.summary) Button({ repo.updateChat(chatId) { it.copy(summary = summary) } }) { Text("Сохранить") }
                    OutlinedButton({
                        working = true
                        ctx.app.runner.summarizeNow(chatId) { err ->
                            working = false
                            Toast.makeText(ctx, err ?: "Память обновлена", Toast.LENGTH_SHORT).show()
                        }
                    }, enabled = !working) {
                        if (working) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.size(8.dp)) }
                        Text("Обновить сейчас")
                    }
                }
                Text("Сжато сообщений: ${chat.summarizedUpTo} из ${chat.messages.size}. Старые сообщения не удаляются — модель просто видит вместо них краткое содержание.",
                    Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            }
        }
    }

    if (adding || editFact != null) {
        var t by remember(editFact) { mutableStateOf(editFact?.text ?: "") }
        AlertDialog({ adding = false; editFact = null },
            title = { Text(if (editFact != null) "Изменить факт" else "Новый факт") },
            text = { OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth(), placeholder = { Text("Например: не любит, когда его зовут «малыш»") }) },
            confirmButton = {
                TextButton({
                    val e = editFact
                    if (t.isNotBlank()) repo.updateChat(chatId) { ch ->
                        if (e != null) ch.copy(facts = ch.facts.map { if (it.id == e.id) it.copy(text = t.trim()) else it })
                        else ch.copy(facts = ch.facts + Fact(text = t.trim()))
                    }
                    adding = false; editFact = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton({ adding = false; editFact = null }) { Text("Отмена") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoreScreen(nav: Nav, bookId: String) = LoreEditor(bookId, onClose = { nav.back() })

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoreEditor(bookId: String, onClose: () -> Unit) {
    val repo = LocalContext.current.app.repo
    val books by repo.books.collectAsStateWithLifecycle()
    val book = books.firstOrNull { it.id == bookId }
    if (book == null) { LaunchedEffect(Unit) { onClose() }; return }
    var confirm by remember { mutableStateOf(false) }
    fun upd(f: (Lorebook) -> Lorebook) = repo.saveBook(f(book))

    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton(onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
            title = { Text("Лорбук", fontWeight = FontWeight.Bold) },
            actions = { IconButton({ confirm = true }) { Icon(Icons.Rounded.Delete, "Удалить лорбук") } },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(book.name, { v -> upd { it.copy(name = v) } }, Modifier.fillMaxWidth(), label = { Text("Название") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            }
            items(book.entries, key = { it.id }) { e ->
                fun updE(f: (LoreEntry) -> LoreEntry) = upd { b -> b.copy(entries = b.entries.map { if (it.id == e.id) f(it) else it }) }
                CardBox {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var keys by remember(e.id) { mutableStateOf(e.keys.joinToString(", ")) }
                        OutlinedTextField(keys, { v -> keys = v; updE { it.copy(keys = v.split(',').map { k -> k.trim() }.filter { k -> k.isNotBlank() }) } },
                            Modifier.fillMaxWidth(), label = { Text("Ключевые слова через запятую") }, singleLine = true, placeholder = { Text("гильдия, барды") })
                        OutlinedTextField(e.content, { v -> updE { it.copy(content = v) } }, Modifier.fillMaxWidth(), minLines = 3,
                            label = { Text("Что знает персонаж") }, placeholder = { Text("Гильдия бардов — закрытое сообщество музыкантов в столице…") })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(e.enabled, { v -> updE { it.copy(enabled = v) } })
                            Spacer(Modifier.size(8.dp))
                            Text(if (e.enabled) "Включена" else "Выключена", Modifier.weight(1f))
                            IconButton({ upd { b -> b.copy(entries = b.entries.filterNot { it.id == e.id }) } }) { Icon(Icons.Rounded.Delete, "Удалить запись") }
                        }
                    }
                }
            }
            item {
                OutlinedButton({ upd { it.copy(entries = it.entries + LoreEntry()) } }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Text("Добавить запись") }
            }
        }
    }
    if (confirm) AlertDialog({ confirm = false },
        title = { Text("Удалить лорбук?") },
        confirmButton = { TextButton({
            repo.deleteBook(bookId)
            repo.chars.value.filter { bookId in it.lorebookIds }.forEach { repo.saveChar(it.copy(lorebookIds = it.lorebookIds - bookId)) }
            confirm = false
        }) { Text("Удалить") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Отмена") } })
}
