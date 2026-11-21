package com.videocalls.app.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.videocalls.app.VideoCallApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
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

    // Pending remote ICE candidates queued before remoteDescription is set
    private val pendingIceCandidates = ConcurrentHashMap<String, MutableList<IceCandidate>>()

    // Callbacks to notify signaling layer
    var onLocalIceCandidate: ((targetUid: String, candidate: IceCandidate) -> Unit)? = null
    var onPeerConnected: ((peerUid: String) -> Unit)? = null
    var onPeerDisconnected: ((peerUid: String) -> Unit)? = null

    // Peer DataChannels for real-time interactivity & games (key: remoteUid)
    private val dataChannels = ConcurrentHashMap<String, DataChannel>()
    var onDataMessageReceived: ((senderUid: String, message: String) -> Unit)? = null

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
        PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer(),
        // Free OpenRelay public STUN/TURN servers for Symmetric NAT and cellular carrier traversal
        PeerConnection.IceServer.builder("stun:openrelay.metered.ca:80").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer()
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
        setupAudioDevice()

        // Setup local audio
        if (localAudioTrack == null) {
            val audioConstraints = MediaConstraints().apply {
                // WebRTC Software AEC3: Software acoustic echo cancellation subtracts speakerphone output
                // without clipping high-frequency or unvoiced speech (shouts, laughter, babble)
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation2", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googDAEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))

                // CRITICAL FOR KIDS: Disable ALL noise suppression and speech gating filters
                // Default WebRTC NS treats high-pitched child shouts, shrieks & laughs as background noise
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "false"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", "false"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "false"))
                mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "false"))
                mandatory.add(MediaConstraints.KeyValuePair("googExperimentalNoiseSuppression", "false"))

                // Keep soft Auto Gain Control active to boost distant/quiet toddler whispers without clipping
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl2", "true"))
            }
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
                        pc.addTransceiver(track, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV, listOf("ARDAMS")))
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
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        onPeerConnected?.invoke(targetUid)
                        localVideoTrack?.setEnabled(true)
                        localAudioTrack?.setEnabled(true)
                    }
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
                Log.d(TAG, "[$targetUid] onAddStream: ${stream?.id}, videoCount=${stream?.videoTracks?.size}")
                stream?.videoTracks?.firstOrNull()?.let { track ->
                    track.setEnabled(true)
                    _remoteVideoTracks.value = _remoteVideoTracks.value + (targetUid to track)
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {
                Log.d(TAG, "[$targetUid] onRemoveStream: ${stream?.id}")
            }

            override fun onDataChannel(dataChannel: DataChannel?) {
                dataChannel?.let { dc ->
                    Log.d(TAG, "[$targetUid] onDataChannel received: ${dc.label()}")
                    setupDataChannel(targetUid, dc)
                }
            }

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "[$targetUid] onRenegotiationNeeded")
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                Log.d(TAG, "[$targetUid] onTrack: kind=${track?.kind()}, id=${track?.id()}")
                if (track is VideoTrack) {
                    track.setEnabled(true)
                    _remoteVideoTracks.value = _remoteVideoTracks.value + (targetUid to track)
                }
            }
        }

        val pc = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
        if (pc != null) {
            val streamIds = listOf("ARDAMS")
            if (localAudioTrack != null) {
                pc.addTransceiver(localAudioTrack, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV, streamIds))
            } else {
                pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV, streamIds))
            }

            if (localVideoTrack != null) {
                pc.addTransceiver(localVideoTrack, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV, streamIds))
            } else {
                pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV, streamIds))
            }

            val dcInit = DataChannel.Init()
            val dc = pc.createDataChannel("interactive_channel", dcInit)
            if (dc != null) {
                setupDataChannel(targetUid, dc)
            }

            peerConnections[targetUid] = pc
        }
        return pc
    }

    private fun setupDataChannel(targetUid: String, dc: DataChannel) {
        dataChannels[targetUid] = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                Log.d(TAG, "[$targetUid] DataChannel state: ${dc.state()}")
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                try {
                    val data = ByteArray(buffer.data.remaining())
                    buffer.data.get(data)
                    val text = String(data, Charsets.UTF_8)
                    onDataMessageReceived?.invoke(targetUid, text)
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading DataChannel message", e)
                }
            }
        })
    }

    fun sendDataMessage(targetUid: String?, message: String) {
        try {
            val bytes = message.toByteArray(Charsets.UTF_8)
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
            if (targetUid != null) {
                dataChannels[targetUid]?.send(buffer)
            } else {
                dataChannels.values.forEach {
                    if (it.state() == DataChannel.State.OPEN) {
                        it.send(buffer)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending DataChannel message", e)
        }
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
                    val preferredOffer = SessionDescription(
                        sdp.type,
                        optimizeSdp(sdp.description)
                    )
                    pc.setLocalDescription(object : SdpObserverAdapter() {
                        override fun onSetSuccess() {
                            onSdpCreated(preferredOffer)
                        }
                    }, preferredOffer)
                }
            }
        }, constraints)
    }

    fun createAnswer(targetUid: String, remoteSdp: SessionDescription, onSdpCreated: (SessionDescription) -> Unit) {
        val pc = getOrCreatePeerConnection(targetUid) ?: return
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                drainRemoteCandidates(targetUid, pc)

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
                            val track = transceiver.receiver.track()
                            if (track is VideoTrack) {
                                track.setEnabled(true)
                                _remoteVideoTracks.value = _remoteVideoTracks.value + (targetUid to track)
                            }
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
                            val preferredAnswer = SessionDescription(
                                answer.type,
                                optimizeSdp(answer.description)
                            )
                            pc.setLocalDescription(object : SdpObserverAdapter() {
                                override fun onSetSuccess() {
                                    onSdpCreated(preferredAnswer)
                                }
                            }, preferredAnswer)
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
                drainRemoteCandidates(targetUid, pc)

                // Extract remote video track from video transceiver
                pc.transceivers.forEach { transceiver ->
                    if (transceiver.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) {
                        val track = transceiver.receiver.track()
                        if (track is VideoTrack) {
                            track.setEnabled(true)
                            _remoteVideoTracks.value = _remoteVideoTracks.value + (targetUid to track)
                        }
                    }
                }
            }
        }, answerSdp)
    }

    fun addRemoteIceCandidate(targetUid: String, candidate: IceCandidate) {
        val pc = peerConnections[targetUid]
        if (pc != null && pc.remoteDescription != null) {
            pc.addIceCandidate(candidate)
        } else {
            val list = pendingIceCandidates.getOrPut(targetUid) { mutableListOf() }
            synchronized(list) {
                list.add(candidate)
            }
        }
    }

    private fun drainRemoteCandidates(targetUid: String, pc: PeerConnection) {
        val list = pendingIceCandidates.remove(targetUid) ?: return
        synchronized(list) {
            for (cand in list) {
                pc.addIceCandidate(cand)
            }
        }
    }

    fun removePeer(targetUid: String) {
        try {
            dataChannels.remove(targetUid)?.close()
            peerConnections[targetUid]?.close()
            peerConnections.remove(targetUid)
            pendingIceCandidates.remove(targetUid)
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
        pendingIceCandidates.clear()

        for ((_, dc) in dataChannels) {
            try {
                dc.close()
                dc.dispose()
            } catch (e: Exception) {}
        }
        dataChannels.clear()

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

    private fun preferCodec(sdpDescription: String, codec: String): String {
        val lines = sdpDescription.split("\r\n").toMutableList()
        val mLineIndex = lines.indexOfFirst { it.startsWith("m=video ") }
        if (mLineIndex == -1) return sdpDescription

        // Find payload type for codec (e.g. "a=rtpmap:96 VP8/90000")
        val codecPattern = Regex("^a=rtpmap:(\\d+)\\s+$codec/90000", RegexOption.IGNORE_CASE)
        val codecPayload = lines.mapNotNull { line ->
            codecPattern.find(line)?.groupValues?.get(1)
        }.firstOrNull() ?: return sdpDescription

        val mLine = lines[mLineIndex]
        val parts = mLine.split(" ").toMutableList()
        if (parts.size < 4) return sdpDescription

        // parts[0] is "m=video", parts[1] is port, parts[2] is protocol, parts[3..] are payloads
        val header = parts.take(3)
        val payloads = parts.drop(3).toMutableList()
        if (payloads.remove(codecPayload)) {
            payloads.add(0, codecPayload)
            lines[mLineIndex] = (header + payloads).joinToString(" ")
        }
        return lines.joinToString("\r\n")
    }

    private fun optimizeSdp(sdpDescription: String): String {
        val withPreferredCodec = preferCodec(sdpDescription, "VP8")
        return tuneOpusForFullRangeAudio(withPreferredCodec)
    }

    /**
     * Tunes Opus audio parameters to ensure child shouts, babbling, laughter, and room sounds
     * are never muted or dropped:
     * - usedtx=0: Disables Discontinuous Transmission (DTX) so the encoder streams continuously
     *   without voice-gated silence.
     * - maxaveragebitrate=64000: Full wideband fidelity across high child pitches (300-3000 Hz).
     */
    private fun tuneOpusForFullRangeAudio(sdpDescription: String): String {
        val lines = sdpDescription.split("\r\n").toMutableList()
        val opusRtpmapRegex = Regex("^a=rtpmap:(\\d+)\\s+opus/48000/2", RegexOption.IGNORE_CASE)
        val opusPayload = lines.mapNotNull { line ->
            opusRtpmapRegex.find(line)?.groupValues?.get(1)
        }.firstOrNull() ?: return sdpDescription

        val fmtpPrefix = "a=fmtp:$opusPayload"
        val fmtpIndex = lines.indexOfFirst { it.startsWith(fmtpPrefix) }

        if (fmtpIndex != -1) {
            var fmtpLine = lines[fmtpIndex]
            if (fmtpLine.contains("usedtx=1")) {
                fmtpLine = fmtpLine.replace("usedtx=1", "usedtx=0")
            } else if (!fmtpLine.contains("usedtx=0")) {
                fmtpLine = "$fmtpLine;usedtx=0"
            }
            if (!fmtpLine.contains("maxaveragebitrate=")) {
                fmtpLine = "$fmtpLine;maxaveragebitrate=64000"
            }
            lines[fmtpIndex] = fmtpLine
        } else {
            val rtpmapIndex = lines.indexOfFirst { it.startsWith("a=rtpmap:$opusPayload") }
            if (rtpmapIndex != -1) {
                lines.add(rtpmapIndex + 1, "a=fmtp:$opusPayload minptime=10;useinbandfec=1;usedtx=0;maxaveragebitrate=64000")
            }
        }
        return lines.joinToString("\r\n")
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
                true,  // enableIntelVp8Encoder
                false  // enableH264HighProfile -> false for universal compatibility across chipsets
            )
            val decoderFactory = DefaultVideoDecoderFactory(sharedEglBase.eglBaseContext)

            // Audio Device Module customized for full-fidelity child vocalizations:
            // 1. Hardware Echo Canceler DISABLED (false) so WebRTC's native C++ Software AEC3 runs.
            //    Hardware AEC on many Android OEM devices leaks in speakerphone mode and disables AEC3.
            //    WebRTC Software AEC3 reliably subtracts speaker output without speech suppression.
            // 2. Hardware Noise Suppressor DISABLED (false) so Android OS DSP does not filter out kid voices/shouts
            val adm = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .createAudioDeviceModule()

            PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
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
