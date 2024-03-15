package com.aj.videplayertest;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private ClientSocket socket;
    private MotionDataSocket motionSocket;
    private VideoView videoView;
    private ImageView imageView;
    private MediaPlayer mediaPlayer;
    private TextView logText, timerText, predictedClassText, correctCountText,
            requiredClassText, motionText, sessionTimeText;
    private List<Schedule> scheduleList;
    private volatile int schedule = 0;
    private volatile int image_count = 0;
    private long timePassed = 0;
    private List<Category> labelList;
    private int correctCounter, motionCounter;
    private final int NUM_CLASSES = 9; // Change this to match the number of classes in your model
    private static final String TAG = "ImageClassifierHelper";
    private ImageClassifier imageClassifier;
    private ImageProcessor imageProcessor;
    private double[] THRESHOLD_VALUES, REQUIRED_FRAMES, TIMEOUT;
    private final String CONFIG_FILE_PATH = Environment.getExternalStorageDirectory().getPath() + "/i-wash-config/config.txt";
    private boolean IN_SESSION;
    private String SESSION_BEGIN_TIME, LOCATION_ID, USER_ID, STATION_ID;
    private LinkedList<String>[] inferenceTimes;
    private LinkedList<Float>[] inferenceConfidence;
    private LinkedList<Integer>[] inferenceClass;
    final int RECONNECT_AFTER = 1000;
    long timeElapsed = 0;
    private boolean isConnected = false, isMotion = false;
    private int MIN_FRAMES_TO_BEGIN_PROCESS;
    private double THRESHOLD_MOTION;
    private int NO_MOTION_TIMEOUT;
    private final int BITMAP_TIMER_ID = 0;
    private final int VIDEO_TIMER_ID = 1;
    private final int MOTION_TIMER_ID = 2;
    private final int SCHEDULE_TIMER_ID = 3;
    private final int INFERENCE_TIMER_ID = 4;

    private final int[] TIMER_DELAYS = {0, 0, 0, 0, 0};
    private final int[] TIMER_PERIODS = {1000, 100, 1000, 100, 1};

    private ExecutorService executor, motionExecutor, bitmapExecutor;
    private final int NUM_THREADS = 5;
    private Timer bitmapSocketControlTimer, motionSocketControlTimer, videoControlTimer,
            scheduleTimer, inferenceTimer;
    private TimerTask scheduleTimerTask, motionSocketTimerTask, bitmapSocketTimerTask,
            videoControlTimerTask, inferenceTimerTask;
    private MessageSender sender;
    private int LAST_STAGE_TIMEOUT;
    private volatile int SAVE_FRAMES;
    private double CONFIDENCE_THRESHOLD_PRE_STAGE;

    DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
    DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS");

    private void startTimer(int timerId) {
        Timer[] timers = {bitmapSocketControlTimer, videoControlTimer, motionSocketControlTimer,
                scheduleTimer, inferenceTimer};
        TimerTask[] timerTasks = {bitmapSocketTimerTask, videoControlTimerTask,
                motionSocketTimerTask, scheduleTimerTask, inferenceTimerTask};

        if (timers[timerId] != null && timerTasks[timerId] != null) {
            // Schedule the timer task
            timers[timerId].schedule(timerTasks[timerId], TIMER_DELAYS[timerId],
                    TIMER_PERIODS[timerId]);
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

        Timer[] timers = {bitmapSocketControlTimer, videoControlTimer, motionSocketControlTimer,
                scheduleTimer, inferenceTimer};

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

                        if (isConnected && isMotion) {
                            updateTimerText(String.format(Locale.US, "%1.0f", Math.floor(timeout * 0.001f)));
                            updateCorrectCountText(String.valueOf(correctCounter));

                            // if a timeout occurs move to next stage
                            if (timeout >= scheduleList.get(schedule).getTimeout()) {
                                correctCounter = 0;

                                if (schedule >= 12) {
                                    setToUnity(); // return to screen 1 after the session
                                    runOnUiThread(() -> sessionTimeText.setText(""));
                                } else if (schedule >= 3) {
                                    videoView.start();
                                    incrementSynchronized();
                                    labelList.clear();
                                } else if (schedule == 2) {
                                    decrementSynchronized();
                                } else if (schedule > 10 || schedule == 0) {
                                    incrementSynchronized();
                                }

                                if (schedule > 3 || schedule < 12) mediaPlayer.start();
                                videoView.seekTo(scheduleList.get(schedule).getStart());
                                timePassed = System.currentTimeMillis();
                            }
                        } else { // if not connected or not in motion

                            if (!isMotion && timeout > NO_MOTION_TIMEOUT) {
                                setToUnity();
                            }

                            if (!isConnected) {
                                setToUnity();
                            }

                            runOnUiThread(() -> sessionTimeText.setText(""));
                            updateCorrectCountText("");
                            updatePredictionText("");
                            updateRequiredText("");
                        }

                        // loops part of a video until timeout
                        if (videoView != null) {
                            Schedule s = scheduleList.get(schedule);
                            if (videoView.getCurrentPosition() >= s.getEnd()) {
                                videoView.seekTo(s.getStart());
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
                            sender.doInBackground(String.valueOf(schedule));
                        } else {
                            sender.doInBackground("-1");
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
                                    File directory = new File(Environment.getExternalStorageDirectory().getPath() + "/i-wash-sessions/", STATION_ID + "-" + SESSION_BEGIN_TIME);
                                    if (!directory.exists()) {
                                        if (directory.mkdir()) {
                                            Log.i("FOLDER-CREATED", "Folder created successfully...");
                                        } else {
                                            Log.i("MKDIR", "Error creating directory...");
                                        }
                                    }
                                }

                                if (label != null) {
                                    if (label.getIndex() == level && label.getScore() >= THRESHOLD_VALUES[level]) {
                                        correctCounter++;
                                        if (correctCounter >= REQUIRED_FRAMES[level]) {
                                            labelList.clear();
                                            correctCounter = 0;
                                            mediaPlayer.start();
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
                            imageView.setVisibility(View.INVISIBLE);
                            IN_SESSION = false;
                            try {
                                String filename = Environment.getExternalStorageDirectory().getPath() +
                                        "/i-wash-sessions/" + STATION_ID + "-" + SESSION_BEGIN_TIME +
                                        "/" + STATION_ID + "-" + SESSION_BEGIN_TIME + ".audit.csv";
                                try {
                                    // Create a BufferedWriter to write to the text file
                                    BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
                                    int correctCount, frameCount = 0;
                                    long timeDifference, totalTime = 0;
                                    String recordStatus = "IR";
                                    USER_ID = "12.14.DS";
                                    String stepLabel, inferenceTime = "";

                                    // Write the headers for the columns
                                    writer.write("HW_SESSION_SEQUENCE_ROW_Num,TIME_SESSION_START," +
                                            "HWS_STATION_ID,HW_Session_Sequence_ROW_Num,HW_Record," +
                                            "HW_Step_Sequence_Num,INFERENCE_TIME,GMT,Location_ID," +
                                            "User_ID,STEP_NUMBER,STEP_LABEL,Inferencing_Counter," +
                                            "Inferred_Step,Confidence,Threshold,Elapsed_Time_" +
                                            "Since_Previous_Frame_In_milliseconds,Total_Time_Since" +
                                            "_The_First_Frame_was_inferenced_for_the_Step," +
                                            "Screen_count_value_to_advance_step,timeout," +
                                            "Total_Time_from_Step_1_to_Step_9_in_milliseconds," +
                                            "Num_of_Steps_with_early_completion," +
                                            "Num_Steps_that_timed_out\n");

                                    long[] totalTimePerStep = new long[NUM_CLASSES];

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
                                                LocalTime last_frame_time = LocalTime.parse(inferenceTime, timeFormat);
                                                LocalTime current_frame_time = LocalTime.parse(inferenceTimes[i].get(j), timeFormat);

                                                // Calculate the time difference
                                                Duration duration = Duration.between(last_frame_time, current_frame_time);

                                                // Convert the time difference to milliseconds
                                                timeDifference = duration.toMillis();
                                                totalTime += timeDifference;
                                            }

                                            inferenceTime = inferenceTimes[i].get(j);

                                            writer.write(String.format(Locale.US,
                                                    "%s-%s-step_%d-%d.png,%s,%s,%d,%s,%d,%s,%d,%s,%s,%d,%s,%d,%d,%1.2f,%1.2f,%d,%d,%1.0f,%1.0f,,,\n",
                                                    STATION_ID, SESSION_BEGIN_TIME, i+1, frameCount, SESSION_BEGIN_TIME,
                                                    STATION_ID, frameCount, recordStatus, frameCount, inferenceTimes[i].get(j),
                                                    -4, LOCATION_ID, USER_ID, i + 1, stepLabel, correctCount, 1 + inferenceClass[i].get(j),
                                                    inferenceConfidence[i].get(j), THRESHOLD_VALUES[i],
                                                    timeDifference, totalTime, REQUIRED_FRAMES[i], TIMEOUT[i]));
                                            frameCount++;

                                        }

                                        // write a line for step summary
                                        writer.write(String.format(Locale.US,
                                                "%s-%s-%d,%s,%s,%d,%s,%d,%s,%d,%s,%s,%d,%s,%d,%s,%s,%s,%s,%d,%s,%s,,,\n",
                                                STATION_ID, SESSION_BEGIN_TIME, frameCount, SESSION_BEGIN_TIME,
                                                STATION_ID, frameCount, "ST", frameCount, "",
                                                -4, LOCATION_ID, USER_ID, i + 1, (size < 15) ? "M" : "T", correctCount, "",
                                                "", "",
                                                "", totalTime, "", ""));
                                        totalTimePerStep[i] = totalTime;

                                    }

                                    int num_steps_timed_out = 0;
                                    int num_steps_not_timed_out = 0;
                                    long totalTimeForSession = Arrays.stream(totalTimePerStep).sum();
                                    runOnUiThread(() -> sessionTimeText.setText(String.format(Locale.US, "Session Time %1.0f Seconds", (float) totalTimeForSession / 1000)));

                                    for (int k = 0; k < NUM_CLASSES; k++) {
                                        if (totalTimePerStep[k] < TIMEOUT[k]) {
                                            num_steps_not_timed_out++;
                                        } else {
                                            num_steps_timed_out++;
                                        }
                                    }

                                    // write a line for the session summary
                                    frameCount++;
                                    writer.write(String.format(Locale.US,
                                            "%s-%s-%d,%s,%s,%d,%s,%d,,,,,,,,,,,,,,,%d,%d,%d\n",
                                            STATION_ID, SESSION_BEGIN_TIME, frameCount,
                                            SESSION_BEGIN_TIME,
                                            STATION_ID,
                                            frameCount,
                                            "SST",
                                            frameCount,
                                            totalTimeForSession,
                                            num_steps_not_timed_out,
                                            num_steps_timed_out));

                                    // Close the BufferedWriter
                                    writer.close();

                                    Log.i("DATA-OUT", "audit file for session has been written to " + filename);

                                    // session summary file
                                    String filenameSummary = Environment.getExternalStorageDirectory().getPath() +
                                            "/i-wash-sessions/" + STATION_ID + "-" + SESSION_BEGIN_TIME + "/" +
                                            STATION_ID + "-" + SESSION_BEGIN_TIME + ".summary.txt";
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

                                        Log.i("DATA-OUT", "summary file for session has been written to " + filename);
                                    } catch (IOException e) {
                                        Log.i("DATA-OUT", "error occurred when writing summary file: " + e.getMessage());
                                    }


                                } catch (IOException e) {
                                    Log.i("ERROR", "Error writing audit file: " + e.getMessage());
                                }

                                for (int i = 0; i < NUM_CLASSES; i++) {
                                    inferenceTimes[i].clear();
                                    inferenceConfidence[i].clear();
                                    inferenceClass[i].clear();
                                }
                            } catch (Exception e) {
                                Log.i("ERROR", "Error writing file...");
                            }
                        }
                    }
                };
        }
    }


    private void setupConfigNew() {
    }

    private void setupConfig() {
        THRESHOLD_VALUES = new double[NUM_CLASSES];
        REQUIRED_FRAMES = new double[NUM_CLASSES];
        TIMEOUT = new double[NUM_CLASSES];
        IN_SESSION = false;

        int count_threshold = 0;
        int count_frames = 0;
        int count_timeout = 0;

        File file = new File(CONFIG_FILE_PATH);

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    if (parts[0].contains("Min_Req_Inferencing_Value")) {
                        THRESHOLD_VALUES[count_threshold] = Double.parseDouble(parts[1]);
                        count_threshold++;
                    } else if (parts[0].contains("Confidence_Threshold_First_Step")) {
                        CONFIDENCE_THRESHOLD_PRE_STAGE = Double.parseDouble(parts[1]);
                    } else if (parts[0].contains("Required_Frames_Above_Min_Req")) {
                        REQUIRED_FRAMES[count_frames] = Double.parseDouble(parts[1]);
                        count_frames++;
                    } else if (parts[0].contains("Timout_For_Step")) {
                        TIMEOUT[count_timeout] = Double.parseDouble(parts[1]);
                        count_timeout++;
                    } else if (parts[0].contains("Station_ID")) {
                        STATION_ID = parts[1];
                    } else if (parts[0].contains("Location_ID")) {
                        LOCATION_ID = parts[1];
                    } else if (parts[0].contains("Required_First_Step_Frames_To_Begin_Hand_Wash")) {
                        MIN_FRAMES_TO_BEGIN_PROCESS = Integer.parseInt(parts[1]);
                    } else if (parts[0].contains("Motion_Threshold")) {
                        THRESHOLD_MOTION = Integer.parseInt(parts[1]);
                    } else if (parts[0].contains("No_Motion_Timeout")) {
                        NO_MOTION_TIMEOUT = Integer.parseInt(parts[1]);
                    } else if (parts[0].contains("Last_Step_Timeout")) {
                        LAST_STAGE_TIMEOUT = Integer.parseInt(parts[1]);
                    } else if (parts[0].contains("save_frames")) {
                        SAVE_FRAMES = Integer.parseInt(parts[1]);
                    }
                }
            }
            br.close();
        } catch (IOException e) {
            Log.i("CONFIG_FILE", "Error reading config file: " + e.getMessage());
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

        executor = Executors.newFixedThreadPool(3);
        motionExecutor = Executors.newSingleThreadExecutor();
        bitmapExecutor = Executors.newFixedThreadPool(NUM_THREADS);
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
            }

            @Override
            public void onClientDisconnected(String message) {
                updateText(message);
                isConnected = false;
            }

            @Override
            public void onClientError(String message) {
            }

            @Override
            public void updateFPS(String message) {
            }

            @Override
            public void onFrameReceived(Bitmap b) {
                if (b != null) {
                    if (schedule > 1 && schedule < 12) {
                        if (!imageView.isShown()) {
                            imageView.setVisibility(View.VISIBLE);
                        }
                        bitmapExecutor.submit(() -> {
                            Category label = classifyImage(b);
                            imageView.setImageBitmap(b);
                            if (label != null) {
                                labelList.add(label);
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
                                motionCounter = 0;
                                isMotion = false;
                                imageView.setVisibility(View.INVISIBLE);
                            }
                        }
                    } catch (Exception e) {
                        Log.i("ERROR", "Error reading float from motionValueList!");
                    }

                    if (schedule == 1 && isMotion) {
                        initializeArrays();
                        updateMotionText("");
                        correctCounter = 0;
                        incrementSynchronized();
                        mediaPlayer.start();
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
            }

            @Override
            public void onClientDisconnected(String message) {
            }

            @Override
            public void onClientError(String message) {
            }

            @Override
            public void updateFPS(String message) {
            }

            @Override
            public void onFrameReceived(Bitmap frame) {
            }
        });
    }

    private void initializeArrays() {
        labelList = new ArrayList<>();

        inferenceTimes = new LinkedList[NUM_CLASSES];
        inferenceConfidence = new LinkedList[NUM_CLASSES];
        inferenceClass = new LinkedList[NUM_CLASSES];

        for (int i = 0; i < NUM_CLASSES; i++) {
            inferenceTimes[i] = new LinkedList<>();
            inferenceConfidence[i] = new LinkedList<>();
            inferenceClass[i] = new LinkedList<>();
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        if (Build.VERSION.SDK_INT >= 33) {
            setupConfigNew();
        } else {
            setupConfig();
        }
        LoadSchedule();
        initializeViews();
        initializeArrays();
        prepareImageClassifier();
        setupSockets();
        sender = new MessageSender();

    }

    @Override
    protected void onStart() {
        super.onStart();

        mediaPlayer = MediaPlayer.create(this, R.raw.tone);
        setToZero();

        setupVideo();

        initializeTimer(MOTION_TIMER_ID);
        initializeTimer(BITMAP_TIMER_ID);
        initializeTimer(SCHEDULE_TIMER_ID);
        initializeTimer(VIDEO_TIMER_ID);

        initializeTimerTask(MOTION_TIMER_ID);
        initializeTimerTask(BITMAP_TIMER_ID);
        initializeTimerTask(SCHEDULE_TIMER_ID);
        initializeTimerTask(VIDEO_TIMER_ID);

        correctCounter = 0;
        motionCounter = 0;

        updateCorrectCountText(String.valueOf(correctCounter));

        timePassed = System.currentTimeMillis();

//        try {


        startTimer(SCHEDULE_TIMER_ID);

        // Problem: The logic for this thread was being run using the ui thread.
        // Fix: a new thread was launched to do this work.
        startTimer(VIDEO_TIMER_ID);
        startTimer(BITMAP_TIMER_ID);

        startTimer(MOTION_TIMER_ID);

        startTimer(INFERENCE_TIMER_ID);
    }

    private void setupImageClassifier(Context context) {
        ImageClassifier.ImageClassifierOptions.Builder optionsBuilder =
                ImageClassifier.ImageClassifierOptions.builder()
                        .setScoreThreshold(0.01f)
                        .setMaxResults(NUM_CLASSES);

        try {
            String MODEL_NAME = "model.tflite";
            imageClassifier =
                    ImageClassifier.createFromFileAndOptions(
                            context,
                            MODEL_NAME,
                            optionsBuilder.build());
        } catch (IOException e) {
            Toast.makeText(this, "Image classifier failed to "
                    + "initialize. See error logs for details", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "TFLite failed to load model with error: "
                    + e.getMessage());
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
            mediaPlayer.reset();
            mediaPlayer.release();
        } catch (Exception e) {
            Log.i("MEDIAPLAYER", "Error while releasing resources for media player: " + e.getMessage());
        }

        stopTimer(MOTION_TIMER_ID);
        stopTimer(BITMAP_TIMER_ID);
        stopTimer(VIDEO_TIMER_ID);
        stopTimer(SCHEDULE_TIMER_ID);
        stopTimer(INFERENCE_TIMER_ID);

        socket.stop();
        motionSocket.stop();

        shutdown(executor);
        shutdown(bitmapExecutor);
        shutdown(motionExecutor);

        labelList.clear();

    }

    private Category classifyImage(Bitmap frame) {
        if (frame != null) {

            if (SAVE_FRAMES == 1) {
                if (schedule >= 3 && schedule <= 11) {
                    String filePath = Environment.getExternalStorageDirectory().getPath() +
                            String.format(Locale.US, "/i-wash-sessions/%s-%s/%s-%s-step_%d-%d.png",
                                    STATION_ID, SESSION_BEGIN_TIME, STATION_ID, SESSION_BEGIN_TIME, schedule - 2, image_count);
                    incrementImageCount();
                    executor.submit(() -> {
                        Bitmap imageCopy = frame.copy(frame.getConfig(), true);
                        saveBitmapToPng(imageCopy, filePath);
                    });
                }
            }

            TensorImage tensorImage =
                    imageProcessor.process(TensorImage.fromBitmap(frame));

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
        runOnUiThread(() -> logText.setText(message));

    }

    private void updateCorrectCountText(String message) {
        runOnUiThread(() -> correctCountText.setText(message));
    }

    private void updatePredictionText(String message) {
        runOnUiThread(() -> predictedClassText.setText(message));
    }

    private void updateRequiredText(String message) {
        runOnUiThread(() -> requiredClassText.setText(message));
    }

    private void updateMotionText(String message) {
        runOnUiThread(() -> motionText.setText(message));
    }

    private void updateTimerText(String message) {
        runOnUiThread(() -> timerText.setText(message));
    }

    private void LoadSchedule() {
        int limit = 100;
        scheduleList = new ArrayList<>();
        scheduleList.add(new Schedule(0, 4000, 4000, 4000));
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

    private boolean hasReadPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasWritePermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ArrayList<String> permissions = new ArrayList<>();
        if (!hasReadPermission()) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (!hasWritePermission()) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (!permissions.isEmpty()) {
            String[] p = new String[permissions.size()];
            permissions.toArray(p);
            ActivityCompat.requestPermissions(this, p, 0);
        }
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

}