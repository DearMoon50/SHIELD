package com.dearmoon.shield.data;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class TelemetryStorage {
    private static final String TAG = "TelemetryStorage";
    private static final String LOG_FILE = "modeb_telemetry.json";
    private final File logFile;

    public TelemetryStorage(Context context) {
        logFile = new File(context.getFilesDir(), LOG_FILE);
        Log.i(TAG, "TelemetryStorage initialized: " + logFile.getAbsolutePath());
    }

    public synchronized void store(TelemetryEvent event) {
        try (FileWriter writer = new FileWriter(logFile, true)) {
            String jsonStr = event.toJSON().toString();
            writer.write(jsonStr);
            writer.write("\n");
            writer.flush();
            Log.d(TAG, "Event stored: " + event.getEventType());
        } catch (Exception e) {
            Log.e(TAG, "Failed to store event", e);
        }
    }

    public File getLogFile() {
        return logFile;
    }
}
