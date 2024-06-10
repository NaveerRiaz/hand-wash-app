package com.aj.videplayertest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

public class ClientSocket implements Runnable
{
    private OnClientConnected clientConnected;
    private long packetCounter = 0 ;
    private boolean running;
    private boolean recording = false;

    public ClientSocket(Context applicationContext, OnClientConnected clientConnected)
    {
        Thread thread = new Thread(this);
        thread.setPriority( Thread.MAX_PRIORITY );
        thread.start();
        this.clientConnected = clientConnected;
        this.running = true;
    }

    public void stop()
    {
        this.running = false;
    }
    public void start()
    {
        if(!this.running){
            this.running = true;
            this.run();
        }
    }

    public void ToggleRecording(boolean flag){
        this.recording = flag;
    }

    public boolean isRunning()
    {
        return  running;
    }

    @Override
    public void run() {
        try {
            int noSignalCount = 0;
            // create a server socket
            while (running) {
                try {

                    // create new socket and connect to the server
                    Socket socket = new Socket("127.0.0.1", 12345);

                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String jsonData = reader.readLine();

                    try {
                        // Parse JSON and extract the float value
                        JsonParser parser = new JsonParser();
                        JsonObject jsonObject = parser.parse(jsonData).getAsJsonObject();
                        boolean motionFlag = jsonObject.get("bool").getAsBoolean();
                        String imageBase64 = jsonObject.get("image").getAsString();
                        boolean camera_ok = jsonObject.get("camera_status").getAsBoolean();
                        String rfid = jsonObject.get("rf_tag").getAsString();

                        byte[] imageBytes = Base64.decode(imageBase64, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

                        this.clientConnected.onClientConnected();
                        this.clientConnected.onFrameReceived(bitmap, motionFlag, camera_ok, rfid);

                    } catch (Exception e) {
                        this.clientConnected.onClientDisconnected("Disconnected");
                        System.out.println(e.getMessage());
                        running = false;
                    }

                    socket.close();
                } catch (Exception e) {
                    running = false;
                    this.clientConnected.onClientError(e.getMessage());
                }
            }
        } catch (Exception e) {
            running = false;
            this.clientConnected.onClientError(e.getMessage());
        }
    }
}