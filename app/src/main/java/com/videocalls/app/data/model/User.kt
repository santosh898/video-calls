package com.videocalls.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class User(
    @DocumentId
    val uid: String = "",
    val phoneNumber: String = "",
    val displayName: String = "",
    val fcmToken: String = "",
    val avatarUrl: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val lastSeen: Date? = null
)
