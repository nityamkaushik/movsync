package com.nityam.movsync.data.p2p

import com.google.firebase.database.ChildEventListener
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

    suspend fun publishFileShare(roomCode: String, seederId: String, fileName: String, fileSize: Long) {
        roomRef(roomCode).child("fileShare").setValue(
            mapOf(
                "seederId" to seederId,
                "fileName" to fileName,
                "fileSize" to fileSize,
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

    suspend fun sendOffer(roomCode: String, peerId: String, sdp: String) {
        roomRef(roomCode).child("signaling").child(peerId).child("offer").setValue(
            mapOf("type" to "offer", "sdp" to sdp, "updatedAt" to ServerValue.TIMESTAMP)
        ).await()
    }

    fun observeOffers(roomCode: String): Flow<Pair<String, String>> = callbackFlow {
        val ref = roomRef(roomCode).child("signaling")
        val seen = mutableSetOf<String>()
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { peer ->
                    val peerId = peer.key.orEmpty()
                    val sdp = peer.child("offer").child("sdp").getValue(String::class.java)
                    if (peerId.isNotBlank() && !sdp.isNullOrBlank() && seen.add(peerId)) {
                        trySend(peerId to sdp)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun sendAnswer(roomCode: String, peerId: String, sdp: String) {
        roomRef(roomCode).child("signaling").child(peerId).child("answer").setValue(
            mapOf("type" to "answer", "sdp" to sdp, "updatedAt" to ServerValue.TIMESTAMP)
        ).await()
    }

    fun observeAnswer(roomCode: String, peerId: String): Flow<String?> = callbackFlow {
        val ref = roomRef(roomCode).child("signaling").child(peerId).child("answer")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.child("sdp").getValue(String::class.java))
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun addCandidate(roomCode: String, peerId: String, role: String, candidate: IceCandidateInfo) {
        roomRef(roomCode)
            .child("signaling")
            .child(peerId)
            .child("${role}Candidates")
            .push()
            .setValue(
                mapOf(
                    "candidate" to candidate.candidate,
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex
                )
            )
            .await()
    }

    fun observeCandidates(roomCode: String, peerId: String, role: String): Flow<IceCandidateInfo> = callbackFlow {
        val ref = roomRef(roomCode).child("signaling").child(peerId).child("${role}Candidates")
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.toIceCandidateInfoOrNull()?.let { trySend(it) }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addChildEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun clearSignaling(roomCode: String, peerId: String) {
        roomRef(roomCode).child("signaling").child(peerId).removeValue().await()
    }

    private fun DataSnapshot.toFileShareInfoOrNull(): FileShareInfo? {
        if (!exists()) return null
        return FileShareInfo(
            seederId = child("seederId").getValue(String::class.java).orEmpty(),
            fileName = child("fileName").getValue(String::class.java).orEmpty(),
            fileSize = child("fileSize").getValue(Long::class.java)
                ?: child("fileSize").getValue(Int::class.java)?.toLong()
                ?: 0L
        )
    }

    private fun DataSnapshot.toIceCandidateInfoOrNull(): IceCandidateInfo? {
        val candidate = child("candidate").getValue(String::class.java) ?: return null
        return IceCandidateInfo(
            sdpMid = child("sdpMid").getValue(String::class.java),
            sdpMLineIndex = child("sdpMLineIndex").getValue(Int::class.java) ?: 0,
            candidate = candidate
        )
    }
}

data class FileShareInfo(
    val seederId: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L
)

data class IceCandidateInfo(
    val sdpMid: String? = null,
    val sdpMLineIndex: Int = 0,
    val candidate: String = ""
)
