package com.aj.videplayertest;

import android.graphics.Bitmap;

public interface OnClientConnected {
    void onMessageReceived();
    void onClientConnected();
    void onClientDisconnected(String message);
    void onFrameReceived(Bitmap frame);
    void onClientError(String message);
    void updateFPS(String message);
    void onFloatReceived(float value);

}
