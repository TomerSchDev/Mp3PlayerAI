package com.tomersch.mp3playerai.ai;

public enum DBColumns {
    PATH("path"),
    TAGS("tags"),

    GENRE("genre"),
    YEAR("year"),
    HYPE("hype"),
    AGGRESSIVE("aggressive"),
    MELODIC("melodic"),
    ATMOSPHERIC("atmospheric"),
    CINEMATIC("cinematic"),
    RHYTHMIC("rhythmic"),
    TITLE("title"),
    ARTIST("artist"),
    AUDIO_BLOB("audio_blob"),
    META_BLOB("meta_blob"),
    FILE_NAME("filename");
    private final String name;

    DBColumns(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static final DBColumns[] columns = new DBColumns[]{PATH, TAGS, GENRE, YEAR, HYPE, AGGRESSIVE, MELODIC, ATMOSPHERIC, CINEMATIC, RHYTHMIC, TITLE, ARTIST, AUDIO_BLOB, META_BLOB, FILE_NAME};
    public static int ColumnToIndex(DBColumns column) {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i] == column) return i;
        }
        return -1;
    }
}
