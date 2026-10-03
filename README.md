# Mini Météo Widget

Petit widget Android 1×1 pensé pour Samsung Galaxy / One UI.

## Affichage
- Icône météo seulement (soleil, nuages, pluie, neige, orage, brouillard)
- Température arrondie, ex. `18°`
- Aucun nom de ville
- Aucun fond : transparent

## Fonctionnement
- Données météo : Open-Meteo, sans clé API.
- Configuration initiale : saisir une ville ou utiliser la position actuelle.
- La position choisie est mémorisée localement, donc aucune localisation permanente n'est nécessaire.
- Actualisation périodique demandée toutes les 30 minutes.
- Toucher le widget tente d'ouvrir Samsung Weather (`com.sec.android.daemonapp`).

## Installation / APK via GitHub
1. Créer un dépôt GitHub vide.
2. Mettre tout ce projet dans le dépôt.
3. L'onglet **Actions** lance `Build APK` automatiquement.
4. Ouvrir la dernière exécution puis télécharger l'artifact `MiniMeteoWidget-apk`.
5. Installer `app-debug.apk` sur le Galaxy.
6. Appui long sur l'écran d'accueil > Widgets > **Mini Météo**.

## Remarque Samsung Weather
Samsung change parfois ses activités internes. Le projet tente d'abord `ParticularsActivity`, puis le launcher du package Samsung Weather ; en dernier recours il ouvre la fiche système de l'app.
