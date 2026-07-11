# Remove Rejoin + File Sharing System

**Date:** 2026-07-11
**Status:** Approved

## Goal

Remove the "rejoin" feature and the entire file sharing (upload/download/P2P) system from both the web app and Android app. Keep the original hash-based file verification flow where both users must have the same local file.

## Background

Commit `999bd3c` ("movie share introduction through p2p webrtc") introduced a file sharing system on top of the original hash-verification flow. The original flow:

1. Host picks a local file, computes a SHA-256 quick fingerprint, creates a room with that fingerprint in Supabase
2. Guest joins with room code, picks a matching local file, fingerprint is compared on the backend
3. If fingerprints match, guest is auto-verified and added as a verified participant
4. Host starts watching when enough verified participants are present

The file sharing system added upload/download infrastructure (storage.to API, WebRTC P2P, Firebase RTDB signaling) to allow guests who don't have the file to download it. This whole layer is being removed. Voice chat, sync engine, drift correction, chat, and participant presence are all untouched.

## Scope

### Remove: Rejoin (Web)

- `web/src/recent-room.js` — entire file (localStorage-based recent room persistence)
- `web/src/screens/home.js` — `renderRecentRoomToast()`, `getRecentRoom`/`clearRecentRoom` imports, recent room rendering
- `web/src/screens/lobby.js` — `saveRecentRoom` import and call in `init()`
- `web/src/style.css` — `.recent-room-rejoin`, `.recent-toast`, `.recent-toast-text`, `.recent-toast-btn`, `.recent-toast-dismiss` CSS classes

### Remove: Rejoin (Android)

- `app/.../data/repository/RecentRoomRepository.kt` — entire file
- `app/.../ui/home/HomeViewModel.kt` — `recentRoom` state flow, `clearRecentRoom()`, `RecentRoom` import
- `app/.../ui/home/HomeScreen.kt` — `RecentRoomCard`, `recentRoom` references, `onRejoinRoom` callback in NavGraph
- `app/.../di/AppContainer.kt` — `RecentRoomRepository` instantiation
- `app/.../ui/navigation/NavGraph.kt` — `onRejoinRoom` handler in HomeScreen composable call

### Remove: File Sharing Cloud Upload/Download (Web)

- `web/src/firebase-file-share.js` — entire file (publish/observe/clear/get file share from Firebase RTDB)
- `web/src/storage-to-api.js` — entire file (upload to storage.to, download, multipart upload, progress tracking)
- `web/src/file-hasher.js` — entire file (migrate `computeQuickFingerprint` into `create.js`)

### Remove: File Sharing UI (Web)

- `web/src/screens/lobby.js` — all file sharing code (lines 198-463):
  - `renderFileShare()` — host/guest file share card rendering
  - `renderGuestFileControls()` — download/select/verify guest controls
  - `startSharing()` — host upload to cloud
  - `startDownload()` — guest trigger native download
  - `verifyFile()` — guest file verification flow
  - `localFileState` object and related state
  - `currentFileShare`, `unsubFileShare`, `verifiedInCurrentRoom`
  - Imports: `computeQuickFingerprint`, `verifyParticipant`, `observeFileShare`, `publishFileShare`, `uploadToStorageTo`, `triggerNativeDownload`, `formatBytes`
- `web/src/style.css` — `.file-share-card`, `.file-share-header`, `.file-share-title`, `.file-share-subtitle`, `.file-share-status`, `.file-share-pill`, `.file-share-actions`, `.download-progress-bar`, `.download-progress-fill` CSS classes

### Remove: File Sharing Cloud Upload/Download (Android)

- `app/.../data/cloud/StorageToApi.kt` — entire file
- `app/.../data/cloud/StorageToTransferService.kt` — entire file
- `app/src/main/AndroidManifest.xml` — `StorageToTransferService` declaration with `foregroundServiceType="dataSync"`
- `app/build.gradle.kts` — remove `io.github.webrtc-sdk:android` dependency

### Remove: File Sharing P2P (Android)

- `app/.../data/p2p/` — entire directory (FileShareSignaling.kt, WebRTCFileTransfer.kt)

### Remove: File Sharing UI (Android)

- `app/.../ui/lobby/LobbyFileShareViewModel.kt` — entire file
- `app/.../ui/lobby/FileShareSection.kt` — entire file
- `app/.../ui/lobby/LobbyScreen.kt` — remove `FileShareSection` composable call, `fileShareViewModel` usage, `LobbyFileShareViewModel` import, file picker launcher, `verifiedVideoUri`, `fileShareState` observation

### Remove: Firebase Rules

- `firebase/database.rules.json` — remove `"fileShare"` node from rules

### Keep & Restore: Original Hash Verification Flow

The original flow must be restored. These files stay but need changes:

- **`web/src/room-repository.js`:** Restore `joinRoom(userId, displayName, code)` → `joinRoom(userId, displayName, code, fingerprint)`. Add fingerprint comparison. Restore `JoinResult.FingerprintMismatch`.
- **`web/src/screens/join.js`:** Add file picker → fingerprint → join with fingerprint flow (currently just enters code and joins without verification).
- **`web/src/screens/create.js`:** Stays as-is (already computes fingerprint and creates room with it).
- **`app/.../data/repository/RoomRepository.kt`:** Restore fingerprint parameter in `joinRoom()`, restore `JoinResult.FingerprintMismatch`.
- **`app/.../ui/join/JoinRoomViewModel.kt`:** Stays mostly as-is (already has `joinWithFile` → fingerprint → join flow).
- **`app/.../ui/join/JoinRoomScreen.kt`:** Stays as-is (already has file picker).
- **`app/.../ui/create/CreateRoomViewModel.kt`:** Stays as-is.
- **`app/.../data/sync/FileHasher.kt`:** Stays (needed for fingerprinting).
- **`app/.../data/firebase/FirebaseSync.kt`:** Keep `setPresenceVerified()` — needed for verified participant tracking.
- **`app/.../di/AppContainer.kt`:** Remove `fileShareSignaling`, keep `fileHasher`.
- **`app/.../ui/lobby/LobbyScreen.kt`:** Simplify start watching — host can press start when enough participants have joined (verified count check stays but uses the original auto-verified-on-join flow).
- **`app/.../ui/lobby/LobbyViewModel.kt`:** Remove file share state, keep presence/participant observation.

## Files Unchanged

- Voice chat (`web/src/voice-chat.js`, `app/.../ui/watch/VoiceChatViewModel.kt`, LiveKit integration)
- Sync engine (`web/src/sync-engine.js`, `app/.../data/sync/SyncEngine.kt`)
- Drift correction (`web/src/drift-corrector.js`, `app/.../data/sync/DriftCorrector.kt`)
- Chat (`web/src/components/chat.js`, `app/.../ui/chat/`)
- Router (`web/src/router.js`, `app/.../ui/navigation/NavGraph.kt`)
- Watch screen (`web/src/screens/watch.js`, `app/.../ui/watch/`)
- Supabase schema (rooms, participants, playlists tables — unchanged)
- App update system (`UpdateManager.kt`, `UpdateViewModel.kt`)
- Theme, components, and utility files

## Data Flow After Removal

```
CREATE ROOM:
  Host opens app → Create Room → picks video file
  → computeQuickFingerprint(file) → creates room with movie_fingerprint in Supabase
  → navigates to lobby (waiting)

JOIN ROOM:
  Guest opens app → Join Room → enters code → picks matching video file
  → computeQuickFingerprint(file) → joinRoom(code, fingerprint)
  → if fingerprint == room.movie_fingerprint → auto-verified → lobby
  → if mismatch → error "File doesn't match the room"

START WATCHING:
  Host sees verified participants count (>=2)
  → clicks Start → sets room started in Firebase
  → all verified participants auto-navigate to watch screen

WATCH:
  Sync engine, drift correction, voice chat, chat all work as before
  Uses local file from ObjectURL (web) or URI (Android)
```

## File Manifest

### Files to Delete Entirely
1. `web/src/recent-room.js`
2. `web/src/firebase-file-share.js`
3. `web/src/storage-to-api.js`
4. `web/src/file-hasher.js`
5. `app/.../data/repository/RecentRoomRepository.kt`
6. `app/.../data/cloud/StorageToApi.kt`
7. `app/.../data/cloud/StorageToTransferService.kt`
8. `app/.../data/p2p/FileShareSignaling.kt`
9. `app/.../data/p2p/WebRTCFileTransfer.kt`
10. `app/.../ui/lobby/LobbyFileShareViewModel.kt`
11. `app/.../ui/lobby/FileShareSection.kt`

### Files to Edit (Web)
1. `web/src/screens/home.js` — remove recent room imports + toast rendering
2. `web/src/screens/lobby.js` — remove file sharing code + saveRecentRoom import
3. `web/src/screens/join.js` — add file picker + fingerprint + join with fingerprint
4. `web/src/room-repository.js` — restore fingerprint in joinRoom, restore FingerprintMismatch
5. `web/src/screens/create.js` — consolidate computeQuickFingerprint (move inline)
6. `web/src/style.css` — remove rejoin CSS + file share CSS
7. `web/src/firebase-sync.js` — keep setPresenceVerified, remove fileShare references

### Files to Edit (Android)
1. `app/.../di/AppContainer.kt` — remove FileShareSignaling, RecentRoomRepository wiring
2. `app/.../ui/home/HomeViewModel.kt` — remove recentRoom state
3. `app/.../ui/home/HomeScreen.kt` — remove RecentRoomCard
4. `app/.../ui/navigation/NavGraph.kt` — remove onRejoinRoom handler
5. `app/.../ui/lobby/LobbyScreen.kt` — remove file share section, simplify
6. `app/.../ui/lobby/LobbyViewModel.kt` — remove file share state
7. `app/.../data/repository/RoomRepository.kt` — restore fingerprint in joinRoom
8. `app/src/main/AndroidManifest.xml` — remove StorageToTransferService
9. `app/build.gradle.kts` — remove WebRTC dependency

### Files to Edit (Backend)
1. `firebase/database.rules.json` — remove fileShare node
