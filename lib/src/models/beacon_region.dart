// lib/src/models/beacon_region.dart
class BeaconRegion {
  final String identifier;
  final String uuid;
  final String? major;
  final String? minor;
  final bool notifyEntryStateOnDisplay;

  BeaconRegion({
    required this.identifier,
    required this.uuid,
    this.major,
    this.minor,
    this.notifyEntryStateOnDisplay = false,
  });

  Map<String, dynamic> toMap() {
    return {
      'identifier': identifier,
      'uuid': uuid,
      'major': major,
      'minor': minor,
      'notifyEntryStateOnDisplay': notifyEntryStateOnDisplay,
    };
  }

  @override
  String toString() {
    return 'BeaconRegion{identifier: $identifier, uuid: $uuid, major: $major, minor: $minor}';
  }
}
