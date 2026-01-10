package com.tomersch.mp3playerai.ai;

import android.content.Context;
import android.util.Log;

import com.tomersch.mp3playerai.models.Song;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced song categorizer that analyzes file paths and metadata
 * Similar to the Python Llama approach but using smart heuristics
 */
public class AdvancedSongCategorizer {
    private static final String TAG = "AdvancedSongCategorizer";
    
    private Context context;
    
    public AdvancedSongCategorizer(Context context) {
        this.context = context;
    }
    
    /**
     * Categorize a song by analyzing its full path structure
     * This mimics the Python Llama approach but uses pattern matching
     */
    public CategorizedSong categorizeSong(Song song) {
        Log.d(TAG, "Analyzing song path: " + song.getPath());
        
        CategorizedSong result = new CategorizedSong();
        result.song = song;
        
        // Extract path components
        File file = new File(song.getPath());
        String filename = file.getName();
        String parentFolder = file.getParentFile() != null ? file.getParentFile().getName() : "";
        String fullPath = song.getPath();
        
        // Extract metadata using smart path analysis
        PathMetadata metadata = analyzePathStructure(filename, parentFolder, fullPath);
        
        // Use extracted metadata if better than existing
        if (metadata.title != null && !metadata.title.equals("Unknown")) {
            result.song.setTitle(metadata.title);
        }
        if (metadata.artist != null && !metadata.artist.equals("Unknown")) {
            result.song.setArtist(metadata.artist);
        }
        
        result.genre = metadata.genre != null ? metadata.genre : "Unknown";
        result.year = metadata.year;
        
        // Analyze mood based on ALL available information
        result.moodScores = analyzeMoodFromPath(
            metadata.title != null ? metadata.title : filename,
            metadata.artist != null ? metadata.artist : song.getArtist(),
            metadata.genre,
            parentFolder,
            fullPath
        );
        
        // Generate tags
        result.tags = generateTags(metadata, parentFolder);
        
        Log.d(TAG, "Categorized: Title=" + metadata.title + ", Artist=" + metadata.artist + 
              ", Genre=" + metadata.genre);
        
        return result;
    }
    
    /**
     * Analyze path structure to extract metadata
     * Mimics: "File name: {name}, Parent folder: {parent}, Full path: {path}"
     */
    private PathMetadata analyzePathStructure(String filename, String parentFolder, String fullPath) {
        PathMetadata meta = new PathMetadata();
        
        // Remove file extension
        String nameWithoutExt = filename.replaceAll("\\.(mp3|flac|m4a|wav|ogg)$", "");
        
        // Pattern 1: "Artist - Title.mp3"
        Pattern pattern1 = Pattern.compile("^(.+?)\\s*-\\s*(.+?)$");
        Matcher matcher1 = pattern1.matcher(nameWithoutExt);
        if (matcher1.find()) {
            meta.artist = cleanString(matcher1.group(1));
            meta.title = cleanString(matcher1.group(2));
            Log.d(TAG, "Matched pattern 'Artist - Title'");
        }
        
        // Pattern 2: "01. Title.mp3" or "1) Title.mp3"
        Pattern pattern2 = Pattern.compile("^\\d+[.)\\s]+(.+)$");
        Matcher matcher2 = pattern2.matcher(nameWithoutExt);
        if (matcher2.find() && meta.title == null) {
            meta.title = cleanString(matcher2.group(1));
            Log.d(TAG, "Matched pattern 'Number. Title'");
        }
        
        // Pattern 3: "[Artist] Title.mp3"
        Pattern pattern3 = Pattern.compile("^\\[(.+?)\\]\\s*(.+)$");
        Matcher matcher3 = pattern3.matcher(nameWithoutExt);
        if (matcher3.find() && meta.artist == null) {
            meta.artist = cleanString(matcher3.group(1));
            meta.title = cleanString(matcher3.group(2));
            Log.d(TAG, "Matched pattern '[Artist] Title'");
        }
        
        // If title still null, use cleaned filename
        if (meta.title == null) {
            meta.title = cleanString(nameWithoutExt);
        }
        
        // Try to extract artist from parent folder if not found
        if (meta.artist == null || meta.artist.equals("Unknown")) {
            meta.artist = extractArtistFromFolder(parentFolder);
        }
        
        // Try to extract genre from path
        meta.genre = extractGenreFromPath(fullPath, parentFolder);
        
        // Try to extract year from path or filename
        meta.year = extractYear(fullPath + " " + filename);
        
        return meta;
    }
    
    /**
     * Extract artist from folder name
     * Example: "EPIC_ The Musical - Animatics [IN ORDER]" → "EPIC The Musical"
     */
    private String extractArtistFromFolder(String folderName) {
        if (folderName == null || folderName.isEmpty()) {
            return "Unknown";
        }
        
        // Remove common suffixes
        String cleaned = folderName
            .replaceAll("\\s*[-–—]\\s*Animatics.*", "")
            .replaceAll("\\s*\\[.*?\\]", "")
            .replaceAll("\\s*\\(.*?\\)", "")
            .replaceAll("_", " ")
            .trim();
        
        // If it looks like an artist name, use it
        if (!cleaned.isEmpty() && cleaned.length() > 2) {
            return cleaned;
        }
        
        return "Unknown";
    }
    
    /**
     * Extract genre from path or folder names
     */
    private String extractGenreFromPath(String fullPath, String parentFolder) {
        String combined = (fullPath + " " + parentFolder).toLowerCase();
        
        // Check for common genre keywords in path
        if (combined.contains("metal")) return "Metal";
        if (combined.contains("rock")) return "Rock";
        if (combined.contains("pop")) return "Pop";
        if (combined.contains("jazz")) return "Jazz";
        if (combined.contains("classical")) return "Classical";
        if (combined.contains("electronic") || combined.contains("edm")) return "Electronic";
        if (combined.contains("hip hop") || combined.contains("rap")) return "Hip Hop";
        if (combined.contains("country")) return "Country";
        if (combined.contains("blues")) return "Blues";
        if (combined.contains("reggae")) return "Reggae";
        if (combined.contains("folk")) return "Folk";
        if (combined.contains("soundtrack") || combined.contains("ost")) return "Soundtrack";
        if (combined.contains("ambient")) return "Ambient";
        if (combined.contains("indie")) return "Indie";
        
        return "Unknown";
    }
    
    /**
     * Extract year from path or filename
     * Looks for 4-digit numbers between 1900-2099
     */
    private int extractYear(String text) {
        Pattern yearPattern = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");
        Matcher matcher = yearPattern.matcher(text);
        
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        
        return 0;
    }
    
    /**
     * Analyze mood from ALL path components
     * This is where we apply the same logic as your Python categorization
     */
    private Map<String, Integer> analyzeMoodFromPath(
            String title, String artist, String genre, String parentFolder, String fullPath) {
        
        Map<String, Integer> scores = getDefaultMoodScores();
        
        // Combine ALL available text for analysis
        String combined = (title + " " + artist + " " + genre + " " + parentFolder + " " + fullPath)
            .toLowerCase();
        
        // Energy / Hype analysis
        if (containsAny(combined, "upbeat", "energetic", "party", "dance", "fast", "pump", "hype", 
                        "power", "energy", "uplifting", "exciting", "intense")) {
            adjustScore(scores, "hype", +30);
        }
        if (containsAny(combined, "slow", "calm", "quiet", "soft", "gentle", "peace", "relax", 
                        "chill", "mellow", "tranquil")) {
            adjustScore(scores, "hype", -30);
        }
        
        // Aggressive / Intensity
        if (containsAny(combined, "metal", "hardcore", "aggressive", "intense", "heavy", "brutal", 
                        "rage", "angry", "fierce", "wild", "powerful")) {
            adjustScore(scores, "aggressive", +40);
        }
        if (containsAny(combined, "love", "romantic", "sweet", "tender", "gentle", "soft")) {
            adjustScore(scores, "aggressive", -25);
        }
        
        // Melodic
        if (containsAny(combined, "melodic", "harmony", "vocal", "sing", "beautiful", "melody", 
                        "lyrical", "tune", "song")) {
            adjustScore(scores, "melodic", +35);
        }
        if (containsAny(combined, "instrumental", "techno", "beat", "bass")) {
            adjustScore(scores, "melodic", -15);
        }
        
        // Atmospheric
        if (containsAny(combined, "ambient", "atmospheric", "space", "dreamy", "ethereal", "chill", 
                        "cosmic", "vast", "expansive", "airy")) {
            adjustScore(scores, "atmospheric", +40);
        }
        
        // Cinematic
        if (containsAny(combined, "epic", "cinematic", "orchestral", "soundtrack", "score", "dramatic", 
                        "ost", "theme", "musical", "symphony")) {
            adjustScore(scores, "cinematic", +45);
        }
        
        // Rhythmic
        if (containsAny(combined, "rhythm", "beat", "drum", "bass", "groove", "funk", "hip hop", 
                        "rap", "percussion", "dance")) {
            adjustScore(scores, "rhythmic", +35);
        }
        
        // Genre-specific adjustments (like your Python code)
        if (genre != null) {
            applyGenreAdjustments(scores, genre);
        }
        
        // Parent folder context (important for themed collections)
        applyFolderContext(scores, parentFolder);
        
        return scores;
    }
    
    /**
     * Apply genre-based score adjustments
     */
    private void applyGenreAdjustments(Map<String, Integer> scores, String genre) {
        String genreLower = genre.toLowerCase();
        
        if (genreLower.contains("metal") || genreLower.contains("rock")) {
            adjustScore(scores, "hype", +20);
            adjustScore(scores, "aggressive", +25);
        }
        if (genreLower.contains("classical") || genreLower.contains("orchestral")) {
            adjustScore(scores, "cinematic", +30);
            adjustScore(scores, "melodic", +25);
        }
        if (genreLower.contains("electronic") || genreLower.contains("edm")) {
            adjustScore(scores, "hype", +25);
            adjustScore(scores, "rhythmic", +30);
        }
        if (genreLower.contains("ambient") || genreLower.contains("chill")) {
            adjustScore(scores, "atmospheric", +35);
            adjustScore(scores, "hype", -25);
        }
        if (genreLower.contains("jazz") || genreLower.contains("blues")) {
            adjustScore(scores, "melodic", +20);
            adjustScore(scores, "rhythmic", +25);
        }
        if (genreLower.contains("soundtrack") || genreLower.contains("ost")) {
            adjustScore(scores, "cinematic", +40);
        }
    }
    
    /**
     * Apply folder context (like "EPIC_ The Musical")
     */
    private void applyFolderContext(Map<String, Integer> scores, String folderName) {
        if (folderName == null) return;
        
        String folderLower = folderName.toLowerCase();
        
        // Musical theater context
        if (folderLower.contains("musical") || folderLower.contains("broadway")) {
            adjustScore(scores, "cinematic", +35);
            adjustScore(scores, "melodic", +30);
        }
        
        // Epic context
        if (folderLower.contains("epic")) {
            adjustScore(scores, "cinematic", +40);
            adjustScore(scores, "hype", +25);
        }
        
        // Anime/OST context
        if (folderLower.contains("anime") || folderLower.contains("ost") || folderLower.contains("soundtrack")) {
            adjustScore(scores, "cinematic", +35);
        }
    }
    
    /**
     * Adjust score within bounds 0-100
     */
    private void adjustScore(Map<String, Integer> scores, String key, int adjustment) {
        int current = scores.get(key);
        int newValue = Math.max(0, Math.min(100, current + adjustment));
        scores.put(key, newValue);
    }
    
    /**
     * Generate tags from all available metadata
     */
    private String generateTags(PathMetadata metadata, String parentFolder) {
        List<String> tags = new ArrayList<>();
        
        if (metadata.genre != null && !metadata.genre.equals("Unknown")) {
            tags.add(metadata.genre.toLowerCase());
        }
        
        // Add contextual tags from folder
        if (parentFolder != null) {
            String folderLower = parentFolder.toLowerCase();
            if (folderLower.contains("musical")) tags.add("musical");
            if (folderLower.contains("epic")) tags.add("epic");
            if (folderLower.contains("anime")) tags.add("anime");
            if (folderLower.contains("soundtrack")) tags.add("soundtrack");
        }
        
        // Add descriptive tags
        String combined = (metadata.title + " " + metadata.artist + " " + parentFolder).toLowerCase();
        if (containsAny(combined, "love", "heart")) tags.add("romantic");
        if (containsAny(combined, "battle", "fight", "war")) tags.add("battle");
        if (containsAny(combined, "dream", "sky", "fly")) tags.add("dreamy");
        if (containsAny(combined, "dark", "shadow", "night")) tags.add("dark");
        
        return tags.isEmpty() ? "general" : String.join(", ", tags);
    }
    
    /**
     * Clean and normalize strings
     */
    private String cleanString(String str) {
        if (str == null) return "Unknown";
        
        return str
            .replaceAll("_", " ")
            .replaceAll("\\s+", " ")
            .replaceAll("^[\\d.)\\s]+", "")  // Remove leading numbers
            .trim();
    }
    
    /**
     * Get default mood scores (neutral)
     */
    private Map<String, Integer> getDefaultMoodScores() {
        Map<String, Integer> scores = new HashMap<>();
        scores.put("hype", 50);
        scores.put("aggressive", 50);
        scores.put("melodic", 50);
        scores.put("atmospheric", 50);
        scores.put("cinematic", 50);
        scores.put("rhythmic", 50);
        return scores;
    }
    
    /**
     * Check if text contains any keywords
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Path metadata extracted from file structure
     */
    private static class PathMetadata {
        String title;
        String artist;
        String genre;
        int year;
    }
    
    /**
     * Result of song categorization
     */
    public static class CategorizedSong {
        public Song song;
        public Map<String, Integer> moodScores;
        public String genre;
        public int year;
        public String tags;
    }
}
