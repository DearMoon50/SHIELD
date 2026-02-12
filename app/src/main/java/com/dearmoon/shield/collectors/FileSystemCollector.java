package com.dearmoon.shield.collectors;

import android.os.FileObserver;
import android.util.Log;
import androidx.annotation.Nullable;
import com.dearmoon.shield.data.FileSystemEvent;
import com.dearmoon.shield.data.TelemetryStorage;
import com.dearmoon.shield.detection.UnifiedDetectionEngine;
import java.io.File;

public class FileSystemCollector extends FileObserver {
    private static final String TAG = "FileSystemCollector";
    private final TelemetryStorage storage;
    private final String monitoredPath;
    private UnifiedDetectionEngine detectionEngine;

    public FileSystemCollector(String path, TelemetryStorage storage) {
        super(path, CREATE | MODIFY | CLOSE_WRITE | MOVED_TO | DELETE);
        this.monitoredPath = path;
        this.storage = storage;
        Log.d(TAG, "FileSystemCollector created for: " + path);
    }

    public void setDetectionEngine(UnifiedDetectionEngine engine) {
        this.detectionEngine = engine;
    }

    @Override
    public void onEvent(int event, @Nullable String path) {
        if (path == null) return;

        String fullPath = monitoredPath + File.separator + path;
        String operation = getOperationName(event);
        
        Log.d(TAG, "FS Event detected: " + operation + " on " + fullPath);
        
        File file = new File(fullPath);
        long size = file.exists() ? file.length() : 0;

        FileSystemEvent fsEvent = new FileSystemEvent(fullPath, operation, size, size);
        storage.store(fsEvent);
        
        // Forward to detection engine for modifications
        if (detectionEngine != null && (operation.equals("MODIFY") || operation.equals("CLOSE_WRITE") || operation.equals("CREATE"))) {
            Log.d(TAG, "Forwarding to detection engine: " + fullPath);
            detectionEngine.processFileEvent(fsEvent);
        }
    }

    private String getOperationName(int event) {
        switch (event) {
            case CREATE: return "CREATE";
            case OPEN: return "OPEN";
            case MODIFY: return "MODIFY";
            case CLOSE_WRITE: return "CLOSE_WRITE";
            case MOVED_TO: return "MOVED_TO";
            case DELETE: return "DELETE";
            default: return "UNKNOWN";
        }
    }
}
