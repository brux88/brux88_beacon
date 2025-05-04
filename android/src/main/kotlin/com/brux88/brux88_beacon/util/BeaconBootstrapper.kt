package com.brux88.brux88_beacon.util
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.flutter.plugin.common.EventChannel
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.MonitorNotifier
import org.altbeacon.beacon.Region
import org.altbeacon.beacon.startup.BootstrapNotifier
import org.altbeacon.beacon.startup.RegionBootstrap
import com.brux88.brux88_beacon.service.BeaconMonitoringService
import java.util.ArrayList
import android.os.Handler
import android.os.Looper

class BeaconBootstrapper(private val applicationContext: Context) : BootstrapNotifier {
    private var regionBootstrap: RegionBootstrap? = null
    private val beaconManager = BeaconManager.getInstanceForApplication(applicationContext)
    private var monitoringSink: EventChannel.EventSink? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun setEventSink(sink: EventChannel.EventSink?) {
        this.monitoringSink = sink
    }

    fun startBootstrapping(region: Region) {
        stopBootstrapping()
        regionBootstrap = RegionBootstrap(this, region)
        Log.d("BEACON_DEBUG", "Bootstrap avviato per regione: ${region.uniqueId}")
    }

    fun stopBootstrapping() {
        if (regionBootstrap != null) {
            regionBootstrap?.disable()
            regionBootstrap = null
            Log.d("BEACON_DEBUG", "Bootstrap fermato")
        }
    }

    override fun getApplicationContext(): Context {
        return applicationContext
    }

    override fun didEnterRegion(region: Region) {
        Log.d("BEACON_DEBUG", "ENTRATO nella regione ${region.uniqueId}")

        // Esegui su thread UI
        mainHandler.post {
            monitoringSink?.success("INSIDE")
        }

        // Avvia il servizio
        val serviceIntent = Intent(applicationContext, BeaconMonitoringService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(serviceIntent)
            } else {
                applicationContext.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("BEACON_DEBUG", "Errore nell'avvio del servizio: ${e.message}")
        }
    }

    override fun didExitRegion(region: Region) {
        Log.d("BEACON_DEBUG", "USCITO dalla regione ${region.uniqueId}")

        // Esegui su thread UI
        mainHandler.post {
            monitoringSink?.success("OUTSIDE")
        }
    }

    override fun didDetermineStateForRegion(state: Int, region: Region) {
        val stateStr = if (state == MonitorNotifier.INSIDE) "INSIDE" else "OUTSIDE"
        Log.d("BEACON_DEBUG", "STATO regione: $stateStr")

        // Esegui su thread UI
        mainHandler.post {
            monitoringSink?.success(stateStr)
        }
    }
}