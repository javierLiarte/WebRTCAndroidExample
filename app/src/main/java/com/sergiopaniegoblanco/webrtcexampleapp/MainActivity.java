package com.sergiopaniegoblanco.webrtcexampleapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import okhttp3.WebSocket;

public class MainActivity extends AppCompatActivity {

    private final int MY_PERMISSIONS_REQUEST_CAMERA = 100;
    private final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 101;
    private final int MY_PERMISSIONS_REQUEST = 102;

    private PeerConnection peerConnection, remotePeer;
    private PeerConnectionFactory peerConnectionFactory;
    private VideoRenderer remoteRenderer;
    private AudioTrack localAudioTrack;
    private VideoTrack localVideoTrack;
//    private String session = "/SessionA";
//    private String secret = "?secret=MY_SECRET";
//    private OkHttpClient webSocket2;
//    private WebSocket ws2;

    @BindView(R.id.start_call)
    Button start_call;
    @BindView(R.id.init_call)
    Button init_call;
    @BindView(R.id.end_call)
    Button end_call;
    @BindView(R.id.remote_gl_surface_view)
    SurfaceViewRenderer remoteVideoView;
    @BindView(R.id.local_gl_surface_view)
    SurfaceViewRenderer localVideoView;

    private SignalingChannel signaling;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        askForPermissions();
        ButterKnife.bind(this);
        initViews();
    }

    public void askForPermissions() {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST);
        } else if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST_RECORD_AUDIO);

        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_CAMERA);
        }
    }

    public void initViews() {
        localVideoView.setMirror(true);
        remoteVideoView.setMirror(false);
        EglBase rootEglBase = EglBase.create();
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        localVideoView.setZOrderMediaOverlay(true);
        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);
        remoteVideoView.setZOrderMediaOverlay(true);
    }

    public void start(View view) {
        start_call.setEnabled(false);
        init_call.setEnabled(true);
        initPeerConnectionFactory();

        MediaConstraints sdpConstraints = createSDPConstraints();
        createPeerConnection(sdpConstraints);
        signaling = new SignalingChannel(peerConnection, new CustomWebSocketListener() {
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                super.onMessage(webSocket, text);
                try {
                    JSONObject json = new JSONObject(new JSONObject(text).getString("utf8Data"));
                    String messageType = json.getString("type");
                    switch (messageType) {
                        case "candidate":
                            showToast("Received Candidate");
                            onCandidate(json);
                            break;
                        case "OFFER":
                            showToast("Received Offer");
                            onOffer(json);
                            break;
                        case "ANSWER":
                            showToast("Received Answer");
                            onAnswer(json);
                            break;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        // get local stream, show it in self-view and add it to be sent
        VideoCapturer videoGrabberAndroid = createVideoGrabber();
        MediaStream stream = getLocalMediaStream(videoGrabberAndroid);
        videoGrabberAndroid.startCapture(1000, 1000, 30);
        peerConnection.addStream(stream);

    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getBaseContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @NonNull
    private MediaConstraints createSDPConstraints() {
        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));
        return sdpConstraints;
    }

    @NonNull
    private MediaStream getLocalMediaStream(VideoCapturer videoGrabberAndroid) {
        MediaConstraints constraints = new MediaConstraints();

        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoGrabberAndroid);
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        AudioSource audioSource = peerConnectionFactory.createAudioSource(constraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        MediaStream stream = peerConnectionFactory.createLocalMediaStream("102");
        stream.addTrack(localAudioTrack);
        stream.addTrack(localVideoTrack);

        final VideoRenderer localRenderer = new VideoRenderer(localVideoView);
        localVideoTrack.addRenderer(localRenderer);

        return stream;
    }

    private void initPeerConnectionFactory() {
        PeerConnectionFactory.initializeAndroidGlobals(this, true);
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = new PeerConnectionFactory(options);
    }

    public void createPeerConnection(MediaConstraints sdpConstraints) {
        final List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection.IceServer iceServer = new PeerConnection.IceServer("stun:stun.l.google.com:19302");
        iceServers.add(iceServer);

        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, sdpConstraints,
                new CustomPeerConnectionObserver("localPeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                gotICECandidate(iceCandidate);
            }

//            @Override
//            public void onRenegotiationNeeded() {
//                super.onRenegotiationNeeded();
//                // TODO(jliarte): 18/12/18 implement offer negotiation here?
//            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                super.onAddTrack(rtpReceiver, mediaStreams);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                showToast("Received Remote stream");
                super.onAddStream(mediaStream);
                gotRemoteStream(mediaStream);
            }
        });
    }

    private void gotICECandidate(IceCandidate iceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        signaling.send(iceCandidate);
    }

    public void createAndSendOffer(MediaConstraints sdpConstraints) {
        peerConnection.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                peerConnection.setLocalDescription(new CustomSdpObserver("localSetLocalDesc") {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        super.onCreateSuccess(sessionDescription);
                        signaling.send(sessionDescription);
//                        drainIceCandidates(); // TODO(jliarte): 19/12/18 implement?
                    }
                }, sessionDescription);

            }
        }, sdpConstraints);
    }

    public void call(View view) {
        start_call.setEnabled(false);
        init_call.setEnabled(false);
        end_call.setEnabled(true);

        // TODO(jliarte): 18/12/18 move to call?
        createAndSendOffer(createSDPConstraints());


//        createRemotePeerConnection();
//        createRemoteSocket();
    }

//    public void createRemotePeerConnection() {
//        final List<PeerConnection.IceServer> iceServers = new ArrayList<>();
//        PeerConnection.IceServer iceServer = new PeerConnection.IceServer("stun:stun.l.google.com:19302");
//        iceServers.add(iceServer);
//
//        MediaConstraints sdpConstraints = createSDPConstraints();
//
//        remotePeer = peerConnectionFactory.createPeerConnection(iceServers, sdpConstraints, new CustomPeerConnectionObserver("remotePeerCreation") {
//
//            @Override
//            public void onIceCandidate(IceCandidate iceCandidate) {
//                super.onIceCandidate(iceCandidate);
//                try {
//                    JSONObject json = new JSONObject();
//                    json.put("type", "candidate");
//                    json.put("label", iceCandidate.sdpMLineIndex);
//                    json.put("id", iceCandidate.sdpMid);
//                    json.put("candidate", iceCandidate.sdp);
//                    ws2.send(json.toString());
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//            }
//
//            public void onAddStream(MediaStream mediaStream) {
//                super.onAddStream(mediaStream);
//                gotRemoteStream(mediaStream);
//            }
//
//            @Override
//            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
//                super.onIceGatheringChange(iceGatheringState);
//
//            }
//        });
//    }
//
//    public void createRemoteSocket() {
//        Request request = new Request.Builder().url(socketAddress).build();
//        CustomWebSocketListener listener = new CustomWebSocketListener();
//        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();
//        webSocket2 = okHttpClientBuilder.build();
//        ws2 = webSocket2.newWebSocket(request, listener);
//        webSocket2.dispatcher().executorService().shutdown();
//    }

    public void hangup(View view) {
//        ws1.send("bye");
//        ws2.send("bye");
        signaling.close();
        signaling = null;
        peerConnection.close();
//        remotePeer.close();
        peerConnection = null;
//        remotePeer = null;
        start_call.setEnabled(true);
        init_call.setEnabled(false);
        end_call.setEnabled(false);
    }

    public VideoCapturer createVideoGrabber() {
        VideoCapturer videoCapturer;
        videoCapturer = createCameraGrabber(new Camera1Enumerator(false));
        return videoCapturer;
    }

    public VideoCapturer createCameraGrabber(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private void gotRemoteStream(MediaStream stream) {
        final VideoTrack videoTrack = stream.videoTracks.getFirst();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    remoteRenderer = new VideoRenderer(remoteVideoView);
                    remoteVideoView.setVisibility(View.VISIBLE);
                    videoTrack.addRenderer(remoteRenderer);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }




    public void onCandidate(JSONObject json) throws JSONException {
        IceCandidate iceCandidate = new IceCandidate(
                json.getString("id"),
                Integer.parseInt(json.getString("label")),
                json.getString("candidate"));
        peerConnection.addIceCandidate(iceCandidate);
    }

    public void onAnswer(JSONObject json) throws JSONException {
        SessionDescription sessionDescription = new SessionDescription(
                SessionDescription.Type.ANSWER,
                json.getString("sdp"));
        peerConnection.setRemoteDescription(
                new CustomSdpObserver("localSetRemoteDesc"),
                sessionDescription);
        //        mainActivity.setRemoteDescription(sessionDescription);
    }

    public void onOffer(JSONObject json) throws JSONException {
        SessionDescription sessionDescription = new SessionDescription(
                SessionDescription.Type.OFFER,
                json.getString("sdp"));
        peerConnection.setRemoteDescription(
                new CustomSdpObserver("remoteSetRemoteDesc") {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        super.onCreateSuccess(sessionDescription);
                        createAnswer();
                    }
                },
                sessionDescription);
    }

    private void createAnswer() {
        peerConnection.createAnswer(new CustomSdpObserver("remoteCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                peerConnection.setLocalDescription(new CustomSdpObserver("remoteSetLocalDesc"),
                        sessionDescription);
                signaling.send(sessionDescription);
            }
        }, new MediaConstraints());
    }
}