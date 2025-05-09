# Changelog

All notable changes to the Brux88 Beacon plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.2] - 2025-05-10

### Added
- New feature to control beacon detection notifications separately from service notifications
- Added `setShowDetectionNotifications(bool)` method to enable/disable detection notifications without affecting foreground service
- Enhanced battery optimization handling to improve background performance
- Improved error handling and logging for debugging purposes

### Fixed
- Fixed issue with service restarts on some Android devices
- Enhanced Bluetooth state management to handle unexpected disconnections
- Improved memory usage in background monitoring mode

## [0.1.1] - 2025-05-07

### Fixed
- Bug fixes for Android 13+ compatibility
- Improved handling of location permissions
- Enhanced stability for background monitoring
- Fixed memory leak in beacon scanning process

## [0.1.0] - 2025-05-05

### Added
- Initial release with support for Android and iOS
- Background beacon monitoring with service persistence
- Foreground beacon ranging with distance estimation
- Support for iBeacon, AltBeacon, and Eddystone formats
- Customizable scan settings for battery optimization
- Notification system for region entry/exit events
- Permission management for Bluetooth and location
- Battery optimization handling for Android
- Boot persistence for auto-restart after device reboot
- Comprehensive logging system for debugging
- Region filtering capabilities
- Individual beacon selection and filtering
- Stream-based API for beacon and monitoring events
- Support for Flutter 3.3.0 and above
- Complete Android and iOS implementations
- Comprehensive documentation and examples

### Fixed
- None (initial release)

### Changed
- None (initial release)

### Removed
- None (initial release)