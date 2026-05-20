package com.example.floatingbuttonapp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
    }

    private void checkPermissions() {
        // 1. Vérifier la superposition (Overlay)
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Étape 1 : Autorisez la superposition", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
        // 2. Vérifier l'Accessibilité
        else if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Étape 2 : Activez 'Mon Assistant' dans Services téléchargés", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        }
        // 3. Vérifier l'Optimisation Batterie (Vital pour Xiaomi)
        else if (!isIgnoringBatteryOptimizations()) {
            Toast.makeText(this, "Étape 3 : Sélectionnez 'Pas de restriction' (Batterie)", Toast.LENGTH_LONG).show();
            requestBatteryIgnore();
        }
        // 4. Tout est OK
        else {
            // C'est ici qu'on dit au Service : "Eh ! Affiche le bouton !"
            Intent intent = new Intent("com.example.floatingbuttonapp.ACTION_SHOW_BUTTON");
            intent.setPackage(getPackageName()); // Sécurité : reste dans l'app
            sendBroadcast(intent);

            Toast.makeText(this, "Assistant prêt !", Toast.LENGTH_SHORT).show();
            finish(); // On ferme l'écran de l'app, le service continue en fond
        }
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }

    private void requestBatteryIgnore() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                // Sur certains Xiaomi, l'action directe plante, on ouvre les paramètres globaux
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String serviceId = getPackageName() + "/" + FloatingButtonService.class.getName();
        String enabledServices = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        if (TextUtils.isEmpty(enabledServices)) return false;

        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            if (splitter.next().equalsIgnoreCase(serviceId)) {
                return true;
            }
        }
        return false;
    }
}