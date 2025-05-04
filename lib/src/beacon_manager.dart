// lib/src/beacon_manager.dart
import 'dart:async';
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
    if (_isInitialized) return true;

    try {
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
      final beaconsList = (event as List<dynamic>)
          .map((e) => Beacon.fromMap(e as Map<dynamic, dynamic>))
          .toList();
      _beaconsController.add(beaconsList);
    }, onError: (dynamic error) {
      print('Error from beacons stream: $error');
    });

    _monitoringEventChannel.receiveBroadcastStream().listen((dynamic event) {
      final state = _parseMonitoringState(event as String);
      _monitoringController.add(state);
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

  /// Start monitoring for beacons
  Future<bool> startMonitoring() async {
    if (!_isInitialized) await initialize();
    return await _methodChannel.invokeMethod<bool>('startMonitoring') ?? false;
  }

  /// Stop monitoring for beacons
  Future<bool> stopMonitoring() async {
    return await _methodChannel.invokeMethod<bool>('stopMonitoring') ?? false;
  }

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
