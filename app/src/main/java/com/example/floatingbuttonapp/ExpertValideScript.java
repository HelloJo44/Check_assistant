package com.example.floatingbuttonapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Script Expert : Automatise la validation des éléments d'une pièce.
 * Il détecte les catégories, les ouvre, coche les états "Bon" et "Propre",
 * gère les menus de fonctionnement et referme les blocs.
 */
public class ExpertValideScript {
    private static final String TAG = "SCRIPT_EXPERT";

    // --- IDs RESSOURCES ---
    private static final String ID_TITRE_STANDARD = "com.checkandvisit.android.checkapp:id/item_name_tv";
    private static final String ID_TITRE_EXPERT = "com.checkandvisit.android.checkapp:id/item_header_title_tv";
    private static final String ID_TITRE_MURS = "com.checkandvisit.android.checkapp:id/wall_header_title_tv";

    private static final String ID_STD_BON = "com.checkandvisit.android.checkapp:id/item_state_good_biv";
    private static final String ID_STD_OK = "com.checkandvisit.android.checkapp:id/item_cleanliness_ok_biv";

    private static final String ID_MUR_BON = "com.checkandvisit.android.checkapp:id/wall_state_good_biv";
    private static final String ID_MUR_OK = "com.checkandvisit.android.checkapp:id/wall_cleanliness_ok_biv";

    private static final String ID_EXP_BON_RADIO = "com.checkandvisit.android.checkapp:id/item_header_good_state_rb";
    private static final String ID_EXP_PROPRE_CHECK = "com.checkandvisit.android.checkapp:id/item_header_cleaned_cb";
    private static final String ID_SELECTEUR_VALEUR = "com.checkandvisit.android.checkapp:id/new_selector_value_tv";

    private static final String ID_BOUTON_REDUIRE = "com.checkandvisit.android.checkapp:id/daddy_footer_collapse_rl";

    private final AccessibilityService service;
    private volatile boolean isRunning = false; // État d'exécution du script
    private final Set<String> processedItems = new HashSet<>();
    private final int screenWidth;
    private final int screenHeight;

    public ExpertValideScript(AccessibilityService service, int width, int height) {
        this.service = service;
        this.screenWidth = width;
        this.screenHeight = height;
    }

    /**
     * INDIQUE SI LE SCRIPT EST EN COURS.
     * @return true si le thread de boucle est actif.
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * DÉMARRAGE DU SCRIPT.
     * Initialise la mémoire des éléments traités et lance le Thread principal (loop).
     */
    public void start() {
        if (isRunning) return;
        isRunning = true;
        processedItems.clear();
        new Thread(this::loop).start();
    }

    /**
     * ARRÊT DU SCRIPT.
     * Passe le booléen de contrôle à false pour stopper toutes les boucles en cours.
     */
    public void stop() {
        isRunning = false;
    }

    /**
     * BOUCLE PRINCIPALE (CERVEAU DU SCRIPT).
     * Gère l'enchaînement : Scan écran -> Ouverture -> Traitement -> Fermeture -> Scroll.
     * S'arrête quand "Valider la pièce" est visible.
     */
    private void loop() {
        String drapeauDepart = null;

        try {
            Thread.sleep(500);
            AccessibilityNodeInfo rootStart = service.getRootInActiveWindow();
            boolean besoinDeRemonter = false; // Initialisation
            if (rootStart != null) {
                List<AccessibilityNodeInfo> catsInitiales = listerCategories(rootStart);
                if (!catsInitiales.isEmpty()) {
                    besoinDeRemonter = true; // On a des éléments, donc il faudra remonter
                    drapeauDepart = catsInitiales.get(0).getText().toString();
                    Log.d(TAG, "🚩 Drapeau de départ : " + drapeauDepart);
                }
            }

            while (isRunning) {
                Thread.sleep(800);
                AccessibilityNodeInfo root = service.getRootInActiveWindow();
                if (root == null) continue;

                List<AccessibilityNodeInfo> categories = listerCategories(root);

                if (categories.isEmpty()) {
                    // Si on voit le bouton de fin
                    if (trouverTexte(root, "Valider la pièce")) {
                        Log.d(TAG, "✅ Fin de page détectée.");

                        // --- ICI ON PLACE LA CONDITION ---
                        if (besoinDeRemonter) {
                            Log.d(TAG, "🔄 Remontée vers : " + drapeauDepart);
                            remonterHautPage(drapeauDepart);
                        } else {
                            Log.d(TAG, "🚀 Pièce vide : Pas de remontée nécessaire.");
                        }
                        // ---------------------------------

                        ScriptNotifier.signalerFin(service, 3);
                        break;
                    }
                    scroll(false, 700);
                    Thread.sleep(1000);
                    continue;
                }

                AccessibilityNodeInfo cat = categories.get(0);
                String nomCat = cat.getText() != null ? cat.getText().toString() : "Inconnu";

                Log.d(TAG, "👉 Traitement de : " + nomCat);
                clickNode(cat);
                processedItems.add(nomCat);
                Thread.sleep(1000);

                if (nomCat.contains("Murs")) {
                    traiterMursUnique();
                    refermerCategorie(nomCat);
                } else if (nomCat.startsWith("Bloc") || estExpert(root)) {
                    traiterBlocExpertAvecScroll();
                } else {
                    traiterStandard();
                    refermerCategorie(nomCat);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur loop: " + e.getMessage());
        } finally {
            isRunning = false;
        }
    }

    /**
     * REMONTÉE HAUT DE PAGE.
     * Effectue des scrolls vers le haut jusqu'à retrouver l'élément de départ.
     * @param cible Le nom de la catégorie qui servait de repère en haut.
     */
    private void remonterHautPage(String cible) {
        for (int i = 0; i < 15; i++) {
            if (!isRunning) break;
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root != null && cible != null && trouverTexte(root, cible)) return;
            scroll(true, 1100);
            try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
        }
    }

    /**
     * TRAITEMENT BLOC EXPERT.
     * Gère les éléments complexes avec scroll interne jusqu'à voir le bouton "Réduire".
     */
    /*private void traiterBlocExpertAvecScroll() throws InterruptedException {
        int safetyScrolls = 0;
        while (isRunning && safetyScrolls < 10) {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) break;

            cliquerSiNonCoche(root, ID_EXP_BON_RADIO);
            cliquerSiNonCoche(root, ID_EXP_PROPRE_CHECK);
            traiterFonctionnement(root);

            List<AccessibilityNodeInfo> reduire = root.findAccessibilityNodeInfosByViewId(ID_BOUTON_REDUIRE);
            if (!reduire.isEmpty() && reduire.get(0).isVisibleToUser()) {
                clickNode(reduire.get(0));
                Thread.sleep(1000);
                break;
            }
            scroll(false, 550);
            Thread.sleep(1000);
            safetyScrolls++;
        }
    }*/

 /*   private void traiterBlocExpertAvecScroll() throws InterruptedException {
        int safetyScrolls = 0;
        while (isRunning && safetyScrolls < 10) {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) break;

            cliquerSiNonCoche(root, ID_EXP_BON_RADIO);
            cliquerSiNonCoche(root, ID_EXP_PROPRE_CHECK);
            traiterFonctionnement(root);

            // --- RECHERCHE AMÉLIORÉE DU BOUTON RÉDUIRE ---
            AccessibilityNodeInfo btnReduire = null;

            // Tentative 1 : Par ID
            List<AccessibilityNodeInfo> reduireIds = root.findAccessibilityNodeInfosByViewId(ID_BOUTON_REDUIRE);
            if (!reduireIds.isEmpty()) btnReduire = reduireIds.get(0);

            // Tentative 2 : Par Texte (si ID échoue ou n'est pas visible)
            if (btnReduire == null || !btnReduire.isVisibleToUser()) {
                List<AccessibilityNodeInfo> reduireTexts = root.findAccessibilityNodeInfosByText("Réduire");
                if (!reduireTexts.isEmpty()) btnReduire = reduireTexts.get(0);
            }

            if (btnReduire != null && btnReduire.isVisibleToUser()) {
                Rect r = new Rect();
                btnReduire.getBoundsInScreen(r);

                // On vérifie que le bouton n'est pas caché derrière le bouton orange de validation
                // Sur tablette, on laisse une marge de 5 pixels seulement
                if (r.top > 0 && r.bottom < (screenHeight - 100)) {
                    Log.d(TAG, "🎯 Bouton Réduire trouvé à Y=" + r.top + ". Fermeture du bloc.");
                    clickNode(btnReduire);
                    Thread.sleep(1000);
                    break;
                }
            }

            Log.d(TAG, "⬇️ 'Réduire' non visible, scroll suivant... (" + safetyScrolls + ")");
            scroll(false, 550);
            Thread.sleep(1000);
            safetyScrolls++;
        }
    }*/
    private void traiterBlocExpertAvecScroll() throws InterruptedException {
        int safetyScrolls = 0;
        while (isRunning && safetyScrolls < 10) {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) break;

            cliquerSiNonCoche(root, ID_EXP_BON_RADIO);
            cliquerSiNonCoche(root, ID_EXP_PROPRE_CHECK);
            traiterFonctionnement(root);

            // --- CORRECTION ICI ---
            List<AccessibilityNodeInfo> reduireNodes = root.findAccessibilityNodeInfosByViewId(ID_BOUTON_REDUIRE);
            AccessibilityNodeInfo vraiBouton = null;

            for (AccessibilityNodeInfo n : reduireNodes) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                // On vérifie que le bouton a une vraie dimension (> 10px de haut)
                // et qu'il est bien dans la partie visible de l'écran
                if (r.height() > 10 && r.top > 0 && r.bottom < screenHeight) {
                    vraiBouton = n;
                    break;
                }
            }

            if (vraiBouton != null) {
                Log.d(TAG, "🎯 Vrai bouton Réduire trouvé");
                clickNode(vraiBouton);
                Thread.sleep(1000);
                return; // On sort du bloc car on a réussi à fermer
            }

            // Si on n'a pas trouvé de bouton avec de la hauteur, on scroll
            Log.d(TAG, "⬇️ Aucun bouton Réduire cliquable trouvé, scroll...");
            scroll(false, 550);
            Thread.sleep(1000);
            safetyScrolls++;
        }
    }


    /**
     * TRAITEMENT STANDARD.
     * Clique sur les boutons d'état et de propreté en évitant le titre global.
     */
    private void traiterStandard() throws InterruptedException {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return;

        Rect zoneExclue = new Rect(-1, -1, -1, -1);
        List<AccessibilityNodeInfo> labelEtat = root.findAccessibilityNodeInfosByText("État de la pièce");
        if (!labelEtat.isEmpty()) {
            labelEtat.get(0).getBoundsInScreen(zoneExclue);
            zoneExclue.top -= 20;
            zoneExclue.bottom += 180;
        }

        cliquerBoutonsAvecExclusion(root, ID_STD_BON, zoneExclue);
        Thread.sleep(600);
        cliquerBoutonsAvecExclusion(root, ID_STD_OK, zoneExclue);
        traiterFonctionnement(root);
    }

    /**
     * CLIC FILTRÉ PAR ZONE.
     * Clique sur les IDs cibles s'ils ne se trouvent pas dans la zone d'exclusion.
     * @param root Noeud racine.
     * @param id Ressource ID du bouton.
     * @param zoneExclue Coordonnées à ne pas toucher.
     */
    private void cliquerBoutonsAvecExclusion(AccessibilityNodeInfo root, String id, Rect zoneExclue) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        for (AccessibilityNodeInfo n : nodes) {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (n.isVisibleToUser() && !zoneExclue.contains(r.centerX(), r.centerY())) {
                clickNode(n);
            }
        }
    }

    /**
     * GESTION DU SÉLECTEUR FONCTIONNEMENT.
     * Ouvre le menu si "Fonctionnement" est écrit, et choisit "Fonctionne".
     * @param root Noeud racine de la fenêtre.
     */
    private void traiterFonctionnement(AccessibilityNodeInfo root) throws InterruptedException {
        List<AccessibilityNodeInfo> selecteurs = root.findAccessibilityNodeInfosByViewId(ID_SELECTEUR_VALEUR);
        for (AccessibilityNodeInfo sel : selecteurs) {
            String txt = sel.getText() != null ? sel.getText().toString() : "";
            if (txt.equals("Fonctionnement") && sel.isVisibleToUser()) {
                AccessibilityNodeInfo parent = sel.getParent();
                clickNode(parent != null && parent.isClickable() ? parent : sel);
                Thread.sleep(1000);
                AccessibilityNodeInfo popup = service.getRootInActiveWindow();
                if (cliquerTexte(popup, "Fonctionne")) Thread.sleep(600);
            }
        }
    }

    /**
     * TRAITEMENT SPÉCIFIQUE MURS.
     * Sélectionne uniquement le premier état pour éviter les erreurs sur les murs multiples.
     */
    private void traiterMursUnique() throws InterruptedException {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return;

        List<AccessibilityNodeInfo> bons = root.findAccessibilityNodeInfosByViewId(ID_MUR_BON);
        if (!bons.isEmpty() && bons.get(0).isVisibleToUser()) {
            clickNode(bons.get(0));
            Thread.sleep(400);
        }

        List<AccessibilityNodeInfo> oks = root.findAccessibilityNodeInfosByViewId(ID_MUR_OK);
        if (!oks.isEmpty() && oks.get(0).isVisibleToUser()) {
            clickNode(oks.get(0));
            Thread.sleep(300);
        }
    }

    /**
     * CLIC CONDITIONNEL (CHECKBOX/RADIO).
     * Vérifie si l'élément est déjà coché avant d'envoyer un clic.
     * @param root Noeud racine.
     * @param id Ressource ID de l'élément à cocher.
     */
    private void cliquerSiNonCoche(AccessibilityNodeInfo root, String id) throws InterruptedException {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        for (AccessibilityNodeInfo n : nodes) {
            if (n.isVisibleToUser() && !n.isChecked()) {
                clickNode(n);
                Thread.sleep(400);
            }
        }
    }

    /**
     * FERMETURE DE CATÉGORIE.
     * Recherche le titre de la catégorie par son nom pour cliquer dessus et la replier.
     * @param nom Le nom exact de la catégorie à refermer.
     */
    private void refermerCategorie(String nom) throws InterruptedException {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        AccessibilityNodeInfo titre = trouverTitrePourFermeture(root, nom);
        if (titre != null) {
            clickNode(titre);
            Thread.sleep(1000);
        }
    }

    /**
     * EFFECTUE UN CLIC SUR UN ÉLÉMENT.
     * Utilise l'action d'accessibilité si possible, sinon simule un tap via coordonnées.
     * @param n Le noeud sur lequel cliquer.
     */
    private void clickNode(AccessibilityNodeInfo n) {
        if (n == null) return;
        if (!n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Rect r = new Rect(); n.getBoundsInScreen(r);
            dispatchTap(r.centerX(), r.centerY());
        }
    }

    /**
     * SIMULATION TACTILE DE TAP.
     * @param x Coordonnée X.
     * @param y Coordonnée Y.
     */
    private void dispatchTap(int x, int y) {
        Path p = new Path(); p.moveTo(x, y);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, 50));
        service.dispatchGesture(b.build(), null, null);
    }

    /**
     * SIMULATION TACTILE DE SCROLL.
     * @param up True pour scroller vers le haut, False vers le bas.
     * @param dist Distance du scroll en pixels.
     */
    private void scroll(boolean up, int dist) {
        Path p = new Path();
        int x = screenWidth / 2;
        int startY = up ? 400 : screenHeight - 600;
        int endY = up ? startY + dist : startY - dist;
        p.moveTo(x, startY); p.lineTo(x, endY);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, 500));
        service.dispatchGesture(b.build(), null, null);
    }

    // --- AUTRES MÉTHODES DE RECHERCHE DÉJÀ PRÉSENTES ---
    private List<AccessibilityNodeInfo> listerCategories(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> valid = new ArrayList<>();
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        findNodesById(root, ID_TITRE_STANDARD, nodes);
        findNodesById(root, ID_TITRE_EXPERT, nodes);
        findNodesById(root, ID_TITRE_MURS, nodes);

        for (AccessibilityNodeInfo n : nodes) {
            String txt = n.getText() != null ? n.getText().toString() : "";
            Rect r = new Rect(); n.getBoundsInScreen(r);
            //MODIF JOJO 1
            if (r.top > 25 && !processedItems.contains(txt) && !txt.equalsIgnoreCase("État de la pièce")) {
                valid.add(n);
            }
        }
        Collections.sort(valid, (a, b) -> {
            Rect ra = new Rect(); a.getBoundsInScreen(ra);
            Rect rb = new Rect(); b.getBoundsInScreen(rb);
            return Integer.compare(ra.top, rb.top);
        });
        return valid;
    }

    private AccessibilityNodeInfo trouverTitrePourFermeture(AccessibilityNodeInfo root, String nom) {
        if (nom.contains("Murs")) {
            List<AccessibilityNodeInfo> m = root.findAccessibilityNodeInfosByViewId(ID_TITRE_MURS);
            if (!m.isEmpty()) return m.get(0);
        }
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(nom);
        for (AccessibilityNodeInfo n : list) {
            String id = n.getViewIdResourceName();
            if (id != null && (id.equals(ID_TITRE_STANDARD) || id.equals(ID_TITRE_EXPERT))) return n;
        }
        return null;
    }

    private boolean estExpert(AccessibilityNodeInfo root) {
        return !root.findAccessibilityNodeInfosByViewId(ID_EXP_BON_RADIO).isEmpty();
    }

    private void findNodesById(AccessibilityNodeInfo node, String id, List<AccessibilityNodeInfo> list) {
        if (node == null) return;
        if (id.equals(node.getViewIdResourceName()) && node.isVisibleToUser()) list.add(node);
        for (int i = 0; i < node.getChildCount(); i++) findNodesById(node.getChild(i), id, list);
    }

    private boolean trouverTexte(AccessibilityNodeInfo root, String t) {
        if (root == null) return false;
        return !root.findAccessibilityNodeInfosByText(t).isEmpty();
    }

    private boolean cliquerTexte(AccessibilityNodeInfo root, String t) {
        if (root == null) return false;
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(t);
        for (AccessibilityNodeInfo n : list) {
            if (n.isVisibleToUser()) { clickNode(n); return true; }
        }
        return false;
    }
}

