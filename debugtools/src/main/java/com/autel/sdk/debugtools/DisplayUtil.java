package com.autel.sdk.debugtools;

import android.content.res.Resources;

/**
 * dp px sp conversion utility
 * Copyright: Autel Robotics
 *
 * @author Autel Robotics on 2021/8/14
 */
public class DisplayUtil {


    /**
     * Convert dp to px (pixels) using the device resolution
     */
    public static int dip2px(float dpValue) {
        final float scale = Resources.getSystem().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

}
