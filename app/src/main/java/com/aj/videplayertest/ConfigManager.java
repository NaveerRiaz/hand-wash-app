package com.aj.videplayertest;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class ConfigManager {

    private Context context;

    public ConfigManager(Context context) {
        this.context = context;
    }

    public void writeConfigToFile(String data) {
        FileOutputStream fos = null;
        try {
            // Get the external files directory for the app, ensuring it's private to the app
            File file = new File(context.getExternalFilesDir(null), "configs.json");
            fos = new FileOutputStream(file);
            fos.write(data.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public String readConfig() {
        FileInputStream fis = null;
        BufferedReader reader = null;
        StringBuilder stringBuilder = new StringBuilder();

        try {
            // Get the external files directory for the app
            File file = new File(context.getExternalFilesDir(null), "configs.json");
            fis = new FileInputStream(file);
            reader = new BufferedReader(new InputStreamReader(fis));
            String line;

            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append('\n');
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return stringBuilder.toString().trim(); // Trim to remove the last newline
    }
}
