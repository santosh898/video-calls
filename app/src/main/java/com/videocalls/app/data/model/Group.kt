package com.videocalls.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Group(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val memberUids: List<String> = emptyList(),
    val memberPhones: List<String> = emptyList(),
    val activeCallId: String? = null,
    @ServerTimestamp
    val createdAt: Date? = null
)
