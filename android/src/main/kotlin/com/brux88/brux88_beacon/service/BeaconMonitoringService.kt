package com.brux88.brux88_beacon.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.MonitorNotifier
import org.altbeacon.beacon.RangeNotifier
import org.altbeacon.beacon.Region
import com.brux88.brux88_beacon.util.NotificationUtils
import com.brux88.brux88_beacon.repository.LogRepository
import com.brux88.brux88_beacon.util.PreferenceUtils
import com.brux88.brux88_beacon.util.RegionUtils
import java.util.Date
import android.os.Handler
import android.os.Looper
class BeaconMonitoringService : Service(), RangeNotifier {
    private val TAG = "BeaconService"
    private lateinit var beaconManager: BeaconManager
    private lateinit var logRepository: LogRepository
    private var wakeLock: PowerManager.WakeLock? = null
    private val retryHandler = Handler(Looper.getMainLooper())
    private val retryRunnable = object : Runnable {
        override fun run() {
            if (::activeRegion.isInitialized) {
                Log.d(TAG, "Tentativo di riavvio ranging")
                logRepository.addLog("RETRY: Tentativo di riavvio ranging")
                
                try {
                    beaconManager.startRangingBeaconsInRegion(activeRegion)
                    beaconManager.startMonitoringBeaconsInRegion(activeRegion)
                    logRepository.addLog("RETRY: Ranging e monitoraggio riavviati")
                } catch (e: Exception) {
                    Log.e(TAG, "RETRY: Errore nel riavvio: ${e.message}")
                    logRepository.addLog("RETRY: Errore nel riavvio: ${e.message}")
                }
            }
            
            // Riprograma se stessi ogni 60 secondi
            retryHandler.postDelayed(this, 60000)
        }
    }
    companion object {
        private const val TAG = "BeaconService"
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "beacon_monitoring_channel"
        
        // Flag statico per tenere traccia dello stato
        private var isServiceRunning = false
        
        fun isRunning(): Boolean {
            return isServiceRunning
        }
    }
    // Implementazione di RangeNotifier
   override fun didRangeBeaconsInRegion(beacons: Collection<Beacon>, region: Region) {
        if (beacons.isNotEmpty()) {
            val beacon = beacons.first()
            val logMessage = "Beacon: ID=${beacon.id1}, Distanza=${String.format("%.2f", beacon.distance)}m, RSSI=${beacon.rssi}"
            Log.d(TAG, logMessage)
            logRepository.addLog(logMessage)

            if (PreferenceUtils.shouldShowDetectionNotifications(this)){
                // Crea messaggio specifico per la modalità
                val notificationTitle = if (isBackgroundOnly) {
                    "Beacon rilevato (Background)"
                } else {
                    "Beacon rilevato"
                }
                
                // Aggiorna sempre la notifica del servizio in foreground
                val notification = NotificationUtils.createServiceNotification(
                    this,
                    notificationTitle,
                    "UUID: ${beacon.id1}, Distanza: ${String.format("%.2f", beacon.distance)}m"
                )
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
            }
        }
    }


    //ID per gestire il servizio in foreground
    private val FOREGROUND_NOTIFICATION_ID = 1
    private val CHANNEL_ID = "beacon_monitoring_channel"
    private var isBackgroundOnly = false // Nuova proprietà

    // La regione attiva per il monitoraggio
    private lateinit var activeRegion: Region

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        isServiceRunning = true

        // Ottieni il BeaconManager dall'Application
        val application = applicationContext
        beaconManager = BeaconManager.getInstanceForApplication(application)
        logRepository = LogRepository(applicationContext)
        retryHandler.postDelayed(retryRunnable, 60000)

        // Configurazione per il ranging (distanza) dei beacon
        beaconManager.addRangeNotifier(this)

        // Acquisizione del WakeLock per mantenere il servizio attivo
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BeaconApp:BeaconWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minuti di timeout come misura di sicurezza

        logRepository.addLog("Servizio beacon creato")
    }
 
     override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        
        // Controlla se il servizio deve funzionare solo in background
        isBackgroundOnly = intent?.getBooleanExtra("backgroundOnly", false) ?: false
        
        // Verifica se il servizio background è abilitato nelle preferenze
        if (isBackgroundOnly && !PreferenceUtils.isBackgroundServiceEnabled(this)) {
            Log.d(TAG, "Servizio background non abilitato, arresto servizio")
            logRepository.addLog("SERVIZIO: Background non abilitato, arresto")
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Gestisci l'azione BLUETOOTH_READY
        if (intent != null && "com.brux88.brux88_beacon.BLUETOOTH_READY".equals(intent.action)) {
            Log.d(TAG, "Ricevuta notifica Bluetooth pronto")
            logRepository.addLog("SERVIZIO: Ricevuta notifica Bluetooth pronto")
            
            try {
                if (::activeRegion.isInitialized) {
                    beaconManager.startRangingBeaconsInRegion(activeRegion)
                    beaconManager.startMonitoringBeaconsInRegion(activeRegion)
                    logRepository.addLog("SERVIZIO: Riavviato ranging dopo attivazione Bluetooth")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore nel riavvio ranging: ${e.message}")
                logRepository.addLog("SERVIZIO: Errore nel riavvio ranging: ${e.message}")
            }
            
            return START_NOT_STICKY
            //return START_STICKY
        }
    
        // Creazione del canale di notifica (richiesto per Android 8.0+)
        createNotificationChannel()
    
        // Crea notifica con testo diverso in base alla modalità
        val notificationText = if (isBackgroundOnly) {
            "Monitoraggio beacon in background"
        } else {
            "Monitoraggio beacon attivo"
        }
        
        // Avvio del servizio in foreground
        startForeground(
            FOREGROUND_NOTIFICATION_ID,
            createNotification(notificationText)
        )
    
        // Ottieni la regione attiva (specifica o generica in base alla selezione)
        activeRegion = if (PreferenceUtils.isSelectedBeaconEnabled(this)) {
            val selectedBeacon = PreferenceUtils.getSelectedBeacon(this)
            if (selectedBeacon != null) {
                RegionUtils.createRegionForBeacon(selectedBeacon)
            } else {
                RegionUtils.ALL_BEACONS_REGION
            }
        } else {
            RegionUtils.ALL_BEACONS_REGION
        }
        
        // Configura i periodi di scansione in base alla modalità
        if (isBackgroundOnly) {
            // Modalità background - scansioni meno frequenti per risparmiare batteria
            beaconManager.foregroundBetweenScanPeriod = 10000    // 10 secondi tra le scansioni
            beaconManager.foregroundScanPeriod = 1100            // 1.1 secondi di scansione
            beaconManager.backgroundBetweenScanPeriod = 30000    // 30 secondi tra le scansioni in background
            beaconManager.backgroundScanPeriod = 1100            // 1.1 secondi
            logRepository.addLog("SERVIZIO: Configurato per modalità background only")
        } else {
            // Modalità normale - scansioni più frequenti
            beaconManager.foregroundBetweenScanPeriod = 0        // Scansione continua in foreground
            beaconManager.foregroundScanPeriod = 1100            // 1.1 secondi
            beaconManager.backgroundBetweenScanPeriod = 5000     // 5 secondi tra le scansioni in background
            beaconManager.backgroundScanPeriod = 1100            // 1.1 secondi
            logRepository.addLog("SERVIZIO: Configurato per modalità normale")
        }
        
        try {
            // Forza aggiornamento dei periodi di scansione
            beaconManager.updateScanPeriods()
                    
            beaconManager.startRangingBeaconsInRegion(activeRegion)
            beaconManager.startMonitoringBeaconsInRegion(activeRegion)
            logRepository.addLog("Ranging e monitoraggio avviati nella regione: ${activeRegion.uniqueId}")
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'avvio del ranging: ${e.message}")
            logRepository.addLog("ERRORE: Impossibile avviare il ranging: ${e.message}")
        }
    
        // Aggiungi un monitor notifier direttamente nel servizio
        beaconManager.addMonitorNotifier(object : MonitorNotifier {
            override fun didEnterRegion(region: Region) {
                Log.d(TAG, "SERVIZIO: Entrato nella regione ${region.uniqueId}")
                val message = if (isBackgroundOnly) {
                    "Beacon rilevato in background - ${region.uniqueId}"
                } else {
                    "Sei entrato nella regione ${region.uniqueId}"
                }
                
                // Aggiorna sempre la notifica del servizio in foreground
                NotificationUtils.updateNotification(
                    this@BeaconMonitoringService,
                    "Beacon rilevato",
                    message
                )
            }
    
            override fun didExitRegion(region: Region) {
                Log.d(TAG, "SERVIZIO: Uscito dalla regione ${region.uniqueId}")
                val message = if (isBackgroundOnly) {
                    "Beacon perso in background - ${region.uniqueId}"
                } else {
                    "Sei uscito dalla regione ${region.uniqueId}"
                }
                
                // Aggiorna sempre la notifica del servizio in foreground
                NotificationUtils.updateNotification(
                    this@BeaconMonitoringService,
                    "Beacon perso",
                    message
                )
            }
    
            override fun didDetermineStateForRegion(state: Int, region: Region) {
                val stateStr = if (state == MonitorNotifier.INSIDE) "DENTRO" else "FUORI"
                Log.d(TAG, "SERVIZIO: Stato regione cambiato a $stateStr per ${region.uniqueId}")
            }
        })
    
        // Se il servizio viene terminato dal sistema, verrà riavviato solo se non è background only
        return if (isBackgroundOnly) START_STICKY else START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Monitoraggio Beacon"
            val descriptionText = "Canale per il monitoraggio dei beacon BLE"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(), // Intent vuoto poiché non abbiamo ancora un'attività principale
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Beacon Monitor")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Icona di default
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy - Background only: $isBackgroundOnly")
        
        // Se è un servizio background only, segna come disabilitato
        if (isBackgroundOnly) {
            PreferenceUtils.setBackgroundServiceEnabled(this, false)
            logRepository.addLog("SERVIZIO: Background service disabilitato al destroy")
        }

        // Rilascio del WakeLock per evitare drain della batteria
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        retryHandler.removeCallbacks(retryRunnable)

        // Ferma il monitoraggio dei beacon
        try {
            if (::activeRegion.isInitialized) {
                beaconManager.stopRangingBeaconsInRegion(activeRegion)
                beaconManager.stopMonitoringBeaconsInRegion(activeRegion)
                logRepository.addLog("Ranging dei beacon fermato")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'arresto del ranging: ${e.message}")
        }
        isServiceRunning = false

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}