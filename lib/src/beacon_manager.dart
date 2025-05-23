// lib/src/beacon_manager.dart
import 'dart:async';
import 'dart:io';
import 'package:brux88_beacon/brux88_beacon.dart';
import 'package:flutter/services.dart';
import 'models/beacon.dart';
import 'models/beacon_region.dart';
import 'models/monitoring_state.dart';
import 'models/notification_settings.dart';
import 'models/scan_settings.dart';

class BeaconManager {
  static const MethodChannel _methodChannel =
      MethodChannel('com.brux88.brux88_beacon/methods');

  static const EventChannel _beaconsEventChannel =
      EventChannel('com.brux88.brux88_beacon/beacons');

  static const EventChannel _monitoringEventChannel =
      EventChannel('com.brux88.brux88_beacon/monitoring');

  // Singleton pattern
  static final BeaconManager _instance = BeaconManager._internal();
  factory BeaconManager() => _instance;
  BeaconManager._internal();

  // Stream controllers
  final _beaconsController = StreamController<List<Beacon>>.broadcast();
  final _monitoringController = StreamController<MonitoringState>.broadcast();

  Stream<List<Beacon>> get beacons => _beaconsController.stream;
  Stream<MonitoringState> get monitoringState => _monitoringController.stream;

  bool _isInitialized = false;

  /// Initialize the beacon manager
  Future<bool> initialize() async {
    try {
      // Se è già inizializzato, non facciamo nulla
      if (_isInitialized) return true;

      // Imposta il flag di monitoraggio a false nelle preferenze
      // senza cercare di fermare il monitoraggio attivo
      try {
        // Imposta direttamente il flag di monitoraggio a false
        await _methodChannel.invokeMethod('setMonitoringEnabled', false);
        print('Set monitoring flag to false before initialization');
      } catch (e) {
        print('Error setting monitoring flag: $e');
        // Continua comunque con l'inizializzazione
      }

      // Ora proviamo a inizializzare
      final result =
          await _methodChannel.invokeMethod<bool>('initialize') ?? false;

      if (result) {
        _setupEventChannels();
        _isInitialized = true;
      }

      return result;
    } catch (e) {
      print('Error initializing beacon manager: $e');
      return false;
    }
  }

  void _setupEventChannels() {
    _beaconsEventChannel.receiveBroadcastStream().listen((dynamic event) {
      // Verifica che event sia effettivamente una List
      if (event is List) {
        final beaconsList = event
            .map((e) => Beacon.fromMap(e as Map<dynamic, dynamic>))
            .toList();
        _beaconsController.add(beaconsList);
      } else if (event is String) {
        // Se è una stringa (errore o messaggio di debug), registralo
        print('Received string message from beacon channel: $event');
        // Eventualmente, puoi anche inviare una lista vuota
        _beaconsController.add([]);
      } else if (event is Map) {
        // Se è un singolo beacon (mappa invece di lista di mappe)
        try {
          final beacon = Beacon.fromMap(event as Map<dynamic, dynamic>);
          _beaconsController.add([beacon]);
        } catch (e) {
          print('Error parsing single beacon: $e');
          _beaconsController.add([]);
        }
      } else {
        // In caso di altri tipi, invia lista vuota
        print(
            'Unexpected event type from beacon channel: ${event.runtimeType}');
        _beaconsController.add([]);
      }
    }, onError: (dynamic error) {
      print('Error from beacons stream: $error');
      _beaconsController.add([]);
    });

    _monitoringEventChannel.receiveBroadcastStream().listen((dynamic event) {
      // Anche qui, verifica il tipo
      if (event is String) {
        final state = _parseMonitoringState(event);
        _monitoringController.add(state);
      } else {
        print('Unexpected monitoring event type: ${event.runtimeType}');
      }
    }, onError: (dynamic error) {
      print('Error from monitoring stream: $error');
    });
  }

  MonitoringState _parseMonitoringState(String state) {
    switch (state) {
      case 'INSIDE':
        return MonitoringState.inside;
      case 'OUTSIDE':
        return MonitoringState.outside;
      default:
        return MonitoringState.unknown;
    }
  }

  /// Setup a recurring alarm to restart the beacon monitoring service periodically
  Future<bool> setupRecurringAlarm() async {
    return await _methodChannel.invokeMethod<bool>('setupRecurringAlarm') ??
        false;
  }

  /// Cancel the recurring alarm
  Future<bool> cancelRecurringAlarm() async {
    return await _methodChannel.invokeMethod<bool>('cancelRecurringAlarm') ??
        false;
  }

  /// Start monitoring for beacons
  Future<bool> startMonitoring() async {
    if (!_isInitialized) await initialize();
    return await _methodChannel.invokeMethod<bool>('startMonitoring') ?? false;
  }

  Future<SelectedBeacon?> getSelectedBeacon() async {
    final resultMap = await _methodChannel
        .invokeMethod<Map<dynamic, dynamic>>('getSelectedBeacon');
    if (resultMap == null) return null;

    return SelectedBeacon.fromMap(resultMap);
  }

  /// Stop monitoring for beacons
  Future<bool> stopMonitoring() async {
    return await _methodChannel.invokeMethod<bool>('stopMonitoring') ?? false;
  }

  // ===== NUOVE FUNZIONALITÀ PER CONTROLLO SERVIZIO BACKGROUND =====

  /// Start only the background service without foreground monitoring
  Future<bool> startBackgroundService() async {
    if (!_isInitialized) await initialize();
    return await _methodChannel.invokeMethod<bool>('startBackgroundService') ??
        false;
  }

  /// Stop only the background service
  Future<bool> stopBackgroundService() async {
    return await _methodChannel.invokeMethod<bool>('stopBackgroundService') ??
        false;
  }

  /// Restart the background service
  Future<bool> restartBackgroundService() async {
    try {
      await stopBackgroundService();
      // Piccola pausa per assicurarsi che il servizio sia completamente fermato
      await Future.delayed(Duration(milliseconds: 500));
      return await startBackgroundService();
    } catch (e) {
      print('Error restarting background service: $e');
      return false;
    }
  }

  /// Check if the background service is running
  Future<bool> isBackgroundServiceRunning() async {
    return await _methodChannel
            .invokeMethod<bool>('isBackgroundServiceRunning') ??
        false;
  }

  /// Enable/disable background service auto-start
  Future<bool> setBackgroundServiceEnabled(bool enabled) async {
    return await _methodChannel.invokeMethod<bool>(
            'setBackgroundServiceEnabled', enabled) ??
        false;
  }

  /// Check if background service auto-start is enabled
  Future<bool> isBackgroundServiceEnabled() async {
    return await _methodChannel
            .invokeMethod<bool>('isBackgroundServiceEnabled') ??
        false;
  }

  // ===== FINE NUOVE FUNZIONALITÀ =====

  /// Start monitoring for a specific region
  Future<bool> startMonitoringForRegion(BeaconRegion region) async {
    if (!_isInitialized) await initialize();
    return await _methodChannel.invokeMethod<bool>(
            'startMonitoringForRegion', region.toMap()) ??
        false;
  }

  /// Stop monitoring for a specific region
  Future<bool> stopMonitoringForRegion(String identifier) async {
    return await _methodChannel.invokeMethod<bool>(
            'stopMonitoringForRegion', {'identifier': identifier}) ??
        false;
  }

  /// Ottiene i log dal repository
  Future<List<String>> getLogs() async {
    try {
      final List<dynamic> logs =
          await _methodChannel.invokeMethod('getLogs') ?? [];
      return logs.map((log) => log.toString()).toList();
    } catch (e) {
      print('Errore nel recupero dei log: $e');
      return [];
    }
  }

  /// Abilita la modalità debug per più log
  Future<bool> enableDebugMode() async {
    return await _methodChannel.invokeMethod<bool>('enableDebugMode') ?? false;
  }

  /// Get all currently monitored regions
  Future<List<BeaconRegion>> getMonitoredRegions() async {
    final regionsMap =
        await _methodChannel.invokeMethod<List<dynamic>>('getMonitoredRegions');
    if (regionsMap == null) return [];

    return regionsMap.map((regionMap) {
      final map = Map<String, dynamic>.from(regionMap as Map);
      return BeaconRegion(
        identifier: map['identifier'] as String,
        uuid: map['uuid'] as String,
        major: map['major'] as String?,
        minor: map['minor'] as String?,
        notifyEntryStateOnDisplay:
            map['notifyEntryStateOnDisplay'] as bool? ?? false,
      );
    }).toList();
  }

  /// Verifica se il BeaconManager è inizializzato
  Future<bool> isInitialized() async {
    try {
      return await _methodChannel.invokeMethod<bool>('isInitialized') ?? false;
    } catch (e) {
      print('Errore nella verifica dell\'inizializzazione: $e');
      return false;
    }
  }

  /// Richiede il permesso per impostare allarmi esatti (necessario per Android 12+)
  Future<bool> requestExactAlarmPermission() async {
    if (Platform.isAndroid) {
      return await _methodChannel
              .invokeMethod<bool>('requestExactAlarmPermission') ??
          false;
    }
    return true; // Su iOS non è necessario
  }

  /// Set a specific beacon to monitor
  Future<bool> setBeaconToMonitor({
    required String uuid,
    String? major,
    String? minor,
    String? name,
    bool enabled = true,
  }) async {
    return await _methodChannel.invokeMethod<bool>('setBeaconToMonitor', {
          'uuid': uuid,
          'major': major,
          'minor': minor,
          'name': name,
          'enabled': enabled,
        }) ??
        false;
  }

  /// Clear selected beacon
  Future<bool> clearSelectedBeacon() async {
    return await _methodChannel.invokeMethod<bool>('clearSelectedBeacon') ??
        false;
  }

  /// Set scan settings
  Future<bool> setScanSettings(ScanSettings settings) async {
    return await _methodChannel.invokeMethod<bool>(
            'setScanSettings', settings.toMap()) ??
        false;
  }

  /// Get current scan settings
  Future<ScanSettings> getScanSettings() async {
    final settingsMap = await _methodChannel
        .invokeMethod<Map<dynamic, dynamic>>('getScanSettings');
    if (settingsMap == null) {
      return ScanSettings();
    }

    final map = Map<String, dynamic>.from(settingsMap);
    return ScanSettings(
      backgroundScanPeriod: map['backgroundScanPeriod'] as int? ?? 1100,
      backgroundBetweenScanPeriod:
          map['backgroundBetweenScanPeriod'] as int? ?? 5000,
      foregroundScanPeriod: map['foregroundScanPeriod'] as int? ?? 1100,
      foregroundBetweenScanPeriod:
          map['foregroundBetweenScanPeriod'] as int? ?? 0,
      maxTrackingAge: map['maxTrackingAge'] as int? ?? 5000,
    );
  }

  /// Set notification settings
  Future<bool> setNotificationSettings(NotificationSettings settings) async {
    return await _methodChannel.invokeMethod<bool>(
            'setNotificationSettings', settings.toMap()) ??
        false;
  }

  /// Check if beacon monitoring is running
  Future<bool> isMonitoringRunning() async {
    return await _methodChannel.invokeMethod<bool>('isMonitoringRunning') ??
        false;
  }

  /// Non influisce sulle notifiche del servizio in foreground
  Future<bool> setShowDetectionNotifications(bool show) async {
    return await _methodChannel.invokeMethod<bool>(
            'setShowDetectionNotifications', show) ??
        false;
  }

  /// Request necessary permissions
  Future<bool> requestPermissions() async {
    return await _methodChannel.invokeMethod<bool>('requestPermissions') ??
        false;
  }

  /// Verifica se il Bluetooth è abilitato
  Future<bool> isBluetoothEnabled() async {
    return await _methodChannel.invokeMethod<bool>('isBluetoothEnabled') ??
        false;
  }

  /// Verifica se la localizzazione è abilitata
  Future<bool> isLocationEnabled() async {
    return await _methodChannel.invokeMethod<bool>('isLocationEnabled') ??
        false;
  }

  /// Controllo dei permessi
  Future<Map<String, bool>> checkPermissions() async {
    final result = await _methodChannel
        .invokeMethod<Map<dynamic, dynamic>>('checkPermissions');
    if (result == null) return {};

    return Map<String, bool>.from(
        result.map((key, value) => MapEntry(key.toString(), value as bool)));
  }

  /// Controllo delle ottimizzazioni batteria
  Future<bool> isBatteryOptimizationIgnored() async {
    return await _methodChannel
            .invokeMethod<bool>('isBatteryOptimizationIgnored') ??
        false;
  }

  /// Richiesta di ignorare le ottimizzazioni batteria
  Future<bool> requestIgnoreBatteryOptimization() async {
    return await _methodChannel
            .invokeMethod<bool>('requestIgnoreBatteryOptimization') ??
        false;
  }

  /// Dispose streams
  void dispose() {
    _beaconsController.close();
    _monitoringController.close();
  }
}
