package com.brux88.brux88_beacon.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.flutter.plugin.common.EventChannel
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.MonitorNotifier
import org.altbeacon.beacon.Region
import org.altbeacon.beacon.startup.BootstrapNotifier
import org.altbeacon.beacon.startup.RegionBootstrap
import com.brux88.brux88_beacon.service.BeaconMonitoringService
import com.brux88.brux88_beacon.repository.LogRepository

class BeaconBootstrapper(private val applicationContext: Context) : BootstrapNotifier {
    private val TAG = "BeaconBootstrapper" // Definisci TAG
    private var regionBootstrap: RegionBootstrap? = null
    private val beaconManager = BeaconManager.getInstanceForApplication(applicationContext)
    private var monitoringSink: EventChannel.EventSink? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val logRepository = LogRepository(applicationContext) // Inizializza logRepository

    fun setEventSink(sink: EventChannel.EventSink?) {
        this.monitoringSink = sink
    }

    fun startBootstrapping(region: Region) {
        stopBootstrapping()
        regionBootstrap = RegionBootstrap(this, region)
        Log.d(TAG, "Bootstrap avviato per regione: ${region.uniqueId}")
        logRepository.addLog("Bootstrap avviato per regione: ${region.uniqueId}")
    }

    fun stopBootstrapping() {
        if (regionBootstrap != null) {
            regionBootstrap?.disable()
            regionBootstrap = null
            Log.d(TAG, "Bootstrap fermato")
            logRepository.addLog("Bootstrap fermato")
        }
    }

    override fun getApplicationContext(): Context {
        return applicationContext
    }

    override fun didEnterRegion(region: Region) {
        Log.d(TAG, "ENTRATO nella regione ${region.uniqueId}")
        logRepository.addLog("ENTRATO nella regione ${region.uniqueId}")

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
            logRepository.addLog("Servizio avviato dopo rilevamento beacon")
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'avvio del servizio: ${e.message}")
            logRepository.addLog("ERRORE nell'avvio del servizio: ${e.message}")
        }
    }

    override fun didExitRegion(region: Region) {
        Log.d(TAG, "USCITO dalla regione ${region.uniqueId}")
        logRepository.addLog("USCITO dalla regione ${region.uniqueId}")

        // Esegui su thread UI
        mainHandler.post {
            monitoringSink?.success("OUTSIDE")
        }
    }

    override fun didDetermineStateForRegion(state: Int, region: Region) {
        val stateStr = if (state == MonitorNotifier.INSIDE) "INSIDE" else "OUTSIDE"
        Log.d(TAG, "STATO regione: $stateStr")
        logRepository.addLog("STATO regione: $stateStr")

        // Esegui su thread UI
        mainHandler.post {
            monitoringSink?.success(stateStr)
        }
    }
}