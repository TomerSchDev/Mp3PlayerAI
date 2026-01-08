package com.tomersch.mp3playerai.ai;

import static com.tomersch.mp3playerai.ai.DBUtils.LOCAL_DB_NAME;
import static com.tomersch.mp3playerai.ai.DBColumns.DBSongData;

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
 * Enhanced SongMatcher with Auto-Categorization
 *
 * Cleaner flow: Map all DB songs first, then if/else for each device song
 */
public class SongMatcher {
    private static final String TAG = "SongMatcher";

    private Context context;
    private AdvancedSongCategorizer categorizer;

    public SongMatcher(Context context) {
        this.context = context;
        this.categorizer = new AdvancedSongCategorizer(context);
    }

    private File ensureSourceDatabase() {
        File sourceDbFile = context.getDatabasePath(DBUtils.DB_NAME);

        if (sourceDbFile.exists()) {
            return sourceDbFile;
        }

        File dbFile = DBUtils.copyDatabase(context);
        if (dbFile == null) {
            Log.e(TAG, "Error copying database");
            return null;
        }

        return dbFile;
    }

    /**
     * Build local database with cleaner if/else flow
     */
    public String buildLocalDatabase(List<Song> deviceSongs) {
        Log.d(TAG, "=".repeat(60));
        Log.d(TAG, "🎵 Building AI-Enhanced Local Database");
        Log.d(TAG, "   Device songs: " + deviceSongs.size());
        Log.d(TAG, "=".repeat(60));

        File localDbFile = context.getDatabasePath(LOCAL_DB_NAME);

        // Delete old local database
        if (localDbFile.exists()) {
            localDbFile.delete();
        }

        SQLiteDatabase sourceDb = null;
        SQLiteDatabase localDb = null;

        try {
            // Open source database
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

            // Create table
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
                            "filename TEXT, " +
                            "last_time_played TEXT"+
                            ", primary key(filename))"
            );

            // STEP 1: Map ALL database songs by normalized filename
            Log.d(TAG, "📋 Step 1: Mapping AI database songs...");

            Map<String, DBSongData> dbSongMap = new HashMap<>();

            // Use the clean column order from DBColumns
            String query = "SELECT " + DBColumns.getSelectColumns() + " FROM "+ DBUtils.TABLE_SONGS;
            Cursor cursor = sourceDb.rawQuery(query, null);

            while (cursor.moveToNext()) {
                // ✨ Clean construction using DBColumns.DBSongData!
                DBSongData data = new DBSongData(cursor);
                String normalizedKey = normalizeSongKey(data.filename);
                dbSongMap.put(normalizedKey, data);
            }

            cursor.close();

            Log.d(TAG, "   ✅ Mapped " + dbSongMap.size() + " AI database songs");

            // STEP 2: For each device song, check if in DB or categorize
            Log.d(TAG, "📱 Step 2: Processing device songs...");

            String insertSql = "INSERT INTO songs VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            SQLiteStatement stmt = localDb.compileStatement(insertSql);

            int matchCount = 0;
            int newSongCount = 0;

            localDb.beginTransaction();

            for (Song deviceSong : deviceSongs) {
                // Normalize device song filename for lookup
                File file = new File(deviceSong.getPath());
                String deviceFilename = file.getName();
                String normalizedKey = normalizeSongKey(deviceFilename);

                // Check if this song exists in AI database
                DBSongData dbData = dbSongMap.get(normalizedKey);

                if (dbData != null) {
                    // ✅ MATCHED: Song exists in AI database
                    stmt.clearBindings();
                    stmt.bindString(1, deviceSong.getPath());  // Use device path!
                    stmt.bindString(2, dbData.title);
                    stmt.bindString(3, dbData.artist);
                    stmt.bindString(4, dbData.genre);
                    stmt.bindString(5, dbData.tags);
                    stmt.bindLong(6, dbData.year);
                    stmt.bindLong(7, dbData.hype);
                    stmt.bindLong(8, dbData.aggressive);
                    stmt.bindLong(9, dbData.melodic);
                    stmt.bindLong(10, dbData.atmospheric);
                    stmt.bindLong(11, dbData.cinematic);
                    stmt.bindLong(12, dbData.rhythmic);

                    if (dbData.audioBlob != null) {
                        stmt.bindBlob(13, dbData.audioBlob);
                    } else {
                        stmt.bindNull(13);
                    }

                    if (dbData.metaBlob != null) {
                        stmt.bindBlob(14, dbData.metaBlob);
                    } else {
                        stmt.bindNull(14);
                    }

                    stmt.bindString(15, deviceFilename);
                    stmt.bindString(16, "{}");


                    stmt.executeInsert();
                    matchCount++;

                } else {
                    // 🆕 NEW SONG: Auto-categorize it!
                    Log.d(TAG, "   📝 NEW: " + deviceSong.getTitle());

                    AdvancedSongCategorizer.CategorizedSong categorized =
                            categorizer.categorizeSong(deviceSong);

                    stmt.clearBindings();
                    stmt.bindString(1, deviceSong.getPath());
                    stmt.bindString(2, categorized.song.getTitle());
                    stmt.bindString(3, categorized.song.getArtist());
                    stmt.bindString(4, categorized.genre != null ? categorized.genre : "Unknown");
                    stmt.bindString(5, categorized.tags != null ? categorized.tags : "auto-categorized");
                    stmt.bindLong(6, categorized.year);

                    // Mood scores from categorizer
                    stmt.bindLong(7, categorized.moodScores.get("hype"));
                    stmt.bindLong(8, categorized.moodScores.get("aggressive"));
                    stmt.bindLong(9, categorized.moodScores.get("melodic"));
                    stmt.bindLong(10, categorized.moodScores.get("atmospheric"));
                    stmt.bindLong(11, categorized.moodScores.get("cinematic"));
                    stmt.bindLong(12, categorized.moodScores.get("rhythmic"));

                    // No embeddings yet
                    stmt.bindNull(13);  // audio_blob
                    stmt.bindNull(14);  // meta_blob

                    stmt.bindString(15, deviceFilename);
                    stmt.bindString(16, "{}");


                    stmt.executeInsert();
                    newSongCount++;

                    Log.d(TAG, "      → Genre: " + categorized.genre +
                            ", Hype: " + categorized.moodScores.get("hype") +
                            ", Cinematic: " + categorized.moodScores.get("cinematic"));
                }
            }

            localDb.setTransactionSuccessful();
            localDb.endTransaction();

            Log.d(TAG, "=".repeat(60));
            Log.d(TAG, "✨ Database Build Complete!");
            Log.d(TAG, "   ✅ Matched songs: " + matchCount);
            Log.d(TAG, "   🆕 Auto-categorized: " + newSongCount);
            Log.d(TAG, "   📊 Total songs: " + (matchCount + newSongCount));
            Log.d(TAG, "=".repeat(60));

            return localDbFile.getPath();

        } catch (Exception e) {
            Log.e(TAG, "❌ Error building local database", e);
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
     * Normalize filename for matching
     * Handles Hebrew, Arabic, Chinese, emoji, and all Unicode properly!
     */
    private String normalizeSongKey(String fileName) {
        if (fileName == null) fileName = "";

        return fileName
                .toLowerCase()
                // Remove file extension
                .replaceAll("\\.(mp3|flac|m4a|wav|ogg|aac)$", "")
                // Remove special punctuation but KEEP Unicode letters/numbers
                .replaceAll("[\\p{Punct}&&[^_]]", "")  // Remove punctuation except underscore
                .replaceAll("_", " ")                   // Replace underscore with space
                .replaceAll("\\s+", " ")                // Normalize whitespace
                .trim();
    }

    /**
     * Check if local database needs rebuild
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

            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM songs", null);
            int dbCount = 0;
            if (cursor.moveToFirst()) {
                dbCount = cursor.getInt(0);
            }
            cursor.close();

            cursor = db.rawQuery("SELECT filename FROM songs", null);
            Map<String, Boolean> dbSongs = new HashMap<>();

            while (cursor.moveToNext()) {
                String fileName = cursor.getString(0);
                String key = normalizeSongKey(fileName);
                dbSongs.put(key, true);
            }
            cursor.close();
            db.close();

            Map<String, Boolean> deviceSongKeys = new HashMap<>();
            for (Song song : deviceSongs) {
                File file = new File(song.getPath());
                String filename = file.getName();
                String key = normalizeSongKey(filename);
                deviceSongKeys.put(key, true);
            }

            if (dbCount != deviceSongs.size()) {
                Log.d(TAG, "Song count changed: DB=" + dbCount + ", Device=" + deviceSongs.size() + " - rebuild needed");
                return true;
            }

            for (String key : deviceSongKeys.keySet()) {
                if (!dbSongs.containsKey(key)) {
                    Log.d(TAG, "New song found - rebuild needed");
                    return true;
                }
            }

            for (String key : dbSongs.keySet()) {
                if (!deviceSongKeys.containsKey(key)) {
                    Log.d(TAG, "Song removed - rebuild needed");
                    return true;
                }
            }

            Log.d(TAG, "Local database is up to date");
            return false;

        } catch (Exception e) {
            Log.e(TAG, "Error checking rebuild", e);
            return true;
        }
    }

    public boolean localDatabaseExists() {
        File localDbFile = context.getDatabasePath(LOCAL_DB_NAME);
        return localDbFile.exists();
    }

    public void deleteLocalDatabase() {
        File localDbFile = context.getDatabasePath(LOCAL_DB_NAME);
        if (localDbFile.exists()) {
            localDbFile.delete();
            Log.d(TAG, "Local database deleted");
        }
    }
}