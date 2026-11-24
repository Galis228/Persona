package com.persona.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Обновление «Персоны» прямо из релизов GitHub: скачать APK, проверить, установить. */
object Updater {
    sealed interface State {
        data object Idle : State
        data class Downloading(val progress: Float) : State
        data class Ready(val version: String) : State
        data object Installing : State
        data class Failed(val message: String) : State
    }

    class NeedPermission : Exception("Нужно разрешить Персоне устанавливать приложения")

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private val lock = Mutex()

    private fun apkFile(ctx: Context) = File(File(ctx.cacheDir, "updates").apply { mkdirs() }, "persona-update.apk")

    /** Можно ли поставить без подтверждения: Android 12+ и прошлое обновление ставила сама Персона. */
    fun canSilent(ctx: Context): Boolean = Build.VERSION.SDK_INT >= 31 && runCatching {
        ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName == ctx.packageName
    }.getOrDefault(false)

    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Скачивает APK последнего релиза и проверяет, что это та же программа и версия новее. */
    suspend fun download(ctx: Context): File = lock.withLock {
        withContext(Dispatchers.IO) {
            val u = ctx.app.repo.settings.value.updates
            val url = u.url ?: throw IllegalStateException("В последнем релизе нет файла APK")
            val f = apkFile(ctx)
            ready(ctx)?.let { return@withContext it }
            _state.value = State.Downloading(0f)
            try {
                var conn = URL(url).openConnection() as HttpURLConnection
                // GitHub отдаёт файл через перенаправление на другой сервер
                repeat(5) {
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 20_000; conn.readTimeout = 60_000
                    if (conn.responseCode in 300..399) {
                        val next = conn.getHeaderField("Location"); conn.disconnect()
                        conn = URL(URL(url), next).openConnection() as HttpURLConnection
                    } else return@repeat
                }
                if (conn.responseCode !in 200..299) throw IllegalStateException("GitHub ответил ${conn.responseCode}")
                val total = conn.contentLengthLong
                val tmp = File(f.path + ".part")
                conn.inputStream.use { inp ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(1 shl 16); var done = 0L
                        while (true) {
                            val n = inp.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            if (total > 0) _state.value = State.Downloading(done.toFloat() / total)
                        }
                    }
                }
                conn.disconnect()
                tmp.renameTo(f)
                ready(ctx) ?: run { f.delete(); throw IllegalStateException("Скачанный файл не подходит: это не новая версия Персоны") }
            } catch (e: Throwable) {
                _state.value = State.Failed(e.message ?: "Не удалось скачать обновление")
                ErrorLog.add("update-download", e)
                throw e
            }
        }
    }

    /** Уже скачанный подходящий APK, если есть. */
    fun ready(ctx: Context): File? {
        val f = apkFile(ctx)
        if (!f.exists()) return null
        val info = runCatching { ctx.packageManager.getPackageArchiveInfo(f.path, 0) }.getOrNull() ?: return null
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        if (info.packageName != ctx.packageName || code <= Updates.versionCode(ctx)) { f.delete(); return null }
        _state.value = State.Ready(info.versionName ?: "")
        return f
    }

    /** Передаёт APK системному установщику. На Android 12+ после первого обновления ставится без лишних вопросов. */
    fun install(ctx: Context, file: File) {
        if (!canInstall(ctx)) throw NeedPermission()
        _state.value = State.Installing
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { session ->
            session.openWrite("persona.apk", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; session.fsync(out) }
            val intent = PendingIntent.getBroadcast(ctx, 4401, Intent(ctx, InstallReceiver::class.java).setPackage(ctx.packageName),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(intent.intentSender)
        }
    }

    fun fail(msg: String) { _state.value = State.Failed(msg) }
    fun reset() { _state.value = State.Idle }

    fun notify(ctx: Context, title: String, text: String) {
        if (!Notifier.canPost(ctx)) return
        val open = PendingIntent.getActivity(ctx, 4402, Intent(ctx, MainActivity::class.java).putExtra("install_update", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, "messages").setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true).build()
        ctx.getSystemService(NotificationManager::class.java).notify(4400, n)
    }
}

/** Ответ системного установщика. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (foreground) runCatching { ctx.startActivity(confirm) }.onFailure { Updater.notify(ctx, "Обновление Персоны готово", "Нажми, чтобы установить") }
                else Updater.notify(ctx, "Обновление Персоны готово", "Нажми, чтобы установить")
                Updater.reset()
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.reset()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "код $status"
                val text = if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || msg.contains("signature", true))
                    "Обновление подписано другим ключом — его можно поставить только после удаления старой версии." else "Установка не удалась: $msg"
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED) ErrorLog.add("update-install", Exception(msg))
                Updater.fail(if (status == PackageInstaller.STATUS_FAILURE_ABORTED) "Установка отменена" else text)
            }
        }
    }
}

/** После обновления — короткое уведомление «Персона обновлена». */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        runCatching { Updater.notify(ctx, "Персона обновлена", "Версия ${Updates.current(ctx)} установлена") }
    }
}

/** Раз в полдня: проверить релиз, скачать и (если можно) поставить без вопросов, иначе — уведомить. */
class UpdateWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val u = ctx.app.repo.settings.value.updates
        if (!u.auto || u.repo.isBlank()) return Result.success()
        val info = Updates.check(ctx).getOrNull() ?: return Result.success()
        if (!Updates.hasNew(ctx, info) || info.url == null) return Result.success()
        val file = runCatching { Updater.download(ctx) }.getOrNull() ?: return Result.success()
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (Updater.canSilent(ctx) && !foreground) runCatching { Updater.install(ctx, file) }
        else Updater.notify(ctx, "Доступна Персона ${info.latest}", "Обновление скачано — нажми, чтобы установить")
        return Result.success()
    }
}
