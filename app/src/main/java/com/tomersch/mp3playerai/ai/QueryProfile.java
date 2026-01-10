package com.tomersch.mp3playerai.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class QueryProfile {
    public final Map<String, Integer> moods;   // 0..100
    public final List<String> keywords;        // tokens for matching title/genre/tags/artist
    public final float topPercent;             // e.g. 0.05 = top 5%
    public final float temperature;            // >0, higher = more random
    public final int avoidRecentMinutes;       // avoid recently played window

    public QueryProfile(
            Map<String, Integer> moods,
            List<String> keywords,
            float topPercent,
            float temperature,
            int avoidRecentMinutes
    ) {
        this.moods = (moods != null) ? moods : new HashMap<>();
        this.keywords = (keywords != null) ? keywords : new ArrayList<>();
        this.topPercent = topPercent;
        this.temperature = temperature;
        this.avoidRecentMinutes = avoidRecentMinutes;
    }

    public static QueryProfile defaults() {
        Map<String, Integer> moods = new HashMap<>();
        moods.put("hype", 50);
        moods.put("aggressive", 50);
        moods.put("melodic", 50);
        moods.put("atmospheric", 50);
        moods.put("cinematic", 50);
        moods.put("rhythmic", 50);

        return new QueryProfile(moods, new ArrayList<>(), 0.05f, 0.55f, 30);
    }
}
