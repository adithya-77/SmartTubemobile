package com.liskovsoft.smartyoutubetv2.tv.ui.playback.other;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.View;
import androidx.leanback.media.PlaybackGlueHost;
import androidx.leanback.media.PlaybackTransportControlGlue;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.PlaybackControlsRow.MultiAction;
import androidx.leanback.widget.PlaybackRowPresenter;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.MaxControlsVideoPlayerGlue;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.PlaybackTransportRowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.widget.OnActionLongClickedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ActionHelpers;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ClosedCaptioningAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.HighQualityAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.TwoStateAction;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages customizing the actions in the {@link PlaybackControlsRow}. Adds and manages the
 * following actions to the primary and secondary controls:
 *
 * <ul>
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.RepeatAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.ThumbsDownAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.ThumbsUpAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.SkipPreviousAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.SkipNextAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.FastForwardAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.RewindAction}
 * </ul>
 *
 * Note that the superclass, {@link PlaybackTransportControlGlue}, manages the playback controls
 * row.
 */
public class VideoPlayerGlue extends MaxControlsVideoPlayerGlue<PlayerAdapter> implements OnActionLongClickedListener {
    private static final String TAG = VideoPlayerGlue.class.getSimpleName();
    private final Map<Integer, Action> mActions = new HashMap<>();
    private final OnActionClickedListener mActionListener;
    private final PlayerTweaksData mPlayerTweaksData;
    private final GeneralData mGeneralData;
    private int mPreviousAction = KeyEvent.ACTION_UP;

    public VideoPlayerGlue(
            Context context,
            PlayerAdapter playerAdapter,
            OnActionClickedListener actionListener) {
        super(context, playerAdapter);

        mPlayerTweaksData = PlayerTweaksData.instance(getContext());
        mGeneralData = GeneralData.instance(getContext());

        mActionListener = actionListener;

        // Only keep actions for Play/Pause, Captions, and Quality
        putAction(new HighQualityAction(context));
        putAction(new ClosedCaptioningAction(context));
    }

    @Override
    protected void onCreatePrimaryActions(ArrayObjectAdapter adapter) {
        // Only keep Play/Pause button
        super.onCreatePrimaryActions(adapter);
    }

    @Override
    protected void onCreateSecondaryActions(ArrayObjectAdapter adapter) {
        super.onCreateSecondaryActions(adapter);

        // Only keep Quality and Captions buttons
        adapter.add(mActions.get(R.id.lb_control_high_quality));
        adapter.add(mActions.get(R.id.lb_control_closed_captioning));
    }

    @Override
    protected PlaybackRowPresenter onCreateRowPresenter() {
        PlaybackRowPresenter rowPresenter = super.onCreateRowPresenter();

        ((PlaybackTransportRowPresenter) rowPresenter).setOnActionLongClickedListener(this);

        return rowPresenter;
    }

    @Override
    public void onActionClicked(Action action) {
        if (!dispatchAction(action)) {
            // Super class handles play/pause and delegates to abstract methods next()/previous().
            super.onActionClicked(action);
        }
    }

    @Override
    public boolean onActionLongClicked(Action action) {
        return dispatchLongClickAction(action);
    }

    @Override
    public void play() {
        super.play();
    }

    @Override
    public void pause() {
        super.pause();
    }

    @Override
    public void next() {
        mActionListener.onNext();
    }

    @Override
    public void previous() {
        mActionListener.onPrevious();
    }

    public void togglePlayback() {
        if (isPlaying()) {
            pause();
        } else {
            play();
        }
    }


    public void setButtonState(int buttonId, int buttonState) {
        setActionIndex(mActions.get(buttonId), buttonState);
    }

    // Stub methods for compatibility (methods removed but still called from PlaybackFragment)
    public void rewind() {
        // Removed - no longer used
    }

    public void fastForward() {
        // Removed - no longer used
    }

    public void setChannelIcon(String iconUrl) {
        // Removed - no longer used
    }

    public void setNextTitle(CharSequence title) {
        // Removed - no longer used
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (!isSingleKeyDown(event.getAction())) {
            return false;
        }

        boolean handled = mActionListener.onKeyDown(keyCode);

        if (!handled) {
            Action action = findAction(keyCode);

            handled = dispatchAction(action);
        }

        // Ignore result to give a chance to handle this event in
        // com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.maxcontrols.PlaybackTransportRowPresenter.ViewHolder
        return handled || super.onKey(v, keyCode, event);
    }

    /**
     * Fixing sticky key press? <br/>
     * Notify key down only when there are paired action available.
     */
    private boolean isSingleKeyDown(int action) {
        boolean result = action == KeyEvent.ACTION_DOWN && mPreviousAction == KeyEvent.ACTION_UP;
        mPreviousAction = action;
        return result;
    }

    private boolean dispatchAction(Action action) {
        if (action == null) {
            return false;
        }

        if (checkShortActionDisabled(action)) {
            return true;
        }

        boolean handled = false;

        // Handle actions
        if (mActions.containsKey((int) action.getId())) {
            mActionListener.onAction((int) action.getId(), getActionIndex(action));
            handled = true;
        }

        if (handled) {
            invalidateUi(action);

            if (action instanceof TwoStateAction) {
                invalidateUi(((TwoStateAction) action).getBoundAction());
            }
        }

        return handled;
    }

    private boolean dispatchLongClickAction(Action action) {
        if (action == null) {
            return false;
        }

        if (checkLongActionDisabled(action)) {
            return false;
        }

        boolean handled = false;

        if (mActions.containsKey((int) action.getId())) {
            mActionListener.onLongAction((int) action.getId(), getActionIndex(action));
            handled = true;
        }

        return handled;
    }

    private int getActionIndex(Action action) {
        if (action instanceof PlaybackControlsRow.MultiAction) {
            PlaybackControlsRow.MultiAction multiAction = (PlaybackControlsRow.MultiAction) action;
            return multiAction.getIndex();
        }

        return 0;
    }

    private void incrementActionIndex(Action action) {
        if (action instanceof PlaybackControlsRow.MultiAction) {
            PlaybackControlsRow.MultiAction multiAction = (PlaybackControlsRow.MultiAction) action;
            multiAction.nextIndex();
        }
    }

    /**
     * Properly handle ui changes of multi-action buttons
     */
    private void invalidateUi(Action action) {
        if (action != null) {
            // Notify adapter of action changes to handle primary actions, such as, play/pause.
            notifyActionChanged(
                    action,
                    (ArrayObjectAdapter) getControlsRow().getPrimaryActionsAdapter());

            // Notify adapter of action changes to handle secondary actions, such as, thumbs up/down and repeat.
            notifyActionChanged(
                    action,
                    (ArrayObjectAdapter) getControlsRow().getSecondaryActionsAdapter());
        }
    }

    private void notifyActionChanged(
            Action action, ArrayObjectAdapter adapter) {
        if (adapter != null) {
            int index = adapter.indexOf(action);
            if (index >= 0) {
                adapter.notifyArrayItemRangeChanged(index, 1);
            }
        }
    }

    private void removePrimaryAction(Action action) {
        ArrayObjectAdapter adapter = (ArrayObjectAdapter) getControlsRow().getPrimaryActionsAdapter();
        if (adapter != null) {
            adapter.remove(action);
        }
    }

    private void removeSecondaryAction(Action action) {
        ArrayObjectAdapter adapter = (ArrayObjectAdapter) getControlsRow().getSecondaryActionsAdapter();
        if (adapter != null) {
            adapter.remove(action);
        }
    }

    private void addPrimaryAction(Action action, int position) {
        ArrayObjectAdapter adapter = (ArrayObjectAdapter) getControlsRow().getPrimaryActionsAdapter();
        addAction(action, position, adapter);
    }

    private void addSecondaryAction(Action action, int position) {
        ArrayObjectAdapter adapter = (ArrayObjectAdapter) getControlsRow().getSecondaryActionsAdapter();
        addAction(action, position, adapter);
    }

    private void addAction(Action action, int position, ArrayObjectAdapter adapter) {
        if (adapter != null) {
            int index = adapter.indexOf(action);
            if (index == -1) {
                int size = adapter.size();
                adapter.add(Math.min(position, size), action);
            }
        }
    }

    private Action findAction(int keyCode) {
        Action action = null;
        PlaybackControlsRow controlsRow = getControlsRow();

        if (controlsRow != null) {
            final ObjectAdapter primaryActionsAdapter = controlsRow.getPrimaryActionsAdapter();
            action = controlsRow.getActionForKeyCode(primaryActionsAdapter, keyCode);

            if (action == null) {
                action = controlsRow.getActionForKeyCode(controlsRow.getSecondaryActionsAdapter(),
                        keyCode);
            }
        }

        return action;
    }

    private void putAction(Action action) {
        mActions.put((int) action.getId(), action);
    }

    private void setActionIndex(Action action, int actionIndex) {
        if (actionIndex == -1) { // button disabled
            disableAction(action);
        } else if (action instanceof MultiAction) {
            ((MultiAction) action).setIndex(actionIndex);
            invalidateUi(action);
        }
    }

    private void disableAction(Action action) {
        Drawable icon = action.getIcon();
        action.setIcon(ActionHelpers.createDrawable(getContext(), (BitmapDrawable) icon, ActionHelpers.getIconGrayedOutColor(getContext())));
        invalidateUi(action);
    }

    /**
     * Long press actions usually more important than short ones. So, try to use it first in case long click is disabled.
     */
    private boolean checkShortActionDisabled(Action action) {
        if (!mGeneralData.isOkButtonLongPressDisabled() && mPlayerTweaksData.isButtonLongClickEnabled()) {
            return false;
        }

        return action == mActions.get(R.id.lb_control_closed_captioning) &&
                dispatchLongClickAction(action); // replace short with long
    }

    private boolean checkLongActionDisabled(Action action) {
        if (!mGeneralData.isOkButtonLongPressDisabled() && mPlayerTweaksData.isButtonLongClickEnabled()) {
            return false;
        }

        return false; // No long click actions needed
    }

    @Override
    protected void onAttachedToHost(PlaybackGlueHost host) {
        super.onAttachedToHost(host);

        Log.d(TAG, "On attached to host");
    }

    @Override
    public void onTopEdgeFocused() {
        mActionListener.onTopEdgeFocused();
    }

    /** Listens for when skip to next and previous actions have been dispatched. */
    public interface OnActionClickedListener {
        /** Skip to the previous item in the queue. */
        void onPrevious();

        /** Skip to the next item in the queue. */
        void onNext();

        void onPlay();

        void onPause();

        void onAction(int actionId, int actionIndex);

        void onLongAction(int actionId, int actionIndex);

        void onTopEdgeFocused();

        boolean onKeyDown(int keyCode);
    }
}
