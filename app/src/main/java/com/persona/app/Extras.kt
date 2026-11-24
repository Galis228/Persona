package com.persona.app

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/** Уведомления от персонажей с кнопкой «Ответить» прямо из шторки. */
object Notifier {
    const val KEY_TEXT = "reply_text"

    fun canPost(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(ctx: Context, chat: Chat, c: Character, text: String, sending: String? = null) {
        if (!canPost(ctx)) return
        val id = chat.id.hashCode()
        val open = PendingIntent.getActivity(ctx, id,
            Intent(ctx, MainActivity::class.java).putExtra("chat", chat.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val replyIntent = PendingIntent.getBroadcast(ctx, id,
            Intent(ctx, ReplyReceiver::class.java).putExtra("chat", chat.id).setPackage(ctx.packageName),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val input = RemoteInput.Builder(KEY_TEXT).setLabel("Ответить ${c.name}").build()
        val action = NotificationCompat.Action.Builder(R.drawable.ic_notif, "Ответить", replyIntent).addRemoteInput(input).setAllowGeneratedReplies(true).build()
        val plain = text.replace(Regex("\\*[^*]*\\*"), "").replace(Regex("\\s+"), " ").trim().ifBlank { text }
        val n = NotificationCompat.Builder(ctx, "messages").setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(c.name)
            .setContentText(sending ?: plain.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(sending ?: plain.take(800)))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(sending != null)
            .addAction(action)
        ctx.app.repo.avatarFile(c.avatar)?.let { f -> BitmapFactory.decodeFile(f.path)?.let { n.setLargeIcon(circle(it)) } }
        ctx.getSystemService(NotificationManager::class.java).notify(id, n.build())
    }

    fun circle(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        cv.drawCircle(size / 2f, size / 2f, size / 2f, p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        cv.drawBitmap(src, Rect((src.width - size) / 2, (src.height - size) / 2, (src.width + size) / 2, (src.height + size) / 2), Rect(0, 0, size, size), p)
        return out
    }
}

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val chatId = intent.getStringExtra("chat") ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_TEXT)?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        val repo = ctx.app.repo
        val chat = repo.chat(chatId) ?: return
        val c = repo.char(chat.charId) ?: return
        Notifier.post(ctx, chat, c, "", sending = "Ты: $text\n${c.name} печатает…")
        WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<ReplyWorker>().setInputData(workDataOf("chat" to chatId, "text" to text)).build())
    }
}

class ReplyWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val app = applicationContext.app
        val chatId = inputData.getString("chat") ?: return Result.success()
        val text = inputData.getString("text") ?: return Result.success()
        val reply = runCatching { app.runner.sendAndWait(chatId, text) }.onFailure { ErrorLog.add("reply-notif", it) }.getOrNull()
        val chat = app.repo.chat(chatId) ?: return Result.success()
        val c = app.repo.char(chat.charId) ?: return Result.success()
        if (reply.isNullOrBlank()) Notifier.post(app, chat, c, "", sending = "Не получилось ответить. Открой чат, чтобы попробовать ещё раз.")
        else Notifier.post(app, chat, c, reply)
        return Result.success()
    }
}

/** Виджет: последнее сообщение из последнего чата и кнопка «Ответить». */
class ChatWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = render(ctx, mgr, ids)

    companion object {
        private var last = 0L
        fun refresh(ctx: Context) {
            val now = System.currentTimeMillis()
            if (now - last < 1500) return
            last = now
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ChatWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, mgr, ids)
        }

        fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val repo = ctx.app.repo
            val chat = repo.chats.value.filter { it.sceneId == null && !it.incognito && repo.char(it.charId) != null }.maxByOrNull { it.updated }
            val c = chat?.let { repo.char(it.charId) }
            val v = RemoteViews(ctx.packageName, R.layout.widget_chat)
            if (chat == null || c == null) {
                v.setTextViewText(R.id.w_name, "Персона")
                v.setTextViewText(R.id.w_text, "Создай персонажа и начни разговор")
                v.setViewVisibility(R.id.w_avatar, View.GONE)
            } else {
                val m = chat.messages.lastOrNull()
                v.setTextViewText(R.id.w_name, c.name)
                v.setTextViewText(R.id.w_text, m?.let { (if (it.fromUser) "Ты: " else "") + it.text.replace(Regex("\\*[^*]*\\*"), "").trim() } ?: c.tagline)
                v.setTextViewText(R.id.w_time, m?.let { timeLabel(it.time) } ?: "")
                repo.avatarFile(c.avatar)?.let { f -> BitmapFactory.decodeFile(f.path) }?.let {
                    v.setImageViewBitmap(R.id.w_avatar, Notifier.circle(Bitmap.createScaledBitmap(it, 128, 128, true)))
                    v.setViewVisibility(R.id.w_avatar, View.VISIBLE)
                } ?: v.setViewVisibility(R.id.w_avatar, View.GONE)
            }
            val open = PendingIntent.getActivity(ctx, 7001,
                Intent(ctx, MainActivity::class.java).apply { chat?.let { putExtra("chat", it.id) }; addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.w_root, open)
            v.setOnClickPendingIntent(R.id.w_reply, open)
            mgr.updateAppWidget(ids, v)
        }
    }
}
