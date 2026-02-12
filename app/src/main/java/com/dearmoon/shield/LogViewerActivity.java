package com.dearmoon.shield;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
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

public class LogViewerActivity extends AppCompatActivity {
    private static final String TAG = "LogViewerActivity";

    private RecyclerView recyclerView;
    private LogAdapter logAdapter;
    private TextView tvEventCount;
    private Spinner spinnerFilter;

    private List<LogEntry> allEvents = new ArrayList<>();
    private List<LogEntry> filteredEvents = new ArrayList<>();

    private String currentFilter = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbarLogs);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        initializeViews();
        loadAllLogs();
        applyFilter();
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recyclerViewLogs);
        tvEventCount = findViewById(R.id.tvEventCount);
        spinnerFilter = findViewById(R.id.spinnerFilter);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        logAdapter = new LogAdapter(filteredEvents);
        recyclerView.setAdapter(logAdapter);

        // Setup filter spinner
        String[] filters = { "ALL", "FILE_SYSTEM", "HONEYFILE_ACCESS", "NETWORK", "DETECTION", "ACCESSIBILITY" };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, filters);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(adapter);

        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentFilter = filters[position];
                applyFilter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void loadAllLogs() {
        allEvents.clear();

        // Load telemetry events
        loadTelemetryEvents();

        // Load detection results
        loadDetectionResults();

        // Sort by timestamp (newest first)
        Collections.sort(allEvents, (a, b) -> Long.compare(b.timestamp, a.timestamp));

        Log.i(TAG, "Loaded " + allEvents.size() + " total events");
    }

    private void loadTelemetryEvents() {
        File telemetryFile = new File(getFilesDir(), "modeb_telemetry.json");
        if (!telemetryFile.exists()) {
            Log.w(TAG, "Telemetry file not found: " + telemetryFile.getAbsolutePath());
            return;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(telemetryFile)))) {

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;

                try {
                    JSONObject json = new JSONObject(line);
                    LogEntry entry = parseTelemetryEvent(json);
                    if (entry != null) {
                        allEvents.add(entry);
                        count++;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing telemetry line: " + line, e);
                }
            }

            Log.i(TAG, "Loaded " + count + " telemetry events from " + telemetryFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error reading telemetry file", e);
            Toast.makeText(this, "Error loading telemetry: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private LogEntry parseTelemetryEvent(JSONObject json) throws Exception {
        String eventType = json.getString("eventType");
        long timestamp = json.getLong("timestamp");

        LogEntry entry = new LogEntry();
        entry.timestamp = timestamp;
        entry.type = eventType;

        switch (eventType) {
            case "FILE_SYSTEM":
                String operation = json.optString("operation", "UNKNOWN");

                entry.title = "File System Event";
                entry.details = String.format(
                        "Operation: %s\nFile: %s\nExtension: %s\nSize Before: %d bytes\nSize After: %d bytes",
                        operation,
                        json.optString("filePath", "Unknown"),
                        json.optString("fileExtension", "N/A"),
                        json.optLong("fileSizeBefore", 0),
                        json.optLong("fileSizeAfter", 0));
                entry.severity = getSeverityForOperation(operation);
                break;

            case "HONEYFILE_ACCESS":
                entry.title = "⚠️ HONEYFILE ACCESSED";
                entry.details = String.format("Access Type: %s\nFile: %s\nCalling UID: %d\nPackage: %s",
                        json.optString("accessType", "UNKNOWN"),
                        json.optString("filePath", "Unknown"),
                        json.optInt("callingUid", -1),
                        json.optString("packageName", "unknown"));
                entry.severity = "HIGH";
                break;

            case "NETWORK":
                entry.title = "Network Event";
                entry.details = String.format(
                        "Protocol: %s\nDestination: %s:%d\nBytes Sent: %d\nBytes Received: %d\nApp UID: %d",
                        json.optString("protocol", "UNKNOWN"),
                        json.optString("destinationIp", "0.0.0.0"),
                        json.optInt("destinationPort", 0),
                        json.optLong("bytesSent", 0),
                        json.optLong("bytesReceived", 0),
                        json.optInt("appUid", -1));
                entry.severity = "INFO";
                break;

            case "ACCESSIBILITY":
                entry.title = "Accessibility Event";
                entry.details = String.format("Package: %s\nClass: %s\nEvent Type: %d",
                        json.optString("packageName", "Unknown"),
                        json.optString("className", "Unknown"),
                        json.optInt("eventTypeCode", -1));
                entry.severity = "INFO";
                break;

            default:
                entry.title = "Unknown Event";
                entry.details = json.toString(2);
                entry.severity = "INFO";
        }

        return entry;
    }

    private void loadDetectionResults() {
        File detectionFile = new File(getFilesDir(), "detection_results.json");
        if (!detectionFile.exists()) {
            Log.w(TAG, "Detection file not found");
            return;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(detectionFile)))) {

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;

                try {
                    JSONObject json = new JSONObject(line);
                    LogEntry entry = parseDetectionResult(json);
                    if (entry != null) {
                        allEvents.add(entry);
                        count++;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing detection line: " + line, e);
                }
            }

            Log.i(TAG, "Loaded " + count + " detection results");
        } catch (Exception e) {
            Log.e(TAG, "Error reading detection file", e);
            Toast.makeText(this, "Error loading detections: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private LogEntry parseDetectionResult(JSONObject json) throws Exception {
        LogEntry entry = new LogEntry();
        entry.timestamp = json.getLong("timestamp");
        entry.type = "DETECTION";

        int confidenceScore = json.getInt("confidence_score");
        String sprtState = json.getString("sprt_state");

        entry.title = "🔍 Detection Result";
        entry.details = String.format(
                "File: %s\n\nEntropy: %s\nKL-Divergence: %s\nSPRT State: %s\nConfidence Score: %d/100\n\nRisk Level: %s",
                json.optString("file_path", "Unknown"),
                json.optString("entropy", "N/A"),
                json.optString("kl_divergence", "N/A"),
                sprtState,
                confidenceScore,
                confidenceScore >= 70 ? "⚠️ HIGH RISK" : confidenceScore >= 40 ? "MEDIUM" : "LOW");

        if (confidenceScore >= 70) {
            entry.severity = "CRITICAL";
        } else if (confidenceScore >= 40) {
            entry.severity = "MEDIUM";
        } else {
            entry.severity = "LOW";
        }

        return entry;
    }

    private String getSeverityForOperation(String operation) {
        switch (operation) {
            case "DELETE":
                return "HIGH";
            case "MODIFY":
            case "CLOSE_WRITE":
                return "MEDIUM";
            case "CREATE":
            case "MOVED_TO":
                return "INFO";
            default:
                return "INFO";
        }
    }

    private void applyFilter() {
        filteredEvents.clear();

        for (LogEntry entry : allEvents) {
            // Apply filtering logic
            if (currentFilter.equals("ALL")) {
                // In the 'ALL' view, we only show critical file system events
                // (MOD/DEL/CLOSE_WRITE)
                // and everything else (Detection, Network, Honeyfile)
                if (entry.type.equals("FILE_SYSTEM")) {
                    if (entry.details.contains("Operation: MODIFY") ||
                            entry.details.contains("Operation: DELETE") ||
                            entry.details.contains("Operation: CLOSE_WRITE")) {
                        filteredEvents.add(entry);
                    }
                } else {
                    filteredEvents.add(entry);
                }
            } else if (entry.type.equals(currentFilter)) {
                // If a specific filter is selected, show EVERYTHING of that type
                filteredEvents.add(entry);
            }
        }

        logAdapter.notifyDataSetChanged();
        updateEventCount();
    }

    private void updateEventCount() {
        String countText = String.format(Locale.US, "Showing %d of %d events",
                filteredEvents.size(), allEvents.size());
        tvEventCount.setText(countText);
    }

    // Inner class for log entries
    public static class LogEntry {
        public long timestamp;
        public String type;
        public String title;
        public String details;
        public String severity; // INFO, LOW, MEDIUM, HIGH, CRITICAL

        public String getFormattedTime() {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US);
            return sdf.format(new Date(timestamp));
        }

        public int getSeverityColor() {
            switch (severity) {
                case "CRITICAL":
                    return 0xFFD32F2F; // Red
                case "HIGH":
                    return 0xFFFF6F00; // Orange
                case "MEDIUM":
                    return 0xFFFFA000; // Amber
                case "LOW":
                    return 0xFF1976D2; // Blue
                default:
                    return 0xFF757575; // Gray
            }
        }
    }
}
