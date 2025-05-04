package com.brux88.brux88_beacon

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Region
import org.altbeacon.beacon.startup.BootstrapNotifier
import org.altbeacon.beacon.startup.RegionBootstrap
import com.brux88.brux88_beacon.repository.BeaconRepository
import com.brux88.brux88_beacon.repository.LogRepository
import com.brux88.brux88_beacon.service.BeaconMonitoringService
import com.brux88.brux88_beacon.util.RegionUtils
import com.brux88.brux88_beacon.util.PreferenceUtils

class BeaconApplication : Application(), BootstrapNotifier {

    private val TAG = "BeaconApplication"
    private var regionBootstrap: RegionBootstrap? = null
    private lateinit var beaconManager: BeaconManager
    private lateinit var logRepository: LogRepository

    // La regione attiva per il monitoraggio
    private lateinit var activeRegion: Region

    override fun onCreate() {
        super.onCreate()

        logRepository = LogRepository(this)
        // ATTIVA IL DEBUG per vedere messaggi dettagliati
        BeaconManager.setDebug(true)
        // Inizializzazione del BeaconManager
        beaconManager = BeaconManager.getInstanceForApplication(this)
        
        // RIMUOVI TUTTI I PARSER ESISTENTI prima di aggiungerne di nuovi
        beaconManager.beaconParsers.clear()

        // Aggiungi TUTTI i formati più comuni

        // iBeacon standard (formato Apple più comune)
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout("m:0-3=4c000215,i:4-19,i:20-21,i:22-23,p:24-24")
        )

        // AltBeacon
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout("m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25")
        )

        // Eddystone-UID
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout("s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19")
        )

        // Eddystone-URL
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout("s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-20v")
        )

        // Configurazione per scansione in background
        beaconManager.setEnableScheduledScanJobs(false)
        beaconManager.backgroundBetweenScanPeriod = PreferenceUtils.getBackgroundBetweenScanPeriod(this)
        beaconManager.backgroundScanPeriod = PreferenceUtils.getBackgroundScanPeriod(this)

        // Configurazione per rilevamento rapido in primo piano
        beaconManager.foregroundBetweenScanPeriod = 0   // Scansione continua
        beaconManager.foregroundScanPeriod = 1100       // 1.1 secondi di scansione

        // Configurazione per la distanza
        beaconManager.setMaxTrackingAge(PreferenceUtils.getMaxTrackingAge(this).toInt())

        // Ottieni la regione corretta (specifica o generica)
        activeRegion = RegionUtils.getMonitoringRegion(this)

        // Registra quale regione stiamo usando
        if (activeRegion.id1 != null) {
            logRepository.addLog("Monitoraggio regione specifica: ${activeRegion.uniqueId}")
        } else {
            logRepository.addLog("Monitoraggio di tutti i beacon (regione generica)")
        }

        // Impostazione per risvegliare l'app quando un beacon viene rilevato
        regionBootstrap = RegionBootstrap(this, activeRegion)

        // Log dell'inizializzazione
        logRepository.addLog("Beacon Application inizializzata")

        // Se il monitoraggio è abilitato, assicuriamo che il servizio sia avviato
        if (PreferenceUtils.isMonitoringEnabled(this)) {
            tryStartMonitoringService()
        }
    }

    private fun tryStartMonitoringService() {
        try {
            val intent = Intent(this, BeaconMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            logRepository.addLog("APP: Tentativo avvio servizio all'inizializzazione dell'app")
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'avvio del servizio all'inizializzazione: ${e.message}")
            logRepository.addLog("APP ERROR: Impossibile avviare servizio all'inizializzazione: ${e.message}")
        }
    }

    /**
     * Aggiorna la regione di monitoraggio attiva
     */
    fun updateMonitoringRegion() {
        try {
            // Ferma il monitoraggio della regione attuale
            if (::activeRegion.isInitialized) {
                beaconManager.stopMonitoringBeaconsInRegion(activeRegion)
                beaconManager.stopRangingBeaconsInRegion(activeRegion)
                logRepository.addLog("Monitoraggio fermato per regione: ${activeRegion.uniqueId}")
            }

            // Ottieni la nuova regione
            activeRegion = RegionUtils.getMonitoringRegion(this)

            // Inizia a monitorare la nuova regione
            beaconManager.startMonitoringBeaconsInRegion(activeRegion)
            logRepository.addLog("Monitoraggio avviato per regione: ${activeRegion.uniqueId}")

            // Aggiorna il bootstrap
            if (regionBootstrap != null) {
                regionBootstrap?.disable()
            }
            regionBootstrap = RegionBootstrap(this, activeRegion)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'aggiornamento della regione: ${e.message}")
            logRepository.addLog("ERRORE: Impossibile aggiornare regione: ${e.message}")
        }
    }

    // Callback chiamata quando un beacon entra in una regione
    override fun didEnterRegion(region: Region) {
        Log.i(TAG, "Beacon rilevato nella regione: ${region.uniqueId}")
        logRepository.addLog("ENTRATA: Beacon rilevato, regione ${region.uniqueId}")

        // Avvia il servizio se non è già in esecuzione
        val serviceIntent = Intent(this, BeaconMonitoringService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            logRepository.addLog("Servizio avviato dopo rilevamento beacon")
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'avvio del servizio dopo rilevamento: ${e.message}")
            logRepository.addLog("ERRORE avvio servizio: ${e.message}")
        }
    }

    // Callback chiamata quando un beacon esce da una regione
    override fun didExitRegion(region: Region) {
        Log.i(TAG, "Beacon uscito dalla regione: ${region.uniqueId}")
        logRepository.addLog("USCITA: Beacon perso, regione ${region.uniqueId}")
    }

    // Callback chiamata quando lo stato di una regione cambia
    override fun didDetermineStateForRegion(state: Int, region: Region) {
        val stateString = if (state == 1) "DENTRO" else "FUORI"
        Log.i(TAG, "Stato regione cambiato: $stateString per ${region.uniqueId}")
        logRepository.addLog("STATO: $stateString per regione ${region.uniqueId}")
    }

    fun getBeaconManager(): BeaconManager {
        return beaconManager
    }

    fun getActiveRegion(): Region {
        return activeRegion
    }
}