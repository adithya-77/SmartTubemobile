package com.liskovsoft.smartyoutubetv2.tv.presenter;

import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.ListRowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class CustomListRowPresenter extends ListRowPresenter {
    public CustomListRowPresenter() {
        // Subtle zoom for a premium feel while keeping rows compact
        super(FocusHighlight.ZOOM_FACTOR_SMALL, ViewUtil.FOCUS_DIMMER_ENABLED);
        setSelectEffectEnabled(ViewUtil.ROW_SELECT_EFFECT_ENABLED);
        enableChildRoundedCorners(ViewUtil.ROUNDED_CORNERS_ENABLED);
    }
}
