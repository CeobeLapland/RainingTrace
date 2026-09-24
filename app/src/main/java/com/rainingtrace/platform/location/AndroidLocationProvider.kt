package com.rainingtrace.platform.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.map.LocationCadenceController
import com.rainingtrace.domain.map.LocationHealth
import com.rainingtrace.domain.map.LocationProvider
import com.rainingtrace.domain.map.LocationSource
import com.rainingtrace.domain.map.LocationWatchdogAction
import com.rainingtrace.domain.map.LocationWatchdogState
import com.rainingtrace.domain.map.RawLocationFix
import com.rainingtrace.domain.map.WorldCoordinate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 真实定位提供方（RT-LOC-003/004/005）。
 *
 * - 优先 FusedLocationProviderClient（高精度、5s 间隔）；
 * - GMS 不可用/启动异常时降级系统 LocationManager（GPS + NETWORK）；
 * - 仅在 [start] 且已授权时采集，[stop] 立即解绑；无权限不抛异常；
 * - 不做去噪——原始 fix 交给 RecordTrackPointUseCase（精度/瞬移过滤）。
 * - 前台默认 5s；进程退到后台由记录服务调 [setPassiveIntervalMs] 降频（省电）。
 * - 本类不自作主张采集：起停与档位都由上层决定。
 */
class AndroidLocationProvider(
    private val appContext: Context,
    private val clock: WorldClock,
    scope: CoroutineScope,
) : LocationProvider, LocationCadenceController, LocationHealth {

    private val _updates = MutableSharedFlow<RawLocationFix>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val updates: Flow<RawLocationFix> = _updates.asSharedFlow()

    @Volatile
    private var lastFix: RawLocationFix? = null
    override val latest: RawLocationFix? get() = lastFix

    /** 后台低频档（毫秒）；null = 前台默认节奏。 */
    @Volatile
    private var passiveIntervalMs: Long? = null

    @Volatile
    private var running = false

    /** 上一次**真实回调**的时刻；探缓存位置不算（否则看门狗永远升级不到重发）。 */
    @Volatile
    private var lastCallbackAtMs: Long = clock.now().toEpochMilli()

    /** 启动时用的是哪个引擎；重发时只走同一个，绝不静默降级换源。 */
    @Volatile
    private var lastEngineFused = false

    private val watchdog = LocationWatchdogState()

    private val _stalled = MutableStateFlow(false)
    override val stalled: StateFlow<Boolean> = _stalled.asStateFlow()

    init {
        // 必须跑在主线程：下面那几个 client/callback 字段都不是 @Volatile，
        // 只在主线程被触碰（Switchable 与记录服务的生命周期都在主线程）。
        // 放进后台线程就是新增一处跨线程竞争。
        scope.launch(Dispatchers.Main.immediate) {
            while (true) {
                delay(WATCHDOG_TICK_MS)
                runCatching { checkWatchdog() }
            }
        }
    }

    /**
     * 看门狗：**只在前台默认档**判定——后台 15 分钟档的静默是预期行为。
     */
    private fun checkWatchdog() {
        if (!running || passiveIntervalMs != null) return
        val silentForMs = clock.now().toEpochMilli() - lastCallbackAtMs
        when (watchdog.consume(silentForMs)) {
            LocationWatchdogAction.WAIT -> Unit

            LocationWatchdogAction.PROBE_LAST_KNOWN -> probeLastKnown()

            LocationWatchdogAction.RE_REQUEST ->
                if (!restartSameEngine()) {
                    // 重发都失败了，就不再假装还在采集。
                    running = false
                    _stalled.value = true
                }

            LocationWatchdogAction.WARN -> _stalled.value = true
        }
    }

    /** 重发请求：**同一个引擎**，失败就直接认输（降级换源会弄乱轨迹，已被明确排除）。 */
    @SuppressLint("MissingPermission")
    private fun restartSameEngine(): Boolean {
        if (!hasPermission()) return false
        stopEngine()
        val ok = if (lastEngineFused) tryStartFused() else tryStartLocationManager()
        if (!ok) return false
        lastCallbackAtMs = clock.now().toEpochMilli()
        watchdog.reset()
        return true
    }

    /** 探一次系统缓存的最后位置：便宜，而且常常立刻就有。 */
    @SuppressLint("MissingPermission")
    private fun probeLastKnown() {
        if (!running || !hasPermission()) return
        val fused = fusedClient
        if (fused != null) {
            runCatching {
                fused.lastLocation.addOnSuccessListener { location ->
                    // fresh = false：这是缓存值，不该重置看门狗，否则永远升级不到重发。
                    location?.let { emitLocation(it, fresh = false) }
                }
            }
            return
        }
        val manager = locationManager
        if (manager != null) {
            runCatching {
                manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?.let { emitLocation(it, fresh = false) }
            }
        }
    }

    /** 切档：正在采集时用新间隔重启一次请求（只重启采集，不产生游戏语义事件）。 */
    override fun setPassiveIntervalMs(intervalMs: Long?) {
        if (passiveIntervalMs == intervalMs) return
        val leavingPassive = intervalMs == null && passiveIntervalMs != null
        passiveIntervalMs = intervalMs
        if (leavingPassive || intervalMs != null) {
            // 换档就重置计时：否则从 15 分钟档回到前台时，
            // lastCallbackAtMs 还停在后台那一刻，看门狗会立刻误报"定位卡住了"。
            lastCallbackAtMs = clock.now().toEpochMilli()
            watchdog.reset()
            _stalled.value = false
        }
        if (running) start()
    }

    private fun currentIntervalMs(): Long = passiveIntervalMs ?: UPDATE_INTERVAL_MS

    private var fusedClient: FusedLocationProviderClient? = null
    private var fusedCallback: LocationCallback? = null
    private var locationManager: LocationManager? = null
    private var managerListener: LocationListener? = null

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        stop()
        if (!hasPermission()) return false
        val fusedOk = tryStartFused()
        lastEngineFused = fusedOk
        val started = if (fusedOk) true else tryStartLocationManager()
        running = started
        lastCallbackAtMs = clock.now().toEpochMilli()
        watchdog.reset()
        _stalled.value = false
        return started
    }

    fun stop() {
        running = false
        stopEngine()
        lastCallbackAtMs = clock.now().toEpochMilli()
        watchdog.reset()
        _stalled.value = false
    }

    private fun stopEngine() {
        runCatching {
            fusedCallback?.let { fusedClient?.removeLocationUpdates(it) }
        }
        fusedCallback = null
        fusedClient = null
        runCatching {
            managerListener?.let { locationManager?.removeUpdates(it) }
        }
        managerListener = null
        locationManager = null
    }

    @SuppressLint("MissingPermission")
    private fun tryStartFused(): Boolean {
        val gmsAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS
        if (!gmsAvailable) return false
        return try {
            val client = LocationServices.getFusedLocationProviderClient(appContext)
            val interval = currentIntervalMs()
            val request = LocationRequest.Builder(interval)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(
                    (interval / 2).coerceAtLeast(FASTEST_INTERVAL_MS),
                )
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { emitLocation(it, fresh = true) }
                }
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            // 先拿一次缓存位置，缩短冷启动等待（不算 fresh）
            runCatching {
                client.lastLocation.addOnSuccessListener { location ->
                    location?.let { emitLocation(it, fresh = false) }
                }
            }
            fusedClient = client
            fusedCallback = callback
            true
        } catch (e: Exception) {
            // 类缺失/服务异常都走系统降级
            runCatching {
                fusedCallback?.let { cb -> fusedClient?.removeLocationUpdates(cb) }
            }
            fusedClient = null
            fusedCallback = null
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryStartLocationManager(): Boolean {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = emitLocation(location, fresh = true)
            @Deprecated("deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        val providers = buildList {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }
        if (providers.isEmpty()) {
            locationManager = null
            return false
        }
        return try {
            val interval = currentIntervalMs()
            providers.forEach { provider ->
                lm.requestLocationUpdates(provider, interval, 0f, listener)
            }
            locationManager = lm
            managerListener = listener
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun emitLocation(location: Location, fresh: Boolean) {
        if (location.latitude.isNaN() || location.longitude.isNaN()) return
        val fix = RawLocationFix(
            coordinate = WorldCoordinate(location.latitude, location.longitude),
            accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else 100.0,
            timestampEpochMs = clock.now().toEpochMilli(),
            source = LocationSource.GPS,
        )
        lastFix = fix
        if (fresh) {
            // 只有真实回调才算"定位活过来了"；探到的缓存位置不算。
            lastCallbackAtMs = clock.now().toEpochMilli()
            watchdog.reset()
            _stalled.value = false
        }
        _updates.tryEmit(fix)
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 5_000L
        const val FASTEST_INTERVAL_MS = 2_000L

        /** 看门狗的检查频率：比最短补救阈值（90s）细得多，够及时又不费电。 */
        const val WATCHDOG_TICK_MS = 30_000L
    }
}
