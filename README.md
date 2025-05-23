# Brux88 Beacon

A robust Flutter plugin for handling BLE beacons (iBeacon, AltBeacon, Eddystone) with reliable background detection and monitoring capabilities.

## Features

- 📱 **Cross-platform**: Works on both Android and iOS
- 🔄 **Background monitoring**: Reliable beacon detection even when the app is in the background
- 🎛️ **Background service control**: Start, stop, and restart background service independently
- 🔔 **Notifications**: Customizable notifications for region entry/exit events
- 🔋 **Battery-optimized**: Configurable scan settings to balance between detection speed and battery usage
- 🎯 **Region filtering**: Monitor specific beacons or all beacons in range
- 📏 **Distance estimation**: Get accurate distance measurements for ranging
- 🔒 **Permission handling**: Integrated permission management for location and Bluetooth
- 📊 **Comprehensive data**: Access to UUID, Major, Minor, RSSI, and TxPower values
- 🔄 **Boot persistence**: Automatic restart of monitoring service after device reboot
- 🛠️ **Debug tools**: Built-in logging system for troubleshooting

## Getting Started

### Installation

Add this to your package's `pubspec.yaml` file:

```yaml
dependencies:
  brux88_beacon: ^0.1.3
```

### Platform-specific setup

#### Android

Add the following permissions to your `AndroidManifest.xml` file:

```xml
<!-- Bluetooth permissions -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Location permissions (required for BLE scan) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Background service permissions -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- Feature required for BLE -->
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

#### iOS

Add the following to your `Info.plist` file:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app uses Bluetooth to scan for nearby beacons.</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>This app uses Bluetooth to scan for nearby beacons.</string>
<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>This app uses your location to detect beacons even when in the background.</string>
<key>NSLocationWhenInUseUsageDescription</key>
<string>This app uses your location to detect nearby beacons.</string>
<key>UIBackgroundModes</key>
<array>
    <string>bluetooth-central</string>
    <string>location</string>
</array>
```

## Usage

### Basic Setup

```dart
import 'package:brux88_beacon/brux88_beacon.dart';

// Create BeaconManager instance
final beaconManager = BeaconManager();

// Initialize the beacon manager
await beaconManager.initialize();

// Request necessary permissions
await beaconManager.requestPermissions();
```

### Background Service Control

The plugin provides independent control over the background service:

```dart
// Start background service only
await beaconManager.startBackgroundService();

// Stop background service
await beaconManager.stopBackgroundService();

// Restart background service
await beaconManager.restartBackgroundService();

// Check if background service is running
bool isRunning = await beaconManager.isBackgroundServiceRunning();

// Enable/disable background service auto-start
await beaconManager.setBackgroundServiceEnabled(true);
bool isEnabled = await beaconManager.isBackgroundServiceEnabled();
```

### Foreground Monitoring

```dart
// Start foreground monitoring (includes ranging)
await beaconManager.startMonitoring();

// Stop all monitoring
await beaconManager.stopMonitoring();

// Check if monitoring is active
bool isMonitoring = await beaconManager.isMonitoringRunning();
```

### Monitoring Specific Beacons

```dart
// Set a specific beacon to monitor
await beaconManager.setBeaconToMonitor(
  uuid: "F7826DA6-4FA2-4E98-8024-BC5B71E0893E",
  major: "1",
  minor: "2",
  enabled: true,
);

// Clear selected beacon (monitor all beacons)
await beaconManager.clearSelectedBeacon();
```

### Listening for Events

```dart
// Listen for beacon detection events
beaconManager.beacons.listen((List<Beacon> beacons) {
  for (var beacon in beacons) {
    print("Found beacon: ${beacon.uuid}, distance: ${beacon.distance}m");
  }
});

// Listen for region monitoring events
beaconManager.monitoringState.listen((MonitoringState state) {
  switch (state) {
    case MonitoringState.inside:
      print("Entered beacon region");
      break;
    case MonitoringState.outside:
      print("Exited beacon region");
      break;
    case MonitoringState.unknown:
      print("Region state unknown");
      break;
  }
});
```

### Configuring Scan Settings

```dart
// Configure scan settings for battery optimization
await beaconManager.setScanSettings(
  ScanSettings(
    backgroundScanPeriod: 1100,              // Scan period in milliseconds
    backgroundBetweenScanPeriod: 5000,       // Time between scans in milliseconds
    foregroundScanPeriod: 1100,              // Foreground scan period
    foregroundBetweenScanPeriod: 0,          // Time between foreground scans (0 = continuous)
    maxTrackingAge: 5000,                    // Maximum age for beacon tracking
  ),
);
```

### Battery Optimization

```dart
// Check if battery optimization is ignored
bool isIgnored = await beaconManager.isBatteryOptimizationIgnored();

// Request to ignore battery optimization
if (!isIgnored) {
  await beaconManager.requestIgnoreBatteryOptimization();
}

// Set up recurring alarms to keep the service alive
await beaconManager.setupRecurringAlarm();
```

### Debug and Logging

```dart
// Enable debug mode for detailed logs
await beaconManager.enableDebugMode();

// Get logs for troubleshooting
List<String> logs = await beaconManager.getLogs();
for (var log in logs) {
  print(log);
}
```

## Complete Example

Here's a complete example showing how to create a beacon monitoring app with background service control:

```dart
import 'package:flutter/material.dart';
import 'package:brux88_beacon/brux88_beacon.dart';

class BeaconControlPage extends StatefulWidget {
  @override
  _BeaconControlPageState createState() => _BeaconControlPageState();
}

class _BeaconControlPageState extends State<BeaconControlPage> {
  final BeaconManager _beaconManager = BeaconManager();
  
  bool _isInitialized = false;
  bool _isBackgroundServiceRunning = false;
  List<Beacon> _detectedBeacons = [];

  @override
  void initState() {
    super.initState();
    _initializeBeaconManager();
    _setupListeners();
  }

  Future<void> _initializeBeaconManager() async {
    final initialized = await _beaconManager.initialize();
    setState(() {
      _isInitialized = initialized;
    });
    
    if (initialized) {
      await _updateStatus();
    }
  }

  void _setupListeners() {
    _beaconManager.beacons.listen((beacons) {
      setState(() {
        _detectedBeacons = beacons;
      });
    });
  }

  Future<void> _updateStatus() async {
    final isBackgroundRunning = await _beaconManager.isBackgroundServiceRunning();
    setState(() {
      _isBackgroundServiceRunning = isBackgroundRunning;
    });
  }

  Future<void> _startBackgroundService() async {
    await _beaconManager.requestPermissions();
    
    // Check battery optimization
    final isBatteryOptimized = await _beaconManager.isBatteryOptimizationIgnored();
    if (!isBatteryOptimized) {
      await _beaconManager.requestIgnoreBatteryOptimization();
    }
    
    final success = await _beaconManager.startBackgroundService();
    if (success) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Background service started')),
      );
      await _updateStatus();
    }
  }

  Future<void> _stopBackgroundService() async {
    final success = await _beaconManager.stopBackgroundService();
    if (success) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Background service stopped')),
      );
      await _updateStatus();
    }
  }

  Future<void> _restartBackgroundService() async {
    final success = await _beaconManager.restartBackgroundService();
    if (success) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Background service restarted')),
      );
      await _updateStatus();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Beacon Control'),
        actions: [
          IconButton(
            onPressed: _updateStatus,
            icon: Icon(Icons.refresh),
          ),
        ],
      ),
      body: Padding(
        padding: EdgeInsets.all(16),
        child: Column(
          children: [
            // Status Card
            Card(
              child: Padding(
                padding: EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Status', style: Theme.of(context).textTheme.headlineSmall),
                    SizedBox(height: 8),
                    Text('Initialized: $_isInitialized'),
                    Text('Background Service: $_isBackgroundServiceRunning'),
                    Text('Beacons Detected: ${_detectedBeacons.length}'),
                  ],
                ),
              ),
            ),
            
            SizedBox(height: 16),
            
            // Control Buttons
            Card(
              child: Padding(
                padding: EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Background Service Control', 
                         style: Theme.of(context).textTheme.headlineSmall),
                    SizedBox(height: 16),
                    
                    Row(
                      children: [
                        Expanded(
                          child: ElevatedButton(
                            onPressed: _isInitialized && !_isBackgroundServiceRunning 
                                ? _startBackgroundService 
                                : null,
                            child: Text('Start Background'),
                          ),
                        ),
                        SizedBox(width: 8),
                        Expanded(
                          child: ElevatedButton(
                            onPressed: _isInitialized && _isBackgroundServiceRunning 
                                ? _stopBackgroundService 
                                : null,
                            child: Text('Stop Background'),
                          ),
                        ),
                      ],
                    ),
                    
                    SizedBox(height: 8),
                    
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton(
                        onPressed: _isInitialized ? _restartBackgroundService : null,
                        child: Text('Restart Background Service'),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            
            SizedBox(height: 16),
            
            // Detected Beacons
            Expanded(
              child: Card(
                child: Padding(
                  padding: EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Detected Beacons', 
                           style: Theme.of(context).textTheme.headlineSmall),
                      SizedBox(height: 8),
                      
                      if (_detectedBeacons.isEmpty)
                        Expanded(
                          child: Center(
                            child: Text('No beacons detected'),
                          ),
                        )
                      else
                        Expanded(
                          child: ListView.builder(
                            itemCount: _detectedBeacons.length,
                            itemBuilder: (context, index) {
                              final beacon = _detectedBeacons[index];
                              return ListTile(
                                leading: Icon(Icons.bluetooth),
                                title: Text('UUID: ${beacon.uuid}'),
                                subtitle: Text(
                                  'Distance: ${beacon.distance.toStringAsFixed(2)}m\n'
                                  'RSSI: ${beacon.rssi} dBm'
                                ),
                                trailing: Text('${beacon.distance < 1 ? "NEAR" : beacon.distance < 3 ? "MID" : "FAR"}'),
                              );
                            },
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _beaconManager.dispose();
    super.dispose();
  }
}
```

## Advanced Features

### Region Management

```dart
// Start monitoring a specific region
await beaconManager.startMonitoringForRegion(
  BeaconRegion(
    identifier: "myRegion",
    uuid: "F7826DA6-4FA2-4E98-8024-BC5B71E0893E",
    major: "1",
    minor: "2",
  ),
);

// Stop monitoring a region
await beaconManager.stopMonitoringForRegion("myRegion");

// Get all monitored regions
List<BeaconRegion> regions = await beaconManager.getMonitoredRegions();
```

### Notification Settings

```dart
// Configure notification settings
await beaconManager.setNotificationSettings(
  NotificationSettings(
    enabled: true,
    channelId: 'beacon_monitoring_channel',
    channelName: 'Beacon Monitoring',
    channelDescription: 'Notifications for beacon monitoring',
    importance: 2, // 1 = low, 2 = default, 3 = high
    showBadge: false,
    entryTitle: 'Beacon Detected',
    entryMessage: 'You have entered a beacon region',
    exitTitle: 'Beacon Lost',
    exitMessage: 'You have left a beacon region',
  ),
);

// Control detection notifications separately from service notifications
await beaconManager.setShowDetectionNotifications(true);
```

## Supported Beacon Types

- **Apple iBeacon**: The most common format used by iOS devices and many beacon manufacturers
- **AltBeacon**: Open source beacon format
- **Eddystone**: Google's beacon format (UID and URL variants)

## Background Service Management

The plugin provides granular control over background services:

- **Independent Control**: Start/stop background service independently from foreground monitoring
- **Battery Optimization**: Background service uses optimized scan intervals to preserve battery
- **Auto-restart**: Service automatically restarts after device reboot if enabled
- **Status Monitoring**: Real-time status updates for service state

## Performance Considerations

### Battery Optimization

- Use longer scan intervals for background monitoring
- Request battery optimization exclusion for reliable background operation
- Configure appropriate scan settings based on your use case

### Scan Settings Recommendations

```dart
// Battery-friendly settings
ScanSettings(
  backgroundScanPeriod: 1100,
  backgroundBetweenScanPeriod: 30000,  // 30 seconds between scans
  foregroundScanPeriod: 1100,
  foregroundBetweenScanPeriod: 0,     // Continuous when in foreground
)

// High-precision settings (more battery usage)
ScanSettings(
  backgroundScanPeriod: 1100,
  backgroundBetweenScanPeriod: 5000,   // 5 seconds between scans
  foregroundScanPeriod: 1100,
  foregroundBetweenScanPeriod: 0,
)
```

## Troubleshooting

### Common Issues

#### Background Monitoring Not Working

1. Ensure all permissions are granted
2. Check battery optimization settings
3. Verify Bluetooth and Location are enabled
4. Enable debug mode to view detailed logs

```dart
await beaconManager.enableDebugMode();
List<String> logs = await beaconManager.getLogs();
```

#### Beacons Not Detected

1. Verify beacon format compatibility (iBeacon, AltBeacon, Eddystone)
2. Check signal strength and distance
3. Ensure beacon is advertising correctly
4. Review scan settings

#### Service Stops Unexpectedly

1. Request battery optimization exclusion
2. Set up recurring alarms for service restart
3. Check device-specific power management settings

### Debug Information

```dart
// Check system status
bool bluetoothEnabled = await beaconManager.isBluetoothEnabled();
bool locationEnabled = await beaconManager.isLocationEnabled();
Map<String, bool> permissions = await beaconManager.checkPermissions();

// Get detailed logs
await beaconManager.enableDebugMode();
List<String> logs = await beaconManager.getLogs();
```

## Platform Differences

### Android

- Uses AltBeacon library for beacon detection
- Requires foreground service for background operation
- Battery optimization exclusion recommended
- Supports all beacon formats

### iOS

- Uses CoreLocation and CoreBluetooth APIs
- Background execution managed by iOS
- Limited background scanning time
- Optimized for iBeacon format

## Migration Guide

### From 0.1.2 to 0.1.3

- Added independent background service control methods
- Enhanced notification management
- Improved battery optimization handling

No breaking changes - all existing code continues to work.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- The Android implementation uses the [AltBeacon library](https://github.com/AltBeacon/android-beacon-library)
- The iOS implementation uses native CoreLocation and CoreBluetooth APIs

## Support

For support, please open an issue on [GitHub](https://github.com/brux88/brux88_beacon/issues) or contact the maintainer.

---

**Note**: This plugin is actively maintained and tested on Flutter 3.3.0+. For older Flutter versions, please use an earlier version of this plugin.