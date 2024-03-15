package com.aj.videplayertest;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
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

                    DataInputStream dataInputStream = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                    byte[] byteArray = new byte[1024];
                    int read;

                    ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

                    while ((read = dataInputStream.read(byteArray)) != -1) {
                        byteBuffer.write(byteArray, 0, read);
                    }

                    if (byteBuffer.size() > 10) {
                        noSignalCount = 0;
                        this.clientConnected.onClientConnected();
                        byte[] byteArrayFinal = byteBuffer.toByteArray();

                        Bitmap bitmap = BitmapFactory.decodeByteArray(byteArrayFinal, 0, byteArrayFinal.length);

                        this.clientConnected.onFrameReceived(bitmap);
                    } else {
                        noSignalCount++;
                        if (noSignalCount >= 3) {
                            this.clientConnected.onClientDisconnected("Disconnected");
                        }
                    }

                    byteBuffer.close();
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