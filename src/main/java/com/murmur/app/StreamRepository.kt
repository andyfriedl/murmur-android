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
import com.google.firebase.auth.FirebaseAuth
import android.util.Log


class StreamRepository(private val context: Context, private val streamId: String) {
    private val db = FirebaseDatabase.getInstance().reference
    private val relayClient: MurmurRelayChatClient? = StreamSession.getRelayChannelKey(context)
        ?.let { savedRelayKey ->
            MurmurRelayChatClient(
                channelId = streamId,
                channelKey = savedRelayKey,
                transport = FirebaseMurmurRelayTransport(db)
            )
        }

    private var messagesListener: ValueEventListener? = null
    private var membersListener: ValueEventListener? = null
    private var connectedRef: com.google.firebase.database.DatabaseReference? = null
    private var connectionListener: com.google.firebase.database.ValueEventListener? = null



    val isCreator = MutableStateFlow(StreamSession.isCreator(context))
    val messages = MutableStateFlow<List<String>>(emptyList())
    val memberCount = MutableStateFlow(0)

    val streamDeleted = MutableStateFlow(false)


    init {
        observeMurmurRelayMessages()
        observeMembers()
        setPresence()
        observeConnection()
        observeStreamDeletion()
        observeNukedFlag()
        isCreator.value = StreamSession.isCreator(context)
    }

    // AFTER — set only `nuked`, which is permitted by your rules
    fun nukeStream(onFinished: (Boolean, String?) -> Unit) {
        if (BuildConfig.TEST_MODE_LOBBY && streamId == "test_lobby") {
            onFinished(false, "Test lobby can’t be deleted.")
            return
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            onFinished(false, "Not signed in")
            return
        }
        db.child("streams").child(streamId).child("nuked")
            .setValue(true)
            .addOnSuccessListener {
                db.child("streams").child(streamId).child("members").child(uid).removeValue()
                onFinished(true, null)
            }
            .addOnFailureListener { e ->
                onFinished(false, e.message)
            }
    }
    fun sendMessage(msg: String) {
        if (relayClient == null) {
            Log.e("MurmurRelay", "Relay send blocked: no relay key")
            return
        }

        relayClient.sendMessage(msg) { success ->
            Log.d("MurmurRelay", "Relay send success: $success")
        }

        db.child("streams/$streamId/lastActive")
            .setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
    }

    private fun observeMurmurRelayMessages() {
        relayClient?.observeMessages { relayMessage ->
            messages.value = messages.value + relayMessage
        } ?: Log.e("MurmurRelay", "Relay observe blocked: no relay key")
    }

    private fun observeConnection() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected")
        connectionListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) == true
                if (connected) {
                    // Re-register onDisconnect and presence on every reconnect
                    val memberRef = db.child("streams/$streamId/members/$uid")
                    memberRef.onDisconnect().removeValue()
                    memberRef.setValue(true)
                    db.child("streams/$streamId/lastActive")
                        .setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        connectedRef!!.addValueEventListener(connectionListener as com.google.firebase.database.ValueEventListener)
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
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val memberRef = db.child("streams/$streamId/members/$uid")
        val creatorRef = db.child("streams/$streamId/creator")

        if (BuildConfig.TEST_MODE_LOBBY && streamId == "test_lobby") {
            // Always joiner in the shared lobby
            isCreator.value = false
            memberRef.setValue(true)
            db.child("streams/$streamId/lastActive")
                .setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
            memberRef.onDisconnect().removeValue()
            return
        }


        // If no creator yet, claim it with *auth.uid* (required by your rules)
        creatorRef.get().addOnSuccessListener { snap ->
            if (!snap.exists()) {
                creatorRef.setValue(uid)
                isCreator.value = true
            } else if (snap.getValue(String::class.java) == uid) {
                isCreator.value = true
            }
        }

        // Mark presence under auth.uid (required by your rules)
        memberRef.setValue(true)
        db.child("streams/$streamId/lastActive")
            .setValue(com.google.firebase.database.ServerValue.TIMESTAMP)

        // Clean up on disconnect
        memberRef.onDisconnect().removeValue()
    }


    private fun observeStreamDeletion() {
        val streamRef = db.child("streams/$streamId")
        streamRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val exists = snapshot.exists()
                val deleted = snapshot.child("deleted").getValue(Boolean::class.java) == true
                if (!exists || deleted) {
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

    fun clear() {
        messagesListener?.let {
            db.child("streams/$streamId/messages").removeEventListener(it)
        }
        membersListener?.let {
            db.child("streams/$streamId/members").removeEventListener(it)
        }
        connectionListener?.let { listener ->
            connectedRef?.removeEventListener(listener)
        }
    }

    fun touchPresence() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val memberRef = db.child("streams/$streamId/members/$uid")
        // Re-register the onDisconnect handler on every foreground/reconnect.
        memberRef.onDisconnect().removeValue()
        // Assert we're in the members list right now.
        memberRef.setValue(true)
        // Optional: keep lastActive fresh
        db.child("streams/$streamId/lastActive")
            .setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
    }


    fun leaveStream(onFinished: () -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            onFinished()
            return
        }

        val memberRef = db.child("streams/$streamId/members/$uid")
        memberRef.removeValue().addOnCompleteListener {
            onFinished()
        }
    }

    companion object {
        fun createInviteId(streamId: String, onComplete: (String?) -> Unit) {
            val inviteId = generateRandomId(6)
            val db = Firebase.database.reference

            val inviteData = mapOf(
                "streamId" to streamId,
                "createdAt" to System.currentTimeMillis()
            )

            db.child("invites").child(inviteId).setValue(inviteData)
                .addOnSuccessListener {
                    onComplete(inviteId)
                }
                .addOnFailureListener {
                    onComplete(null)
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

        fun tryJoinStream(
            context: Context,
            streamId: String,
            onResult: (success: Boolean, message: String?) -> Unit
        ) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank()) {
                onResult(false, "Not signed in.")
                return
            }

            val streamRef = FirebaseDatabase.getInstance()
                .getReference("streams")
                .child(streamId)

            // Read once to check existence, pro flag, and current member count.
            streamRef.get().addOnCompleteListener { task ->
                val s = task.result
                if (!task.isSuccessful || s == null || !s.exists()) {
                    onResult(false, "Stream not found yet. Ask the host to open the stream, then try again.")
                    return@addOnCompleteListener
                }

                val deleted = s.child("deleted").getValue(Boolean::class.java) == true
                val nuked = s.child("nuked").getValue(Boolean::class.java) == true
                if (deleted || nuked) {
                    onResult(false, "This stream is closed.")
                    return@addOnCompleteListener
                }

                val pro = s.child("pro").getValue(Boolean::class.java) == true
                val membersSnap = s.child("members")
                val alreadyMember = membersSnap.hasChild(uid)
                val currentCount = membersSnap.childrenCount.toInt()

                if (!pro && !alreadyMember && currentCount >= UpgradeConfig.FREE_STREAM_MEMBER_LIMIT) {
                    onResult(false, "This free stream is full.")
                    return@addOnCompleteListener
                }

                // Write only the child we’re allowed to write per rules.
                streamRef.child("members").child(uid).setValue(true)
                    .addOnSuccessListener {
                        StreamSession.setStreamId(context, streamId)
                        onResult(true, null)
                    }
                    .addOnFailureListener { e ->
                        onResult(false, e.message)
                    }
            }
        }


    }

}
