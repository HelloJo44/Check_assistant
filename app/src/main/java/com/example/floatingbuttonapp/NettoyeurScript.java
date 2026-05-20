package com.example.floatingbuttonapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections; // IMPORTER CELA POUR LE TRI
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NettoyeurScript {

    private final AccessibilityService service;
    private final int screenWidth;
    private final int screenHeight;
    private volatile boolean isRunning = false;

    private static final String TAG = "NETTOYEUR";

    // --- IDs CIBLES ---
    private static final String ID_IND_EXP_CHECK = "com.checkandvisit.android.checkapp:id/item_header_cleaned_cb";
    private static final String ID_IND_EXP_RADIO = "com.checkandvisit.android.checkapp:id/item_header_good_state_rb";

    private static final String ID_TITRE_RACINE_EXPERT = "com.checkandvisit.android.checkapp:id/item_header_title_tv";
    private static final String ID_TITRE_STANDARD = "com.checkandvisit.android.checkapp:id/item_name_tv";
    private static final String ID_TITRE_MUR = "com.checkandvisit.android.checkapp:id/wall_header_title_tv";

    public NettoyeurScript(AccessibilityService service, int screenWidth, int screenHeight) {
        this.service = service;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    public boolean isRunning() { return isRunning; }

    public void stop() {
        isRunning = false;
        Log.d(TAG, "🛑 SIGNAL D'ARRÊT REÇU");
    }

    public void lancer() {
        if (isRunning) return;
        isRunning = true;
        showToast("🧹 Nettoyage Haut->Bas...");

        new Thread(() -> {
            Log.d(TAG, "🏁 DÉMARRAGE DU THREAD");
            Set<String> titresCliquesCetteFois = new HashSet<>();
            String drapeauDepart = trouverPremierTitreVisible();

            try {
                while (isRunning) {
                    AccessibilityNodeInfo root = service.getRootInActiveWindow();
                    if (root == null) { Thread.sleep(500); continue; }

                    if (!isRunning) return;

                    List<AccessibilityNodeInfo> indicateurs = trouverIndicateursOuverture(root);
                    boolean finDePage = chercherTexte(root, "Valider la pièce");
                    boolean actionFaite = false;

                    if (!indicateurs.isEmpty()) {

                        // --- NOUVEAU : TRIER LES INDICATEURS DU HAUT VERS LE BAS ---
                        // On s'assure de traiter l'élément le plus haut physiquement sur l'écran en premier
                        Collections.sort(indicateurs, (node1, node2) -> {
                            Rect r1 = new Rect(); node1.getBoundsInScreen(r1);
                            Rect r2 = new Rect(); node2.getBoundsInScreen(r2);
                            return Integer.compare(r1.top, r2.top);
                        });

                        List<AccessibilityNodeInfo> titres = trouverTousLesTitres(root);

                        for (AccessibilityNodeInfo ind : indicateurs) {
                            if (!isRunning) return;

                            AccessibilityNodeInfo cible = trouverTitreParent(ind, titres);

                            if (cible != null && cible.getText() != null) {
                                String txt = cible.getText().toString();

                                // Ignore le bouton Valider ou Ajouter
                                if (txt.contains("Valider") || txt.contains("Ajouter")) continue;

                                if (!titresCliquesCetteFois.contains(txt)) {
                                    Log.d(TAG, "⚡ ACTION SUR (Y=" + getTopY(cible) + ") : '" + txt + "'");

                                    clickNode(cible);
                                    titresCliquesCetteFois.add(txt);

                                    // Pause pour laisser l'animation se faire (le bloc du haut se ferme)
                                    Log.d(TAG, "⏳ Fermeture...");
                                    Thread.sleep(800);

                                    // BREAK : On a fermé le plus haut. On relance le scan pour voir ce qui est remonté.
                                    actionFaite = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (actionFaite) continue;

                    if (finDePage) {
                        Log.d(TAG, "🛑 Fin de page atteinte.");
                        break;
                    }

                    Log.d(TAG, "🔄 Scroll...");
                    performGlobalScroll(false);
                    Thread.sleep(1100);
                }

                if (isRunning) remonterHautPage(drapeauDepart);

            } catch (Exception e) {
                Log.e(TAG, "Erreur script: " + e.getMessage());
            } finally {
                if (isRunning) ScriptNotifier.signalerFin(service, 1);
                isRunning = false;
                Log.d(TAG, "🏁 FIN DU THREAD");
            }
        }).start();
    }

    // --- LOGIQUE DE CIBLAGE (INCHANGÉE MAIS ESSENTIELLE) ---
    private AccessibilityNodeInfo trouverTitreParent(AccessibilityNodeInfo indicateur, List<AccessibilityNodeInfo> titres) {
        Rect rectInd = new Rect();
        indicateur.getBoundsInScreen(rectInd);

        String indId = indicateur.getViewIdResourceName();
        if (indId == null) return null;

        boolean isExpert = indId.contains(ID_IND_EXP_CHECK) || indId.contains(ID_IND_EXP_RADIO);

        AccessibilityNodeInfo meilleurTitre = null;
        int distanceMin = Integer.MAX_VALUE;
        int maxDist = (int) (screenHeight * 0.8);

        for (AccessibilityNodeInfo titre : titres) {
            String titreId = titre.getViewIdResourceName();
            if (titreId == null) continue;

            // Filtrage strict Expert vs Standard
            if (isExpert) {
                if (!titreId.contains(ID_TITRE_RACINE_EXPERT)) continue;
            } else {
                if (titreId.contains(ID_TITRE_RACINE_EXPERT)) continue;
            }

            Rect rectTitre = new Rect();
            titre.getBoundsInScreen(rectTitre);

            if (rectTitre.top < rectInd.top) {
                int distY = rectInd.top - rectTitre.bottom;
                if (distY > 0 && distY < maxDist && distY < distanceMin) {
                    distanceMin = distY;
                    meilleurTitre = titre;
                }
            }
        }
        return meilleurTitre;
    }

    private List<AccessibilityNodeInfo> trouverTousLesTitres(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> all = new ArrayList<>();
        collecterTousLesNoeuds(root, all);
        List<AccessibilityNodeInfo> titres = new ArrayList<>();
        String[] idsTitres = {ID_TITRE_STANDARD, ID_TITRE_RACINE_EXPERT, ID_TITRE_MUR};

        for (AccessibilityNodeInfo n : all) {
            String resId = n.getViewIdResourceName();
            if (resId != null) {
                for (String id : idsTitres) {
                    if (resId.contains(id)) {
                        titres.add(n);
                        break;
                    }
                }
            }
        }
        return titres;
    }

    private List<AccessibilityNodeInfo> trouverIndicateursOuverture(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        String[] ids = {
                ID_IND_EXP_CHECK,
                ID_IND_EXP_RADIO,
                "com.checkandvisit.android.checkapp:id/item_state_good_biv",
                "com.checkandvisit.android.checkapp:id/item_cleanliness_ok_biv",
                "com.checkandvisit.android.checkapp:id/wall_state_good_biv",
                "com.checkandvisit.android.checkapp:id/wall_cleanliness_ok_biv"
        };
        for (String id : ids) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null) {
                for (AccessibilityNodeInfo n : nodes) if (n.isVisibleToUser()) result.add(n);
            }
        }
        return result;
    }

    private int getTopY(AccessibilityNodeInfo node) {
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        return r.top;
    }

    private void clickNode(AccessibilityNodeInfo node) {
        if (node == null) return;
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        Path p = new Path();
        p.moveTo(r.centerX(), r.centerY());
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, 80));
        service.dispatchGesture(b.build(), null, null);
    }

    private void performGlobalScroll(boolean versLeHaut) {
        Path path = new Path();
        int centerX = screenWidth / 2;
        if (versLeHaut) {
            path.moveTo(centerX, screenHeight * 0.2f);
            path.lineTo(centerX, screenHeight * 0.8f);
        } else {
            path.moveTo(centerX, screenHeight * 0.8f);
            path.lineTo(centerX, screenHeight * 0.3f);
        }
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 800));
        service.dispatchGesture(builder.build(), null, null);
    }

    private void remonterHautPage(String cibleNom) {
        for (int i = 0; i < 15; i++) {
            if (!isRunning) return;
            performGlobalScroll(true);
            try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
            if (cibleNom != null && chercherTexte(service.getRootInActiveWindow(), cibleNom)) break;
        }
    }

    private String trouverPremierTitreVisible() {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return null;
        List<AccessibilityNodeInfo> titres = trouverTousLesTitres(root);
        for (AccessibilityNodeInfo t : titres) {
            Rect r = new Rect();
            t.getBoundsInScreen(r);
            if (r.top > 150) return t.getText() != null ? t.getText().toString() : null;
        }
        return null;
    }

    private void collecterTousLesNoeuds(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> list) {
        if (node == null) return;
        list.add(node);
        for (int i = 0; i < node.getChildCount(); i++) collecterTousLesNoeuds(node.getChild(i), list);
    }

    private boolean chercherTexte(AccessibilityNodeInfo root, String texte) {
        if (root == null) return false;
        List<AccessibilityNodeInfo> res = root.findAccessibilityNodeInfosByText(texte);
        return res != null && !res.isEmpty();
    }

    private void showToast(String msg) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(service, msg, Toast.LENGTH_SHORT).show());
    }
}