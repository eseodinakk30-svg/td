package com.resonance.td;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;

/**
 * Звук синтезируется на лету — в игре нет ни одного аудиофайла.
 * Игра ритмическая, поэтому бит идёт от того же метронома, что и башни.
 * Если звуковое устройство не открылось, всё молча выключается.
 */
public class Sfx {

    private static final int RATE = 44100;
    private static final int BUF = 512;
    private static final int VOICES = 24;

    public static final int KICK = 0;
    public static final int HAT = 1;
    public static final int CHIME = 2;
    public static final int BOOM = 3;
    public static final int ALARM = 4;
    public static final int CLICK = 5;

    private AudioDevice device;
    private Thread thread;
    private volatile boolean running;
    public volatile boolean muted;

    private final int[] pending = new int[8];

    // голоса
    private final float[] vFreq = new float[VOICES];
    private final float[] vPhase = new float[VOICES];
    private final float[] vAmp = new float[VOICES];
    private final float[] vDecay = new float[VOICES];
    private final int[] vType = new int[VOICES];
    private final float[] vSweep = new float[VOICES];

    private final float[] buf = new float[BUF];
    private long noise = 0x2545F4914F6CDD1DL;
    private int chimeStep;

    public void start() {
        try {
            device = Gdx.audio.newAudioDevice(RATE, true);
        } catch (Throwable t) {
            device = null;
            return;
        }
        running = true;
        thread = new Thread(new Runnable() {
            public void run() {
                loop();
            }
        }, "resonance-sfx");
        thread.setDaemon(true);
        thread.start();
    }

    public void trigger(int kind) {
        if (device == null) return;
        synchronized (pending) {
            pending[kind]++;
        }
    }

    public void dispose() {
        running = false;
        if (thread != null) {
            try {
                thread.join(400);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        if (device != null) {
            try {
                device.dispose();
            } catch (Throwable ignored) {
            }
            device = null;
        }
    }

    // ------------------------------------------------------------- синтез

    private void loop() {
        while (running) {
            drainEvents();
            fill();
            try {
                device.writeSamples(buf, 0, BUF);
            } catch (Throwable t) {
                running = false;
            }
        }
    }

    private void drainEvents() {
        synchronized (pending) {
            for (int k = 0; k < pending.length; k++) {
                while (pending[k] > 0) {
                    pending[k]--;
                    spawn(k);
                }
            }
        }
    }

    private void spawn(int kind) {
        int v = freeVoice();
        if (v < 0) return;
        vPhase[v] = 0f;
        vType[v] = kind;
        vSweep[v] = 0f;
        switch (kind) {
            case KICK:
                vFreq[v] = 120f;
                vAmp[v] = 0.55f;
                vDecay[v] = 9f;
                vSweep[v] = -170f;
                break;
            case HAT:
                vFreq[v] = 0f;
                vAmp[v] = 0.10f;
                vDecay[v] = 46f;
                break;
            case CHIME: {
                // пентатоника — чтобы «музыка боя» не резала ухо
                int[] scale = {0, 3, 5, 7, 10, 12, 15};
                int st = scale[chimeStep % scale.length];
                chimeStep++;
                vFreq[v] = 440f * (float) Math.pow(2.0, (st + 12) / 12.0);
                vAmp[v] = 0.16f;
                vDecay[v] = 7f;
                break;
            }
            case BOOM:
                vFreq[v] = 200f;
                vAmp[v] = 0.22f;
                vDecay[v] = 15f;
                vSweep[v] = -260f;
                break;
            case ALARM:
                vFreq[v] = 82f;
                vAmp[v] = 0.5f;
                vDecay[v] = 3.6f;
                vSweep[v] = -18f;
                break;
            case CLICK:
                vFreq[v] = 900f;
                vAmp[v] = 0.18f;
                vDecay[v] = 34f;
                break;
            default:
                vAmp[v] = 0f;
        }
    }

    private int freeVoice() {
        int worst = -1;
        float worstAmp = 0.02f;
        for (int i = 0; i < VOICES; i++) {
            if (vAmp[i] <= 0.001f) return i;
            if (vAmp[i] < worstAmp) {
                worstAmp = vAmp[i];
                worst = i;
            }
        }
        return worst;
    }

    private void fill() {
        float dt = 1f / RATE;
        boolean silent = muted;
        for (int i = 0; i < BUF; i++) buf[i] = 0f;
        if (silent) {
            for (int v = 0; v < VOICES; v++) vAmp[v] = 0f;
            return;
        }
        for (int v = 0; v < VOICES; v++) {
            if (vAmp[v] <= 0.001f) continue;
            float f = vFreq[v], a = vAmp[v], ph = vPhase[v];
            int type = vType[v];
            for (int i = 0; i < BUF; i++) {
                float s;
                if (type == HAT) {
                    s = whiteNoise() * 0.6f;
                } else if (type == BOOM) {
                    s = (float) Math.sin(ph) * 0.55f + whiteNoise() * 0.45f;
                } else if (type == ALARM) {
                    s = (float) Math.sin(ph) + (float) Math.sin(ph * 1.5f) * 0.4f;
                } else {
                    s = (float) Math.sin(ph);
                }
                buf[i] += s * a;
                ph += 6.2831855f * f * dt;
                if (ph > 6.2831855f) ph -= 6.2831855f;
                f += vSweep[v] * dt;
                if (f < 20f) f = 20f;
                a -= a * vDecay[v] * dt;
            }
            vFreq[v] = f;
            vPhase[v] = ph;
            vAmp[v] = a;
        }
        for (int i = 0; i < BUF; i++) {
            float s = buf[i] * 0.8f;
            if (s > 1f) s = 1f;
            if (s < -1f) s = -1f;
            buf[i] = s;
        }
    }

    private float whiteNoise() {
        noise ^= noise << 13;
        noise ^= noise >>> 7;
        noise ^= noise << 17;
        return (noise >> 40) / 8388608f;
    }
}
