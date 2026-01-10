package com.tomersch.mp3playerai.activities;

import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.tomersch.mp3playerai.R;
import com.tomersch.mp3playerai.ai.ModelDownloadWorker;
import com.tomersch.mp3playerai.ai.models.ModelCatalog;
import com.tomersch.mp3playerai.ai.models.ModelPrefs;
import com.tomersch.mp3playerai.ai.models.ModelStorage;
import com.tomersch.mp3playerai.ui.SimpleItemSelectedListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModelManagerActivity extends AppCompatActivity {

    private Spinner spinnerCatalog;
    private Spinner spinnerInstalled;
    private Button btnDownload, btnRefresh, btnUseSelected, btnDelete;
    private TextView tvModelsDir, tvDownloadStatus, tvInstalledInfo, tvActiveModel;

    private ArrayAdapter<String> catalogAdapter;
    private ArrayAdapter<String> installedAdapter;

    private List<ModelCatalog.ModelItem> catalogItems = new ArrayList<>();
    private List<File> installedFiles = new ArrayList<>();

    private UUID activeDownloadWorkId = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_manager);

        spinnerCatalog = findViewById(R.id.spinner_catalog);
        spinnerInstalled = findViewById(R.id.spinner_installed);
        btnDownload = findViewById(R.id.btn_download);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnUseSelected = findViewById(R.id.btn_use_selected);
        btnDelete = findViewById(R.id.btn_delete);

        tvModelsDir = findViewById(R.id.tv_models_dir);
        tvDownloadStatus = findViewById(R.id.tv_download_status);
        tvInstalledInfo = findViewById(R.id.tv_installed_info);
        tvActiveModel = findViewById(R.id.tv_active_model);

        File modelsDir = ModelStorage.getModelsDir(this);
        tvModelsDir.setText("Models dir: " + modelsDir.getAbsolutePath());

        // Catalog spinner
        catalogItems = ModelCatalog.getDefaultCatalog();
        List<String> catalogNames = new ArrayList<>();
        for (ModelCatalog.ModelItem item : catalogItems) {
            catalogNames.add(item.displayName + " (" + item.fileName + ")");
        }
        catalogAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, catalogNames);
        spinnerCatalog.setAdapter(catalogAdapter);

        // Installed spinner (filled by refresh)
        installedAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>());
        spinnerInstalled.setAdapter(installedAdapter);

        spinnerInstalled.setOnItemSelectedListener(new SimpleItemSelectedListener(pos -> updateInstalledInfo(pos)));

        btnRefresh.setOnClickListener(v -> refreshInstalled());

        btnDownload.setOnClickListener(v -> {
            int idx = spinnerCatalog.getSelectedItemPosition();
            if (idx < 0 || idx >= catalogItems.size()) return;
            ModelCatalog.ModelItem chosen = catalogItems.get(idx);
            startDownload(chosen);
        });

        btnUseSelected.setOnClickListener(v -> {
            int idx = spinnerInstalled.getSelectedItemPosition();
            if (idx < 0 || idx >= installedFiles.size()) {
                Toast.makeText(this, "No installed model selected", Toast.LENGTH_SHORT).show();
                return;
            }
            File f = installedFiles.get(idx);
            ModelPrefs.setActiveModelPath(this, f.getAbsolutePath());
            updateActiveModelLabel();
            Toast.makeText(this, "Active model set", Toast.LENGTH_SHORT).show();
        });

        btnDelete.setOnClickListener(v -> {
            int idx = spinnerInstalled.getSelectedItemPosition();
            if (idx < 0 || idx >= installedFiles.size()) {
                Toast.makeText(this, "No installed model selected", Toast.LENGTH_SHORT).show();
                return;
            }
            File f = installedFiles.get(idx);

            // If deleting active model, clear preference
            String active = ModelPrefs.getActiveModelPath(this);
            if (active != null && active.equals(f.getAbsolutePath())) {
                ModelPrefs.setActiveModelPath(this, null);
            }

            boolean ok = f.delete();
            if (!ok) {
                Toast.makeText(this, "Failed to delete: " + f.getName(), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Deleted: " + f.getName(), Toast.LENGTH_SHORT).show();
            }
            refreshInstalled();
            updateActiveModelLabel();
        });

        refreshInstalled();
        updateActiveModelLabel();
    }

    private void refreshInstalled() {
        installedFiles = ModelStorage.listInstalledModels(this);

        List<String> names = new ArrayList<>();
        for (File f : installedFiles) names.add(f.getName());

        installedAdapter.clear();
        installedAdapter.addAll(names);
        installedAdapter.notifyDataSetChanged();

        if (installedFiles.isEmpty()) {
            tvInstalledInfo.setText("Info: (no models installed)");
        } else {
            updateInstalledInfo(0);
        }
    }

    private void updateInstalledInfo(int pos) {
        if (pos < 0 || pos >= installedFiles.size()) {
            tvInstalledInfo.setText("Info: -");
            return;
        }
        File f = installedFiles.get(pos);
        String size = Formatter.formatFileSize(this, f.length());
        tvInstalledInfo.setText("Info: " + f.getName() + " • " + size);
    }

    private void updateActiveModelLabel() {
        String active = ModelPrefs.getActiveModelPath(this);
        tvActiveModel.setText("Active model: " + (active == null ? "(none)" : active));
    }

    private void startDownload(ModelCatalog.ModelItem item) {
        // If already installed, do nothing (download-once behavior)
        File dest = ModelStorage.getModelFile(this, item.fileName);
        if (dest.exists() && dest.length() > 0) {
            Toast.makeText(this, "Already downloaded", Toast.LENGTH_SHORT).show();
            refreshInstalled();
            return;
        }

        // Ensure directory exists
        ModelStorage.ensureModelsDir(this);

        Data input = new Data.Builder()
                .putString(ModelDownloadWorker.KEY_URL, item.url)
                .putString(ModelDownloadWorker.KEY_FILE_NAME, item.fileName)
                .putString(ModelDownloadWorker.KEY_DISPLAY_NAME, item.displayName)
                // optional for later:
                .putString(ModelDownloadWorker.KEY_SHA256, item.sha256 == null ? "" : item.sha256)
                .build();

        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(ModelDownloadWorker.class)
                .setInputData(input)
                .build();

        activeDownloadWorkId = req.getId();
        WorkManager.getInstance(this).enqueue(req);

        observeDownload(req.getId());
        tvDownloadStatus.setText("Status: downloading " + item.displayName);
    }

    private void observeDownload(UUID workId) {
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(workId).observe(this, workInfo -> {
            if (workInfo == null) return;

            if (workInfo.getState() == WorkInfo.State.RUNNING) {
                int progress = workInfo.getProgress().getInt(ModelDownloadWorker.PROGRESS, -1);
                if (progress >= 0) {
                    tvDownloadStatus.setText("Status: downloading... " + progress + "%");
                } else {
                    tvDownloadStatus.setText("Status: downloading...");
                }
                return;
            }

            if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                tvDownloadStatus.setText("Status: download complete");
                Toast.makeText(this, "Download complete", Toast.LENGTH_SHORT).show();
                refreshInstalled();
                return;
            }

            if (workInfo.getState() == WorkInfo.State.FAILED) {
                String err = workInfo.getOutputData().getString(ModelDownloadWorker.KEY_ERROR);
                tvDownloadStatus.setText("Status: failed" + (err != null ? (" • " + err) : ""));
                Toast.makeText(this, "Download failed", Toast.LENGTH_LONG).show();
                return;
            }

            if (workInfo.getState() == WorkInfo.State.CANCELLED) {
                tvDownloadStatus.setText("Status: cancelled");
            }
        });
    }
}
