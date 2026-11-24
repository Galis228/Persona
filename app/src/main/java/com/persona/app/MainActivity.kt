package com.persona.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle

sealed interface Route {
    data object Home : Route
    data class Editor(val charId: String?) : Route
    data class ChatR(val chatId: String, val focus: String? = null) : Route
    data class Memory(val chatId: String) : Route
    data class Lore(val bookId: String) : Route
    data class SettingsPage(val page: String) : Route
    data class SceneEditor(val sceneId: String?) : Route
    data class Call(val chatId: String) : Route
    data object Search : Route
    data class Book(val chatId: String) : Route
}

class Nav {
    val stack = mutableStateListOf<Route>(Route.Home)
    val top get() = stack.last()
    fun go(r: Route) { stack.add(r) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replace(r: Route) { stack[stack.lastIndex] = r }
}

class MainActivity : FragmentActivity() {
    private val pendingChat = mutableStateOf<String?>(null)
    private val pendingUpdate = mutableStateOf(false)
    private val locked = mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingChat.value = intent?.getStringExtra("chat")
        pendingUpdate.value = intent?.getBooleanExtra("install_update", false) == true
        locked.value = app.repo.settings.value.lockEnabled
        setContent { PersonaTheme { Root() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("chat")?.let { pendingChat.value = it }
        if (intent.getBooleanExtra("install_update", false)) pendingUpdate.value = true
    }

    override fun onStop() { super.onStop(); stoppedAt = SystemClock.elapsedRealtime() }

    override fun onStart() {
        super.onStart()
        val s = app.repo.settings.value
        if (s.lockEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Блокируем снова, если приложение было свёрнуто дольше 30 секунд
        if (s.lockEnabled && stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > 30_000) locked.value = true
    }

    fun authenticate(title: String = "Персона", onOk: () -> Unit) {
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle("Подтверди, что это ты").setAllowedAuthenticators(auth).build())
    }

    fun canLock(): Boolean = BiometricManager.from(this).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    @Composable
    private fun Root() {
        val nav = remember { Nav() }
        val isLocked by locked
        val s by app.repo.settings.collectAsStateWithLifecycle()
        val dark = LocalDark.current
        val bg = MaterialTheme.colorScheme.background
        LaunchedEffect(dark, bg) {
            val t = android.graphics.Color.TRANSPARENT
            enableEdgeToEdge(if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t), if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t))
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bg.toArgb()))
        }
        LaunchedEffect(Unit) { DownloadWatch.loop(this@MainActivity) }
        LaunchedEffect(isLocked) { if (isLocked) authenticate { locked.value = false } }
        LaunchedEffect(s.lockEnabled) { if (!s.lockEnabled) locked.value = false }
        if (isLocked) { LockScreen { authenticate { locked.value = false } }; return }
        LaunchedEffect(pendingChat.value) {
            pendingChat.value?.let { id ->
                if (app.repo.chat(id) != null) { nav.stack.clear(); nav.stack.add(Route.Home); nav.go(Route.ChatR(id)) }
                pendingChat.value = null
            }
        }
        LaunchedEffect(pendingUpdate.value) {
            if (pendingUpdate.value) { UpdateRequest.installNow = true; nav.go(Route.SettingsPage("updates")); pendingUpdate.value = false }
        }
        BackHandler(nav.stack.size > 1) { nav.back() }
        when (val r = nav.top) {
            Route.Home -> HomeScreen(nav)
            is Route.Editor -> EditorScreen(nav, r.charId)
            is Route.ChatR -> if (app.repo.chat(r.chatId)?.sceneId != null) SceneChatScreen(nav, r.chatId, r.focus) else ChatScreen(nav, r.chatId, r.focus)
            is Route.Memory -> MemoryScreen(nav, r.chatId)
            is Route.Lore -> LoreScreen(nav, r.bookId)
            is Route.SettingsPage -> SettingsPageScreen(nav, r.page)
            is Route.SceneEditor -> SceneEditorScreen(nav, r.sceneId)
            is Route.Call -> CallScreen(nav, r.chatId)
            Route.Search -> SearchScreen(nav)
            is Route.Book -> BookScreen(nav, r.chatId)
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.Fingerprint, null, Modifier.padding(8.dp), tint = Accent)
            Text("Персона заблокирована", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Приложи палец или введи код телефона", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(onUnlock) { Text("Разблокировать") }
        }
    }
}
