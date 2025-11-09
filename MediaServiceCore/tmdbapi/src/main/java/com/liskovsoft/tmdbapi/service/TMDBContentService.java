package com.liskovsoft.tmdbapi.service;

import com.liskovsoft.tmdbapi.api.TMDBApi;
import com.liskovsoft.tmdbapi.models.TMDBMovie;
import com.liskovsoft.tmdbapi.models.TMDBMovieResponse;
import com.liskovsoft.tmdbapi.models.TMDBMovieDetails;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.Call;
import retrofit2.Response;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

public class TMDBContentService {
    private static final String TMDB_API_KEY = "68872c817530adf9fd665f33874e926e";
    private static final String TMDB_BASE_URL = "https://api.themoviedb.org/3/";
    private static final String TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/";
    
    private TMDBApi tmdbApi;
    
    public TMDBContentService() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(TMDB_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        
        tmdbApi = retrofit.create(TMDBApi.class);
    }
    
    public List<TMDBMovie> getPopularMovies(int page) {
        try {
            Call<TMDBMovieResponse> call = tmdbApi.getPopularMovies(TMDB_API_KEY, page, "en-US");
            Response<TMDBMovieResponse> response = call.execute();
            
            if (response.isSuccessful() && response.body() != null) {
                return response.body().getResults();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
    
    public List<TMDBMovie> getTopRatedMovies(int page) {
        try {
            Call<TMDBMovieResponse> call = tmdbApi.getTopRatedMovies(TMDB_API_KEY, page, "en-US");
            Response<TMDBMovieResponse> response = call.execute();
            
            if (response.isSuccessful() && response.body() != null) {
                return response.body().getResults();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
    
    public List<TMDBMovie> getNowPlayingMovies(int page) {
        try {
            Call<TMDBMovieResponse> call = tmdbApi.getNowPlayingMovies(TMDB_API_KEY, page, "en-US");
            Response<TMDBMovieResponse> response = call.execute();
            
            if (response.isSuccessful() && response.body() != null) {
                return response.body().getResults();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
    
    public List<TMDBMovie> getUpcomingMovies(int page) {
        try {
            Call<TMDBMovieResponse> call = tmdbApi.getUpcomingMovies(TMDB_API_KEY, page, "en-US");
            Response<TMDBMovieResponse> response = call.execute();
            
            if (response.isSuccessful() && response.body() != null) {
                return response.body().getResults();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
    
    public TMDBMovieDetails getMovieDetails(int movieId) {
        try {
            Call<TMDBMovieDetails> call = tmdbApi.getMovieDetails(movieId, TMDB_API_KEY, "en-US");
            Response<TMDBMovieDetails> response = call.execute();
            
            if (response.isSuccessful() && response.body() != null) {
                return response.body();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public String getPosterUrl(String posterPath, String size) {
        if (posterPath == null || posterPath.isEmpty()) {
            return null;
        }
        return TMDB_IMAGE_BASE_URL + size + posterPath;
    }
    
    public String getBackdropUrl(String backdropPath, String size) {
        if (backdropPath == null || backdropPath.isEmpty()) {
            return null;
        }
        return TMDB_IMAGE_BASE_URL + size + backdropPath;
    }
}