package org.appspot.apprtc;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Ack;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.appspot.apprtc.AppRTCClient.RoomConnectionParameters;
import org.appspot.apprtc.AppRTCClient.SignalingParameters;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.ScheduledExecutorService;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Created by Yudi Rahmat on 11/14/2016.
 * Email yudirahmat7@gmail.com
 */
public class AppaTesting extends Activity implements AppRTCClient.SignalingEvents {
    private AppRTCClient appRtcClient;
    private AppRTCClient.RoomConnectionParameters roomConnectionParameters;


    private final SDPObserver sdpObserver = new SDPObserver();
    private MediaConstraints sdpMediaConstraints;
    private SessionDescription localSdp; // either offer or answer SDP

    private final PCObserver pcObserver = new PCObserver();
    private MediaConstraints pcConstraints;
    private PeerConnection peerConnection;
    private PeerConnectionFactory factory = null;
    private SignalingParameters signalingParameters;
    private ScheduledExecutorService executor;
    private Socket mSocket;

    private static final String DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement";

    private int myUserId;
    private int destId;

    private String TAG = "AppRTC";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        //peerConnectionClient = PeerConnectionClient.getInstance();

        trustAllCertificates();
        //connectPeer();
        connectSocket();
        connectListener();

        appRtcClient = new DirectRTCClient(this);
        //executor = Executors.newSingleThreadScheduledExecutor();
    }

    private void logAndToast(String msg) {
        Log.d(TAG, "tes123 " + msg);
        //Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    // are routed to UI thread.
    private void onConnectedToRoomInternal(final SignalingParameters params) {
        signalingParameters = params;
        logAndToast("Creating peer connection, delay= " + "ms");
        createPeerConnection();

        if (signalingParameters.initiator) {
            logAndToast("Creating OFFER...");
            // Create offer. Offer SDP will be sent to answering client in
            // PeerConnectionEvents.onLocalDescription event.

            peerConnection.createOffer(sdpObserver, sdpMediaConstraints);
        } else {
            if (params.offerSdp != null) {
                logAndToast("Creating ANSWER...");
                // Create answer. Answer SDP will be sent to offering client in
                // PeerConnectionEvents.onLocalDescription event.
            }
            if (params.iceCandidates != null) {
                logAndToast("Add remote ICE candidates from room.");

            }
            /*if (params.offerSdp != null) {
                peerConnectionClient.setRemoteDescription(params.offerSdp);
                logAndToast("Creating ANSWER...");
                // Create answer. Answer SDP will be sent to offering client in
                // PeerConnectionEvents.onLocalDescription event.
                peerConnectionClient.createAnswer();
            }
            if (params.iceCandidates != null) {
                // Add remote ICE candidates from room.
                for (IceCandidate iceCandidate : params.iceCandidates) {
                    peerConnectionClient.addRemoteIceCandidate(iceCandidate);
                }
            }*/
        }
    }

    private void createPeerConnection(){
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(signalingParameters.iceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        peerConnection = factory.createPeerConnection(
                rtcConfig, pcConstraints, pcObserver);

    }

    private void connectListener() {
        mSocket.on("peer.connected", onPeerConnected);
        mSocket.emit("init", returnInit(), new Ack() {
            @Override
            public void call(Object... args) {
                try {
                    myUserId = (int) args[1];

                    roomConnectionParameters = new RoomConnectionParameters(
                            "https://ezmedicall.stagingapps.net:2013", "TesqV7eJwl", false);

                    appRtcClient.connectToRoom(roomConnectionParameters);
                    //createMediaConstraintsInternal();
                    //onConnectedToRoomInternal(null);

                    for (int i = 0; i < args.length; i++) {
                        Log.i(TAG, "tes123 : " + args[i]);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        mSocket.connect();
    }

    private JSONObject returnInit() {
        JSONObject json = new JSONObject();

        try {
            json = new JSONObject();

            json.put("room", "TesqV7eJwl");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return json;
    }

    private void connectSocket() {
        try {
            mSocket = IO.socket("https://ezmedicall.stagingapps.net:2013");
        } catch (URISyntaxException e) {}
    }

    private Emitter.Listener onPeerConnected = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];

                    try {
                        JSONObject json = (JSONObject) args[1];
                        destId = json.optInt("id");

                        Log.i(TAG, "destId : " + destId);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    };

    public static void trustAllCertificates() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            X509Certificate[] myTrustedAnchors = new X509Certificate[0];
                            return myTrustedAnchors;
                        }

                        @Override
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String arg0, SSLSession arg1) {
                    return true;
                }
            });
        } catch (Exception e) {
        }
    }
/*

    // Activity interfaces
    @Override
    public void onPause() {
        super.onPause();
        activityRunning = false;
        if (peerConnectionClient != null) {
            peerConnectionClient.stopVideoSource();
        }
        //cpuMonitor.pause();
    }

    @Override
    public void onResume() {
        super.onResume();
        activityRunning = true;
        if (peerConnectionClient != null) {
            peerConnectionClient.startVideoSource();
        }
        //cpuMonitor.resume();
    }
*/

    private void createMediaConstraintsInternal() {
        pcConstraints = new MediaConstraints();
        // Enable DTLS for normal calls and disable for loopback calls.
        pcConstraints.optional.add(
                new MediaConstraints.KeyValuePair(DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT, "true"));

        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveAudio", "true"));

        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo", "false"));
        /*if (videoCallEnabled || peerConnectionParameters.loopback) {
            sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                    "OfferToReceiveVideo", "true"));
        } else {
            sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                    "OfferToReceiveVideo", "false"));
        }*/
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mSocket.disconnect();
        mSocket.off("peer.connected", onPeerConnected);
    }

    @Override
    public void onConnectedToRoom(final AppRTCClient.SignalingParameters params) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "tes123  berhasil init signal " + params.clientId);
                //onConnectedToRoomInternal(params);
            }
        });
    }

    @Override
    public void onRemoteDescription(SessionDescription sdp) {
        showLog("onRemoteDescription");
    }

    @Override
    public void onRemoteIceCandidate(IceCandidate candidate) {
        showLog("onRemoteIceCandidate");

    }

    @Override
    public void onRemoteIceCandidatesRemoved(IceCandidate[] candidates) {
        showLog("onRemoteIceCandidatesRemoved");

    }

    @Override
    public void onChannelClose() {
        showLog("onChannelClose");

    }

    @Override
    public void onChannelError(String description) {
        showLog("onChannelError " + description);

    }

    private class SDPObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(final SessionDescription origSdp) {
            Log.i(TAG, "tes123 onCreateSuccess");

            /*if (localSdp != null) {
                reportError("Multiple SDP create.");
                return;
            }
            String sdpDescription = origSdp.description;
            if (preferIsac) {
                sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true);
            }
            if (videoCallEnabled) {
                sdpDescription = preferCodec(sdpDescription, preferredVideoCodec, false);
            }
            final SessionDescription sdp = new SessionDescription(
                    origSdp.type, sdpDescription);
            localSdp = sdp;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (peerConnection != null && !isError) {
                        Log.d(TAG, "Set local SDP from " + sdp.type);
                        peerConnection.setLocalDescription(sdpObserver, sdp);
                    }
                }
            });*/
        }

        @Override
        public void onSetSuccess() {
            showLog("onSetSuccess");
            /*executor.execute(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "tes123 onSetSuccess");

                    if (peerConnection == null || isError) {
                        return;
                    }
                    if (isInitiator) {
                        // For offering peer connection we first create offer and set
                        // local SDP, then after receiving answer set remote SDP.
                        if (peerConnection.getRemoteDescription() == null) {
                            // We've just set our local SDP so time to send it.
                            Log.d(TAG, "Local SDP set succesfully");
                            events.onLocalDescription(localSdp);
                        } else {
                            // We've just set remote description, so drain remote
                            // and send local ICE candidates.
                            Log.d(TAG, "Remote SDP set succesfully");
                            drainCandidates();
                        }
                    } else {
                        // For answering peer connection we set remote SDP and then
                        // create answer and set local SDP.
                        if (peerConnection.getLocalDescription() != null) {
                            // We've just set our local SDP so time to send it, drain
                            // remote and send local ICE candidates.
                            Log.d(TAG, "Local SDP set succesfully");
                            events.onLocalDescription(localSdp);
                            drainCandidates();
                        } else {
                            // We've just set remote SDP - do nothing for now -
                            // answer will be created soon.
                            Log.d(TAG, "Remote SDP set succesfully");
                        }
                    }
                }
            });*/
        }

        @Override
        public void onCreateFailure(final String error) {
            reportError("createSDP error: " + error);
        }

        @Override
        public void onSetFailure(final String error) {
            reportError("setSDP error: " + error);
        }
    }

    private void reportError(final String errorMessage) {
        Log.e(TAG, "Peerconnection error: " + errorMessage);
    }

    private void showLog(final String errorMessage) {
        Log.i(TAG, "tes123 : " + errorMessage);
    }

    // Implementation detail: observe ICE & stream changes and react accordingly.
    private class PCObserver implements PeerConnection.Observer {
        @Override
        public void onIceCandidate(final IceCandidate candidate){
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    showLog("onIceCandidate");
                    //events.onIceCandidate(candidate);
                }
            });
        }

        @Override
        public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    showLog("onIceCandidatesRemoved");
                    //events.onIceCandidatesRemoved(candidates);
                }
            });
        }

        @Override
        public void onSignalingChange(
                PeerConnection.SignalingState newState) {
            Log.d(TAG, "SignalingState: " + newState);
        }

        @Override
        public void onIceConnectionChange(
                final PeerConnection.IceConnectionState newState) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "IceConnectionState: " + newState);
                    /*if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                        events.onIceConnected();
                    } else if (newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                        events.onIceDisconnected();
                    } else if (newState == PeerConnection.IceConnectionState.FAILED) {
                        reportError("ICE connection failed.");
                    }*/
                }
            });
        }

        @Override
        public void onIceGatheringChange(
                PeerConnection.IceGatheringState newState) {
            Log.d(TAG, "IceGatheringState: " + newState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {
            Log.d(TAG, "IceConnectionReceiving changed to " + receiving);
        }

        @Override
        public void onAddStream(final MediaStream stream){
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    showLog("onAddStream");
                    /*if (peerConnection == null || isError) {
                        return;
                    }
                    if (stream.audioTracks.size() > 1 || stream.videoTracks.size() > 1) {
                        reportError("Weird-looking stream: " + stream);
                        return;
                    }
                    if (stream.videoTracks.size() == 1) {
                        remoteVideoTrack = stream.videoTracks.get(0);
                        remoteVideoTrack.setEnabled(renderVideo);
                        remoteVideoTrack.addRenderer(new VideoRenderer(remoteRender));
                    }*/
                }
            });
        }

        @Override
        public void onRemoveStream(final MediaStream stream){
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    showLog("onRemoveStream");
                    //remoteVideoTrack = null;
                }
            });
        }

        @Override
        public void onDataChannel(final DataChannel dc) {
            reportError("AppRTC doesn't use data channels, but got: " + dc.label()
                    + " anyway!");
        }

        @Override
        public void onRenegotiationNeeded() {
            // No need to do anything; AppRTC follows a pre-agreed-upon
            // signaling/negotiation protocol.
            showLog("onRenegotiationNeeded");
        }
    }
}
