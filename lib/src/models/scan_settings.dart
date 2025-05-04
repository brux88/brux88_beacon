// lib/src/models/scan_settings.dart
class ScanSettings {
  /// Background scan period in milliseconds
  final int backgroundScanPeriod;

  /// Time between background scans in milliseconds
  final int backgroundBetweenScanPeriod;

  /// Foreground scan period in milliseconds
  final int foregroundScanPeriod;

  /// Time between foreground scans in milliseconds
  final int foregroundBetweenScanPeriod;

  /// Maximum tracking age in milliseconds
  final int maxTrackingAge;

  ScanSettings({
    this.backgroundScanPeriod = 1100,
    this.backgroundBetweenScanPeriod = 5000,
    this.foregroundScanPeriod = 1100,
    this.foregroundBetweenScanPeriod = 0,
    this.maxTrackingAge = 5000,
  });

  Map<String, dynamic> toMap() {
    return {
      'backgroundScanPeriod': backgroundScanPeriod,
      'backgroundBetweenScanPeriod': backgroundBetweenScanPeriod,
      'foregroundScanPeriod': foregroundScanPeriod,
      'foregroundBetweenScanPeriod': foregroundBetweenScanPeriod,
      'maxTrackingAge': maxTrackingAge,
    };
  }
}
