package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.RowPresenter.ViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ChannelHeaderPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ChannelHeaderPresenter.ChannelHeaderCallback;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ShortsCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CustomListRowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemLongPressedListener;
import com.liskovsoft.smartyoutubetv2.tv.presenters.MovieDetailsVideoActionPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.VideoSection;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class MultipleRowsFragment extends RowsSupportFragment implements VideoSection {
    private static final String TAG = MultipleRowsFragment.class.getSimpleName();
    private UriBackgroundManager mBackgroundManager;
    private ArrayObjectAdapter mRowsAdapter;
    private ListRowPresenter mRowPresenter;
    private Map<Integer, VideoGroupObjectAdapter> mVideoGroupAdapters;
    private final List<VideoGroup> mPendingUpdates = new ArrayList<>();
    private VideoGroupPresenter mMainPresenter;
    private VideoCardPresenter mCardPresenter;
    private ShortsCardPresenter mShortsPresenter;
    private int mSelectedRowIndex = -1;
    private ChannelHeaderCallback mChannelHeaderCallback;
    

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mMainPresenter = getMainPresenter();
        mCardPresenter = new VideoCardPresenter();
        mShortsPresenter = new ShortsCardPresenter();
        mBackgroundManager = ((LeanbackActivity) getActivity()).getBackgroundManager();

        setupAdapter();
        setupEventListeners();
        applyPendingUpdates();
    }

    protected void addHeader(ChannelHeaderCallback callback) {
        mChannelHeaderCallback = callback;
    }

    protected abstract VideoGroupPresenter getMainPresenter();

    private void applyPendingUpdates() {
        // prevent modification within update method
        List<VideoGroup> copyArray = new ArrayList<>(mPendingUpdates);

        mPendingUpdates.clear();

        for (VideoGroup group : copyArray) {
            update(group);
        }
    }

    private void setupAdapter() {
        if (mVideoGroupAdapters == null) {
            mVideoGroupAdapters = new HashMap<>();
        }

        if (mRowsAdapter == null) {
            mRowPresenter = new CustomListRowPresenter();
            
            // Enable header display for genre categorization
            mRowPresenter.setHeaderPresenter(new androidx.leanback.widget.RowHeaderPresenter());
            mRowPresenter.setSelectEffectEnabled(true);

            ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
            presenterSelector.addClassPresenter(ListRow.class, mRowPresenter);
            presenterSelector.addClassPresenter(ChannelHeaderCallback.class, new ChannelHeaderPresenter());

            mRowsAdapter = new ArrayObjectAdapter(presenterSelector);
            setAdapter(mRowsAdapter);
        }
    }

    private void applyRowAlignment() {
        // Keep only the currently focused row visible within the rows container
        androidx.leanback.widget.VerticalGridView gridView = getVerticalGridView();
        if (gridView != null && getView() != null && getActivity() != null) {
            // Measure the actual rows container (lower half) height
            android.view.View rowsContainer = getActivity().findViewById(androidx.leanback.R.id.browse_container_dock);

            final java.lang.Runnable[] applyHolder = new java.lang.Runnable[1];
            final int[] rowsHeightHolder = new int[1];
            applyHolder[0] = () -> {
                rowsHeightHolder[0] = rowsContainer != null ? rowsContainer.getHeight() : 0;
                if (rowsHeightHolder[0] <= 0) {
                    // Defer until layout pass finishes; run once when height becomes available
                    final android.view.View target = rowsContainer != null ? rowsContainer : gridView;
                    target.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            if (rowsContainer != null && rowsContainer.getHeight() > 0) {
                                target.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                // Re-run now that we have a non-zero height
                                applyHolder[0].run();
                            }
                        }
                    });
                    return;
                }

                android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
                getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);

                // Use the same layout for ALL sections (60/40 ratio: backdrop/rows)
                // This prevents layout reset when switching between sections
                
                // Position rows container to overlap backdrop - same for all sections
                if (rowsContainer != null && rowsContainer.getParent() instanceof android.view.ViewGroup) {
                    android.view.ViewGroup parentLayout = (android.view.ViewGroup) rowsContainer.getParent();
                    if (parentLayout instanceof android.widget.FrameLayout) {
                        // Set rows container to take 50% of screen height, starting at 50% from top
                        // This extends from 50% to 100% (bottom), eliminating unused space
                        int screenHeightPx = dm.heightPixels;
                        int rowsHeightPx = (int) (screenHeightPx * 0.5f); // 50% of screen (extends to bottom)
                        
                        // Position rows container to start at 50% from top (transition line at 50%)
                        // Container now extends from 50% to 100%, filling the bottom area completely
                        int topMarginPx = (int) (screenHeightPx * 0.5f); // Start at 50% from top
                        
                        if (rowsContainer.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams) {
                            android.widget.FrameLayout.LayoutParams rowsParams = 
                                (android.widget.FrameLayout.LayoutParams) rowsContainer.getLayoutParams();
                            rowsParams.height = rowsHeightPx;
                            rowsParams.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                            rowsParams.gravity = android.view.Gravity.NO_GRAVITY; // Use manual positioning
                            rowsParams.topMargin = topMarginPx;
                            rowsContainer.setLayoutParams(rowsParams);
                            
                            // Ensure rows container is elevated to appear on top of backdrop
                            rowsContainer.setElevation(4f);
                            
                            // Cast to ViewGroup to set clip properties
                            if (rowsContainer instanceof android.view.ViewGroup) {
                                android.view.ViewGroup rowsContainerGroup = (android.view.ViewGroup) rowsContainer;
                                rowsContainerGroup.setClipToPadding(false);
                                rowsContainerGroup.setClipChildren(false);
                            }
                        }
                    }
                }
                
                // Wait for layout to adjust, then apply alignment - same for all sections
                if (rowsContainer != null) {
                    final android.view.View rowsContainerFinal = rowsContainer;
                    final androidx.leanback.widget.VerticalGridView gridViewFinal = gridView;
                    rowsContainerFinal.post(() -> {
                        rowsContainerFinal.post(() -> {
                            // Re-measure after layout adjustment
                            int newRowsHeight = rowsContainerFinal.getHeight();
                            if (newRowsHeight > 0) {
                                rowsHeightHolder[0] = newRowsHeight;
                                
                                // Ensure grid view allows scrolling under backdrop
                                gridViewFinal.setClipToPadding(false);
                                gridViewFinal.setClipChildren(false);

                                if (mRowPresenter != null) {
                                    // Make row height larger to allow scrolling under backdrop
                                    // Use screen height so rows can scroll under the backdrop
                                    int screenHeightPx2 = dm.heightPixels;
                                    mRowPresenter.setRowHeight(screenHeightPx2);
                                    mRowPresenter.setExpandedRowHeight(screenHeightPx2);
                                }

                                // Align rows at a fixed position within the container (not stuck at top)
                                // Use NO_EDGE alignment with an offset to center rows better within the container
                                gridViewFinal.setItemAlignmentOffset(0);
                                gridViewFinal.setItemAlignmentOffsetPercent(androidx.leanback.widget.VerticalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);
                                
                                // Set window alignment offset to position rows in the lower part of the container
                                // This prevents rows from stopping at the very top
                                int windowAlignOffset = (int) (newRowsHeight * 0.1f); // Position at 10% from top of container
                                gridViewFinal.setWindowAlignmentOffset(windowAlignOffset);
                                gridViewFinal.setWindowAlignmentOffsetPercent(androidx.leanback.widget.VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
                                gridViewFinal.setWindowAlignment(androidx.leanback.widget.VerticalGridView.WINDOW_ALIGN_NO_EDGE);
                            }
                        });
                    });
                    return; // Exit early, will be handled by post
                }

                // Fallback if layout adjustment fails
                gridView.setClipToPadding(false);
                gridView.setClipChildren(false);
                if (mRowPresenter != null) {
                    int screenHeightPx = dm.heightPixels;
                    mRowPresenter.setRowHeight(screenHeightPx);
                    mRowPresenter.setExpandedRowHeight(screenHeightPx);
                }
                gridView.setItemAlignmentOffset(0);
                gridView.setItemAlignmentOffsetPercent(androidx.leanback.widget.VerticalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);
                
                // Use NO_EDGE alignment for better row positioning
                int fallbackHeight = rowsHeightHolder[0] > 0 ? rowsHeightHolder[0] : (int) (dm.heightPixels * 0.5f);
                int windowAlignOffset = (int) (fallbackHeight * 0.1f); // Position at 10% from top
                gridView.setWindowAlignmentOffset(windowAlignOffset);
                gridView.setWindowAlignmentOffsetPercent(androidx.leanback.widget.VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
                gridView.setWindowAlignment(androidx.leanback.widget.VerticalGridView.WINDOW_ALIGN_NO_EDGE);
            };

            // Kick off now; if height is zero, listener above will retry once ready
            applyHolder[0].run();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        applyRowAlignment();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Apply alignment - same for all sections since we use uniform layout
        applyRowAlignment();

        // Also hide headers to avoid lingering sidebar
        try {
            androidx.fragment.app.Fragment parent = getParentFragment();
            if (parent instanceof androidx.leanback.app.BrowseSupportFragment) {
                ((androidx.leanback.app.BrowseSupportFragment) parent).startHeadersTransition(false);
            }
        } catch (Throwable ignored) {
        }
    }

    private void setupEventListeners() {
        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
        mCardPresenter.setOnItemViewLongPressedListener(new ItemViewLongPressedListener());
        mShortsPresenter.setOnItemViewLongPressedListener(new ItemViewLongPressedListener());
    }

    @Override
    public void clear() {
        if (mRowsAdapter != null) {
            mRowsAdapter.clear();
            if (mChannelHeaderCallback != null) {
                mRowsAdapter.add(mChannelHeaderCallback);
            }
        }

        if (mVideoGroupAdapters != null) {
            mVideoGroupAdapters.clear();
        }

        // Reset the position (bug appeared after fragment been reused)
        setPosition(mChannelHeaderCallback != null ? 1 : 0);
    }

    private void removeByIndex(int idx) {
        if (mRowsAdapter != null && mRowsAdapter.size() > idx) {
            ListRow row = (ListRow) mRowsAdapter.get(idx);
            mRowsAdapter.remove(row);
            VideoGroupObjectAdapter group = (VideoGroupObjectAdapter) row.getAdapter();
            mVideoGroupAdapters.values().remove(group);
        }
    }

    private void removeById(int id) {
        if (mRowsAdapter != null) {
            VideoGroupObjectAdapter needed = mVideoGroupAdapters.get(id);
            for (int i = 0; i < mRowsAdapter.size(); i++) {
                Object row = mRowsAdapter.get(i);

                if (row instanceof ListRow) {
                    VideoGroupObjectAdapter adapter = (VideoGroupObjectAdapter) ((ListRow) row).getAdapter();
                    if (adapter == needed) {
                        mRowsAdapter.remove(row);
                        mVideoGroupAdapters.remove(id);
                    }
                }
            }
        }
    }

    private int findPositionById(int id) {
        if (mRowsAdapter != null) {
            VideoGroupObjectAdapter needed = mVideoGroupAdapters.get(id);
            for (int i = 0; i < mRowsAdapter.size(); i++) {
                Object row = mRowsAdapter.get(i);

                if (row instanceof ListRow) {
                    VideoGroupObjectAdapter adapter = (VideoGroupObjectAdapter) ((ListRow) row).getAdapter();
                    if (adapter == needed) {
                        return i;
                    }
                }
            }
        }

        return -1;
    }

    private boolean isComputingLayout(VideoGroup group) {
        int action = group.getAction();

        // Attempt to fix: IllegalStateException: Cannot call this method while RecyclerView is computing a layout or scrolling
        if ((action == VideoGroup.ACTION_SYNC || action == VideoGroup.ACTION_REPLACE) && getVerticalGridView() != null) {
            if (getVerticalGridView().isComputingLayout()) {
                return true;
            }
            int position = findPositionById(group.getId());
            if (position != -1) {
                RecyclerView.ViewHolder viewHolder = getVerticalGridView().findViewHolderForAdapterPosition(position);
                if (viewHolder != null) {
                    Object nestedRecyclerView = Helpers.getField(viewHolder, "mNestedRecyclerView");
                    if (nestedRecyclerView instanceof WeakReference) {
                        Object recyclerView = ((WeakReference<?>) nestedRecyclerView).get();
                        return recyclerView instanceof RecyclerView && ((RecyclerView) recyclerView).isComputingLayout();
                    }
                }
            }
        }

        return false;
    }

    @Override
    public boolean isEmpty() {
        if (mRowsAdapter == null) {
            return mPendingUpdates.isEmpty();
        }

        return mRowsAdapter.size() == 0;
    }

    @Override
    public void update(VideoGroup group) {
        if (isComputingLayout(group)) {
            return;
        }

        if (mVideoGroupAdapters == null) {
            mPendingUpdates.add(group);
            return;
        }

        // Correct position depending on the search bar presence
        if (group.getPosition() != -1 && mChannelHeaderCallback != null) {
            group.setPosition(group.getPosition() + 1);
        }

        int action = group.getAction();

        if (action == VideoGroup.ACTION_REPLACE) {
            if (group.getPosition() == -1) {
                clear();
            } else {
                removeById(group.getId());
            }
        } else if (action == VideoGroup.ACTION_REMOVE) {
            VideoGroupObjectAdapter adapter = mVideoGroupAdapters.get(group.getId());
            if (adapter != null) {
                adapter.remove(group);
            }
            return;
        } else if (action == VideoGroup.ACTION_SYNC) {
            VideoGroupObjectAdapter adapter = mVideoGroupAdapters.get(group.getId());
            if (adapter != null) {
                freeze(true);
                adapter.sync(group);
                freeze(false);
            }
            return;
        }

        if (group.isEmpty()) {
            return;
        }

        VideoGroupObjectAdapter existingAdapter = GridFragmentHelper.findRelatedAdapter(mVideoGroupAdapters, group, this::freeze);

        if (existingAdapter == null) {
            HeaderItem rowHeader = new HeaderItem(group.getTitle());
            int videoGroupId = group.getId(); // Create unique int from category.

            VideoGroupObjectAdapter videoGroupAdapter = new VideoGroupObjectAdapter(group, group.isShorts() ? mShortsPresenter : mCardPresenter);

            mVideoGroupAdapters.put(videoGroupId, videoGroupAdapter);

            ListRow row = new ListRow(rowHeader, videoGroupAdapter);

            if (group.getPosition() == -1 || group.getPosition() > mRowsAdapter.size()) {
                mRowsAdapter.add(row);
            } else {
                mRowsAdapter.add(group.getPosition(), row);
            }
        } else {
            Log.d(TAG, "Continue row %s %s", group.getTitle(), System.currentTimeMillis());

            freeze(true);

            existingAdapter.add(group); // continue

            freeze(false);
        }

        restorePosition();
    }

    private void restorePosition() {
        setPosition(mSelectedRowIndex);

        // Maybe we don't need to load next group since all rows already fetched?
    }

    @Override
    public int getPosition() {
        return getSelectedPosition();
    }

    @Override
    public void setPosition(int index) {
        if (index < 0) {
            return;
        }

        if (mRowsAdapter != null && index < mRowsAdapter.size()) {
            setSelectedPosition(index, false);
            mSelectedRowIndex = -1;
        } else {
            mSelectedRowIndex = index;
        }
    }

    @Override
    public void selectItem(Video item) {
        // NOP
    }

    /**
     * Disable scrolling on partially updated rows. This prevent cards from misbehaving.
     */
    private void freeze(boolean freeze) {
        // Disable scrolling on partially updated rows. This prevent controls from misbehaving.
        if (mRowPresenter != null) {
            ViewHolder vh = getRowViewHolder(getSelectedPosition());
            if (vh instanceof ListRowPresenter.ViewHolder) {
                mRowPresenter.freeze(vh, freeze);
            }
        }
    }

    private final class ItemViewLongPressedListener implements OnItemLongPressedListener {
        @Override
        public void onItemLongPressed(Presenter.ViewHolder itemViewHolder, Object item) {

            if (item instanceof Video) {
                mMainPresenter.onVideoItemLongClicked((Video) item);
            } else {
                Toast.makeText(getActivity(), item.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final class ItemViewClickedListener implements androidx.leanback.widget.OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Video) {
                // Use custom presenter to open movie details instead of direct playback
                android.util.Log.d("MultipleRowsFragment", "Opening movie details for: " + ((Video) item).getTitle());
                MovieDetailsVideoActionPresenter.instance(getContext()).apply((Video) item);
            } else {
                Toast.makeText(getActivity(), item.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            // Ensure headers are hidden when content gains focus
            try {
                androidx.fragment.app.Fragment parent = getParentFragment();
                if (parent instanceof androidx.leanback.app.BrowseSupportFragment) {
                    ((androidx.leanback.app.BrowseSupportFragment) parent).startHeadersTransition(false);
                }
            } catch (Throwable ignored) {
            }
            if (item instanceof Video) {
                // Update upper-half backdrop ImageView and show TMDB info
                if (getActivity() != null) {
                    android.view.View browseFrame = getActivity().findViewById(androidx.leanback.R.id.browse_frame);
                    if (browseFrame != null && browseFrame instanceof android.view.ViewGroup) {
                        android.view.ViewGroup browseFrameGroup = (android.view.ViewGroup) browseFrame;
                        android.widget.ImageView backdrop = browseFrameGroup.findViewById(androidx.leanback.R.id.backdrop_image);
                        
                        // Find info overlay container - it's in the same parent as backdrop_image
                        android.view.ViewGroup overlayContainer = null;
                        if (backdrop != null && backdrop.getParent() instanceof android.view.ViewGroup) {
                            overlayContainer = ((android.view.ViewGroup) backdrop.getParent()).findViewById(androidx.leanback.R.id.backdrop_info_overlay_container);
                        }
                        android.view.View infoOverlay = null;
                        if (overlayContainer != null) {
                            // Check if overlay already exists
                            if (overlayContainer.getChildCount() > 0) {
                                infoOverlay = overlayContainer.getChildAt(0);
                            } else {
                                // Inflate and add overlay
                                android.view.LayoutInflater inflater = android.view.LayoutInflater.from(getContext());
                                infoOverlay = inflater.inflate(R.layout.backdrop_info_overlay, overlayContainer, false);
                                // Ensure width constraint is applied programmatically
                                android.view.ViewGroup.LayoutParams params = infoOverlay.getLayoutParams();
                                if (params == null) {
                                    params = new android.widget.FrameLayout.LayoutParams(
                                        (int) (400 * getResources().getDisplayMetrics().density),
                                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                        android.view.Gravity.TOP | android.view.Gravity.START
                                    );
                                } else {
                                    params.width = (int) (400 * getResources().getDisplayMetrics().density);
                                }
                                infoOverlay.setLayoutParams(params);
                                overlayContainer.addView(infoOverlay);
                            }
                        }
                        
                        com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter bp2 = com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter.instance(getContext());
                        boolean isHome = bp2.getCurrentSection() == null || bp2.isHomeSection();
                        
                        if (!isHome) {
                            // For non-Home sections, backdrop is managed by BrowseFragment.updateBackdropVisibility()
                            // Fade out info overlay smoothly
                            final android.view.ViewGroup finalOverlayContainer = overlayContainer;
                            if (finalOverlayContainer != null && finalOverlayContainer.getVisibility() == android.view.View.VISIBLE) {
                                finalOverlayContainer.animate()
                                        .alpha(0f)
                                        .setDuration(200)
                                        .withEndAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                finalOverlayContainer.setVisibility(android.view.View.GONE);
                                                finalOverlayContainer.setAlpha(1f);
                                            }
                                        })
                                        .start();
                            } else if (finalOverlayContainer != null) {
                                finalOverlayContainer.setVisibility(android.view.View.GONE);
                            }
                            return;
                        }
                        
                        Video v = (Video) item;
                        
                        // Update backdrop image with smooth cross-fade transition
                        if (backdrop != null) {
                            // Enable hardware acceleration for smooth backdrop transitions
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
                                backdrop.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
                            }
                            
                            String url = null;
                            if (v.backdropImageUrl != null && !v.backdropImageUrl.isEmpty()) {
                                url = v.backdropImageUrl;
                            } else if (v.videoId != null) {
                                url = com.liskovsoft.smartyoutubetv2.tv.services.TMDBDataCache.instance(getContext()).getBackdropUrl(v.videoId);
                            }
                            if (url != null && !url.isEmpty()) {
                                com.bumptech.glide.request.RequestOptions backdropOptions = new com.bumptech.glide.request.RequestOptions()
                                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                                        .skipMemoryCache(false)
                                        .centerCrop();
                                
                                com.bumptech.glide.Glide.with(getContext())
                                        .load(url)
                                        .apply(backdropOptions)
                                        .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(300)) // Smooth 300ms cross-fade
                                        .into(backdrop);
                            } else {
                                // Fade out backdrop smoothly
                                backdrop.animate()
                                        .alpha(0f)
                                        .setDuration(200)
                                        .withEndAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                backdrop.setImageDrawable(null);
                                                backdrop.setAlpha(1f);
                                            }
                                        })
                                        .start();
                            }
                        }
                        
                        // Update TMDB info overlay (title and description)
                        if (infoOverlay != null && overlayContainer != null && v.videoId != null) {
                            // Get TMDB detailed info
                            com.liskovsoft.smartyoutubetv2.tv.services.TMDBDetailedMovieInfo detailedInfo = 
                                com.liskovsoft.smartyoutubetv2.tv.services.TMDBDataCache.instance(getContext()).getDetailedMovieInfo(v.videoId);
                            
                            android.widget.TextView titleView = infoOverlay.findViewById(R.id.backdrop_movie_title);
                            android.widget.TextView descriptionView = infoOverlay.findViewById(R.id.backdrop_movie_description);
                            
                            if (detailedInfo != null && detailedInfo.title != null) {
                                // Show overlay with TMDB data
                                if (titleView != null) {
                                    titleView.setText(detailedInfo.title);
                                }
                                if (descriptionView != null) {
                                    if (detailedInfo.overview != null && !detailedInfo.overview.isEmpty()) {
                                        descriptionView.setText(detailedInfo.overview);
                                        descriptionView.setVisibility(android.view.View.VISIBLE);
                                    } else {
                                        descriptionView.setVisibility(android.view.View.GONE);
                                    }
                                }
                                // Fade in overlay smoothly
                                overlayContainer.setAlpha(0f);
                                overlayContainer.setVisibility(android.view.View.VISIBLE);
                                overlayContainer.animate()
                                        .alpha(1f)
                                        .setDuration(250)
                                        .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                                        .start();
                            } else {
                                // Try to use video title if TMDB data not available
                                if (titleView != null && v.getTitle() != null) {
                                    titleView.setText(v.getTitle());
                                    if (descriptionView != null) {
                                        descriptionView.setVisibility(android.view.View.GONE);
                                    }
                                    // Fade in overlay smoothly
                                    overlayContainer.setAlpha(0f);
                                    overlayContainer.setVisibility(android.view.View.VISIBLE);
                                    overlayContainer.animate()
                                            .alpha(1f)
                                            .setDuration(250)
                                            .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                                            .start();
                                } else {
                                    // Fade out overlay smoothly if no data available
                                    final android.view.ViewGroup finalOverlayContainer2 = overlayContainer;
                                    if (finalOverlayContainer2 != null && finalOverlayContainer2.getVisibility() == android.view.View.VISIBLE) {
                                        finalOverlayContainer2.animate()
                                                .alpha(0f)
                                                .setDuration(200)
                                                .withEndAction(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        finalOverlayContainer2.setVisibility(android.view.View.GONE);
                                                        finalOverlayContainer2.setAlpha(1f);
                                                    }
                                                })
                                                .start();
                                    } else if (finalOverlayContainer2 != null) {
                                        finalOverlayContainer2.setVisibility(android.view.View.GONE);
                                    }
                                }
                            }
                        } else if (overlayContainer != null) {
                            // Fade out overlay smoothly if not available
                            final android.view.ViewGroup finalOverlayContainer3 = overlayContainer;
                            if (finalOverlayContainer3.getVisibility() == android.view.View.VISIBLE) {
                                finalOverlayContainer3.animate()
                                        .alpha(0f)
                                        .setDuration(200)
                                        .withEndAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                finalOverlayContainer3.setVisibility(android.view.View.GONE);
                                                finalOverlayContainer3.setAlpha(1f);
                                            }
                                        })
                                        .start();
                            } else {
                                finalOverlayContainer3.setVisibility(android.view.View.GONE);
                            }
                        }
                    }
                }
                mBackgroundManager.setBackgroundFrom((Video) item);

                mMainPresenter.onVideoItemSelected((Video) item);

                checkScrollEnd((Video)item);
            }
        }

        private void checkScrollEnd(Video item) {
            for (VideoGroupObjectAdapter adapter : mVideoGroupAdapters.values()) {
                int index = adapter.indexOf(item);

                if (index != -1) {
                    int size = adapter.size();
                    if (index > (size - ViewUtil.ROW_SCROLL_CONTINUE_NUM)) {
                        mMainPresenter.onScrollEnd((Video) adapter.get(size - 1));
                    }
                    break;
                }
            }
        }
    }

    // Force-hide headers once content receives focus (prevents lingering sidebar)
    // Merge into existing onResume above to avoid duplicates
}
