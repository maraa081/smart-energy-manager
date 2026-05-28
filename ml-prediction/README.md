# Smart Energy Manager - Prediction RandomForest

Modele de prediction de consommation energetique avec RandomForest (scikit-learn).

## Utilisation

```bash
pip install -r requirements.txt
python predict.py --train        # entrainer le modele
python predict.py --predict ...  # predire une consommation
python predict.py --eval         # evaluer le modele
python predict.py --visualize    # generer les graphiques
```

## Exemple

```bash
python predict.py --predict --mois 6 --heure 14 --type ELECTRICITE --temperature 25
```

Retourne une prediction en kWh avec un intervalle de confiance a 95%.

## Fichiers

- predict.py : script principal
- sample_data.csv : donnees d'exemple
- model.pkl : modele entraine
- requirements.txt : dependances Python
- figures/ : graphiques generes par --visualize

## Parametres

Possibilite de modifier les parametres du RandomForest :

```bash
python predict.py --train --n-estimators 200 --max-depth 12
```

Voir `python predict.py --help` pour la liste complete.
