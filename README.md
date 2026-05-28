# Smart Energy Manager

Appli JavaFX pour suivre les consommations d'energie de batiments.

## Fonctionnalites

- Tableau de bord : conso du jour, du mois, de l'annee, cout total
- Gestion des batiments : ajout, modification, suppression
- Consommations : saisie manuelle, import CSV, generation de donnees test
- Graphiques : courbes, histogrammes, repartition, comparaison
- Analyses : tendances, predictions, detection d'anomalies
- Meteo integree via Open-Meteo (API gratuite)
- Detection d'anomalies : z-score, donnees manquantes, valeurs aberrantes
- Prediction : regression lineaire + RandomForest (Python)

## Prérequis

- Java 21+
- Maven 3.8+

## Lancer l'app

```bash
mvn clean javafx:run
```

Au premier lancement, 4 batiments d'exemple sont generes automatiquement.

## Persistance

Les donnees sont sauvegardees dans :
```
~/.smart-energy-manager/data/buildings.json
```

## Import CSV

```
date,heure,type_energie,quantite,cout
2025-01-15,08:00,ELECTRICITE,12.5,2.35
```

Types supportes : ELECTRICITE, GAZ, SOLAIRE, CHAUFFAGE, CLIMATISATION, EAU

## Modele de prediction ML (RandomForest)

```bash
cd ml-prediction
pip install -r requirements.txt
python predict.py --train
python predict.py --predict --mois 6 --heure 14 --type ELECTRICITE --temperature 25
```

## Stack

Java 21 + JavaFX 23 + Maven + Jackson + Apache Commons CSV + Open-Meteo + scikit-learn

## Auteur

Projet etudiant — @maraa081
