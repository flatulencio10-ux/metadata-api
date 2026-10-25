package com.customlauncher.widgets.football

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.customlauncher.container
import com.customlauncher.core.model.PanelItem
import com.customlauncher.widget.WidgetConfig
import java.util.concurrent.TimeUnit

/** Refreshes every competition used by a placed football widget, every 15 minutes. */
class FootballRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.container
        val profile = container.profileRepository.current()
        val widgets = profile.activities.flatMap { act ->
            act.pages.flatMap { it.widgets } + act.panels.flatMap { it.items }.filterIsInstance<PanelItem.Widget>().map { it.widget }
        }.filter { it.type == FootballWidget.id }
        if (widgets.isEmpty()) {
            cancel(applicationContext)
            return Result.success()
        }
        val codes = widgets.map { WidgetConfig(it.config).string(FootballWidget.competitionField) }.distinct()
        var failed = false
        for (code in codes) {
            val r = container.footballRepository.refresh(code)
            if (r is RefreshResult.Failed) failed = true
        }
        return if (failed && runAttemptCount < 3) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "football_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FootballRefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
