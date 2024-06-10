package com.aj.videplayertest;

import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.util.Log;

public class SpeechRecognitionListener implements RecognitionListener {

    private final MainActivity activity;

    public SpeechRecognitionListener(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onReadyForSpeech(Bundle params) {

        Log.d("SpeechRecognition", "Ready for speech");
        activity.showMicrophones();
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.d("SpeechRecognition", "Beginning of speech");
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        // This method can be used to update a visualizer, for example
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        // This is not typically used
    }

    @Override
    public void onEndOfSpeech() {
        Log.d("SpeechRecognition", "End of speech");
    }

    @Override
    public void onError(int error) {
        String message;
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio recording error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Client side error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Insufficient permissions";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Network error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No match found";
                activity.promptSpeechInput();
                return;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "RecognitionService busy";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "Error from server";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "No speech input";
                activity.promptSpeechInput();
                return;
            default:
                message = "Didn't understand, please try again.";
                activity.promptSpeechInput();
                return;
        }
        Log.d("SpeechRecognition", "Error occurred: " + message);
    }

    @Override
    public void onResults(Bundle results) {
        // This method is left empty and should be overridden in the specific use-case, as in MainActivity
        activity.hideMicrophones();
        activity.processResults(results);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        // Can be used for real-time feedback of recognition results
        // activity.processResults(partialResults);
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
        // Handle any specific events here if needed
    }
}


