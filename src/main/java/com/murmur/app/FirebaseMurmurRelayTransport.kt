package com.murmur.app

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.murmurrelay.core.MurmurRelayResult
import com.murmurrelay.core.transport.RelayTransport

class FirebaseMurmurRelayTransport(
    private val database: DatabaseReference
) : RelayTransport {

    override fun sendMessage(
        roomId: String,
        encryptedPayload: String,
        onComplete: (MurmurRelayResult) -> Unit
    ) {
        val messageRef = database
            .child("shadowrelay")
            .child("rooms")
            .child(roomId)
            .child("messages")
            .push()

        val messageData = mapOf(
            "encryptedPayload" to encryptedPayload,
            "createdAt" to System.currentTimeMillis()
        )

        messageRef.setValue(messageData)
            .addOnSuccessListener {
                onComplete(MurmurRelayResult.Success)
            }
            .addOnFailureListener { error ->
                onComplete(MurmurRelayResult.Error(error.message ?: "Failed to send message"))
            }
    }

    override fun observeMessages(
        roomId: String,
        onMessage: (String) -> Unit
    ) {
        database
            .child("shadowrelay")
            .child("rooms")
            .child(roomId)
            .child("messages")
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val encryptedPayload = snapshot
                        .child("encryptedPayload")
                        .getValue(String::class.java)

                    if (encryptedPayload != null) {
                        onMessage(encryptedPayload)
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
                override fun onChildRemoved(snapshot: DataSnapshot) = Unit
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
                override fun onCancelled(error: DatabaseError) = Unit
            })
    }
}