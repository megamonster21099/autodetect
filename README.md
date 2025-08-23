# Autodetect App

An Android application that can run in two modes: CLIENT and TARGET. The app provides location tracking functionality with Firebase backend integration.

## Features

### TARGET Mode
- Runs a foreground service that detects device location every 30 seconds
- **Smart location saving**: Only saves new locations if they differ by more than 10 meters from the last saved location
- **Timestamp updates**: If location hasn't changed significantly, only the timestamp of the last record is updated
- **Automatic cleanup**: Maintains maximum 1000 location records, automatically removing oldest entries
- Saves optimized location data to Firebase Realtime Database
- Uses WorkManager to monitor and restart the service if needed
- Automatically starts on device boot
- Compatible with Android 10 and above

### CLIENT Mode
- Fetches and displays all saved location data from Firebase
- Shows locations in a list with date/time
- Tapping any location item opens it in Google Maps
- No background services running

## Permissions Required
- `ACCESS_FINE_LOCATION` - For precise location tracking
- `ACCESS_COARSE_LOCATION` - For approximate location tracking
- `FOREGROUND_SERVICE` - For running location service in background
- `FOREGROUND_SERVICE_LOCATION` - For location-specific foreground service (Android 10+)
- `RECEIVE_BOOT_COMPLETED` - For auto-starting after device reboot
- `INTERNET` - For Firebase communication
