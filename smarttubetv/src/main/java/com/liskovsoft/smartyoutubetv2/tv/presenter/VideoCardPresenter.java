package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Presenter;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.LongClickPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.GridFragmentHelper;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.complexcardview.ComplexImageCardView;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;
import com.liskovsoft.smartyoutubetv2.tv.services.TMDBImageService;
import com.liskovsoft.smartyoutubetv2.tv.services.TMDBImageCallback;
import com.liskovsoft.smartyoutubetv2.tv.services.TMDBDetailedMovieInfo;
import com.liskovsoft.smartyoutubetv2.tv.services.TMDBDataCache;
import com.liskovsoft.smartyoutubetv2.tv.presenters.MovieDetailsVideoActionPresenter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Data class to store movie details for each card
class MovieDetails {
    String title;
    String overview;
    String poster;
    String rating;
    String releaseDate;
    TMDBDetailedMovieInfo detailedInfo;

    MovieDetails(String title, String overview, String poster, String rating, String releaseDate) {
        this.title = title;
        this.overview = overview;
        this.poster = poster;
        this.rating = rating;
        this.releaseDate = releaseDate;
    }
    
    void setDetailedInfo(TMDBDetailedMovieInfo detailedInfo) {
        this.detailedInfo = detailedInfo;
    }
}

/*
 * A CardPresenter is used to generate Views and bind Objects to them on demand.
 * It contains an Image CardView
 */
public class VideoCardPresenter extends LongClickPresenter {
    private static final String TAG = VideoCardPresenter.class.getSimpleName();
    private int mDefaultBackgroundColor = -1;
    private int mDefaultTextColor = -1;
    private int mSelectedBackgroundColor = -1;
    private int mSelectedTextColor = -1;
    private int mCardPreviewType;
    private int mThumbQuality;
    private int mWidth;
    private int mHeight;
    private TMDBImageService mTMDBService;
    
    // Request management for preventing duplicate TMDB calls
    private static final ConcurrentHashMap<String, AtomicInteger> sRequestCounters = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> sRequestCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> sBackdropCache = new ConcurrentHashMap<>();
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static final long DEBOUNCE_DELAY_MS = 100; // 100ms debounce for faster response

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();

        mDefaultBackgroundColor =
            ContextCompat.getColor(context, Helpers.getThemeAttr(context, R.attr.cardDefaultBackground));
        mDefaultTextColor =
                ContextCompat.getColor(context, R.color.card_default_text);
        mSelectedBackgroundColor =
                ContextCompat.getColor(context, Helpers.getThemeAttr(context, R.attr.cardSelectedBackground));
        mSelectedTextColor =
                ContextCompat.getColor(context, R.color.card_selected_text_grey);

        mCardPreviewType = getCardPreviewType(context);
        mThumbQuality = getThumbQuality(context);

        boolean isCardMultilineTitleEnabled = isCardMultilineTitleEnabled(context);
        boolean isCardMultilineSubtitleEnabled = isCardMultilineSubtitleEnabled(context);
        boolean isCardTextAutoScrollEnabled = isCardTextAutoScrollEnabled(context);
        float cardTextScrollSpeed = getCardTextScrollSpeed(context);

        updateDimensions(context);
        
        // Initialize TMDB service with memory optimizations
        if (mTMDBService == null) {
            mTMDBService = new TMDBImageService();
        }

        ComplexImageCardView cardView = new ComplexImageCardView(context) {
            @Override
            public void setSelected(boolean selected) {
                updateCardBackgroundColor(this, selected);
                updateCardSelectionStyle(this, selected);
                super.setSelected(selected);
            }
        };

        cardView.setTitleLinesNum(isCardMultilineTitleEnabled ? 2 : 1);
        cardView.setContentLinesNum(isCardMultilineSubtitleEnabled ? 2 : 1);
        cardView.enableTextAutoScroll(isCardTextAutoScrollEnabled);
        cardView.setTextScrollSpeed(cardTextScrollSpeed);
        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        cardView.enableBadge(isBadgeEnabled());
        // Disable title and content - just show image skeleton
        cardView.enableTitle(false);
        cardView.enableContent(false);
        
        // Add Netflix-like styling
        setupCardStyling(cardView);
        return new ViewHolder(cardView);
    }

    private void updateCardBackgroundColor(ComplexImageCardView view, boolean selected) {
        int backgroundColor = selected ? mSelectedBackgroundColor : mDefaultBackgroundColor;

        // Both background colors should be set because the view's
        // background is temporarily visible during animations.
        view.setBackgroundColor(backgroundColor);
        View infoField = view.findViewById(R.id.info_field);
        if (infoField != null) {
            infoField.setBackgroundColor(backgroundColor);
        }

        // Text color updates removed since we're not showing title/content
    }

    private void setupCardStyling(ComplexImageCardView view) {
        // Add elevation for depth
        if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            view.setElevation(8f);
        }
        // Set rectangular background
        view.setBackgroundColor(mDefaultBackgroundColor);
    }

    private void updateCardSelectionStyle(ComplexImageCardView view, boolean selected) {
        if (selected) {
            // Add bold white border for selection
            view.setBackgroundResource(R.drawable.card_selected_border);
            // Increase elevation when selected
            if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
                view.setElevation(20f);
            }
            // Ensure the border is visible by setting padding
            view.setPadding(8, 8, 8, 8);
            // Slightly enlarge the image for focus effect
            view.getMainImageView().setScaleX(1.05f);
            view.getMainImageView().setScaleY(1.05f);
            // Force a redraw to ensure the border is visible
            view.invalidate();
        } else {
            // Use rectangular background when not selected
            view.setBackgroundColor(mDefaultBackgroundColor);
            // Reset elevation and padding
            if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
                view.setElevation(8f);
            }
            view.setPadding(0, 0, 0, 0);
            // Reset image scale to normal
            view.getMainImageView().setScaleX(1.0f);
            view.getMainImageView().setScaleY(1.0f);
            // Force a redraw
            view.invalidate();
        }
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        super.onBindViewHolder(viewHolder, item);

        Video video = (Video) item;

        ComplexImageCardView cardView = (ComplexImageCardView) viewHolder.view;
        Context context = cardView.getContext();

        // Remove title and content text to use full space for image
        // cardView.setTitleText(video.getTitle());
        // cardView.setContentText(video.getSecondTitle());
        // Count progress that very close to zero. E.g. when user closed video immediately.
        cardView.setProgress(video.percentWatched > 0 && video.percentWatched < 1 ? 1 : Math.round(video.percentWatched));
        cardView.setBadgeText(
                video.hasNewContent ? context.getString(R.string.badge_new_content) :
                video.isLive ? context.getString(R.string.badge_live) :
                video.isShorts ? context.getString(R.string.header_shorts).toUpperCase() :
                video.badge
        );
        cardView.setBadgeColor(video.hasNewContent || video.isLive || video.isUpcoming ?
                ContextCompat.getColor(context, R.color.dark_red) : ContextCompat.getColor(context, R.color.black));

        if (mCardPreviewType != MainUIData.CARD_PREVIEW_DISABLED) {
            cardView.setPreview(video);
            cardView.setMute(mCardPreviewType == MainUIData.CARD_PREVIEW_MUTED);
        }

        cardView.setMainImageDimensions(mWidth, mHeight);

        if (context instanceof Activity && ((Activity) context).isDestroyed()) {
            // Glide.with(context): IllegalArgumentException: You cannot start a load for a destroyed activity
            return;
        }

        // Optimized TMDB image loading with debouncing and caching
        String videoTitle = video.getTitle();
        String cacheKey = videoTitle.toLowerCase().trim();
        
        // CRITICAL: Check SQLite persistent cache FIRST (survives restarts)
        String cachedPosterUrl = null;
        String cachedBackdropUrl = null;
        
        if (video.videoId != null) {
            TMDBDataCache persistentCache = TMDBDataCache.instance(context);
            cachedPosterUrl = persistentCache.getPosterUrl(video.videoId);
            cachedBackdropUrl = persistentCache.getBackdropUrl(video.videoId);
            
            // Also check in-memory cache as fallback
            if (cachedBackdropUrl == null) {
                cachedBackdropUrl = sBackdropCache.get(cacheKey);
            }
        } else {
            // Fallback to in-memory cache if no video ID
            cachedBackdropUrl = sBackdropCache.get(cacheKey);
        }
        
        // Check in-memory cache for fast lookup
        String cachedImageUrl = sRequestCache.get(cacheKey);
        if (cachedImageUrl != null && cachedPosterUrl == null) {
            cachedPosterUrl = cachedImageUrl;
        }
        
        if (cachedPosterUrl != null) {
            // Use cached result immediately - FAST PATH
            if (cachedBackdropUrl != null) {
                video.backdropImageUrl = cachedBackdropUrl;
            }
            loadImageWithGlide(context, cardView, video, cachedPosterUrl);
            return;
        }
        
        // Get or create request counter for this title
        AtomicInteger requestCounter = sRequestCounters.computeIfAbsent(cacheKey, k -> new AtomicInteger(0));
        int currentRequestId = requestCounter.incrementAndGet();
        
        // Debounce requests - only proceed if this is still the latest request
        sHandler.postDelayed(() -> {
            if (requestCounter.get() != currentRequestId) {
                // A newer request was made, cancel this one
                return;
            }
            
            // Make TMDB API call - pass description for TMDB ID lookup
            // Try to get description from multiple sources
            String videoDescription = video.description;
            
            // If description is null, try to get it from mediaItem metadata
            if ((videoDescription == null || videoDescription.isEmpty()) && video.mediaItem != null) {
                try {
                    // Check if mediaItem has a method to get description
                    if (video.mediaItem instanceof com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata) {
                        com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata metadata = 
                            (com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata) video.mediaItem;
                        videoDescription = metadata.getDescription();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error getting description from mediaItem", e);
                }
            }
            
            Log.d(TAG, "Video title: " + videoTitle + ", description length: " + (videoDescription != null ? videoDescription.length() : "null"));
            mTMDBService.getMoviePosterByTitle(videoTitle, videoDescription, new TMDBImageCallback() {
                @Override
                public void onImageUrlReceived(String imageUrl) {
                    // Check if this is still the latest request
                    if (requestCounter.get() != currentRequestId) {
                        return; // Cancel if newer request exists
                    }
                    
                    // Cache the result in BOTH in-memory AND SQLite
                    if (imageUrl != null) {
                        sRequestCache.put(cacheKey, imageUrl);
                        
                        // CRITICAL: Persist to SQLite to survive app restarts
                        if (video.videoId != null) {
                            TMDBDataCache.instance(context).storePosterUrl(video.videoId, imageUrl);
                        }
                    }
                    
                    // Run on main thread for UI updates
                    cardView.post(() -> {
                        if (requestCounter.get() != currentRequestId) {
                            return; // Double-check on main thread
                        }
                        loadImageWithGlide(context, cardView, video, imageUrl);
                    });
                }
                
                @Override
                public void onBackdropUrlReceived(String backdropUrl) {
                    if (backdropUrl != null && !backdropUrl.isEmpty()) {
                        video.backdropImageUrl = backdropUrl;
                        // Cache the backdrop URL in BOTH in-memory AND SQLite
                        sBackdropCache.put(cacheKey, backdropUrl);
                        
                        // CRITICAL: Persist backdrop URL to SQLite to survive app restarts
                        if (video.videoId != null) {
                            TMDBDataCache.instance(context).storeBackdropUrl(video.videoId, backdropUrl);
                        }
                    }
                }
                
                @Override
                public void onMovieDetailsReceived(String title, String overview, String poster, String rating, String releaseDate) {
                    MovieDetails movieDetails = new MovieDetails(title, overview, poster, rating, releaseDate);
                    cardView.setTag(movieDetails);
                    
                    if (poster != null && !poster.isEmpty()) {
                        video.cardImageUrl = poster;
                    }
                }
                
                @Override
                public void onDetailedMovieInfoReceived(TMDBDetailedMovieInfo detailedInfo) {
                    if (detailedInfo != null) {
                        MovieDetailsVideoActionPresenter.storeDetailedMovieInfo(context, video.videoId, detailedInfo);
                    }
                }
            });
        }, DEBOUNCE_DELAY_MS);
        
        // Store video reference for click handling
        cardView.setTag(video);
    }
    
    private void loadImageWithGlide(Context context, ComplexImageCardView cardView, Video video, String imageUrl) {
        String finalImageUrl = imageUrl != null ? imageUrl : ClickbaitRemover.updateThumbnail(video, mThumbQuality);
        
        Glide.with(context)
                .load(finalImageUrl)
                .apply(ViewUtil.glideOptions())
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .listener(mErrorListener)
                .error(
                    Glide.with(context)
                        .load(ClickbaitRemover.updateThumbnail(video, mThumbQuality))
                        .apply(ViewUtil.glideOptions())
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .skipMemoryCache(false)
                        .listener(mErrorListener)
                        .error(R.drawable.card_placeholder)
                )
                .into(cardView.getMainImageView());
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        super.onUnbindViewHolder(viewHolder);

        ComplexImageCardView cardView = (ComplexImageCardView) viewHolder.view;

        // Remove references to images so that the garbage collector can free up memory.
        cardView.setBadgeImage(null);
        cardView.setMainImage(null);

        // DON'T clear Glide cache - this causes images to reload when navigating back
        // The images will stay cached and load instantly when you return
        // Glide.with(cardView.getContext().getApplicationContext()).clear(cardView.getMainImageView());
    }

    private void updateDimensions(Context context) {
        Pair<Integer, Integer> dimens = getCardDimensPx(context);

        mWidth = dimens.first;
        mHeight = dimens.second;
    }
    
    protected Pair<Integer, Integer> getCardDimensPx(Context context) {
        return GridFragmentHelper.getCardDimensPx(context, R.dimen.card_width, R.dimen.card_height, MainUIData.instance(context).getVideoGridScale());
    }

    protected boolean isCardTextAutoScrollEnabled(Context context) {
        return MainUIData.instance(context).isCardTextAutoScrollEnabled();
    }

    protected int getCardPreviewType(Context context) {
        return MainUIData.instance(context).getCardPreviewType();
    }

    protected boolean isCardMultilineTitleEnabled(Context context) {
        return MainUIData.instance(context).isCardMultilineTitleEnabled();
    }

    protected boolean isCardMultilineSubtitleEnabled(Context context) {
        return MainUIData.instance(context).isCardMultilineSubtitleEnabled();
    }

    protected float getCardTextScrollSpeed(Context context) {
        return MainUIData.instance(context).getCardTextScrollSpeed();
    }

    protected int getThumbQuality(Context context) {
        return MainUIData.instance(context).getThumbQuality();
    }

    protected boolean isContentEnabled() {
        return true;
    }

    protected boolean isTitleEnabled() {
        return true;
    }

    protected boolean isBadgeEnabled() {
        return true;
    }

    private final RequestListener<Drawable> mErrorListener = new RequestListener<Drawable>() {
        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
            Log.e(TAG, "Glide load failed: " + e);
            return false;
        }

        @Override
        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
            return false;
        }
    };

    private final RequestListener<Bitmap> mErrorListener2 = new RequestListener<Bitmap>() {
        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
            Log.e(TAG, "Glide load failed: " + e);
            return false;
        }

        @Override
        public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
            return false;
        }
    };
    
}
