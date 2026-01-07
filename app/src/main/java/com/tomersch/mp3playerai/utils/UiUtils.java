package com.tomersch.mp3playerai.utils;

import android.content.Context;

public final class UiUtils {
    private UiUtils() {}

    public static int dp(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
