package com.tomersch.mp3playerai.activities;

import android.os.Bundle;
import android.text.format.Formatter;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.tomersch.mp3playerai.R;
import com.tomersch.mp3playerai.ai.ModelManager;

import com.tomersch.mp3playerai.ui.SimpleItemSelectedListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ModelManagerActivity extends AppCompatActivity {

    private Spinner spinnerCatalog;
    private Spinner spinnerInstalled;
    private Button btnDownload, btnRefresh, btnUseSelected, btnDelete;
    private TextView tvDownloadStatus;
    private TextView tvInstalledInfo;
    private TextView tvActiveModel;

    private ArrayAdapter<String> catalogAdapter;
    private ArrayAdapter<String> installedAdapter;

    private List<ModelManager.ModelConfig> catalogItems = new ArrayList<>();
    private List<String> installedFiles = new ArrayList<>();
    private ModelManager modelManager;

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

        TextView tvModelsDir = findViewById(R.id.tv_models_dir);
        tvDownloadStatus = findViewById(R.id.tv_download_status);
        tvInstalledInfo = findViewById(R.id.tv_installed_info);
        tvActiveModel = findViewById(R.id.tv_active_model);
        //tvModelsDir.setText("Models directory: " + ModelStorage.getModelsDir(this));

        // Catalog spinner
        catalogItems = new ArrayList<>(Arrays.asList(ModelManager.AVAILABLE_MODELS));
        List<String> catalogNames = new ArrayList<>();
        for (ModelManager.ModelConfig item : catalogItems) {
            catalogNames.add(item.name + " (" + item.description + ")");
        }
        catalogAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, catalogNames);
        spinnerCatalog.setAdapter(catalogAdapter);

        // Installed spinner (filled by refresh)
        installedAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>());
        spinnerInstalled.setAdapter(installedAdapter);

        spinnerInstalled.setOnItemSelectedListener(new SimpleItemSelectedListener(pos -> updateInstalledInfo(pos)));

        btnRefresh.setOnClickListener(v -> refreshInstalled());
        modelManager = new ModelManager(this);
        btnDownload.setOnClickListener(v -> {
            int idx = spinnerCatalog.getSelectedItemPosition();
            if (idx < 0 || idx >= catalogItems.size()) return;
            ModelManager.ModelConfig chosen = catalogItems.get(idx);
            startDownload(chosen);
        });

        btnUseSelected.setOnClickListener(v -> {
            int idx = spinnerInstalled.getSelectedItemPosition();
            if (idx < 0 || idx >= installedFiles.size()) {
                Toast.makeText(this, "No installed model selected", Toast.LENGTH_SHORT).show();
                return;
            }
            String name = installedFiles.get(idx);
            ModelManager.ModelConfig chosen = modelManager.getModelByName(name);
            if (chosen == null) {
                Toast.makeText(this, "Model not found", Toast.LENGTH_SHORT).show();
                return;
            }
            if (modelManager.isModelDownloaded(chosen.name)){
                modelManager.setActiveModel(chosen.name);
                updateActiveModelLabel();
            }
            else {
                Toast.makeText(this, "Model not downloaded", Toast.LENGTH_SHORT).show();
            }
        });

        btnDelete.setOnClickListener(v -> {
            int idx = spinnerInstalled.getSelectedItemPosition();
            if (idx < 0 || idx >= installedFiles.size()) {
                Toast.makeText(this, "No installed model selected", Toast.LENGTH_SHORT).show();
                return;
            }
            String name = installedFiles.get(idx);
            ModelManager.ModelConfig m = modelManager.getModelByName(name);
            if (m == null) {
                Toast.makeText(this, "Model not found", Toast.LENGTH_SHORT).show();
                return;
            }
            // If deleting active model, clear preference
            if(!modelManager.isModelDownloaded(m.name)){
                Toast.makeText(this, "Model not downloaded", Toast.LENGTH_SHORT).show();
                return;
            }
            if (m.name.equals(modelManager.getActiveModel())) {
                Toast.makeText(this, "Cannot delete active model", Toast.LENGTH_SHORT).show();
                return;
            }
            modelManager.deleteModel( m.name);
            refreshInstalled();
            Toast.makeText(this, "Model deleted", Toast.LENGTH_SHORT).show();
            updateActiveModelLabel();

        });
    }

    private void refreshInstalled() {
        installedFiles = modelManager.getDownloadedModels();



        installedAdapter.clear();
        installedAdapter.addAll(installedFiles);
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
        String s = installedFiles.get(pos);
        ModelManager.ModelConfig m = modelManager.getModelByName(s);
        if (m == null) {
            tvInstalledInfo.setText("Info: -");
            return;
        }
        String size = Formatter.formatFileSize(this, m.sizeBytes);
        tvInstalledInfo.setText("Info: " + s + " • " + size);
    }

    private void updateActiveModelLabel() {
        String active = modelManager.getActiveModel();
        tvActiveModel.setText("Active model: " + (active == null ? "(none)" : active));
    }

    private void startDownload(ModelManager.ModelConfig item) {
        if(modelManager.isModelDownloaded(item.name))
        {
            Toast.makeText(this,"Model already downloaded", Toast.LENGTH_SHORT).show();
            refreshInstalled();
            return;
        }
        new Thread(
                ()->{
                    modelManager.downloadModel(item, new ModelManager.ProgressCallback() {
                        @Override
                        public void onProgress(int percent) {
                            tvDownloadStatus.setText("Status: downloading... " + percent + "%");
                        }
                    });
                    refreshInstalled();
                    Toast.makeText(this, "Download complete", Toast.LENGTH_SHORT).show();
                }
        );
        tvDownloadStatus.setText("Status: downloading...");
    }


}
