package com.example.floatingbuttonapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.app.AlertDialog; // NOUVEAU POUR LA CONFIRMATION
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;

import java.util.List;

public class FloatingButtonService extends AccessibilityService {

    // NOMS DES SIGNAUX
    public static final String ACTION_SHOW_BUTTON = "com.example.floatingbuttonapp.ACTION_SHOW_BUTTON";
    public static final String ACTION_STOP_SERVICE = "com.example.floatingbuttonapp.ACTION_STOP_SERVICE";
    private static final String TAG = "SERVICE_MAIN";

    private WindowManager windowManager;
    private View floatingView;
    private View trashLayout;
    private WindowManager.LayoutParams paramsBouton;
    private volatile boolean isTTRunning = false; // Contrôle la séquence globale TT

    // --- NOS DEUX SCRIPTS ---
    private ExpertValideScript expertScript;
    private NettoyeurScript nettoyeurScript;

    private boolean isButtonVisible = false;
    private boolean isUserHidden = false; // Bloque la réapparition automatique
    private int screenHeight;
    private int screenWidth;

    // --- declaration des boutons
    private View buttonTout;   // Bouton TT
    private View buttonReplie; // Bouton Nettoyeur
    private View buttonBonOk;  // Bouton BonOk (Ajouté)
    private View buttonStop;      // Bouton Stop

    // --- LE RÉCEPTEUR D'ORDRES ---
    private final BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null) return;

            if (intent.getAction().equals(ACTION_SHOW_BUTTON)) {
                if (floatingView != null) {
                    paramsBouton.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                    paramsBouton.x = 0;
                    paramsBouton.y = 25;
                    windowManager.updateViewLayout(floatingView, paramsBouton);
                    floatingView.setVisibility(View.VISIBLE);
                    isButtonVisible = true;
                    isUserHidden = false;
                }
            }
            else if (intent.getAction().equals(ACTION_STOP_SERVICE)) {
                stopEverythingAndKill();
            }
        }
    };
    /**
     * Vérifie si l'utilisateur est sur la page de saisie (présence du bouton retour).
     * Basé sur le marqueur XML : content-desc="Revenir en haut de la page"
     */
    private boolean estSurLaPageDeSaisie() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        // On cherche l'élément qui a la description spécifique
        return chercherParContentDesc(root, "Revenir en haut de la page");
    }

    /**
     * Recherche récursive d'un noeud par son Content Description.
     */
    private boolean chercherParContentDesc(AccessibilityNodeInfo node, String cible) {
        if (node == null) return false;

        CharSequence desc = node.getContentDescription();
        if (desc != null && cible.equals(desc.toString())) {
            return true;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (chercherParContentDesc(node.getChild(i), cible)) return true;
        }
        return false;
    }
    /**
     * AFFICHE LA BOÎTE DE DIALOGUE DE CONFIRMATION.
     * Avertit l'utilisateur de ne pas toucher l'écran avant de lancer la magie.
     * Nécessite de configurer le type de fenêtre de l'alerte car nous sommes dans un Service.
     */
/**    private void afficherConfirmationLancement() {
        // On doit exécuter cela sur le thread principal (UI)
        new Handler(Looper.getMainLooper()).post(() -> {
            // Utilisation d'un thème sombre standard pour la boîte de dialogue
            AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
            builder.setTitle("🧙‍♂️ Lancement de la séquence");
            builder.setMessage("Ne touchez pas à l'écran pendant que la magie opère !");
            builder.setCancelable(true); // Permet d'annuler en tapant à côté

            // Bouton de confirmation qui lance la vraie méthode
            builder.setPositiveButton("DÉMARRER LA MAGIE ✨", (dialog, which) -> {
                dialog.dismiss();
                Log.d(TAG, "🟢 Confirmation reçue : Lancement séquence complète.");
                lancerSequenceComplete();
            });

            // Bouton d'annulation
            builder.setNegativeButton("Annuler", (dialog, which) -> dialog.dismiss());

            AlertDialog dialog = builder.create();

            // CRUCIAL : Définir le type de fenêtre pour qu'elle s'affiche au-dessus des autres apps
            // (Comme le bouton flottant lui-même)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            } else {
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
            }

            dialog.show();
        });
    }*/

    /**
     * LANCE LA SÉQUENCE COMPLÈTE (SÉCURITÉ MAXIMALE).
     * 1. Lance le Nettoyeur pour fermer toutes les catégories.
     * 2. Attend la fin du nettoyage.
     * 3. Lance l'Expert Valide sur une page propre.
     */
    private void lancerSequenceComplete() {
        new Thread(() -> {
            try {
                isTTRunning = true; // 🟢 On active le signal pour permettre l'exécution

                executerSaisiePiece();

                showToast("✅ Pièce traitée avec succès !");
            } catch (InterruptedException e) {
                Log.e(TAG, "Erreur sequence complete: " + e.getMessage());
            } finally {
                isTTRunning = false; // ⚪ On libère le signal à la fin
            }
        }).start();
    }

    /**
     * OBSERVATEUR D'ÉVÉNEMENTS (Accessibility).
     * Gère la visibilité du bouton selon l'application au premier plan.
     */
/*    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            String pkg = (event.getPackageName() != null) ? event.getPackageName().toString() : "";
            String TARGET_APP = "com.checkandvisit.android.checkapp";
// On ne réagit que sur les changements de fenêtres


            if (pkg.equals(TARGET_APP)) {
                boolean surAccueil = estSurPageAccueil();
                boolean surSaisie = estSurLaPageDeSaisie();

                // --- GESTION DES BOUTONS ---

                // 1. Bouton TT (Seulement Accueil)
                if (buttonTout != null) {
                    buttonTout.setEnabled(surAccueil);
                    buttonTout.setAlpha(surAccueil ? 1.0f : 0.3f);
                }

                // 2. Boutons Nettoyeur ET BonOk (Seulement Saisie/Pièce)
                if (buttonReplie != null && buttonBonOk != null) {
                    boolean actif = surSaisie;

                    buttonReplie.setEnabled(actif);
                    buttonReplie.setAlpha(actif ? 1.0f : 0.3f);

                    buttonBonOk.setEnabled(actif);
                    buttonBonOk.setAlpha(actif ? 1.0f : 0.3f);
                }

                // 3. Bouton STOP (Toujours actif)
                if (buttonStop != null) {
                    buttonStop.setEnabled(true);
                    buttonStop.setAlpha(1.0f);
                }
                if (!isButtonVisible && !isUserHidden) showFloatingButton();

            }
            // --- PROTECTION : On ne fait rien si c'est le système ou notre propre app ---
            else if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui") || pkg.contains("launcher")) {
                // On ignore ces changements pour éviter de cacher le bouton quand on baisse le rideau de notifications
                return;
            }
            // 2. PROTECTION : SI C'EST LE SYSTÈME (Notifications, Volume, etc.)

            // --- CAS : AUTRE APPLICATION ---
            else {
                if (isButtonVisible) {
                    hideFloatingButton();
                }
                // Arrêt automatique SANS message d'alerte
                arretUrgenceScripts(false);
            }
        }
    }*/
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        int eventType = event.getEventType();
        String pkg = (event.getPackageName() != null) ? event.getPackageName().toString() : "";
        String TARGET_APP = "com.checkandvisit.android.checkapp";

        // 1. GESTION DE LA VISIBILITÉ (Seulement sur changement d'application/fenêtre)
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            if (pkg.equals(TARGET_APP)) {
                // On est bien sur CheckApp
                if (!isButtonVisible && !isUserHidden) {
                    Log.d("TT_DEBUG", "✅ CheckApp détectée -> Affichage");
                    showFloatingButton();
                }
            }
            else if (pkg.isEmpty() || pkg.equals("android") || pkg.equals("com.android.systemui")
                    ||  pkg.equals(getPackageName())) {
                // --- CAS CRUCIAL : TRANSITION OU SYSTÈME ---
                // Si c'est un Toast, le Bureau, ou une fenêtre système : ON NE FAIT RIEN.
                // On laisse le bouton dans son état actuel (S'il était affiché, il reste affiché).
                Log.d("TT_DEBUG", "⚙️ Transition/Système (" + pkg + ") -> On ne change rien");
            }
            else {
                // On est VRAIMENT sur une autre application (Paramètres, Chrome, etc.)
                Log.d("TT_DEBUG", "❌ Autre application (" + pkg + ") -> Masquage");
                hideFloatingButton();
                arretUrgenceScripts(false);
            }
        }

        // 2. MISE À JOUR DES BOUTONS (Grillage/Activation)
        // On le fait sur STATE_CHANGED et CONTENT_CHANGED pour être réactif
        if (isButtonVisible && (pkg.equals(TARGET_APP) || pkg.isEmpty())) {
            actualiserEtatDesBoutons();
        }
    }

    // Pour simplifier le code, on sort la logique de grillage des boutons ici
    private void actualiserEtatDesBoutons() {
        boolean surAccueil = estSurPageAccueil();
        boolean surSaisie = estSurLaPageDeSaisie();

        if (buttonTout != null) {
            buttonTout.setEnabled(surAccueil);
            buttonTout.setAlpha(surAccueil ? 1.0f : 0.3f);
        }
        if (buttonReplie != null && buttonBonOk != null) {
            buttonReplie.setEnabled(surSaisie);
            buttonReplie.setAlpha(surSaisie ? 1.0f : 0.3f);
            buttonBonOk.setEnabled(surSaisie);
            buttonBonOk.setAlpha(surSaisie ? 1.0f : 0.3f);
        }
    }
    // Méthodes d'aide pour la clarté
    private void showFloatingButton() {
        if (floatingView != null) {
            floatingView.setVisibility(View.VISIBLE);
            isButtonVisible = true;
        }
    }

    private void hideFloatingButton() {
        if (floatingView != null) {
            floatingView.setVisibility(View.GONE);
            isButtonVisible = false;
        }
    }

    /**
     * ARRÊT D'URGENCE.
     * Stoppe immédiatement tous les threads des scripts.
     * Utilisé par le bouton STOP et lors de la sortie de l'application cible.
     */
    // On ajoute un paramètre 'manuel' pour savoir si c'est l'utilisateur qui a cliqué sur STOP

    private void arretUrgenceScripts(boolean estManuel) {
        // On vérifie si quelque chose est VRAIMENT en train de tourner
        boolean unScriptTourne = isTTRunning ||
                (nettoyeurScript != null && nettoyeurScript.isRunning()) ||
                (expertScript != null && expertScript.isRunning());

        // Si rien ne tourne, on sort en silence
        if (!unScriptTourne) {
            isTTRunning = false; // Sécurité supplémentaire
            return;
        }

        // Sinon, on arrête tout
        isTTRunning = false;
        if (nettoyeurScript != null) nettoyeurScript.stop();
        if (expertScript != null) expertScript.stop();

        // On n'affiche le Toast que si l'utilisateur a cliqué sur STOP
        if (estManuel) {
            showToast("🛑 Arrêt manuel demandé");
        } else {
            Log.d(TAG, "🔇 Scripts stoppés automatiquement (sortie de l'app cible)");
        }
    }

    @Override
    public void onInterrupt() {
        // Obligatoire pour AccessibilityService
    }

    /**
     * INITIALISATION DU SERVICE.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SHOW_BUTTON);
        filter.addAction(ACTION_STOP_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(commandReceiver, filter);
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenHeight = metrics.heightPixels;
        screenWidth = metrics.widthPixels;
    }

    /**
     * CONNEXION DU SERVICE.
     * Instanciation des scripts et préparation de l'interface.
     */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Service Connecté !");
        updateNotification();
        nettoyeurScript = new NettoyeurScript(this, screenWidth, screenHeight);
        expertScript = new ExpertValideScript(this, screenWidth, screenHeight);
        if (floatingView == null) {
            creationPoubelle();
            creationBoutonPiece();
        }
    }

    /**
     * DESTRUCTION DU SERVICE.
     * Nettoyage des ressources.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(commandReceiver); } catch (Exception e) {}
        arretUrgenceScripts(true);
        if (floatingView != null) windowManager.removeView(floatingView);
        if (trashLayout != null) windowManager.removeView(trashLayout);
    }

    /**
     * CRÉATION DE LA ZONE POUBELLE.
     */
    private void creationPoubelle() {
        trashLayout = LayoutInflater.from(this).inflate(R.layout.layout_trash_bin, null);
        trashLayout.setVisibility(View.GONE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM;
        windowManager.addView(trashLayout, params);
    }

    /**
     * CRÉATION DU WIDGET FLOTTANT.
     * Gère l'affichage des 3 boutons et leurs interactions tactiles.
     */
    private void creationBoutonPiece() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        paramsBouton = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        paramsBouton.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        paramsBouton.x = 0; paramsBouton.y = 25;

        windowManager.addView(floatingView, paramsBouton);
        floatingView.setVisibility(View.GONE);

        // Récupération des 3 boutons
        buttonReplie = floatingView.findViewById(R.id.button_replie);
        buttonStop = floatingView.findViewById(R.id.button_stop); // NOUVEAU
        buttonBonOk = floatingView.findViewById(R.id.button_bon_ok);
        buttonTout = floatingView.findViewById(R.id.button_tt_piece);
        View.OnTouchListener sharedTouchListener = new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging;
            private static final int CLICK_THRESHOLD = 20;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = paramsBouton.x;
                        initialY = paramsBouton.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        trashLayout.setVisibility(View.VISIBLE);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - initialTouchX) > CLICK_THRESHOLD ||
                                Math.abs(event.getRawY() - initialTouchY) > CLICK_THRESHOLD) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            paramsBouton.x = initialX + (int) (event.getRawX() - initialTouchX);
                            paramsBouton.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(floatingView, paramsBouton);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        trashLayout.setVisibility(View.GONE);
                        if (isDragging) {
                            // Gestion de la poubelle
                            if (event.getRawY() > (screenHeight - 250)) {
                                // 1. ARRÊT DES SCRIPTS (Comme le bouton STOP)
                                Log.d(TAG, "🗑️ Widget jeté à la poubelle : Arrêt d'urgence des scripts.");
                                arretUrgenceScripts(true);
                                // 1. Activer le verrou
                                isUserHidden = true;
                                // 2. MASQUAGE DU WIDGET
                                floatingView.setVisibility(View.GONE);
                                isButtonVisible = false;

                                // 3. REINITIALISER LA POSITION (pour la prochaine fois)
                                paramsBouton.x = 0;
                                paramsBouton.y = 25;
                                paramsBouton.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                                windowManager.updateViewLayout(floatingView, paramsBouton);


                                Toast.makeText(FloatingButtonService.this, "Assistant masqué (Relancez l'app pour le revoir)", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // --- GESTION DES CLICS (Optimisée) ---
                            int id = v.getId();

                            if (id == R.id.button_replie) {
                                // Bouton Nettoyeur simple (Pas de confirmation demandée pour lui)
                                if (estSurLaPageDeSaisie()) {
                                    if (nettoyeurScript != null) nettoyeurScript.lancer();
                                } else {
                                    Toast.makeText(FloatingButtonService.this, "⚠️ Erreur : Allez dans une pièce pour nettoyer", Toast.LENGTH_SHORT).show();
                                }

                            } else if (id == R.id.button_tt_piece) {
                                // Bouton TT : On appelle la confirmation avec le script "Toutes Pieces"
                                Log.d(TAG, "🟣 Clic TT : Demande confirmation.");
                                demanderConfirmation(
                                        "🚀 Séquence Automatique (X pièces)",
                                        "Le script va traiter toute la liste.\n⚠️ NE TOUCHEZ PAS À L'ÉCRAN.\n\nConfirmer le lancement ?",
                                        () -> lancerSequenceToutesPieces() // <--- L'action à faire
                                );

                            } else if (id == R.id.button_stop) {
                                // Bouton STOP
                                arretUrgenceScripts(true);
                                // Le toast est déjà géré dans arretUrgenceScripts(true), pas besoin de le remettre ici

                            } else if (id == R.id.button_bon_ok) {
                                // Bouton BonOK : On appelle la confirmation avec le script "Complete" (1 pièce)
                                if (estSurLaPageDeSaisie()) {
                                    Log.d(TAG, "🟢 Clic BonOK : Demande confirmation.");
                                    demanderConfirmation(
                                            "🧙‍♂️ Validation Pièce Unique",
                                            "Lancement de la validation pour CETTE pièce uniquement.\nNe touchez pas à l'écran.",
                                            () -> lancerSequenceComplete() // <--- L'action à faire
                                    );
                                } else {
                                    Log.w(TAG, "⚠️ Tentative de lancement hors page de saisie.");
                                    Toast.makeText(FloatingButtonService.this, "⚠️ Action impossible ici.\nOuvrez d'abord une pièce.", Toast.LENGTH_LONG).show();


                                }
                            }
                        }
                        return true;
                }
                return false;
            }
        };

        // On attache le listener aux 3 boutons
        buttonReplie.setOnTouchListener(sharedTouchListener);
        buttonStop.setOnTouchListener(sharedTouchListener); // NOUVEAU
        buttonBonOk.setOnTouchListener(sharedTouchListener);
        buttonTout.setOnTouchListener(sharedTouchListener);
    }

    /**
     * MISE À JOUR DE LA NOTIFICATION.
     */
    private void updateNotification() {
        createNotificationChannel();
        Intent stopIntent = new Intent(ACTION_STOP_SERVICE);
        stopIntent.setPackage(getPackageName());
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, "CHANNEL_ID_ASSISTANT")
                .setContentTitle("Assistant Nettoyage")
                .setContentText("Service actif")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "ARRÊTER", stopPendingIntent)
                .build();
        startForeground(1, notification);
    }

    /**
     * CRÉATION DU CANAL DE NOTIFICATION.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(new NotificationChannel(
                        "CHANNEL_ID_ASSISTANT", "Assistant", NotificationManager.IMPORTANCE_LOW));
            }
        }
    }

    /**
     * ARRÊT COMPLET DU SERVICE.
     */
    private void stopEverythingAndKill() {
        arretUrgenceScripts(true);
        stopForeground(true);
        disableSelf();
        stopSelf();
    }
    /**
     * Vérifie si on est sur l'onglet "Pièces" (Accueil)
     */
    private boolean estSurPageAccueil() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        // On cherche l'élément du menu bas qui est sélectionné
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId("com.checkandvisit.android.checkapp:id/menu_rooms");
        if (nodes != null && !nodes.isEmpty()) {
            AccessibilityNodeInfo menuRooms = nodes.get(0);
            boolean isSelected = menuRooms.isSelected();
            menuRooms.recycle();
            root.recycle();
            return isSelected;
        }
        root.recycle();
        return false;
    }


    /**
     * Gère le popup "Localisez la pièce".
     * Remplit le champ si vide et clique sur VALIDER.
     * @return true si une action a été effectuée (ce qui permet de reset le timeout d'attente).
     */
    /**
     * Gère le popup "Localisez la pièce".
     * Remplit le champ "A remplir" si c'est vide ou si c'est le texte d'exemple.
     */
    private boolean gererPopUpLocalisation() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        // 1. Vérifier si le popup est là via le titre
        List<AccessibilityNodeInfo> titles = root.findAccessibilityNodeInfosByText("Localisez la pièce");
        if (titles.isEmpty()) {
            root.recycle();
            return false;
        }

        Log.d("TT_PIECE", "🧩 Pop-up localisation détecté !");
        boolean actionEffectuee = false;

        // 2. Gérer le champ texte
        List<AccessibilityNodeInfo> inputs = root.findAccessibilityNodeInfosByViewId("com.checkandvisit.android.checkapp:id/locate_room_et");
        if (!inputs.isEmpty()) {
            AccessibilityNodeInfo input = inputs.get(0);

            // On récupère le texte actuel
            CharSequence seq = input.getText();
            String currentText = (seq != null) ? seq.toString().trim() : "";

            // LE TEXTE "GRISE" A DÉTECTER
            String placeholder = "(exemple : à droite de la salle de bain)";

            // CONDITION : Si vide OU si c'est le texte d'exemple par défaut
            // J'ajoute startsWith("(exemple") par sécurité au cas où le texte changerait légèrement
            if (currentText.isEmpty() || currentText.equals(placeholder) || currentText.startsWith("(exemple")) {

                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "A remplir");
                input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);

                Log.d("TT_PIECE", "✍️ Champ vide ou par défaut détecté -> Ecriture de 'A remplir'.");
                try { Thread.sleep(500); } catch (InterruptedException e) {}
                actionEffectuee = true;
            } else {
                Log.d("TT_PIECE", "✍️ Une localisation valide existe déjà : " + currentText);
            }
        }

        // 3. Cliquer sur VALIDER
        List<AccessibilityNodeInfo> buttons = root.findAccessibilityNodeInfosByViewId("android:id/button1");
        if (!buttons.isEmpty()) {
            AccessibilityNodeInfo btn = buttons.get(0);
            if (btn.isVisibleToUser()) {
                btn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d("TT_PIECE", "✅ Validation du popup.");
                actionEffectuee = true;
            }
        }

        root.recycle();
        return actionEffectuee;
    }



    /**
     * Séquence Étape 1 : Traiter la première pièce trouvée
     */
    private void lancerSequenceToutesPieces() {
        if (isTTRunning) return;

        new Thread(() -> {
            try {
                isTTRunning = true;
                showToast("⏳ Attente de l'application...");
                Log.d("TT_PIECE", "⏳ Attente de stabilisation de l'écran...");
                Thread.sleep(1000);

                // --- ÉTAPE 1 : ATTENDRE QUE L'APP SOIT PRÊTE ---
                // On attend que le conteneur des pièces soit affiché (max 10 secondes)
                boolean prete = attendreAffichageId("com.checkandvisit.android.checkapp:id/room_bottom_ll", 10);

                if (!prete || !isTTRunning) {
                    showToast("❌ Erreur : Application non détectée");
                    Log.e("TT_PIECE", "❌ Impossible de récupérer l'écran");
                    isTTRunning = false;
                    return;
                }


                // --- ÉTAPE 2 : COMPTER ET TRIER LES PIÈCES ---
                AccessibilityNodeInfo rootInitial = getRootInActiveWindow();
                if (rootInitial == null) return;

                // On récupère la liste brute
                List<AccessibilityNodeInfo> piecesVisibles = rootInitial.findAccessibilityNodeInfosByViewId("com.checkandvisit.android.checkapp:id/room_bottom_ll");

                // Si la liste est vide, on tente de chercher le parent si le bottom_ll n'est pas trouvé
                if (piecesVisibles.isEmpty()) {
                    piecesVisibles = rootInitial.findAccessibilityNodeInfosByViewId("com.checkandvisit.android.checkapp:id/room_parent");
                }

                // --- LE CORRECTIF TABLETTE : LE TRI VISUEL ---
                // On transforme la liste immuable en liste modifiable pour pouvoir la trier
                List<AccessibilityNodeInfo> piecesTriees = new java.util.ArrayList<>(piecesVisibles);

                if (!piecesTriees.isEmpty()) {
                    java.util.Collections.sort(piecesTriees, (a, b) -> {
                        Rect rA = new Rect(); a.getBoundsInScreen(rA);
                        Rect rB = new Rect(); b.getBoundsInScreen(rB);

                        // 1. On compare la hauteur (Lignes) avec une tolérance de 50px
                        int diffY = rA.top - rB.top;
                        if (Math.abs(diffY) > 50) {
                            return Integer.compare(rA.top, rB.top); // Celui du haut gagne
                        }
                        // 2. Si même ligne, on compare la gauche (Colonnes)
                        return Integer.compare(rA.left, rB.left); // Celui de gauche gagne
                    });
                }

                int totalPieces = piecesTriees.size();
                rootInitial.recycle(); // On libère le root, on a copié les noeuds dans piecesTriees

                if (totalPieces == 0) {
                    showToast("❌ Aucune pièce à traiter");
                    isTTRunning = false;
                    return;
                }

                // --- LOGS : AFFICHER LES NOMS DES PIÈCES ---
                Log.d("TT_PIECE", "📋 --- LISTE DES PIÈCES DÉTECTÉES (" + totalPieces + ") ---");
                for (int k = 0; k < totalPieces; k++) {
                    AccessibilityNodeInfo p = piecesTriees.get(k);
                    Rect r = new Rect();
                    p.getBoundsInScreen(r);

                    // On cherche le nom à l'intérieur pour l'afficher
                    String nom = "Inconnu";
                    List<AccessibilityNodeInfo> tvs = p.findAccessibilityNodeInfosByViewId("com.checkandvisit.android.checkapp:id/room_name_tv");
                    if (!tvs.isEmpty() && tvs.get(0).getText() != null) {
                        nom = tvs.get(0).getText().toString();
                    }

                    Log.d("TT_PIECE", "   📍 Index " + k + " : " + nom + " (X=" + r.centerX() + ", Y=" + r.centerY() + ")");
                }
                Log.d("TT_PIECE", "------------------------------------------------");

                // --- ÉTAPE 3 : LA BOUCLE FOR ---
                for (int i = 0; i < totalPieces; i++) {
                    if (!isTTRunning) break; // Sortie immédiate si bouton STOP

                    showToast("🏠 Pièce " + (i + 1) + " / " + totalPieces);

                    // 1. On rafraîchit la vue
                    AccessibilityNodeInfo currentRoot = getRootInActiveWindow();
                    if (currentRoot == null) continue;

                    // 2. On RÉCUPÈRE et on RETRIE les pièces (Indispensable pour garder l'ordre visuel)
                    List<AccessibilityNodeInfo> raw = currentRoot.findAccessibilityNodeInfosByViewId("com.checkandvisit.android.checkapp:id/room_bottom_ll");

                    // --- PETIT AJOUT DE SÉCURITÉ ICI (Même logique qu'au début) ---
                    if (raw.isEmpty()) {
                        raw = currentRoot.findAccessibilityNodeInfosByViewId("com.checkandvisit.android.checkapp:id/room_parent");
                    }
                    // -------------------------------------------------------------

                    List<AccessibilityNodeInfo> currentSorted = new java.util.ArrayList<>(raw);

                    java.util.Collections.sort(currentSorted, (a, b) -> {
                        Rect rA = new Rect(); a.getBoundsInScreen(rA);
                        Rect rB = new Rect(); b.getBoundsInScreen(rB);
                        int diffY = rA.top - rB.top;
                        if (Math.abs(diffY) > 50) return Integer.compare(rA.top, rB.top);
                        return Integer.compare(rA.left, rB.left);
                    });

                    // 3. On clique sur la i-ème pièce TRIÉE
                    if (i < currentSorted.size()) {
                        AccessibilityNodeInfo pieceCible = currentSorted.get(i);

                        // 1. On récupère le nom de la pièce pour le log
                        List<AccessibilityNodeInfo> names = pieceCible.findAccessibilityNodeInfosByViewId("com.checkandvisit.android.checkapp:id/room_name_tv");
                        String nomPiece = (!names.isEmpty() && names.get(0).getText() != null) ? names.get(0).getText().toString() : "Inconnue";

                        Rect rectInitial = new Rect();
                        pieceCible.getBoundsInScreen(rectInitial);
                        Log.d("TT_PIECE", "🔍 Analyse [" + nomPiece + "] - ID: " + pieceCible.getViewIdResourceName() +
                                " | Cliquable: " + pieceCible.isClickable());

                        // --- CORRECTION LOGIQUE : ON REMONTE JUSQU'A TROUVER LE PARENT CLIQUABLE ---
                        AccessibilityNodeInfo vraiCible = pieceCible;

                        // Si l'élément trouvé n'est pas celui qui reçoit les clics
                        if (!pieceCible.isClickable()) {
                            AccessibilityNodeInfo temp = pieceCible.getParent();
                            while (temp != null) {
                                String resId = temp.getViewIdResourceName();
                                // On cherche soit l'ID room_parent, soit un élément cliquable
                                if (resId != null && resId.contains("room_parent")) {
                                    vraiCible = temp;
                                    Log.d("TT_PIECE", "🔧 Redirection réussie vers room_parent pour : " + nomPiece);
                                    break;
                                }
                                temp = temp.getParent();
                            }
                        }

                        // Log des coordonnées finales pour vérification
                        Rect rFinal = new Rect();
                        vraiCible.getBoundsInScreen(rFinal);
                        Log.d("TT_PIECE", "🎯 Clic final sur " + nomPiece + " à : X=" + rFinal.centerX() + " Y=" + rFinal.centerY());

                        cliquerSurCoordonnees(vraiCible);
                        // 2. Attendre que la page de saisie s'ouvre OU gérer le popup
                        int attentePage = 0;
                        // On augmente un peu le timeout global (20 * 500ms = 10 secondes) car le popup peut prendre du temps
                        while (!estSurLaPageDeSaisie() && attentePage < 20 && isTTRunning) {

                            // On vérifie si le popup bloque le passage
                            boolean popupGere = gererPopUpLocalisation();

                            if (popupGere) {
                                // Si on a géré un popup, on remet le compteur à 0 pour laisser le temps à la page suivante de charger
                                attentePage = 0;
                                Log.d("TT_PIECE", "⏳ Popup validé, attente du chargement de la pièce...");
                            }

                            Thread.sleep(500);
                            attentePage++;
                        }

                        // 3. Lancer la saisie (Nettoyeur + Expert)
                        if (estSurLaPageDeSaisie() && isTTRunning) {
                            executerSaisiePiece(); // Cette méthode gère déjà ses propres boucles

                            // 4. Retour à l'accueil
                            if (isTTRunning) {
                                Thread.sleep(800);
                                revenirArriere();

                                // Attendre d'être bien revenu sur l'accueil
                                attendreAffichageId("com.checkandvisit.android.checkapp:id/room_bottom_ll", 5);
                                Thread.sleep(1000); // Pause de confort pour le processeur
                            }
                        }
                    }
                    if (currentRoot != null) currentRoot.recycle();
                }

            } catch (Exception e) {
                Log.e("TT_PIECE", "Erreur fatale : " + e.getMessage());
            } finally {
                isTTRunning = false;

                // C'est ICI qu'on modifie la fin :

                showToast("🏁 Séquence terminée - X pièces traitées");
                Log.d("TT_PIECE", "🛑 Fin de thread propre.");

                // ON LANCE LES 3 SÉRIES DE BIPS VIA SCRIPTNOTIFIER
                try {
                    for (int k = 0; k < 3; k++) {
                        // On appelle signalerFin avec '2' pour avoir le son "Expert" (3 notes)
                        ScriptNotifier.signalerFin(getApplicationContext(), 2);
                        // On attend un peu entre les séries pour que ce soit distinct
                        Thread.sleep(1500);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Clique sur le bouton retour "Revenir en haut de la page"
     */
    private void revenirArriere() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        // On cherche par le content-desc que tu as identifié
        trouverEtCliquerContentDesc(root, "Revenir en haut de la page");
    }

    private boolean trouverEtCliquerContentDesc(AccessibilityNodeInfo node, String cible) {
        if (node == null) return false;
        if (cible.equals(String.valueOf(node.getContentDescription()))) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (trouverEtCliquerContentDesc(node.getChild(i), cible)) return true;
        }
        return false;
    }
    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        });
    }
    private void cliquerSurCoordonnees(AccessibilityNodeInfo node) {
        if (node == null) return;

        // Récupérer les coordonnées du rectangle de la pièce
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);

        // Calculer le centre
        int x = rect.centerX();
        int y = rect.centerY();

        // Simuler un appui tactile
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        Path path = new Path();
        path.moveTo(x, y);
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 100));

        dispatchGesture(gestureBuilder.build(), null, null);
        Log.d("TT_PIECE", "👉 Clic simulé aux coordonnées : " + x + ", " + y);
    }
    /**
     * Logique commune partagée par le bouton "BonOK" et le mode "TT"
     * Cette méthode doit être appelée depuis un Thread secondaire.
    */

    private void executerSaisiePiece() throws InterruptedException {
        if (!isTTRunning) return; // Vérification initiale

        Log.d(TAG, "🧹 Début Nettoyage...");
        nettoyeurScript.lancer();
        while (nettoyeurScript.isRunning() && isTTRunning) { // Check isTTRunning ici
            Thread.sleep(500);
        }

        if (!isTTRunning) return; // Arrêt si demandé pendant le nettoyage
        Thread.sleep(1000);

        Log.d(TAG, "🚀 Début Expert...");
        expertScript.start();
        while (expertScript.isRunning() && isTTRunning) { // Check isTTRunning ici
            Thread.sleep(1000);
        }
    }
    private boolean attendreAffichageId(String resourceId, int timeoutSecondes) {
        int tentatives = 0;
        while (tentatives < (timeoutSecondes * 2)) { // *2 car on attend 500ms
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(resourceId);
                root.recycle();
                if (nodes != null && !nodes.isEmpty()) {
                    return true; // L'élément est enfin là !
                }
            }
            try { Thread.sleep(500); } catch (InterruptedException e)
            {e.printStackTrace();
            }
            tentatives++;
        }
        return false; // Temps écoulé, l'élément n'est pas apparu
        }
    /**
     * Affiche une fenêtre de confirmation générique.
     * @param titre Le titre de la fenêtre
     * @param message Le message d'avertissement
     * @param actionALancer La méthode à exécuter si l'utilisateur clique sur OUI
     */
    private void demanderConfirmation(String titre, String message, Runnable actionALancer) {
        // On s'assure d'être sur le Thread UI
        new Handler(Looper.getMainLooper()).post(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
            builder.setTitle(titre);
            builder.setMessage(message);
            builder.setCancelable(true);

            // Bouton de confirmation
            builder.setPositiveButton("DÉMARRER", (dialog, which) -> {
                dialog.dismiss();
                Log.d(TAG, "🟢 Confirmation reçue.");
                // C'est ici qu'on lance l'action passée en paramètre
                actionALancer.run();
            });

            // Bouton d'annulation
            builder.setNegativeButton("Annuler", (dialog, which) -> dialog.dismiss());

            AlertDialog dialog = builder.create();

            // Type de fenêtre (Indispensable pour un Service)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            } else {
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
            }

            dialog.show();
        });
    }
}