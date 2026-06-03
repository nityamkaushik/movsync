# MovieSync 🎬

MovieSync is a "watch together" Android application that allows friends to watch the same locally-stored movie file in perfect synchronization from anywhere in the world. 

**No streaming. No file transfer.** Just flawless metadata synchronization.

![Android API](https://img.shields.io/badge/Android-API%2026%2B-brightgreen.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)
![Jetpack Compose](https://img.shields.io/badge/Compose-Material%203-purple.svg)

---

## 🎯 How It Works

1. **Host** creates a room and selects a local movie file on their device.
2. The app generates a **fast fingerprint** (SHA-256 of sampled chunks) of the movie file.
3. A 6-character room code is generated.
4. **Participants** enter the room code and select their local copy of the same movie.
5. The app verifies the fingerprint to ensure everyone is watching the exact same file.
6. Once verified, the host starts the movie. Play, pause, and seek commands are instantly synchronized across all devices using Firebase Realtime Database.

## ✨ Key Features

- **True Zero-Latency Sync:** Uses Firebase Realtime Database (~20-50ms latency) to ensure playback commands happen instantly.
- **Auto-Drift Correction:** A 3-tier algorithm smoothly adjusts playback speed (1.05x or 0.95x) to eliminate network drift without jarring jumps.
- **Two-Phase File Verification:** A quick 12MB sample fingerprint ensures instant verification when joining, with an optional full SHA-256 background hash for absolute certainty.
- **Server-Side Cleanup:** Uses Firebase `onDisconnect()` handlers to automatically clean up presence and room data if a user's app crashes or loses connection.
- **Modern Cinema UI:** Built with Jetpack Compose Material 3, featuring a dark AMOLED-friendly theme, glassmorphism overlays, and smooth spring animations.
- **Late Joiner Support:** Anyone joining an active session instantly catches up to the current playback position.

---

## 🏗️ Architecture: The Hybrid Approach

MovieSync uses a hybrid backend to maximize speed while staying 100% within free tiers:

1. **Firebase Realtime Database (Spark Plan)**
   - Used purely for real-time synchronization (play/pause/seek/heartbeat) and presence.
   - Chosen for its superior ~20-50ms latency and built-in `onDisconnect()` capabilities.
2. **Supabase (Free Tier)**
   - Used for persistent data (rooms, participants, metadata) via PostgreSQL.
   - Chosen for its robust relational data modeling, Row Level Security (RLS), and Edge Functions.
3. **Anonymous Auth**
   - Users are authenticated anonymously on both Supabase and Firebase simultaneously, ensuring secure database access without user friction.

---

## 🚀 Setup Guide

To build and run MovieSync, you need to configure both Firebase and Supabase.

### 1. Firebase Setup (Spark Plan)
1. Go to [Firebase Console](https://console.firebase.google.com) and create a new project. **Ensure it is on the Spark (Free) plan.**
2. Add an Android App with the package name `com.nityam.movsync`.
3. Download the `google-services.json` file and place it exactly at:
   ```
   app/google-services.json
   ```
4. **Enable Authentication:** Go to Build > Authentication > Sign-in method. Enable **Anonymous**.
5. **Create Realtime Database:** Go to Build > Realtime Database. Create a database (choose the region closest to you) in **locked mode**.
6. **Set Rules:** Go to the Rules tab and paste the contents of `firebase/database.rules.json` found in this repository. Publish the rules.

### 2. Supabase Setup (Free Tier)
1. Go to [Supabase](https://supabase.com) and create a new project.
2. **Run Schema:** Go to the SQL Editor, paste the entire contents of `supabase/schema.sql` found in this repository, and run it. This creates the tables, indexes, and RLS policies.
3. **Enable Auth:** Go to Authentication > Providers. Enable **Anonymous Sign-Ins**.
4. **Get Credentials:** Go to Project Settings > API. Copy the **Project URL** and the **anon (public) key**.
5. Add these credentials to your `local.properties` file in the root of the Android project:
   ```properties
   SUPABASE_URL=https://your-project-id.supabase.co
   SUPABASE_KEY=eyJhbGciOiJIUzI1NiIsIn...
   ```

### 3. (Optional) Deploy Supabase Edge Function
To auto-delete stale rooms older than 24 hours:
```bash
supabase login
supabase link --project-ref your-project-id
supabase functions deploy expire-rooms --no-verify-jwt
```

### 4. Build the App
Open the project in Android Studio, sync Gradle, and run it on an Android device or emulator running API 26 or higher.

---

## 🛠️ Tech Stack

- **UI:** Jetpack Compose, Material 3, Navigation Compose
- **Media Player:** AndroidX Media3 (ExoPlayer)
- **Backend Sync:** Firebase Realtime Database KTX
- **Backend Database:** Supabase Kotlin SDK (Postgrest, GoTrue)
- **Concurrency:** Kotlin Coroutines & Flow
- **Data Persistence:** Jetpack DataStore (Preferences)
- **Architecture:** MVVM, Clean Architecture concepts, Manual DI
- **Serialization:** kotlinx.serialization

---

## 🔒 Privacy

MovieSync **does not** stream, upload, or transfer your local video files. Only the 64-character hash of the file and tiny synchronization commands (play, pause, seek position) are sent to the servers.
