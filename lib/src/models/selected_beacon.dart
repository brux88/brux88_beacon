// lib/src/models/selected_beacon.dart
class SelectedBeacon {
  final String uuid;
  final String? major;
  final String? minor;
  final String? name;
  final bool enabled;

  SelectedBeacon({
    required this.uuid,
    this.major,
    this.minor,
    this.name,
    this.enabled = true,
  });

  factory SelectedBeacon.fromMap(Map<dynamic, dynamic> map) {
    return SelectedBeacon(
      uuid: map['uuid'] as String,
      major: map['major'] as String?,
      minor: map['minor'] as String?,
      name: map['name'] as String?,
      enabled: map['enabled'] as bool? ?? true,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'uuid': uuid,
      'major': major,
      'minor': minor,
      'name': name,
      'enabled': enabled,
    };
  }

  @override
  String toString() {
    return 'SelectedBeacon{uuid: $uuid, major: $major, minor: $minor, name: $name, enabled: $enabled}';
  }
}
