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
import com.brux88.brux88_beacon.util.NotificationUtils
import org.altbeacon.beacon.BeaconConsumer
import android.content.ServiceConnection

class Brux88BeaconPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, RangeNotifier, BeaconConsumer  {
    private val TAG = "Brux88BeaconPlugin"
    private var beaconBootstrapper: BeaconBootstrapper? = null
    private var isBeaconServiceConnected = false
    private var pendingOperations = mutableListOf<() -> Unit>()

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
    override fun onBeaconServiceConnect() {
        Log.d(TAG, "🔗 BeaconService connesso!")
        logRepository.addLog("BEACON SERVICE: Connesso")
        
        isBeaconServiceConnected = true
        
        // Esegui tutte le operazioni in coda
        pendingOperations.forEach { operation ->
            try {
                operation.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Errore esecuzione operazione in coda: ${e.message}")
            }
        }
        pendingOperations.clear()
        
        Log.d(TAG, "✅ BeaconService pronto per operazioni")
        logRepository.addLog("BEACON SERVICE: Pronto per operazioni")
    }

    override fun getApplicationContext(): Context {
        return context.applicationContext
    }

    override fun unbindService(connection: ServiceConnection) {
        Log.d(TAG, "🔌 Unbinding BeaconService")
        try {
            context.unbindService(connection)
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante unbind: ${e.message}")
        }
    }

    override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean {
        Log.d(TAG, "🔗 Binding BeaconService")
        return try {
            context.bindService(intent, connection, flags)
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante bind: ${e.message}")
            false
        }
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
                    isMonitoringRunning(result)
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
                "setShowDetectionNotifications" -> {
                    val show = call.arguments as? Boolean ?: true
                    setShowDetectionNotifications(show, result)
                }
                "startBackgroundService" -> {
                    startBackgroundService(result)
                }
                "stopBackgroundService" -> {
                    stopBackgroundService(result)
                }
                "isBackgroundServiceRunning" -> {
                    isBackgroundServiceRunning(result)
                }
                "setBackgroundServiceEnabled" -> {
                    val enabled = call.arguments as? Boolean ?: false
                    setBackgroundServiceEnabled(enabled, result)
                }
                "isBackgroundServiceEnabled" -> {
                    isBackgroundServiceEnabled(result)
                }
                "getDetailedPermissions" -> {
                    getDetailedPermissions(result)
                }
                "startForegroundMonitoringOnly" -> {
                    startForegroundMonitoringOnly(result)
                }
                "stopForegroundMonitoringOnly" -> {
                    stopForegroundMonitoringOnly(result)
                }
                "isForegroundMonitoringRunning" -> {
                    isForegroundMonitoringRunning(result)
                }
                "setAutoStartEnabled" -> {
                    val enabled = call.arguments as? Boolean ?: false
                    setAutoStartEnabled(enabled, result)
                }
                "setWatchdogEnabled" -> {
                    val enabled = call.arguments as? Boolean ?: false
                    setWatchdogEnabled(enabled, result)
                }
                "setAutoRestartEnabled" -> {
                    val enabled = call.arguments as? Boolean ?: false
                    setAutoRestartEnabled(enabled, result)
                }
                "setAutoRestartEnabled" -> {
                    val enabled = call.arguments as? Boolean ?: false
                    setAutoRestartEnabled(enabled, result)
                }
                "isAutoRestartEnabled" -> {
                    isAutoRestartEnabled(result)
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
    private fun setupRecurringAlarmInternal() {
        try {
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
            
            logRepository.addLog("AUTO-RESTART: Allarme ricorrente impostato")
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'impostazione allarme ricorrente: ${e.message}", e)
            logRepository.addLog("ERRORE AUTO-RESTART: Allarme ricorrente: ${e.message}")
        }
    }

    private fun cancelRecurringAlarmInternal() {
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
            
            logRepository.addLog("AUTO-RESTART: Allarme ricorrente cancellato")
        } catch (e: Exception) {
            Log.e(TAG, "Errore nella cancellazione allarme ricorrente: ${e.message}", e)
            logRepository.addLog("ERRORE AUTO-RESTART: Cancellazione allarme: ${e.message}")
        }
    }

    // Aggiungi un metodo per ottenere lo stato
    private fun isAutoRestartEnabled(result: Result) {
        try {
            val isEnabled = PreferenceUtils.isAutoRestartEnabled(context)
            Log.d(TAG, "Auto-restart abilitato: $isEnabled")
            logRepository.addLog("AUTO-RESTART: Stato richiesto - $isEnabled")
            result.success(isEnabled)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel controllo auto-restart: ${e.message}", e)
            logRepository.addLog("ERRORE AUTO-RESTART: Controllo stato: ${e.message}")
            result.error("AUTO_RESTART_CHECK_ERROR", "Errore nel controllo auto-restart: ${e.message}", null)
        }
    }
    private fun setAutoRestartEnabled(enabled: Boolean, result: Result) {
        try {
            PreferenceUtils.setAutoRestartEnabled(context, enabled)
            
            if (enabled) {
                // Se abilitato, imposta tutti i meccanismi di riavvio
                logRepository.addLog("AUTO-RESTART: Abilitazione meccanismi di riavvio")
                
                // Abilita il watchdog
                PreferenceUtils.setWatchdogEnabled(context, true)
                
                // Imposta l'allarme ricorrente se il monitoraggio è attivo
                if (PreferenceUtils.isMonitoringEnabled(context) || PreferenceUtils.isBackgroundServiceEnabled(context)) {
                    setupRecurringAlarmInternal()
                }
                
            } else {
                // Se disabilitato, ferma tutti i meccanismi di riavvio
                logRepository.addLog("AUTO-RESTART: Disabilitazione meccanismi di riavvio")
                
                // Disabilita il watchdog
                PreferenceUtils.setWatchdogEnabled(context, false)
                
                // Cancella l'allarme ricorrente
                cancelRecurringAlarmInternal()
            }
            
            logRepository.addLog("AUTO-RESTART: ${if (enabled) "abilitato" else "disabilitato"}")
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'impostazione auto-restart: ${e.message}", e)
            logRepository.addLog("ERRORE AUTO-RESTART: ${e.message}")
            result.error("AUTO_RESTART_ERROR", "Errore nell'impostazione auto-restart: ${e.message}", null)
        }
    }
    private fun setWatchdogEnabled(enabled: Boolean, result: Result) {
        try {
            PreferenceUtils.setWatchdogEnabled(context, enabled)
            
            if (enabled) {
                // Imposta il watchdog se abilitato
                setupServiceWatchdog(result)
            } else {
                // Cancella il watchdog se disabilitato
                cancelServiceWatchdog(result)
            }
            
            logRepository.addLog("Watchdog servizio ${if (enabled) "abilitato" else "disabilitato"}")
            result.success(true)
        } catch (e: Exception) {
            result.error("WATCHDOG_ERROR", "Errore nell'impostazione watchdog: ${e.message}", null)
        }
    }

    private fun cancelServiceWatchdog(result: Result) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ServiceWatchdogReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )

            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            
            logRepository.addLog("Watchdog per il servizio cancellato")
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nella cancellazione del watchdog: ${e.message}", e)
            result.error("WATCHDOG_CANCEL_ERROR", "Errore nella cancellazione del watchdog: ${e.message}", null)
        }
    }


    private fun setAutoStartEnabled(enabled: Boolean, result: Result) {
        try {
            PreferenceUtils.setAutoStartEnabled(context, enabled)
            logRepository.addLog("Auto-start impostato a: $enabled")
            result.success(true)
        } catch (e: Exception) {
            result.error("AUTO_START_ERROR", "Errore nell'impostazione auto-start: ${e.message}", null)
        }
    }
        private fun startForegroundMonitoringOnly(result: Result) {
        try {
            Log.d(TAG, "Avvio monitoraggio SOLO foreground")
            logRepository.addLog("Avvio monitoraggio SOLO foreground")
            
            if (!::beaconManager.isInitialized) {
                result.error("NOT_INITIALIZED", "BeaconManager non inizializzato", null)
                return
            }
            
            // IMPORTANTE: Controlla se il servizio è connesso
            if (!isBeaconServiceConnected) {
                Log.d(TAG, "⏳ BeaconService non ancora connesso, operazione in coda")
                logRepository.addLog("FOREGROUND: Servizio non connesso, operazione in coda")
                
                // Metti l'operazione in coda
                pendingOperations.add {
                    startForegroundMonitoringOnlyInternal(result)
                }
                return
            }
            
            // Se il servizio è connesso, esegui immediatamente
            startForegroundMonitoringOnlyInternal(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'avvio del monitoraggio foreground: ${e.message}", e)
            result.error("START_FOREGROUND_MONITORING_ERROR", "Errore: ${e.message}", null)
        }
    }
    

    private fun setupBeaconCallbacks() {
        try {
            // IMPORTANTE: Rimuovi i notifier esistenti per evitare duplicati
            beaconManager.removeAllRangeNotifiers()
            beaconManager.removeAllMonitorNotifiers()
            
            // Range notifier per rilevare i beacon e le loro distanze
            beaconManager.addRangeNotifier(this) // Usa l'implementazione esistente
            
            // Monitor notifier per eventi di entrata/uscita dalle regioni
            beaconManager.addMonitorNotifier(object : MonitorNotifier {
                override fun didEnterRegion(region: Region) {
                    Log.d(TAG, "PLUGIN: Entrato nella regione ${region.uniqueId}")
                    logRepository.addLog("PLUGIN: Entrato nella regione ${region.uniqueId}")
                    
                    // Invia evento al Dart
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        monitoringSink?.success("INSIDE")
                    }
                }

                override fun didExitRegion(region: Region) {
                    Log.d(TAG, "PLUGIN: Uscito dalla regione ${region.uniqueId}")
                    logRepository.addLog("PLUGIN: Uscito dalla regione ${region.uniqueId}")
                    
                    // Invia evento al Dart
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        monitoringSink?.success("OUTSIDE")
                    }
                }

                override fun didDetermineStateForRegion(state: Int, region: Region) {
                    val stateStr = if (state == MonitorNotifier.INSIDE) "INSIDE" else "OUTSIDE"
                    Log.d(TAG, "PLUGIN: Stato regione determinato: $stateStr per ${region.uniqueId}")
                    logRepository.addLog("PLUGIN: Stato regione: $stateStr per ${region.uniqueId}")
                    
                    // Invia evento al Dart
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        monitoringSink?.success(stateStr)
                    }
                }
            })
            
            Log.d(TAG, "Callbacks beacon configurati correttamente")
            logRepository.addLog("Callbacks beacon configurati")
            
        } catch (e: Exception) {
            Log.e(TAG, "Errore nella configurazione dei callbacks: ${e.message}", e)
            logRepository.addLog("ERRORE nella configurazione dei callbacks: ${e.message}")
        }
    }

private fun startForegroundMonitoringOnlyInternal(result: Result) {
        try {
            Log.d(TAG, "🚀 Avvio effettivo monitoraggio foreground")
            logRepository.addLog("FOREGROUND: Avvio effettivo")
            
            // Setup callbacks
            setupBeaconCallbacks()
            
            // Determina regione
            val selectedBeacon = PreferenceUtils.getSelectedBeacon(context)
            val region = if (selectedBeacon != null && PreferenceUtils.isSelectedBeaconEnabled(context)) {
                Log.d(TAG, "Monitoraggio beacon specifico: ${selectedBeacon.uuid}")
                RegionUtils.createRegionForBeacon(selectedBeacon)
            } else {
                Log.d(TAG, "Monitoraggio di tutti i beacon")
                RegionUtils.ALL_BEACONS_REGION
            }
            
            activeRegion = region
            
            // Configura scan periods
            beaconManager.foregroundBetweenScanPeriod = 0
            beaconManager.foregroundScanPeriod = 1100
            
            try {
                beaconManager.updateScanPeriods()
            } catch (e: Exception) {
                Log.w(TAG, "Errore aggiornamento scan periods: ${e.message}")
            }
            
            // AVVIA ranging e monitoring - ora il servizio è connesso!
            beaconManager.startRangingBeaconsInRegion(region)
            beaconManager.startMonitoringBeaconsInRegion(region)
            
            Log.d(TAG, "✅ Ranging avviato nella regione: ${region.uniqueId}")
            logRepository.addLog("FOREGROUND: Ranging avviato in ${region.uniqueId}")
            
            // Salva stato
            PreferenceUtils.setForegroundMonitoringEnabled(context, true)
            
            // Log di debug
            Log.d(TAG, "📊 Configurazione finale:")
            Log.d(TAG, "  Servizio connesso: $isBeaconServiceConnected")
            Log.d(TAG, "  Regioni in ranging: ${beaconManager.rangedRegions.size}")
            Log.d(TAG, "  Regioni monitorate: ${beaconManager.monitoredRegions.size}")
            
            result.success(true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'avvio effettivo foreground: ${e.message}", e)
            logRepository.addLog("ERRORE avvio effettivo foreground: ${e.message}")
            result.error("START_FOREGROUND_INTERNAL_ERROR", "Errore avvio: ${e.message}", null)
        }
    }
    private fun stopForegroundMonitoringOnly(result: Result) {
        try {
            Log.d(TAG, "Arresto monitoraggio SOLO foreground")
            logRepository.addLog("Arresto monitoraggio SOLO foreground")
            
            // Ferma SOLO il ranging e il monitoraggio (NON il servizio background)
            if (::activeRegion.isInitialized) {
                beaconManager.stopRangingBeaconsInRegion(activeRegion)
                beaconManager.stopMonitoringBeaconsInRegion(activeRegion)
                Log.d(TAG, "Ranging e monitoraggio foreground fermati nella regione: ${activeRegion.uniqueId}")
                logRepository.addLog("Ranging e monitoraggio foreground fermati nella regione: ${activeRegion.uniqueId}")
            } else {
                // Se non abbiamo una regione attiva, ferma tutto il ranging
                beaconManager.rangedRegions.forEach { region ->
                    beaconManager.stopRangingBeaconsInRegion(region)
                }
                beaconManager.monitoredRegions.forEach { region ->
                    beaconManager.stopMonitoringBeaconsInRegion(region)
                }
                Log.d(TAG, "Fermati tutti i ranging e monitoraggi foreground")
                logRepository.addLog("Fermati tutti i ranging e monitoraggi foreground")
            }
            
            // Aggiorna lo stato di monitoraggio FOREGROUND
            PreferenceUtils.setForegroundMonitoringEnabled(context, false)
            
            // NON fermare il servizio background qui - rimane indipendente
            
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'arresto del monitoraggio foreground: ${e.message}", e)
            logRepository.addLog("ERRORE nell'arresto del monitoraggio foreground: ${e.message}")
            result.error("STOP_FOREGROUND_MONITORING_ERROR", "Errore nell'arresto del monitoraggio foreground: ${e.message}", null)
        }
    }

    private fun isForegroundMonitoringRunning(result: Result) {
        try {
            // Controlla se ci sono regioni in ranging (foreground)
            val hasRangedRegions = beaconManager.rangedRegions.isNotEmpty()
            val isForegroundEnabled = PreferenceUtils.isForegroundMonitoringEnabled(context)
            
            val isRunning = hasRangedRegions && isForegroundEnabled
            
            Log.d(TAG, "Stato monitoraggio foreground: rangedRegions=$hasRangedRegions, enabled=$isForegroundEnabled, running=$isRunning")
            logRepository.addLog("Stato monitoraggio foreground: ranging=$hasRangedRegions, enabled=$isForegroundEnabled, running=$isRunning")
            
            result.success(isRunning)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel controllo stato monitoraggio foreground: ${e.message}", e)
            logRepository.addLog("ERRORE nel controllo stato monitoraggio foreground: ${e.message}")
            result.error("FOREGROUND_MONITORING_CHECK_ERROR", "Errore nel controllo stato monitoraggio foreground: ${e.message}", null)
        }
    }
    private fun getDetailedPermissions(result: Result) {
        try {
            val permissionsMap = mutableMapOf<String, Any>()
            
            // Controlla i permessi di localizzazione
            val locationGranted = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            val coarseLocationGranted = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            permissionsMap["location"] = locationGranted
            permissionsMap["coarseLocation"] = coarseLocationGranted
            
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
            
            // Aggiungi informazioni aggiuntive sullo stato del sistema
            permissionsMap["androidVersion"] = Build.VERSION.SDK_INT
            permissionsMap["packageName"] = context.packageName
            
            // Controlla se la batteria è ottimizzata
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                permissionsMap["batteryOptimizationIgnored"] = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                permissionsMap["batteryOptimizationIgnored"] = true
            }
            
            // Controlla se può schedulare allarmi esatti (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                permissionsMap["canScheduleExactAlarms"] = alarmManager.canScheduleExactAlarms()
            } else {
                permissionsMap["canScheduleExactAlarms"] = true
            }
            
            Log.d(TAG, "Permessi dettagliati: $permissionsMap")
            logRepository.addLog("Permessi dettagliati ottenuti: ${permissionsMap.size} elementi")
            
            result.success(permissionsMap)
            
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel recupero permessi dettagliati: ${e.message}", e)
            logRepository.addLog("ERRORE nel recupero permessi dettagliati: ${e.message}")
            result.error("DETAILED_PERMISSIONS_ERROR", "Errore nel recupero permessi dettagliati: ${e.message}", null)
        }
    }
    private fun isMonitoringRunning(result: Result) {
        try {
            // Verifica prima lo stato salvato nelle preferenze
           // val isEnabledInPrefs = PreferenceUtils.isMonitoringEnabled(context)
            
            // Poi verifica se il servizio è effettivamente in esecuzione
            val isServiceRunning = isServiceRunning(context, BeaconMonitoringService::class.java)
            
            // Considera attivo il monitoraggio solo se entrambe le condizioni sono vere
            val isActive = isServiceRunning && isServiceRunning
            
            Log.d(TAG, "Stato monitoraggio: Servizio=$isServiceRunning, Attivo=$isActive")
            logRepository.addLog("Controllo stato monitoraggio: Servizio=$isServiceRunning, Attivo=$isActive")
            
            result.success(isActive)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel controllare lo stato del monitoraggio: ${e.message}", e)
            logRepository.addLog("ERRORE nel controllo stato monitoraggio: ${e.message}")
            result.error("MONITORING_CHECK_ERROR", "Errore nel controllare lo stato del monitoraggio: ${e.message}", null)
        }
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        try {
            // Primo tentativo: usa il flag statico nel servizio
            if (BeaconMonitoringService::class.java == serviceClass && BeaconMonitoringService.isRunning()) {
                return true
            }
            
            // Secondo tentativo: usa il ActivityManager
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel verificare lo stato del servizio: ${e.message}")
        }
        return false
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
    private fun setShowDetectionNotifications(show: Boolean, result: Result) {
        try {
            PreferenceUtils.setShowDetectionNotifications(context, show)
            logRepository.addLog("Notifiche di rilevamento beacon ${if (show) "abilitate" else "disabilitate"}")
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'impostazione delle notifiche di rilevamento: ${e.message}", e)
            logRepository.addLog("ERRORE nell'impostazione delle notifiche di rilevamento: ${e.message}")
            result.error("NOTIFICATION_SETTING_ERROR", 
                        "Errore nell'impostazione delle notifiche di rilevamento: ${e.message}", null)
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
            isBeaconServiceConnected = false
            pendingOperations.clear()

            // AGGIUNGI: Reset esplicito dei flag di monitoraggio
            PreferenceUtils.setMonitoringEnabled(context, false)
            PreferenceUtils.setForegroundMonitoringEnabled(context, false)
            PreferenceUtils.setBackgroundServiceEnabled(context, false)
            PreferenceUtils.setPendingRestart(context, false)
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
             // IMPORTANTE: Bind al BeaconService
            Log.d(TAG, "🔗 Binding al BeaconService...")
            logRepository.addLog("BINDING: Connessione al BeaconService")
            
            beaconManager.bind(this)
            val uniqueId = "myUniqueId"
            val region = Region(uniqueId, null, null, null)
            activeRegion = region
            
            // Configurazione notifier per i beacon
            setupBeaconCallbacks()
            
            // Inizializza il BeaconBootstrapper
            beaconBootstrapper = BeaconBootstrapper(context.applicationContext)
            beaconBootstrapper?.setEventSink(monitoringSink)
            //beaconBootstrapper?.startBootstrapping(region)
    
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
           /*  if (PreferenceUtils.hasPendingRestart(context) && PreferenceUtils.isMonitoringEnabled(context)) {
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

            }*/
               // Inizializza lo stato delle notifiche
            PreferenceUtils.setShowDetectionNotifications(
                context,
                PreferenceUtils.shouldShowDetectionNotifications(context)
            )
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
 
    // Implementazione di RangeNotifier
    override fun didRangeBeaconsInRegion(beacons: Collection<Beacon>, region: Region) {
        Log.d(TAG, "RANGE CALLBACK: trovati ${beacons.size} beacon nella regione ${region.uniqueId}")
        
        if (beacons.isNotEmpty()) {
            // Log dettagliato per ogni beacon
            beacons.forEachIndexed { index, beacon ->
                Log.d(TAG, "Beacon #${index + 1}:")
                Log.d(TAG, "  UUID: ${beacon.id1}")
                Log.d(TAG, "  Major: ${beacon.id2}")
                Log.d(TAG, "  Minor: ${beacon.id3}")
                Log.d(TAG, "  Distance: ${String.format("%.2f", beacon.distance)}m")
                Log.d(TAG, "  RSSI: ${beacon.rssi} dBm")
                Log.d(TAG, "  TxPower: ${beacon.txPower}")
                
                logRepository.addLog("BEACON: ${beacon.id1} a ${String.format("%.2f", beacon.distance)}m")
            }
            
            // Aggiorna il repository beacon
            beaconRepository.updateBeacons(beacons, region)
            
            // IMPORTANTE: Invia i beacon al Dart tramite l'event channel
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    val beaconMaps = beacons.map { beacon ->
                        mapOf(
                            "uuid" to beacon.id1.toString(),
                            "major" to beacon.id2?.toString(),
                            "minor" to beacon.id3?.toString(),
                            "distance" to beacon.distance,
                            "rssi" to beacon.rssi,
                            "txPower" to beacon.txPower
                        )
                    }
                    
                    Log.d(TAG, "Inviando ${beaconMaps.size} beacon a Flutter")
                    beaconsSink?.success(beaconMaps)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Errore nell'invio beacon a Flutter: ${e.message}", e)
                    logRepository.addLog("ERRORE invio beacon a Flutter: ${e.message}")
                }
            }
        } else {
            Log.d(TAG, "RANGE CALLBACK: nessun beacon trovato nella regione ${region.uniqueId}")
            // Invia lista vuota a Flutter
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                beaconsSink?.success(emptyList<Map<String, Any>>())
            }
        }
    }

    private fun startMonitoring(result: Result) {
        try {
            Log.d(TAG, "Avvio monitoraggio COMPLETO (foreground + background)")
            logRepository.addLog("Avvio monitoraggio COMPLETO (foreground + background)")
            
            // Prima avvia il monitoraggio foreground
            startForegroundMonitoringOnly(object : Result {
                override fun success(foregroundResult: Any?) {
                    if (foregroundResult == true) {
                        // Poi avvia il servizio background
                        startBackgroundService(object : Result {
                            override fun success(backgroundResult: Any?) {
                                val overallSuccess = (foregroundResult == true) && (backgroundResult == true)
                                Log.d(TAG, "Monitoraggio completo: foreground=${foregroundResult}, background=${backgroundResult}, overall=${overallSuccess}")
                                logRepository.addLog("Monitoraggio completo avviato: foreground=${foregroundResult}, background=${backgroundResult}")
                                result.success(overallSuccess)
                            }
                            
                            override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                                Log.e(TAG, "Errore avvio background durante monitoraggio completo: $errorMessage")
                                logRepository.addLog("ERRORE avvio background durante monitoraggio completo: $errorMessage")
                                // Anche se il background fallisce, il foreground è attivo
                                result.success(true)
                            }
                            
                            override fun notImplemented() {
                                result.notImplemented()
                            }
                        })
                    } else {
                        result.success(false)
                    }
                }
                
                override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                    result.error(errorCode, errorMessage, errorDetails)
                }
                
                override fun notImplemented() {
                    result.notImplemented()
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'avvio del monitoraggio completo: ${e.message}", e)
            logRepository.addLog("ERRORE nell'avvio del monitoraggio completo: ${e.message}")
            result.error("START_COMPLETE_MONITORING_ERROR", "Errore nell'avvio del monitoraggio completo: ${e.message}", null)
        }
    }

    // Modifica anche stopMonitoring per chiarire che ferma TUTTO:
    private fun stopMonitoring(result: Result) {
        try {
            Log.d(TAG, "Arresto monitoraggio COMPLETO (foreground + background)")
            logRepository.addLog("Arresto monitoraggio COMPLETO (foreground + background)")
            
            // Prima ferma il monitoraggio foreground
            stopForegroundMonitoringOnly(object : Result {
                override fun success(foregroundResult: Any?) {
                    // Poi ferma il servizio background
                    stopBackgroundService(object : Result {
                        override fun success(backgroundResult: Any?) {
                            val overallSuccess = (foregroundResult == true) && (backgroundResult == true)
                            Log.d(TAG, "Monitoraggio completo fermato: foreground=${foregroundResult}, background=${backgroundResult}, overall=${overallSuccess}")
                            logRepository.addLog("Monitoraggio completo fermato: foreground=${foregroundResult}, background=${backgroundResult}")
                            result.success(overallSuccess)
                        }
                        
                        override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                            Log.e(TAG, "Errore arresto background durante stop completo: $errorMessage")
                            logRepository.addLog("ERRORE arresto background durante stop completo: $errorMessage")
                            // Anche se il background fallisce, il foreground è stato fermato
                            result.success(true)
                        }
                        
                        override fun notImplemented() {
                            result.notImplemented()
                        }
                    })
                }
                
                override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                    result.error(errorCode, errorMessage, errorDetails)
                }
                
                override fun notImplemented() {
                    result.notImplemented()
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'arresto del monitoraggio completo: ${e.message}", e)
            logRepository.addLog("ERRORE nell'arresto del monitoraggio completo: ${e.message}")
            result.error("STOP_COMPLETE_MONITORING_ERROR", "Errore nell'arresto del monitoraggio completo: ${e.message}", null)
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
            
            // Su Android, Flutter gestisce la richiesta dei permessi tramite la libreria permission_handler
            // Qui restituiamo lo stato attuale dei permessi dopo aver verificato la situazione
            
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
            
            // Determina se tutti i permessi essenziali sono concessi
            val location = permissionsMap["location"] ?: false
            val bluetoothScan = permissionsMap["bluetoothScan"] ?: false
            val bluetoothConnect = permissionsMap["bluetoothConnect"] ?: false
            
            val allEssentialGranted = location && bluetoothScan && bluetoothConnect
            
            Log.d(TAG, "Stato permessi: location=$location, bluetoothScan=$bluetoothScan, bluetoothConnect=$bluetoothConnect, allGranted=$allEssentialGranted")
            logRepository.addLog("Stato permessi: location=$location, bluetooth=$bluetoothScan/$bluetoothConnect, granted=$allEssentialGranted")
            
            // Restituisce un bool che indica se tutti i permessi essenziali sono concessi
            result.success(allEssentialGranted)
            
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
        
        // UNBIND dal BeaconService
        try {
            if (::beaconManager.isInitialized && isBeaconServiceConnected) {
                Log.d(TAG, "🔌 Unbinding dal BeaconService")
                beaconManager.unbind(this)
                isBeaconServiceConnected = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'unbind: ${e.message}")
        }
    }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
      activity = binding.activity
      Log.d(TAG, "Plugin allegato ad Activity")
      logRepository.addLog("Plugin allegato ad Activity")
      
      // Verifica se il monitoraggio era attivo e ripristinalo
      /*if (PreferenceUtils.isMonitoringEnabled(context)) {
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
      }*/
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

  private fun startBackgroundService(result: Result) {
        try {
            Log.d(TAG, "Avvio servizio background")
            logRepository.addLog("Avvio servizio background")
            
            if (PreferenceUtils.isAutoRestartEnabled(context)) {
                setupRecurringAlarmInternal()
                logRepository.addLog("BACKGROUND: Allarme ricorrente abilitato (auto-restart attivo)")
            } else {
                logRepository.addLog("BACKGROUND: Allarme ricorrente disabilitato (auto-restart inattivo)")
            }
            // Verifica se il Bluetooth è abilitato
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                result.error("BLUETOOTH_DISABLED", "Bluetooth non disponibile o disabilitato", null)
                return
            }
            
            // Imposta il flag che il servizio background è abilitato
            PreferenceUtils.setBackgroundServiceEnabled(context, true)
            
            // Avvia il servizio in foreground
            val serviceIntent = Intent(context, BeaconMonitoringService::class.java)
            serviceIntent.putExtra("backgroundOnly", true)
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                logRepository.addLog("Servizio background avviato con successo")
                result.success(true)
            } catch (e: Exception) {
                Log.e(TAG, "Errore nell'avvio del servizio background: ${e.message}", e)
                logRepository.addLog("ERRORE nell'avvio del servizio background: ${e.message}")
                result.error("SERVICE_START_ERROR", "Errore nell'avvio del servizio background: ${e.message}", null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'avvio del servizio background: ${e.message}", e)
            logRepository.addLog("ERRORE nell'avvio del servizio background: ${e.message}")
            result.error("BACKGROUND_SERVICE_ERROR", "Errore nell'avvio del servizio background: ${e.message}", null)
        }
    }

    private fun stopBackgroundService(result: Result) {
        try {
            Log.d(TAG, "Arresto servizio background")
            logRepository.addLog("Arresto servizio background")
            cancelRecurringAlarmInternal()

            // Imposta il flag che il servizio background è disabilitato
            PreferenceUtils.setBackgroundServiceEnabled(context, false)
            
            // Ferma il servizio
            val serviceIntent = Intent(context, BeaconMonitoringService::class.java)
            context.stopService(serviceIntent)
            
            // Cancella anche l'allarme ricorrente se presente
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val alarmIntent = Intent(context, BeaconMonitoringService::class.java)
            val pendingIntent = PendingIntent.getService(
                context,
                0,
                alarmIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            
            logRepository.addLog("Servizio background arrestato con successo")
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'arresto del servizio background: ${e.message}", e)
            logRepository.addLog("ERRORE nell'arresto del servizio background: ${e.message}")
            result.error("BACKGROUND_SERVICE_STOP_ERROR", "Errore nell'arresto del servizio background: ${e.message}", null)
        }
    }

    private fun isBackgroundServiceRunning(result: Result) {
        try {
            val isServiceRunning = isServiceRunning(context, BeaconMonitoringService::class.java)
            val isEnabledInPrefs = PreferenceUtils.isBackgroundServiceEnabled(context)
            
            // Il servizio è considerato in esecuzione se è effettivamente attivo E abilitato nelle preferenze
            val isRunning = isServiceRunning && isEnabledInPrefs
            
            Log.d(TAG, "Stato servizio background: running=$isServiceRunning, enabled=$isEnabledInPrefs, active=$isRunning")
            logRepository.addLog("Stato servizio background: running=$isServiceRunning, enabled=$isEnabledInPrefs, active=$isRunning")
            
            result.success(isRunning)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel controllo stato servizio background: ${e.message}", e)
            logRepository.addLog("ERRORE nel controllo stato servizio background: ${e.message}")
            result.error("BACKGROUND_SERVICE_CHECK_ERROR", "Errore nel controllo stato servizio background: ${e.message}", null)
        }
    }

    private fun setBackgroundServiceEnabled(enabled: Boolean, result: Result) {
        try {
            Log.d(TAG, "Impostazione servizio background abilitato: $enabled")
            logRepository.addLog("Impostazione servizio background abilitato: $enabled")
            
            PreferenceUtils.setBackgroundServiceEnabled(context, enabled)
            
            if (enabled) {
                // Se abilitato, avvia il servizio se non è già in esecuzione
                if (!isServiceRunning(context, BeaconMonitoringService::class.java)) {
                    startBackgroundService(result)
                    return
                }
            } else {
                // Se disabilitato, ferma il servizio se è in esecuzione
                if (isServiceRunning(context, BeaconMonitoringService::class.java)) {
                    stopBackgroundService(result)
                    return
                }
            }
            
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'impostazione servizio background: ${e.message}", e)
            logRepository.addLog("ERRORE nell'impostazione servizio background: ${e.message}")
            result.error("BACKGROUND_SERVICE_SETTING_ERROR", "Errore nell'impostazione servizio background: ${e.message}", null)
        }
    }

    private fun isBackgroundServiceEnabled(result: Result) {
        try {
            val isEnabled = PreferenceUtils.isBackgroundServiceEnabled(context)
            Log.d(TAG, "Servizio background abilitato: $isEnabled")
            logRepository.addLog("Servizio background abilitato: $isEnabled")
            result.success(isEnabled)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel controllo abilitazione servizio background: ${e.message}", e)
            logRepository.addLog("ERRORE nel controllo abilitazione servizio background: ${e.message}")
            result.error("BACKGROUND_SERVICE_ENABLED_CHECK_ERROR", "Errore nel controllo abilitazione servizio background: ${e.message}", null)
        }
    }
}