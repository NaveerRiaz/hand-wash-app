package com.aj.videplayertest;

import android.content.Context;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

public class MotionDataSocket implements Runnable
{
    private OnClientConnected clientConnected;
    private long packetCounter = 0 ;
    private boolean running;
    private boolean recording = false;

    public MotionDataSocket(Context applicationContext, OnClientConnected clientConnected)
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
            // create a server socket
            while (running) {
                try {

                    // create new socket and connect to the server
                    Socket socket = new Socket("127.0.0.1", 11223);

                    // Read the JSON data from the server
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String jsonData = reader.readLine();

                    // Parse JSON and extract the float value
                    JsonParser parser = new JsonParser();
                    JsonObject jsonObject = parser.parse(jsonData).getAsJsonObject();
                    float receivedFloat = jsonObject.get("float_number").getAsFloat();

                    this.clientConnected.onClientConnected();
                    this.clientConnected.onFloatReceived(receivedFloat);
                    socket.close();
                } catch (Exception e) {
                    this.clientConnected.onClientDisconnected("Disconnected");
                    running = false;
                }
            }
        } catch (Exception e) {
            running = false;
        }
    }
}
