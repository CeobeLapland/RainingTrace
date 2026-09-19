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
import com.rainingtrace.domain.map.LocationProvider
import com.rainingtrace.domain.map.LocationSource
import com.rainingtrace.domain.map.RawLocationFix
import com.rainingtrace.domain.map.WorldCoordinate
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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
) : LocationProvider, LocationCadenceController {

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

    /** 切档：正在采集时用新间隔重启一次请求（只重启采集，不产生游戏语义事件）。 */
    override fun setPassiveIntervalMs(intervalMs: Long?) {
        if (passiveIntervalMs == intervalMs) return
        passiveIntervalMs = intervalMs
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
        val started = if (tryStartFused()) true else tryStartLocationManager()
        running = started
        return started
    }

    fun stop() {
        running = false
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
                    result.lastLocation?.let(::emitLocation)
                }
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            // 先拿一次缓存位置，缩短冷启动等待
            runCatching {
                client.lastLocation.addOnSuccessListener { location ->
                    location?.let(::emitLocation)
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
            override fun onLocationChanged(location: Location) = emitLocation(location)
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

    private fun emitLocation(location: Location) {
        if (location.latitude.isNaN() || location.longitude.isNaN()) return
        val fix = RawLocationFix(
            coordinate = WorldCoordinate(location.latitude, location.longitude),
            accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else 100.0,
            timestampEpochMs = clock.now().toEpochMilli(),
            source = LocationSource.GPS,
        )
        lastFix = fix
        _updates.tryEmit(fix)
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 5_000L
        const val FASTEST_INTERVAL_MS = 2_000L
    }
}
