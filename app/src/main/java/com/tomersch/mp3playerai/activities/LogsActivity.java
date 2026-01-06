package com.tomersch.mp3playerai.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.tomersch.mp3playerai.R;
import com.tomersch.mp3playerai.utils.UserActivityLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Activity to view and export activity logs
 */
public class LogsActivity extends AppCompatActivity {

    private TextView tvStats;
    private TextView tvRecentLogs;
    private Button btnExportCsv;
    private Button btnExportJson;
    private Button btnClearLogs;
    private Button btnBack;

    private UserActivityLogger activityLogger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);

        activityLogger = new UserActivityLogger(this);

        tvStats = findViewById(R.id.tvStats);
        tvRecentLogs = findViewById(R.id.tvRecentLogs);
        btnExportCsv = findViewById(R.id.btnExportCsv);
        btnExportJson = findViewById(R.id.btnExportJson);
        btnClearLogs = findViewById(R.id.btnClearLogs);
        btnBack = findViewById(R.id.btnBack);

        loadLogs();

        btnExportCsv.setOnClickListener(v -> exportCsv());
        btnExportJson.setOnClickListener(v -> exportJson());
        btnClearLogs.setOnClickListener(v -> clearLogs());
        btnBack.setOnClickListener(v -> finish());
    }

    private void loadLogs() {
        // Load statistics
        UserActivityLogger.ListeningStats stats = activityLogger.getListeningStats();
        tvStats.setText(stats.toString());

        // Load recent logs (last 50)
        List<UserActivityLogger.ActivityLog> logs = activityLogger.getAllLogs();
        StringBuilder recentLogsText = new StringBuilder();

        recentLogsText.append("=== RECENT ACTIVITY (Last 50) ===\n\n");

        int start = Math.max(0, logs.size() - 50);
        for (int i = logs.size() - 1; i >= start; i--) {
            recentLogsText.append(logs.get(i).toString()).append("\n");
        }

        tvRecentLogs.setText(recentLogsText.toString());
    }

    private void exportCsv() {
        try {
            String csv = activityLogger.exportLogsAsCsv();
            File file = new File(getExternalFilesDir(null), "activity_logs.csv");

            FileWriter writer = new FileWriter(file);
            writer.write(csv);
            writer.close();

            shareFile(file, "text/csv");

            Toast.makeText(this, "Exported to: " + file.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.e(e.getMessage(), "Error exporting logs to CSV", e);
            Toast.makeText(this, "Export failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void exportJson() {
        try {
            String json = activityLogger.exportLogsAsJson();
            File file = new File(getExternalFilesDir(null), "activity_logs.json");

            FileWriter writer = new FileWriter(file);
            writer.write(json);
            writer.close();

            shareFile(file, "application/json");

            Toast.makeText(this, "Exported to: " + file.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.e("ActivityLogsActivity", "Error exporting logs to JSON", e);
            Toast.makeText(this, "Export failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void shareFile(File file, String mimeType) {
        Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", file);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(shareIntent, "Share logs"));
    }

    private void clearLogs() {
        activityLogger.clearLogs();
        Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show();
        loadLogs();
    }
}