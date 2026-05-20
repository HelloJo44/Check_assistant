package com.example.floatingbuttonapp;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

public class ScriptNotifier {
    private static final String TAG = "ScriptNotifier";

    public static void signalerFin(Context context, int nombreDeFois) {
        // --- 1. AFFICHAGE DU TOAST (Sur le thread principal) ---
        new Handler(Looper.getMainLooper()).post(() -> {
            String message = (nombreDeFois == 1) ? "Nettoyeur terminé ✅" : "Expert terminé ✅";
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        });

        // --- 2. SONS ET VIBRATIONS (Sur un thread séparé) ---

        new Thread(() -> {
            try {
                ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_SYSTEM, 100);
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

                if (nombreDeFois == 1) {
                    // Signal Nettoyeur
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 150);
                    vibrer(vibrator, 200);
                } else {
                    // Signal Expert (3 notes mélodiques)
                    int[] sonsExpert = {ToneGenerator.TONE_CDMA_PIP, ToneGenerator.TONE_CDMA_PIP, ToneGenerator.TONE_CDMA_CONFIRM};
                    for (int i = 0; i < 3; i++) {
                        toneGen.startTone(sonsExpert[i], 150);
                        vibrer(vibrator, i == 2 ? 300 : 70); // Vibration plus longue sur la fin
                        Thread.sleep(250);
                    }
                }

                Thread.sleep(500);
                toneGen.release();
            } catch (Exception e) {
                Log.e(TAG, "Erreur signal: " + e.getMessage());
            }
        }).start();
    }

    private static void vibrer(Vibrator v, long duree) {
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(duree, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(duree);
            }
        }
    }
}