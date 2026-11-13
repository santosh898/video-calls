package com.videocalls.app.data.model

data class ContactItem(
    val contactId: String,
    val displayName: String,
    val rawPhoneNumber: String,
    val normalizedPhoneNumber: String, // E.164 format, e.g. +14155552671
    val isRegistered: Boolean = false,
    val registeredUser: User? = null
)
