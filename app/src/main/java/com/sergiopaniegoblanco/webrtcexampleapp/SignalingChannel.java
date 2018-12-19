package com.sergiopaniegoblanco.webrtcexampleapp;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

/**
 * Created by jliarte on 18/12/18.
 */

class SignalingChannel {
  private static final String LOG_TAG = SignalingChannel.class.getSimpleName();
  //    private String socketAddress = "http://10.0.2.2:1337";
  private String socketAddress = "http://192.168.1.69:1337";
  private OkHttpClient webSocket;
  private WebSocket ws;

  public SignalingChannel(PeerConnection localPeer, CustomWebSocketListener webSocketListener) {
    Request request = new Request.Builder().url(socketAddress).build();
    OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();
    webSocket = okHttpClientBuilder.build();
    ws = webSocket.newWebSocket(request, webSocketListener);
    webSocket.dispatcher().executorService().shutdown();

  }

  public void send(IceCandidate iceCandidate) {
    try {
      JSONObject json = new JSONObject();
      json.put("type", "candidate");
      json.put("label", iceCandidate.sdpMLineIndex);
      json.put("id", iceCandidate.sdpMid);
      json.put("candidate", iceCandidate.sdp);
      Log.d(LOG_TAG, "sending ICE candidate" + iceCandidate);
      ws.send(json.toString());
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }

  public void send(SessionDescription sessionDescription) {
    try {
      JSONObject json = new JSONObject();
      json.put("type", sessionDescription.type);
      json.put("sdp", sessionDescription.description);
      Log.d(LOG_TAG, "sending SDP " + sessionDescription.type + " " + sessionDescription);
      ws.send(json.toString());
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }

  public void close() {
    ws.close(1000, "end of call");
  }
}
