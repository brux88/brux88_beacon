// lib/src/models/beacon.dart
class Beacon {
  final String uuid;
  final String? major;
  final String? minor;
  final double distance;
  final int rssi;
  final int txPower;
  final DateTime lastSeen; // Aggiungi questo campo

  Beacon({
    required this.uuid,
    this.major,
    this.minor,
    required this.distance,
    required this.rssi,
    required this.txPower,
    DateTime? lastSeen,
  }) : this.lastSeen = lastSeen ?? DateTime.now();

  factory Beacon.fromMap(Map<dynamic, dynamic> map) {
    return Beacon(
      uuid: map['uuid'] as String,
      major: map['major'] as String?,
      minor: map['minor'] as String?,
      distance: map['distance'] as double,
      rssi: map['rssi'] as int,
      txPower: map['txPower'] as int,
      lastSeen: DateTime.now(), // Imposta il timestamp attuale
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'uuid': uuid,
      'major': major,
      'minor': minor,
      'distance': distance,
      'rssi': rssi,
      'txPower': txPower,
      'lastSeen': lastSeen.millisecondsSinceEpoch,
    };
  }

  @override
  String toString() {
    return 'Beacon{uuid: $uuid, major: $major, minor: $minor, distance: $distance, rssi: $rssi, txPower: $txPower}';
  }
}
