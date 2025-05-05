package com.brux88.brux88_beacon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Region
import org.altbeacon.beacon.RangeNotifier
import org.altbeacon.beacon.MonitorNotifier
import org.altbeacon.beacon.startup.RegionBootstrap

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent

import com.brux88.brux88_beacon.model.SelectedBeacon
import com.brux88.brux88_beacon.repository.BeaconRepository
import com.brux88.brux88_beacon.repository.LogRepository
import com.brux88.brux88_beacon.service.BeaconMonitoringService
import com.brux88.brux88_beacon.service.BluetoothStateReceiver
import com.brux88.brux88_beacon.service.ServiceWatchdogReceiver
import com.brux88.brux88_beacon.util.PreferenceUtils
import com.brux88.brux88_beacon.util.RegionUtils
import com.brux88.brux88_beacon.util.BeaconBootstrapper
import android.content.IntentFilter
import android.bluetooth.BluetoothAdapter
class Brux88BeaconPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, RangeNotifier  {
    private val TAG = "Brux88BeaconPlugin"
    private var beaconBootstrapper: BeaconBootstrapper? = null


    private lateinit var methodChannel: MethodChannel
    private lateinit var beaconsEventChannel: EventChannel
    private lateinit var monitoringEventChannel: EventChannel
    
    private lateinit var context: Context
    private var activity: Activity? = null
    
    // Componenti beacon
    private lateinit var beaconManager: BeaconManager
    private lateinit var beaconRepository: BeaconRepository
    private lateinit var logRepository: LogRepository
    
    // Event sinks
    private var beaconsSink: EventChannel.EventSink? = null
    private var monitoringSink: EventChannel.EventSink? = null
    //private var regionBootstrap: RegionBootstrap? = null  // Aggiungi questa variabile

    // Regione di monitoraggio attiva
    private lateinit var activeRegion: Region

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext
        methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, "com.brux88.brux88_beacon/methods")
        beaconsEventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "com.brux88.brux88_beacon/beacons")
        monitoringEventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "com.brux88.brux88_beacon/monitoring")
        
        methodChannel.setMethodCallHandler(this)
        setupEventChannels()
        
        // Inizializza repository di log per debug
        logRepository = LogRepository(context)
        logRepository.addLog("Plugin allegato al motore Flutter")
    }

    private fun setupEventChannels() {
        beaconsEventChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                beaconsSink = events
                beaconBootstrapper?.setEventSink(events)  // Aggiorna il sink nel bootstrapper
                logRepository.addLog("Beacon event channel attivato")
            }

            override fun onCancel(arguments: Any?) {
                beaconsSink = null
                beaconBootstrapper?.setEventSink(null)  // Rimuovi il sink nel bootstrapper
                logRepository.addLog("Beacon event channel cancellato")
            }
        })

        monitoringEventChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                monitoringSink = events
                logRepository.addLog("Monitoring event channel attivato")
            }

            override fun onCancel(arguments: Any?) {
                monitoringSink = null
                logRepository.addLog("Monitoring event channel cancellato")
            }
        })
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        try {
            when (call.method) {
                "initialize" -> {
                    initialize(result)
                }
                "startMonitoring" -> {
                    startMonitoring(result)
                }
                "stopMonitoring" -> {
                    stopMonitoring(result)
                }
                "setBeaconToMonitor" -> {
                    val uuid = call.argument<String>("uuid")
                    val major = call.argument<String>("major")
                    val minor = call.argument<String>("minor")
                    val name = call.argument<String>("name")
                    val enabled = call.argument<Boolean>("enabled") ?: true
                    
                    if (uuid != null) {
                        setBeaconToMonitor(uuid, major, minor, name, enabled, result)
                    } else {
                        result.error("INVALID_ARGUMENTS", "UUID è richiesto", null)
                    }
                }
                "clearSelectedBeacon" -> {
                    clearSelectedBeacon(result)
                }
                "setScanSettings" -> {
                    setScanSettings(call, result)
                }
                "getScanSettings" -> {
                    getScanSettings(result)
                }
                "startMonitoringForRegion" -> {
                    startMonitoringForRegion(call, result)
                }
                "stopMonitoringForRegion" -> {
                    stopMonitoringForRegion(call, result)
                }
                "getMonitoredRegions" -> {
                    getMonitoredRegions(result)
                }
                "isMonitoringRunning" -> {
                    result.success(PreferenceUtils.isMonitoringEnabled(context))
                }
                "isBluetoothEnabled" -> {
                    isBluetoothEnabled(result)
                }
                "isLocationEnabled" -> {
                    isLocationEnabled(result)
                }
                "checkPermissions" -> {
                    checkPermissions(result)
                }
                "requestPermissions" -> {
                    requestPermissions(result)
                }
                "isBatteryOptimizationIgnored" -> {
                    isBatteryOptimizationIgnored(result)
                }
                "requestIgnoreBatteryOptimization" -> {
                    requestIgnoreBatteryOptimization(result)
                }
                "enableDebugMode" -> {
                    enableDebugMode(result)
                }
                "getSelectedBeacon" -> {
                    getSelectedBeacon(result)
                }
                "setupRecurringAlarm" -> {
                    setupRecurringAlarm(result)
                }
                "cancelRecurringAlarm" -> {
                    cancelRecurringAlarm(result)
                }
                "getLogs" -> {
                  getLogs(result)
                }
                "requestExactAlarmPermission" -> {
                    requestExactAlarmPermission(result)
                }
                "isInitialized" -> {
                    isInitialized(result)
                }
                "setMonitoringEnabled" -> {
                    val enabled = call.arguments as Boolean
                    PreferenceUtils.setMonitoringEnabled(context, enabled)
                    result.success(true)
                }
                else -> {
                    result.notImplemented()
                }
            }
        } catch (e: Exception) {
            logRepository.addLog("ERRORE in onMethodCall: ${e.message}")
            Log.e(TAG, "Errore in onMethodCall: ${e.message}", e)
            result.error("INTERNAL_ERROR", "Si è verificato un errore interno: ${e.message}", null)
        }
    }
    private fun isInitialized(result: Result) {
        try {
          // Verifica se il BeaconManager è inizializzato
          val isInitialized = ::beaconManager.isInitialized
          
          // Verifica aggiuntiva per assicurarsi che sia correttamente configurato
          var isConfigured = false
          if (isInitialized) {
            try {
              // Verifica se è possibile accedere alle proprietà del BeaconManager
              val testAccess = beaconManager.foregroundScanPeriod
              isConfigured = true
            } catch (e: Exception) {
              isConfigured = false
            }
          }
          val bootstrapperInitialized = beaconBootstrapper != null

          val initializedStatus = isInitialized && isConfigured
          
          Log.d(TAG, "Controllo inizializzazione BeaconManager: $initializedStatus")
          logRepository.addLog("Controllo inizializzazione BeaconManager: $initializedStatus")
          
          result.success(initializedStatus)
        } catch (e: Exception) {
          Log.e(TAG, "Errore nella verifica dell'inizializzazione: ${e.message}", e)
          logRepository.addLog("ERRORE nella verifica dell'inizializzazione: ${e.message}")
          result.error("INIT_CHECK_ERROR", "Errore nella verifica dell'inizializzazione: ${e.message}", null)
        }
      }
    private fun setupRecurringAlarm(result: Result) {
        try {
            // Verifica il permesso per Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!alarmManager.canScheduleExactAlarms()) {
                    result.error(
                        "ALARM_PERMISSION_ERROR",
                        "Manca il permesso SCHEDULE_EXACT_ALARM. Utilizzare requestExactAlarmPermission() prima di chiamare questo metodo.",
                        null
                    )
                    return
                }
            }
    
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, BeaconMonitoringService::class.java)
            val pendingIntent = PendingIntent.getService(
                context,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
    
            // Intervallo di 15 minuti
            val interval = 15 * 60 * 1000L
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + interval,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + interval,
                    pendingIntent
                )
            }
            
            logRepository.addLog("Allarme impostato per il riavvio periodico del servizio")
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nella configurazione dell'allarme: ${e.message}", e)
            logRepository.addLog("ERRORE nella configurazione dell'allarme: ${e.message}")
            result.error("ALARM_ERROR", "Errore nella configurazione dell'allarme: ${e.message}", null)
        }
    }
    
    // Aggiungi anche la cancellazione dell'allarme
    private fun cancelRecurringAlarm(result: Result) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, BeaconMonitoringService::class.java)
            val pendingIntent = PendingIntent.getService(
                context,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
    
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            
            logRepository.addLog("Allarme per il riavvio del servizio cancellato")
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nella cancellazione dell'allarme: ${e.message}", e)
            logRepository.addLog("ERRORE nella cancellazione dell'allarme: ${e.message}")
            result.error("ALARM_CANCEL_ERROR", "Errore nella cancellazione dell'allarme: ${e.message}", null)
        }
    }

    private fun getSelectedBeacon(result: Result) {
        try {
          val selectedBeacon = PreferenceUtils.getSelectedBeacon(context)
          if (selectedBeacon != null) {
            val beaconMap = HashMap<String, Any?>()
            beaconMap["uuid"] = selectedBeacon.uuid
            beaconMap["major"] = selectedBeacon.major
            beaconMap["minor"] = selectedBeacon.minor
            beaconMap["name"] = selectedBeacon.name
            beaconMap["enabled"] = PreferenceUtils.isSelectedBeaconEnabled(context)
            
            Log.d(TAG, "Beacon selezionato: $beaconMap")
            logRepository.addLog("Ottenuto beacon selezionato: ${selectedBeacon.uuid}")
            
            result.success(beaconMap)
          } else {
            Log.d(TAG, "Nessun beacon selezionato")
            logRepository.addLog("Nessun beacon selezionato trovato")
            result.success(null)
          }
        } catch (e: Exception) {
          Log.e(TAG, "Errore nel recupero del beacon selezionato: ${e.message}", e)
          logRepository.addLog("ERRORE nel recupero del beacon selezionato: ${e.message}")
          result.error("GET_SELECTED_BEACON_ERROR", "Errore nel recupero del beacon selezionato: ${e.message}", null)
        }
      }
      private fun initialize(result: Result) {
        try {
            Log.d(TAG, "Inizializzazione BeaconManager")
            logRepository.addLog("Inizializzazione BeaconManager")
            
            // Ferma tutte le attività BeaconManager in corso
            try {
                if (::beaconManager.isInitialized) {
                    Log.d(TAG, "Tentativo di rilascio dell'istanza precedente del BeaconManager")
                    logRepository.addLog("Tentativo di rilascio dell'istanza precedente del BeaconManager")
                    
                    // Ferma tutti i monitoring
                    for (region in ArrayList(beaconManager.monitoredRegions)) {
                        try {
                            beaconManager.stopMonitoringBeaconsInRegion(region)
                        } catch (e: Exception) {
                            Log.e(TAG, "Errore nell'arresto del monitoring per ${region.uniqueId}: ${e.message}")
                        }
                    }
                    
                    // Ferma tutti i ranging
                    for (region in ArrayList(beaconManager.rangedRegions)) {
                        try {
                            beaconManager.stopRangingBeaconsInRegion(region)
                        } catch (e: Exception) {
                            Log.e(TAG, "Errore nell'arresto del ranging per ${region.uniqueId}: ${e.message}")
                        }
                    }
                    
                    // Rimuovi tutti i notifier
                    try {
                        beaconManager.removeAllMonitorNotifiers()
                        beaconManager.removeAllRangeNotifiers()
                    } catch (e: Exception) {
                        Log.e(TAG, "Errore nella rimozione dei notifier: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore nel fermare le attività BeaconManager precedenti: ${e.message}")
                logRepository.addLog("ERRORE nel fermare le attività BeaconManager precedenti: ${e.message}")
                // Continua comunque con l'inizializzazione
            }
            
            // Dormi brevemente per dare tempo al sistema di rilasciare le risorse
            Thread.sleep(100)
            
            // Forza il rilascio dell'istanza precedente di BeaconManager
            if (::beaconManager.isInitialized) {
                try {
                    // Usa reflection per accedere al metodo di pulizia delle istanze
                    val cleanupMethod = BeaconManager::class.java.getDeclaredMethod("cleanupInstances")
                    cleanupMethod.isAccessible = true
                    cleanupMethod.invoke(null)
                    Log.d(TAG, "Cleanup istanze BeaconManager eseguito con successo")
                } catch (e: Exception) {
                    Log.e(TAG, "Errore nel cleanup delle istanze BeaconManager: ${e.message}")
                }
            }
            
            // Inizializza una nuova istanza di BeaconManager
            beaconManager = BeaconManager.getInstanceForApplication(context)
            
            // Abilita debug per più log
            BeaconManager.setDebug(true)
            
            // Inizializza repository beacon
            beaconRepository = BeaconRepository(context)
            
            // Pulisci e aggiungi i parser di beacon
            beaconManager.beaconParsers.clear()
            
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
            beaconManager.backgroundBetweenScanPeriod = PreferenceUtils.getBackgroundBetweenScanPeriod(context)
            beaconManager.backgroundScanPeriod = PreferenceUtils.getBackgroundScanPeriod(context)
    
            // Configurazione per rilevamento rapido in primo piano
            beaconManager.foregroundBetweenScanPeriod = 0   // Scansione continua
            beaconManager.foregroundScanPeriod = 1100       // 1.1 secondi di scansione
            
            // Configurazione per la distanza
            beaconManager.setMaxTrackingAge(PreferenceUtils.getMaxTrackingAge(context).toInt())
            
            // Imposta la flag di monitoraggio a false per sicurezza
            PreferenceUtils.setMonitoringEnabled(context, false)
            
            val uniqueId = "myUniqueId"
            val region = Region(uniqueId, null, null, null)
            activeRegion = region
            
            // Configurazione notifier per i beacon
            setupBeaconCallbacks()
            
            // Inizializza il BeaconBootstrapper
            beaconBootstrapper = BeaconBootstrapper(context.applicationContext)
            beaconBootstrapper?.setEventSink(monitoringSink)
            beaconBootstrapper?.startBootstrapping(region)
    
            // Ottieni la regione corretta (specifica o generica)
            activeRegion = RegionUtils.getMonitoringRegion(context)
            // Registra il receiver per il Bluetooth se non è già registrato
            try {
                val bluetoothReceiver = BluetoothStateReceiver()
                val intentFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                context.applicationContext.registerReceiver(bluetoothReceiver, intentFilter)
                logRepository.addLog("Receiver Bluetooth registrato")
            } catch (e: Exception) {
                Log.e(TAG, "Errore nella registrazione del receiver Bluetooth: ${e.message}")
                logRepository.addLog("ERRORE nella registrazione del receiver Bluetooth: ${e.message}")
                // Continua comunque
            }
            
            // Verifica se c'è un riavvio pendente
            if (PreferenceUtils.hasPendingRestart(context) && PreferenceUtils.isMonitoringEnabled(context)) {
                Log.d(TAG, "Riavvio pendente rilevato, pianificazione avvio servizio")
                logRepository.addLog("Riavvio pendente rilevato durante inizializzazione")
                
                // Verifica lo stato del Bluetooth
                val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                    try {
                        val serviceIntent = Intent(context, BeaconMonitoringService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        logRepository.addLog("Servizio avviato per riavvio pendente")
                        PreferenceUtils.setPendingRestart(context, false)
                    } catch (e: Exception) {
                        Log.e(TAG, "Errore nell'avvio del servizio per riavvio pendente: ${e.message}")
                        logRepository.addLog("ERRORE nell'avvio del servizio per riavvio pendente: ${e.message}")
                    }
                }
            }
            logRepository.addLog("BeaconManager inizializzato con successo")
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'inizializzazione: ${e.message}", e)
            logRepository.addLog("ERRORE nell'inizializzazione: ${e.message}")
            result.error("INITIALIZATION_ERROR", "Errore nell'inizializzazione: ${e.message}", null)
        }
    }
    private fun getLogs(result: Result) {
      try {
          // Ottieni i log dal repository usando il nuovo metodo
          val logs = logRepository.getCurrentLogs()
          
          // Limita a max 100 log per non sovracaricare il canale
          val recentLogs = if (logs.size > 100) logs.take(100) else logs
          
          Log.d(TAG, "Inviati ${recentLogs.size} log a Flutter")
          result.success(recentLogs)
      } catch (e: Exception) {
          Log.e(TAG, "Errore nel recupero dei log: ${e.message}", e)
          result.error("LOGS_ERROR", "Errore nel recupero dei log: ${e.message}", null)
      }
  }
  private fun setupServiceWatchdog(result: Result) {
    try {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ServiceWatchdogReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Controlla ogni 30 minuti
        val interval = 30 * 60 * 1000L
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval,
                pendingIntent
            )
        }
        
        logRepository.addLog("Watchdog per il servizio configurato")
        result.success(true)
    } catch (e: Exception) {
        Log.e(TAG, "Errore nella configurazione del watchdog: ${e.message}", e)
        logRepository.addLog("ERRORE nella configurazione del watchdog: ${e.message}")
        result.error("WATCHDOG_ERROR", "Errore nella configurazione del watchdog: ${e.message}", null)
    }
}

    private fun setupBeaconCallbacks() {
        // Range notifier per gli aggiornamenti sulle distanze
        beaconManager.addRangeNotifier(this)
            // Aggiungi un RangeNotifier separato per debugging

    }

    // Implementazione di RangeNotifier
    override fun didRangeBeaconsInRegion(beacons: Collection<Beacon>, region: Region) {
        Log.d(TAG, "Range: trovati ${beacons.size} beacon nella regione ${region.uniqueId}")
        
        if (beacons.isNotEmpty()) {
            // Log dettagliato su ogni beacon
            beacons.forEach { beacon ->
                Log.d(TAG, "Beacon: UUID=${beacon.id1}, Major=${beacon.id2}, Minor=${beacon.id3}, " +
                        "Dist=${String.format("%.2f", beacon.distance)}m, RSSI=${beacon.rssi}, " +
                        "TxPower=${beacon.txPower}")
            }
            
            // Aggiorna il repository beacon
            beaconRepository.updateBeacons(beacons, region)
            
            // Invia i beacon al Dart tramite l'event channel
            beaconsSink?.success(beacons.map { beacon ->
                mapOf(
                    "uuid" to beacon.id1.toString(),
                    "major" to beacon.id2?.toString(),
                    "minor" to beacon.id3?.toString(),
                    "distance" to beacon.distance,
                    "rssi" to beacon.rssi,
                    "txPower" to beacon.txPower
                )
            })
        }
    }

    private fun startMonitoring(result: Result) {
        try {
            Log.d(TAG, "Avvio monitoraggio beacon")
            logRepository.addLog("Avvio monitoraggio beacon")
                    // Impostare l'allarme per il riavvio periodico
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BeaconMonitoringService::class.java)
        val pendingIntent = PendingIntent.getService(
            context,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Intervallo di 15 minuti
        val interval = 15 * 60 * 1000L
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval,
                pendingIntent
            )
        }
        
        logRepository.addLog("Allarme impostato per il riavvio periodico del servizio")
            // Verifica se abbiamo un beacon selezionato
            val selectedBeacon = PreferenceUtils.getSelectedBeacon(context)
            val region = if (selectedBeacon != null && PreferenceUtils.isSelectedBeaconEnabled(context)) {
                Log.d(TAG, "Monitoraggio beacon specifico: ${selectedBeacon.uuid}")
                logRepository.addLog("Monitoraggio beacon specifico: ${selectedBeacon.uuid}")
                RegionUtils.createRegionForBeacon(selectedBeacon)
            } else {
                Log.d(TAG, "Monitoraggio di tutti i beacon")
                logRepository.addLog("Monitoraggio di tutti i beacon")
                RegionUtils.ALL_BEACONS_REGION
            }
            
            // Salva la regione attiva
            activeRegion = region
            
            // Avvia il ranging e il monitoraggio
            beaconManager.startRangingBeaconsInRegion(region)
            beaconManager.startMonitoringBeaconsInRegion(region)
            
            Log.d(TAG, "Ranging e monitoraggio avviati nella regione: ${region.uniqueId}")
            logRepository.addLog("Ranging e monitoraggio avviati nella regione: ${region.uniqueId}")
            
            // Salva lo stato di monitoraggio
            PreferenceUtils.setMonitoringEnabled(context, true)
            beaconBootstrapper?.startBootstrapping(activeRegion)

            // Avvia il servizio in foreground
            val serviceIntent = Intent(context, BeaconMonitoringService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                logRepository.addLog("Servizio di monitoraggio avviato")
            } catch (e: Exception) {
                Log.e(TAG, "Errore nell'avvio del servizio: ${e.message}", e)
                logRepository.addLog("ERRORE nell'avvio del servizio: ${e.message}")
                // Continuiamo comunque, il monitoraggio funzionerà in foreground
            }
            
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'avvio del monitoraggio: ${e.message}", e)
            logRepository.addLog("ERRORE nell'avvio del monitoraggio: ${e.message}")
            result.error("START_MONITORING_ERROR", "Errore nell'avvio del monitoraggio: ${e.message}", null)
        }
    }

    // Nel plugin, aggiungi un metodo per verificare e richiedere l'esclusione
    fun checkBatteryOptimization() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
          if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
              // Richiedi all'utente di disattivare l'ottimizzazione della batteria
              if (activity != null) {
                  val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                  intent.data = Uri.parse("package:${context.packageName}")
                  activity?.startActivity(intent)
              }
          }
      }
    }

    private fun stopMonitoring(result: Result) {
        try {
            Log.d(TAG, "Arresto monitoraggio beacon")
            logRepository.addLog("Arresto monitoraggio beacon")
             // Cancellare l'allarme per il riavvio periodico
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BeaconMonitoringService::class.java)
        val pendingIntent = PendingIntent.getService(
            context,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        
        logRepository.addLog("Allarme per il riavvio del servizio cancellato")
            // Ferma il ranging e il monitoraggio
            if (::activeRegion.isInitialized) {
                beaconManager.stopRangingBeaconsInRegion(activeRegion)
                beaconManager.stopMonitoringBeaconsInRegion(activeRegion)
                Log.d(TAG, "Ranging e monitoraggio fermati nella regione: ${activeRegion.uniqueId}")
                logRepository.addLog("Ranging e monitoraggio fermati nella regione: ${activeRegion.uniqueId}")
            } else {
                // Se non abbiamo una regione attiva, ferma tutto
                beaconManager.rangedRegions.forEach { region ->
                    beaconManager.stopRangingBeaconsInRegion(region)
                }
                beaconManager.monitoredRegions.forEach { region ->
                    beaconManager.stopMonitoringBeaconsInRegion(region)
                }
                Log.d(TAG, "Fermati tutti i ranging e monitoraggi")
                logRepository.addLog("Fermati tutti i ranging e monitoraggi")
            }
            
            // Aggiorna lo stato di monitoraggio
            PreferenceUtils.setMonitoringEnabled(context, false)
            beaconBootstrapper?.stopBootstrapping()

            // Ferma il servizio in foreground
            val serviceIntent = Intent(context, BeaconMonitoringService::class.java)
            context.stopService(serviceIntent)
            logRepository.addLog("Servizio di monitoraggio fermato")
            
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'arresto del monitoraggio: ${e.message}", e)
            logRepository.addLog("ERRORE nell'arresto del monitoraggio: ${e.message}")
            result.error("STOP_MONITORING_ERROR", "Errore nell'arresto del monitoraggio: ${e.message}", null)
        }
    }

    private fun setBeaconToMonitor(
        uuid: String, 
        major: String?, 
        minor: String?, 
        name: String?,
        enabled: Boolean,
        result: Result
    ) {
        try {
            Log.d(TAG, "Impostazione beacon da monitorare: UUID=$uuid, Major=$major, Minor=$minor, Enabled=$enabled")
            logRepository.addLog("Impostazione beacon da monitorare: UUID=$uuid, Major=$major, Minor=$minor, Enabled=$enabled")
            
            val selectedBeacon = SelectedBeacon(uuid, major, minor, name)
            PreferenceUtils.saveSelectedBeacon(context, selectedBeacon)
            PreferenceUtils.setSelectedBeaconEnabled(context, enabled)
            
            // Se il monitoraggio è attivo, aggiorna la regione
            if (PreferenceUtils.isMonitoringEnabled(context)) {
                // Ferma il monitoraggio corrente
                if (::activeRegion.isInitialized) {
                    beaconManager.stopRangingBeaconsInRegion(activeRegion)
                    beaconManager.stopMonitoringBeaconsInRegion(activeRegion)
                }
                
                // Crea la nuova regione
                val newRegion = if (enabled) {
                    RegionUtils.createRegionForBeacon(selectedBeacon)
                } else {
                    RegionUtils.ALL_BEACONS_REGION
                }
                
                // Avvia il monitoraggio con la nuova regione
                beaconManager.startRangingBeaconsInRegion(newRegion)
                beaconManager.startMonitoringBeaconsInRegion(newRegion)
                
                // Aggiorna la regione attiva
                activeRegion = newRegion
                
                Log.d(TAG, "Monitoraggio aggiornato alla nuova regione: ${newRegion.uniqueId}")
                logRepository.addLog("Monitoraggio aggiornato alla nuova regione: ${newRegion.uniqueId}")
            }
            
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'impostazione del beacon: ${e.message}", e)
            logRepository.addLog("ERRORE nell'impostazione del beacon: ${e.message}")
            result.error("SET_BEACON_ERROR", "Errore nell'impostazione del beacon: ${e.message}", null)
        }
    }

    private fun clearSelectedBeacon(result: Result) {
        try {
            Log.d(TAG, "Pulizia beacon selezionato")
            logRepository.addLog("Pulizia beacon selezionato")
            
            PreferenceUtils.clearSelectedBeacon(context)
            
            // Se il monitoraggio è attivo, passa alla regione generica
            if (PreferenceUtils.isMonitoringEnabled(context)) {
                // Ferma il monitoraggio corrente
                if (::activeRegion.isInitialized) {
                    beaconManager.stopRangingBeaconsInRegion(activeRegion)
                    beaconManager.stopMonitoringBeaconsInRegion(activeRegion)
                }
                
                // Avvia il monitoraggio con la regione generica
                activeRegion = RegionUtils.ALL_BEACONS_REGION
                beaconManager.startRangingBeaconsInRegion(activeRegion)
                beaconManager.startMonitoringBeaconsInRegion(activeRegion)
                
                Log.d(TAG, "Monitoraggio aggiornato alla regione generica")
                logRepository.addLog("Monitoraggio aggiornato alla regione generica")
            }
            
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nella pulizia del beacon: ${e.message}", e)
            logRepository.addLog("ERRORE nella pulizia del beacon: ${e.message}")
            result.error("CLEAR_BEACON_ERROR", "Errore nella pulizia del beacon: ${e.message}", null)
        }
    }

    private fun setScanSettings(call: MethodCall, result: Result) {
        try {
            val backgroundScanPeriod = call.argument<Int>("backgroundScanPeriod") ?: 1100
            val backgroundBetweenScanPeriod = call.argument<Int>("backgroundBetweenScanPeriod") ?: 5000
            val foregroundScanPeriod = call.argument<Int>("foregroundScanPeriod") ?: 1100
            val foregroundBetweenScanPeriod = call.argument<Int>("foregroundBetweenScanPeriod") ?: 0
            val maxTrackingAge = call.argument<Int>("maxTrackingAge") ?: 5000
            
            Log.d(TAG, "Impostazione parametri di scansione: " +
                    "BGScan=$backgroundScanPeriod, BGBetween=$backgroundBetweenScanPeriod, " +
                    "FGScan=$foregroundScanPeriod, FGBetween=$foregroundBetweenScanPeriod, " +
                    "MaxAge=$maxTrackingAge")
            
            beaconManager.backgroundScanPeriod = backgroundScanPeriod.toLong()
            beaconManager.backgroundBetweenScanPeriod = backgroundBetweenScanPeriod.toLong()
            beaconManager.foregroundScanPeriod = foregroundScanPeriod.toLong()
            beaconManager.foregroundBetweenScanPeriod = foregroundBetweenScanPeriod.toLong()
            beaconManager.setMaxTrackingAge(maxTrackingAge)
            
            // Salva nelle preferenze
            PreferenceUtils.setBackgroundScanPeriod(context, backgroundScanPeriod.toLong())
            PreferenceUtils.setBackgroundBetweenScanPeriod(context, backgroundBetweenScanPeriod.toLong())
            PreferenceUtils.setMaxTrackingAge(context, maxTrackingAge.toLong())
            
            // Se siamo in uno stato attivo, aggiorna i parametri
            if (PreferenceUtils.isMonitoringEnabled(context)) {
                // Aggiorna i parametri
                logRepository.addLog("Aggiornamento parametri di scansione durante monitoraggio attivo")
                beaconManager.updateScanPeriods()
            }
            
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'impostazione dei parametri di scansione: ${e.message}", e)
            logRepository.addLog("ERRORE nell'impostazione dei parametri di scansione: ${e.message}")
            result.error("SCAN_SETTINGS_ERROR", "Errore nell'impostazione dei parametri di scansione: ${e.message}", null)
        }
    }

    private fun getScanSettings(result: Result) {
        try {
            val settings = hashMapOf<String, Any>()
            settings["backgroundScanPeriod"] = beaconManager.backgroundScanPeriod.toInt()
            settings["backgroundBetweenScanPeriod"] = beaconManager.backgroundBetweenScanPeriod.toInt()
            settings["foregroundScanPeriod"] = beaconManager.foregroundScanPeriod.toInt()
            settings["foregroundBetweenScanPeriod"] = beaconManager.foregroundBetweenScanPeriod.toInt()
            settings["maxTrackingAge"] = PreferenceUtils.getMaxTrackingAge(context).toInt()
            
            Log.d(TAG, "Ottenuti parametri di scansione: $settings")
            result.success(settings)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel recupero dei parametri di scansione: ${e.message}", e)
            logRepository.addLog("ERRORE nel recupero dei parametri di scansione: ${e.message}")
            result.error("GET_SCAN_SETTINGS_ERROR", "Errore nel recupero dei parametri di scansione: ${e.message}", null)
        }
    }
    
    private fun startMonitoringForRegion(call: MethodCall, result: Result) {
        try {
            val identifier = call.argument<String>("identifier")
            val uuid = call.argument<String>("uuid")
            val major = call.argument<String>("major")
            val minor = call.argument<String>("minor")
            
            if (identifier == null || uuid == null) {
                result.error("INVALID_ARGUMENTS", "Identifier e UUID sono richiesti", null)
                return
            }
            
            Log.d(TAG, "Avvio monitoraggio regione specifica: ID=$identifier, UUID=$uuid, Major=$major, Minor=$minor")
            logRepository.addLog("Avvio monitoraggio regione specifica: ID=$identifier, UUID=$uuid, Major=$major, Minor=$minor")
            
            // Crea SelectedBeacon temporaneo per usare il metodo di creazione regione
            val tempBeacon = SelectedBeacon(uuid, major, minor, identifier)
            val region = RegionUtils.createRegionForBeacon(tempBeacon)
            
            // Avvia il monitoraggio per questa regione
            beaconManager.startRangingBeaconsInRegion(region)
            beaconManager.startMonitoringBeaconsInRegion(region)
            
            Log.d(TAG, "Monitoraggio avviato per regione: ${region.uniqueId}")
            logRepository.addLog("Monitoraggio avviato per regione: ${region.uniqueId}")
            
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'avvio del monitoraggio per regione: ${e.message}", e)
            logRepository.addLog("ERRORE nell'avvio del monitoraggio per regione: ${e.message}")
            result.error("START_REGION_ERROR", "Errore nell'avvio del monitoraggio per regione: ${e.message}", null)
        }
    }

    private fun stopMonitoringForRegion(call: MethodCall, result: Result) {
        try {
            val identifier = call.argument<String>("identifier")
            
            if (identifier == null) {
                result.error("INVALID_ARGUMENTS", "Identifier è richiesto", null)
                return
            }
            
            Log.d(TAG, "Arresto monitoraggio regione: $identifier")
            logRepository.addLog("Arresto monitoraggio regione: $identifier")
            
            // Trova la regione con questo identifier
            val region = beaconManager.monitoredRegions.find { it.uniqueId == identifier }
            
            if (region != null) {
                beaconManager.stopRangingBeaconsInRegion(region)
                beaconManager.stopMonitoringBeaconsInRegion(region)
                Log.d(TAG, "Monitoraggio fermato per regione: ${region.uniqueId}")
                logRepository.addLog("Monitoraggio fermato per regione: ${region.uniqueId}")
                result.success(true)
            } else {
                Log.d(TAG, "Nessuna regione trovata con identifier: $identifier")
                logRepository.addLog("Nessuna regione trovata con identifier: $identifier")
                result.error("REGION_NOT_FOUND", "Nessuna regione trovata con identifier: $identifier", null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'arresto del monitoraggio per regione: ${e.message}", e)
            logRepository.addLog("ERRORE nell'arresto del monitoraggio per regione: ${e.message}")
            result.error("STOP_REGION_ERROR", "Errore nell'arresto del monitoraggio per regione: ${e.message}", null)
        }
    }

    private fun getMonitoredRegions(result: Result) {
        try {
            val regions = beaconManager.monitoredRegions
            val regionMaps = regions.map { region ->
                val map = hashMapOf<String, Any?>()
                map["identifier"] = region.uniqueId
                
                if (region.id1 != null) {
                    map["uuid"] = region.id1.toString()
                }
                
                if (region.id2 != null) {
                    map["major"] = region.id2.toString()
                }
                
                if (region.id3 != null) {
                    map["minor"] = region.id3.toString()
                }
                
                map
            }
            
            Log.d(TAG, "Ottenute ${regions.size} regioni monitorate")
            logRepository.addLog("Ottenute ${regions.size} regioni monitorate")
            result.success(regionMaps)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel recupero delle regioni monitorate: ${e.message}", e)
            logRepository.addLog("ERRORE nel recupero delle regioni monitorate: ${e.message}")
            result.error("GET_REGIONS_ERROR", "Errore nel recupero delle regioni monitorate: ${e.message}", null)
        }
    }

    private fun isBluetoothEnabled(result: Result) {
      try {
          // Ottieni il BluetoothAdapter
          val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
          val isEnabled = bluetoothAdapter?.isEnabled ?: false
          
          Log.d(TAG, "Stato Bluetooth: ${if (isEnabled) "ATTIVO" else "DISATTIVATO"}")
          logRepository.addLog("Stato Bluetooth: ${if (isEnabled) "ATTIVO" else "DISATTIVATO"}")
          
          result.success(isEnabled)
      } catch (e: Exception) {
          Log.e(TAG, "Errore nel controllo dello stato Bluetooth: ${e.message}", e)
          logRepository.addLog("ERRORE nel controllo dello stato Bluetooth: ${e.message}")
          result.error("BLUETOOTH_ERROR", "Errore nel controllo dello stato Bluetooth: ${e.message}", null)
      }
  }

  private fun isLocationEnabled(result: Result) {
      try {
          val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
          val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
          val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
          
          val isEnabled = isGpsEnabled || isNetworkEnabled
          Log.d(TAG, "Stato Location: ${if (isEnabled) "ATTIVO" else "DISATTIVATO"} (GPS: $isGpsEnabled, Network: $isNetworkEnabled)")
          logRepository.addLog("Stato Location: ${if (isEnabled) "ATTIVO" else "DISATTIVATO"} (GPS: $isGpsEnabled, Network: $isNetworkEnabled)")
          
          result.success(isEnabled)
      } catch (e: Exception) {
          Log.e(TAG, "Errore nel controllo dello stato Location: ${e.message}", e)
          logRepository.addLog("ERRORE nel controllo dello stato Location: ${e.message}")
          result.error("LOCATION_ERROR", "Errore nel controllo dello stato Location: ${e.message}", null)
      }
  }

  private fun checkPermissions(result: Result) {
    try {
        val permissionsMap = mutableMapOf<String, Boolean>()
        
        // Controlla i permessi di localizzazione
        permissionsMap["location"] = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        // Controlla i permessi Bluetooth per Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsMap["bluetoothScan"] = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            
            permissionsMap["bluetoothConnect"] = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            permissionsMap["bluetoothScan"] = true
            permissionsMap["bluetoothConnect"] = true
        }
        
        // Controlla i permessi di notifica per Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsMap["notifications"] = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            permissionsMap["notifications"] = true
        }
        
        // Controlla il permesso di localizzazione in background per Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsMap["backgroundLocation"] = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            permissionsMap["backgroundLocation"] = true
        }
        
        Log.d(TAG, "Stato permessi: $permissionsMap")
        logRepository.addLog("Stato permessi: $permissionsMap")
        
        result.success(permissionsMap)
    } catch (e: Exception) {
        Log.e(TAG, "Errore nel controllo dei permessi: ${e.message}", e)
        logRepository.addLog("ERRORE nel controllo dei permessi: ${e.message}")
        result.error("PERMISSIONS_ERROR", "Errore nel controllo dei permessi: ${e.message}", null)
    }
}

private fun requestExactAlarmPermission(result: Result) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                if (activity != null) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    intent.data = Uri.parse("package:" + context.packageName)
                    activity?.startActivity(intent)
                    logRepository.addLog("Richiesto permesso per allarmi esatti")
                    result.success(true)
                } else {
                    logRepository.addLog("Activity non disponibile per richiesta permesso allarmi")
                    result.error("ACTIVITY_NULL", "Activity necessaria per richiedere permesso allarmi", null)
                }
            } else {
                logRepository.addLog("Permesso allarmi esatti già concesso")
                result.success(true)
            }
        } else {
            // Per versioni precedenti di Android, il permesso è automaticamente concesso
            result.success(true)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Errore nella richiesta del permesso allarmi: ${e.message}", e)
        logRepository.addLog("ERRORE: ${e.message}")
        result.error("ALARM_PERMISSION_ERROR", "Errore nella richiesta del permesso allarmi: ${e.message}", null)
    }
}

  private fun requestPermissions(result: Result) {
      if (activity != null) {
          try {
              Log.d(TAG, "Richiesta permessi")
              logRepository.addLog("Richiesta permessi")
              
              // Nota: Flutter gestisce la richiesta dei permessi tramite la libreria permission_handler
              // Qui possiamo solo verificare lo stato attuale
              checkPermissions(result)
          } catch (e: Exception) {
              Log.e(TAG, "Errore nella richiesta dei permessi: ${e.message}", e)
              logRepository.addLog("ERRORE nella richiesta dei permessi: ${e.message}")
              result.error("PERMISSION_REQUEST_ERROR", "Errore nella richiesta dei permessi: ${e.message}", null)
          }
      } else {
          Log.e(TAG, "Activity non disponibile per la richiesta dei permessi")
          logRepository.addLog("Activity non disponibile per la richiesta dei permessi")
          result.error("ACTIVITY_UNAVAILABLE", "Activity necessaria per richiedere i permessi", null)
      }
  }

  private fun isBatteryOptimizationIgnored(result: Result) {
      try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
              val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
              val packageName = context.packageName
              val isIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
              
              Log.d(TAG, "Ottimizzazione batteria ignorata: $isIgnored")
              logRepository.addLog("Ottimizzazione batteria ignorata: $isIgnored")
              
              result.success(isIgnored)
          } else {
              // Versioni precedenti non hanno questa restrizione
              result.success(true)
          }
      } catch (e: Exception) {
          Log.e(TAG, "Errore nel controllo dell'ottimizzazione batteria: ${e.message}", e)
          logRepository.addLog("ERRORE nel controllo dell'ottimizzazione batteria: ${e.message}")
          result.error("BATTERY_OPT_ERROR", "Errore nel controllo dell'ottimizzazione batteria: ${e.message}", null)
      }
  }

  private fun requestIgnoreBatteryOptimization(result: Result) {
      try {
          if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
              val packageName = context.packageName
              val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
              intent.data = Uri.parse("package:$packageName")
              
              Log.d(TAG, "Avvio activity per richiesta ignore battery optimization")
              logRepository.addLog("Avvio activity per richiesta ignore battery optimization")
              
              activity?.startActivity(intent)
              result.success(true)
          } else {
              if (activity == null) {
                  Log.e(TAG, "Activity non disponibile per la richiesta di ottimizzazione batteria")
                  logRepository.addLog("Activity non disponibile per la richiesta di ottimizzazione batteria")
                  result.error("ACTIVITY_NULL", "Activity necessaria per richiedere ottimizzazione batteria", null)
              } else {
                  // Versioni precedenti non hanno questa restrizione
                  result.success(true)
              }
          }
      } catch (e: Exception) {
          Log.e(TAG, "Errore nella richiesta di ignorare l'ottimizzazione batteria: ${e.message}", e)
          logRepository.addLog("ERRORE nella richiesta di ignorare l'ottimizzazione batteria: ${e.message}")
          result.error("BATTERY_OPT_REQUEST_ERROR", "Errore nella richiesta di ignorare l'ottimizzazione batteria: ${e.message}", null)
      }
  }
  
  private fun enableDebugMode(result: Result) {
      try {
          // Attiva il debug della libreria AltBeacon
          BeaconManager.setDebug(true)
          
          Log.d(TAG, "Modalità debug attivata")
          logRepository.addLog("Modalità debug attivata")
          
          // Log più dettagliato per ogni beacon
          beaconManager.addRangeNotifier { beacons, region ->
              Log.d(TAG, "DEBUG RANGE: Trovati ${beacons.size} beacon nella regione ${region.uniqueId}")
              beacons.forEach { beacon ->
                  Log.d(TAG, "DEBUG Beacon: UUID=${beacon.id1}, Major=${beacon.id2}, Minor=${beacon.id3}, " +
                          "Dist=${String.format("%.2f", beacon.distance)}m, RSSI=${beacon.rssi}, " +
                          "TxPower=${beacon.txPower}")
              }
          }
          
          result.success(true)
      } catch (e: Exception) {
          Log.e(TAG, "Errore nell'attivazione debug: ${e.message}", e)
          logRepository.addLog("ERRORE nell'attivazione debug: ${e.message}")
          result.error("DEBUG_ERROR", "Errore nell'attivazione debug: ${e.message}", null)
      }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
      methodChannel.setMethodCallHandler(null)
      beaconsEventChannel.setStreamHandler(null)
      monitoringEventChannel.setStreamHandler(null)
      
      Log.d(TAG, "Plugin staccato dal motore Flutter")
      logRepository.addLog("Plugin staccato dal motore Flutter")
      
      // Ferma il monitoraggio se attivo
      /*if (::beaconManager.isInitialized && PreferenceUtils.isMonitoringEnabled(context)) {
          try {
              // Ferma tutti i ranging e monitoraggi
              beaconManager.rangedRegions.forEach { region ->
                  beaconManager.stopRangingBeaconsInRegion(region)
              }
              beaconManager.monitoredRegions.forEach { region ->
                  beaconManager.stopMonitoringBeaconsInRegion(region)
              }
              
              Log.d(TAG, "Fermati tutti i ranging e monitoraggi al distacco")
              logRepository.addLog("Fermati tutti i ranging e monitoraggi al distacco")
              
              // Ferma il servizio in foreground
              val serviceIntent = Intent(context, BeaconMonitoringService::class.java)
              context.stopService(serviceIntent)
              logRepository.addLog("Servizio di monitoraggio fermato al distacco")
          } catch (e: Exception) {
              Log.e(TAG, "Errore nell'arresto del monitoraggio al distacco: ${e.message}", e)
              logRepository.addLog("ERRORE nell'arresto del monitoraggio al distacco: ${e.message}")
          }
      }*/
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
      activity = binding.activity
      Log.d(TAG, "Plugin allegato ad Activity")
      logRepository.addLog("Plugin allegato ad Activity")
      
      // Verifica se il monitoraggio era attivo e ripristinalo
      if (PreferenceUtils.isMonitoringEnabled(context)) {
          Log.d(TAG, "Ripristino monitoraggio al ricollegamento all'Activity")
          logRepository.addLog("Ripristino monitoraggio al ricollegamento all'Activity")
          
          try {
              // Avvia il servizio in foreground
              val serviceIntent = Intent(context, BeaconMonitoringService::class.java)
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                  context.startForegroundService(serviceIntent)
              } else {
                  context.startService(serviceIntent)
              }
              logRepository.addLog("Servizio di monitoraggio riavviato")
          } catch (e: Exception) {
              Log.e(TAG, "Errore nel riavvio del servizio: ${e.message}", e)
              logRepository.addLog("ERRORE nel riavvio del servizio: ${e.message}")
          }
      }
  }

  override fun onDetachedFromActivityForConfigChanges() {
      activity = null
      Log.d(TAG, "Plugin staccato da Activity per cambio configurazione")
      logRepository.addLog("Plugin staccato da Activity per cambio configurazione")
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
      activity = binding.activity
      Log.d(TAG, "Plugin ricollegato ad Activity dopo cambio configurazione")
      logRepository.addLog("Plugin ricollegato ad Activity dopo cambio configurazione")
  }

  override fun onDetachedFromActivity() {
      activity = null
      Log.d(TAG, "Plugin staccato da Activity")
      logRepository.addLog("Plugin staccato da Activity")
  }
}