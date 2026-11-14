package com.videocalls.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class CallSession(
    @DocumentId
    val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerPhone: String = "",
    val receiverId: String? = null,
    val receiverPhone: String = "",
    val groupId: String? = null,
    val isGroupCall: Boolean = false,
    val callType: CallType = CallType.VIDEO,
    val status: CallStatus = CallStatus.RINGING,
    val activeParticipants: List<String> = emptyList(),
    val maxParticipantsReached: Int = 1,
    val targetPhones: List<String> = emptyList(),
    val targetUids: List<String> = emptyList(),
    @ServerTimestamp
    val createdAt: Date? = null
)

enum class CallType {
    AUDIO,
    VIDEO
}

enum class CallStatus {
    RINGING,
    CONNECTED,
    ENDED,
    REJECTED,
    MISSED
}

data class IceCandidateModel(
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val sdp: String = "",
    val senderUid: String = "",
    val targetUid: String = ""
)

data class SdpModel(
    val type: String = "", // "offer" or "answer"
    val sdp: String = "",
    val senderUid: String = "",
    val targetUid: String = ""
)
