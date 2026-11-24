package com.persona.app

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.provider.Settings as SysSettings

/** Состояние загрузок моделей — общее для всего приложения. */
object DownloadWatch {
    val live = mutableStateMapOf<String, Downloads.State>()
    var tick by mutableStateOf(0)

    suspend fun loop(ctx: android.content.Context) {
        val repo = ctx.app.repo
        while (true) {
            val d = repo.settings.value.downloads
            if (d.isEmpty()) { live.clear(); delay(1500); continue }
            for ((file, id) in d) {
                val st = Downloads.state(ctx, id)
                when {
                    st == null -> { live.remove(file); Downloads.cancel(ctx, file) }
                    st.status == DownloadManager.STATUS_SUCCESSFUL -> { live.remove(file); Downloads.finish(ctx, file); tick++ }
                    st.status == DownloadManager.STATUS_FAILED -> {
                        live.remove(file); Downloads.cancel(ctx, file)
                        Toast.makeText(ctx, "Загрузка ${ModelCatalog.title(file)} не удалась (код ${st.reason}). Попробуй ещё раз.", Toast.LENGTH_LONG).show()
                    }
                    else -> live[file] = st
                }
            }
            delay(800)
        }
    }
}

// ======================== Главная страница настроек ========================

@Composable
fun SettingsHome(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.app
    val repo = app.repo
    val s by repo.settings.collectAsStateWithLifecycle()
    var showLog by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val e = app.engines
    val mode = e.pick(s)
    val ready = if (mode == Mode.LOCAL) e.local.hasModel(s) else e.api.ready(s)
    val tick = DownloadWatch.tick
    val installed = remember(tick, s.activeModel, s.downloads) { e.local.models().filter { it.name !in s.downloads }.size }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Карточка модели
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(if (mode == Mode.LOCAL) Icons.Rounded.PhoneAndroid else Icons.Rounded.Cloud, null, Modifier.size(30.dp), tint = Accent)
                        }
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (ready) e.label(s, mode).substringBefore(" · ") else "Модель не настроена",
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(if (ready) Mint else Amber))
                                Spacer(Modifier.size(6.dp))
                                Text(when {
                                    !ready -> "Скачай модель или подключи API"
                                    mode == Mode.LOCAL -> "На телефоне · ${if (s.useGpu) "GPU" else "CPU"} · ${s.contextTokens} токенов"
                                    else -> "Через API · ${s.provider.title}"
                                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                    val modes = listOf(Mode.AUTO to "Авто", Mode.LOCAL to "Телефон", Mode.API to "API")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, (m, t) ->
                            SegmentedButton(s.mode == m, { repo.settings { it.copy(mode = m) } }, SegmentedButtonDefaults.itemShape(i, modes.size)) { Text(t, maxLines = 1) }
                        }
                    }
                    Text(when (s.mode) {
                        Mode.AUTO -> "Отвечает модель на телефоне, а если её нет — API."
                        Mode.LOCAL -> "Всё работает офлайн, переписка не покидает телефон."
                        Mode.API -> "Умнее и быстрее, нужен интернет и ключ."
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Профиль
        item {
            val p = s.persona(null)
            SettingsGroup("Профиль и вид") {
                SettingsRow(Icons.Rounded.Palette, Color(0xFF7A3B6E), "Оформление",
                    "${s.look.theme.title} · ${if (s.look.accent == -1) "цвета обоев" else ACCENTS.getOrElse(s.look.accent) { ACCENTS[0] }.title.lowercase()} · ${s.look.bubbles.title.lowercase()}") {
                    nav.go(Route.SettingsPage("look"))
                }
                RowDivider()
                SettingsRow(Icons.Rounded.Person, Color(0xFF4A3D78), "Мои персоны",
                    (p?.name ?: s.user) + " · " + when (s.personas.size) { 0 -> "персон пока нет"; 1 -> "1 персона"; in 2..4 -> "${s.personas.size} персоны"; else -> "${s.personas.size} персон" }) {
                    nav.go(Route.SettingsPage("personas"))
                }
            }
        }
        // Модель
        item {
            SettingsGroup("Модель") {
                SettingsRow(Icons.Rounded.PhoneAndroid, Color(0xFF2F6B5A), "На телефоне",
                    if (e.local.hasModel(s)) "${ModelCatalog.title(s.activeModel)} · установлено: $installed" + if (DownloadWatch.live.isNotEmpty()) " · идёт загрузка" else ""
                    else if (DownloadWatch.live.isNotEmpty()) "Идёт загрузка…" else "Модель не скачана") { nav.go(Route.SettingsPage("local")) }
                RowDivider()
                SettingsRow(Icons.Rounded.Key, Color(0xFF3B4E7A), "API-ключ",
                    if (e.api.ready(s)) "${s.provider.title} · ${s.apiModel.substringAfterLast('/')}" else "Не подключён · вход через OpenRouter в один тап") { nav.go(Route.SettingsPage("api")) }
            }
        }
        // Ролплей
        item {
            SettingsGroup("Ролплей") {
                SettingsRow(Icons.Rounded.Tune, Color(0xFF7A3B4E), "Стиль ответов",
                    "${Preset.entries.firstOrNull { it.temp == s.temperature && it.rep == s.repPenalty }?.title ?: "Свой"} · ${s.length.title}" +
                        if (s.noImpersonation) " · не пишет за тебя" else "") { nav.go(Route.SettingsPage("style")) }
                RowDivider()
                SettingsRow(Icons.Rounded.Psychology, Color(0xFF7A5A2F), "Память",
                    if (s.summarize) "Сжатие каждые ${s.summarizeEvery} сообщений · факты о тебе" else "Выключена") { nav.go(Route.SettingsPage("memory")) }
                RowDivider()
                SettingsRow(Icons.Rounded.Code, Color(0xFF2F5F7A), "Системный промпт",
                    (if (s.systemTemplate == DEFAULT_TEMPLATE) "Стандартный" else "Свой") + " · скрытая инструкция для модели") { nav.go(Route.SettingsPage("prompt")) }
            }
        }
        // Прочее
        item {
            SettingsGroup("Данные и защита") {
                val act = ctx as? MainActivity
                Row(Modifier.fillMaxWidth().clickable {
                    if (s.lockEnabled) act?.authenticate("Выключить замок") { repo.settings { it.copy(lockEnabled = false) } }
                    else if (act?.canLock() == true) act.authenticate("Включить замок") { repo.settings { it.copy(lockEnabled = true) } }
                    else Toast.makeText(ctx, "Сначала настрой отпечаток или код блокировки в настройках телефона", Toast.LENGTH_LONG).show()
                }.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF3B4E7A)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Fingerprint, null, Modifier.size(22.dp), tint = Color(0xFFF3EEFF)) }
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Замок по отпечатку", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Персона спросит отпечаток или код при открытии", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(s.lockEnabled, null)
                }
                RowDivider()
                SettingsRow(Icons.Rounded.Backup, Color(0xFF2F6B5A), "Резервная копия", "Сохранить всё в файл или восстановить") { nav.go(Route.SettingsPage("backup")) }
                RowDivider()
                SettingsRow(Icons.Rounded.Payments, Color(0xFF7A5A2F), "Расходы API", usageSummary(repo.usage.collectAsStateWithLifecycle().value)) { nav.go(Route.SettingsPage("usage")) }
            }
        }
        item {
            SettingsGroup("Прочее") {
                SettingsRow(Icons.Rounded.SystemUpdate, Color(0xFF2F5F7A), "Обновления",
                    if (Updates.hasNew(ctx, s.updates)) "Доступна версия ${s.updates.latest}" else "Версия ${Updates.current(ctx)}" + if (s.updates.auto) " · автообновление" else "") {
                    nav.go(Route.SettingsPage("updates"))
                }
                RowDivider()
                SettingsRow(Icons.Rounded.BugReport, Color(0xFF5A4A5A), "Журнал ошибок", "Скопировать отчёт, если что-то сломалось") { showLog = true }
                RowDivider()
                SettingsRow(Icons.Rounded.RestartAlt, Color(0xFF5A4A5A), "Сбросить ролплейные настройки", "Стиль, правила, память и промпт — по умолчанию") { confirmReset = true }
            }
        }
        item {
            val v = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
            Text("Персона $v", Modifier.fillMaxWidth().padding(top = 4.dp), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }

    if (showLog) AlertDialog({ showLog = false },
        title = { Text("Журнал ошибок") },
        text = { Text(ErrorLog.text(), Modifier.verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
        confirmButton = { TextButton({
            ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("log",
                "Персона ${ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName}\n${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n" +
                    "Модель: ${s.activeModel}, GPU ${s.useGpu}, режим ${s.mode}, API ${s.provider}/${s.apiModel}\n\n" + ErrorLog.text()))
            Toast.makeText(ctx, "Отчёт скопирован", Toast.LENGTH_SHORT).show()
        }) { Text("Скопировать") } },
        dismissButton = { TextButton({ ErrorLog.clear(); showLog = false }) { Text("Очистить") } })
    if (confirmReset) AlertDialog({ confirmReset = false },
        title = { Text("Сбросить настройки ролплея?") },
        text = { Text("Модели, ключ, персоны и персонажи не пострадают.") },
        confirmButton = { TextButton({
            val d = Settings()
            repo.settings { it.copy(preset = d.preset, temperature = d.temperature, topP = d.topP, repPenalty = d.repPenalty, length = d.length,
                noImpersonation = d.noImpersonation, italicActions = d.italicActions, summarize = d.summarize, summarizeEvery = d.summarizeEvery, systemTemplate = d.systemTemplate) }
            confirmReset = false
        }) { Text("Сбросить") } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Отмена") } })
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) { Column { content() } }
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun SettingsRow(icon: ImageVector, tint: Color, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = Color(0xFFF3EEFF))
        }
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ======================== Подстраницы ========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageScreen(nav: Nav, page: String) {
    val title = when (page) {
        "local" -> "Модели на телефоне"; "api" -> "API-ключ"; "prompt" -> "Системный промпт"; "backup" -> "Резервная копия"; "look" -> "Оформление"; "updates" -> "Обновления"; "usage" -> "Расходы API"; "style" -> "Стиль ответов"; "memory" -> "Память"; "personas" -> "Мои персоны"; else -> "Настройки"
    }
    Scaffold(topBar = {
        TopAppBar(navigationIcon = { IconButton({ nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
            title = { Text(title, fontWeight = FontWeight.Bold) })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (page) {
                "local" -> LocalPage()
                "api" -> ApiPage()
                "style" -> StylePage()
                "memory" -> MemoryPage()
                "personas" -> PersonasPage()
                "prompt" -> PromptPage()
                "backup" -> BackupPage()
                "look" -> LookPage()
                "updates" -> UpdatesPage()
                "usage" -> UsagePage()
            }
        }
    }
}

@Composable
private fun LocalPage() {
    val ctx = LocalContext.current
    val app = ctx.app
    val repo = app.repo
    val s by repo.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    var importing by remember { mutableStateOf<Float?>(null) }
    var speed by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<File?>(null) }
    val dl = DownloadWatch.live
    val gtick = DownloadWatch.tick

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            } ?: "model.litertlm"
            if (!name.endsWith(".litertlm", true)) { Toast.makeText(ctx, "Нужен файл .litertlm", Toast.LENGTH_LONG).show(); return@launch }
            importing = 0f
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val size = ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                    val dest = File(app.engines.local.saveDir, name)
                    val tmp = File(dest.path + ".part")
                    ctx.contentResolver.openInputStream(uri)!!.use { inp ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(1 shl 20); var done = 0L
                            while (true) {
                                val n = inp.read(buf); if (n < 0) break
                                out.write(buf, 0, n); done += n
                                if (size > 0) importing = done.toFloat() / size
                            }
                        }
                    }
                    tmp.renameTo(dest)
                }.onFailure { ErrorLog.add("import", it) }.isSuccess
            }
            importing = null
            if (ok) { repo.settings { it.copy(activeModel = name) }; tick++ }
            Toast.makeText(ctx, if (ok) "Модель импортирована" else "Не удалось скопировать файл", Toast.LENGTH_SHORT).show()
        }
    }
    val installed = remember(tick, gtick, s.downloads, s.activeModel) { app.engines.local.models().filter { it.name !in s.downloads } }

    SharedFolderCard(tick) { tick++ }
    SectionTitle("Модели")
    ModelCatalog.all.forEach { m ->
        val haveFile = installed.firstOrNull { it.name == m.file }
        val active = s.activeModel == m.file && haveFile != null
        val st = dl[m.file]
        val downloading = st != null || m.file in s.downloads
        Surface(shape = RoundedCornerShape(20.dp), color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().then(if (active) Modifier.border(1.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp)) else Modifier),
            onClick = { if (haveFile != null && !active) repo.settings { it.copy(activeModel = m.file) } }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (m.best) { Spacer(Modifier.size(8.dp)); Badge("Советую") }
                            if (active) { Spacer(Modifier.size(6.dp)); Badge("Активна", Mint) }
                        }
                        Text(m.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when {
                        downloading -> TextButton({ Downloads.cancel(ctx, m.file); dl.remove(m.file) }) { Text("Отмена") }
                        haveFile != null -> IconButton({ confirmDelete = haveFile }) { Icon(Icons.Rounded.Delete, "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        else -> Button({
                            val free = app.engines.local.saveDir.usableSpace
                            if (free < m.sizeBytes + 300_000_000) Toast.makeText(ctx, "Мало места: нужно ${bytesLabel(m.sizeBytes)}, свободно ${bytesLabel(free)}", Toast.LENGTH_LONG).show()
                            else Downloads.start(ctx, m)
                        }) { Text("Скачать") }
                    }
                }
                if (downloading) {
                    val p = if (st != null && st.total > 0) st.done.toFloat() / st.total else 0f
                    LinearProgressIndicator({ p }, Modifier.fillMaxWidth())
                    Text(if (st?.status == DownloadManager.STATUS_PAUSED) "Пауза — ждём сеть…" else "${(p * 100).toInt()}% · ${bytesLabel(st?.done ?: 0)} из ${bytesLabel(m.sizeBytes)}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Text(
                    (if (haveFile != null) "Установлена" + (if (app.engines.local.isShared(haveFile)) " · общая папка" else "") + (if (!active) " · нажми, чтобы выбрать" else "") else bytesLabel(m.sizeBytes)),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    installed.filter { f -> ModelCatalog.all.none { it.file == f.name } }.forEach { f ->
        val active = s.activeModel == f.name
        Surface(shape = RoundedCornerShape(20.dp), color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(), onClick = { if (!active) repo.settings { it.copy(activeModel = f.name) } }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.name.substringBeforeLast('.'), fontWeight = FontWeight.Bold)
                        if (active) { Spacer(Modifier.size(6.dp)); Badge("Активна", Mint) }
                    }
                    Text("Свой файл · " + (if (app.engines.local.isShared(f)) "общая папка · " else "") + bytesLabel(f.length()),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ confirmDelete = f }) { Icon(Icons.Rounded.Delete, "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    importing?.let { p -> Column { Text("Копирую файл… ${(p * 100).toInt()}%"); LinearProgressIndicator({ p }, Modifier.fillMaxWidth().padding(top = 6.dp)) } }
    OutlinedButton({ importFile.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth().height(48.dp), enabled = importing == null) {
        Icon(Icons.Rounded.Add, null); Spacer(Modifier.size(6.dp)); Text("Импорт своего файла (.litertlm)")
    }

    SectionTitle("Работа модели")
    CardBox {
        Column {
            SwitchRow("Ускорение GPU", "Быстрее, но сильнее греет телефон", s.useGpu) { v -> repo.settings { it.copy(useGpu = v) } }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Контекст — сколько текста модель держит в голове", fontWeight = FontWeight.SemiBold)
                val opts = listOf(2048, 4096, 8192)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    opts.forEachIndexed { i, v ->
                        SegmentedButton(s.contextTokens == v, { repo.settings { it.copy(contextTokens = v) } }, SegmentedButtonDefaults.itemShape(i, opts.size)) { Text("$v") }
                    }
                }
                Text("Больше — персонаж помнит дальше, но ответ начинается медленнее и нужно больше памяти.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().clickable {
                if (!app.engines.local.hasModel(s)) { Toast.makeText(ctx, "Сначала скачай или выбери модель", Toast.LENGTH_SHORT).show(); return@clickable }
                speed = "Проверяю… первый запуск загружает модель в память"
                scope.launch { speed = speedTest(app, s) }
            }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Тест скорости", fontWeight = FontWeight.SemiBold)
                    Text(speed ?: "Сравнить CPU и GPU на одной сцене", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    confirmDelete?.let { f ->
        AlertDialog({ confirmDelete = null },
            title = { Text("Удалить модель?") },
            text = { Text("${f.name} — ${bytesLabel(f.length())}. Её можно будет скачать снова." +
                if (app.engines.local.isShared(f)) "\n\nФайл лежит в общей папке — он пропадёт и в НаноПеро." else "") },
            confirmButton = { TextButton({
                app.engines.local.unload(); f.delete()
                if (s.activeModel == f.name) repo.settings { it.copy(activeModel = app.engines.local.models().firstOrNull { m -> m.name != f.name }?.name ?: "") }
                confirmDelete = null; tick++
            }) { Text("Удалить") } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Отмена") } })
    }
}

@Composable
private fun Badge(text: String, color: Color = Accent) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.18f)) {
        Text(text, Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApiPage() {
    val ctx = LocalContext.current
    val app = ctx.app
    val repo = app.repo
    val s by repo.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val auth by OpenRouterAuth.state.collectAsStateWithLifecycle()
    var keyText by remember { mutableStateOf(KeyCrypto.decrypt(s.apiKeyEnc)) }
    var showKey by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var apiModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var pickModel by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    val connected = app.engines.api.ready(s)
    var balance by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(s.apiKeyEnc, s.provider) { balance = if (connected) app.engines.api.balance(s) else null }

    fun connectWith(provider: Provider, key: String) {
        checking = true; status = null
        scope.launch {
            val r = Connector.connect(ctx, provider, key)
            if (r.ok) { keyText = key; apiModels = r.models }
            status = r.ok to r.message
            checking = false
        }
    }
    fun pasteAndConnect(preferred: Provider?) {
        val clip = ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim().orEmpty()
        if (!KeyLinks.looksLikeKey(clip)) { status = false to "В буфере нет ключа. Скопируй его на сайте провайдера и нажми ещё раз."; return }
        connectWith(KeyLinks.detect(clip) ?: preferred ?: s.provider, clip)
    }
    LaunchedEffect(auth) { if (auth is OpenRouterAuth.State.Done) { keyText = KeyCrypto.decrypt(repo.settings.value.apiKeyEnc); status = true to (auth as OpenRouterAuth.State.Done).message } }

    // ---------- Статус ----------
    if (connected) Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Mint.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = Mint) }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${s.provider.title} подключён", fontWeight = FontWeight.Bold)
                Text(s.apiModel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                balance?.let { b -> Text("Баланс: " + String.format(java.util.Locale.US, "$%.2f", b), style = MaterialTheme.typography.bodySmall,
                    color = if (b < 0.1) MaterialTheme.colorScheme.error else Mint) }
            }
            TextButton({
                if (apiModels.isEmpty()) scope.launch { runCatching { app.engines.api.listModels(repo.settings.value) }.onSuccess { apiModels = it } }
                pickModel = true
            }) { Text("Модель") }
        }
    }
    status?.let { (ok, msg) -> Text(msg, color = if (ok) Mint else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

    // ---------- Быстрое подключение ----------
    SectionTitle(if (connected) "Подключить другой" else "Быстрое подключение")
    // OpenRouter: вход в один тап
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("OpenRouter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(8.dp)); Badge("Проще всего")
            }
            Text("Сотни моделей по одному входу, есть бесплатные. Войди через браузер — ключ создастся и подключится сам.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (val a = auth) {
                OpenRouterAuth.State.Waiting -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.size(10.dp))
                    Text("Жду входа в браузере… После входа нажми «Вернуться в Персону».", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton({ OpenRouterAuth.reset() }) { Text("Отмена") }
                }
                OpenRouterAuth.State.Exchanging -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.size(10.dp)); Text("Получаю ключ…")
                }
                is OpenRouterAuth.State.Failed -> Text(a.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                else -> {}
            }
            Button({ OpenRouterAuth.start(ctx) }, Modifier.fillMaxWidth().height(48.dp),
                enabled = auth != OpenRouterAuth.State.Waiting && auth != OpenRouterAuth.State.Exchanging) { Text("Войти через OpenRouter") }
        }
    }
    // Остальные: получить ключ → вставить
    listOf(
        Provider.GEMINI to "Модели Google, есть бесплатный лимит",
        Provider.OPENAI to "Модели GPT, оплата по использованию",
        Provider.CLAUDE to "Модели Claude, оплата по использованию",
    ).forEach { (p, note) ->
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ KeyLinks.url(p)?.let { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }, Modifier.weight(1f)) { Text("1. Получить ключ") }
                    Button({ pasteAndConnect(p) }, Modifier.weight(1f), enabled = !checking) {
                        if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("2. Вставить")
                    }
                }
            }
        }
    }
    Text("Скопируй ключ на сайте и нажми «Вставить» — приложение само поймёт, чей это ключ, проверит его и выберет модель.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    // ---------- Вручную ----------
    TextButton({ manual = !manual }) { Text(if (manual) "Скрыть ручную настройку" else "Ручная настройка и свой сервер") }
    if (manual) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Provider.entries.forEach { p ->
                FilterChip(s.provider == p, {
                    if (s.provider != p) { repo.settings { it.copy(provider = p, apiModel = "") }; apiModels = emptyList(); status = null }
                }, { Text(p.title) }, leadingIcon = if (s.provider == p) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null)
            }
        }
        CardBox {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.provider == Provider.CUSTOM) OutlinedTextField(s.baseUrl, { v -> repo.settings { it.copy(baseUrl = v.trim()) } }, Modifier.fillMaxWidth(),
                    label = { Text("Адрес сервера") }, placeholder = { Text("http://192.168.1.10:1234/v1") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = RoundedCornerShape(14.dp),
                    supportingText = { Text("LM Studio, Ollama и другие OpenAI-совместимые серверы") })
                OutlinedTextField(keyText, { v ->
                    keyText = v.trim(); status = null
                    KeyLinks.detect(keyText)?.let { p -> if (p != s.provider && s.provider != Provider.CUSTOM) repo.settings { it.copy(provider = p, apiModel = "") } }
                    repo.settings { it.copy(apiKeyEnc = KeyCrypto.encrypt(keyText)) }
                }, Modifier.fillMaxWidth(), label = { Text("Ключ") }, placeholder = { Text(s.provider.keyHint) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton({ showKey = !showKey }) { Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Показать ключ") } },
                    supportingText = { Text("Хранится зашифрованным на телефоне") })
                Button({ connectWith(s.provider, keyText) }, Modifier.fillMaxWidth().height(48.dp), enabled = !checking && (keyText.isNotBlank() || s.provider == Provider.CUSTOM)) {
                    if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Проверить и подключить")
                }
                Surface(onClick = { pickModel = true }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Модель", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(s.apiModel.ifBlank { "Не выбрана" }, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (connected) TextButton({ repo.settings { it.copy(apiKeyEnc = "", apiModel = "") }; keyText = ""; status = null; OpenRouterAuth.reset() }) {
        Text("Отключить API", color = MaterialTheme.colorScheme.error)
    }

    SectionTitle("Картинки сцен")
    CardBox {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Кнопка «Нарисовать сцену» в чате превращает текущий момент в иллюстрацию. Рисует модель картинок того же провайдера и ключа.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.provider == Provider.CLAUDE) Text("Claude не рисует картинки — для них выбери другой провайдер.", style = MaterialTheme.typography.bodySmall, color = Amber)
            else {
                OutlinedTextField(s.imageModel, { v -> repo.settings { it.copy(imageModel = v.trim()) } }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                    label = { Text("Модель для картинок") }, placeholder = { Text(when (s.provider) {
                        Provider.OPENROUTER -> "например, google/gemini-2.5-flash-image"
                        Provider.OPENAI -> "например, gpt-image-1"
                        Provider.GEMINI -> "например, imagen-4.0-generate-001"
                        else -> "название модели"
                    }) })
                if (apiModels.isNotEmpty()) {
                    val img = apiModels.filter { m -> listOf("image", "imagen", "dall-e", "flux", "diffusion").any { m.contains(it, true) } }.take(8)
                    if (img.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        img.forEach { m -> FilterChip(s.imageModel == m, { repo.settings { it.copy(imageModel = m) } }, { Text(m.substringAfterLast('/'), maxLines = 1) }) }
                    }
                }
                Text("Картинки обычно платные: проверь цену модели у провайдера.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (pickModel) ApiModelPicker(apiModels, s.apiModel, { m -> repo.settings { it.copy(apiModel = m) }; pickModel = false }) { pickModel = false }
}

@Composable
private fun StylePage() {
    val repo = LocalContext.current.app.repo
    val s by repo.settings.collectAsStateWithLifecycle()
    SectionTitle("Характер ответов")
    val icons = mapOf(Preset.STRICT to Icons.Rounded.Shield, Preset.BALANCE to Icons.Rounded.Balance, Preset.FANTASY to Icons.Rounded.AutoAwesome)
    val desc = mapOf(Preset.STRICT to "Точно держит роль и факты, меньше выдумок",
        Preset.BALANCE to "Живо, но последовательно — для большинства историй",
        Preset.FANTASY to "Смело импровизирует, неожиданные повороты")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Preset.entries.forEach { p ->
            val on = s.temperature == p.temp && s.repPenalty == p.rep
            Surface(onClick = { repo.settings { it.copy(preset = p, temperature = p.temp, topP = p.topP, repPenalty = p.rep) } },
                shape = RoundedCornerShape(20.dp), color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.weight(1f).then(if (on) Modifier.border(1.dp, Accent, RoundedCornerShape(20.dp)) else Modifier)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(icons[p]!!, null, tint = if (on) Accent else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(p.title, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    Text(Preset.entries.firstOrNull { s.temperature == it.temp && s.repPenalty == it.rep }?.let { desc[it] } ?: "Свои значения — подстроены ползунками ниже.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    CardBox {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LabeledSlider("Креативность", s.temperature, 0.1f..1.5f, "%.2f") { v -> repo.settings { it.copy(temperature = (v * 20).toInt() / 20f) } }
            LabeledSlider("Против повторов", s.repPenalty, 1.0f..1.3f, "%.2f") { v -> repo.settings { it.copy(repPenalty = (v * 100).toInt() / 100f) } }
            if (s.repPenalty > 1.08f) Text("Выше 1,08 модель может путать окончания в русском тексте.", style = MaterialTheme.typography.bodySmall, color = Amber)
        }
    }
    SectionTitle("Длина ответа")
    val examples = mapOf(Length.REPLY to "«*кивает* Давай.»", Length.PARAGRAPH to "Один-два абзаца — как в переписке", Length.SCENE to "Развёрнутая сцена — как в книге")
    CardBox {
        Column {
            Length.entries.forEachIndexed { i, l ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().clickable { repo.settings { it.copy(length = l) } }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(s.length == l, { repo.settings { it.copy(length = l) } })
                    Column { Text(l.title, fontWeight = FontWeight.SemiBold); Text(examples[l]!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
    SectionTitle("Правила")
    CardBox {
        Column {
            SwitchRow("Не писать за меня", "Персонаж не решает за твоего героя", s.noImpersonation) { v -> repo.settings { it.copy(noImpersonation = v) } }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow("Действия *курсивом*", "Стиль «*улыбается* Привет». Выключи для обычной переписки", s.italicActions) { v -> repo.settings { it.copy(italicActions = v) } }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow("Исправлять ошибки", "После ответа модель вычитывает текст: орфография, падежи, роды. Ответ появится на пару секунд позже", s.proofread) { v -> repo.settings { it.copy(proofread = v) } }
        }
    }
}

@Composable
private fun MemoryPage() {
    val repo = LocalContext.current.app.repo
    val s by repo.settings.collectAsStateWithLifecycle()
    Text("Когда переписка становится длинной, старые сообщения сжимаются в краткое содержание, а важные факты о тебе выписываются отдельно. Так персонаж помнит больше, чем помещается в модель.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    CardBox {
        Column {
            SwitchRow("Долгая память", "Краткое содержание и факты о тебе", s.summarize) { v -> repo.settings { it.copy(summarize = v) } }
            if (s.summarize) Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                LabeledSlider("Сжимать каждые … сообщений", s.summarizeEvery.toFloat(), 10f..60f, "%.0f") { v -> repo.settings { it.copy(summarizeEvery = v.toInt()) } }
                Text("Реже — меньше нагрузка на модель, чаще — точнее память.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text("Настроение, отношение, дневник и распорядок дня включаются отдельно у каждого персонажа — в «Расширенном создании».",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PersonasPage() {
    val repo = LocalContext.current.app.repo
    val s by repo.settings.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<Persona?>(null) }
    var confirm by remember { mutableStateOf<Persona?>(null) }
    Text("Персона — это ты в ролевом чате: имя и пара слов о себе. Для каждого чата можно выбрать свою, а персонажи будут звать тебя по её имени.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (s.personas.isEmpty()) CardBox {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Пока одна безымянная персона: «${s.user}».", fontWeight = FontWeight.SemiBold)
            Text("Создай свою — например, себя настоящего и пару героев для фэнтези и детективов.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    s.personas.forEach { p ->
        val def = s.defaultPersonaId == p.id || (s.defaultPersonaId == null && s.personas.first().id == p.id)
        Surface(onClick = { edit = p }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(Character(name = p.name), 48.dp)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        if (def) { Spacer(Modifier.size(8.dp)); Badge("По умолчанию") }
                    }
                    Text(p.about.ifBlank { "Без описания" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                if (!def) TextButton({ repo.settings { it.copy(defaultPersonaId = p.id) } }) { Text("Сделать основной") }
            }
        }
    }
    OutlinedButton({ edit = Persona() }, Modifier.fillMaxWidth().height(48.dp)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.size(6.dp)); Text("Новая персона") }

    edit?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        var about by remember(p.id) { mutableStateOf(p.about) }
        val exists = s.personas.any { it.id == p.id }
        AlertDialog({ edit = null },
            title = { Text(if (exists) "Персона" else "Новая персона") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth(), label = { Text("Имя") }, singleLine = true)
                    OutlinedTextField(about, { about = it.take(600) }, Modifier.fillMaxWidth(), label = { Text("О себе") }, minLines = 3,
                        placeholder = { Text("Например: студент-алхимик, рыжий, вечно опаздывает") })
                }
            },
            confirmButton = { TextButton({
                if (name.isNotBlank()) {
                    val np = p.copy(name = name.trim(), about = about.trim())
                    repo.settings { st ->
                        val list = if (exists) st.personas.map { if (it.id == np.id) np else it } else st.personas + np
                        st.copy(personas = list, defaultPersonaId = st.defaultPersonaId ?: np.id)
                    }
                }
                edit = null
            }) { Text("Сохранить") } },
            dismissButton = { Row {
                if (exists) TextButton({ confirm = p; edit = null }) { Text("Удалить") }
                TextButton({ edit = null }) { Text("Отмена") }
            } })
    }
    confirm?.let { p ->
        AlertDialog({ confirm = null }, title = { Text("Удалить «${p.name}»?") },
            text = { Text("Чаты останутся, в них ты станешь основной персоной.") },
            confirmButton = { TextButton({
                repo.settings { st -> st.copy(personas = st.personas.filterNot { it.id == p.id }, defaultPersonaId = st.defaultPersonaId.takeIf { it != p.id }) }
                confirm = null
            }) { Text("Удалить") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Отмена") } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptPage() {
    val repo = LocalContext.current.app.repo
    val s by repo.settings.collectAsStateWithLifecycle()
    val chars by repo.chars.collectAsStateWithLifecycle()
    var tv by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(s.systemTemplate)) }
    var previewFor by remember { mutableStateOf(chars.firstOrNull()?.id) }
    var showPreview by remember { mutableStateOf(false) }
    val dirty = tv.text != s.systemTemplate

    fun insert(tag: String) {
        val sel = tv.selection
        val t = tv.text.substring(0, sel.min) + tag + tv.text.substring(sel.max)
        tv = androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(sel.min + tag.length))
    }

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Что это", fontWeight = FontWeight.Bold)
            Text("Скрытая инструкция, которую модель читает перед каждым ответом. Ты её не видишь в чате, но она решает, как модель понимает свою роль.",
                style = MaterialTheme.typography.bodyMedium)
            Text("Метки в фигурных скобках — это места, куда приложение само подставит текст: имя персонажа, его описание, память и так далее.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    SectionTitle("Шаблон")
    OutlinedTextField(tv, { tv = it }, Modifier.fillMaxWidth().heightIn(min = 220.dp), shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp))
    val missing = PROMPT_TAGS.filter { t -> t.ru != "{{я}}" && !tv.text.contains(t.ru) && !tv.text.contains(t.en) }
    if (missing.isNotEmpty()) Surface(shape = RoundedCornerShape(14.dp), color = Amber.copy(alpha = 0.12f)) {
        Text("В шаблоне нет: " + missing.joinToString { it.title.lowercase() } + ". Эти сведения модель не получит.",
            Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = Amber)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ repo.settings { it.copy(systemTemplate = tv.text) } }, enabled = dirty, modifier = Modifier.weight(1f)) { Text("Сохранить") }
        OutlinedButton({ tv = androidx.compose.ui.text.input.TextFieldValue(DEFAULT_TEMPLATE) }, Modifier.weight(1f), enabled = tv.text != DEFAULT_TEMPLATE) { Text("Стандартный") }
    }

    SectionTitle("Метки — нажми, чтобы вставить")
    CardBox {
        Column {
            PROMPT_TAGS.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().clickable { insert(t.ru) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(t.ru, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Accent)
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, fontWeight = FontWeight.SemiBold)
                        Text(t.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.Add, "Вставить", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    Text("Английские метки из SillyTavern ({{char}}, {{user}}) тоже работают.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    SectionTitle("Как это увидит модель")
    if (chars.isEmpty()) Text("Создай персонажа, чтобы посмотреть итоговый промпт.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chars.take(8).forEach { c -> FilterChip(previewFor == c.id, { previewFor = c.id; showPreview = true }, { Text(c.name) }, leadingIcon = { Avatar(c, 20.dp) }) }
        }
        val c = chars.firstOrNull { it.id == previewFor }
        val books by repo.books.collectAsStateWithLifecycle()
        val allChats by repo.chats.collectAsStateWithLifecycle()
        if (c != null) {
            val chat = allChats.filter { it.charId == c.id && it.sceneId == null }.maxByOrNull { it.updated } ?: Chat(charId = c.id)
            val text = Prompt.system(c, chat, s.copy(systemTemplate = tv.text).forChat(chat), books, chat.messages.takeLast(6).joinToString("\n") { it.text })
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))) {
                Column(Modifier.padding(14.dp)) {
                    Text("≈ ${Prompt.estimateTokens(text)} токенов из ${s.contextTokens} на телефоне", style = MaterialTheme.typography.labelMedium, color = Amber)
                    Spacer(Modifier.size(6.dp))
                    Text(if (showPreview) text else text.take(600) + if (text.length > 600) "…" else "", fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
                    if (text.length > 600) TextButton({ showPreview = !showPreview }) { Text(if (showPreview) "Свернуть" else "Показать полностью") }
                }
            }
        }
    }
}

private val today get() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
private fun money(v: Double) = if (v < 0.01) String.format(java.util.Locale.US, "$%.4f", v) else String.format(java.util.Locale.US, "$%.2f", v)
private fun tokens(n: Long) = when { n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f млн", n / 1e6); n >= 1000 -> "${n / 1000} тыс."; else -> "$n" }

fun usageSummary(u: List<UsageDay>): String {
    val month = today.take(7)
    val m = u.filter { it.date.startsWith(month) }
    if (m.isEmpty()) return "Пока запросов не было"
    val cost = m.sumOf { it.cost }; val known = m.any { it.costKnown }
    return "За месяц: ${m.sumOf { it.requests }} запросов" + if (known) " · ${money(cost)}" else " · ${tokens(m.sumOf { it.inTok + it.outTok })} токенов"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookPage() {
    val repo = LocalContext.current.app.repo
    val s by repo.settings.collectAsStateWithLifecycle()
    val look = s.look
    fun set(f: (Appearance) -> Appearance) = repo.settings { it.copy(look = f(it.look)) }

    // Живое превью
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val demo = Character(name = "Лира")
            val flat = look.bubbles == BubbleStyle.FLAT
            val r = if (look.bubbles == BubbleStyle.COMPACT) 12.dp else 18.dp
            Row(verticalAlignment = Alignment.Top) {
                if (look.chatAvatars) { Avatar(demo, 30.dp); Spacer(Modifier.size(8.dp)) }
                Surface(shape = if (flat) RoundedCornerShape(0.dp) else RoundedCornerShape(r, r, r, 6.dp), color = if (flat) Color.Transparent else BubbleChar) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = if (flat) 4.dp else 9.dp)) {
                        if (flat) Text("Лира", style = MaterialTheme.typography.labelMedium, color = Accent, fontWeight = FontWeight.Bold)
                        Text(rpText("*машет рукой* Привет! Как тебе новый вид?", Accent), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = if (flat) Alignment.CenterStart else Alignment.CenterEnd) {
                Surface(shape = if (flat) RoundedCornerShape(0.dp) else RoundedCornerShape(r, r, 6.dp, r),
                    color = if (flat) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = look.bubbleAlpha.coerceIn(0.5f, 1f))) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = if (flat) 4.dp else 9.dp)) {
                        if (flat) Text("Ты", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Text("Очень нравится!", style = MaterialTheme.typography.bodyLarge, color = if (flat) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }

    SectionTitle("Тема")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ThemeMode.entries.forEachIndexed { i, m ->
            SegmentedButton(look.theme == m, { set { it.copy(theme = m) } }, SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) { Text(m.title, maxLines = 1, fontSize = 12.sp) }
        }
    }
    SectionTitle("Акцентный цвет")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ACCENTS.forEachIndexed { i, a ->
            val on = look.accent == i
            Box(Modifier.size(48.dp).clip(CircleShape).background(if (LocalDark.current) a.dark else a.light)
                .border(if (on) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape).clickable { set { it.copy(accent = i) } },
                contentAlignment = Alignment.Center) { if (on) Icon(Icons.Rounded.Check, a.title, tint = MaterialTheme.colorScheme.surface) }
        }
        if (Build.VERSION.SDK_INT >= 31) FilterChip(look.accent == -1, { set { it.copy(accent = -1) } }, { Text("Цвета обоев") }, Modifier.height(48.dp))
    }
    SectionTitle("Размер текста")
    CardBox {
        Column(Modifier.padding(16.dp)) {
            LabeledSlider("Масштаб", look.textScale, 0.85f..1.3f, "%.2f") { v -> set { it.copy(textScale = (v * 20).toInt() / 20f) } }
            Text("Меняет размер текста во всём приложении.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    SectionTitle("Сообщения")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        BubbleStyle.entries.forEachIndexed { i, b ->
            SegmentedButton(look.bubbles == b, { set { it.copy(bubbles = b) } }, SegmentedButtonDefaults.itemShape(i, BubbleStyle.entries.size)) { Text(b.title, maxLines = 1, fontSize = 12.sp) }
        }
    }
    CardBox {
        Column {
            Column(Modifier.padding(16.dp)) {
                LabeledSlider("Плотность пузырей", look.bubbleAlpha, 0.4f..1f, "%.1f") { v -> set { it.copy(bubbleAlpha = (v * 10).toInt() / 10f) } }
                Text("Ниже — сквозь пузыри лучше виден фон чата.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow("Аватарки в чате", "Рядом с репликами персонажа", look.chatAvatars) { v -> set { it.copy(chatAvatars = v) } }
            SwitchRow("Квадратные аватарки", "Со скруглёнными углами вместо кругов", look.squareAvatars) { v -> set { it.copy(squareAvatars = v) } }
        }
    }
    SectionTitle("Списки и шрифт")
    CardBox {
        Column {
            SwitchRow("Компактный список", "Больше чатов на экране", look.compact) { v -> set { it.copy(compact = v) } }
            SwitchRow("Шрифт с засечками", "Книжное настроение во всём приложении", look.serif) { v -> set { it.copy(serif = v) } }
        }
    }
    TextButton({ set { Appearance() } }) { Text("Вернуть как было") }
}

object UpdateRequest { var installNow by mutableStateOf(false) }

/** Скачать и поставить обновление, попросив разрешение, если его ещё нет. */
suspend fun runUpdate(ctx: android.content.Context, onNeedPermission: () -> Unit) {
    val r = runCatching { Updater.install(ctx, Updater.download(ctx)) }
    r.exceptionOrNull()?.let { e -> if (e is Updater.NeedPermission) { Updater.reset(); onNeedPermission() } }
}

@Composable
private fun UpdatesPage() {
    val ctx = LocalContext.current
    val repo = ctx.app.repo
    val s by repo.settings.collectAsStateWithLifecycle()
    val st by Updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var needPerm by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var repoText by remember { mutableStateOf(s.updates.repo) }
    val u = s.updates
    val hasNew = Updates.hasNew(ctx, u)

    fun update() = scope.launch { runUpdate(ctx) { needPerm = true } }
    LaunchedEffect(UpdateRequest.installNow) { if (UpdateRequest.installNow) { UpdateRequest.installNow = false; if (hasNew) update() } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (needPerm && Updater.canInstall(ctx)) { needPerm = false; update() } }
    LaunchedEffect(Unit) { if (u.lastCheck == 0L || System.currentTimeMillis() - u.lastCheck > 600_000) { checking = true; Updates.check(ctx); checking = false } }

    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Установлена версия ${Updates.current(ctx)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when {
                checking -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.size(8.dp)); Text("Проверяю…") }
                hasNew -> {
                    Text("Доступна версия ${u.latest}", color = Mint, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    u.notes?.takeIf { it.isNotBlank() }?.let { Text(it.trim(), style = MaterialTheme.typography.bodySmall, maxLines = 12) }
                    when (val x = st) {
                        is Updater.State.Downloading -> { Text("Скачиваю… ${(x.progress * 100).toInt()}%"); LinearProgressIndicator({ x.progress }, Modifier.fillMaxWidth()) }
                        Updater.State.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.size(8.dp)); Text("Устанавливаю… приложение перезапустится")
                        }
                        else -> Button(::update, Modifier.fillMaxWidth().height(48.dp), enabled = u.url != null) {
                            Text(if (st is Updater.State.Ready) "Установить ${u.latest}" else "Обновить до ${u.latest}")
                        }
                    }
                    if (u.url == null) Text("В релизе нет APK-файла.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                u.latest != null -> Text("Это последняя версия", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Text("Релизов пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            (st as? Updater.State.Failed)?.let { Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (needPerm) Surface(shape = RoundedCornerShape(20.dp), color = Amber.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Нужно одно разрешение", fontWeight = FontWeight.Bold)
            Text("Android спросит, можно ли Персоне устанавливать приложения. Включи переключатель и вернись — установка продолжится сама.", style = MaterialTheme.typography.bodySmall)
            Button({ ctx.startActivity(Intent(SysSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))) }) { Text("Открыть настройки") }
        }
    }
    CardBox {
        Column {
            SwitchRow("Обновлять автоматически", "Раз в полдня проверять GitHub и скачивать новую версию" + if (Build.VERSION.SDK_INT >= 31) ". После первого обновления ставится без лишних вопросов" else "", u.auto) { v ->
                repo.settings { it.copy(updates = it.updates.copy(auto = v)) }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            NavRow("Проверить сейчас", if (u.lastCheck > 0) "Последняя проверка: " + java.text.SimpleDateFormat("d MMMM, HH:mm", java.util.Locale("ru")).format(java.util.Date(u.lastCheck)) else "Ещё не проверялось", {
                checking = true
                scope.launch {
                    status = Updates.check(ctx).fold({ i -> if (Updates.compare(i.latest ?: "0", Updates.current(ctx)) > 0) null else "Новых версий нет" }, { it.message ?: "Не удалось проверить" })
                    checking = false
                }
            })
            u.page?.let { pg -> HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                NavRow("Открыть на GitHub", u.repo, { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pg))) }) }
        }
    }
    status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    TextButton({ advanced = !advanced }) { Text(if (advanced) "Скрыть" else "Другой репозиторий") }
    if (advanced) {
        OutlinedTextField(repoText, { repoText = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
            label = { Text("Репозиторий GitHub") }, placeholder = { Text(DEFAULT_REPO) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ repo.settings { it.copy(updates = it.updates.copy(repo = repoText.ifBlank { DEFAULT_REPO }, latest = null, url = null)) } }) { Text("Сохранить") }
            TextButton({ repoText = DEFAULT_REPO; repo.settings { it.copy(updates = it.updates.copy(repo = DEFAULT_REPO)) } }) { Text("По умолчанию") }
        }
    }
}

@Composable
private fun UsagePage() {
    val repo = LocalContext.current.app.repo
    val usage by repo.usage.collectAsStateWithLifecycle()
    val s by repo.settings.collectAsStateWithLifecycle()
    var priceFor by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val month = today.take(7)
    @Composable fun summary(title: String, l: List<UsageDay>) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (l.any { it.costKnown }) money(l.sumOf { it.cost }) else "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Accent)
                Text("${l.sumOf { it.requests }} запросов · вход ${tokens(l.sumOf { it.inTok })} · ответы ${tokens(l.sumOf { it.outTok })} токенов",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text("Считаются только запросы через API — модели на телефоне бесплатны. OpenRouter сам присылает стоимость, для остальных укажи цену модели — её видно на сайте провайдера.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { summary("Сегодня", usage.filter { it.date == today }) }
        Box(Modifier.weight(1f)) { summary("Этот месяц", usage.filter { it.date.startsWith(month) }) }
    }
    SectionTitle("По моделям за месяц")
    val models = (usage.filter { it.date.startsWith(month) }.map { it.model } + listOfNotNull(s.apiModel.takeIf { it.isNotBlank() })).distinct()
    if (models.isEmpty()) Text("Пока пусто.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    models.forEach { m ->
        val l = usage.filter { it.date.startsWith(month) && it.model == m }
        val price = s.prices[m]
        Surface(onClick = { priceFor = m }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.substringAfterLast('/'), fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${l.sumOf { it.requests }} запросов · ${tokens(l.sumOf { it.inTok + it.outTok })} токенов · " +
                        (price?.let { "цена $${it.inPerM}/$${it.outPerM} за 1 млн" } ?: if (l.any { it.costKnown }) "цена от провайдера" else "нажми, чтобы указать цену"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (l.any { it.costKnown }) Text(money(l.sumOf { it.cost }), fontWeight = FontWeight.Bold)
            }
        }
    }
    TextButton({ confirm = true }) { Text("Обнулить статистику", color = MaterialTheme.colorScheme.error) }
    priceFor?.let { m ->
        var inP by remember(m) { mutableStateOf(s.prices[m]?.inPerM?.toString() ?: "") }
        var outP by remember(m) { mutableStateOf(s.prices[m]?.outPerM?.toString() ?: "") }
        AlertDialog({ priceFor = null }, title = { Text("Цена модели") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(m, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(inP, { inP = it.replace(',', '.') }, label = { Text("Вход, $ за 1 млн токенов") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(outP, { outP = it.replace(',', '.') }, label = { Text("Ответ, $ за 1 млн токенов") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Text("Будет учитываться для новых запросов.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton({
                repo.settings { it.copy(prices = it.prices + (m to Price(inP.toDoubleOrNull() ?: 0.0, outP.toDoubleOrNull() ?: 0.0))) }; priceFor = null
            }) { Text("Сохранить") } },
            dismissButton = { TextButton({ priceFor = null }) { Text("Отмена") } })
    }
    if (confirm) AlertDialog({ confirm = false }, title = { Text("Обнулить статистику расходов?") },
        confirmButton = { TextButton({ repo.clearUsage(); confirm = false }) { Text("Обнулить") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Отмена") } })
}

@Composable
private fun BackupPage() {
    val ctx = LocalContext.current
    val repo = ctx.app.repo
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) { busy = true; scope.launch {
            result = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { repo.exportBackup(it) } }
                .fold({ "Копия сохранена" }, { ErrorLog.add("backup", it); "Не удалось сохранить: ${it.message}" }) }
            busy = false
        } }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { busy = true; scope.launch {
            result = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)!!.use { repo.importBackup(it) } }
                .fold({ (c, ch) -> "Восстановлено: персонажей $c, чатов $ch" }, { ErrorLog.add("restore", it); it.message ?: "Не удалось восстановить" }) }
            busy = false
        } }
    }
    val chars by repo.chars.collectAsStateWithLifecycle()
    val chats by repo.chats.collectAsStateWithLifecycle()
    Text("Один файл со всем: персонажи, чаты, групповые чаты, лорбуки, память, персоны, настройки, аватарки и картинки. Можно перенести на новый телефон.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Сейчас в Персоне", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${chars.size} персонажей · ${chats.size} чатов", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
    Button({ save.launch("persona-$today.zip") }, Modifier.fillMaxWidth().height(52.dp), enabled = !busy) { Text("Сохранить копию") }
    OutlinedButton({ restore.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, Modifier.fillMaxWidth().height(52.dp), enabled = !busy) { Text("Восстановить из копии") }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    result?.let { Text(it, color = if (it.startsWith("Не ")) MaterialTheme.colorScheme.error else Mint) }
    SectionTitle("Автобэкап")
    val st by repo.settings.collectAsStateWithLifecycle()
    val ab = st.autoBackup
    fun setAb(f: (AutoBackupCfg) -> AutoBackupCfg) = repo.settings { it.copy(autoBackup = f(it.autoBackup)) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            val name = android.provider.DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').ifBlank { "Папка" }
            setAb { it.copy(folderUri = uri.toString(), folderName = name) }
        }
    }
    CardBox {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Как часто", fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BackupFreq.entries.forEachIndexed { i, f ->
                    SegmentedButton(ab.freq == f, { setAb { it.copy(freq = f) } }, SegmentedButtonDefaults.itemShape(i, BackupFreq.entries.size)) {
                        Text(when (f) { BackupFreq.OFF -> "Выкл"; BackupFreq.DAILY -> "День"; BackupFreq.WEEKLY -> "Неделя"; BackupFreq.MONTHLY -> "Месяц" }, maxLines = 1)
                    }
                }
            }
            if (ab.freq != BackupFreq.OFF) {
                Text("Сколько копий хранить", fontWeight = FontWeight.SemiBold)
                val keeps = listOf(1, 3, 5, 10)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    keeps.forEachIndexed { i, k -> SegmentedButton(ab.keep == k, { setAb { it.copy(keep = k) } }, SegmentedButtonDefaults.itemShape(i, keeps.size)) { Text("$k") } }
                }
                Text("Старые копии удаляются сами.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Куда сохранять", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (ab.folderUri == null) "Загрузки/Персона" else ab.folderName ?: "Своя папка", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    TextButton({ pickFolder.launch(null) }) { Text("Выбрать") }
                    if (ab.folderUri != null) TextButton({ setAb { it.copy(folderUri = null, folderName = null) } }) { Text("Сбросить") }
                }
            }
            Text(when {
                ab.lastError != null -> "Последняя попытка не удалась: ${ab.lastError}"
                ab.last > 0 -> "Последняя автокопия: " + java.text.SimpleDateFormat("d MMMM, HH:mm", java.util.Locale("ru")).format(java.util.Date(ab.last))
                else -> "Автокопий ещё не было"
            }, style = MaterialTheme.typography.bodySmall, color = if (ab.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton({ busy = true; scope.launch { val r = AutoBackup.run(ctx); result = r.fold({ "Сохранено: $it" }, { "Не удалось: ${it.message}" }); busy = false } },
                Modifier.fillMaxWidth(), enabled = !busy) { Text("Сделать автокопию сейчас") }
        }
    }
    Text("API-ключ и скачанные модели в копию не входят: ключ зашифрован для этого телефона, а модели слишком большие. При восстановлении совпадающие персонажи и чаты заменяются, остальное не трогается.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SharedFolderCard(tick: Int, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val local = ctx.app.engines.local
    var allowed by remember { mutableStateOf(local.sharedAllowed()) }
    var moving by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = local.sharedAllowed()
        if (now != allowed) { allowed = now; onChanged() }
    }
    if (Build.VERSION.SDK_INT < 30) return
    val privateModels = remember(tick, allowed) { local.privateDir.listFiles()?.count { it.name.endsWith(".litertlm") } ?: 0 }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(if (allowed) Mint.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.FolderShared, null, tint = if (allowed) Mint else Accent)
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Общая папка с НаноПеро", fontWeight = FontWeight.Bold)
                    Text(if (allowed) "Включена · Загрузки/$SHARED_FOLDER" else "Одна модель на два приложения",
                        style = MaterialTheme.typography.bodySmall, color = if (allowed) Mint else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!allowed) {
                Text("Модели будут лежать в «Загрузки/$SHARED_FOLDER» — их увидят и «Персона», и «НаноПеро». Не придётся качать Gemma дважды.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button({
                    runCatching {
                        ctx.startActivity(Intent(SysSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                    }.onFailure { ctx.startActivity(Intent(SysSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                }, Modifier.fillMaxWidth()) { Text("Разрешить доступ к файлам") }
            } else if (privateModels > 0) OutlinedButton({
                scope.launch {
                    moving = "Переношу…"
                    val r = runCatching { local.moveToShared { moving = it } }
                    moving = null; onChanged()
                    Toast.makeText(ctx, r.fold({ "Перенесено моделей: $it" }, { it.message ?: "Ошибка" }), Toast.LENGTH_SHORT).show()
                }
            }, Modifier.fillMaxWidth(), enabled = moving == null) { Text(moving ?: "Перенести мои модели в общую папку ($privateModels)") }
        }
    }
}

@Composable
fun LabeledSlider(title: String, v: Float, range: ClosedFloatingPointRange<Float>, fmt: String, onChange: (Float) -> Unit) {
    Column {
        Row { Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(String.format(java.util.Locale.US, fmt, v).replace('.', ','), fontWeight = FontWeight.Bold, color = Accent) }
        Slider(v, onChange, valueRange = range)
    }
}

@Composable
private fun ApiModelPicker(models: List<String>, current: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    AlertDialog(onClose,
        title = { Text("Модель API") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(if (models.isEmpty()) "Впиши название модели" else "Поиск среди ${models.size}") })
                Spacer(Modifier.size(8.dp))
                val shown = models.filter { q.isBlank() || it.contains(q.trim(), true) }.take(200)
                if (models.isEmpty()) Text("Нажми «Проверить ключ», чтобы загрузить список, или впиши название вручную и нажми «Готово».",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown) { m ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(m) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(m == current, { onPick(m) })
                            Text(m, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ if (q.isNotBlank()) onPick(q.trim()) else onClose() }) { Text("Готово") } })
}

private suspend fun speedTest(app: App, s: Settings): String {
    val r = GenRequest("Ты рассказчик.", listOf(Turn(true, "Опиши маяк в шторм в пяти предложениях.")), 150, 0.7f, 0.9f, 1.0f)
    suspend fun run(gpu: Boolean): String = runCatching {
        var first = 0L; var chars = 0
        app.engines.local.generate(s, r, gpu).collect { if (first == 0L) first = System.currentTimeMillis(); chars += it.length }
        val sec = (System.currentTimeMillis() - first).coerceAtLeast(1) / 1000f
        String.format(java.util.Locale.US, "%.0f симв/с", chars / sec)
    }.getOrElse { "ошибка" }
    val cpu = run(false)
    val gpu = run(true)
    app.engines.local.unload()
    return "CPU: $cpu · GPU: $gpu"
}

@Suppress("unused") private val keepMemoryIcon = Icons.Rounded.Memory
