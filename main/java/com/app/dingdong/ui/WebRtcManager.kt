package com.app.dingdong.ui // 본인 패키지명 확인

import android.content.Context
import org.webrtc.*
import java.util.ArrayList

class WebRtcManager(
    private val context: Context,
    private val videoView: SurfaceViewRenderer,
    private val eglBase: EglBase
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var localAudioTrack: AudioTrack? = null

    init {
        // 1. WebRTC 전역 라이브러리 초기화
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        // 2. 영상/음성 데이터를 처리할 팩토리 빌더 생성
        val options = PeerConnectionFactory.Options()
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = SoftwareVideoDecoderFactory()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    // 3. 라즈베리파이와 1:1로 통신할 피어 커넥션 방 개설
    fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        val iceServers = ArrayList<PeerConnection.IceServer>()
        // 구글의 무료 공용 STUN 서버를 사용하여 서로의 네트워크 경로를 찾습니다.
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)

        startLocalAudio()

        return peerConnection
    }

    private fun startLocalAudio() {
        // 1. 오디오 설정을 위한 가이드라인 객체를 만듭니다.
        val audioConstraints = MediaConstraints()

        // 2. 팩토리를 통해 오디오 "소스"를 먼저 생성합니다. (변수 이름 꼬임 해결!)
        val mediaAudioSource = peerConnectionFactory?.createAudioSource(audioConstraints)

        // 3. 생성된 소스를 바탕으로 진짜 오디오 "트랙"을 만듭니다.
        // 🌟 [교정]: 변수명이 아니라 처음에 선언해둔 전역 변수 localAudioTrack에 값을 대입해줍니다!
        localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", mediaAudioSource)

        // 4. 오디오 트랙을 활성화합니다.
        localAudioTrack?.setEnabled(true)

        // 5. WebRTC 파이프라인(PeerConnection)에 내 오디오 트랙을 탑재합니다.
        peerConnection?.addTrack(localAudioTrack, listOf("ARDAMS"))
    }

    // 4. 전화를 걸기 위한 통화 요청서(Offer) 만들기
    fun createOffer(sdpObserver: SdpObserver) {
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")) // 영상만 수신
        }
        peerConnection?.createOffer(sdpObserver, mediaConstraints)
    }

    // 5. 라즈베리파이에서 수신한 비디오 트랙을 우리 화면(videoView)에 꽂아주기
    fun attachVideoTrack(mediaStream: MediaStream) {
        if (mediaStream.videoTracks.isNotEmpty()) {
            val videoTrack = mediaStream.videoTracks[0]
            videoTrack.setEnabled(true)
            videoTrack.addSink(videoView) // 도화지에 영상 연결
        }
    }

    fun close() {
        peerConnection?.close()
        peerConnection = null
    }
}