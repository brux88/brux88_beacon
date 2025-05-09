// lib/src/models/notification_settings.dart
class NotificationSettings {
  final bool enabled;
  final String channelId;
  final String channelName;
  final String channelDescription;
  final int importance; // 1 = low, 2 = default, 3 = high
  final bool showBadge;
  final String? smallIconResourceName;
  final bool showBeaconDetectionNotifications; // Aggiungi questa proprietà

  // Personalizzazione del contenuto
  final String entryTitle;
  final String entryMessage;
  final String exitTitle;
  final String exitMessage;

  NotificationSettings({
    this.enabled = true,
    this.channelId = 'beacon_monitoring_channel',
    this.channelName = 'Beacon Monitoring',
    this.channelDescription = 'Notifications for beacon monitoring',
    this.importance = 2,
    this.showBadge = false,
    this.smallIconResourceName,
    this.showBeaconDetectionNotifications = true, // Nuovo campo
    this.entryTitle = 'Beacon Detected',
    this.entryMessage = 'You have entered a beacon region',
    this.exitTitle = 'Beacon Lost',
    this.exitMessage = 'You have left a beacon region',
  });

  Map<String, dynamic> toMap() {
    return {
      'enabled': enabled,
      'channelId': channelId,
      'channelName': channelName,
      'channelDescription': channelDescription,
      'importance': importance,
      'showBadge': showBadge,
      'smallIconResourceName': smallIconResourceName,
      'showBeaconDetectionNotifications':
          showBeaconDetectionNotifications, // Aggiungilo alla mappa
      'entryTitle': entryTitle,
      'entryMessage': entryMessage,
      'exitTitle': exitTitle,
      'exitMessage': exitMessage,
    };
  }
}
