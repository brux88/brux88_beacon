// lib/src/utils/log_manager.dart
enum LogLevel { debug, info, warning, error }

class LogManager {
  static final LogManager _instance = LogManager._internal();
  factory LogManager() => _instance;
  LogManager._internal();

  bool _enableLogs = false;
  List<LogEntry> _logs = [];
  final int _maxLogs = 100;

  void enableLogs(bool enable) {
    _enableLogs = enable;
  }

  void log(LogLevel level, String message) {
    if (!_enableLogs) return;

    final entry = LogEntry(
      timestamp: DateTime.now(),
      level: level,
      message: message,
    );

    _logs.add(entry);
    if (_logs.length > _maxLogs) {
      _logs.removeAt(0);
    }

    // Print to console for development purposes
    final prefix = level.toString().split('.').last.toUpperCase();
    print('BEACON_PLUGIN [$prefix]: $message');
  }

  List<LogEntry> getLogs() {
    return List.unmodifiable(_logs);
  }

  void clearLogs() {
    _logs.clear();
  }
}

class LogEntry {
  final DateTime timestamp;
  final LogLevel level;
  final String message;

  LogEntry({
    required this.timestamp,
    required this.level,
    required this.message,
  });

  @override
  String toString() {
    final levelStr = level.toString().split('.').last.toUpperCase();
    final timeStr =
        '${timestamp.hour}:${timestamp.minute}:${timestamp.second}.${timestamp.millisecond}';
    return '[$timeStr] $levelStr: $message';
  }

  Map<String, dynamic> toMap() {
    return {
      'timestamp': timestamp.millisecondsSinceEpoch,
      'level': level.toString().split('.').last,
      'message': message,
    };
  }
}
