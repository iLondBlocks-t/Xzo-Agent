package com.xzo.agent.core

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.xzo.agent.XzoApp
import com.xzo.agent.agent.AgentInput
import com.xzo.agent.agent.AgentMode
import com.xzo.agent.data.db.AutomationEntity
import com.xzo.agent.data.db.MessageEntity
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Runs saved prompts on a schedule, in the background, and tells you when they're done.
 *
 * WorkManager's periodic minimum is 15 minutes and its timing drifts, so each
 * automation instead re-enqueues a *one-shot* job aimed at the next occurrence of
 * its wall-clock time — accurate, battery friendly, and it survives reboots.
 */
object AutomationScheduler {

    const val KEY_ID = "automation_id"
    private const val TAG = "xzo-automation"

    fun workName(id: Long) = "$TAG-$id"

    fun schedule(context: Context, automation: AutomationEntity) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (!automation.enabled) {
            wm.cancelUniqueWork(workName(automation.id))
            return
        }
        val delay = millisUntilNext(automation.hour, automation.minute)
        val request = OneTimeWorkRequestBuilder<AutomationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong(KEY_ID, automation.id).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        wm.enqueueUniqueWork(workName(automation.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun runNow(context: Context, automationId: Long) {
        val request = OneTimeWorkRequestBuilder<AutomationWorker>()
            .setInputData(Data.Builder().putLong(KEY_ID, automationId).build())
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(workName(automationId) + "-now", ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, automationId: Long) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(automationId))
    }

    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext as? XzoApp ?: return
        app.container.db.automations().all().forEach { schedule(context, it) }
    }

    fun millisUntilNext(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis - now.timeInMillis
    }
}

class AutomationWorker(
    private val appContext: Context,
    params: androidx.work.WorkerParameters
) : androidx.work.CoroutineWorker(appContext, params) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val app = appContext.applicationContext as? XzoApp
            ?: return androidx.work.ListenableWorker.Result.failure()
        val container = app.container
        val id = inputData.getLong(AutomationScheduler.KEY_ID, -1L)
        if (id <= 0) return androidx.work.ListenableWorker.Result.failure()

        val dao = container.db.automations()
        val automation = dao.byId(id) ?: return androidx.work.ListenableWorker.Result.failure()

        // Every automation keeps its own conversation so the history builds up over time.
        val conversationId = automation.conversationId.takeIf {
            it > 0 && container.db.conversations().byId(it) != null
        } ?: container.db.conversations().insert(
            com.xzo.agent.data.db.ConversationEntity(
                title = "⏰ ${automation.title}",
                modelId = container.settingsSnapshot.primaryModel
            )
        )

        container.db.messages().insert(
            MessageEntity(conversationId = conversationId, role = "user", content = automation.prompt)
        )

        val settings = container.settingsSnapshot
        val outcome = runCatching {
            container.engine.run(
                AgentInput(
                    userText = automation.prompt,
                    history = container.chatRepo.wireHistory(conversationId, 8),
                    modelId = settings.primaryModel,
                    fallbackModelId = settings.fallbackModel,
                    persona = settings.persona,
                    temperature = settings.temperature,
                    maxTokens = settings.maxTokens,
                    toolsEnabled = settings.toolsEnabled,
                    selfVerify = settings.selfVerify,
                    streaming = false,
                    maxIterations = settings.maxIterations,
                    useBuiltInTools = settings.useBuiltInTools,
                    reasoningEffort = settings.reasoningEffort,
                    autoRoute = settings.autoRoute,
                    mode = runCatching { AgentMode.valueOf(automation.mode) }.getOrDefault(AgentMode.AGENT)
                )
            ) { }
        }.getOrNull()

        val answer = outcome?.answer.orEmpty()
        val ok = answer.isNotBlank() && outcome?.error == null

        if (answer.isNotBlank()) {
            container.db.messages().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = "assistant",
                    content = answer,
                    model = outcome?.model,
                    provider = outcome?.provider?.label,
                    verified = outcome?.trace?.verified == true,
                    traceJson = outcome?.trace?.encode(),
                    error = !ok
                )
            )
        }

        dao.update(
            automation.copy(
                lastRunAt = System.currentTimeMillis(),
                lastResult = answer.take(400).ifBlank { "No result" },
                lastOk = ok,
                conversationId = conversationId
            )
        )

        if (automation.notify && answer.isNotBlank()) {
            container.notifier.notifyResult("⏰ ${automation.title}", answer)
        }

        // Queue tomorrow's run.
        dao.byId(id)?.let { AutomationScheduler.schedule(appContext, it) }

        return if (ok) androidx.work.ListenableWorker.Result.success()
        else androidx.work.ListenableWorker.Result.retry()
    }
}
