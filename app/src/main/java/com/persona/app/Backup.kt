package com.persona.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Автобэкап: по расписанию сохраняет копию в «Загрузки/Персона» или выбранную папку и удаляет лишние старые. */
object AutoBackup {
    const val PREFIX = "persona-auto-"
    private const val SUBDIR = "Персона"

    fun due(c: AutoBackupCfg, now: Long = System.currentTimeMillis()) = c.freq != BackupFreq.OFF && now - c.last >= c.freq.ms - 3_600_000L

    suspend fun run(ctx: Context): Result<String> = withContext(Dispatchers.IO) {
        val repo = ctx.app.repo
        val cfg = repo.settings.value.autoBackup
        val name = PREFIX + java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".zip"
        val r = runCatching {
            val tree = cfg.folderUri?.let(Uri::parse)
            if (tree != null) {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val doc = DocumentsContract.createDocument(ctx.contentResolver, parent, "application/zip", name) ?: error("Не удалось создать файл в папке")
                ctx.contentResolver.openOutputStream(doc)!!.use { repo.exportBackup(it) }
                cleanupTree(ctx, tree, cfg.keep)
                "${cfg.folderName ?: "выбранная папка"}/$name"
            } else if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + SUBDIR)
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: error("Не удалось создать файл в Загрузках")
                ctx.contentResolver.openOutputStream(uri)!!.use { repo.exportBackup(it) }
                cleanupDownloads(ctx, cfg.keep)
                "Загрузки/$SUBDIR/$name"
            } else {
                val dir = File(ctx.getExternalFilesDir(null), "backups").apply { mkdirs() }
                File(dir, name).outputStream().use { repo.exportBackup(it) }
                dir.listFiles()?.filter { it.name.startsWith(PREFIX) }?.sortedByDescending { it.name }?.drop(cfg.keep)?.forEach { it.delete() }
                "${dir.path}/$name"
            }
        }
        repo.settings { it.copy(autoBackup = it.autoBackup.copy(last = if (r.isSuccess) System.currentTimeMillis() else it.autoBackup.last,
            lastError = r.exceptionOrNull()?.let { e -> e.message ?: e.toString() })) }
        r.onFailure { ErrorLog.add("auto-backup", it) }
    }

    private fun cleanupDownloads(ctx: Context, keep: Int) {
        if (Build.VERSION.SDK_INT < 29) return
        val items = mutableListOf<Pair<Long, String>>()
        ctx.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?", arrayOf("%$SUBDIR%", "$PREFIX%"), null)?.use { c ->
            while (c.moveToNext()) items += c.getLong(0) to c.getString(1)
        }
        items.sortedByDescending { it.second }.drop(keep).forEach { (id, _) ->
            runCatching { ctx.contentResolver.delete(android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id), null, null) }
        }
    }

    private fun cleanupTree(ctx: Context, tree: Uri, keep: Int) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val items = mutableListOf<Pair<String, String>>()
        ctx.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) { val n = c.getString(1) ?: continue; if (n.startsWith(PREFIX)) items += c.getString(0) to n }
        }
        items.sortedByDescending { it.second }.drop(keep).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }
}

class AutoBackupWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val cfg = applicationContext.app.repo.settings.value.autoBackup
        if (AutoBackup.due(cfg)) AutoBackup.run(applicationContext)
        return Result.success()
    }
}

/** Проверка новых версий в релизах GitHub. */
object Updates {
    fun current(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

    /** 1, если a новее b. */
    fun compare(a: String, b: String): Int {
        val pa = a.trimStart('v', 'V').split('.', '-').map { it.toIntOrNull() ?: 0 }
        val pb = b.trimStart('v', 'V').split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    fun hasNew(ctx: Context, u: UpdateInfo) = u.latest != null && compare(u.latest, current(ctx)) > 0

    fun versionCode(ctx: Context): Long = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }.getOrDefault(0)

    private fun get(url: String): Pair<Int, String?> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json"); c.connectTimeout = 15_000; c.readTimeout = 20_000
        c.useCaches = false
        return try { c.responseCode to if (c.responseCode in 200..299) c.inputStream.bufferedReader().readText() else null } finally { c.disconnect() }
    }

    /** Кандидат на обновление из одного источника. */
    private data class Found(val version: String, val apk: String?, val notes: String?, val page: String?)

    /** Ветка updates: update.json + APK рядом. Её публикует сборка без доступа к релизам. */
    private fun fromBranch(r: String): Found? {
        val base = "https://raw.githubusercontent.com/$r/updates/"
        val (code, body) = get(base + "update.json?t=${System.currentTimeMillis() / 60000}")
        if (code != 200 || body == null) return null
        val o = json.parseToJsonElement(body).jsonObject
        val v = (o["versionName"] as? JsonPrimitive)?.content ?: return null
        val apk = (o["apk"] as? JsonPrimitive)?.content?.let { if (it.startsWith("http")) it else base + it }
        return Found(v.trimStart('v', 'V'), apk, (o["notes"] as? JsonPrimitive)?.content, "https://github.com/$r/tree/updates")
    }

    /** Обычный релиз GitHub с приложенным APK. */
    private fun fromRelease(r: String): Found? {
        val (code, body) = get("https://api.github.com/repos/$r/releases/latest")
        if (code != 200 || body == null) return null
        val o = json.parseToJsonElement(body).jsonObject
        val tag = (o["tag_name"] as? JsonPrimitive)?.content ?: return null
        val apk = (o["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.firstOrNull { (it["name"] as? JsonPrimitive)?.content?.endsWith(".apk") == true }
            ?.let { (it["browser_download_url"] as? JsonPrimitive)?.content }
        return Found(tag.trimStart('v', 'V'), apk, (o["body"] as? JsonPrimitive)?.content, (o["html_url"] as? JsonPrimitive)?.content)
    }

    suspend fun check(ctx: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        val repo = ctx.app.repo
        val r = repo.settings.value.updates.repo.trim().removePrefix("https://github.com/").trim('/')
        runCatching {
            if (!Regex("^[\\w.-]+/[\\w.-]+$").matches(r)) throw IllegalArgumentException("Укажи репозиторий в виде «владелец/название»")
            val found = listOfNotNull(runCatching { fromBranch(r) }.getOrNull(), runCatching { fromRelease(r) }.getOrNull())
                .filter { it.apk != null }
                .maxWithOrNull { a, b -> compare(a.version, b.version) }
                ?: throw IllegalStateException("В репозитории $r пока нет опубликованных версий")
            repo.settings.value.updates.copy(repo = r, lastCheck = System.currentTimeMillis(), latest = found.version, url = found.apk,
                notes = found.notes?.take(800), page = found.page)
        }.onSuccess { info -> repo.settings { it.copy(updates = info) } }
    }
}
