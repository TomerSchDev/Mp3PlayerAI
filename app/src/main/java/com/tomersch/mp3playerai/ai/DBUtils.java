package com.tomersch.mp3playerai.ai;


import static com.tomersch.mp3playerai.ai.DBColumns.*;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.tomersch.mp3playerai.models.Song;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Objects;

public final class DBUtils {
    //title	artist	audio_blob	meta_blob	filename
    private DBUtils (){};
    public static final String DB_NAME = "music_vectors_ai.db";
    public static final String LOCAL_DB_NAME = "music_vectors_local.db";

    public static final String TABLE_SONGS = "songs";
    private static final String TAG = "DBUtils";

    public static void addSongToDB(SQLiteStatement stmt, Song song,DBSongData dbSongData) {
    }
    public static File copyDatabase(Context context) {

// Open input stream from assets
        boolean res =true;
        InputStream inputStream =null;
        FileOutputStream outputStream=null;
        File dbFile = new File(context.getDatabasePath(DB_NAME).getPath());
        if (dbFile.exists()) return dbFile;
        Objects.requireNonNull(dbFile.getParentFile()).mkdirs();
        try {
            inputStream = context.getAssets().open(DB_NAME);
            outputStream = new FileOutputStream(dbFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error copying database", e);
            res = false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing input stream", e);
                    res = false;
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.flush();
                    outputStream.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing output stream", e);
                    res = false;
                }
                ;
            }
        }
        if (!res) return null;
        Log.d(TAG, "Database copied from assets");
        return dbFile;
    }
}
