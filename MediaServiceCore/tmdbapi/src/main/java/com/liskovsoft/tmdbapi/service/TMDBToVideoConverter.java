package com.liskovsoft.tmdbapi.service;

import com.liskovsoft.tmdbapi.models.TMDBMovie;
import com.liskovsoft.tmdbapi.models.TMDBMovieDetails;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import java.util.ArrayList;
import java.util.List;

public class TMDBToVideoConverter {
    
    public static Video convertToVideo(TMDBMovie movie) {
        if (movie == null) {
            return null;
        }
        
        Video video = new Video();
        
        // Basic info
        video.title = movie.getTitle();
        video.secondTitle = movie.getOverview();
        video.category = "Movie";
        video.itemType = 1; // Media item type
        video.isMovie = true;
        
        // Generate a fake video ID for YouTube search
        video.videoId = generateYouTubeVideoId(movie.getTitle(), movie.getReleaseDate());
        
        // Images
        video.cardImageUrl = movie.getPosterPath() != null ? 
            "https://image.tmdb.org/t/p/w500" + movie.getPosterPath() : null;
        video.bgImageUrl = movie.getBackdropPath() != null ? 
            "https://image.tmdb.org/t/p/w1280" + movie.getBackdropPath() : null;
        
        // Author/Studio info
        video.author = "TMDB Movie";
        
        // Badge with rating
        if (movie.getVoteAverage() > 0) {
            video.badge = String.format("%.1f★", movie.getVoteAverage());
        }
        
        // Additional metadata
        video.id = movie.getId();
        video.description = movie.getOverview();
        
        return video;
    }
    
    public static Video convertToVideo(TMDBMovieDetails movieDetails) {
        if (movieDetails == null) {
            return null;
        }
        
        Video video = convertToVideo((TMDBMovie) movieDetails);
        
        if (video != null) {
            // Add additional details
            if (movieDetails.getRuntime() > 0) {
                video.badge = String.format("%d min • %.1f★", 
                    movieDetails.getRuntime(), movieDetails.getVoteAverage());
            }
            
            // Add genre info to description
            if (movieDetails.getGenres() != null && !movieDetails.getGenres().isEmpty()) {
                StringBuilder genreText = new StringBuilder();
                for (int i = 0; i < Math.min(3, movieDetails.getGenres().size()); i++) {
                    if (i > 0) genreText.append(", ");
                    genreText.append(movieDetails.getGenres().get(i).getName());
                }
                video.secondTitle = genreText.toString();
            }
        }
        
        return video;
    }
    
    public static List<Video> convertToVideos(List<TMDBMovie> movies) {
        List<Video> videos = new ArrayList<>();
        
        if (movies != null) {
            for (TMDBMovie movie : movies) {
                Video video = convertToVideo(movie);
                if (video != null) {
                    videos.add(video);
                }
            }
        }
        
        return videos;
    }
    
    private static String generateYouTubeVideoId(String title, String releaseDate) {
        // Generate a search-friendly video ID for YouTube lookup
        String searchQuery = title;
        if (releaseDate != null && !releaseDate.isEmpty()) {
            searchQuery += " " + releaseDate.substring(0, 4); // Add year
        }
        
        // Create a hash-based ID for consistency
        return "tmdb_" + Math.abs(searchQuery.hashCode());
    }
}