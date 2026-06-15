package com.nityam.movsync.data.p2p

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FileShareSignaling(private val database: FirebaseDatabase) {
    private fun roomRef(roomCode: String) = database.reference
        .child("movsync")
        .child("rooms")
        .child(roomCode.uppercase())

    suspend fun publishFileShare(roomCode: String, seederId: String, fileName: String, fileSize: Long, goFileCode: String) {
        roomRef(roomCode).child("fileShare").setValue(
            mapOf(
                "seederId" to seederId,
                "fileName" to fileName,
                "fileSize" to fileSize,
                "goFileCode" to goFileCode,
                "updatedAt" to ServerValue.TIMESTAMP
            )
        ).await()
    }

    fun observeFileShare(roomCode: String): Flow<FileShareInfo?> = callbackFlow {
        val ref = roomRef(roomCode).child("fileShare")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.toFileShareInfoOrNull())
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }


    private fun DataSnapshot.toFileShareInfoOrNull(): FileShareInfo? {
        if (!exists()) return null
        return FileShareInfo(
            seederId = child("seederId").getValue(String::class.java).orEmpty(),
            fileName = child("fileName").getValue(String::class.java).orEmpty(),
            fileSize = child("fileSize").getValue(Long::class.java)
                ?: child("fileSize").getValue(Int::class.java)?.toLong()
                ?: 0L,
            goFileCode = child("goFileCode").getValue(String::class.java).orEmpty()
        )

}

data class FileShareInfo(
    val seederId: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L,
    val goFileCode: String = ""
)
