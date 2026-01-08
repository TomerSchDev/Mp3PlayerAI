package com.tomersch.mp3playerai.ai;

import static com.tomersch.mp3playerai.ai.DBUtils.LOCAL_DB_NAME;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.tomersch.mp3playerai.models.Song;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Matches device songs with AI database and creates a temporary database
 * with only songs that exist on the device (with correct device paths)
 */
public class SongMatcher {
    private static final String TAG = "SongMatcher";

    private Context context;

    public SongMatcher(Context context) {
        this.context = context;
    }
    private File ensureSourceDatabase() {
        File sourceDbFile = context.getDatabasePath(DBUtils.DB_NAME);

        if (sourceDbFile.exists()) {
            return sourceDbFile; // Already exists
        }

        File dbFile = DBUtils.copyDatabase(context);
        if (dbFile == null) {
            Log.e(TAG, "Error copying database");
            return null;
        }

        return dbFile;
    }
    /**
     * Build local database with only songs that exist on device
     * @param deviceSongs List of all songs on the device
     * @return Path to local database
     */
    public String buildLocalDatabase(List<Song> deviceSongs) {
        Log.d(TAG, "Building local database with " + deviceSongs.size() + " device songs");

        File localDbFile = context.getDatabasePath(LOCAL_DB_NAME);

        // Delete old local database if it exists
        if (localDbFile.exists()) {
            localDbFile.delete();
        }

        SQLiteDatabase sourceDb = null;
        SQLiteDatabase localDb = null;

        try {
            // Open source database (read-only)
            File sourceDbFile = ensureSourceDatabase();
            if (!sourceDbFile.exists()) {
                Log.e(TAG, "Source database not found: " + DBUtils.DB_NAME);
                return null;
            }

            sourceDb = SQLiteDatabase.openDatabase(
                    sourceDbFile.getPath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY
            );

            // Create local database
            localDb = SQLiteDatabase.openOrCreateDatabase(localDbFile, null);

            // Create table structure (same as source)
            localDb.execSQL(
                    "CREATE TABLE IF NOT EXISTS songs (" +
                            "path TEXT, " +
                            "title TEXT, " +
                            "artist TEXT, " +
                            "genre TEXT, " +
                            "tags TEXT, " +
                            "year INTEGER, " +
                            "hype INTEGER, " +
                            "aggressive INTEGER, " +
                            "melodic INTEGER, " +
                            "atmospheric INTEGER, " +
                            "cinematic INTEGER, " +
                            "rhythmic INTEGER, " +
                            "audio_blob BLOB, " +
                            "meta_blob BLOB, " +
                            "filename TEXT" +
                            ")"
            );

            // Build lookup map for device songs (title -> Song)
            Map<String, Song> deviceSongMap = new HashMap<>();
            for (Song song : deviceSongs) {
                File file = new File(song.getPath());
                String filename = file.getName();
                String normalized = normalizeSongKey(filename);
                deviceSongMap.put(normalized, song);
            }

            Log.d(TAG, "Built device song map with " + deviceSongMap.size() + " entries");

            // Query all songs from source database
            Cursor cursor = sourceDb.rawQuery(
                    "SELECT path, title, artist, genre, tags, year, " +
                            "hype, aggressive, melodic, atmospheric, cinematic, rhythmic, " +
                            "audio_blob, meta_blob, filename FROM songs",
                    null
            );
            String insertSql = "INSERT INTO songs VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            SQLiteStatement stmt = localDb.compileStatement(insertSql);
            int matchCount = 0;
            localDb.beginTransaction();
            int fileNameCol = cursor.getColumnIndex(DBColumns.FILE_NAME.getName());

            while (cursor.moveToNext()) {
                String songFilename = cursor.getString(fileNameCol);
                // Try to match with device song
                String key = normalizeSongKey(songFilename);
                Song deviceSong = deviceSongMap.get(key);

                if (deviceSong != null) {
                    // Found match! Insert into local database with device path
                    stmt.clearBindings();
                    stmt.bindString(1, deviceSong.getPath());      // path - use device path!
                    for (int D = 1; D < DBColumns.ColumnToIndex(DBColumns.AUDIO_BLOB); D++)
                    {
                        stmt.bindString(D+1, cursor.getString(D));       // title
                    }
                    stmt.bindBlob(DBColumns.ColumnToIndex(DBColumns.AUDIO_BLOB)+1,cursor.getBlob(DBColumns.ColumnToIndex(DBColumns.AUDIO_BLOB)+1));
                    stmt.bindBlob(DBColumns.ColumnToIndex(DBColumns.META_BLOB)+1,cursor.getBlob(DBColumns.ColumnToIndex(DBColumns.META_BLOB)+1));

                    stmt.bindString(fileNameCol, deviceSong.getTitle());    // filename

                    stmt.executeInsert();
                    matchCount++;
                }
            }

            localDb.setTransactionSuccessful();
            localDb.endTransaction();

            cursor.close();

            Log.d(TAG, "Local database created with " + matchCount + " matched songs");

            return localDbFile.getPath();

        } catch (Exception e) {
            Log.e(TAG, "Error building local database", e);
            return null;
        } finally {
            if (sourceDb != null && sourceDb.isOpen()) {
                sourceDb.close();
            }
            if (localDb != null && localDb.isOpen()) {
                localDb.close();
            }
        }
    }

    /**
     * Normalize song title and artist for matching
     */
    private String normalizeSongKey(String fileName) {
        if (fileName == null) fileName = "";

        // Normalize: lowercase, remove special chars, trim

        return fileName
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s|]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Check if local database needs to be rebuilt
     * @param deviceSongs Current list of device songs
     * @return true if rebuild is needed, false if local database is up to date
     */
    public boolean needsRebuild(List<Song> deviceSongs) {
        File localDbFile = context.getDatabasePath(LOCAL_DB_NAME);

        if (!localDbFile.exists()) {
            Log.d(TAG, "Local database doesn't exist - rebuild needed");
            return true;
        }

        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                    localDbFile.getPath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY
            );

            // Get count of songs in local database
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM songs", null);
            int dbCount = 0;
            if (cursor.moveToFirst()) {
                dbCount = cursor.getInt(0);
            }
            cursor.close();

            // Get all song keys from local database
            cursor = db.rawQuery("SELECT title, artist FROM songs", null);
            Map<String, Boolean> dbSongs = new HashMap<>();
            int fileNameCol = cursor.getColumnIndex(DBColumns.FILE_NAME.getName());

            while (cursor.moveToNext()) {
                String fileName = cursor.getString(fileNameCol);
                String key = normalizeSongKey(fileName);
                dbSongs.put(key, true);
            }
            cursor.close();
            db.close();

            // Build device song keys
            Map<String, Boolean> deviceSongKeys = new HashMap<>();
            for (Song song : deviceSongs) {
                File file = new File(song.getPath());
                String filename = file.getName();
                String key = normalizeSongKey(filename);
                deviceSongKeys.put(key, true);
            }

            // Check if counts are different
            if (dbCount != deviceSongs.size()) {
                Log.d(TAG, "Song count changed: DB has " + dbCount + ", device has " + deviceSongs.size() + " - rebuild needed");
                return true;
            }

            // Check if all device songs are in database
            for (String key : deviceSongKeys.keySet()) {
                if (!dbSongs.containsKey(key)) {
                    Log.d(TAG, "New song found on device - rebuild needed");
                    return true;
                }
            }

            // Check if all database songs are still on device
            for (String key : dbSongs.keySet()) {
                if (!deviceSongKeys.containsKey(key)) {
                    Log.d(TAG, "Song removed from device - rebuild needed");
                    return true;
                }
            }

            Log.d(TAG, "Local database is up to date - no rebuild needed");
            return false;

        } catch (Exception e) {
            Log.e(TAG, "Error checking if rebuild needed", e);
            return true; // Rebuild if we can't check
        }
    }

    /**
     * Check if local database exists
     */
    public boolean localDatabaseExists() {
        File localDbFile = context.getDatabasePath(LOCAL_DB_NAME);
        return localDbFile.exists();
    }

    /**
     * Delete local database
     */
    public void deleteLocalDatabase() {
        File localDbFile = context.getDatabasePath(LOCAL_DB_NAME);
        if (localDbFile.exists()) {
            localDbFile.delete();
            Log.d(TAG, "Local database deleted");
        }
    }
}