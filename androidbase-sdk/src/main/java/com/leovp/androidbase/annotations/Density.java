package com.leovp.androidbase.annotations;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntDef({ Density.WIDTH_BASED, Density.HEIGHT_BASED, Density.LONG_SIDE_BASED, Density.SHORT_SIDE_BASED })
@Retention(RetentionPolicy.SOURCE)
public @interface Density {
    int WIDTH_BASED = 1;
    int HEIGHT_BASED = 2;
    int LONG_SIDE_BASED = 3;
    int SHORT_SIDE_BASED = 4;
}
