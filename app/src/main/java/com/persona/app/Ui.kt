package com.persona.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.withContext

/** Готовые акцентные цвета: название, для тёмной темы, для светлой. */
data class AccentPreset(val title: String, val dark: Color, val light: Color)
val ACCENTS = listOf(
    AccentPreset("Фиолетовый", Color(0xFFC9B6FF), Color(0xFF6B4BC9)),
    AccentPreset("Бирюзовый", Color(0xFF7FDBCA), Color(0xFF006A60)),
    AccentPreset("Розовый", Color(0xFFFFB1C8), Color(0xFF9C3D62)),
    AccentPreset("Янтарный", Color(0xFFF2C27B), Color(0xFF7A5A00)),
    AccentPreset("Зелёный", Color(0xFFA6D785), Color(0xFF3B6A1F)),
    AccentPreset("Синий", Color(0xFFA8C7FF), Color(0xFF2E5EA8)),
)

val LocalLook = androidx.compose.runtime.staticCompositionLocalOf { Appearance() }
val LocalDark = androidx.compose.runtime.staticCompositionLocalOf { true }

val Accent: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val Amber: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
val Mint: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() = if (LocalDark.current) Color(0xFF8FE3B0) else Color(0xFF1E7A4A)
val BubbleChar: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() =
    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = LocalLook.current.bubbleAlpha.coerceIn(0.3f, 1f))

val PersonaColors = darkColorScheme(
    primary = Color(0xFFC9B6FF), onPrimary = Color(0xFF24124F),
    primaryContainer = Color(0xFF4A3D78), onPrimaryContainer = Color(0xFFF3EEFF),
    secondary = Color(0xFFCDC2DB), onSecondary = Color(0xFF342D40),
    secondaryContainer = Color(0xFF2A2340), onSecondaryContainer = Color(0xFFE9DEF8),
    tertiary = Color(0xFFF2C27B), onTertiary = Color(0xFF3F2E00),
    background = Color(0xFF14121A), onBackground = Color(0xFFECE6F3),
    surface = Color(0xFF14121A), onSurface = Color(0xFFECE6F3),
    surfaceVariant = Color(0xFF2A2634), onSurfaceVariant = Color(0xFFB3ABBF),
    surfaceContainerLowest = Color(0xFF0F0D14), surfaceContainerLow = Color(0xFF1A1722),
    surfaceContainer = Color(0xFF1E1B26), surfaceContainerHigh = Color(0xFF2A2634), surfaceContainerHighest = Color(0xFF3A3448),
    outline = Color(0xFF6E6680), outlineVariant = Color(0xFF3A3448),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
)

private fun lerpC(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

fun schemeFor(look: Appearance, dark: Boolean, ctx: android.content.Context): androidx.compose.material3.ColorScheme {
    if (look.accent == -1 && android.os.Build.VERSION.SDK_INT >= 31) {
        val d = if (dark) androidx.compose.material3.dynamicDarkColorScheme(ctx) else androidx.compose.material3.dynamicLightColorScheme(ctx)
        return if (dark && look.theme == ThemeMode.AMOLED) d.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black) else d
    }
    val a = ACCENTS.getOrElse(look.accent) { ACCENTS[0] }
    return if (dark) {
        val p = a.dark
        val bg = if (look.theme == ThemeMode.AMOLED) Color.Black else Color(0xFF14121A)
        PersonaColors.copy(
            primary = p, onPrimary = lerpC(p, Color.Black, 0.78f),
            primaryContainer = lerpC(p, bg, 0.62f), onPrimaryContainer = lerpC(p, Color.White, 0.75f),
            secondaryContainer = lerpC(p, bg, 0.84f), onSecondaryContainer = lerpC(p, Color.White, 0.8f),
            background = bg, surface = bg,
            surfaceContainerLowest = if (look.theme == ThemeMode.AMOLED) Color.Black else PersonaColors.surfaceContainerLowest,
            surfaceContainerLow = if (look.theme == ThemeMode.AMOLED) Color(0xFF0A0A0C) else PersonaColors.surfaceContainerLow,
            surfaceContainer = if (look.theme == ThemeMode.AMOLED) Color(0xFF121214) else PersonaColors.surfaceContainer,
            surfaceContainerHigh = if (look.theme == ThemeMode.AMOLED) Color(0xFF1C1C20) else PersonaColors.surfaceContainerHigh,
        )
    } else {
        val p = a.light
        androidx.compose.material3.lightColorScheme(
            primary = p, onPrimary = Color.White,
            primaryContainer = lerpC(p, Color.White, 0.78f), onPrimaryContainer = lerpC(p, Color.Black, 0.55f),
            secondaryContainer = lerpC(p, Color.White, 0.88f), onSecondaryContainer = lerpC(p, Color.Black, 0.6f),
            tertiary = Color(0xFF8A5A00), onTertiary = Color.White,
            background = Color(0xFFFCF8FD), surface = Color(0xFFFCF8FD), onBackground = Color(0xFF1C1A20), onSurface = Color(0xFF1C1A20),
            surfaceVariant = Color(0xFFE8E0EC), onSurfaceVariant = Color(0xFF4E4757),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F2F8),
            surfaceContainer = Color(0xFFF0EBF3), surfaceContainerHigh = Color(0xFFEAE5ED), surfaceContainerHighest = Color(0xFFE4DFE8),
            outline = Color(0xFF7C7486), outlineVariant = Color(0xFFCCC4D2),
        )
    }
}

private fun serifTypography(): androidx.compose.material3.Typography {
    val t = androidx.compose.material3.Typography()
    val f = androidx.compose.ui.text.font.FontFamily.Serif
    return t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = f), displayMedium = t.displayMedium.copy(fontFamily = f), displaySmall = t.displaySmall.copy(fontFamily = f),
        headlineLarge = t.headlineLarge.copy(fontFamily = f), headlineMedium = t.headlineMedium.copy(fontFamily = f), headlineSmall = t.headlineSmall.copy(fontFamily = f),
        titleLarge = t.titleLarge.copy(fontFamily = f), titleMedium = t.titleMedium.copy(fontFamily = f), titleSmall = t.titleSmall.copy(fontFamily = f),
        bodyLarge = t.bodyLarge.copy(fontFamily = f), bodyMedium = t.bodyMedium.copy(fontFamily = f), bodySmall = t.bodySmall.copy(fontFamily = f),
        labelLarge = t.labelLarge.copy(fontFamily = f), labelMedium = t.labelMedium.copy(fontFamily = f), labelSmall = t.labelSmall.copy(fontFamily = f),
    )
}

@Composable
fun isDarkLook(look: Appearance) = when (look.theme) {
    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    else -> true
}

@Composable
fun PersonaTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val st by ctx.app.repo.settings.collectAsStateWithLifecycle()
    val look = st.look
    val dark = isDarkLook(look)
    val scheme = androidx.compose.runtime.remember(look, dark) { schemeFor(look, dark, ctx) }
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLook provides look, LocalDark provides dark,
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * look.textScale.coerceIn(0.8f, 1.4f)),
    ) {
        MaterialTheme(colorScheme = scheme, typography = if (look.serif) serifTypography() else androidx.compose.material3.Typography(), content = content)
    }
}


private val avatarColors = listOf(0xFF6B4BC9, 0xFF2F6B5A, 0xFF7A3B4E, 0xFF3B4E7A, 0xFF7A5A2F, 0xFF5A2F7A, 0xFF2F5F7A).map { Color(it) }

@Composable
fun rememberAvatar(name: String?): ImageBitmap? {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) {
            ctx.app.repo.avatarFile(name)?.let { f -> BitmapFactory.decodeFile(f.path)?.asImageBitmap() }
        }
    }
    return bmp
}

@Composable
fun Avatar(c: Character?, size: Dp, shape: Shape? = null, modifier: Modifier = Modifier, emotion: String? = null) {
    val shape = shape ?: if (LocalLook.current.squareAvatars) RoundedCornerShape(size * 0.28f) else CircleShape
    val img = rememberAvatar(emotion?.let { e -> c?.emotions?.get(e) } ?: c?.avatar)
    val name = c?.name.orEmpty()
    Box(
        modifier.size(size).clip(shape).background(if (img != null) Color.Transparent else avatarColors[(name.hashCode() and 0x7fffffff) % avatarColors.size]),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) Image(img, null, Modifier.size(size), contentScale = ContentScale.Crop)
        else Text(name.take(1).uppercase().ifBlank { "?" }, color = Color(0xFFF3EEFF), fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.4f).sp)
    }
}

/** Текст реплики: *действия* — курсивом и цветом. */
fun rpText(t: String, actionColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < t.length) {
        val start = t.indexOf('*', i)
        if (start < 0) { append(t.substring(i)); break }
        val end = t.indexOf('*', start + 1)
        if (end < 0) { append(t.substring(i)); break }
        append(t.substring(i, start))
        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = actionColor)) { append(t.substring(start + 1, end)) }
        i = end + 1
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = Text(
    text.uppercase(), modifier.padding(top = 8.dp, start = 4.dp), color = MaterialTheme.colorScheme.primary,
    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp,
)

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Surface(
    modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, content = content,
)

@Composable
fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChange)
    }
}

@Composable
fun NavRow(title: String, sub: String?, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
        }
        trailing?.invoke()
    }
}

fun timeLabel(t: Long): String {
    val now = java.util.Calendar.getInstance()
    val c = java.util.Calendar.getInstance().apply { timeInMillis = t }
    val sameDay = now.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR) && now.get(java.util.Calendar.DAY_OF_YEAR) == c.get(java.util.Calendar.DAY_OF_YEAR)
    val f = if (sameDay) "HH:mm" else "d MMM"
    return java.text.SimpleDateFormat(f, java.util.Locale("ru")).format(java.util.Date(t))
}

fun bytesLabel(b: Long): String = if (b >= 1_000_000_000) String.format(java.util.Locale.US, "%.1f ГБ", b / 1e9).replace('.', ',') else "${b / 1_000_000} МБ"

/** Картинка из папки images: превью в чате, по нажатию — во весь экран. */
@Composable
fun MsgImage(name: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var full by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val bmp by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) { ctx.app.repo.imageFile(name)?.let { f -> BitmapFactory.decodeFile(f.path)?.asImageBitmap() } }
    }
    val b = bmp
    if (b == null) Box(modifier.size(220.dp, 160.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
    else Image(b, null, modifier.widthIn(max = 280.dp).heightIn(max = 360.dp).clip(RoundedCornerShape(16.dp)).clickable { full = true }, contentScale = ContentScale.Fit)
    if (full && b != null) androidx.compose.ui.window.Dialog({ full = false }, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable { full = false }, contentAlignment = Alignment.Center) {
            Image(b, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
        }
    }
}

/** Голосовой ввод через системное распознавание речи. */
@Composable
fun rememberVoiceInput(onText: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
    }
    return {
        val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Говори…")
        runCatching { launcher.launch(i) }.onFailure {
            android.widget.Toast.makeText(ctx, "На телефоне нет распознавания речи (нужно приложение Google)", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

/** Заметка автора: скрытая подсказка модели на ближайшие ответы. */
@Composable
fun AuthorNoteDialog(note: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var t by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(note) }
    androidx.compose.material3.AlertDialog(onClose,
        title = { Text("Заметка автора") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Скрытая подсказка модели: что сейчас важно в сцене. Персонаж её учитывает, но не цитирует.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.OutlinedTextField(t, { t = it.take(500) }, Modifier.fillMaxWidth(), minLines = 3,
                    placeholder = { Text("Например: сейчас глубокая ночь, она устала и хочет домой") })
            }
        },
        confirmButton = { androidx.compose.material3.TextButton({ onSave(t); onClose() }) { Text("Сохранить") } },
        dismissButton = { androidx.compose.material3.TextButton({ onSave(""); onClose() }) { Text("Убрать") } })
}


val REACTIONS = listOf("❤️", "😂", "😮", "😢", "😡", "👍")

/** Анимированные точки «печатает». */
@Composable
fun TypingDots(color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        repeat(3) { i ->
            val y by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.keyframes { durationMillis = 900; 0f at 0 + i * 150; 1f at 200 + i * 150; 0f at 450 + i * 150 }), label = "d$i")
            Box(Modifier.size(8.dp).graphicsLayer { translationY = -y * 5f * density }.clip(CircleShape).background(color.copy(alpha = 0.5f + y * 0.5f)))
        }
    }
}
