package com.aj.videplayertest;

import android.os.AsyncTask;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Objects;

public class MessageSender extends AsyncTask<String,Void,Void> {

    Socket s;
    DataOutputStream dos;
    PrintWriter pw;

    @Override
    protected Void doInBackground(String... strings) {

        String message = strings[0];
        try {
            s = new Socket("127.0.0.1", 7777);
            pw = new PrintWriter(s.getOutputStream());
            pw.write(message);

            pw.flush();
            pw.close();

            s.close();

        } catch (IOException e) {
            // Log.i("MESSAGE_SENDER", Objects.requireNonNull(e.getMessage()));
        }


        return null;
    }
}
