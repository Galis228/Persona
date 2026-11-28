package com.persona.app

import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Понятные ошибки API: что случилось, что делать и куда нажать. */
object ApiErrors {
    fun classify(code: Int, body: String, msg: String, s: Settings): EngineException {
        val low = (body + " " + msg).lowercase()
        val p = s.provider
        val model = s.apiModel
        return when {
            code == 401 || (code == 403 && !low.contains("region")) -> EngineException(
                "Ключ ${p.title} не подошёл: он неверный, удалён или отозван. Создай новый ключ и подключи его заново.",
                ErrKind.KEY, KeyLinks.url(p))
            code == 402 || low.contains("insufficient credits") || low.contains("requires more credits") -> EngineException(
                "На ${p.title} закончились кредиты. Пополни баланс — или выбери бесплатную модель (с «:free» в названии), они работают без кредитов.",
                ErrKind.CREDITS, if (p == Provider.OPENROUTER) "https://openrouter.ai/settings/credits" else null)
            low.contains("insufficient_quota") || (p == Provider.OPENAI && code == 429 && low.contains("quota")) -> EngineException(
                "На аккаунте OpenAI закончились деньги или не привязана оплата. Пополни баланс в разделе Billing.",
                ErrKind.CREDITS, "https://platform.openai.com/settings/organization/billing")
            low.contains("credit balance is too low") || (p == Provider.CLAUDE && low.contains("billing")) -> EngineException(
                "Баланс кредитов Claude слишком мал. Пополни его в консоли Anthropic в разделе Billing.",
                ErrKind.CREDITS, "https://console.anthropic.com/settings/billing")
            code == 429 && (p == Provider.GEMINI || low.contains("resource_exhausted")) -> EngineException(
                "Бесплатный лимит Gemini исчерпан: слишком много запросов за минуту или за день. Подожди немного или подключи оплату в Google AI Studio. Можно временно переключиться на модель телефона.",
                ErrKind.QUOTA_DAILY, "https://ai.google.dev/gemini-api/docs/rate-limits")
            code == 429 && (low.contains("free-models-per-day") || low.contains("per-day") || low.contains("daily")) -> EngineException(
                "Дневной лимит бесплатных запросов исчерпан. Он обновится завтра — или пополни баланс, и лимит станет больше.",
                ErrKind.QUOTA_DAILY, if (p == Provider.OPENROUTER) "https://openrouter.ai/settings/credits" else null)
            code == 429 -> EngineException(
                "Слишком много запросов подряд — провайдер просит подождать. Попробуй через минуту." +
                    if (model.endsWith(":free")) " Бесплатные модели часто перегружены: можно выбрать другую." else "",
                ErrKind.RATE)
            code == 404 && (low.contains("data policy") || low.contains("privacy")) -> EngineException(
                "В настройках приватности OpenRouter запрещены бесплатные модели. Открой настройки и разреши их — или выбери платную модель.",
                ErrKind.PRIVACY, "https://openrouter.ai/settings/privacy")
            code == 404 || low.contains("model_not_found") || low.contains("not a valid model") -> EngineException(
                "Модель «${model.ifBlank { "?" }}» не найдена у ${p.title}. Выбери другую в «Настройки → Через интернет».",
                ErrKind.MODEL)
            code == 403 -> EngineException("${p.title} недоступен из твоего региона или для этого ключа. $msg".trim(), ErrKind.KEY)
            code in 500..599 -> EngineException("У ${p.title} сбой на сервере ($code). Попробуй ещё раз чуть позже.", ErrKind.RATE)
            else -> EngineException("Ошибка ${p.title} $code: $msg".trim())
        }
    }
}

/** Окно с ошибкой API и кнопками «что сделать». */
@androidx.compose.runtime.Composable
fun ApiErrorDialog(e: ChatEvent.Error, nav: Nav, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val app = ctx.app
    val s by app.repo.settings.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val hasLocal = app.engines.local.hasModel(s)
    androidx.compose.material3.AlertDialog(onClose,
        title = { androidx.compose.material3.Text(when (e.kind) {
            ErrKind.CREDITS -> "Закончились кредиты"
            ErrKind.QUOTA_DAILY -> "Лимит исчерпан"
            ErrKind.KEY -> "Ключ не подошёл"
            ErrKind.PRIVACY -> "Бесплатные модели запрещены"
            ErrKind.MODEL -> "Модель не найдена"
            else -> "Ошибка"
        }) },
        text = { androidx.compose.material3.Text(e.message) },
        confirmButton = {
            androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                e.url?.let { u ->
                    androidx.compose.material3.TextButton({
                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u))); onClose()
                    }) { androidx.compose.material3.Text(when (e.kind) { ErrKind.CREDITS -> "Пополнить баланс"; ErrKind.KEY -> "Создать новый ключ"; ErrKind.PRIVACY -> "Открыть настройки OpenRouter"; else -> "Подробнее" }) }
                }
                if (s.provider == Provider.OPENROUTER && e.kind in setOf(ErrKind.CREDITS, ErrKind.MODEL) && !s.apiModel.endsWith(":free"))
                    androidx.compose.material3.TextButton({
                        scope.launch {
                            runCatching { app.engines.api.listModels(s) }.getOrNull()?.filter { it.endsWith(":free") }
                                ?.let { ModelPick.recommend(Provider.OPENROUTER, it) }?.let { m ->
                                    app.repo.settings { st -> st.copy(apiModel = m) }
                                    android.widget.Toast.makeText(ctx, "Теперь отвечает ${m.substringAfterLast('/')}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            onClose()
                        }
                    }) { androidx.compose.material3.Text("Взять бесплатную модель") }
                if (hasLocal && e.kind != ErrKind.OTHER) androidx.compose.material3.TextButton({ app.repo.settings { it.copy(mode = Mode.LOCAL) }; onClose() }) {
                    androidx.compose.material3.Text("Отвечать моделью телефона")
                }
                if (e.kind in setOf(ErrKind.KEY, ErrKind.MODEL)) androidx.compose.material3.TextButton({ onClose(); nav.go(Route.SettingsPage("api")) }) {
                    androidx.compose.material3.Text("Настройки API")
                }
                androidx.compose.material3.TextButton(onClose) { androidx.compose.material3.Text("Закрыть") }
            }
        })
}
