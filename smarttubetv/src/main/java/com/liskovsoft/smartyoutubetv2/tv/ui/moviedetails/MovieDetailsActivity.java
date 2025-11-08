package com.liskovsoft.smartyoutubetv2.tv.ui.moviedetails;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoActionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.MovieDetailsView;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import androidx.core.content.ContextCompat;
import android.view.KeyEvent;
import android.widget.ProgressBar;

public class MovieDetailsActivity extends LeanbackActivity implements MovieDetailsView {
    public static final String EXTRA_MOVIE_TITLE = "movie_title";
    public static final String EXTRA_MOVIE_OVERVIEW = "movie_overview";
    public static final String EXTRA_MOVIE_POSTER = "movie_poster";
    public static final String EXTRA_MOVIE_BACKDROP = "movie_backdrop";
    public static final String EXTRA_MOVIE_RATING = "movie_rating";
    public static final String EXTRA_MOVIE_RELEASE_DATE = "movie_release_date";
    public static final String EXTRA_MOVIE_RUNTIME = "movie_runtime";
    public static final String EXTRA_MOVIE_STATUS = "movie_status";
    public static final String EXTRA_MOVIE_TAGLINE = "movie_tagline";
    public static final String EXTRA_MOVIE_GENRES = "movie_genres";
    public static final String EXTRA_MOVIE_PRODUCTION_COMPANIES = "movie_production_companies";
    public static final String EXTRA_MOVIE_PRODUCTION_COUNTRIES = "movie_production_countries";
    public static final String EXTRA_MOVIE_SPOKEN_LANGUAGES = "movie_spoken_languages";
    public static final String EXTRA_MOVIE_CERTIFICATION = "movie_certification";
    public static final String EXTRA_VIDEO_ID = "video_id";

    private String mMovieTitle;
    private String mMovieOverview;
    private String mMoviePoster;
    private String mMovieBackdrop;
    private String mMovieRating;
    private String mMovieReleaseDate;
    private String mMovieRuntime;
    private String mMovieStatus;
    private String mMovieTagline;
    private String mMovieGenres;
    private String mMovieProductionCompanies;
    private String mMovieProductionCountries;
    private String mMovieSpokenLanguages;
    private String mMovieCertification;
    private String mVideoId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_details);
        
        // Get movie details from intent
        Intent intent = getIntent();
        mMovieTitle = intent.getStringExtra(EXTRA_MOVIE_TITLE);
        mMovieOverview = intent.getStringExtra(EXTRA_MOVIE_OVERVIEW);
        mMoviePoster = intent.getStringExtra(EXTRA_MOVIE_POSTER);
        mMovieBackdrop = intent.getStringExtra(EXTRA_MOVIE_BACKDROP);
        mMovieRating = intent.getStringExtra(EXTRA_MOVIE_RATING);
        mMovieReleaseDate = intent.getStringExtra(EXTRA_MOVIE_RELEASE_DATE);
        mMovieRuntime = intent.getStringExtra(EXTRA_MOVIE_RUNTIME);
        mMovieStatus = intent.getStringExtra(EXTRA_MOVIE_STATUS);
        mMovieTagline = intent.getStringExtra(EXTRA_MOVIE_TAGLINE);
        mMovieGenres = intent.getStringExtra(EXTRA_MOVIE_GENRES);
        mMovieProductionCompanies = intent.getStringExtra(EXTRA_MOVIE_PRODUCTION_COMPANIES);
        mMovieProductionCountries = intent.getStringExtra(EXTRA_MOVIE_PRODUCTION_COUNTRIES);
        mMovieSpokenLanguages = intent.getStringExtra(EXTRA_MOVIE_SPOKEN_LANGUAGES);
        mMovieCertification = intent.getStringExtra(EXTRA_MOVIE_CERTIFICATION);
        mVideoId = intent.getStringExtra(EXTRA_VIDEO_ID);
        
        // Set up the UI
        setupUI();
    }

    private void setupUI() {
        // Set movie title
        TextView titleView = findViewById(R.id.movie_title);
        if (titleView != null && mMovieTitle != null) {
            titleView.setText(mMovieTitle);
        }

        // Set movie overview
        TextView overviewView = findViewById(R.id.movie_overview);
        if (overviewView != null && mMovieOverview != null) {
            overviewView.setText(mMovieOverview);
        }

        // Set movie rating
        TextView ratingView = findViewById(R.id.movie_rating);
        if (ratingView != null && mMovieRating != null) {
            // Clean the rating value - remove any existing "/10" or other suffixes
            String cleanRating = mMovieRating.replaceAll("/10.*", "").trim();
            ratingView.setText(cleanRating);
        }

        // Set movie release year
        TextView releaseYearView = findViewById(R.id.movie_release_year);
        if (releaseYearView != null && mMovieReleaseDate != null) {
            // Extract year from release date
            String year = mMovieReleaseDate.length() >= 4 ? mMovieReleaseDate.substring(0, 4) : mMovieReleaseDate;
            releaseYearView.setText(year);
        }

        // Set movie runtime
        TextView runtimeView = findViewById(R.id.movie_runtime);
        if (runtimeView != null && mMovieRuntime != null) {
            runtimeView.setText(mMovieRuntime);
        }

        // Set movie age rating/certification
        TextView ageRatingView = findViewById(R.id.movie_age_rating);
        if (ageRatingView != null && mMovieCertification != null && !mMovieCertification.isEmpty()) {
            ageRatingView.setText(mMovieCertification);
        } else if (ageRatingView != null) {
            ageRatingView.setVisibility(View.GONE);
        }

        // Set movie genres (populate the genre chips - max 3 genres)
        LinearLayout genresContainer = findViewById(R.id.genres_container);
        if (genresContainer != null && mMovieGenres != null && !mMovieGenres.isEmpty()) {
            genresContainer.removeAllViews(); // Clear existing genre chips
            
            String[] genres = mMovieGenres.split(",");
            int genreCount = 0;
            for (String genre : genres) {
                if (genreCount >= 3) break; // Limit to maximum 3 genres
                
                genre = genre.trim();
                if (!genre.isEmpty()) {
                    TextView genreChip = new TextView(this);
                    genreChip.setText(genre);
                    genreChip.setTextColor(getResources().getColor(R.color.white));
                    genreChip.setTextSize(12);
                    genreChip.setTypeface(android.graphics.Typeface.SANS_SERIF);
                    genreChip.setBackgroundResource(R.drawable.genre_chip);
                    genreChip.setPadding(6, 6, 6, 6);
                    
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    params.setMargins(0, 0, 6, 0);
                    genreChip.setLayoutParams(params);
                    
                    genresContainer.addView(genreChip);
                    genreCount++;
                }
            }
        }


        // Load movie backdrop
        ImageView backdropView = findViewById(R.id.movie_backdrop);
        if (backdropView != null) {
            String imageUrl = mMovieBackdrop != null && !mMovieBackdrop.isEmpty() ? mMovieBackdrop : mMoviePoster;
            android.util.Log.d("MovieDetails", "Backdrop URL: " + mMovieBackdrop + ", Poster URL: " + mMoviePoster + ", Using: " + imageUrl);
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(this)
                        .load(imageUrl)
                        .into(backdropView);
            }
        }

        // Check video progress and update UI accordingly
        updateVideoProgress();

        // Set up play button
        TextView playButton = findViewById(R.id.play_button);
        if (playButton != null) {
            playButton.setOnClickListener(v -> playMovie());
            // Text color is now handled by selector in layout XML
        }

        // Set up trailer button
        TextView trailerButton = findViewById(R.id.trailer_button);
        if (trailerButton != null) {
            trailerButton.setOnClickListener(v -> {
                // TODO: Implement trailer functionality
            });
            // Text color is now handled by selector in layout XML
        }

        // Set initial focus to play button
        if (playButton != null) {
            playButton.requestFocus();
        }
    }

    private void updateVideoProgress() {
        if (mVideoId == null || mVideoId.isEmpty()) {
            return;
        }

        // Get video progress from VideoStateService
        VideoStateService stateService = VideoStateService.instance(this);
        VideoStateService.State state = stateService.getByVideoId(mVideoId);

        ProgressBar progressBar = findViewById(R.id.video_progress_bar);
        TextView playButton = findViewById(R.id.play_button);

        if (state != null && state.durationMs > 0) {
            // Calculate progress percentage
            float percentWatched = (state.positionMs * 100f) / state.durationMs;
            
            // Only show progress if video has been watched (more than 1% and less than 100%)
            if (percentWatched > 1 && percentWatched < 100) {
                // Show progress bar and set progress
                if (progressBar != null) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress((int) percentWatched);
                }

                // Change play button to Resume
                if (playButton != null) {
                    playButton.setText("Resume");
                }
            } else {
                // Hide progress bar if video not watched or fully watched
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }

                // Keep play button as Play
                if (playButton != null) {
                    playButton.setText("Play");
                }
            }
        } else {
            // No progress found, hide progress bar and keep Play button
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
            if (playButton != null) {
                playButton.setText("Play");
            }
        }
    }

    private void playMovie() {
        if (mVideoId != null && !mVideoId.isEmpty()) {
            // Create Video object from video ID and start playback
            Video video = Video.from(mVideoId);
            if (video != null) {
                // Set additional video properties if needed
                video.title = mMovieTitle != null ? mMovieTitle : "Unknown Title";
                video.cardImageUrl = mMoviePoster;
                
                // Use VideoActionPresenter which is the standard way to handle video playback
                VideoActionPresenter.instance(this).apply(video);
                
                // Don't finish the activity - let it stay as parent for the video player
                // The activity will be finished when the user presses back from the video player
            } else {
                // Fallback: just close the activity
                finish();
            }
        } else {
            // No video ID available, just close the activity
            finish();
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // When back is pressed on movie details page, go back to home screen
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}