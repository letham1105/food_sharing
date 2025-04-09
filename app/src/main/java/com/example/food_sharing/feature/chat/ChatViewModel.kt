package com.example.food_sharing.feature.chat

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.food_sharing.model.Message
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import com.android.volley.Response
import com.example.food_sharing.R
import com.google.auth.oauth2.GoogleCredentials

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {


    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val db = Firebase.database

    fun sendMessage(channelID: String, messageText: String?, image: String? = null) {
        val message = Message(
            id = db.reference.push().key ?: UUID.randomUUID().toString(),
            senderId = Firebase.auth.currentUser?.uid ?: "",
            text = messageText,
            createdAt = System.currentTimeMillis(),
            senderName = Firebase.auth.currentUser?.displayName ?: "",
            profileUrl = null,
            imageUrl = image
        )

        db.reference.child("messages").child(channelID).push().setValue(message)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    postNotificationToUsers(channelID, message.senderName, messageText ?: "")
                }
            }
    }

    // Không còn gửi ảnh nữa — Hàm này bị xoá hoặc giữ lại nếu cần logic upload sau
    fun sendImageMessage(uri: Uri, channelID: String) {
        // Không làm gì vì đã xoá Supabase
    }

    fun listenForMessages(channelID: String) {
        db.reference.child("messages").child(channelID).orderByChild("createdAt")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<Message>()
                    snapshot.children.forEach { data ->
                        data.getValue(Message::class.java)?.let { list.add(it) }
                    }
                    _messages.value = list
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatViewModel", "Message listener cancelled: ${error.message}")
                }
            })

        subscribeForNotification(channelID)
        registerUserIdToChannel(channelID)
    }

    fun getAllUserEmails(channelID: String, callback: (List<String>) -> Unit) {
        val ref = db.reference.child("channels").child(channelID).child("users")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userIds = snapshot.children.mapNotNull { it.value?.toString() }
                callback(userIds)
            }

            override fun onCancelled(error: DatabaseError) {
                callback(emptyList())
            }
        })
    }

    fun registerUserIdToChannel(channelID: String) {
        val currentUser = Firebase.auth.currentUser
        val ref = db.reference.child("channels").child(channelID).child("users")
        val userId = currentUser?.uid ?: return

        ref.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    ref.child(userId).setValue(currentUser.email)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun subscribeForNotification(channelID: String) {
        FirebaseMessaging.getInstance().subscribeToTopic("group_$channelID")
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    Log.d("ChatViewModel", "Subscribed to topic: group_$channelID")
                } else {
                    Log.e("ChatViewModel", "Failed to subscribe to topic: group_$channelID")
                }
            }
    }

    private fun postNotificationToUsers(
        channelID: String,
        senderName: String,
        messageContent: String
    ) {
        val fcmUrl = "https://fcm.googleapis.com/v1/projects/chatter-bbd0d/messages:send"

        val jsonBody = JSONObject().apply {
            put("message", JSONObject().apply {
                put("topic", "group_$channelID")
                put("notification", JSONObject().apply {
                    put("title", "New message in $channelID")
                    put("body", "$senderName: $messageContent")
                })
            })
        }

        val request = object : StringRequest(Method.POST, fcmUrl, Response.Listener {
            Log.d("ChatViewModel", "Notification sent successfully")
        }, Response.ErrorListener { error ->
            Log.e("ChatViewModel", "Failed to send notification: ${error.message}")
        }) {
            override fun getBody(): ByteArray = jsonBody.toString().toByteArray()

            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf(
                    "Authorization" to "Bearer ${getAccessToken()}",
                    "Content-Type" to "application/json"
                )
            }
        }

        Volley.newRequestQueue(context).add(request)
    }

    private fun getAccessToken(): String {
        val inputStream = context.resources.openRawResource(R.raw.chatter_key)
        val googleCreds = GoogleCredentials.fromStream(inputStream)
            .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))
        return googleCreds.refreshAccessToken().tokenValue
    }
}
