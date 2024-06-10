package com.aj.videplayertest;

import android.os.AsyncTask;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpAlert extends AsyncTask<String, Void, String> {

    @Override
    protected String doInBackground(String... strings) {
        try {

            String station_id = strings[0];
            URL url = new URL("https://api.telegram.org/bot6713797997:AAFzjRUTTYrfqjEdlCN8uaqKjxA-jHyKTlM/sendMessage");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            // Set the request method to POST
            urlConnection.setRequestMethod("POST");

            // Set headers
            urlConnection.setRequestProperty("Content-Type", "application/json");

            // Enable output and input
            urlConnection.setDoOutput(true);
            urlConnection.setDoInput(true);

            // Create the request body (replace with your JSON body)
            String requestBody = "{\"chat_id\": \"-1002074992999\", \"text\": \""+ station_id + ": Disconnected!\"}";

            // Write the request body to the connection
            DataOutputStream outputStream = new DataOutputStream(urlConnection.getOutputStream());
            outputStream.writeBytes(requestBody);
            outputStream.flush();
            outputStream.close();

            // Get the response
            InputStream inputStream;
            if (urlConnection.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST) {
                inputStream = urlConnection.getInputStream();
            } else {
                inputStream = urlConnection.getErrorStream();
            }

            // Read the response
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                response.append(line);
            }

            // Close resources
            bufferedReader.close();
            inputStream.close();
            urlConnection.disconnect();

            // Return the response as a string
            return response.toString();

        } catch (IOException e) {
            e.printStackTrace();
            return null; // Handle error here or return null
        }
    }

    // Example usage
    // To make the request, execute the AsyncTask
    // new MyHttpRequest().execute();
}

