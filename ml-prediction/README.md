# Smart Energy Manager — Modèle de Prédiction RandomForest

Ce dossier contient un modèle de prédiction de consommation énergétique utilisant **Random Forest** (scikit-learn).

## Objectif

Prédire la consommation électrique (kWh) pour un bâtiment donné en se basant sur :

- L'**heure** de la journée
- Le **mois** de l'année
- Le **type d'énergie**
- La **température extérieure** (via API Open-Meteo)
- Le **jour de la semaine** (week-end ou jour ouvré)

## Utilisation

```bash
# 1. Installer les dépendances
pip install -r requirements.txt

# 2. Entraîner le modèle avec les données d'exemple
python predict.py --train

# 3. Faire une prédiction
python predict.py --predict --mois 6 --heure 14 --type ELECTRICITE --temperature 25.0

# 4. Voir les métriques du modèle
python predict.py --eval
```

## Structure

```
ml-prediction/
├── README.md           ← Ce fichier
├── requirements.txt    ← Dépendances Python
├── predict.py          ← Script principal (entraînement + prédiction)
├── sample_data.csv     ← Données d'exemple exportées
└── model.pkl           ← Modèle entraîné (généré après --train)
```

## Exemple de résultat

```
Prédiction pour Juin, 14h, ELECTRICITE, 25°C
→ 12.45 kWh estimés
→ Intervalle de confiance : [10.2, 14.7] kWh
→ R² du modèle : 0.87
```

## Pour présenter à un prof

Le script inclut :

- Une **analyse des features importantes** (feature importance plot)
- Les **métriques d'évaluation** (R², MAE, RMSE)
- La **visualisation** des prédictions vs valeurs réelles
- Une **matrice de corrélation** entre les variables

```bash
# Générer les graphiques pour la présentation
python predict.py --visualize
```
