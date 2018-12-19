package com.sergiopaniegoblanco.webrtcexampleapp;

import android.util.Log;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Created by sergiopaniegoblanco on 02/12/2017.
 */

public class CustomWebSocketListener extends WebSocketListener {

    private static final String LOG_TAG = CustomWebSocketListener.class.getSimpleName();

    public CustomWebSocketListener() {
        super();
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        Log.d(LOG_TAG, "Open : " + response);
    }
    @Override
    public void onMessage(WebSocket webSocket, String text) {
        Log.d(LOG_TAG, "Receiving message with text: " + text);
    }
    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        Log.d(LOG_TAG, "Receiving bytes : " + bytes.hex());
    }
    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        Log.d(LOG_TAG, "Closing : " + code + " / " + reason);
    }
    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.d(LOG_TAG, "Error : " + t.getMessage());
    }

}