package com.nityam.movsync.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.nityam.movsync.data.firebase.FirebaseSync
import com.nityam.movsync.data.p2p.FileShareSignaling
import com.nityam.movsync.data.p2p.WebRTCFileTransfer
import com.nityam.movsync.data.repository.AuthRepository
import com.nityam.movsync.data.repository.RoomRepository
import com.nityam.movsync.data.supabase.SupabaseClientProvider
import com.nityam.movsync.data.sync.DriftCorrector
import com.nityam.movsync.data.sync.FileHasher
import com.nityam.movsync.data.sync.SyncEngine
import com.nityam.movsync.data.updater.UpdateManager

class AppContainer(context: Context) {
    val supabaseClient = SupabaseClientProvider.create()
    val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    val firebaseDatabase: FirebaseDatabase = FirebaseDatabase.getInstance()

    val firebaseSync = FirebaseSync(firebaseDatabase, firebaseAuth)
    val fileShareSignaling = FileShareSignaling(firebaseDatabase)
    val webrtcFileTransfer = WebRTCFileTransfer(context.applicationContext)
    val authRepository = AuthRepository(context.applicationContext, supabaseClient, firebaseAuth)
    val fileHasher = FileHasher()
    val driftCorrector = DriftCorrector()
    val syncEngine = SyncEngine(firebaseSync, driftCorrector)
    val roomRepository = RoomRepository(supabaseClient, firebaseSync)
    val updateManager = UpdateManager(context.applicationContext)
}
