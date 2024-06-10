package com.aj.videplayertest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ImageClassifierHelper";
    final int RECONNECT_AFTER = 1000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int NUM_CLASSES = 9; // Change this to match the number of classes in your model
    private final int BITMAP_TIMER_ID = 0;
    private final int VIDEO_TIMER_ID = 1;
    private final int MOTION_TIMER_ID = 2;
    private final int SCHEDULE_TIMER_ID = 3;
    private final int INFERENCE_TIMER_ID = 4;
    private final int CONFIG_TIMER_ID = 5;
    private final int[] TIMER_DELAYS = {0, 0, 0, 0, 0, 0};
    private final int[] TIMER_PERIODS = {1000, 100, 1000, 100, 1, 5000};
    private final int NUM_THREADS = 5;
    long timeElapsed = 0;
    ConcurrentLinkedQueue<String> imageFilenames;
    ConcurrentLinkedQueue<Bitmap> imageFrames;
    DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
    DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS");
    private String deviceName;
    private ConfigManager configHandler;
    private ApiClient sessionDataApi, configApi;
    private BroadcastReceiver batteryLevelReceiver;
    private ClientSocket socket;
    private MotionDataSocket motionSocket;
    private VideoView videoView;
    private ImageView imageView;
    private MediaPlayer beepSuccess, beepFailure, beepRfTag;
    private TextView logText, timerText, predictedClassText, correctCountText, requiredClassText, motionText, sessionTimeText, numbersTextView, listeningTextView, stationIdTextView, bleIdTextView, userGreetingTextView, rpiStatusTextView, internetStatusTextView, socketsStatusTextView, cameraStatusTextView, batteryStatusTextView;
    private View connectionStatusIndicator;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private String USER_TAG = "";
    private List<Schedule> scheduleList;
    private volatile int schedule = 0;
    private volatile int image_count = 0;
    private long timePassed = 0;
    private List<Category> labelList;
    private int correctCounter, motionCounter;
    private ImageClassifier imageClassifier;
    private ImageProcessor imageProcessor;
    private volatile double[] THRESHOLD_VALUES, REQUIRED_FRAMES, TIMEOUT;
    private boolean IN_SESSION;
    private String SESSION_BEGIN_TIME, CUSTOMER_ID, USER_ID, STATION_ID;
    private LinkedList<String>[] inferenceTimes;
    private LinkedList<Float>[] inferenceConfidence;
    private LinkedList<Integer>[] inferenceClass;
    private View labelsContainer, valueContainer;
    private boolean isConnected = false, isMotion = false;
    private int MIN_FRAMES_TO_BEGIN_PROCESS;
    private double THRESHOLD_MOTION;
    private ExecutorService executor, motionExecutor, bitmapExecutor, apiExecutor, configExecutor, audioExecutor;
    private Timer bitmapSocketControlTimer, motionSocketControlTimer, videoControlTimer, scheduleTimer, inferenceTimer, configTimer;
    private TimerTask scheduleTimerTask, motionSocketTimerTask, bitmapSocketTimerTask, videoControlTimerTask, inferenceTimerTask, configTimerTask;
    private MessageSender sender;
    private int LAST_STAGE_TIMEOUT;
    private volatile boolean SAVE_FRAMES;
    private double CONFIDENCE_THRESHOLD_PRE_STAGE;
    private String STATION_MODE;
    private BluetoothLeAdvertiser advertiser;
    private BottomSheetDialog bottomSheetDialog;
    private SpeechRecognizer speechRecognizer;
    private String spokenNumbers = "";
    private ExecutorService speechExecutorService;
    private boolean keyReceived;
    private View speechInfoContainer;
    private String BLE_ID;
    private BluetoothGattServer gattServer;

    private String rootPath;
    private volatile boolean openCase = false;
    private int openCaseRequestCount = 0;
    
    public static void saveBitmapToPng(Bitmap bitmap, String filePath) {
        try {
            // Create a new file output stream where you want to save the PNG
            FileOutputStream outputStream = new FileOutputStream(filePath);

            // Compress the bitmap to PNG format
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);

            // Flush and close the output stream
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            Log.e("BitmapToPngConverter", "Error saving PNG file: " + e.getMessage());
        }
    }

    private void updateConfigFile() {
        try {
            String configs = configApi.getConfigData(deviceName);

            if (configs != null) {
                handler.post(() -> internetStatusTextView.setTextColor(Color.GREEN));
                String configs_old = configHandler.readConfig();
                if (!configs_old.equals(configs)) {
                    configHandler.writeConfigToFile(configs);
                    Log.i("CONFIG", "Config File has been updated!");
                    setupConfig();
                    LoadSchedule();
                }
            } else {
                runOnUiThread(()->internetStatusTextView.setTextColor(Color.RED));
            }
        } catch (Exception e) {
            runOnUiThread(()->internetStatusTextView.setTextColor(Color.RED));
            Log.i("CONFIG-ERROR", Arrays.toString(e.getStackTrace()));
        }
    }

    private void startTimer(int timerId) {
        Timer[] timers = {bitmapSocketControlTimer, videoControlTimer, motionSocketControlTimer, scheduleTimer, inferenceTimer, configTimer};
        TimerTask[] timerTasks = {bitmapSocketTimerTask, videoControlTimerTask, motionSocketTimerTask, scheduleTimerTask, inferenceTimerTask, configTimerTask};

        if (timers[timerId] != null && timerTasks[timerId] != null) {
            // Schedule the timer task
            timers[timerId].schedule(timerTasks[timerId], TIMER_DELAYS[timerId], TIMER_PERIODS[timerId]);
        }
    }

    public void shutdown(ExecutorService executorToShutDown) {
        executorToShutDown.shutdown();
        try {
            if (!executorToShutDown.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                executorToShutDown.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorToShutDown.shutdownNow();
        }
    }

    private void stopTimer(int timerId) {

        Timer[] timers = {bitmapSocketControlTimer, videoControlTimer, motionSocketControlTimer, scheduleTimer, inferenceTimer, configTimer};

        if (timers[timerId] != null) {
            // Cancel the timer
            timers[timerId].cancel();
            // Initialize the timer and timer task again
            initializeTimer(timerId);
            initializeTimerTask(timerId);
        }
    }

    private void initializeTimer(int timerId) {
        switch (timerId) {
            case BITMAP_TIMER_ID:
                bitmapSocketControlTimer = new Timer("bitmap-socket-main-control");
            case VIDEO_TIMER_ID:
                videoControlTimer = new Timer("video-control-timer");
            case MOTION_TIMER_ID:
                motionSocketControlTimer = new Timer();
            case SCHEDULE_TIMER_ID:
                scheduleTimer = new Timer("schedule-timer");
            case INFERENCE_TIMER_ID:
                inferenceTimer = new Timer("inference-timer");
            case CONFIG_TIMER_ID:
                configTimer = new Timer("config-timer");
        }

    }

    private void initializeTimerTask(int timerId) {
        switch (timerId) {
            case BITMAP_TIMER_ID:
                bitmapSocketTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        if (socket != null) {
                            if (!socket.isRunning()) {
                                long currentTime = System.currentTimeMillis();
                                if (currentTime - timeElapsed >= RECONNECT_AFTER) {
                                    socket.start();
                                    timeElapsed = System.currentTimeMillis();

                                }
                            }
                        }
                    }
                };
            case VIDEO_TIMER_ID:
                videoControlTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        long timeout = System.currentTimeMillis() - timePassed;
                        updateTimerText(String.format(Locale.US, "%1.0f", Math.floor(timeout * 0.001f)));

                        if (isConnected) {
//                            updateCorrectCountText(String.valueOf(correctCounter));

                            // if a timeout occurs move to next stage
                            if (isMotion && timeout >= scheduleList.get(schedule).getTimeout()) {
                                correctCounter = 0;

                                if (schedule >= 12) {
                                    setToUnity(); // return to screen 1 after the session
                                    runOnUiThread(() -> sessionTimeText.setText(""));
                                } else if (schedule >= 3) { // a bad step
                                    videoView.start();
                                    incrementSynchronized();
                                    labelList.clear();
                                } else if (schedule == 2 && !isMotion) { // timeout without X good frames on pre-step
                                    decrementSynchronized();
                                } else if (schedule > 10 || schedule == 0) {
                                    incrementSynchronized();
                                }

                                if (schedule > 3 && schedule < 12) beepFailure.start();
                                videoView.seekTo(scheduleList.get(schedule).getStart());
                                timePassed = System.currentTimeMillis();
                            }
                        } else { // if not connected or not in motion

//                            if (!isMotion && timeout > NO_MOTION_TIMEOUT) {
//                                setToUnity();
//                            }

                            if (schedule > 0) {
                            // connection lost: return to screen 1
                            IN_SESSION = false;
                            USER_ID = "";USER_TAG="";
                            
                            runOnUiThread(()->{
                                userGreetingTextView.setText("");
                                speechInfoContainer.setVisibility(View.INVISIBLE);
                            });
                            setToUnity();
                            runOnUiThread(() -> sessionTimeText.setText(""));
                            updateCorrectCountText("");
                            updatePredictionText("");
                            updateRequiredText("");
                            }
                        }


                        // loops part of a video until timeout
                        if (videoView != null) {
                            Schedule s = scheduleList.get(schedule);
                            if (isMotion) { // when in motion loop with time reset
                                if (videoView.getCurrentPosition() >= s.getEnd()) {
//                                    timePassed = System.currentTimeMillis();
                                    videoView.seekTo(s.getStart());
                                }
                            } else { // when NOT in motion loop without time reset

                                if (videoView.getCurrentPosition() >= s.getEnd()) {
                                    videoView.seekTo(s.getStart());
                                }

                                if (timeout >= scheduleList.get(schedule).getTimeout()) {

                                    if (STATION_MODE.equals("record")) {
                                        if (schedule > 2 && schedule < 12) schedule = 12;
                                        else setToUnity();
                                    } else {
                                        setToUnity();
                                    }

                                    IN_SESSION = false;

                                    USER_ID = "";USER_TAG="";
                                    runOnUiThread(()->{
                                        userGreetingTextView.setText("");
                                    });

                                    timePassed = System.currentTimeMillis();
                                    videoView.seekTo(scheduleList.get(schedule).getStart());

                                    runOnUiThread(() -> sessionTimeText.setText(""));
                                    updateCorrectCountText("");
                                    updatePredictionText("");
                                    updateRequiredText("");
                                }
                            }
                        }
                    }
                };
            case MOTION_TIMER_ID:
                motionSocketTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        if (motionSocket != null) {
                            if (!motionSocket.isRunning()) {
                                long currentTime = System.currentTimeMillis();
                                if (currentTime - timeElapsed >= RECONNECT_AFTER) {
                                    motionSocket.start();
                                    timeElapsed = System.currentTimeMillis();
                                }
                            }
                        }
                    }
                };

            case SCHEDULE_TIMER_ID:
                scheduleTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        if (schedule > 0 && schedule <= 11) {
                            sender.doInBackground(schedule + ";" + openCase);
                            if (openCase) {
                                openCaseRequestCount++;

                                if (openCaseRequestCount >= 5) {
                                    toggleOpenCase();
                                    handler.post(()->userGreetingTextView.setText(""));
                                    openCaseRequestCount = 0;
                                }
                            }
                        } else {
                            sender.doInBackground("-1;false");
                        }
                    }
                };

            case CONFIG_TIMER_ID:
                configTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        if (schedule < 3) {
                                configExecutor.submit(() -> {
                                    try {
                                    updateConfigFile();
                                    } catch (Exception e) {
                                        internetStatusTextView.setTextColor(Color.RED);
                                        e.printStackTrace();
                                    }
                                });

                        }
                    }
                };

            case INFERENCE_TIMER_ID:
                inferenceTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        if (!labelList.isEmpty()) {

                            Category label = labelList.remove(0);
                            int level = schedule - 3;

                            if (schedule == 2) {
                                if (label != null) {
                                    if (label.getIndex() == 0 && label.getScore() >= CONFIDENCE_THRESHOLD_PRE_STAGE) {
                                        correctCounter++;
                                        updateCorrectCountText(String.valueOf(correctCounter));
                                        if (correctCounter >= MIN_FRAMES_TO_BEGIN_PROCESS) {
                                            labelList.clear();
                                            correctCounter = 0;
                                            incrementSynchronized();
                                            videoView.seekTo(scheduleList.get(schedule).getStart());
                                            timePassed = System.currentTimeMillis();
                                        }
                                    }
                                }
                            } else if (schedule >= 3 && schedule <= 11) {
                                if (!IN_SESSION) {
                                    IN_SESSION = true;
                                    resetImageCount();
                                    SESSION_BEGIN_TIME = LocalDateTime.now().format(dateTimeFormat);

                                    if (STATION_MODE.equals("record")) {
                                        createSessionDirectory();
                                    }

                                }

                                if (label != null) {
                                    if (label.getIndex() == level && label.getScore() >= THRESHOLD_VALUES[level]) {
                                        correctCounter++;
                                        updateCorrectCountText(String.valueOf(correctCounter));
                                        if (correctCounter >= REQUIRED_FRAMES[level]) {
                                            labelList.clear();
                                            correctCounter = 0;
                                            beepSuccess.start();
                                            incrementSynchronized();
                                            videoView.seekTo(scheduleList.get(schedule).getStart());
                                            timePassed = System.currentTimeMillis();
                                        }
                                    }
                                    inferenceTimes[level].add(LocalTime.now().format(timeFormat));
                                    inferenceConfidence[level].add(label.getScore());
                                    inferenceClass[level].add(label.getIndex());
                                    updateRequiredText(String.valueOf(schedule - 2));
                                }
                            }
                        }

                        if (schedule > 11 && IN_SESSION) {
                            hideImageFeed();
                            IN_SESSION = false;


                            USER_ID = (USER_ID.isEmpty())? "unregistered-user" : USER_ID;

                            runOnUiThread(()->{
                                userGreetingTextView.setText("");
                                speechInfoContainer.setVisibility(View.INVISIBLE);
                            });

//                            USER_ID = spokenNumbers.isEmpty() ? "unregistered-user" : spokenNumbers;

                            int step;
                            for (step = NUM_CLASSES - 1; step >= 0; step--) {
                                if (!inferenceTimes[step].isEmpty()) {
                                    break;
                                }
                            }

                            int length = inferenceTimes[step].size();

                            // Parse the timestamps into LocalTime objects
                            LocalTime last_frame_time = LocalTime.parse(inferenceTimes[step].get(length - 1), timeFormat);
                            LocalTime first_frame_time = LocalTime.parse(inferenceTimes[0].get(0), timeFormat);

                            // Calculate the time difference
                            Duration duration = Duration.between(first_frame_time, last_frame_time);
                            Duration finalDuration = duration;
                            runOnUiThread(() -> sessionTimeText.setText(String.format(Locale.US, "Session Time %1.0f Seconds", (float) finalDuration.toMillis() / 1000)));

                            String imageFilePath = null;
                            if (!STATION_MODE.equals("record")) {
                                createSessionDirectory();

                                imageFilePath = rootPath + "/i-wash-sessions/" + STATION_ID + "-" + SESSION_BEGIN_TIME + "/" + STATION_ID + "-" + SESSION_BEGIN_TIME + ".image_names.csv";
                                try (FileWriter writer = new FileWriter(imageFilePath)) {
                                    for (String filename : imageFilenames) {
                                        writer.write(filename + System.lineSeparator());
                                    }
                                } catch (IOException e) {
                                    Log.i("FILENAMES", "Error writing filenames: " + e.getMessage());
                                }

                                imageFilePath = rootPath + "/i-wash-sessions/" + STATION_ID + "-" + SESSION_BEGIN_TIME + "/" + STATION_ID + "-" + SESSION_BEGIN_TIME + ".images.png";

                                int imagesPerRow = 1; // For example, adjust as needed
                                int rows = (int) Math.ceil(imageFrames.size() / (double) imagesPerRow);
                                int width = 224 * imagesPerRow;
                                int height = 224 * rows;

                                Bitmap combinedImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                                Canvas canvas = new Canvas(combinedImage);
                                int x = 0;
                                int y = 0;

                                for (Bitmap bitmap : imageFrames) {
                                    canvas.drawBitmap(bitmap, x, y, null);
                                    x += 224;
                                    if (x >= combinedImage.getWidth()) {
                                        x = 0;
                                        y += 224;
                                    }
                                }

                                // Save the combinedImage to a file
                                try (FileOutputStream out = new FileOutputStream(imageFilePath)) {
                                    combinedImage.compress(Bitmap.CompressFormat.PNG, 100, out);
                                } catch (IOException e) {
                                    Log.i("IMAGE-FRAMES", "Error writing image frames: " + e.getMessage());
                                }

                            }

                            String[] sessionData = new String[0];

                            try {
                                String auditFilename = rootPath + "/i-wash-sessions/" + STATION_ID + "-" + SESSION_BEGIN_TIME + "/" + STATION_ID + "-" + SESSION_BEGIN_TIME + ".audit.csv";
                                try {
                                    // Create a BufferedWriter to write to the text file
                                    BufferedWriter writer = new BufferedWriter(new FileWriter(auditFilename));
                                    int correctCount, frameCount = 0;
                                    long timeDifference, totalTime = 0;
                                    String recordStatus = "IR";
//                                    USER_ID = "12.14.DS";
                                    String stepLabel, inferenceTime = "";

                                    // Write the headers for the columns
                                    writer.write("HW_SESSION_SEQUENCE_ROW_Num,TIME_SESSION_START," + "HWS_STATION_ID,HW_Session_Sequence_ROW_Num,HW_Record," + "HW_Step_Sequence_Num,INFERENCE_TIME,GMT,Location_ID," + "User_ID,STEP_NUMBER,STEP_LABEL,Inferencing_Counter," + "Inferred_Step,Confidence,Threshold,Elapsed_Time_" + "Since_Previous_Frame_In_milliseconds,Total_Time_Since" + "_The_First_Frame_was_inferenced_for_the_Step," + "Screen_count_value_to_advance_step,timeout," + "Total_Time_from_Step_1_to_Step_9_in_milliseconds," + "Num_of_Steps_with_early_completion," + "Num_Steps_that_timed_out\n");

                                    long[] totalTimePerStep = new long[NUM_CLASSES];
                                    int[] correctCountsPerStep = new int[NUM_CLASSES];

                                    for (int i = 0; i < NUM_CLASSES; i++) {
                                        correctCount = 0;
                                        int size = inferenceTimes[i].size();
                                        for (int j = 0; j < size; j++) {

                                            if (inferenceClass[i].get(j).equals(i) && inferenceConfidence[i].get(j) >= THRESHOLD_VALUES[i]) {
                                                correctCount++;
                                                stepLabel = "M";
                                            } else {
                                                stepLabel = "T";
                                            }

                                            if (j == 0) {
                                                timeDifference = 0;
                                                totalTime = 0;
                                            } else {
                                                // Parse the timestamps into LocalTime objects
                                                last_frame_time = LocalTime.parse(inferenceTime, timeFormat);
                                                LocalTime current_frame_time = LocalTime.parse(inferenceTimes[i].get(j), timeFormat);

                                                // Calculate the time difference
                                                duration = Duration.between(last_frame_time, current_frame_time);

                                                // Convert the time difference to milliseconds
                                                timeDifference = duration.toMillis();
                                                totalTime += timeDifference;
                                            }

                                            inferenceTime = inferenceTimes[i].get(j);

                                            writer.write(String.format(Locale.US, "%s-%s-step_%d-%d.png,%s,%s,%d,%s,%d,%s,%d,%s,%s,%d,%s,%d,%d,%1.2f,%1.2f,%d,%d,%1.0f,%1.0f,,,\n", STATION_ID, SESSION_BEGIN_TIME, i + 1, frameCount, SESSION_BEGIN_TIME, STATION_ID, frameCount, recordStatus, frameCount, inferenceTimes[i].get(j), -4, CUSTOMER_ID, USER_ID, i + 1, stepLabel, correctCount, 1 + inferenceClass[i].get(j), inferenceConfidence[i].get(j), THRESHOLD_VALUES[i], timeDifference, totalTime, REQUIRED_FRAMES[i], TIMEOUT[i]));
                                            frameCount++;

                                        }

                                        correctCountsPerStep[i] = correctCount;

                                        // write a line for step summary
                                        writer.write(String.format(Locale.US, "%s-%s-%d,%s,%s,%d,%s,%d,%s,%d,%s,%s,%d,%s,%d,%s,%s,%s,%s,%d,%s,%s,,,\n", STATION_ID, SESSION_BEGIN_TIME, frameCount, SESSION_BEGIN_TIME, STATION_ID, frameCount, "ST", frameCount, "", -4, CUSTOMER_ID, USER_ID, i + 1, (size < 15) ? "M" : "T", correctCount, "", "", "", "", totalTime, "", ""));
                                        totalTimePerStep[i] = totalTime;
                                    }

                                    int num_steps_timed_out = 0;
                                    int num_steps_not_timed_out = 0;
                                    long totalTimeForSession = finalDuration.toMillis();

                                    for (int k = 0; k < NUM_CLASSES; k++) {
                                        if (correctCountsPerStep[k] >= REQUIRED_FRAMES[k]) {
                                            num_steps_not_timed_out++;
                                        } else {
                                            num_steps_timed_out++;
                                        }
                                    }

                                    // write a line for the session summary
                                    frameCount++;
                                    writer.write(String.format(Locale.US, "%s-%s-%d,%s,%s,%d,%s,%d,,,,,,,,,,,,,,,%d,%d,%d\n", STATION_ID, SESSION_BEGIN_TIME, frameCount, SESSION_BEGIN_TIME, STATION_ID, frameCount, "SST", frameCount, totalTimeForSession, num_steps_not_timed_out, num_steps_timed_out));

                                    // Close the BufferedWriter
                                    writer.close();

                                    Log.i("DATA-OUT", "audit file for session has been written to " + auditFilename);

                                    // send session data to the server through the api
                                    sessionData = new String[]{STATION_ID + SESSION_BEGIN_TIME, CUSTOMER_ID, USER_ID, String.valueOf(totalTimeForSession), String.valueOf(num_steps_not_timed_out), String.valueOf(num_steps_timed_out), String.valueOf(totalTimePerStep[0]), String.valueOf(totalTimePerStep[1]), String.valueOf(totalTimePerStep[2]), String.valueOf(totalTimePerStep[3]), String.valueOf(totalTimePerStep[4]), String.valueOf(totalTimePerStep[5]), String.valueOf(totalTimePerStep[6]), String.valueOf(totalTimePerStep[7]), String.valueOf(totalTimePerStep[8])};

                                    // session summary file
                                    String filenameSummary = rootPath + "/i-wash-sessions/" + STATION_ID + "-" + SESSION_BEGIN_TIME + "/" + STATION_ID + "-" + SESSION_BEGIN_TIME + ".summary.txt";
                                    try {
                                        // Create a BufferedWriter to write to the text file
                                        BufferedWriter writerSummary = new BufferedWriter(new FileWriter(filenameSummary));

                                        writerSummary.write(String.format(Locale.US, "%s%s\n", STATION_ID, SESSION_BEGIN_TIME));
                                        writerSummary.write(String.format(Locale.US, "%s,%d\n", "Total_Time_of_all_Steps_in_HW_Session", totalTimeForSession));
                                        writerSummary.write(String.format(Locale.US, "%s,%d\n", "Num_Steps_Not_timed_out", num_steps_not_timed_out));
                                        writerSummary.write(String.format(Locale.US, "%s,%d\n", "SST_Num_Steps_that_timed_out", num_steps_timed_out));
                                        for (int k = 0; k < NUM_CLASSES; k++) {
                                            writerSummary.write(String.format(Locale.US, "STEP_%02d_Step_Timout_in_Milliseconds_minus__To_Comlete_Step_in_milliseconds,%d\n", k + 1, totalTimePerStep[k]));
                                        }
                                        writerSummary.close();

                                        Log.i("DATA-OUT", "summary file for session has been written to " + auditFilename);
                                    } catch (IOException e) {
                                        Log.i("DATA-OUT", "error occurred when writing summary file: " + e.getMessage());
                                    }


                                } catch (IOException e) {
                                    Log.i("ERROR", "Error writing audit file: " + e.getMessage());
                                }

                                // only send data to api/sessions when in normal mode
                                if (!STATION_MODE.equals("record")) {
                                    String finalImageFilePath = imageFilePath;
                                    String[] finalSessionData = sessionData;
                                    apiExecutor.submit(() -> {
                                        String response = sessionDataApi.postSessionData(finalSessionData, new File(finalImageFilePath), new File(auditFilename));

                                        if (response != null) {
                                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Session data sent to server successfully!", Toast.LENGTH_SHORT).show());


                                        /*
                                        // delete folder from android
                                        File folderToDelete = new File(rootPath +
                                                "/i-wash-sessions/" + STATION_ID + "-" + SESSION_BEGIN_TIME);
                                        boolean success = FileUtils.deleteFolder(folderToDelete);
                                        if (success) {
                                            Log.i("RMDIR", "Folder was deleted successfully.");
                                        } else {
                                            Log.i("RMDIR", "Failed to delete the folder.");
                                        }
                                        */


                                        } else {
                                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error Sending data to server!", Toast.LENGTH_SHORT).show());
                                        }
                                    });

                                }

                                for (int i = 0; i < NUM_CLASSES; i++) {
                                    inferenceTimes[i].clear();
                                    inferenceConfidence[i].clear();
                                    inferenceClass[i].clear();
                                }

                                configExecutor.submit(() -> updateConfigFile());

                            } catch (Exception e) {
                                Log.i("ERROR", "Error writing file...");
                            }
                            USER_ID = "";USER_TAG="";
                        }
                    }
                };
        }
    }

    private void setupConfig() {

        // create new arrays to hold new data
        double[] newThresholdValues = new double[NUM_CLASSES];
        double[] newRequiredFrames = new double[NUM_CLASSES];
        double[] newTimeout = new double[NUM_CLASSES];

        String configs = configHandler.readConfig();

        try {
            JsonParser parser = new JsonParser();
            JsonObject jsonObject = parser.parse(configs).getAsJsonObject();

            for (int i = 0; i < NUM_CLASSES; i++) {
                newThresholdValues[i] = jsonObject.get(String.format(Locale.US, "threshold_step%d", i + 1)).getAsDouble();
                newRequiredFrames[i] = jsonObject.get(String.format(Locale.US, "good_frames_required_step%d", i + 1)).getAsInt();
                newTimeout[i] = jsonObject.get(String.format(Locale.US, "timeout_step%d", i + 1)).getAsInt();
            }

            CONFIDENCE_THRESHOLD_PRE_STAGE = jsonObject.get("threshold_pre_stage").getAsDouble();
            STATION_ID = jsonObject.get("station_uuid").getAsString();
            CUSTOMER_ID = jsonObject.get("customer_id").getAsString();
            MIN_FRAMES_TO_BEGIN_PROCESS = jsonObject.get("good_frames_required_pre_stage").getAsInt();
            LAST_STAGE_TIMEOUT = jsonObject.get("timeout_end_of_session").getAsInt();
            SAVE_FRAMES = jsonObject.get("flag_save_images").getAsBoolean();
            THRESHOLD_MOTION = jsonObject.get("threshold_motion").getAsDouble();
            STATION_MODE = jsonObject.get("station_mode").getAsString();
            BLE_ID = jsonObject.get("ble_id").getAsString();;

            THRESHOLD_VALUES = newThresholdValues;
            REQUIRED_FRAMES = newRequiredFrames;
            TIMEOUT = newTimeout;

            handler.post(() -> {
                stationIdTextView.setText(STATION_ID);
                bleIdTextView.setText(BLE_ID);
            });



            if (STATION_MODE.equals("record")) {
                Log.i("STATION_MODE", "Recording...");
                showImageFeed();
            } else {
                Log.i("STATION_MODE", "Normal");
                hideImageFeed();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void initializeViews() {
        videoView = findViewById(R.id.videoView);
        imageView = findViewById(R.id.imageView);
        logText = findViewById(R.id.logText);
        timerText = findViewById(R.id.timer);
        predictedClassText = findViewById((R.id.prediction));
        requiredClassText = findViewById((R.id.required));
        correctCountText = findViewById(R.id.countCorrect);
        motionText = findViewById(R.id.motionTextView);
        sessionTimeText = findViewById(R.id.sessionTimeTextView);
//        numbersTextView = findViewById(R.id.userIdTextView);
//        listeningTextView = findViewById(R.id.listeningTextView);
        userGreetingTextView = findViewById(R.id.userGreetingTextView);
        stationIdTextView = findViewById(R.id.stationIDTextView);
        speechInfoContainer = findViewById(R.id.speechInfoContainer);
        labelsContainer = findViewById(R.id.lblContainer);
        valueContainer = findViewById(R.id.valuesContainer);
        connectionStatusIndicator = findViewById(R.id.connectionStatusIndicator);
        bleIdTextView = findViewById(R.id.bleIdTextView);

    }

    private void initializeExecutors() {
        executor = Executors.newFixedThreadPool(3);
        motionExecutor = Executors.newSingleThreadExecutor();
        apiExecutor = Executors.newSingleThreadExecutor();
        bitmapExecutor = Executors.newFixedThreadPool(NUM_THREADS);
        audioExecutor = Executors.newSingleThreadExecutor();
//        speechExecutorService = Executors.newSingleThreadExecutor();
    }

    private void prepareImageClassifier() {
        setupImageClassifier(this);
        imageProcessor = new ImageProcessor.Builder().build();
    }

    private void setupVideo() {
        MediaController mediaController = new MediaController(this);
        mediaController.setVisibility(View.GONE);
        mediaController.setAnchorView(videoView);
        Uri uri = Uri.parse(Environment.getExternalStorageDirectory().getPath() + "/media/video.mp4");

        videoView.setMediaController(mediaController);
        videoView.setVideoURI(uri);
        videoView.requestFocus();
        videoView.start();
    }

    private void setupSockets() {
        this.socket = new ClientSocket(getApplicationContext(), new OnClientConnected() {
            @Override
            public void onFloatReceived(float value) {
            }

            @Override
            public void onMessageReceived() {
                updateText("Client message");
            }

            @Override
            public void onClientConnected() {
                updateText("Connected");
                isConnected = true;
                handler.post(() -> rpiStatusTextView.setTextColor(Color.GREEN));
            }

            @Override
            public void onClientDisconnected(String message) {
                updateText(message);

                isConnected = false;
                isMotion = false;
                handler.post(()-> cameraStatusTextView.setTextColor(Color.RED));
                handler.post(() -> rpiStatusTextView.setTextColor(Color.RED));
            }

            @Override
            public void onClientError(String message) {
            }

            @Override
            public void updateFPS(String message) {
            }

            @Override
            public void onFrameReceived(Bitmap b, boolean motionFlag, boolean camera_status, String rfid) {
                if (b != null) {

                    if (!camera_status) {
                        handler.post(()->cameraStatusTextView.setTextColor(Color.RED));
                    } else {
                        handler.post(()->cameraStatusTextView.setTextColor(Color.GREEN));
                    }

                    if (!rfid.equalsIgnoreCase("none")) {

                        if (!USER_TAG.equals(rfid)) {
                            USER_TAG = rfid;
                            Log.d("RF-TAG", "received: " + rfid);
                            beepRfTag.start();
                            if (rfid.equals("907983992222")) {
                                handler.post(() -> userGreetingTextView.setText("Welcome DS.23.03"));
                                USER_ID = "Dean Stark 2303";
                            } else {
                                handler.post(() -> userGreetingTextView.setText("Invalid Tag!"));
                                USER_ID = "";
                            }
                        }
                    }

                    if (schedule < 12) {
                        if (STATION_MODE.equals("record")) {
                            showImageFeed();
                            runOnUiThread(() -> imageView.setImageBitmap(b));
                        }
                    }

                    if (schedule > 1 && schedule < 12 && isMotion) {

                        bitmapExecutor.submit(() -> {
                            Category label = classifyImage(b);
                            if (label != null) {

                                if (motionFlag) {
                                    labelList.add(label);
                                }
                            }
                        });
                    } else {
                        if (!labelList.isEmpty()) {
                            labelList.clear();
                        }
                    }
                }
            }
        });

        this.motionSocket = new MotionDataSocket(getApplicationContext(), new OnClientConnected() {
            @Override
            public void onFloatReceived(float value) {
                updateMotionText(String.format(Locale.US, "%1.0f", value));

                motionExecutor.submit(() -> {
                    try {
                        if (value >= 0) {
                            if (value >= THRESHOLD_MOTION) {
                                motionCounter++;
                                if (motionCounter > 10) {
                                    isMotion = true;
                                }
                            } else {
                                if (isMotion) {
                                    motionCounter = 0;
                                    isMotion = false;
                                    hideMicrophones();
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.i("ERROR", "Error reading float from motionValueList!");
                    }

                    if (schedule == 1 && isMotion) {

                        spokenNumbers = "";
                        handler.post(()-> {
                            userGreetingTextView.setText("");
                            speechInfoContainer.setVisibility(View.VISIBLE);
                            promptSpeechInput();
                        });
                        initializeArrays();
                        updateMotionText("");
                        correctCounter = 0;
                        setupConfig();
                        incrementSynchronized();
                        videoView.seekTo(scheduleList.get(schedule).getStart());
                        timePassed = System.currentTimeMillis();
                    }

                });
            }

            @Override
            public void onMessageReceived() {
            }

            @Override
            public void onClientConnected() {
                handler.post(() -> socketsStatusTextView.setTextColor(Color.GREEN));
            }

            @Override
            public void onClientDisconnected(String message) {
                hideMicrophones();
                handler.post(() -> socketsStatusTextView.setTextColor(Color.RED));
            }

            @Override
            public void onClientError(String message) {
            }

            @Override
            public void updateFPS(String message) {
            }

            @Override
            public void onFrameReceived(Bitmap frame, boolean motionFlag, boolean camera_status, String rfid) {
            }
        });
    }

    private void initializeArrays() {
        labelList = new ArrayList<>();
        imageFilenames = new ConcurrentLinkedQueue<>();
        imageFrames = new ConcurrentLinkedQueue<>();

        inferenceTimes = new LinkedList[NUM_CLASSES];
        inferenceConfidence = new LinkedList[NUM_CLASSES];
        inferenceClass = new LinkedList[NUM_CLASSES];

        for (int i = 0; i < NUM_CLASSES; i++) {
            inferenceTimes[i] = new LinkedList<>();
            inferenceConfidence[i] = new LinkedList<>();
            inferenceClass[i] = new LinkedList<>();
        }

    }

    private void initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        SpeechRecognitionListener listener = new SpeechRecognitionListener(this);
        speechRecognizer.setRecognitionListener(listener);
    }

    void processResults(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null) {
            Log.i("SPEECH", "You said: " + matches);
            for (String match : matches) {
                for (String result: match.split(" ")) {
                    if (result.equalsIgnoreCase("clear") ||
                        result.equalsIgnoreCase("reset")) {
                        spokenNumbers = "";
                        userGreetingTextView.setText("");
                        USER_ID = "";USER_TAG="";

                    } else if (result.equals("2303")) {
                        userGreetingTextView.setText("Welcome DS.23.03");
                        USER_ID = "Dean Stark 2303";
                    }
                }
            }
            promptSpeechInput();
        }
    }

    void promptSpeechInput() {
        if (isMotion) {
            Log.d("SpeechRecognition", "Attempting to start listening");
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            speechRecognizer.startListening(intent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Hide the action bar
        try {
            getSupportActionBar().hide();
        } catch (Exception e) {
            Log.d("APP-BAR", "No Action bar...");
        }

        rootPath = this.getFilesDir().getPath();

        File iWashSessionsDir = new File(this.getFilesDir(), "i-wash-sessions");

        if (!iWashSessionsDir.exists()) {
            boolean isCreated = iWashSessionsDir.mkdirs();
            if (isCreated) {
                Log.d("DirectoryStatus", "Directory 'i-wash-sessions' created successfully.");
            } else {
                Log.e("DirectoryStatus", "Failed to create directory 'i-wash-sessions'.");
            }
        } else {
            Log.d("DirectoryStatus", "Directory 'i-wash-sessions' already exists.");
        }


        rpiStatusTextView = findViewById(R.id.RpiIndicatorTextView);
        internetStatusTextView = findViewById(R.id.InternetIndicatorTextView);
        socketsStatusTextView = findViewById(R.id.SocketsIndicatorTextView);
        cameraStatusTextView = findViewById(R.id.CameraIndicatorTextView);
        batteryStatusTextView = findViewById(R.id.BatteryIndicatorTextView);

        bottomSheetDialog = new BottomSheetDialog(this);
        View bottomSheetView = LayoutInflater.from(this).inflate(R.layout.ble_listener, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (hasBluetoothPermissions() && hasLocationPermissions() && hasStoragePermissions() && hasRecordPermission()) {
            startApp();
        } else {
            requestPermissions();
        }
    }

    @SuppressLint("MissingPermission")
    void startApp() {

        batteryLevelReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                float batteryPct = level * 100 / (float) scale;

                if (batteryPct >= 5) {
                    handler.post(() -> batteryStatusTextView.setTextColor(Color.GREEN));
                } else {
                    handler.post(() -> batteryStatusTextView.setTextColor(Color.RED));
                }

                updateBatteryLevelUI(batteryPct); // Update your UI with the battery percentage
            }
        };

        keyReceived = false;
        initializeSpeechRecognizer();

        BluetoothAdapter myDevice = BluetoothAdapter.getDefaultAdapter();

        deviceName = myDevice.getName();
        Log.i("DEVICE_NAME", deviceName);

        // Create an instance of the ApiClient class
        sessionDataApi = new ApiClient("http://10.8.0.2:8000/api/sessions/");
        configApi = new ApiClient("http://10.8.0.2:8000/api/configurations/get?station_uuid=");
//        sessionDataApi = new ApiClient("http://192.168.1.249:8000/api/sessions/");
//        configApi = new ApiClient("http://192.168.1.249:8000/api/configurations/get?station_uuid=");

        configExecutor = Executors.newSingleThreadExecutor();
        configHandler = new ConfigManager(this);

        configExecutor.execute(() -> {
            String configs = configApi.getConfigData(deviceName);

            if (configs != null) {
                Log.i("CONFIG", "Received configurations from api.");
                configHandler.writeConfigToFile(configs);
            }

            handler.post(() -> {

                initializeViews();
                setupConfig();
                LoadSchedule();
                initializeExecutors();
                initializeArrays();
                prepareImageClassifier();
                setupSockets();
                sender = new MessageSender();

                beepSuccess = MediaPlayer.create(this, R.raw.tone);
                beepFailure = MediaPlayer.create(this, R.raw.tone_fail);
                beepRfTag = MediaPlayer.create(this, R.raw.rf_tag_beep);

                setToZero();
                IN_SESSION = false;
                USER_ID = "";USER_TAG="";

                setupVideo();

                initializeTimer(MOTION_TIMER_ID);
                initializeTimer(BITMAP_TIMER_ID);
                initializeTimer(SCHEDULE_TIMER_ID);
                initializeTimer(VIDEO_TIMER_ID);
                initializeTimer(CONFIG_TIMER_ID);

                initializeTimerTask(MOTION_TIMER_ID);
                initializeTimerTask(BITMAP_TIMER_ID);
                initializeTimerTask(SCHEDULE_TIMER_ID);
                initializeTimerTask(VIDEO_TIMER_ID);
                initializeTimerTask(CONFIG_TIMER_ID);

                correctCounter = 0;
                motionCounter = 0;

                updateCorrectCountText(String.valueOf(correctCounter));

                timePassed = System.currentTimeMillis();

                startTimer(SCHEDULE_TIMER_ID);
                startTimer(VIDEO_TIMER_ID);
                startTimer(BITMAP_TIMER_ID);
                startTimer(MOTION_TIMER_ID);
                startTimer(INFERENCE_TIMER_ID);
                startTimer(CONFIG_TIMER_ID);

                ExecutorService bleService = Executors.newSingleThreadExecutor();
                bleService.submit(() -> {
                    setupGattServer(bluetoothManager);
                    startBroadcast(bluetoothAdapter);
                });
            });

        });


    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void setupImageClassifier(Context context) {
        ImageClassifier.ImageClassifierOptions.Builder optionsBuilder = ImageClassifier.ImageClassifierOptions.builder().setScoreThreshold(0.01f).setMaxResults(NUM_CLASSES);

        try {
            String MODEL_NAME = "model.tflite";
            imageClassifier = ImageClassifier.createFromFileAndOptions(context, MODEL_NAME, optionsBuilder.build());
        } catch (IOException e) {
            Toast.makeText(this, "Image classifier failed to " + "initialize. See error logs for details", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "TFLite failed to load model with error: " + e.getMessage());
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        killAllProcesses();
    }

    @Override
    protected void onStop() {
        super.onStop();
        killAllProcesses();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        killAllProcesses();
    }

    private void killAllProcesses() {

        try {
            beepSuccess.reset();
            beepSuccess.release();

            beepRfTag.reset();
            beepRfTag.release();
        } catch (Exception e) {
            Log.i("MEDIAPLAYER", "Error while releasing resources for media player: " + e.getMessage());
        }

        stopTimer(MOTION_TIMER_ID);
        stopTimer(BITMAP_TIMER_ID);
        stopTimer(VIDEO_TIMER_ID);
        stopTimer(SCHEDULE_TIMER_ID);
        stopTimer(INFERENCE_TIMER_ID);
        stopTimer(CONFIG_TIMER_ID);

        socket.stop();
        motionSocket.stop();

        shutdown(executor);
        shutdown(bitmapExecutor);
        shutdown(motionExecutor);
        shutdown(apiExecutor);
        shutdown(configExecutor);
        shutdown(audioExecutor);

        labelList.clear();

    }

    private Category classifyImage(Bitmap frame) {
        if (frame != null) {

//            if (SAVE_FRAMES == 1) {
            if (schedule >= 3 && schedule <= 11) {
                String filePath = rootPath + String.format(Locale.US, "/i-wash-sessions/%s-%s/%s-%s-step_%d-%d.png", STATION_ID, SESSION_BEGIN_TIME, STATION_ID, SESSION_BEGIN_TIME, schedule - 2, image_count);
                incrementImageCount();
                executor.submit(() -> {
                    Bitmap imageCopy = frame.copy(frame.getConfig(), true);

                    if (STATION_MODE.equals("record")) {
                        saveBitmapToPng(imageCopy, filePath);
                    } else {
                        imageFilenames.add(filePath);
                        imageFrames.add(imageCopy);
                    }
                });
            }
//            }

            TensorImage tensorImage = imageProcessor.process(TensorImage.fromBitmap(frame));

            List<Classifications> result = imageClassifier.classify(tensorImage);

            Classifications c = result.get(0);
            if (c.getHeadIndex() <= c.getCategories().size() - 1) {
                Category label = c.getCategories().get(c.getHeadIndex());
                if (schedule >= 2 && schedule <= 11) {
                    updatePredictionText(String.format(Locale.US, "Class %d (%.2f)", label.getIndex() + 1, label.getScore()));
                }
                return label;
            } else {
                updatePredictionText("unknown");
                return null;
            }

        }
        return null;
    }

    private synchronized void incrementImageCount() {
        image_count++;
    }

    private synchronized void resetImageCount() {
        image_count = 0;
    }

    private void updateText(String message) {

        if (STATION_MODE.equals("record")) runOnUiThread(() -> logText.setText(message));

    }

    private void updateCorrectCountText(String message) {
        if (STATION_MODE.equals("record")) runOnUiThread(() -> correctCountText.setText(message));
    }

    private void updatePredictionText(String message) {
        if (STATION_MODE.equals("record")) runOnUiThread(() -> predictedClassText.setText(message));
    }

    private void updateRequiredText(String message) {
        if (STATION_MODE.equals("record")) runOnUiThread(() -> requiredClassText.setText(message));
    }

    private void updateMotionText(String message) {
        if (STATION_MODE.equals("record")) runOnUiThread(() -> motionText.setText(message));
    }

    private void updateTimerText(String message) {
        if (STATION_MODE.equals("record")) runOnUiThread(() -> timerText.setText(message));
    }

    private void showImageFeed() {
        if (STATION_MODE.equals("record")) {
            if (imageView.getVisibility() != View.VISIBLE) {
                runOnUiThread(() -> {
                    imageView.setVisibility(View.VISIBLE);
                    connectionStatusIndicator.setVisibility(View.VISIBLE);
                });
            }
            runOnUiThread(this::showDebugData);
        }
    }

    private void hideImageFeed() {
        if (imageView.getVisibility() == View.VISIBLE) {
            runOnUiThread(() -> imageView.setVisibility(View.INVISIBLE));
            connectionStatusIndicator.setVisibility(View.INVISIBLE);
        }
        runOnUiThread(this::hideDebugData);
    }

    private void showDebugData() {

        timerText.setVisibility(View.VISIBLE);
        motionText.setVisibility(View.VISIBLE);

        labelsContainer.setVisibility(View.VISIBLE);
        valueContainer.setVisibility(View.VISIBLE);

        logText.setVisibility(View.VISIBLE);

    }

    private void hideDebugData() {

        timerText.setVisibility(View.INVISIBLE);
        motionText.setVisibility(View.INVISIBLE);

        labelsContainer.setVisibility(View.INVISIBLE);
        valueContainer.setVisibility(View.INVISIBLE);

        logText.setVisibility(View.INVISIBLE);
    }

    private void LoadSchedule() {
        int limit = 100;
        scheduleList = new ArrayList<>();
        scheduleList.add(new Schedule(0, 2000, 4000, 2000));
        scheduleList.add(new Schedule(4001, 11000, 5000, 7000));
        scheduleList.add(new Schedule(12001, 28000, 5000, 16000));
        //class 0
        scheduleList.add(new Schedule(28001, 32000 - limit, (int) TIMEOUT[0], 4000)); // step 1
        scheduleList.add(new Schedule(32001, 36000 - limit, (int) TIMEOUT[1], 4000)); // step 2
        scheduleList.add(new Schedule(36001, 40000 - limit, (int) TIMEOUT[2], 4000)); // step 3
        scheduleList.add(new Schedule(40001, 44000 - limit, (int) TIMEOUT[3], 4000)); // step 4
        scheduleList.add(new Schedule(44001, 48000 - limit, (int) TIMEOUT[4], 4000)); // step 5
        scheduleList.add(new Schedule(48001, 52000 - limit, (int) TIMEOUT[5], 4000)); // step 6
        scheduleList.add(new Schedule(52001, 56000 - limit, (int) TIMEOUT[6], 4000)); // step 7
        scheduleList.add(new Schedule(56001, 60000 - limit, (int) TIMEOUT[7], 4000)); // step 8
        scheduleList.add(new Schedule(60001, 62500 - limit, (int) TIMEOUT[8], 2500)); // step 9
        ///class 8 ends
        scheduleList.add(new Schedule(64001, 68000 - limit, LAST_STAGE_TIMEOUT, 4000));
        scheduleList.add(new Schedule(69001, 73000 - limit, 5000, 4000));
        scheduleList.add(new Schedule(74001, 78000 - limit, 5000, 4000));
    }

    private void createSessionDirectory() {
        File directory = new File(rootPath + "/i-wash-sessions/", STATION_ID + "-" + SESSION_BEGIN_TIME);
        if (!directory.exists()) {
            if (directory.mkdir()) {
                Log.i("FOLDER-CREATED", "Folder created successfully...");
            } else {
                Log.i("MKDIR", "Error creating directory...");
            }
        }
    }

    private boolean hasReadPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasWritePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    boolean hasBluetoothPermissions() {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED);
    }

    boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    boolean hasRecordPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    boolean hasStoragePermissions() {
        return hasWritePermission() && hasReadPermission();
    }

    private void requestPermissions() {
        ArrayList<String> permissions = new ArrayList<>();
        if (!hasReadPermission()) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (!hasWritePermission()) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (!hasBluetoothPermissions()) {
            permissions.add(Manifest.permission.BLUETOOTH);
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        }

        if (!hasRecordPermission()) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }

        if (!hasLocationPermissions()) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!permissions.isEmpty()) {
            String[] p = new String[permissions.size()];
            permissions.toArray(p);
            ActivityCompat.requestPermissions(this, p, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        Log.i("PERMISSIONS", "Received results from : " + requestCode);

        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                return;
            }
        }
        startApp();

    }

    public synchronized void toggleOpenCase() {
        openCase = !openCase;
    }

    public synchronized void incrementSynchronized() {
        schedule++;
    }

    public synchronized void decrementSynchronized() {
        schedule--;
    }

    private synchronized void setToUnity() {
        schedule = 1;
    }

    private synchronized void setToZero() {
        schedule = 0;
    }

    // to show battery level on top right

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryLevelReceiver, ifilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
//        unregisterReceiver(batteryLevelReceiver);
    }

    private void updateBatteryLevelUI(float batteryPct) {
        TextView batteryLevelText = findViewById(R.id.batteryLevelText);
        batteryLevelText.setText(String.format(Locale.US, "%.0f%%", batteryPct));
    }

    // invisible button on top left
    public void onInvisibleButtonClick(View view) {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }

    // BLE Listener
    private void startBroadcast(BluetoothAdapter bluetoothAdapter) {

        if (bluetoothAdapter != null && !bluetoothAdapter.isMultipleAdvertisementSupported()) {
            Log.e("BLE Advertise", "Multiple advertisement not supported");
        }


        advertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
        AdvertiseSettings settings = new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).setConnectable(true).build();

        ParcelUuid pUuid = new ParcelUuid(UUID.fromString("0000" + BLE_ID + "-1234-1234-1234-4444"));

        AdvertiseData data = new AdvertiseData.Builder().setIncludeDeviceName(false).addServiceUuid(pUuid).build();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        advertiser.startAdvertising(settings, data, new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                Log.i("BLE", "Advertising started successfully");
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e("BLE", "Advertising onStartFailure: " + errorCode);
            }
        });

    }

    void stopBroadcast() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        advertiser.stopAdvertising(new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                Log.i("BLE", "Advertising stopped successfully");
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e("BLE", "Stopping Advertising onStartFailure: " + errorCode);
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void setupGattServer(BluetoothManager bluetoothManager) {

        UUID SERVICE_UUID = UUID.fromString("0000" + BLE_ID + "-1234-1234-1234-555555555555");
        UUID UUID_HELLO_WORLD = UUID.fromString("ABCD1234-1234-1234-1234-555555555555");
        gattServer = bluetoothManager.openGattServer(this, new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                super.onConnectionStateChange(device, status, newState);
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i("GATT Server", "BluetoothDevice CONNECTED: " + device);
                    runOnUiThread(() -> bottomSheetDialog.show());


                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i("GATT Server", "BluetoothDevice DISCONNECTED: " + device);
                    runOnUiThread(() -> bottomSheetDialog.hide());
                }
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

                String bleData = new String(value, Charset.defaultCharset());
                Log.i("GATT Server", bleData);

                if (bleData.equalsIgnoreCase("replace cartridge")) {
                    toggleOpenCase();

                    // Acknowledge the write
                    if (responseNeeded) {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                    }

                    runOnUiThread(()->stopBroadcast());
                    return;
                }

                String[] parts = bleData.split(";");

                USER_ID = "Guest";

                if (parts.length > 1) {
                    if (!parts[1].equals("null")) {
                        USER_ID = parts[1];
                    }
                }

                // Acknowledge the write
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }

                runOnUiThread(() -> {
                    userGreetingTextView.setText("Hello " +  USER_ID);
                    bottomSheetDialog.hide();
                    speechInfoContainer.setVisibility(View.VISIBLE);
                    stopBroadcast();
                });
            }
        });

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        BluetoothGattCharacteristic helloWorldCharacteristic = new BluetoothGattCharacteristic(UUID_HELLO_WORLD, BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        service.addCharacteristic(helloWorldCharacteristic);
        gattServer.addService(service);
    }

    public void showMicrophones() {
        ImageView microphone_left  = findViewById(R.id.imageView2);
        ImageView microphone_right  = findViewById(R.id.imageView3);

        runOnUiThread(()-> {
            microphone_left.setVisibility(View.VISIBLE);
            microphone_right.setVisibility(View.VISIBLE);
        });
    }

    public void hideMicrophones() {
        ImageView microphone_left  = findViewById(R.id.imageView2);
        ImageView microphone_right  = findViewById(R.id.imageView3);

        runOnUiThread(()-> {
            microphone_left.setVisibility(View.INVISIBLE);
            microphone_right.setVisibility(View.INVISIBLE);
        });
    }

}