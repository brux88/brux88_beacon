package com.brux88.brux88_beacon

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.NonNull
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
import org.altbeacon.beacon.Region
import org.altbeacon.beacon.RangeNotifier
import org.altbeacon.beacon.MonitorNotifier
import com.brux88.brux88_beacon.model.SelectedBeacon
import com.brux88.brux88_beacon.repository.BeaconRepository
import com.brux88.brux88_beacon.repository.LogRepository
import com.brux88.brux88_beacon.service.BeaconMonitoringService
import com.brux88.brux88_beacon.util.PreferenceUtils
import com.brux88.brux88_beacon.util.RegionUtils
import android.os.Build
import android.util.Log
import android.bluetooth.BluetoothAdapter
import android.location.LocationManager
import android.provider.Settings
import android.net.Uri
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.PowerManager
import org.altbeacon.beacon.Identifier

class Brux88BeaconPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  private val TAG = "Brux88BeaconPlugin"
  
  private lateinit var methodChannel : MethodChannel
  private lateinit var beaconsEventChannel: EventChannel
  private lateinit var monitoringEventChannel: EventChannel
  
  private lateinit var context: Context
  private var activity: Activity? = null
  
  // Beacon components
  private lateinit var beaconManager: BeaconManager
  private lateinit var beaconRepository: BeaconRepository
  
  // Event sinks
  private var beaconsSink: EventChannel.EventSink? = null
  private var monitoringSink: EventChannel.EventSink? = null

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, "com.brux88.brux88_beacon/methods")
    beaconsEventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "com.brux88.brux88_beacon/beacons")
    monitoringEventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "com.brux88.brux88_beacon/monitoring")
    
    methodChannel.setMethodCallHandler(this)
    setupEventChannels()
  }

  private fun setupEventChannels() {
    beaconsEventChannel.setStreamHandler(object : EventChannel.StreamHandler {
      override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        beaconsSink = events
      }

      override fun onCancel(arguments: Any?) {
        beaconsSink = null
      }
    })

    monitoringEventChannel.setStreamHandler(object : EventChannel.StreamHandler {
      override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        monitoringSink = events
      }

      override fun onCancel(arguments: Any?) {
        monitoringSink = null
      }
    })
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
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
          result.error("INVALID_ARGUMENTS", "UUID is required", null)
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
      "setNotificationSettings" -> {
        setNotificationSettings(call, result)
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
      "isBatteryOptimizationIgnored" -> {
        isBatteryOptimizationIgnored(result)
      }
      "requestIgnoreBatteryOptimization" -> {
        requestIgnoreBatteryOptimization(result)
      }
      "requestPermissions" -> {
        requestPermissions(result)
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  private fun initialize(result: Result) {
    try {
      beaconManager = BeaconManager.getInstanceForApplication(context)
      beaconRepository = BeaconRepository(context)
      
      // Setup monitoring and ranging callbacks
      setupBeaconCallbacks()
      
      result.success(true)
    } catch (e: Exception) {
      result.error("INITIALIZATION_ERROR", "Failed to initialize beacon manager: ${e.message}", null)
    }
  }

  private fun setupBeaconCallbacks() {
    // Range notifier per gli aggiornamenti sulle distanze
    beaconManager.addRangeNotifier(object : RangeNotifier {
      override fun didRangeBeaconsInRegion(beacons: Collection<Beacon>, region: Region) {
        if (beacons.isNotEmpty() && beaconsSink != null) {
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
          beaconsSink?.success(beaconMaps)
        }
      }
    })
    
    // Monitor notifier per gli eventi di regione
    beaconManager.addMonitorNotifier(object : MonitorNotifier {
      override fun didEnterRegion(region: Region) {
        monitoringSink?.success("INSIDE")
      }

      override fun didExitRegion(region: Region) {
        monitoringSink?.success("OUTSIDE")
      }

      override fun didDetermineStateForRegion(state: Int, region: Region) {
        val stateStr = if (state == MonitorNotifier.INSIDE) "INSIDE" else "OUTSIDE"
        monitoringSink?.success(stateStr)
      }
    })
  }

  private fun startMonitoring(result: Result) {
    try {
      val serviceIntent = Intent(context, BeaconMonitoringService::class.java)
      PreferenceUtils.setMonitoringEnabled(context, true)
      
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
      } else {
        context.startService(serviceIntent)
      }
      
      result.success(true)
    } catch (e: Exception) {
      result.error("START_MONITORING_ERROR", "Failed to start monitoring: ${e.message}", null)
    }
  }

  private fun stopMonitoring(result: Result) {
    try {
      val serviceIntent = Intent(context, BeaconMonitoringService::class.java)
      context.stopService(serviceIntent)
      PreferenceUtils.setMonitoringEnabled(context, false)
      result.success(true)
    } catch (e: Exception) {
      result.error("STOP_MONITORING_ERROR", "Failed to stop monitoring: ${e.message}", null)
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
      val selectedBeacon = SelectedBeacon(uuid, major, minor, name)
      PreferenceUtils.saveSelectedBeacon(context, selectedBeacon)
      PreferenceUtils.setSelectedBeaconEnabled(context, enabled)
      
      result.success(true)
    } catch (e: Exception) {
      result.error("SET_BEACON_ERROR", "Failed to set beacon: ${e.message}", null)
    }
  }

  private fun clearSelectedBeacon(result: Result) {
    try {
      PreferenceUtils.clearSelectedBeacon(context)
      result.success(true)
    } catch (e: Exception) {
      result.error("CLEAR_BEACON_ERROR", "Failed to clear beacon: ${e.message}", null)
    }
  }

  private fun requestPermissions(result: Result) {
    if (activity != null) {
      // In un'implementazione reale, qui dovresti richiedere i permessi necessari
      // Per ora, restituiamo semplicemente success
      result.success(true)
    } else {
      result.error("ACTIVITY_UNAVAILABLE", "Activity is required to request permissions", null)
    }
  }

  private fun checkForRestoredMonitoring() {
    if (PreferenceUtils.isMonitoringEnabled(context)) {
      // Il monitoraggio era attivo quando l'app è stata chiusa
      try {
        val serviceIntent = Intent(context, BeaconMonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(serviceIntent)
        } else {
          context.startService(serviceIntent)
        }
        Log.d(TAG, "Restored monitoring service after app launch")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to restore monitoring: ${e.message}")
      }
    }
  }


  // Aggiungi questi metodi alla classe Brux88BeaconPlugin

  private fun setScanSettings(call: MethodCall, result: Result) {
    try {
      val backgroundScanPeriod = call.argument<Int>("backgroundScanPeriod") ?: 1100
      val backgroundBetweenScanPeriod = call.argument<Int>("backgroundBetweenScanPeriod") ?: 5000
      val foregroundScanPeriod = call.argument<Int>("foregroundScanPeriod") ?: 1100
      val foregroundBetweenScanPeriod = call.argument<Int>("foregroundBetweenScanPeriod") ?: 0
      val maxTrackingAge = call.argument<Int>("maxTrackingAge") ?: 5000
      
      beaconManager.backgroundScanPeriod = backgroundScanPeriod.toLong()
      beaconManager.backgroundBetweenScanPeriod = backgroundBetweenScanPeriod.toLong()
      beaconManager.foregroundScanPeriod = foregroundScanPeriod.toLong()
      beaconManager.foregroundBetweenScanPeriod = foregroundBetweenScanPeriod.toLong()
      beaconManager.setMaxTrackingAge(maxTrackingAge)
      
      // Salva anche nelle preferenze
      PreferenceUtils.setBackgroundScanPeriod(context, backgroundScanPeriod.toLong())
      PreferenceUtils.setBackgroundBetweenScanPeriod(context, backgroundBetweenScanPeriod.toLong())
      PreferenceUtils.setMaxTrackingAge(context, maxTrackingAge.toLong())
      
      result.success(true)
    } catch (e: Exception) {
      result.error("SCAN_SETTINGS_ERROR", "Failed to set scan settings: ${e.message}", null)
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
      
      result.success(settings)
    } catch (e: Exception) {
      result.error("GET_SCAN_SETTINGS_ERROR", "Failed to get scan settings: ${e.message}", null)
    }
  }

  private fun setNotificationSettings(call: MethodCall, result: Result) {
    try {
      // Qui implementeresti la logica per configurare le notifiche
      // Per esempio, potresti salvare le impostazioni in PreferenceUtils
      // E poi utilizzarle in NotificationUtils
      
      result.success(true)
    } catch (e: Exception) {
      result.error("NOTIFICATION_SETTINGS_ERROR", "Failed to set notification settings: ${e.message}", null)
    }
  }

  private fun startMonitoringForRegion(call: MethodCall, result: Result) {
    try {
      val identifier = call.argument<String>("identifier")
      val uuid = call.argument<String>("uuid")
      val major = call.argument<String>("major")
      val minor = call.argument<String>("minor")
      
      if (identifier == null || uuid == null) {
        result.error("INVALID_ARGUMENTS", "Identifier and UUID are required", null)
        return
      }
      
      // Crea l'Identifier per il beacon
      val id1 = Identifier.parse(uuid)
      val id2 = if (major != null) Identifier.parse(major) else null
      val id3 = if (minor != null) Identifier.parse(minor) else null
      
      // Crea la Region
      val region = Region(identifier, id1, id2, id3)
      
      // Avvia il monitoraggio
      beaconManager.startMonitoringBeaconsInRegion(region)
      beaconManager.startRangingBeaconsInRegion(region)
      
      result.success(true)
    } catch (e: Exception) {
      result.error("START_REGION_ERROR", "Failed to start monitoring for region: ${e.message}", null)
    }
  }

  private fun stopMonitoringForRegion(call: MethodCall, result: Result) {
    try {
      val identifier = call.argument<String>("identifier")
      
      if (identifier == null) {
        result.error("INVALID_ARGUMENTS", "Identifier is required", null)
        return
      }
      
      // Cerca la regione esistente
      val regions = beaconManager.monitoredRegions
      val region = regions.find { it.uniqueId == identifier }
      
      if (region != null) {
        beaconManager.stopMonitoringBeaconsInRegion(region)
        beaconManager.stopRangingBeaconsInRegion(region)
        result.success(true)
      } else {
        result.error("REGION_NOT_FOUND", "No region found with identifier: $identifier", null)
      }
    } catch (e: Exception) {
      result.error("STOP_REGION_ERROR", "Failed to stop monitoring for region: ${e.message}", null)
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
      
      result.success(regionMaps)
    } catch (e: Exception) {
      result.error("GET_REGIONS_ERROR", "Failed to get monitored regions: ${e.message}", null)
    }
  }

  private fun isBluetoothEnabled(result: Result) {
    try {
      val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
      result.success(bluetoothAdapter?.isEnabled ?: false)
    } catch (e: Exception) {
      result.error("BLUETOOTH_ERROR", "Failed to check Bluetooth state: ${e.message}", null)
    }
  }

  private fun isLocationEnabled(result: Result) {
    try {
      val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
      val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
      val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
      
      result.success(isGpsEnabled || isNetworkEnabled)
    } catch (e: Exception) {
      result.error("LOCATION_ERROR", "Failed to check location state: ${e.message}", null)
    }
  }

  private fun checkPermissions(result: Result) {
    try {
      val permissions = hashMapOf<String, Boolean>()
      
      // Controlla i permessi di localizzazione
      permissions["location"] = ContextCompat.checkSelfPermission(
        context, 
        Manifest.permission.ACCESS_FINE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED
      
      // Controlla i permessi Bluetooth per Android 12+
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions["bluetoothScan"] = ContextCompat.checkSelfPermission(
          context, 
          Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
        
        permissions["bluetoothConnect"] = ContextCompat.checkSelfPermission(
          context, 
          Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
      } else {
        permissions["bluetoothScan"] = true
        permissions["bluetoothConnect"] = true
      }
      
      // Controlla i permessi di notifica per Android 13+
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions["notifications"] = ContextCompat.checkSelfPermission(
          context, 
          Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
      } else {
        permissions["notifications"] = true
      }
      
      // Controlla il permesso di localizzazione in background per Android 10+
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        permissions["backgroundLocation"] = ContextCompat.checkSelfPermission(
          context, 
          Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
      } else {
        permissions["backgroundLocation"] = true
      }
      
      result.success(permissions)
    } catch (e: Exception) {
      result.error("PERMISSIONS_ERROR", "Failed to check permissions: ${e.message}", null)
    }
  }

  private fun isBatteryOptimizationIgnored(result: Result) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName
        result.success(powerManager.isIgnoringBatteryOptimizations(packageName))
      } else {
        result.success(true) // Versioni precedenti ad Android M non hanno questa limitazione
      }
    } catch (e: Exception) {
      result.error("BATTERY_OPT_ERROR", "Failed to check battery optimization: ${e.message}", null)
    }
  }

  private fun requestIgnoreBatteryOptimization(result: Result) {
    try {
      if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val packageName = context.packageName
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:$packageName")
        activity?.startActivity(intent)
        result.success(true)
      } else {
        result.error("ACTIVITY_NULL", "Activity is required to request battery optimization", null)
      }
    } catch (e: Exception) {
      result.error("BATTERY_OPT_REQUEST_ERROR", "Failed to request ignore battery optimization: ${e.message}", null)
    }
  }


  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    methodChannel.setMethodCallHandler(null)
    beaconsEventChannel.setStreamHandler(null)
    monitoringEventChannel.setStreamHandler(null)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    checkForRestoredMonitoring()
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
  }
}