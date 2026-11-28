package com.persona.app

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** Как сервис выглядит в списке подключения. */
private data class ProviderCard(val p: Provider, val letter: String, val color: Color, val sub: String, val badge: String?)

private val CARDS = listOf(
    ProviderCard(Provider.OPENROUTER, "OR", Color(0xFF6C5CE7), "Сотни моделей по одному ключу, есть бесплатные. Вход в один тап.", "Проще всего"),
    ProviderCard(Provider.OPENAI, "AI", Color(0xFF10A37F), "GPT-4o mini и другие GPT. Нужна предоплата от \$5.", null),
    ProviderCard(Provider.GEMINI, "G", Color(0xFF4285F4), "Модели Google. Бесплатный лимит без карты.", "Бесплатно"),
    ProviderCard(Provider.DEEPSEEK, "DS", Color(0xFF4D6BFE), "Очень дешёвые и хорошие модели, кэш сам по себе.", "Копейки"),
    ProviderCard(Provider.CLAUDE, "C", Color(0xFFD97757), "Лучше всех держит роль и пишет, но дороже.", "Качество"),
    ProviderCard(Provider.CUSTOM, "PC", Color(0xFF7A7A85), "LM Studio, Ollama на компьютере в той же сети.", null),
)

private fun Settings.perReplyLabel(model: String = apiModel): String? {
    val p = KnownModels.price(model) ?: prices[model] ?: return null
    val one = KnownModels.perReply(p, this)
    return if (one == 0.0) "бесплатно" else "ответ ≈ ${KnownModels.money(one)} · на \$10 ${KnownModels.repliesLabel(10 / one)} ответов"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiSetupPage() {
    val ctx = LocalContext.current
    val app = ctx.app
    val repo = app.repo
    val s by repo.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val connected = app.engines.api.ready(s)
    var open by remember { mutableStateOf(if (connected) null else Provider.OPENROUTER) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var pickModel by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf<Connector.Result?>(null) }
    var balance by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(s.apiKeyEnc, s.provider) { balance = if (connected) app.engines.api.balance(s) else null }

    fun runTest(model: String? = null) {
        testing = true; test = null
        scope.launch { test = Connector.test(ctx, model); testing = false }
    }

    if (!connected) Text("Нейросеть в интернете умнее модели на телефоне и не тратит батарею. Нужен ключ — это как пароль от сервиса. Выбери сервис, вставь ключ, а приложение само всё проверит и подберёт модель.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    // ---------- Статус ----------
    if (connected) Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Mint.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = Mint) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Подключено: ${s.provider.title}", fontWeight = FontWeight.Bold)
                    Text(KnownModels.title(s.apiModel), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    if (KnownModels.title(s.apiModel) != s.apiModel) Text(s.apiModel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            KnownModels.info(s.apiModel)?.note?.takeIf { it.isNotBlank() }?.let { n ->
                Text(n + if (n.contains("отключит")) ". Потом просто выбери другую модель." else "", style = MaterialTheme.typography.bodySmall,
                    color = if (n.contains("отключит")) Amber else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.perReplyLabel()?.let { Text("Цена: $it", style = MaterialTheme.typography.bodySmall) }
            balance?.let { b -> Text("Баланс: " + KnownModels.money(b).replace("бесплатно", "\$0"), style = MaterialTheme.typography.bodySmall, color = if (b < 0.1) MaterialTheme.colorScheme.error else Mint) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ pickModel = true }, Modifier.weight(1f)) { Text("Сменить модель") }
                OutlinedButton({ runTest() }, Modifier.weight(1f), enabled = !testing) {
                    if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Проверить")
                }
            }
            test?.let { ResultLine(it) }
        }
    }

    // ---------- Выбор сервиса ----------
    SectionTitle(if (connected) "Подключить другой сервис" else "Выбери сервис")
    CARDS.forEach { card ->
        val selected = open == card.p
        val isCurrent = connected && s.provider == card.p
        Surface(shape = RoundedCornerShape(20.dp), color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier)) {
            Column {
                Row(Modifier.fillMaxWidth().clickable { open = if (selected) null else card.p }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(card.color), contentAlignment = Alignment.Center) {
                        Text(card.letter, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(card.p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            card.badge?.let { Spacer(Modifier.width(8.dp)); Pill(it) }
                            if (isCurrent) { Spacer(Modifier.width(6.dp)); Pill("сейчас", Mint) }
                        }
                        Text(card.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AnimatedVisibility(selected) {
                    ConnectForm(card.p) { r -> if (r.ok) { models = r.models; open = null; test = r } }
                }
            }
        }
    }

    // ---------- Экономия ----------
    SectionTitle("Экономия токенов")
    CardBox {
        Column {
            SwitchRow("Кэш промпта", "Персонаж, правила и начало переписки идут первыми и не меняются — OpenAI, DeepSeek и Gemini берут за них в 2–10 раз дешевле, Claude — на 90% дешевле.",
                s.promptCache) { v -> repo.settings { it.copy(promptCache = v) } }
            HorizontalDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Savings, null, tint = Accent); Spacer(Modifier.width(8.dp))
                    Text("Сколько переписки отправлять", fontWeight = FontWeight.SemiBold)
                }
                val opts = listOf(6000 to "Мало", 12000 to "Обычно", 24000 to "Много")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    opts.forEachIndexed { i, (v, t) ->
                        SegmentedButton(s.apiContext == v, { repo.settings { it.copy(apiContext = v) } }, SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(t) }
                    }
                }
                Text("Каждый раз модели заново отправляется недавняя переписка. Старое не теряется — оно хранится в сжатой памяти. Меньше — дешевле, но мелкие детали из прошлого персонаж вспомнит хуже.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (connected) s.perReplyLabel()?.let { Text("С этими настройками: $it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Accent) }
            }
            HorizontalDivider()
            SwitchRow("Исправлять ошибки", "Каждый ответ перечитывается ещё одним запросом — тратит примерно вдвое больше. Для GPT, Claude и DeepSeek обычно не нужно.",
                s.proofread) { v -> repo.settings { it.copy(proofread = v) } }
        }
    }
    Text("Ещё дешевле: короткая длина ответа в «Стиль ответов» и поменьше персонажей в общих чатах — там отвечает каждый.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    // ---------- Картинки ----------
    SectionTitle("Картинки сцен")
    ImageModelBox(s, models)

    if (connected) TextButton({ repo.settings { it.copy(apiKeyEnc = "", apiModel = "") }; test = null; OpenRouterAuth.reset(); open = Provider.OPENROUTER }) {
        Text("Отключить API", color = MaterialTheme.colorScheme.error)
    }

    if (pickModel) ModelSheet(s.provider, models, s.apiModel, onLoaded = { models = it }, onClose = { pickModel = false }) { m ->
        repo.settings { it.copy(apiModel = m) }
        pickModel = false
        runTest(m)
    }
}

@Composable
private fun Pill(text: String, color: Color = Accent) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.18f)) {
        Text(text, Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ResultLine(r: Connector.Result) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.Top) {
        Icon(if (r.ok) Icons.Rounded.Check else Icons.Rounded.ErrorOutline, null, Modifier.size(20.dp), tint = if (r.ok) Mint else MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(r.message, style = MaterialTheme.typography.bodyMedium, color = if (r.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
            r.url?.let { u -> TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }) { Text("Открыть, чтобы исправить") } }
        }
    }
}

/** Пошаговое подключение одного сервиса. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectForm(p: Provider, onDone: (Connector.Result) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.app
    val repo = app.repo
    val s by repo.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val auth by OpenRouterAuth.state.collectAsStateWithLifecycle()
    var key by remember(p) { mutableStateOf(if (s.provider == p) KeyCrypto.decrypt(s.apiKeyEnc) else "") }
    var url by remember(p) { mutableStateOf(s.baseUrl) }
    var want by remember(p) { mutableStateOf(if (s.provider == p) s.apiModel else "") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember(p) { mutableStateOf<Connector.Result?>(null) }
    val other = KeyLinks.detect(KeyLinks.clean(key))?.takeIf { it != p && p != Provider.CUSTOM }

    fun connect(provider: Provider = p) {
        busy = true; result = null
        scope.launch {
            val r = Connector.connect(ctx, provider, key, if (provider == Provider.CUSTOM) url.trim() else null, want.takeIf { it.isNotBlank() })
            result = r; busy = false
            if (r.ok) onDone(r)
        }
    }
    LaunchedEffect(auth) { (auth as? OpenRouterAuth.State.Done)?.let { onDone(Connector.Result(true, it.message)) } }

    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Шаги
        KeyLinks.steps(p).forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
        val site = KeyLinks.url(p); val bill = KeyLinks.billing(p)
        if (site != null || bill != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            site?.let { u -> OutlinedButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }, Modifier.weight(1f)) { Text("Получить ключ") } }
            if (bill != null && p != Provider.OPENROUTER) OutlinedButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(bill))) }, Modifier.weight(1f)) { Text("Пополнить") }
        }

        if (p == Provider.OPENROUTER) {
            when (val a = auth) {
                OpenRouterAuth.State.Waiting -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp))
                    Text("Жду входа в браузере… Потом нажми «Вернуться в Персону».", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton({ OpenRouterAuth.reset() }) { Text("Отмена") }
                }
                OpenRouterAuth.State.Exchanging -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Получаю ключ и проверяю…")
                }
                is OpenRouterAuth.State.Failed -> Text(a.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                else -> {}
            }
            Button({ OpenRouterAuth.start(ctx) }, Modifier.fillMaxWidth().height(50.dp),
                enabled = auth != OpenRouterAuth.State.Waiting && auth != OpenRouterAuth.State.Exchanging) {
                Icon(Icons.Rounded.Bolt, null); Spacer(Modifier.width(8.dp)); Text("Войти через OpenRouter")
            }
            Text("или вставь ключ вручную:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (p == Provider.CUSTOM) OutlinedTextField(url, { url = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
            label = { Text("Адрес сервера") }, placeholder = { Text("http://192.168.1.10:1234/v1") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))

        OutlinedTextField(key, { key = it; result = null }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
            label = { Text(if (p == Provider.CUSTOM) "Ключ (если нужен)" else "Ключ") }, placeholder = { Text(p.keyHint) },
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row {
                    IconButton({ show = !show }) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Показать ключ") }
                    IconButton({
                        val clip = ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                        key = KeyLinks.clean(clip); result = null
                    }) { Icon(Icons.Rounded.ContentPaste, "Вставить") }
                }
            },
            supportingText = { Text("Хранится зашифрованным только на этом телефоне") })
        other?.let { o ->
            Surface(shape = RoundedCornerShape(12.dp), color = Amber.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Похоже, это ключ ${o.title}, а не ${p.title}.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton({ connect(o) }) { Text("Подключить как ${o.title}") }
                }
            }
        }

        // Модель: можно сразу выбрать, а можно оставить пустым
        val featured = ModelPick.featured(p, emptyList()).filter { KnownModels.info(it) != null }
        if (featured.isNotEmpty()) {
            Text("Модель (можно не выбирать — подберу сам):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                featured.forEach { m -> FilterChip(ModelPick.matches(want, m) && want.isNotBlank(), { want = if (want == m) "" else m }, { Text(KnownModels.title(m)) }) }
            }
        }
        OutlinedTextField(want, { want = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
            label = { Text("Своя модель") }, placeholder = { Text(if (p == Provider.CUSTOM) "название модели на сервере" else "например: 4o mini") },
            supportingText = { Text("Можно писать как удобно — «4o mini», «gpt 4o-mini»: найду точное название сам") })

        Button({ connect() }, Modifier.fillMaxWidth().height(50.dp), enabled = !busy && (key.isNotBlank() || p == Provider.CUSTOM)) {
            if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(10.dp)); Text("Проверяю ключ и модель…") }
            else Text("Подключить")
        }
        result?.let { ResultLine(it) }
    }
}

/** Выбор модели: рекомендуемые с ценой и все остальные с поиском «как попало». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(p: Provider, models: List<String>, current: String, onLoaded: (List<String>) -> Unit, onClose: () -> Unit, onPick: (String) -> Unit) {
    val app = LocalContext.current.app
    val s by app.repo.settings.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(models.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (models.isEmpty()) runCatching { app.engines.api.listModels(app.repo.settings.value) }
            .onSuccess { onLoaded(it) }.onFailure { error = it.message }
        loading = false
    }
    val featured = ModelPick.featured(p, models)
    val all = models.filter { ModelPick.isChat(it) && it !in featured }
    val shown = if (q.isBlank()) all else (featured + all).filter { ModelPick.matches(q, it) }
    val guess = ModelPick.best(q, models)
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Модель", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Поиск: «4o mini», «haiku», «free»…") })
            if (loading) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Загружаю список…") }
            error?.let { Text("Не удалось загрузить список: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (q.isNotBlank() && (guess == null || !guess.equals(q.trim(), true))) TextButton({ onPick(guess ?: q.trim()) }) {
                Text(if (guess != null) "Выбрать: ${KnownModels.title(guess)}" else "Использовать «${q.trim()}» как есть")
            }
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                if (q.isBlank() && featured.isNotEmpty()) {
                    item { SectionTitle("Рекомендуем") }
                    items(featured) { m -> ModelRow(m, m == current, s) { onPick(m) } }
                    if (all.isNotEmpty()) item { SectionTitle("Все модели (${all.size})") }
                }
                items(shown.take(300)) { m -> ModelRow(m, m == current, s) { onPick(m) } }
            }
        }
    }
}

@Composable
private fun ModelRow(m: String, selected: Boolean, s: Settings, onClick: () -> Unit) {
    val info = KnownModels.info(m)
    val price = KnownModels.price(m)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(KnownModels.title(m), fontWeight = FontWeight.Bold)
                info?.tag?.takeIf { it.isNotBlank() }?.let { Spacer(Modifier.width(6.dp)); Pill(it) }
                if (m.endsWith(":free")) { Spacer(Modifier.width(6.dp)); Pill("0 \$", Mint) }
            }
            if (KnownModels.title(m) != m) Text(m, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            info?.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.contains("отключит")) Amber else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        price?.let { p ->
            val per100 = KnownModels.perReply(p, s) * 100
            Column(horizontalAlignment = Alignment.End) {
                Text(KnownModels.money(per100), fontWeight = FontWeight.Bold, color = if (per100 == 0.0) Mint else Accent)
                if (per100 > 0) Text("за 100 ответов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) { Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) }
    }
}

/** Модель для «Нарисовать сцену». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageModelBox(s: Settings, models: List<String>) {
    val repo = LocalContext.current.app.repo
    CardBox {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Кнопка «Нарисовать сцену» в чате превращает текущий момент в иллюстрацию тем же ключом.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.provider == Provider.CLAUDE || s.provider == Provider.DEEPSEEK) Text("${s.provider.title} не рисует картинки — для них нужен другой сервис.", style = MaterialTheme.typography.bodySmall, color = Amber)
            else {
                OutlinedTextField(s.imageModel, { v -> repo.settings { it.copy(imageModel = v.trim()) } }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                    label = { Text("Модель для картинок") }, placeholder = { Text(when (s.provider) {
                        Provider.OPENROUTER -> "например, google/gemini-2.5-flash-image"
                        Provider.OPENAI -> "например, gpt-image-1"
                        Provider.GEMINI -> "например, imagen-4.0-generate-001"
                        else -> "название модели"
                    }) })
                val img = models.filter { m -> listOf("image", "imagen", "dall-e", "flux", "diffusion").any { m.contains(it, true) } }.take(8)
                if (img.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    img.forEach { m -> FilterChip(s.imageModel == m, { repo.settings { it.copy(imageModel = m) } }, { Text(m.substringAfterLast('/'), maxLines = 1) }) }
                }
                Text("Картинки обычно платные: проверь цену модели у провайдера.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
