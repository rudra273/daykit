package com.daykit.feature.dailywidgets

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daykit.feature.widget.StepsChartWidgetProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reads real hardware step counts via Android's low-power [Sensor.TYPE_STEP_COUNTER],
 * handles midnight rollovers and device reboots, and persists daily totals into [STEPS_PREFS].
 */
object DeviceStepCounter {
    internal const val STEPS_PREFS = "daykit_daily_steps_v1"
    private const val KEY_SENSOR_DATE = "sensor_date"
    private const val KEY_BASELINE_RAW = "sensor_baseline_raw"
    private const val KEY_LAST_RAW = "sensor_last_raw"
    private const val KEY_CARRIED_STEPS = "sensor_carried_steps"
    private const val WORK_NAME = "daykit_steps_widget_sync"

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isSensorAvailable(context: Context): Boolean {
        val mgr = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return false
        return mgr.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    /**
     * Converts cumulative hardware [rawTotal] (steps since boot) into today's step count,
     * handling midnight date transitions and mid-day device reboots.
     */
    fun ingestRawSensorValue(
        context: Context,
        rawTotal: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Int {
        if (rawTotal < 0) return 0
        val prefs = context.applicationContext.getSharedPreferences(STEPS_PREFS, Context.MODE_PRIVATE)
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
        val savedDate = prefs.getString(KEY_SENSOR_DATE, null)
        val savedBaseline = prefs.getInt(KEY_BASELINE_RAW, -1)
        val savedLastRaw = prefs.getInt(KEY_LAST_RAW, -1)
        val savedCarried = prefs.getInt(KEY_CARRIED_STEPS, 0)

        var baseline = savedBaseline
        var carried = savedCarried
        val todaySteps: Int

        when {
            savedDate == null || baseline < 0 -> {
                // First ever reading: anchor baseline at current hardware counter
                baseline = rawTotal
                carried = 0
                todaySteps = prefs.getInt("steps_$todayKey", 0).coerceAtLeast(0)
                carried = todaySteps
            }
            savedDate != todayKey -> {
                // New calendar day
                if (savedLastRaw >= 0 && rawTotal < savedLastRaw) {
                    // Rebooted overnight: rawTotal is already fresh steps since boot
                    baseline = 0
                    carried = 0
                    todaySteps = rawTotal
                } else {
                    baseline = rawTotal
                    carried = 0
                    todaySteps = 0
                }
            }
            savedLastRaw >= 0 && rawTotal < savedLastRaw -> {
                // Device rebooted mid-day: preserve steps already counted today
                val priorToday = prefs.getInt("steps_$todayKey", 0).coerceAtLeast(0)
                carried = priorToday
                baseline = 0
                todaySteps = (carried + rawTotal).coerceAtLeast(0)
            }
            else -> {
                todaySteps = (carried + (rawTotal - baseline)).coerceAtLeast(0)
            }
        }

        prefs.edit()
            .putString(KEY_SENSOR_DATE, todayKey)
            .putInt(KEY_BASELINE_RAW, baseline)
            .putInt(KEY_LAST_RAW, rawTotal)
            .putInt(KEY_CARRIED_STEPS, carried)
            .putInt("steps_$todayKey", todaySteps)
            .apply()

        return todaySteps
    }

    /**
     * Reads a single cached sample from [Sensor.TYPE_STEP_COUNTER] (usually < 50ms)
     * with a safety timeout so the listener never leaks.
     */
    fun syncOneShot(
        context: Context,
        timeoutMs: Long = 2000L,
        onComplete: (Int?) -> Unit = {},
    ) {
        val appCtx = context.applicationContext
        if (!hasPermission(appCtx) || !isSensorAvailable(appCtx)) {
            onComplete(null)
            return
        }
        val mgr = appCtx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = mgr?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (mgr == null || sensor == null) {
            onComplete(null)
            return
        }

        val done = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())
        lateinit var listener: SensorEventListener

        val timeoutRunnable = Runnable {
            if (done.compareAndSet(false, true)) {
                runCatching { mgr.unregisterListener(listener) }
                onComplete(null)
            }
        }

        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!done.compareAndSet(false, true)) return
                mainHandler.removeCallbacks(timeoutRunnable)
                runCatching { mgr.unregisterListener(this) }
                val raw = event.values.firstOrNull()?.toInt() ?: -1
                val today = if (raw >= 0) ingestRawSensorValue(appCtx, raw) else null
                onComplete(today)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = runCatching {
            mgr.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }.getOrDefault(false)

        if (!registered) {
            if (done.compareAndSet(false, true)) onComplete(null)
            return
        }

        mainHandler.postDelayed(timeoutRunnable, timeoutMs)
    }

    /**
     * Registers a live listener while a screen is visible; returns an unregister callback.
     */
    fun registerLiveListener(
        context: Context,
        onStepsUpdated: (Int) -> Unit,
    ): () -> Unit {
        val appCtx = context.applicationContext
        if (!hasPermission(appCtx) || !isSensorAvailable(appCtx)) return {}
        val mgr = appCtx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return {}
        val sensor = mgr.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return {}

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val raw = event.values.firstOrNull()?.toInt() ?: return
                val today = ingestRawSensorValue(appCtx, raw)
                onStepsUpdated(today)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        runCatching { mgr.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) }
        return { runCatching { mgr.unregisterListener(listener) } }
    }

    fun schedulePeriodicSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<StepsSyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelPeriodicSync(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }
}

class StepsSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (DeviceStepCounter.hasPermission(applicationContext)) {
            suspendCancellableCoroutine<Unit> { cont ->
                DeviceStepCounter.syncOneShot(applicationContext, timeoutMs = 2500L) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
        StepsChartWidgetProvider.refreshAll(applicationContext)
        return Result.success()
    }
}
