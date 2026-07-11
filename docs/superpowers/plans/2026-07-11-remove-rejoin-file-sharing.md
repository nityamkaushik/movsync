# Remove Rejoin + File Sharing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Remove rejoin and file sharing (upload/download/P2P) systems from web and Android. Restore original hash-only verification flow.

**Architecture:** Delete 11 files entirely, edit 16 files across web and Android. Voice chat, sync, drift correction, chat are untouched. The original create-room-with-fingerprint + join-room-with-fingerprint flow is restored on both platforms.

**Tech Stack:** JavaScript (vanilla, COOP+COEP), Kotlin (Jetpack Compose), Firebase RTDB, Supabase

## Global Constraints

- Web uses vanilla JS (no framework) with Vite
- Android uses Jetpack Compose + Kotlin coroutines
- Firebase RTDB path: `movsync/rooms/{roomCode}`
- Voice chat, sync engine, drift correction — NO CHANGES
- All file hashing uses SHA-256 quick fingerprint (3 x 4MB chunks + file size)

---

### Task 1: Remove Web Rejoin Feature

**Files:**
- Delete: `web/src/recent-room.js`
- Modify: `web/src/screens/home.js:1-18` (remove imports + toast rendering)
- Modify: `web/src/screens/lobby.js:14` (remove `saveRecentRoom` import)
- Modify: `web/src/screens/lobby.js:136-140` (remove `saveRecentRoom()` call)
- Modify: `web/src/style.css` (remove `.recent-room-rejoin`, `.recent-toast`, `.recent-toast-text`, `.recent-toast-btn`, `.recent-toast-dismiss` CSS blocks)

- [ ] **Step 1: Delete recent-room.js**

```bash
Remove-Item -LiteralPath "web/src/recent-room.js"
```

- [ ] **Step 2: Remove rejoin imports and rendering from home.js**

Edit `web/src/screens/home.js`:
- Line 8: Remove `import { clearRecentRoom, getRecentRoom } from '../recent-room.js';`
- Line 13: Remove `const recentRoom = getRecentRoom();`
- Lines 193-196: Remove the entire block:
```js
  // ── Recent room ────────────────────────────────────────
  if (recentRoom) {
    renderRecentRoomToast(container, recentRoom);
  }
```
- Lines 264-281: Remove the entire `renderRecentRoomToast` function

- [ ] **Step 3: Remove saveRecentRoom import from lobby.js**

Edit `web/src/screens/lobby.js` line 14: Remove `import { saveRecentRoom } from '../recent-room.js';`

- [ ] **Step 4: Remove saveRecentRoom call from lobby.js**

Edit `web/src/screens/lobby.js` lines 136-140: Remove:
```js
  saveRecentRoom({
    code: roomCode,
    movieName: currentRoom?.movie_name,
    isHost,
  });
```

- [ ] **Step 5: Remove rejoin CSS from style.css**

In `web/src/style.css`, remove the CSS block for `.recent-room-rejoin` (approx line 838) and the `.recent-toast`, `.recent-toast-text`, `.recent-toast-btn`, `.recent-toast-dismiss` classes.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "remove rejoin feature from web"
```

---

### Task 2: Remove Android Rejoin Feature

**Files:**
- Delete: `app/src/main/java/com/nityam/movsync/data/repository/RecentRoomRepository.kt`
- Modify: `app/src/main/java/com/nityam/movsync/di/AppContainer.kt:9,25`
- Modify: `app/src/main/java/com/nityam/movsync/ui/home/HomeViewModel.kt:7,25,34,59-61`
- Modify: `app/src/main/java/com/nityam/movsync/ui/home/HomeScreen.kt:48,317-323,402-469`
- Modify: `app/src/main/java/com/nityam/movsync/ui/navigation/NavGraph.kt:32-34`

- [ ] **Step 1: Delete RecentRoomRepository.kt**

```bash
Remove-Item -LiteralPath "app/src/main/java/com/nityam/movsync/data/repository/RecentRoomRepository.kt"
```

- [ ] **Step 2: Remove RecentRoomRepository wiring from AppContainer.kt**

Edit `app/.../di/AppContainer.kt`:
- Line 9: Remove `import com.nityam.movsync.data.repository.RecentRoomRepository`
- Line 25: Remove `val recentRoomRepository = RecentRoomRepository(context.applicationContext)`

- [ ] **Step 3: Clean up HomeViewModel.kt**

Edit `app/.../ui/home/HomeViewModel.kt`:
- Line 7: Remove `import com.nityam.movsync.data.repository.RecentRoom`
- Line 25: Remove `private val recentRoomRepository = app.container.recentRoomRepository`
- Line 34: Remove `val recentRoom: StateFlow<RecentRoom?> = recentRoomRepository.recentRoom`
- Lines 59-61: Remove the entire `clearRecentRoom()` function

- [ ] **Step 4: Remove RecentRoomCard from HomeScreen.kt**

Edit `app/.../ui/home/HomeScreen.kt`:
- Line 48: Remove `import com.nityam.movsync.data.repository.RecentRoom`
- Lines 317-323: Remove the recent room card rendering block:
```kotlin
                recentRoom?.let { room ->
                    RecentRoomCard(
                        room = room,
                        onRejoin = { onRejoinRoom(room.code) },
                        onDismiss = viewModel::clearRecentRoom
                    )
                }
```
- Lines 402-469: Remove the entire `RecentRoomCard` composable function

- [ ] **Step 5: Remove onRejoinRoom from NavGraph.kt**

Edit `app/.../ui/navigation/NavGraph.kt` lines 32-34: Remove `onRejoinRoom = { code -> navController.navigate(Route.Lobby.create(code, isHost = false, uri = null)) },` from the HomeScreen composable call

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "remove rejoin feature from Android"
```

---

### Task 3: Remove Web File Sharing System — Delete Core Module Files

**Files:**
- Delete: `web/src/firebase-file-share.js`
- Delete: `web/src/storage-to-api.js`
- Delete: `web/src/file-hasher.js`

- [ ] **Step 1: Delete firebase-file-share.js**

```bash
Remove-Item -LiteralPath "web/src/firebase-file-share.js"
```

- [ ] **Step 2: Delete storage-to-api.js**

```bash
Remove-Item -LiteralPath "web/src/storage-to-api.js"
```

- [ ] **Step 3: Extract computeQuickFingerprint from file-hasher.js into create.js before deleting**

First, copy the `computeQuickFingerprint` function from `web/src/file-hasher.js` into `web/src/screens/create.js` as a local function. The function computes SHA-256 of 3 x 4MB chunks (head, middle, tail) + file size.

Then delete:
```bash
Remove-Item -LiteralPath "web/src/file-hasher.js"
```

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "remove file sharing core modules from web"
```

---

### Task 4: Remove Web File Sharing UI + Restore Original Join Verification

**Files:**
- Modify: `web/src/screens/lobby.js` — remove all file sharing code (lines 198-463)
- Modify: `web/src/screens/create.js` — add `computeQuickFingerprint` inline
- Modify: `web/src/screens/join.js` — add file picker + fingerprint join flow
- Modify: `web/src/room-repository.js` — restore `joinRoom` with fingerprint parameter
- Modify: `web/src/style.css` — remove file-share CSS classes
- Modify: `web/src/screens/home.js` — clean up `window.__movsync_pendingCreateFile` reference

- [ ] **Step 1: Update room-repository.js to restore fingerprint join**

Edit `web/src/room-repository.js`:
- Change `joinRoom(userId, displayName, code)` to `joinRoom(userId, displayName, code, fingerprint)`
- Add fingerprint comparison logic:
```js
export async function joinRoom(userId, displayName, code, fingerprint) {
  const room = await getRoomByCode(code);
  if (!room) return { result: 'not_found' };
  if (room.movie_fingerprint && room.movie_fingerprint !== fingerprint) {
    return { result: 'fingerprint_mismatch', room };
  }
  await addParticipant(room.id, userId, displayName, false, true);
  await firebaseSync.trackPresence(room.code, userId, displayName, false, true);
  return { result: 'joined', room };
}
```

- [ ] **Step 2: Add computeQuickFingerprint to create.js**

Add the `computeQuickFingerprint` function (from file-hasher.js) at the bottom of `web/src/screens/create.js` (before or after `getVideoDuration`). Update the import to remove reference to file-hasher.js.

Remove the import line: `import { computeQuickFingerprint } from '../file-hasher.js';` and make it a local function.

- [ ] **Step 3: Update join.js with file picker + fingerprint flow**

Edit `web/src/screens/join.js`:
- Add file picker HTML (`<input type="file" id="joinFileInput" accept="video/*" hidden />`)
- Add `computeQuickFingerprint` as a local function (copy same impl as in create.js)
- Change `processJoin` to first pick a file, compute fingerprint, then call `joinRoom(code, fingerprint)`
- Handle `fingerprint_mismatch` result by showing error message

- [ ] **Step 4: Strip file sharing code from lobby.js**

Edit `web/src/screens/lobby.js`:
- Remove imports for: `computeQuickFingerprint`, `verifyParticipant`, `observeFileShare`, `publishFileShare`, `uploadToStorageTo`, `triggerNativeDownload`, `formatBytes`
- Remove `currentFileShare` variable declaration (line 24)
- Remove `localFileState` object (lines 31-37)
- Remove `verifiedInCurrentRoom` (line 29)
- Remove `unsubFileShare` from cleanup
- Remove `currentFileShare = null` from cleanup
- Remove `verifyFile()`, `startSharing()`, `startDownload()`, `renderFileShare()`, `renderGuestFileControls()` functions
- Remove `renderFileShare()` call in `init()`
- Remove `observeFileShare()` call in `init()`
- Remove the `#lobbyFileInput` hidden input and its change listener
- Keep `window.__movsync_file` and `window.__movsync_videoUrl` — used by watch screen
- Simplify the lobby HTML — replace `#fileShareContainer` section with a simple waiting status

- [ ] **Step 5: Remove file share CSS from style.css**

Remove the following CSS blocks from `web/src/style.css`:
- `.file-share-card`, `.file-share-card::before`
- `.file-share-header`, `.file-share-title`, `.file-share-subtitle`
- `.file-share-status`, `.file-share-status.success`, `.file-share-status.error`
- `.file-share-pill`, `.file-share-pill.pill-live`
- `.file-share-actions`
- `.download-progress-bar`, `.download-progress-fill`
- Any `.recent-*` CSS if not already removed in Task 1

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "remove file sharing UI from web, restore fingerprint join"
```

---

### Task 5: Remove Android File Sharing System — Delete Core Files

**Files:**
- Delete: `app/src/main/java/com/nityam/movsync/data/cloud/StorageToApi.kt`
- Delete: `app/src/main/java/com/nityam/movsync/data/cloud/StorageToTransferService.kt`
- Delete: `app/src/main/java/com/nityam/movsync/data/p2p/FileShareSignaling.kt`
- Delete: `app/src/main/java/com/nityam/movsync/data/p2p/WebRTCFileTransfer.kt`
- Delete: `app/src/main/java/com/nityam/movsync/ui/lobby/LobbyFileShareViewModel.kt`
- Delete: `app/src/main/java/com/nityam/movsync/ui/lobby/FileShareSection.kt`
- Modify: `app/src/main/AndroidManifest.xml` — remove StorageToTransferService declaration
- Modify: `app/build.gradle.kts` — remove WebRTC dependency

- [ ] **Step 1: Delete all file sharing Kotlin files**

```bash
Remove-Item -LiteralPath "app/src/main/java/com/nityam/movsync/data/cloud/StorageToApi.kt"
Remove-Item -LiteralPath "app/src/main/java/com/nityam/movsync/data/cloud/StorageToTransferService.kt"
Remove-Item -LiteralPath "app/src/main/java/com/nityam/movsync/data/p2p/FileShareSignaling.kt"
Remove-Item -LiteralPath "app/src/main/java/com/nityam/movsync/data/p2p/WebRTCFileTransfer.kt"
Remove-Item -LiteralPath "app/src/main/java/com/nityam/movsync/ui/lobby/LobbyFileShareViewModel.kt"
Remove-Item -LiteralPath "app/src/main/java/com/nityam/movsync/ui/lobby/FileShareSection.kt"
```

- [ ] **Step 2: Remove StorageToTransferService from AndroidManifest.xml**

Edit `app/src/main/AndroidManifest.xml`:
Remove the `<service android:name=".data.cloud.StorageToTransferService" android:foregroundServiceType="dataSync" .../>` declaration.

- [ ] **Step 3: Remove WebRTC dependency from build.gradle.kts**

Edit `app/build.gradle.kts`:
Remove `implementation("io.github.webrtc-sdk:android:104.5112.09")`

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "remove file sharing core files from Android"
```

---

### Task 6: Remove Android File Sharing UI + Restore Original Join Verification

**Files:**
- Modify: `app/.../di/AppContainer.kt` — remove fileShareSignaling, keep fileHasher
- Modify: `app/.../ui/lobby/LobbyScreen.kt` — remove file share section, simplify
- Modify: `app/.../ui/lobby/LobbyViewModel.kt` — remove file share state
- Modify: `app/.../data/repository/RoomRepository.kt` — restore fingerprint in joinRoom

- [ ] **Step 1: Clean up AppContainer.kt**

Edit `app/.../di/AppContainer.kt`:
- Remove line 7: `import com.nityam.movsync.data.p2p.FileShareSignaling`
- Remove line 23: `val fileShareSignaling = FileShareSignaling(firebaseDatabase)`
- Keep `FileHasher` import + instantiation

- [ ] **Step 2: Restore RoomRepository.kt joinRoom with fingerprint**

Edit `app/.../data/repository/RoomRepository.kt`:
- Change `joinRoom(userId: String, displayName: String, code: String)` to `joinRoom(userId: String, displayName: String, code: String, fingerprint: String)`
- Restore fingerprint comparison logic:
```kotlin
suspend fun joinRoom(
    userId: String,
    displayName: String,
    code: String,
    fingerprint: String
): JoinResult {
    val room = getRoomByCode(code) ?: return JoinResult.NotFound
    if (room.movieFingerprint != fingerprint) {
        return JoinResult.FingerprintMismatch(room)
    }
    addParticipant(room.id, userId, displayName, isHost = false, verified = true)
    firebaseSync.trackPresence(room.code, userId, displayName, isHost = false, verified = true)
    return JoinResult.Joined(room)
}
```
- Restore `JoinResult.FingerprintMismatch`:
```kotlin
sealed interface JoinResult {
    data class Joined(val room: Room) : JoinResult
    data class FingerprintMismatch(val room: Room) : JoinResult
    data object NotFound : JoinResult
}
```
- Keep `verifyParticipant()` — still called when host manually verifies

- [ ] **Step 3: Clean up LobbyScreen.kt**

Edit `app/.../ui/lobby/LobbyScreen.kt`:
- Remove `LobbyFileShareViewModel` import and viewModel parameter
- Remove `fileShareViewModel` usage
- Remove `FileShareSection` composable call (lines 200-218)
- Remove file picker launcher (lines 91-103)
- Remove `verifiedVideoUri` observation (line 67)
- Remove `fileShareViewModel.cleanup()` call
- Remove `fileShareViewModel.observeFileShare()` call
- Simplify `LobbyActionCard` — remove `verifiedCount` check, host can click start immediately when ready
- Remove the snackbar about "Select or download the movie file first"

- [ ] **Step 4: Clean up LobbyViewModel.kt**

Edit `app/.../ui/lobby/LobbyViewModel.kt`:
- Remove any file share related state
- Keep presence/participant observation

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "remove file sharing UI from Android, restore fingerprint join"
```

---

### Task 7: Update Firebase Database Rules

**Files:**
- Modify: `firebase/database.rules.json` — remove `fileShare` node from rules

- [ ] **Step 1: Remove fileShare node from database rules**

Edit `firebase/database.rules.json`:
Remove the entire `"fileShare"` block:
```json
"fileShare": {
  ".write": "auth != null",
  ".read": true
},
```

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "remove fileShare node from Firebase rules"
```
