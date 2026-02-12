package com.dearmoon.shield.collectors;

import android.content.Context;
import android.os.FileObserver;
import android.util.Log;
import androidx.annotation.Nullable;
import com.dearmoon.shield.data.HoneyfileEvent;
import com.dearmoon.shield.data.TelemetryStorage;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class HoneyfileCollector {
    private static final String TAG = "HoneyfileCollector";
    private final TelemetryStorage storage;
    private final List<HoneyfileObserver> observers = new ArrayList<>();
    private final List<File> honeyfiles = new ArrayList<>();

    public HoneyfileCollector(TelemetryStorage storage) {
        this.storage = storage;
    }

    public void createHoneyfiles(Context context, String[] directories) {
        for (String dir : directories) {
            File directory = new File(dir);
            if (!directory.exists()) continue;

            File honeyfile = new File(directory, ".important_document.txt");
            try (FileWriter writer = new FileWriter(honeyfile)) {
                writer.write("CONFIDENTIAL DATA - DO NOT ACCESS");
                honeyfiles.add(honeyfile);
                
                HoneyfileObserver observer = new HoneyfileObserver(honeyfile.getAbsolutePath());
                observer.startWatching();
                observers.add(observer);
                
                Log.d(TAG, "Created honeyfile: " + honeyfile.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Failed to create honeyfile", e);
            }
        }
    }

    public void stopWatching() {
        for (HoneyfileObserver observer : observers) {
            observer.stopWatching();
        }
        observers.clear();
    }

    private class HoneyfileObserver extends FileObserver {
        private final String filePath;

        HoneyfileObserver(String path) {
            super(path, OPEN | MODIFY | DELETE | CLOSE_WRITE);
            this.filePath = path;
        }

        @Override
        public void onEvent(int event, @Nullable String path) {
            String accessType = getAccessType(event);
            HoneyfileEvent honeyEvent = new HoneyfileEvent(
                filePath, accessType, android.os.Process.myUid(), "unknown"
            );
            storage.store(honeyEvent);
            Log.w(TAG, "HONEYFILE ACCESSED: " + filePath + " - " + accessType);
        }

        private String getAccessType(int event) {
            switch (event) {
                case OPEN: return "OPEN";
                case MODIFY: return "MODIFY";
                case DELETE: return "DELETE";
                case CLOSE_WRITE: return "WRITE";
                default: return "UNKNOWN";
            }
        }
    }
}
