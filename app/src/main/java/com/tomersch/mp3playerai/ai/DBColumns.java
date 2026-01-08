package com.tomersch.mp3playerai.ai;

import android.database.Cursor;

/**
 * Database column definitions and utilities
 * Provides clean mapping between columns and indices
 */
public enum DBColumns {
    PATH("path"),
    TITLE("title"),
    ARTIST("artist"),
    GENRE("genre"),
    TAGS("tags"),
    YEAR("year"),
    HYPE("hype"),
    AGGRESSIVE("aggressive"),
    MELODIC("melodic"),
    ATMOSPHERIC("atmospheric"),
    CINEMATIC("cinematic"),
    RHYTHMIC("rhythmic"),
    AUDIO_BLOB("audio_blob"),
    META_BLOB("meta_blob"),
    FILE_NAME("filename"),
    LAST_TIME_PLAYED("last_time_played");

    private final String name;

    DBColumns(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    // ===== COLUMN ORDER (matches SELECT query order) =====
    // This MUST match your SQL SELECT order!
    public static final DBColumns[] columns = new DBColumns[]{
            PATH,           // 0
            TITLE,          // 1
            ARTIST,         // 2
            GENRE,          // 3
            TAGS,           // 4
            YEAR,           // 5
            HYPE,           // 6
            AGGRESSIVE,     // 7
            MELODIC,        // 8
            ATMOSPHERIC,    // 9
            CINEMATIC,      // 10
            RHYTHMIC,       // 11
            AUDIO_BLOB,     // 12
            META_BLOB,      // 13
            FILE_NAME,      // 14
            LAST_TIME_PLAYED
    };

    /**
     * Get cursor column index for a DBColumn enum
     *
     * Usage: cursor.getString(ColumnToIndex(TITLE))
     */
    public static int ColumnToIndex(DBColumns column) {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i] == column) return i;
        }
        return -1;
    }

    /**
     * Get cursor column index by name (for dynamic queries)
     *
     * Usage: cursor.getString(getIndex(cursor, TITLE))
     */
    public static int getIndex(Cursor cursor, DBColumns column) {
        return cursor.getColumnIndex(column.getName());
    }

    /**
     * Build SQL SELECT string with all columns in correct order
     *
     * Usage: "SELECT " + getSelectColumns() + " FROM songs"
     */
    public static String getSelectColumns() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(columns[i].getName());
        }
        return sb.toString();
    }

    // ===== DATABASE SONG DATA CLASS =====

    /**
     * Container for database song data
     * Can be constructed from Cursor or manually
     */
    public static class DBSongData {
        public String title;
        public String artist;
        public String genre;
        public String tags;
        public int year;
        public int hype;
        public int aggressive;
        public int melodic;
        public int atmospheric;
        public int cinematic;
        public int rhythmic;
        public byte[] audioBlob;
        public byte[] metaBlob;
        public String filename;
        public String last_time_played;


        /**
         * Construct from Cursor (automatically reads all columns)
         *
         * IMPORTANT: Cursor must have columns in the order defined in DBColumns.columns[]
         * Use: SELECT path, title, artist, genre, tags, year, hype, aggressive, melodic,
         *             atmospheric, cinematic, rhythmic, audio_blob, meta_blob, filename FROM songs
         */
        public DBSongData(Cursor cursor) {
            title = cursor.getString(ColumnToIndex(TITLE));
            artist = cursor.getString(ColumnToIndex(ARTIST));
            genre = cursor.getString(ColumnToIndex(GENRE));
            tags = cursor.getString(ColumnToIndex(TAGS));
            year = cursor.getInt(ColumnToIndex(YEAR));
            hype = cursor.getInt(ColumnToIndex(HYPE));
            aggressive = cursor.getInt(ColumnToIndex(AGGRESSIVE));
            melodic = cursor.getInt(ColumnToIndex(MELODIC));
            atmospheric = cursor.getInt(ColumnToIndex(ATMOSPHERIC));
            cinematic = cursor.getInt(ColumnToIndex(CINEMATIC));
            rhythmic = cursor.getInt(ColumnToIndex(RHYTHMIC));
            audioBlob = cursor.getBlob(ColumnToIndex(AUDIO_BLOB));
            metaBlob = cursor.getBlob(ColumnToIndex(META_BLOB));
            filename = cursor.getString(ColumnToIndex(FILE_NAME));
        }

        /**
         * Empty constructor for manual creation
         */
        public DBSongData() {
            // Default values
            title = "Unknown";
            artist = "Unknown";
            genre = "Unknown";
            tags = "";
            year = 0;
            hype = 50;
            aggressive = 50;
            melodic = 50;
            atmospheric = 50;
            cinematic = 50;
            rhythmic = 50;
            audioBlob = null;
            metaBlob = null;
            filename = "";
        }

        /**
         * Check if this song has embeddings
         */
        public boolean hasEmbeddings() {
            return audioBlob != null && metaBlob != null;
        }

        /**
         * Get mood score by name
         */
        public int getMoodScore(String moodName) {
            switch (moodName.toLowerCase()) {
                case "hype": return hype;
                case "aggressive": return aggressive;
                case "melodic": return melodic;
                case "atmospheric": return atmospheric;
                case "cinematic": return cinematic;
                case "rhythmic": return rhythmic;
                default: return 50;
            }
        }

        /**
         * Debug string representation
         */
        @Override
        public String toString() {
            return String.format("DBSongData{title='%s', artist='%s', genre='%s', " +
                            "hype=%d, cinematic=%d, hasEmbeddings=%b}",
                    title, artist, genre, hype, cinematic, hasEmbeddings());
        }
    }
}