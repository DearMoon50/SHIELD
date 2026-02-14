package com.dearmoon.shield;

import android.os.Build; // Added import for Build
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileAccessActivity extends AppCompatActivity {
    private static final String TAG = "FileAccessActivity";
    private RecyclerView recyclerView;
    private LogAdapter logAdapter;
    private TextView tvEventCount;
    private Button btnClearLogs;
    private List<LogViewerActivity.LogEntry> allEvents = new ArrayList<>();
    private List<LogViewerActivity.LogEntry> filteredEvents = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_access);

        // Force status bar to black
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(0xFF000000);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbarFileAccess);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        initializeViews();
        loadLogs();
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recyclerViewFileAccess);
        tvEventCount = findViewById(R.id.tvFileAccessCount);
        btnClearLogs = findViewById(R.id.btnClearFileAccess);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        logAdapter = new LogAdapter(filteredEvents);
        recyclerView.setAdapter(logAdapter);

        btnClearLogs.setOnClickListener(v -> clearAllLogs());
        findViewById(R.id.btnRefreshFileAccess).setOnClickListener(v -> {
            loadLogs();
            Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadLogs() {
        allEvents.clear();
        filteredEvents.clear();

        File telemetryFile = new File(getFilesDir(), "modeb_telemetry.json");
        Log.i(TAG, "Loading from: " + telemetryFile.getAbsolutePath());
        Log.i(TAG, "File exists: " + telemetryFile.exists());

        if (!telemetryFile.exists()) {
            Log.w(TAG, "No telemetry file found");
            updateEventCount();
            logAdapter.notifyDataSetChanged();
            return;
        }

        Log.i(TAG, "File size: " + telemetryFile.length() + " bytes");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(telemetryFile)))) {

            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (line.trim().isEmpty())
                    continue;

                try {
                    JSONObject json = new JSONObject(line);
                    String eventType = json.optString("eventType", "");
                    Log.d(TAG, "Line " + lineCount + ": eventType=" + eventType);

                    if ("FILE_SYSTEM".equals(eventType)) {
                        LogViewerActivity.LogEntry entry = parseFileEvent(json);
                        if (entry != null) {
                            allEvents.add(entry);
                            Log.d(TAG, "Added event: " + entry.title);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing line " + lineCount + ": " + line, e);
                }
            }
            Log.i(TAG, "Total events loaded: " + allEvents.size());
        } catch (Exception e) {
            Log.e(TAG, "Error reading file", e);
        }

        Collections.sort(allEvents, (a, b) -> Long.compare(b.timestamp, a.timestamp));
        filteredEvents.addAll(allEvents);
        logAdapter.notifyDataSetChanged();
        updateEventCount();
    }

    private LogViewerActivity.LogEntry parseFileEvent(JSONObject json) throws Exception {
        LogViewerActivity.LogEntry entry = new LogViewerActivity.LogEntry();
        entry.timestamp = json.getLong("timestamp");
        entry.type = "FILE_SYSTEM";

        String operation = json.optString("operation", "UNKNOWN");
        String filePath = json.optString("filePath", "Unknown");
        String fileName = getFileName(filePath);

        entry.title = fileName;
        entry.details = String.format(
                "Operation: %s\nFull Path: %s\nExtension: %s\nSize: %d bytes",
                operation,
                filePath,
                json.optString("fileExtension", "N/A"),
                json.optLong("fileSizeAfter", 0));

        switch (operation) {
            case "DELETED":
                entry.severity = "HIGH";
                break;
            case "MODIFY":
            case "COMPRESSED":
                entry.severity = "MEDIUM";
                break;
            default:
                entry.severity = "INFO";
        }

        return entry;
    }

    private String getFileName(String fullPath) {
        if (fullPath == null || fullPath.isEmpty())
            return "Unknown";
        int lastSlash = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'));
        return lastSlash >= 0 ? fullPath.substring(lastSlash + 1) : fullPath;
    }

    private void updateEventCount() {
        String countText = String.format(Locale.US, "Showing %d file operations", filteredEvents.size());
        tvEventCount.setText(countText);
    }

    private void clearAllLogs() {
        File telemetryFile = new File(getFilesDir(), "modeb_telemetry.json");
        telemetryFile.delete();

        allEvents.clear();
        filteredEvents.clear();
        logAdapter.notifyDataSetChanged();
        updateEventCount();

        Toast.makeText(this, "All logs cleared", Toast.LENGTH_SHORT).show();
    }
}
