# ⚡ Smart Energy Manager

Application JavaFX de gestion et suivi de consommations énergétiques pour bâtiments.

## Fonctionnalités

- 📊 **Tableau de bord** — Vue d'ensemble des consommations, tendances et alertes météo
- 🏢 **Gestion des bâtiments** — Ajout, modification, clonage et suppression
- ⚡ **Consommations** — Saisie manuelle, import CSV, génération de données test
- 📈 **Graphiques** — Courbes temporelles, histogrammes, répartition, comparaison
- 🔍 **Analyses** — Bâtiments les plus consommateurs, tendances, prédictions, anomalies
- ☀️ **Météo intégrée** — Température du jour et alertes via Open-Meteo (gratuit)
- 🤖 **Détection d'anomalies** — Z-score, données manquantes, valeurs aberrantes
- 📉 **Prédiction** — Régression linéaire sur 6 mois glissants

## Prérequis

- **Java 21+** (OpenJDK 21 recommandé)
- **Maven 3.8+**
- **JavaFX 23** (géré automatiquement par Maven)

## Installation & Lancement

```bash
# Cloner le dépôt
git clone https://github.com/maraa081/smart-energy-manager.git
cd smart-energy-manager

# Compiler et lancer
mvn clean javafx:run
```

**Premier lancement :** 4 bâtiments d'exemple sont automatiquement créés (appartement, maison, bureau, commerce) avec des données de consommation simulées. Pas besoin de tout ressaisir.

## Persistance des données

Les données sont sauvegardées automatiquement dans :
```
~/.smart-energy-manager/data/buildings.json
```

Les modifications (ajout de bâtiments, relevés, imports CSV) sont persistées entre les sessions.

## Import CSV

Format attendu (avec en-tête) :

```csv
date,heure,type_energie,quantite,cout
2025-01-15,08:00,ELECTRICITE,12.5,2.35
```

Types d'énergie supportés : `ELECTRICITE`, `GAZ`, `SOLAIRE`, `CHAUFFAGE`, `CLIMATISATION`, `EAU`

## Modèle de prédiction ML

Un modèle **RandomForest** se trouve dans le dossier `ml-prediction/` :

```bash
cd ml-prediction
pip install -r requirements.txt
python predict.py --train
python predict.py --predict --mois 6 --heure 14 --type ELECTRICITE --temperature 25
```

Voir [ml-prediction/README.md](ml-prediction/README.md) pour plus de détails.

## Stack technique

| Composant | Technologie |
|-----------|-------------|
| UI | JavaFX 23 |
| Build | Maven |
| Persistance | JSON (Jackson) |
| CSV | Apache Commons CSV |
| Météo | Open-Meteo API (gratuite) |
| ML | scikit-learn (Python) |

## Auteur

Projet étudiant — [@maraa081](https://github.com/maraa081)
