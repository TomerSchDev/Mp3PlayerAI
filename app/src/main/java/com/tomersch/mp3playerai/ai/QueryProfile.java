package com.tomersch.mp3playerai.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured representation of a user's music query after LLM parsing.
 * 
 * This is what the LLM converts natural language into.
 * Example: "upbeat anime openings" → QueryProfile with genres=["anime","jrock"], moods={energetic:80, melodic:70}
 */
public class QueryProfile {
    
    // Text keywords extracted from query (e.g., "chill", "workout", "study")
    public List<String> keywords = new ArrayList<>();
    
    // Genre hints (e.g., "rock", "electronic", "anime")
    public List<String> genres = new ArrayList<>();
    
    // Mood scores 0-100 (hype, aggressive, melodic, atmospheric, cinematic, rhythmic)
    public Map<String, Integer> moods = new HashMap<>();
    
    // How many minutes to avoid recently played songs (default: 30)
    public int avoidRecentMinutes = 30;
    
    // Top what % of songs to pick from randomly (default: 0.05 = top 5%)
    public float topPercent = 0.05f;
    
    // Temperature for weighted random selection (higher = more random)
    // 0.5 = conservative, 1.0 = balanced, 1.5 = very random
    public float temperature = 1.0f;
    
    /**
     * Create default profile (neutral preferences)
     */
    public QueryProfile() {
        // Initialize with neutral mood scores
        moods.put("hype", 50);
        moods.put("aggressive", 50);
        moods.put("melodic", 50);
        moods.put("atmospheric", 50);
        moods.put("cinematic", 50);
        moods.put("rhythmic", 50);
    }
    
    /**
     * Create profile from LLM JSON output
     */
    public static QueryProfile fromLlmJson(String json) {
        // Simple JSON parsing (you can use Gson if you want)
        QueryProfile profile = new QueryProfile();
        
        try {
            // Very basic JSON parsing (works for our simple structure)
            // Format: {"genres":["rock","metal"],"moods":{"energetic":80,"aggressive":70}}
            
            // Extract genres
            if (json.contains("\"genres\"")) {
                int start = json.indexOf("[", json.indexOf("\"genres\""));
                int end = json.indexOf("]", start);
                if (start > 0 && end > start) {
                    String genresStr = json.substring(start + 1, end);
                    String[] parts = genresStr.split(",");
                    for (String part : parts) {
                        String genre = part.trim().replaceAll("[\"\']", "");
                        if (!genre.isEmpty()) {
                            profile.genres.add(genre);
                            profile.keywords.add(genre); // Also add to keywords
                        }
                    }
                }
            }
            
            // Extract mood scores
            if (json.contains("\"moods\"")) {
                String[] moodKeys = {"energetic", "hype", "aggressive", "melodic", 
                                    "atmospheric", "cinematic", "rhythmic"};
                
                for (String mood : moodKeys) {
                    if (json.contains("\"" + mood + "\"")) {
                        int start = json.indexOf(":", json.indexOf("\"" + mood + "\""));
                        int end = json.indexOf(",", start);
                        if (end == -1) end = json.indexOf("}", start);
                        
                        if (start > 0 && end > start) {
                            try {
                                String valueStr = json.substring(start + 1, end).trim();
                                // Remove any decimal points or extra characters
                                valueStr = valueStr.replaceAll("[^0-9]", "");
                                if (!valueStr.isEmpty()) {
                                    int value = Integer.parseInt(valueStr);
                                    // Normalize to 0-100 if needed
                                    if (value > 100) value = 100;
                                    if (value < 0) value = 0;
                                    
                                    // Map to our standard mood names
                                    String moodKey = mood;
                                    if (mood.equals("energetic")) moodKey = "hype";
                                    
                                    profile.moods.put(moodKey, value);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
            
            // Extract keywords (words that aren't in quotes as values)
            // Look for descriptive words like "chill", "intense", "upbeat"
            String lowerJson = json.toLowerCase();
            String[] commonDescriptors = {"chill", "relax", "calm", "peaceful",
                                         "energetic", "intense", "powerful", "aggressive",
                                         "happy", "sad", "dark", "bright",
                                         "fast", "slow", "upbeat", "mellow"};
            
            for (String descriptor : commonDescriptors) {
                if (lowerJson.contains(descriptor) && !profile.keywords.contains(descriptor)) {
                    profile.keywords.add(descriptor);
                }
            }
            
        } catch (Exception e) {
            // If parsing fails, return default profile
            android.util.Log.e("QueryProfile", "Failed to parse LLM JSON: " + e.getMessage());
        }
        
        return profile;
    }
    
    @Override
    public String toString() {
        return "QueryProfile{" +
                "keywords=" + keywords +
                ", genres=" + genres +
                ", moods=" + moods +
                ", topPercent=" + topPercent +
                ", temperature=" + temperature +
                '}';
    }
}
