# helmet-mobile-admin

Mobile admin Android APP for smart helmet management — device monitoring, alarm handling, account management.

## Architecture

```
Login (account+password) → JWT → Admin dashboard
  ├─ Dashboard: device stats, online count, alarm overview
  ├─ Device list: search, add, detail, assign/unassign
  ├─ Alarms: list, filter, handle
  ├─ Accounts: hierarchy-based sub-admin management
  └─ Monitor: HLS live view + playback/screenshots/call records
```

- Kotlin, minSdk 26, targetSdk 34
- OkHttp 4.12 + Gson for API communication
- Material Components for UI
- HLS (VideoView) for live monitoring
- Coroutines for async operations

## Commands

```bash
# Build: ./gradlew assembleDebug --no-daemon
# Clean build: rm -rf app/build && ./gradlew assembleDebug --no-daemon
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Key Files

| File | Purpose |
|------|---------|
| ApiService.kt | HTTP client, JWT auth, all API endpoints |
| LoginActivity.kt | Login UI, token storage |
| MainActivity.kt | Dashboard, device/adm/account/monitor tabs |
| DeviceDetailActivity.kt | Device info, recordings, screenshots, call history |
| MonitorActivity.kt | HLS live video player |

## Hard Constraints

- API uses JWT Bearer token auth (authRequired middleware on server)
- Account hierarchy: super admin (level 0) > admin (1) > user (2)
- Permission inheritance: admins can only manage their own sub-accounts
- All API responses use `{ code: 0/-1, msg, data }` envelope
