package com.videocalls.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.videocalls.app.data.model.*
import com.videocalls.app.data.util.PhoneNumberHelper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

private const val TAG = "CallSignalingRepo"

class CallSignalingRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // ----------------------------------------------------
    // Call Session Management
    // ----------------------------------------------------

    suspend fun createSingleCall(
        callerId: String,
        callerName: String,
        callerPhone: String,
        receiverId: String,
        receiverPhone: String,
        callType: CallType
    ): String {
        val cleanCallerPhone = PhoneNumberHelper.cleanIndianNumber(callerPhone)
        val cleanReceiverPhone = PhoneNumberHelper.cleanIndianNumber(receiverPhone)
        val callRef = firestore.collection("calls").document()
        val session = CallSession(
            id = callRef.id,
            callerId = callerId,
            callerName = callerName,
            callerPhone = cleanCallerPhone,
            receiverId = receiverId,
            receiverPhone = cleanReceiverPhone,
            isGroupCall = false,
            callType = callType,
            status = CallStatus.RINGING,
            activeParticipants = listOf(callerId)
        )
        callRef.set(session).await()
        return callRef.id
    }

    suspend fun createGroupCall(
        callerId: String,
        callerName: String,
        callerPhone: String,
        groupId: String,
        callType: CallType
    ): String {
        val cleanPhone = PhoneNumberHelper.cleanIndianNumber(callerPhone)
        val callRef = firestore.collection("calls").document()
        val session = CallSession(
            id = callRef.id,
            callerId = callerId,
            callerName = callerName,
            callerPhone = cleanPhone,
            groupId = groupId,
            isGroupCall = true,
            callType = callType,
            status = CallStatus.RINGING,
            activeParticipants = listOf(callerId)
        )
        callRef.set(session).await()

        firestore.collection("groups").document(groupId)
            .update("activeCallId", callRef.id)
            .await()

        return callRef.id
    }

    fun observeCallSession(callId: String): Flow<CallSession?> = callbackFlow {
        val listener = firestore.collection("calls").document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing call session $callId", error)
                    return@addSnapshotListener
                }
                val session = snapshot?.toObject(CallSession::class.java)
                trySend(session)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Listens in real-time for incoming calls directed to this phone number (or UID fallback)
     */
    fun observeIncomingCalls(receiverPhone: String, receiverUid: String? = null): Flow<CallSession?> = callbackFlow {
        val cleanPhone = PhoneNumberHelper.cleanIndianNumber(receiverPhone)
        val query = if (cleanPhone.length == 10) {
            firestore.collection("calls")
                .whereEqualTo("receiverPhone", cleanPhone)
                .whereEqualTo("status", CallStatus.RINGING.name)
        } else if (!receiverUid.isNullOrBlank()) {
            firestore.collection("calls")
                .whereEqualTo("receiverId", receiverUid)
                .whereEqualTo("status", CallStatus.RINGING.name)
        } else {
            null
        }

        if (query == null) {
            trySend(null)
            awaitClose {}
            return@callbackFlow
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error observing incoming calls", error)
                return@addSnapshotListener
            }
            val call = snapshot?.documents?.firstOrNull()?.toObject(CallSession::class.java)
            trySend(call)
        }
        awaitClose { listener.remove() }
    }

    suspend fun updateCallStatus(callId: String, status: CallStatus) {
        try {
            firestore.collection("calls").document(callId)
                .update("status", status.name)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating call status $callId to $status", e)
        }
    }

    suspend fun joinCall(callId: String, uid: String) {
        firestore.collection("calls").document(callId)
            .update(
                "activeParticipants", FieldValue.arrayUnion(uid),
                "status", CallStatus.CONNECTED.name
            )
            .await()
    }

    suspend fun leaveCall(callId: String, uid: String, groupId: String? = null) {
        try {
            val callRef = firestore.collection("calls").document(callId)
            callRef.update("activeParticipants", FieldValue.arrayRemove(uid)).await()

            val snapshot = callRef.get().await()
            val session = snapshot.toObject(CallSession::class.java)
            if (session != null && session.activeParticipants.isEmpty()) {
                callRef.update("status", CallStatus.ENDED.name).await()
                if (groupId != null) {
                    firestore.collection("groups").document(groupId)
                        .update("activeCallId", null)
                        .await()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error leaving call", e)
        }
    }

    // ----------------------------------------------------
    // Robust 1-to-1 WebRTC Signaling Under 100 Lines
    // ----------------------------------------------------

    suspend fun sendOffer(callId: String, sdp: SessionDescription) {
        val signal = hashMapOf(
            "type" to "offer",
            "sdp" to sdp.description,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("calls").document(callId)
            .collection("signals")
            .document("offer")
            .set(signal, SetOptions.merge())
            .await()
    }

    fun observeOffer(callId: String): Flow<SessionDescription?> = callbackFlow {
        val listener = firestore.collection("calls").document(callId)
            .collection("signals")
            .document("offer")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val sdpStr = snapshot.getString("sdp")
                if (!sdpStr.isNullOrBlank()) {
                    trySend(SessionDescription(SessionDescription.Type.OFFER, sdpStr))
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendAnswer(callId: String, sdp: SessionDescription) {
        val signal = hashMapOf(
            "type" to "answer",
            "sdp" to sdp.description,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("calls").document(callId)
            .collection("signals")
            .document("answer")
            .set(signal, SetOptions.merge())
            .await()
    }

    fun observeAnswer(callId: String): Flow<SessionDescription?> = callbackFlow {
        val listener = firestore.collection("calls").document(callId)
            .collection("signals")
            .document("answer")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val sdpStr = snapshot.getString("sdp")
                if (!sdpStr.isNullOrBlank()) {
                    trySend(SessionDescription(SessionDescription.Type.ANSWER, sdpStr))
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendCandidate(callId: String, isCaller: Boolean, candidate: IceCandidate) {
        val collName = if (isCaller) "callerCandidates" else "receiverCandidates"
        val data = hashMapOf(
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "sdp" to candidate.sdp,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("calls").document(callId)
            .collection(collName)
            .add(data)
            .await()
    }

    fun observeRemoteCandidates(callId: String, isCaller: Boolean): Flow<IceCandidate> = callbackFlow {
        val collName = if (isCaller) "receiverCandidates" else "callerCandidates"
        val listener = firestore.collection("calls").document(callId)
            .collection(collName)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                snapshot.documentChanges.forEach { change ->
                    val data = change.document.data
                    val sdp = data["sdp"] as? String ?: ""
                    val sdpMid = data["sdpMid"] as? String ?: ""
                    val sdpMLineIndex = (data["sdpMLineIndex"] as? Long)?.toInt() ?: 0
                    if (sdp.isNotBlank()) {
                        trySend(IceCandidate(sdpMid, sdpMLineIndex, sdp))
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    // ----------------------------------------------------
    // Multi-Peer Group Call Signaling Overloads
    // ----------------------------------------------------

    suspend fun sendOffer(callId: String, senderUid: String, targetUid: String, sdp: SessionDescription) {
        val signal = hashMapOf(
            "type" to "offer",
            "sdp" to sdp.description,
            "senderUid" to senderUid,
            "targetUid" to targetUid,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("calls").document(callId)
            .collection("signals")
            .document("offer_${senderUid}_$targetUid")
            .set(signal, SetOptions.merge())
            .await()
    }

    suspend fun sendAnswer(callId: String, senderUid: String, targetUid: String, sdp: SessionDescription) {
        val signal = hashMapOf(
            "type" to "answer",
            "sdp" to sdp.description,
            "senderUid" to senderUid,
            "targetUid" to targetUid,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("calls").document(callId)
            .collection("signals")
            .document("answer_${senderUid}_$targetUid")
            .set(signal, SetOptions.merge())
            .await()
    }

    suspend fun sendIceCandidate(callId: String, senderUid: String, targetUid: String, candidate: IceCandidate) {
        val data = hashMapOf(
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "sdp" to candidate.sdp,
            "senderUid" to senderUid,
            "targetUid" to targetUid,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("calls").document(callId)
            .collection("candidates")
            .add(data)
            .await()
    }

    fun observeIncomingSignals(callId: String, currentUid: String): Flow<Map<String, Any>> = callbackFlow {
        val listener = firestore.collection("calls").document(callId)
            .collection("signals")
            .whereEqualTo("targetUid", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing incoming signals", error)
                    return@addSnapshotListener
                }
                snapshot?.documentChanges?.forEach { change ->
                    val data = change.document.data
                    trySend(data)
                }
            }
        awaitClose { listener.remove() }
    }

    fun observeIncomingCandidates(callId: String, currentUid: String): Flow<IceCandidateModel> = callbackFlow {
        val listener = firestore.collection("calls").document(callId)
            .collection("candidates")
            .whereEqualTo("targetUid", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing incoming candidates", error)
                    return@addSnapshotListener
                }
                snapshot?.documentChanges?.forEach { change ->
                    val data = change.document.data
                    val model = IceCandidateModel(
                        sdpMid = data["sdpMid"] as? String ?: "",
                        sdpMLineIndex = (data["sdpMLineIndex"] as? Long)?.toInt() ?: 0,
                        sdp = data["sdp"] as? String ?: "",
                        senderUid = data["senderUid"] as? String ?: "",
                        targetUid = data["targetUid"] as? String ?: ""
                    )
                    trySend(model)
                }
            }
        awaitClose { listener.remove() }
    }
}
