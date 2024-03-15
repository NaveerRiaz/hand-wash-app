package com.aj.videplayertest;

import android.content.res.Resources;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ConfigFileReader {

    public String readTextFile(Resources resources, int resourceId) {
        StringBuilder stringBuilder = new StringBuilder();
        InputStream inputStream = resources.openRawResource(resourceId);

        try {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            char[] buffer = new char[1024];
            int bytesRead;

            while ((bytesRead = inputStreamReader.read(buffer)) != -1) {
                stringBuilder.append(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return stringBuilder.toString();
    }
}

