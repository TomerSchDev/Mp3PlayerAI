package com.tomersch.mp3playerai.ai.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple catalog. Replace URLs with your own hosting:
 * - GitHub Releases
 * - Google Drive direct download
 * - Dropbox direct download
 *
 * For MediaPipe tasks, you typically download a .task model.
 */
public final class ModelCatalog {

    private ModelCatalog() {}

    public static final class ModelItem {
        public final String displayName;
        public final String fileName;
        public final String url;
        public final String sha256; // optional

        public ModelItem(String displayName, String fileName, String url, String sha256) {
            this.displayName = displayName;
            this.fileName = fileName;
            this.url = url;
            this.sha256 = sha256;
        }
    }

    public static List<ModelItem> getDefaultCatalog() {
        List<ModelItem> list = new ArrayList<>();

        // IMPORTANT:
        // Put your actual downloadable URLs here.
        // Example placeholders:
        list.add(new ModelItem(
                "Gemma 2B (example)",
                "gemma_2b.task",
                "https://YOUR_HOSTING/gemma_2b.task",
                "" // optional SHA-256
        ));

        list.add(new ModelItem(
                "Phi-2 (example)",
                "phi2.task",
                "https://YOUR_HOSTING/phi2.task",
                ""
        ));

        return list;
    }
}
