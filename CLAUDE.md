# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BingWallpaper is an Android application that automatically sets Bing's daily image as device wallpaper. It's written in Java with Android SDK and uses Gradle for build management.

## Common Development Commands

### Build Commands
```bash
# Clean and build the project
./gradlew clean build

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug APK to connected device
./gradlew installDebug
```

### Testing Commands
```bash
# Run unit tests
./gradlew test

# Run unit tests with Robolectric (includes Android resources)
./gradlew testDebugUnitTest
```

### Code Quality
```bash
# Run lint checks
./gradlew lint

# Check lint but don't fail on errors (configured in build.gradle)
./gradlew lintDebug
```

## Project Architecture

### Core Structure
- **Main Application**: `MApplication.java` - Application entry point with initialization
- **Primary Activity**: `MainActivity.java` - Main UI that displays Bing wallpapers
- **Package Structure**: `me.liaoheng.wallpaper` - Base package namespace

### Key Components

#### UI Layer (`ui/`)
- `MainActivity` - Primary wallpaper display and management
- `SettingsActivity` - Application preferences and configuration
- `WallpaperDetailActivity` - Detailed wallpaper view
- `WallpaperHistoryListActivity` - Browse wallpaper history

#### Services (`service/`)
- `BingWallpaperIntentService` - Background wallpaper operations
- `LiveWallpaperService` - Live wallpaper implementation
- `BingWallpaperWorker` - WorkManager-based scheduling
- `AutoSetWallpaperBroadcastReceiver` - Alarm-based automation

#### Data Layer (`data/`)
- `BingWallpaperNetworkClient` - API client for Bing services
- `BingWallpaperNetworkService` - Network service interface
- `db/DBHelper` - SQLite database management
- `provider/TasksProvider` - Content provider for task data

#### Models (`model/`)
- `BingWallpaper` - Core wallpaper data model
- `Config` - Application configuration
- `Wallpaper` - Wallpaper metadata

#### Utilities (`util/`)
- `BingWallpaperUtils` - Core wallpaper operations
- `WallpaperUtils` - Wallpaper management utilities
- `Settings` - SharedPreferences management
- `WorkerManager` - Background task coordination

### Key Technologies
- **Networking**: Retrofit + OkHttp + RxJava3
- **Image Loading**: Glide with custom module
- **Database**: SQLite with custom provider
- **Background Work**: WorkManager + AlarmManager
- **Dependency Management**: External dependencies.gradle from GitHub
- **Error Tracking**: Sentry integration
- **Analytics**: Optional Firebase Analytics (if google-services.json exists)

### Build Configuration
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 35
- **Compile SDK**: 35
- **Java Version**: 11
- **Signing**: Configurable via local.properties
- **ViewBinding**: Enabled
- **Proguard**: Disabled for release builds

### Testing Setup
- **Unit Tests**: JUnit + Robolectric
- **Test Location**: `app/src/test/java/`
- **Android Resources**: Included in tests via `includeAndroidResources = true`

### Development Notes
- The app supports both phone and tablet layouts via resource qualifiers
- Live wallpaper runs in separate process (`:live_wallpaper`)
- Background service runs in separate process (`:background`)
- Multi-language support with Crowdin integration
- MIUI-specific dialog handling for system compatibility