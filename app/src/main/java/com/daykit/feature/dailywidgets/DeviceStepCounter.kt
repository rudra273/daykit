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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-demand Walk / Run Session step counter powered by Android's hardware [Sensor.TYPE_STEP_COUNTER].
 *
 * Zero battery usage when stopped: no background listeners or workers run unless a session is
 * actively recording. While running, counts steps from 0 using the hardware step-counter delta
 * and saves completed sessions (steps, distance, date, start & end time) for bar-chart inspection.
 */
object DeviceStepCounter {
    private const val PREFS_NAME = "daykit_step_sessions_v1"
    private const val KEY_ACTIVE_RUNNING = "active_running"
    private const val KEY_ACTIVE_ID = "active_id"
    private const val KEY_ACTIVE_START_MS = "active_start_ms"
    private const val KEY_ACTIVE_START_RAW = "active_start_raw"
    private const val KEY_ACTIVE_LAST_RAW = "active_last_raw"
    private const val KEY_ACTIVE_CARRIED = "active_carried"
    private const val KEY_ACTIVE_STEPS = "active_steps"
    private const val KEY_HISTORY_JSON = "history_json"
    private const val KEY_SELECTED_INDEX = "selected_index"

    const val VISIBLE_SLOTS = 7
    private const val MAX_STORED_SESSIONS = 20

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

    fun isRunning(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACTIVE_RUNNING, false)

    fun startSession(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        onStarted: () -> Unit = {},
    ) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ACTIVE_RUNNING, true)
            .putString(KEY_ACTIVE_ID, UUID.randomUUID().toString().take(8))
            .putLong(KEY_ACTIVE_START_MS, nowMillis)
            .putInt(KEY_ACTIVE_START_RAW, -1)
            .putInt(KEY_ACTIVE_LAST_RAW, -1)
            .putInt(KEY_ACTIVE_CARRIED, 0)
            .putInt(KEY_ACTIVE_STEPS, 0)
            .putInt(KEY_SELECTED_INDEX, -1)
            .apply()

        // Immediately anchor hardware baseline at 0 steps
        readRawOneShot(appCtx, timeoutMs = 1500L) { raw ->
            if (raw != null && raw >= 0 && isRunning(appCtx)) {
                prefs.edit()
                    .putInt(KEY_ACTIVE_START_RAW, raw)
                    .putInt(KEY_ACTIVE_LAST_RAW, raw)
                    .putInt(KEY_ACTIVE_STEPS, 0)
                    .apply()
            }
            onStarted()
        }
    }

    fun stopSession(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        onStopped: (StepRunSession?) -> Unit = {},
    ) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE_RUNNING, false)) {
            onStopped(null)
            return
        }

        readRawOneShot(appCtx, timeoutMs = 1500L) { raw ->
            if (raw != null && raw >= 0) {
                ingestActiveRaw(appCtx, raw)
            }
            val id = prefs.getString(KEY_ACTIVE_ID, null) ?: UUID.randomUUID().toString().take(8)
            val startMs = prefs.getLong(KEY_ACTIVE_START_MS, nowMillis)
            val finalSteps = prefs.getInt(KEY_ACTIVE_STEPS, 0).coerceAtLeast(0)
            val endMs = nowMillis.coerceAtLeast(startMs + 1000L)

            val completed = StepRunSession(
                id = id,
                startMillis = startMs,
                endMillis = endMs,
                steps = finalSteps,
                isActive = false,
            )

            val history = loadHistory(appCtx).toMutableList()
            history.add(completed)
            val trimmed = history.takeLast(MAX_STORED_SESSIONS)
            saveHistory(appCtx, trimmed)

            prefs.edit()
                .putBoolean(KEY_ACTIVE_RUNNING, false)
                .putInt(KEY_SELECTED_INDEX, -1) // auto-select newly completed run
                .apply()

            onStopped(completed)
        }
    }

    fun syncActiveSessionOneShot(
        context: Context,
        timeoutMs: Long = 1600L,
        onComplete: (Int?) -> Unit = {},
    ) {
        val appCtx = context.applicationContext
        if (!isRunning(appCtx)) {
            onComplete(null)
            return
        }
        readRawOneShot(appCtx, timeoutMs) { raw ->
            val steps = if (raw != null && raw >= 0) ingestActiveRaw(appCtx, raw) else null
            onComplete(steps)
        }
    }

    fun selectBar(context: Context, slotIndex: Int) {
        val appCtx = context.applicationContext
        val state = loadSessionState(appCtx)
        if (slotIndex !in state.sessions.indices) return
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SELECTED_INDEX, slotIndex)
            .apply()
    }

    fun loadSessionState(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): StepsChartState {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val running = prefs.getBoolean(KEY_ACTIVE_RUNNING, false)
        val history = loadHistory(appCtx)

        val visible = if (running) {
            val active = StepRunSession(
                id = prefs.getString(KEY_ACTIVE_ID, "live") ?: "live",
                startMillis = prefs.getLong(KEY_ACTIVE_START_MS, nowMillis),
                endMillis = nowMillis,
                steps = prefs.getInt(KEY_ACTIVE_STEPS, 0).coerceAtLeast(0),
                isActive = true,
            )
            (history.takeLast(VISIBLE_SLOTS - 1) + active)
        } else {
            history.takeLast(VISIBLE_SLOTS)
        }

        val savedSelected = prefs.getInt(KEY_SELECTED_INDEX, -1)
        val resolvedIndex = when {
            visible.isEmpty() -> -1
            savedSelected in visible.indices -> savedSelected
            else -> visible.lastIndex
        }

        return StepsChartState(
            isRunning = running,
            selectedIndex = resolvedIndex,
            sessions = visible,
            totalSlots = VISIBLE_SLOTS,
        )
    }

    /**
     * Registers a live foreground listener ONLY while a session is actively running.
     */
    fun registerLiveListener(
        context: Context,
        onStepsUpdated: (Int) -> Unit,
    ): () -> Unit {
        val appCtx = context.applicationContext
        if (!isRunning(appCtx) || !hasPermission(appCtx) || !isSensorAvailable(appCtx)) return {}
        val mgr = appCtx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return {}
        val sensor = mgr.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return {}

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!isRunning(appCtx)) return
                val raw = event.values.firstOrNull()?.toInt() ?: return
                val steps = ingestActiveRaw(appCtx, raw)
                onStepsUpdated(steps)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        runCatching { mgr.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) }
        return { runCatching { mgr.unregisterListener(listener) } }
    }

    private fun ingestActiveRaw(context: Context, rawTotal: Int): Int {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE_RUNNING, false)) return 0

        var startRaw = prefs.getInt(KEY_ACTIVE_START_RAW, -1)
        val lastRaw = prefs.getInt(KEY_ACTIVE_LAST_RAW, -1)
        var carried = prefs.getInt(KEY_ACTIVE_CARRIED, 0)
        val prevSteps = prefs.getInt(KEY_ACTIVE_STEPS, 0).coerceAtLeast(0)

        val steps = when {
            startRaw < 0 -> {
                startRaw = rawTotal
                0
            }
            lastRaw >= 0 && rawTotal < lastRaw -> {
                // Device rebooted mid-session
                carried = prevSteps
                startRaw = 0
                (carried + rawTotal).coerceAtLeast(0)
            }
            else -> {
                (carried + (rawTotal - startRaw)).coerceAtLeast(0)
            }
        }

        prefs.edit()
            .putInt(KEY_ACTIVE_START_RAW, startRaw)
            .putInt(KEY_ACTIVE_LAST_RAW, rawTotal)
            .putInt(KEY_ACTIVE_CARRIED, carried)
            .putInt(KEY_ACTIVE_STEPS, steps)
            .apply()

        return steps
    }

    private fun readRawOneShot(
        context: Context,
        timeoutMs: Long,
        onResult: (Int?) -> Unit,
    ) {
        val appCtx = context.applicationContext
        if (!hasPermission(appCtx) || !isSensorAvailable(appCtx)) {
            onResult(null)
            return
        }
        val mgr = appCtx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = mgr?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (mgr == null || sensor == null) {
            onResult(null)
            return
        }

        val done = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())
        lateinit var listener: SensorEventListener

        val timeoutRunnable = Runnable {
            if (done.compareAndSet(false, true)) {
                runCatching { mgr.unregisterListener(listener) }
                onResult(null)
            }
        }

        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!done.compareAndSet(false, true)) return
                mainHandler.removeCallbacks(timeoutRunnable)
                runCatching { mgr.unregisterListener(this) }
                val raw = event.values.firstOrNull()?.toInt()
                onResult(raw)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = runCatching {
            mgr.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }.getOrDefault(false)

        if (!registered) {
            if (done.compareAndSet(false, true)) onResult(null)
            return
        }
        mainHandler.postDelayed(timeoutRunnable, timeoutMs)
    }

    private fun loadHistory(context: Context): List<StepRunSession> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        StepRunSession(
                            id = obj.optString("id", "s_$i"),
                            startMillis = obj.optLong("start", 0L),
                            endMillis = obj.optLong("end", 0L),
                            steps = obj.optInt("steps", 0).coerceAtLeast(0),
                            isActive = false,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveHistory(context: Context, sessions: List<StepRunSession>) {
        val arr = JSONArray()
        for (s in sessions) {
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("start", s.startMillis)
                    .put("end", s.endMillis)
                    .put("steps", s.steps),
            )
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY_JSON, arr.toString())
            .apply()
    }
}
