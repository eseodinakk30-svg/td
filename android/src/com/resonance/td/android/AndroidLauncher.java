package com.resonance.td.android;

import android.os.Bundle;
import android.view.WindowManager;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.resonance.td.ResonanceGame;

/** Точка входа на Android: заворачивает игру в AndroidApplication libGDX. */
public class AndroidLauncher extends AndroidApplication {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        AndroidApplicationConfiguration cfg = new AndroidApplicationConfiguration();
        cfg.useAccelerometer = false;
        cfg.useCompass = false;
        cfg.useGyroscope = false;
        cfg.useImmersiveMode = true;
        cfg.numSamples = 2;   // сглаживание — вся графика векторная
        cfg.useWakelock = false;
        cfg.depth = 0;

        initialize(new ResonanceGame(), cfg);
    }
}
