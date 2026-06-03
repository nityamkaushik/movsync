package com.nityam.movsync.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private val Context.authDataStore by preferencesDataStore(name = "auth_settings")

class AuthRepository(
    private val context: Context,
    private val supabase: SupabaseClient,
    private val firebaseAuth: FirebaseAuth
) {
    private val displayNameKey = stringPreferencesKey("display_name")

    val displayName: Flow<String> = context.authDataStore.data.map { preferences ->
        preferences[displayNameKey] ?: ""
    }

    suspend fun saveDisplayName(name: String) {
        context.authDataStore.edit { preferences ->
            preferences[displayNameKey] = name
        }
    }

    suspend fun ensureSignedIn(): String {
        if (supabase.auth.currentUserOrNull() == null) {
            supabase.auth.signInAnonymously()
        }
        if (firebaseAuth.currentUser == null) {
            firebaseAuth.signInAnonymously().await()
        }
        return supabase.auth.currentUserOrNull()?.id ?: firebaseAuth.currentUser?.uid.orEmpty()
    }
}
