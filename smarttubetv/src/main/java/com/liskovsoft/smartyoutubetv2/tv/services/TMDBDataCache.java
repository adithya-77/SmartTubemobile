package com.liskovsoft.smartyoutubetv2.tv.services;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;
import android.util.Log;
import java.util.concurrent.TimeUnit;

/**
 * Persistent cache for TMDB movie data to reduce API calls and improve performance
 */
public class TMDBDataCache {
    private static final String TAG = "TMDBDataCache";
    private static final String DATABASE_NAME = "tmdb_cache.db";
    private static final int DATABASE_VERSION = 3;
    private static TMDBDataCache sInstance;
    
    private final Context mContext;
    private final DatabaseHelper mDbHelper;
    
    // Cache expiry: 1 month (30 days)
    private static final long CACHE_EXPIRY_MS = TimeUnit.DAYS.toMillis(30);
    
    private TMDBDataCache(Context context) {
        mContext = context.getApplicationContext();
        mDbHelper = new DatabaseHelper(mContext);
        
        // Clean up expired entries on initialization (runs once per app start)
        clearExpiredEntries();
    }
    
    public static synchronized TMDBDataCache instance(Context context) {
        if (sInstance == null) {
            sInstance = new TMDBDataCache(context);
        }
        return sInstance;
    }
    
    /**
     * Store poster URL for a video
     */
    public void storePosterUrl(String videoId, String posterUrl) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            db.execSQL(
                "INSERT OR REPLACE INTO poster_cache (video_id, poster_url, backdrop_url, timestamp) VALUES (?, ?, ?, ?)",
                new Object[]{videoId, posterUrl, null, System.currentTimeMillis()}
            );
        } catch (Exception e) {
            Log.e(TAG, "Error storing poster URL for " + videoId, e);
        } finally {
            db.close();
        }
    }
    
    /**
     * Store backdrop URL for a video
     */
    public void storeBackdropUrl(String videoId, String backdropUrl) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            // Check if record exists
            Cursor cursor = db.rawQuery(
                "SELECT video_id FROM poster_cache WHERE video_id = ?",
                new String[]{videoId}
            );
            
            if (cursor.moveToFirst()) {
                // Update existing record
                db.execSQL(
                    "UPDATE poster_cache SET backdrop_url = ?, timestamp = ? WHERE video_id = ?",
                    new Object[]{backdropUrl, System.currentTimeMillis(), videoId}
                );
            } else {
                // Create new record with backdrop only
                db.execSQL(
                    "INSERT OR REPLACE INTO poster_cache (video_id, poster_url, backdrop_url, timestamp) VALUES (?, ?, ?, ?)",
                    new Object[]{videoId, null, backdropUrl, System.currentTimeMillis()}
                );
            }
            cursor.close();
        } catch (Exception e) {
            Log.e(TAG, "Error storing backdrop URL for " + videoId, e);
        } finally {
            db.close();
        }
    }
    
    /**
     * Get poster URL for a video (returns null if expired or not found)
     */
    public String getPosterUrl(String videoId) {
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String posterUrl = null;
        try (Cursor cursor = db.rawQuery(
            "SELECT poster_url, timestamp FROM poster_cache WHERE video_id = ?",
            new String[]{videoId}
        )) {
            if (cursor.moveToFirst()) {
                long timestamp = cursor.getLong(1);
                long age = System.currentTimeMillis() - timestamp;
                
                if (age < CACHE_EXPIRY_MS) {
                    posterUrl = cursor.getString(0);
                    Log.d(TAG, "Cache hit for " + videoId + " (age: " + (age / 1000) + "s)");
                } else {
                    Log.d(TAG, "Cache expired for " + videoId + " (age: " + (age / 1000) + "s)");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting poster URL for " + videoId, e);
        } finally {
            db.close();
        }
        return posterUrl;
    }
    
    /**
     * Get backdrop URL for a video (returns null if expired or not found)
     */
    public String getBackdropUrl(String videoId) {
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String backdropUrl = null;
        try (Cursor cursor = db.rawQuery(
            "SELECT backdrop_url, timestamp FROM poster_cache WHERE video_id = ?",
            new String[]{videoId}
        )) {
            if (cursor.moveToFirst()) {
                long timestamp = cursor.getLong(1);
                long age = System.currentTimeMillis() - timestamp;
                
                if (age < CACHE_EXPIRY_MS) {
                    backdropUrl = cursor.getString(0);
                    if (backdropUrl != null && !backdropUrl.isEmpty()) {
                        Log.d(TAG, "Backdrop cache hit for " + videoId);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting backdrop URL for " + videoId, e);
        } finally {
            db.close();
        }
        return backdropUrl;
    }
    
    /**
     * Store genre for a video
     */
    public void storeGenre(String videoId, String genre) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            db.execSQL(
                "INSERT OR REPLACE INTO genre_cache (video_id, genre, timestamp) VALUES (?, ?, ?)",
                new Object[]{videoId, genre, System.currentTimeMillis()}
            );
        } catch (Exception e) {
            Log.e(TAG, "Error storing genre for " + videoId, e);
        } finally {
            db.close();
        }
    }
    
    /**
     * Get genre for a video
     */
    public String getGenre(String videoId) {
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String genre = null;
        try (Cursor cursor = db.rawQuery(
            "SELECT genre, timestamp FROM genre_cache WHERE video_id = ?",
            new String[]{videoId}
        )) {
            if (cursor.moveToFirst()) {
                long timestamp = cursor.getLong(1);
                long age = System.currentTimeMillis() - timestamp;
                
                if (age < CACHE_EXPIRY_MS) {
                    genre = cursor.getString(0);
                    Log.d(TAG, "Genre cache hit for " + videoId);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting genre for " + videoId, e);
        } finally {
            db.close();
        }
        return genre;
    }
    
    /**
     * Store detailed movie information
     */
    public void storeDetailedMovieInfo(String videoId, com.liskovsoft.smartyoutubetv2.tv.services.TMDBDetailedMovieInfo detailedInfo) {
        if (videoId == null || detailedInfo == null) return;
        
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            db.execSQL(
                "INSERT OR REPLACE INTO detailed_movie_info_cache (" +
                "video_id, title, overview, poster_url, backdrop_url, rating, release_date, " +
                "runtime, status, tagline, genres, production_companies, production_countries, " +
                "spoken_languages, cast, crew, director, writer, certification, timestamp" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new Object[]{
                    videoId, 
                    detailedInfo.title,
                    detailedInfo.overview,
                    detailedInfo.posterUrl,
                    detailedInfo.backdropUrl,
                    detailedInfo.rating,
                    detailedInfo.releaseDate,
                    detailedInfo.runtime,
                    detailedInfo.status,
                    detailedInfo.tagline,
                    detailedInfo.genres != null ? String.join(",", detailedInfo.genres) : "",
                    detailedInfo.productionCompanies != null ? String.join(",", detailedInfo.productionCompanies) : "",
                    detailedInfo.productionCountries != null ? String.join(",", detailedInfo.productionCountries) : "",
                    detailedInfo.spokenLanguages != null ? String.join(",", detailedInfo.spokenLanguages) : "",
                    detailedInfo.cast != null ? String.join(",", detailedInfo.cast) : "",
                    detailedInfo.crew != null ? String.join(",", detailedInfo.crew) : "",
                    detailedInfo.director,
                    detailedInfo.writer,
                    detailedInfo.certification,
                    System.currentTimeMillis()
                }
            );
            Log.d(TAG, "Stored detailed movie info for " + videoId);
        } catch (Exception e) {
            Log.e(TAG, "Error storing detailed movie info", e);
        } finally {
            db.close();
        }
    }
    
    /**
     * Get detailed movie information from cache
     */
    public com.liskovsoft.smartyoutubetv2.tv.services.TMDBDetailedMovieInfo getDetailedMovieInfo(String videoId) {
        if (videoId == null) return null;
        
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
            "SELECT * FROM detailed_movie_info_cache WHERE video_id = ?",
            new String[]{videoId}
        )) {
            if (cursor.moveToFirst()) {
                long timestamp = cursor.getLong(cursor.getColumnIndex("timestamp"));
                long age = System.currentTimeMillis() - timestamp;
                
                if (age < CACHE_EXPIRY_MS) {
                    // Parse the data and return TMDBDetailedMovieInfo object
                    java.util.List<String> genres = new java.util.ArrayList<>();
                    String genresStr = cursor.getString(cursor.getColumnIndex("genres"));
                    if (genresStr != null && !genresStr.isEmpty()) {
                        genres = java.util.Arrays.asList(genresStr.split(","));
                    }
                    
                    java.util.List<String> productionCompanies = new java.util.ArrayList<>();
                    String companiesStr = cursor.getString(cursor.getColumnIndex("production_companies"));
                    if (companiesStr != null && !companiesStr.isEmpty()) {
                        productionCompanies = java.util.Arrays.asList(companiesStr.split(","));
                    }
                    
                    java.util.List<String> productionCountries = new java.util.ArrayList<>();
                    String countriesStr = cursor.getString(cursor.getColumnIndex("production_countries"));
                    if (countriesStr != null && !countriesStr.isEmpty()) {
                        productionCountries = java.util.Arrays.asList(countriesStr.split(","));
                    }
                    
                    java.util.List<String> spokenLanguages = new java.util.ArrayList<>();
                    String languagesStr = cursor.getString(cursor.getColumnIndex("spoken_languages"));
                    if (languagesStr != null && !languagesStr.isEmpty()) {
                        spokenLanguages = java.util.Arrays.asList(languagesStr.split(","));
                    }
                    
                    java.util.List<String> cast = new java.util.ArrayList<>();
                    String castStr = cursor.getString(cursor.getColumnIndex("cast"));
                    if (castStr != null && !castStr.isEmpty()) {
                        cast = java.util.Arrays.asList(castStr.split(","));
                    }
                    
                    java.util.List<String> crew = new java.util.ArrayList<>();
                    String crewStr = cursor.getString(cursor.getColumnIndex("crew"));
                    if (crewStr != null && !crewStr.isEmpty()) {
                        crew = java.util.Arrays.asList(crewStr.split(","));
                    }
                    
                    return new com.liskovsoft.smartyoutubetv2.tv.services.TMDBDetailedMovieInfo(
                        cursor.getString(cursor.getColumnIndex("title")),
                        cursor.getString(cursor.getColumnIndex("overview")),
                        cursor.getString(cursor.getColumnIndex("poster_url")),
                        cursor.getString(cursor.getColumnIndex("backdrop_url")),
                        cursor.getString(cursor.getColumnIndex("rating")),
                        cursor.getString(cursor.getColumnIndex("release_date")),
                        cursor.getString(cursor.getColumnIndex("runtime")),
                        cursor.getString(cursor.getColumnIndex("status")),
                        cursor.getString(cursor.getColumnIndex("tagline")),
                        genres,
                        productionCompanies,
                        productionCountries,
                        spokenLanguages,
                        cast,
                        crew,
                        cursor.getString(cursor.getColumnIndex("director")),
                        cursor.getString(cursor.getColumnIndex("writer")),
                        cursor.getString(cursor.getColumnIndex("certification"))
                    );
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting detailed movie info", e);
        } finally {
            db.close();
        }
        return null;
    }
    
    /**
     * Clear expired entries to free up space
     * Called automatically on app start to keep database clean
     */
    public void clearExpiredEntries() {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            long expireThreshold = System.currentTimeMillis() - CACHE_EXPIRY_MS;
            int deleted = db.delete("poster_cache", "timestamp < ?", new String[]{String.valueOf(expireThreshold)});
            int deleted2 = db.delete("genre_cache", "timestamp < ?", new String[]{String.valueOf(expireThreshold)});
            int deleted3 = db.delete("detailed_movie_info_cache", "timestamp < ?", new String[]{String.valueOf(expireThreshold)});
            
            if (deleted > 0 || deleted2 > 0 || deleted3 > 0) {
                Log.i(TAG, "Cleanup: Removed " + (deleted + deleted2 + deleted3) + " expired cache entries (older than 30 days)");
            } else {
                Log.d(TAG, "Cleanup: No expired entries to remove (all data is fresh)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing expired entries", e);
        } finally {
            db.close();
        }
    }
    
    /**
     * Database helper class
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        
        @Override
        public void onCreate(SQLiteDatabase db) {
            // Create poster cache table
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS poster_cache (" +
                "video_id TEXT PRIMARY KEY, " +
                "poster_url TEXT, " +
                "backdrop_url TEXT, " +
                "timestamp INTEGER NOT NULL" +
                ")"
            );
            
            // Create genre cache table
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS genre_cache (" +
                "video_id TEXT PRIMARY KEY, " +
                "genre TEXT NOT NULL, " +
                "timestamp INTEGER NOT NULL" +
                ")"
            );
            
            // Create detailed movie info cache table
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS detailed_movie_info_cache (" +
                "video_id TEXT PRIMARY KEY, " +
                "title TEXT, " +
                "overview TEXT, " +
                "poster_url TEXT, " +
                "backdrop_url TEXT, " +
                "rating TEXT, " +
                "release_date TEXT, " +
                "runtime TEXT, " +
                "status TEXT, " +
                "tagline TEXT, " +
                "genres TEXT, " +
                "production_companies TEXT, " +
                "production_countries TEXT, " +
                "spoken_languages TEXT, " +
                "cast TEXT, " +
                "crew TEXT, " +
                "director TEXT, " +
                "writer TEXT, " +
                "certification TEXT, " +
                "timestamp INTEGER NOT NULL" +
                ")"
            );
            
            // Create indices for faster lookups
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_poster_timestamp ON poster_cache(timestamp)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_genre_timestamp ON genre_cache(timestamp)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_detailed_timestamp ON detailed_movie_info_cache(timestamp)");
        }
        
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                // Add backdrop_url column to existing poster_cache table
                try {
                    db.execSQL("ALTER TABLE poster_cache ADD COLUMN backdrop_url TEXT");
                    Log.i(TAG, "Database upgraded: Added backdrop_url column");
                } catch (Exception e) {
                    Log.w(TAG, "Could not add backdrop_url column (may already exist): " + e.getMessage());
                    // If column already exists, that's fine
                }
            }
            if (oldVersion < 3) {
                // Create detailed_movie_info_cache table for version 3
                try {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS detailed_movie_info_cache (" +
                        "video_id TEXT PRIMARY KEY, " +
                        "title TEXT, " +
                        "overview TEXT, " +
                        "poster_url TEXT, " +
                        "backdrop_url TEXT, " +
                        "rating TEXT, " +
                        "release_date TEXT, " +
                        "runtime TEXT, " +
                        "status TEXT, " +
                        "tagline TEXT, " +
                        "genres TEXT, " +
                        "production_companies TEXT, " +
                        "production_countries TEXT, " +
                        "spoken_languages TEXT, " +
                        "cast TEXT, " +
                        "crew TEXT, " +
                        "director TEXT, " +
                        "writer TEXT, " +
                        "certification TEXT, " +
                        "timestamp INTEGER NOT NULL" +
                        ")"
                    );
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_detailed_timestamp ON detailed_movie_info_cache(timestamp)");
                    Log.i(TAG, "Database upgraded: Added detailed_movie_info_cache table");
                } catch (Exception e) {
                    Log.w(TAG, "Could not add detailed_movie_info_cache table: " + e.getMessage());
                }
            }
        }
    }
}
