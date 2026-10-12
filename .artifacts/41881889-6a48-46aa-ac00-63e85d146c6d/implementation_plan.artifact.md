# Implementation Plan - Dependency Updates

This plan outlines the updates for various project dependencies, plugins, and the Gradle wrapper to their latest stable versions as identified by the `dependencyUpdates` report.

## User Review Required

> [!IMPORTANT]
> The `NewPipeExtractor` version is reported as having a "later" version `0.24.6` while the current version is `v0.26.1`. This appears to be a versioning scheme change or an incorrect recommendation from the plugin. I will **not** update this dependency to avoid an accidental downgrade.

> [!NOTE]
> `androidx.compose.material3:material3` is currently at `1.5.0-alpha20`. The report suggests `1.4.0` is the latest milestone. Since we are already on an alpha version (likely for specific features), I will leave it at `1.5.0-alpha20` unless you prefer to move to a stable version.

## Proposed Changes

### Gradle Infrastructure

#### [MODIFY] [gradle-wrapper.properties](file:///C:/Users/shubh/AndroidStudioProjects/SpatialFlow/gradle/wrapper/gradle-wrapper.properties)
- Update `distributionUrl` to use Gradle `9.7.1`.

#### [MODIFY] [settings.gradle](file:///C:/Users/shubh/AndroidStudioProjects/SpatialFlow/settings.gradle)
- Update `com.android.settings` plugin from `9.3.0` to `9.4.0`.

### Dependency Management

#### [MODIFY] [libs.versions.toml](file:///C:/Users/shubh/AndroidStudioProjects/SpatialFlow/gradle/libs.versions.toml)
Update the following versions:
- `agp`: `9.3.0` -> `9.4.0`
- `composeBom`: `2026.06.01` -> `2026.09.00`
- `navigationUi`: `2.9.8` -> `2.10.1`
- `room`: `2.8.4` -> `2.8.5` (common, runtime, compiler)
- `securityCrypto`: `1.1.0-alpha06` -> `1.1.0` (Stable)
- `ksp`: `2.3.11` -> `2.3.12`
- `loggingInterceptor`: `5.4.0` -> `5.5.0`
- `ktor`: `3.5.2` -> `3.6.0`
- `kotlin`: `2.4.10` -> `2.4.20`
- `kotlinStdlib`: `2.2.10` -> `2.4.20`
- `media3`: `1.11.0` -> `1.11.1`
- `rhino`: `1.8.1` -> `1.9.1`

### Module-specific Updates

#### [MODIFY] [build.gradle](file:///C:/Users/shubh/AndroidStudioProjects/SpatialFlow/app/build.gradle)
- Update `io.github.ben-manes.versions` plugin from `0.61.0` to `0.64.0`.

## Verification Plan

### Automated Tests
- Run `gradle sync` to ensure all dependencies resolve correctly.
- Run `app:assembleDebug` to verify the build still succeeds with new dependencies.
- (Optional) Run `dependencyUpdates` again to see the updated report.

### Manual Verification
- Verify that the app launches correctly on a device/emulator (if requested).
