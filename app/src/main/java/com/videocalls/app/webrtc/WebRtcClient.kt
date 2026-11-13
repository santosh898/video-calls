package com.videocalls.app.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.videocalls.app.VideoCallApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.*
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "WebRtcClient"

class WebRtcClient(
    private val context: Context = VideoCallApp.instance,
    val eglBase: EglBase = sharedEglBase
) {

    private val peerConnectionFactory: PeerConnectionFactory = sharedPeerConnectionFactory
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var localVideoSource: VideoSource? = null
    var localVideoTrack: VideoTrack? = null
        private set

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackFlow: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private var localAudioSource: AudioSource? = null
    var localAudioTrack: AudioTrack? = null
        private set

    // Multi-peer map for 1-1 calls or Mesh group calls (key: remoteUid)
    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()

    // Remote video tracks emitted for Compose UI rendering
    private val _remoteVideoTracks = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    val remoteVideoTracks: StateFlow<Map<String, VideoTrack>> = _remoteVideoTracks.asStateFlow()

    // Callbacks to notify signaling layer
    var onLocalIceCandidate: ((targetUid: String, candidate: IceCandidate) -> Unit)? = null
    var onPeerConnected: ((peerUid: String) -> Unit)? = null
    var onPeerDisconnected: ((peerUid: String) -> Unit)? = null

    // Audio routing
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()

    private var isClosed = false

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer()
    )

    init {
        setupAudioDevice()
    }

    private fun setupAudioDevice() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            setSpeakerphoneOn(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up audio device", e)
        }
    }

    fun startLocalMedia(isAudioOnly: Boolean = false) {
        if (isClosed) return

        // Setup local audio
        if (localAudioTrack == null) {
            val audioConstraints = MediaConstraints()
            localAudioSource = peerConnectionFactory.createAudioSource(audioConstraints)
            localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", localAudioSource)
            localAudioTrack?.setEnabled(true)
        }

        if (!isAudioOnly && localVideoTrack == null) {
            // Setup local video
            videoCapturer = createCameraCapturer()
            localVideoSource = peerConnectionFactory.createVideoSource(false)
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            videoCapturer?.initialize(surfaceTextureHelper, context, localVideoSource?.capturerObserver)
            // 720p 30fps optimal quality/bandwidth balance
            try {
                videoCapturer?.startCapture(1280, 720, 30)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting camera capture at 720p, trying 640x480", e)
                try {
                    videoCapturer?.startCapture(640, 480, 30)
                } catch (e2: Exception) {
                    Log.e(TAG, "Error starting camera capture at 480p", e2)
                }
            }

            val track = peerConnectionFactory.createVideoTrack("ARDAMSv0", localVideoSource)
            track.setEnabled(true)
            localVideoTrack = track
            _localVideoTrack.value = track

            // Attach local video track to any already created peer connections
            peerConnections.values.forEach { pc ->
                try {
                    val videoTransceiver = pc.transceivers.find { it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO }
                    if (videoTransceiver != null) {
                        videoTransceiver.sender.setTrack(track, true)
                        videoTransceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                    } else {
                        pc.addTransceiver(track, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating video track on transceiver", e)
                }
            }
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // Prefer front camera for video calls
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }

        // Fallback to back camera
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }
        return null
    }

    fun getOrCreatePeerConnection(targetUid: String): PeerConnection? {
        if (isClosed) return null
        peerConnections[targetUid]?.let { return it }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "[$targetUid] onSignalingChange: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "[$targetUid] onIceConnectionChange: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> onPeerConnected?.invoke(targetUid)
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> onPeerDisconnected?.invoke(targetUid)
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "[$targetUid] onIceGatheringChange: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "[$targetUid] onIceCandidate: ${it.sdp}")
                    onLocalIceCandidate?.invoke(targetUid, it)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "[$targetUid] onAddStream: ${stream?.id}")
            }

            override fun onRemoveStream(stream: MediaStream?) {
                Log.d(TAG, "[$targetUid] onRemoveStream: ${stream?.id}")
            }

            override fun onDataChannel(dataChannel: DataChannel?) {}

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "[$targetUid] onRenegotiationNeeded")
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                Log.d(TAG, "[$targetUid] onTrack: ${track?.kind()}")
                if (track is VideoTrack) {
                    _remoteVideoTracks.value = _remoteVideoTracks.value + (targetUid to track)
                }
            }
        }

        val pc = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
        if (pc != null) {
            // Pre-allocate audio & video transceivers with SEND_RECV direction so that incoming offers
            // match immediately to two-way media pipelines (crucial for receiver video)
            if (localAudioTrack != null) {
                pc.addTransceiver(localAudioTrack, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV))
            } else {
                pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV))
            }

            if (localVideoTrack != null) {
                pc.addTransceiver(localVideoTrack, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV))
            } else {
                pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV))
            }
            peerConnections[targetUid] = pc
        }
        return pc
    }

    fun createOffer(targetUid: String, onSdpCreated: (SessionDescription) -> Unit) {
        val pc = getOrCreatePeerConnection(targetUid) ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        pc.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { sdp ->
                    pc.setLocalDescription(object : SdpObserverAdapter() {
                        override fun onSetSuccess() {
                            onSdpCreated(sdp)
                        }
                    }, sdp)
                }
            }
        }, constraints)
    }

    fun createAnswer(targetUid: String, remoteSdp: SessionDescription, onSdpCreated: (SessionDescription) -> Unit) {
        val pc = getOrCreatePeerConnection(targetUid) ?: return
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                // Ensure local tracks are attached and direction is SEND_RECV on all transceivers
                pc.transceivers.forEach { transceiver ->
                    when (transceiver.mediaType) {
                        MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO -> {
                            localAudioTrack?.let { transceiver.sender.setTrack(it, true) }
                            transceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                        }
                        MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO -> {
                            localVideoTrack?.let { transceiver.sender.setTrack(it, true) }
                            transceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                        }
                    }
                }

                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                }
                pc.createAnswer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        desc?.let { answer ->
                            pc.setLocalDescription(object : SdpObserverAdapter() {
                                override fun onSetSuccess() {
                                    onSdpCreated(answer)
                                }
                            }, answer)
                        }
                    }
                }, constraints)
            }
        }, remoteSdp)
    }

    fun setRemoteAnswer(targetUid: String, answerSdp: SessionDescription) {
        val pc = peerConnections[targetUid] ?: return
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                Log.d(TAG, "[$targetUid] Remote Answer Set Successfully")
            }
        }, answerSdp)
    }

    fun addRemoteIceCandidate(targetUid: String, candidate: IceCandidate) {
        val pc = peerConnections[targetUid] ?: return
        pc.addIceCandidate(candidate)
    }

    fun removePeer(targetUid: String) {
        try {
            peerConnections[targetUid]?.close()
            peerConnections.remove(targetUid)
            val updated = _remoteVideoTracks.value.toMutableMap()
            updated.remove(targetUid)
            _remoteVideoTracks.value = updated
        } catch (e: Exception) {
            Log.e(TAG, "Error removing peer $targetUid", e)
        }
    }

    fun toggleMic(): Boolean {
        val newMuted = !_isMicMuted.value
        _isMicMuted.value = newMuted
        localAudioTrack?.setEnabled(!newMuted)
        return newMuted
    }

    fun toggleVideo(): Boolean {
        val newEnabled = !_isVideoEnabled.value
        _isVideoEnabled.value = newEnabled
        localVideoTrack?.setEnabled(newEnabled)
        return newEnabled
    }

    fun flipCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun toggleSpeakerphone(): Boolean {
        val newSpeaker = !_isSpeakerOn.value
        setSpeakerphoneOn(newSpeaker)
        return newSpeaker
    }

    fun setSpeakerphoneOn(on: Boolean) {
        _isSpeakerOn.value = on
        try {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = on
        } catch (e: Exception) {
            Log.e(TAG, "Error setting speakerphone", e)
        }
    }

    fun close() {
        if (isClosed) return
        isClosed = true

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting audioManager", e)
        }

        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capturer", e)
        }
        try {
            videoCapturer?.dispose()
        } catch (e: Exception) {}
        videoCapturer = null

        try {
            surfaceTextureHelper?.dispose()
        } catch (e: Exception) {}
        surfaceTextureHelper = null

        try {
            _remoteVideoTracks.value = emptyMap()
        } catch (e: Exception) {}

        for ((_, pc) in peerConnections) {
            try {
                pc.close()
                pc.dispose()
            } catch (e: Exception) {}
        }
        peerConnections.clear()

        try {
            localVideoTrack?.setEnabled(false)
            localVideoTrack?.dispose()
        } catch (e: Exception) {}
        localVideoTrack = null
        _localVideoTrack.value = null

        try {
            localVideoSource?.dispose()
        } catch (e: Exception) {}
        localVideoSource = null

        try {
            localAudioTrack?.setEnabled(false)
            localAudioTrack?.dispose()
        } catch (e: Exception) {}
        localAudioTrack = null

        try {
            localAudioSource?.dispose()
        } catch (e: Exception) {}
        localAudioSource = null
    }

    companion object {
        val sharedEglBase: EglBase by lazy { EglBase.create() }

        val sharedPeerConnectionFactory: PeerConnectionFactory by lazy {
            val context = VideoCallApp.instance
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            val encoderFactory = DefaultVideoEncoderFactory(
                sharedEglBase.eglBaseContext,
                true,
                true
            )
            val decoderFactory = DefaultVideoDecoderFactory(sharedEglBase.eglBaseContext)

            PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setOptions(PeerConnectionFactory.Options())
                .createPeerConnectionFactory()
        }
    }
}

open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {
        Log.e(TAG, "SdpObserver onCreateFailure: $error")
    }
    override fun onSetFailure(error: String?) {
        Log.e(TAG, "SdpObserver onSetFailure: $error")
    }
}
