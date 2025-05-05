# Brux88 Beacon



A robust Flutter plugin for handling BLE beacons (iBeacon, AltBeacon, Eddystone) with reliable background detection and monitoring capabilities.

## Features

- 📱 **Cross-platform**: Works on both Android and iOS
- 🔄 **Background monitoring**: Reliable beacon detection even when the app is in the background
- 🔔 **Notifications**: Customizable notifications for region entry/exit events
- 🔋 **Battery-optimized**: Configurable scan settings to balance between detection speed and battery usage
- 🎯 **Region filtering**: Monitor specific beacons or all beacons in range
- 📏 **Distance estimation**: Get accurate distance measurements for ranging
- 🔒 **Permission handling**: Integrated permission management for location and Bluetooth
- 📊 **Comprehensive data**: Access to UUID, Major, Minor, RSSI, and TxPower values
- 🔄 **Boot persistence**: Automatic restart of monitoring service after device reboot

## Getting Started

### Installation

Add this to your package's `pubspec.yaml` file:

```yaml
dependencies:
  brux88_beacon: ^0.1.0
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

### Monitoring Beacons

```dart
// Start monitoring for all beacons
await beaconManager.startMonitoring();

// Or start monitoring for a specific beacon
await beaconManager.setBeaconToMonitor(
  uuid: "F7826DA6-4FA2-4E98-8024-BC5B71E0893E",
  major: "1",
  minor: "2",
  enabled: true,
);
await beaconManager.startMonitoring();

// Stop monitoring
await beaconManager.stopMonitoring();
```

### Listening for Beacons

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

### Customizing Notifications

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
```

### Managing Regions

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

### Accessing Logs

```dart
// Get logs for debugging
List<String> logs = await beaconManager.getLogs();
for (var log in logs) {
  print(log);
}

// Enable debug mode for more detailed logs
await beaconManager.enableDebugMode();
```

## Example App

Check the `example` folder for a complete working application that demonstrates how to use the plugin.

## Supported Beacon Types

- Apple iBeacon
- AltBeacon
- Eddystone (UID and URL formats)

## Advanced Configuration

### Android-specific

The plugin uses the AltBeacon library for Android. You can customize various aspects of the library's behavior through the `ScanSettings` class.

### iOS-specific

On iOS, the plugin uses CoreLocation and CoreBluetooth APIs. The monitoring settings provided through `ScanSettings` are still respected but are adapted to the iOS platform capabilities.

## Troubleshooting

### Background Monitoring

For reliable background monitoring:

1. Ensure all the necessary permissions are granted
2. On Android, request to ignore battery optimization
3. Set up a recurring alarm with `setupRecurringAlarm()`
4. On iOS, ensure background modes are properly configured in Info.plist

### Permission Issues

If you're experiencing permission issues:

1. Check the current permission status with `checkPermissions()`
2. Request necessary permissions with `requestPermissions()`
3. For Android 12+, request the SCHEDULE_EXACT_ALARM permission explicitly

### Beacon Detection

If beacons are not being detected:

1. Verify Bluetooth is enabled with `isBluetoothEnabled()`
2. Verify Location is enabled with `isLocationEnabled()`
3. Check if the beacon is using a supported format (iBeacon, AltBeacon, Eddystone)
4. Enable debug mode for more detailed logs with `enableDebugMode()`
5. Check the logs with `getLogs()`

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- The Android implementation uses the [AltBeacon library](https://github.com/AltBeacon/android-beacon-library)
- The iOS implementation uses native CoreLocation and CoreBluetooth APIs

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.