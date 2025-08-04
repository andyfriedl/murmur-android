package com.murmur.app

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.database.ktx.getValue
import kotlinx.coroutines.flow.MutableStateFlow


class StreamRepository(private val context: Context, private val streamId: String) {
    private val db = FirebaseDatabase.getInstance().reference
    private val deviceId = StreamSession.getDeviceId(context)

    private var messagesListener: ValueEventListener? = null
    private var membersListener: ValueEventListener? = null


    val isCreator = MutableStateFlow(StreamSession.isCreator(context))
    val messages = MutableStateFlow<List<String>>(emptyList())
    val memberCount = MutableStateFlow(0)

    val streamDeleted = MutableStateFlow(false)



    init {
        observeMessages()
        observeMembers()
        setPresence()
        observeStreamDeletion()
        observeNukedFlag()
        isCreator.value = StreamSession.isCreator(context)
    }

    fun nukeStream(onFinished: () -> Unit) {
        db.child("streams/$streamId").removeValue().addOnCompleteListener {
            onFinished()
        }
    }

    fun sendMessage(msg: String) {
        val encrypted = CryptoUtils.encrypt(msg)
        db.child("streams/$streamId/messages").push().setValue(encrypted)

        // Update lastActive timestamp
        db.child("streams/$streamId/lastActive").setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
    }

    private fun observeMessages() {
        val ref = db.child("streams/$streamId/messages")
        messagesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val result = snapshot.children.mapNotNull {
                    val raw = it.getValue<String>()
                    raw?.let {
                        val decrypted = CryptoUtils.decrypt(it)
                        decrypted
                    }
                }
                messages.value = result
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(messagesListener as ValueEventListener)
    }

    private fun observeMembers() {
        val ref = db.child("streams/$streamId/members")
        membersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.childrenCount.toInt()
                memberCount.value = count
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(membersListener as ValueEventListener)
    }

    private fun setPresence() {
        val memberRef = db.child("streams/$streamId/members/$deviceId")

        val creatorRef = db.child("streams/$streamId/creator")
        creatorRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                creatorRef.setValue(deviceId)
                isCreator.value = true
            } else {
                val existing = snapshot.getValue(String::class.java)
                if (existing == deviceId) {
                    isCreator.value = true
                }
            }
        }

        memberRef.setValue(true)
        db.child("streams/$streamId/lastActive")
            .setValue(com.google.firebase.database.ServerValue.TIMESTAMP)

        memberRef.onDisconnect().removeValue()
    }

    private fun observeStreamDeletion() {
        val streamRef = db.child("streams/$streamId")
        streamRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    streamDeleted.value = true
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun observeNukedFlag() {
        val nukedRef = db.child("streams/$streamId/nuked")
        nukedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) == true) {
                    streamDeleted.value = true
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun checkIfCreator() {
        db.child("streams/$streamId/creator").get().addOnSuccessListener { snapshot ->
            val creatorId = snapshot.getValue(String::class.java)
            isCreator.value = (creatorId == deviceId)
        }
    }

    fun clear() {
        messagesListener?.let {
            db.child("streams/$streamId/messages").removeEventListener(it)
        }
        membersListener?.let {
            db.child("streams/$streamId/members").removeEventListener(it)
        }
    }

    fun leaveStream(onFinished: () -> Unit) {
        val memberRef = db.child("streams/$streamId/members/$deviceId")
        memberRef.removeValue().addOnCompleteListener {
            onFinished()
        }
    }

    companion object {
        fun createInviteId(streamId: String, onComplete: (String?) -> Unit) {
            println("📡 Creating invite for stream: $streamId")
            val inviteId = generateRandomId(6)
            val db = Firebase.database.reference

            val inviteData = mapOf(
                "streamId" to streamId,
                "createdAt" to System.currentTimeMillis()
            )

            db.child("invites").child(inviteId).setValue(inviteData)
                .addOnSuccessListener {
                    println("✅ Invite created: $inviteId")
                    onComplete(inviteId)
                }
                .addOnFailureListener {
                    println("❌ Failed to create invite: ${it.message}")
                    onComplete(null)
                }
                .addOnCompleteListener {
                    println("🔁 Invite setValue task completed")
                }
        }

        fun getStreamIdForInvite(inviteId: String, onResult: (String?) -> Unit) {
            val db = Firebase.database.reference

            db.child("invites").child(inviteId).get()
                .addOnSuccessListener { snapshot ->
                    val streamId = snapshot.child("streamId").getValue(String::class.java)
                    onResult(streamId)
                }
                .addOnFailureListener {
                    println("❌ Failed to fetch stream for invite: ${it.message}")
                    onResult(null)
                }
        }

        private fun generateRandomId(length: Int = 16): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            return (1..length)
                .map { chars.random() }
                .joinToString("")
        }

        fun deleteInvite(inviteId: String) {
            val db = Firebase.database.reference
            db.child("invites").child(inviteId).removeValue()
        }


    }




}

fun StreamSession.clearStreamId(context: Context) {
    val prefs = context.getSharedPreferences("stream_prefs", Context.MODE_PRIVATE)
    prefs.edit().remove("stream_id").apply()
}

