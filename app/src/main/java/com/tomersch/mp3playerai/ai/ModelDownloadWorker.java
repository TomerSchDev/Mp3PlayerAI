package com.tomersch.mp3playerai.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tomersch.mp3playerai.ai.models.ModelStorage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ModelDownloadWorker extends Worker {

    public static final String KEY_URL = "url";
    public static final String KEY_FILE_NAME = "file_name";
    public static final String KEY_DISPLAY_NAME = "display_name";
    public static final String KEY_SHA256 = "sha256";

    public static final String KEY_ERROR = "error";
    public static final String PROGRESS = "progress";

    public ModelDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String url = getInputData().getString(KEY_URL);
        String fileName = getInputData().getString(KEY_FILE_NAME);

        if (url == null || url.trim().isEmpty() || fileName == null || fileName.trim().isEmpty()) {
            return fail("Missing url/fileName");
        }

        try {
            ModelStorage.ensureModelsDir(getApplicationContext());
            File dest = ModelStorage.getModelFile(getApplicationContext(), fileName);

            // download-once: if file exists and non-empty, treat as success
            if (dest.exists() && dest.length() > 0) {
                return Result.success();
            }

            // Download to temp then rename (atomic-ish)
            File tmp = new File(dest.getAbsolutePath() + ".part");
            if (tmp.exists()) tmp.delete();

            OkHttpClient client = new OkHttpClient.Builder().build();
            Request req = new Request.Builder().url(url).get().build();

            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    return fail("HTTP " + resp.code());
                }

                ResponseBody body = resp.body();
                if (body == null) return fail("Empty body");

                long total = body.contentLength();
                try (InputStream in = body.byteStream();
                     FileOutputStream out = new FileOutputStream(tmp)) {

                    byte[] buf = new byte[1024 * 64];
                    long readTotal = 0;
                    int r;

                    while ((r = in.read(buf)) != -1) {
                        out.write(buf, 0, r);
                        readTotal += r;

                        if (total > 0) {
                            int pct = (int) ((readTotal * 100) / total);
                            setProgressAsync(new Data.Builder().putInt(PROGRESS, pct).build());
                        }
                    }
                }
            }

            // Basic sanity
            if (!tmp.exists() || tmp.length() == 0) {
                return fail("Downloaded file is empty");
            }

            // Replace old if exists
            if (dest.exists()) dest.delete();
            boolean renamed = tmp.renameTo(dest);
            if (!renamed) {
                // fallback copy
                return fail("Failed to finalize download (rename failed)");
            }

            // Optional: verify SHA-256 here later if you want.
            return Result.success();

        } catch (Exception e) {
            return fail(e.getMessage());
        }
    }

    private Result fail(String msg) {
        Data out = new Data.Builder().putString(KEY_ERROR, msg == null ? "unknown" : msg).build();
        return Result.failure(out);
    }
}
