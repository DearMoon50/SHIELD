package com.dearmoon.shield.detection;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import com.dearmoon.shield.data.FileSystemEvent;
import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UnifiedDetectionEngine {
    private static final String TAG = "UnifiedDetectionEngine";
    private static final String DETECTION_LOG = "detection_results.json";

    private final Context context;
    private final EntropyAnalyzer entropyAnalyzer;
    private final KLDivergenceCalculator klCalculator;
    private final SPRTDetector sprtDetector;

    private final HandlerThread detectionThread;
    private final Handler detectionHandler;

    private final ConcurrentLinkedQueue<String> recentModifications = new ConcurrentLinkedQueue<>();
    private long lastModificationTime = 0;
    private int modificationsInWindow = 0;
    private static final long TIME_WINDOW_MS = 1000; // 1 second window

    public UnifiedDetectionEngine(Context context) {
        this.context = context;
        this.entropyAnalyzer = new EntropyAnalyzer();
        this.klCalculator = new KLDivergenceCalculator();
        this.sprtDetector = new SPRTDetector();

        detectionThread = new HandlerThread("DetectionThread");
        detectionThread.start();
        detectionHandler = new Handler(detectionThread.getLooper());
    }

    public void processFileEvent(FileSystemEvent event) {
        detectionHandler.post(() -> analyzeFileEvent(event));
    }

    private void analyzeFileEvent(FileSystemEvent event) {
        try {
            String operation = event.toJSON().optString("operation", "");
            String filePath = event.toJSON().optString("filePath", "");

            Log.d(TAG, "Analyzing file event: " + operation + " on " + filePath);

            // Only analyze modifications and creations
            if (!operation.equals("MODIFY") && !operation.equals("CLOSE_WRITE")
                    && !operation.equals("CREATE")) {
                Log.d(TAG, "Skipping operation: " + operation);
                return;
            }

            File file = new File(filePath);

            // Skip if file doesn't exist or is too small
            if (!file.exists()) {
                Log.d(TAG, "File doesn't exist: " + filePath);
                return;
            }

            if (file.length() < 100) {
                Log.d(TAG, "File too small (" + file.length() + " bytes): " + filePath);
                return;
            }

            // Update modification rate for SPRT
            updateModificationRate();

            // Calculate entropy and KL-divergence
            Log.d(TAG, "Calculating entropy for: " + filePath);
            double entropy = entropyAnalyzer.calculateEntropy(file);
            double klDivergence = klCalculator.calculateDivergence(file);

            Log.d(TAG, "Entropy: " + entropy + ", KL: " + klDivergence);

            // Skip if entropy calculation failed
            if (entropy == 0.0) {
                Log.w(TAG, "Entropy calculation failed for: " + filePath);
                return;
            }

            // Get SPRT state
            double modRate = (double) modificationsInWindow / (TIME_WINDOW_MS / 1000.0);
            SPRTDetector.SPRTState sprtState = sprtDetector.addObservation(modRate);

            // Calculate composite confidence score
            int confidenceScore = calculateConfidenceScore(entropy, klDivergence, sprtState);

            Log.i(TAG, "Detection: entropy=" + entropy + ", kl=" + klDivergence + ", sprt=" + sprtState + ", score="
                    + confidenceScore);

            // Create detection result
            DetectionResult result = new DetectionResult(
                    entropy, klDivergence, sprtState.name(), confidenceScore, filePath);

            // Log result
            logDetectionResult(result);

            // Reset SPRT if decision reached
            if (sprtState != SPRTDetector.SPRTState.CONTINUE) {
                Log.w(TAG, "SPRT Decision: " + sprtState + " | Confidence: " + confidenceScore);
                sprtDetector.reset();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error analyzing file event", e);
        }
    }

    private void updateModificationRate() {
        long currentTime = System.currentTimeMillis();

        // Add current modification
        recentModifications.add(String.valueOf(currentTime));

        // Remove modifications outside time window
        while (!recentModifications.isEmpty()) {
            long oldTime = Long.parseLong(recentModifications.peek());
            if (currentTime - oldTime > TIME_WINDOW_MS) {
                recentModifications.poll();
            } else {
                break;
            }
        }

        modificationsInWindow = recentModifications.size();
        lastModificationTime = currentTime;
    }

    private int calculateConfidenceScore(double entropy, double klDivergence,
            SPRTDetector.SPRTState sprtState) {
        int score = 0;

        // Entropy contribution (0-40 points)
        if (entropy > 7.8)
            score += 40;
        else if (entropy > 7.5)
            score += 30;
        else if (entropy > 7.0)
            score += 20;
        else if (entropy > 6.0)
            score += 10;

        // KL-divergence contribution (0-30 points)
        if (klDivergence < 0.05)
            score += 30; // Very uniform (encrypted)
        else if (klDivergence < 0.1)
            score += 20;
        else if (klDivergence < 0.2)
            score += 10;

        // SPRT contribution (0-30 points)
        if (sprtState == SPRTDetector.SPRTState.ACCEPT_H1)
            score += 30;
        else if (sprtState == SPRTDetector.SPRTState.CONTINUE)
            score += 10;

        return Math.min(score, 100);
    }

    private void logDetectionResult(DetectionResult result) {
        try {
            File logFile = new File(context.getFilesDir(), DETECTION_LOG);
            FileWriter writer = new FileWriter(logFile, true);
            String jsonStr = result.toJSON().toString();
            writer.write(jsonStr + "\n");
            writer.close();

            Log.i(TAG, "Detection logged: " + jsonStr);

            if (result.isHighRisk()) {
                Log.w(TAG, "HIGH RISK DETECTED: " + result.toJSON().toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to log detection result", e);
        }
    }

    public File getDetectionLogFile() {
        return new File(context.getFilesDir(), DETECTION_LOG);
    }

    public void shutdown() {
        detectionThread.quitSafely();
    }
}
