package com.liskovsoft.smartyoutubetv2.tv.presenters;

import android.content.Context;
import android.content.Intent;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.ui.moviedetails.MovieDetailsActivity;
import com.liskovsoft.smartyoutubetv2.tv.services.TMDBDetailedMovieInfo;
import com.liskovsoft.youtubeapi.service.GenreService;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom presenter that opens movie details page instead of direct playback
 */
public class MovieDetailsVideoActionPresenter {
    
    private final Context mContext;
    private static final ConcurrentHashMap<String, TMDBDetailedMovieInfo> sDetailedMovieInfoMap = new ConcurrentHashMap<>();
    
    public MovieDetailsVideoActionPresenter(Context context) {
        mContext = context;
    }
    
    public static MovieDetailsVideoActionPresenter instance(Context context) {
        return new MovieDetailsVideoActionPresenter(context);
    }
    
    public static void storeDetailedMovieInfo(Context context, String videoId, TMDBDetailedMovieInfo detailedInfo) {
        if (videoId == null || detailedInfo == null) {
            return;
        }

        // Update in-memory cache
        sDetailedMovieInfoMap.put(videoId, detailedInfo);

        // Persist to SQLite for future sessions
        try {
            if (context != null) {
                com.liskovsoft.smartyoutubetv2.tv.services.TMDBDataCache
                        .instance(context.getApplicationContext())
                        .storeDetailedMovieInfo(videoId, detailedInfo);
            }
        } catch (Exception e) {
            android.util.Log.e("MovieDetailsVideoActionPresenter", "Failed to store to SQLite", e);
        }

        // Also store the primary genre in the shared service
        if (detailedInfo.genres != null && !detailedInfo.genres.isEmpty()) {
            String primaryGenre = detailedInfo.genres.get(0);
            GenreService.instance().storeVideoGenre(videoId, primaryGenre);
        }
    }
    
    /**
     * Get the primary genre for a video from stored TMDB data
     */
    public static String getPrimaryGenreForVideo(String videoId) {
        if (videoId == null) {
            return null;
        }
        
        TMDBDetailedMovieInfo detailedInfo = sDetailedMovieInfoMap.get(videoId);
        if (detailedInfo != null && detailedInfo.genres != null && !detailedInfo.genres.isEmpty()) {
            // Return the first genre as the primary genre
            return detailedInfo.genres.get(0);
        }
        
        return null;
    }
    
    /**
     * Get all genres for a video from stored TMDB data
     */
    public static java.util.List<String> getAllGenresForVideo(String videoId) {
        if (videoId == null) {
            return null;
        }
        
        TMDBDetailedMovieInfo detailedInfo = sDetailedMovieInfoMap.get(videoId);
        if (detailedInfo != null) {
            return detailedInfo.genres;
        }
        
        return null;
    }
    
    /**
     * Get detailed movie info for a video ID (for external access)
     */
    public static TMDBDetailedMovieInfo getDetailedMovieInfo(String videoId) {
        if (videoId == null) {
            return null;
        }
        return sDetailedMovieInfoMap.get(videoId);
    }
    
    public void apply(Video item) {
        if (item == null || mContext == null) {
            android.util.Log.e("MovieDetailsVideoActionPresenter", "Item or context is null!");
            return;
        }
        
        android.util.Log.d("MovieDetailsVideoActionPresenter", "Opening movie details for: " + item.getTitle());
        // Open movie details page instead of direct playback
        openMovieDetails(item);
    }
    
    private void openMovieDetails(Video video) {
        if (video == null || mContext == null) {
            return;
        }
        
        Intent intent = new Intent(mContext, MovieDetailsActivity.class);
        
        // Get backdrop URL from SQLite cache if not in memory
        final String backdropUrl;
        String tempBackdrop = video.backdropImageUrl;
        if ((tempBackdrop == null || tempBackdrop.isEmpty()) && video.videoId != null) {
            tempBackdrop = com.liskovsoft.smartyoutubetv2.tv.services.TMDBDataCache.instance(mContext).getBackdropUrl(video.videoId);
        }
        backdropUrl = tempBackdrop;
        
        // Check if we have detailed movie information (FIRST from memory, THEN from SQLite)
        TMDBDetailedMovieInfo detailedInfo = sDetailedMovieInfoMap.get(video.videoId);
        
        // If not in memory, try to load from SQLite cache
        if (detailedInfo == null && video.videoId != null) {
            detailedInfo = com.liskovsoft.smartyoutubetv2.tv.services.TMDBDataCache.instance(mContext).getDetailedMovieInfo(video.videoId);
            // Store in memory for faster access next time
            if (detailedInfo != null) {
                sDetailedMovieInfoMap.put(video.videoId, detailedInfo);
            }
        }
        
        if (detailedInfo != null) {
            // Use detailed TMDB information
            intent.putExtra(MovieDetailsActivity.EXTRA_MOVIE_TITLE, detailedInfo.title);
            intent.putExtra(MovieDetailsActivity.EXTRA_MOVIE_OVERVIEW, detailedInfo.overview);
            intent.putExtra(MovieDetailsActivity.EXTRA_MOVIE_POSTER, detailedInfo.posterUrl);
            intent.putExtra(MovieDetailsActivity.EXTRA_MOVIE_BACKDROP, backdropUrl != null && !backdropUrl.isEmpty() ? backdropUrl : "");
            intent.putExtra(MovieDetailsActivity.EXTRA_MOVIE_RATING, detailedInfo.rating);
            intent.putExtra(MovieDetailsActivity.EXTRA_MOVIE_RELEASE_DATE, detailedInfo.releaseDate);
            intent.putExtra(MovieDetailsActivity.EXTRA_MOVIE_RUNTIME, detailedInfo.runtime);
            intent.putExtra(MovieDetailsActivity.EXTRA_MOVIE_STATUS, detailedInfo.status);
            intent.putExtra(MovieDetailsActivity.EXTRA_MOVIE_TAGLINE, detailedInfo.tagline);
            intent.putExtra(MovieDetailsActivity.EXTRA_MOVIE_GENRES, String.join(", ", detailedInfo.genres));
            intent.putExtra(MovieDetailsActivity.EXTRA_MOVIE_PRODUCTION_COMPANIES, String.join(", ", detailedInfo.productionCompanies));
            intent.putExtra(MovieDetailsActivity.EXTRA_MOVIE_PRODUCTION_COUNTRIES, String.join(", ", detailedInfo.productionCountries));
            intent.putExtra(MovieDetailsActivity.EXTRA_MOVIE_SPOKEN_LANGUAGES, String.join(", ", detailedInfo.spokenLanguages));
            intent.putExtra(MovieDetailsActivity.EXTRA_MOVIE_CERTIFICATION, detailedInfo.certification != null ? detailedInfo.certification : "");

            intent.putExtra(MovieDetailsActivity.EXTRA_VIDEO_ID, video.videoId);
            mContext.startActivity(intent);
            return;
        }

        // No cached details: fetch from TMDB now and open when ready
        final Video finalVideo = video;
        final String[] fetchedBackdrop = new String[1];
        com.liskovsoft.smartyoutubetv2.tv.services.TMDBImageService tmdbService = new com.liskovsoft.smartyoutubetv2.tv.services.TMDBImageService();
        final String title = finalVideo.getTitle();
        final String description = finalVideo.description;

        tmdbService.getMoviePosterByTitle(title, description, new com.liskovsoft.smartyoutubetv2.tv.services.TMDBImageCallback() {
            @Override
            public void onImageUrlReceived(String imageUrl) {
                // not used directly here
            }

            @Override
            public void onBackdropUrlReceived(String backdrop) {
                fetchedBackdrop[0] = backdrop;
            }

            @Override
            public void onMovieDetailsReceived(String t, String overview, String poster, String rating, String releaseDate) {
                Intent i = new Intent(mContext, MovieDetailsActivity.class);
                i.putExtra(MovieDetailsActivity.EXTRA_MOVIE_TITLE, t != null ? t : title);
                i.putExtra(MovieDetailsActivity.EXTRA_MOVIE_OVERVIEW, overview != null ? overview : "No overview available");
                i.putExtra(MovieDetailsActivity.EXTRA_MOVIE_POSTER, poster != null ? poster : (finalVideo.getCardImageUrl() != null ? finalVideo.getCardImageUrl() : ""));
                i.putExtra(MovieDetailsActivity.EXTRA_MOVIE_BACKDROP, fetchedBackdrop[0] != null ? fetchedBackdrop[0] : (backdropUrl != null ? backdropUrl : ""));
                i.putExtra(MovieDetailsActivity.EXTRA_MOVIE_RATING, rating != null ? rating : "N/A");
                i.putExtra(MovieDetailsActivity.EXTRA_MOVIE_RELEASE_DATE, releaseDate != null ? releaseDate : "Unknown");
                i.putExtra(MovieDetailsActivity.EXTRA_MOVIE_RUNTIME, "Unknown");
                i.putExtra(MovieDetailsActivity.EXTRA_MOVIE_STATUS, "Unknown");
                i.putExtra(MovieDetailsActivity.EXTRA_MOVIE_TAGLINE, "");
                i.putExtra(MovieDetailsActivity.EXTRA_MOVIE_GENRES, "");
                i.putExtra(MovieDetailsActivity.EXTRA_MOVIE_PRODUCTION_COMPANIES, "");
                i.putExtra(MovieDetailsActivity.EXTRA_MOVIE_PRODUCTION_COUNTRIES, "");
                i.putExtra(MovieDetailsActivity.EXTRA_MOVIE_SPOKEN_LANGUAGES, "");
                i.putExtra(MovieDetailsActivity.EXTRA_MOVIE_CERTIFICATION, "");
                i.putExtra(MovieDetailsActivity.EXTRA_VIDEO_ID, finalVideo.videoId);
                mContext.startActivity(i);
            }

            @Override
            public void onDetailedMovieInfoReceived(com.liskovsoft.smartyoutubetv2.tv.services.TMDBDetailedMovieInfo info) {
                if (info != null) {
                    // Persist and memoize for future
                    storeDetailedMovieInfo(mContext, finalVideo.videoId, info);
                }
            }
        });
    }
    
    public static void updateMovieCertification(String videoId, String certification) {
        TMDBDetailedMovieInfo detailedInfo = sDetailedMovieInfoMap.get(videoId);
        if (detailedInfo != null) {
            detailedInfo.certification = certification;
        }
    }
}