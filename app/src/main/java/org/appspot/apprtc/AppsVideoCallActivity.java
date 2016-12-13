package org.appspot.apprtc;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Ack;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONObject;

import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Created by Yudi Rahmat on 11/28/2016.
 * Email yudirahmat7@gmail.com
 */
public class AppsVideoCallActivity extends Activity {
    private String TAG = "DATA1234";

    private Socket mSocket;

    private int myUserId;
    private int destId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        trustAllCertificates();
        connectSocket();
        connectListener();
    }

    private void connectSocket() {
        try {
            mSocket = IO.socket("https://ezmedicall.stagingapps.net:2013");
        } catch (URISyntaxException e) {
            showLog(e.toString());
        }
    }

    private void connectListener() {
        mSocket.on("peer.connected", onPeerConnected);
        mSocket.emit("init", returnInit(), new Ack() {
            @Override
            public void call(Object... args) {
                for (int i = 0; i < args.length; i++) {
                    showLog("tes123 init : " + args[i]);
                }

                try {
                    myUserId = (int) args[1];

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        mSocket.connect();
    }

    private Emitter.Listener onPeerConnected = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < args.length; i++) {
                        showLog("tes123 peer.connected : " + args[i]);
                    }

                    try {
                        JSONObject json = (JSONObject) args[1];
                        destId = json.optInt("id");

                        showLog("destId : " + destId);
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

    private void showLog(String text) {
        Log.i(TAG, "testing123 - " + text);
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
}
