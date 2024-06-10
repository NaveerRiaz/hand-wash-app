package com.aj.videplayertest;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiClient {
    private final OkHttpClient client;
    private final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
    private final String url;

    // Constructor to initialize the OkHttpClient and URL
    public ApiClient(String url) {
        this.client = new OkHttpClient();
        this.url = url;
    }

    // Method to send POST request
    public String postSessionData(String[] sessionData, File pngFile, File csvFile) {

        // Create RequestBody instances for the PNG and CSV files
        RequestBody pngRequestBody = RequestBody.create(pngFile, MediaType.parse("image/png"));
        RequestBody csvRequestBody = RequestBody.create(csvFile, MediaType.parse("text/csv"));

        // Create a multipart request body
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("session_id", sessionData[0])
                .addFormDataPart("customer_uuid", sessionData[1])
                .addFormDataPart("user_id", sessionData[2])
                .addFormDataPart("total_time", sessionData[3])
                .addFormDataPart("num_steps_good", sessionData[4])
                .addFormDataPart("num_steps_bad", sessionData[5])
                .addFormDataPart("timeout_step1", sessionData[6])
                .addFormDataPart("timeout_step2", sessionData[7])
                .addFormDataPart("timeout_step3", sessionData[8])
                .addFormDataPart("timeout_step4", sessionData[9])
                .addFormDataPart("timeout_step5", sessionData[10])
                .addFormDataPart("timeout_step6", sessionData[11])
                .addFormDataPart("timeout_step7", sessionData[12])
                .addFormDataPart("timeout_step8", sessionData[13])
                .addFormDataPart("timeout_step9", sessionData[14])
                .addFormDataPart("image_file", pngFile.getName(), pngRequestBody)
                .addFormDataPart("audit_file", csvFile.getName(), csvRequestBody)
                .build();

        // Create the request
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            return response.body().string(); // Return response string
        } catch (Exception e) {
            e.printStackTrace();
            return null; // Return null in case of error
        }
    }

    public String getConfigData(String stationUuid) {
        try {
            Request request = new Request.Builder()
                    .url(this.url + stationUuid)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                // Use the response.
                assert response.body() != null;
                String responseData = response.body().string();
                // Note: You might want to handle the response on the UI thread if you're going to update any UI components
                // Run on UI thread: activity.runOnUiThread(() -> { /* update UI here */ });
                System.out.println(responseData);
                return responseData;
            }
        } catch (Exception e) {

            e.printStackTrace();
        }
        return null;
    }
}

