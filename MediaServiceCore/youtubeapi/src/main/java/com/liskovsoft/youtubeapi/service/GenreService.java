package com.liskovsoft.youtubeapi.service;

import android.content.Context;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Service to store and retrieve movie genre information
 * Now with SQLite persistence to survive app restarts
 */
public class GenreService {
    private static final String TAG = GenreService.class.getSimpleName();
    private static GenreService sInstance;
    
    // Map to store video ID -> primary genre (in-memory cache)
    private final ConcurrentHashMap<String, String> sVideoGenreMap = new ConcurrentHashMap<>();
    private Context mContext; // For SQLite access
    
    private GenreService() {}
    
    public static GenreService instance() {
        if (sInstance == null) {
            sInstance = new GenreService();
        }
        return sInstance;
    }
    
    /**
     * Set context for SQLite access (called from Android app)
     */
    public void setContext(Context context) {
        mContext = context;
    }
    
    /**
     * Store the primary genre for a video (in both memory and SQLite)
     */
    public void storeVideoGenre(String videoId, String primaryGenre) {
        if (videoId != null && primaryGenre != null && !primaryGenre.isEmpty()) {
            // Store in memory for fast access
            sVideoGenreMap.put(videoId, primaryGenre);
            System.out.println("GenreService: Stored genre '" + primaryGenre + "' for video ID: " + videoId);
            
            // Store in SQLite for persistence
            if (mContext != null) {
                try {
                    Class<?> tmdbCacheClass = Class.forName("com.liskovsoft.smartyoutubetv2.tv.services.TMDBDataCache");
                    Object cacheInstance = tmdbCacheClass.getMethod("instance", Context.class).invoke(null, mContext);
                    tmdbCacheClass.getMethod("storeGenre", String.class, String.class).invoke(cacheInstance, videoId, primaryGenre);
                } catch (Exception e) {
                    System.out.println("GenreService: Could not store genre in SQLite: " + e.getMessage());
                }
            }
        } else {
            System.out.println("GenreService: Invalid data - videoId: " + videoId + ", genre: " + primaryGenre);
        }
    }
    
    /**
     * Get the primary genre for a video (check both memory and SQLite)
     */
    public String getVideoGenre(String videoId) {
        if (videoId == null) {
            return null;
        }
        
        // First try in-memory cache
        String genre = sVideoGenreMap.get(videoId);
        
        // If not in memory, try SQLite persistent cache
        if (genre == null && mContext != null) {
            try {
                Class<?> tmdbCacheClass = Class.forName("com.liskovsoft.smartyoutubetv2.tv.services.TMDBDataCache");
                Object cacheInstance = tmdbCacheClass.getMethod("instance", Context.class).invoke(null, mContext);
                genre = (String) tmdbCacheClass.getMethod("getGenre", String.class).invoke(cacheInstance, videoId);
                
                // If found in SQLite, store in memory for faster future access
                if (genre != null) {
                    sVideoGenreMap.put(videoId, genre);
                    System.out.println("GenreService: Loaded genre '" + genre + "' from SQLite for video ID: " + videoId);
                }
            } catch (Exception e) {
                System.out.println("GenreService: Could not load genre from SQLite: " + e.getMessage());
            }
        }
        
        System.out.println("GenreService: Retrieved genre '" + genre + "' for video ID: " + videoId);
        return genre;
    }
    
    /**
     * Check if we have genre data for a video
     */
    public boolean hasGenreData(String videoId) {
        return videoId != null && sVideoGenreMap.containsKey(videoId);
    }
    
    /**
     * Get all video IDs that have genre data
     */
    public List<String> getVideosWithGenreData() {
        return new ArrayList<>(sVideoGenreMap.keySet());
    }
    
    /**
     * Clear all stored genre data
     */
    public void clearAll() {
        sVideoGenreMap.clear();
    }
}