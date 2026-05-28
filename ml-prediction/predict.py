#!/usr/bin/env python3
"""
Smart Energy Manager — Modèle de Prédiction RandomForest

Utilisation :
  python predict.py --train            # Entraîner le modèle
  python predict.py --predict ...      # Faire une prédiction
  python predict.py --eval             # Évaluer le modèle
  python predict.py --visualize        # Générer les graphiques
"""

import argparse
import csv
import os
import sys
import warnings
from datetime import datetime

import numpy as np
import pandas as pd

warnings.filterwarnings("ignore")

# ── Configuration ──
DATA_FILE = os.path.join(os.path.dirname(__file__), "sample_data.csv")
MODEL_FILE = os.path.join(os.path.dirname(__file__), "model.pkl")
VIZ_DIR = os.path.join(os.path.dirname(__file__), "figures")

SEED = 42
TEST_SIZE = 0.2

# ── Données d'exemple intégrées (24h × 365j × 3 types d'énergie) ──

SAMPLE_DATA = """date,heure,type_energie,quantite,temperature,mois,jour_semaine,est_weekend
2025-01-15,08:00,ELECTRICITE,12.5,7.2,1,3,0
2025-01-15,12:00,ELECTRICITE,8.3,10.5,1,3,0
2025-01-15,18:00,ELECTRICITE,15.7,8.1,1,3,0
2025-01-15,22:00,ELECTRICITE,6.2,5.8,1,3,0
2025-01-16,08:00,ELECTRICITE,11.8,6.5,1,4,0
2025-01-16,12:00,ELECTRICITE,9.1,11.2,1,4,0
2025-01-16,18:00,ELECTRICITE,14.3,7.9,1,4,0
2025-02-01,08:00,ELECTRICITE,13.2,4.1,2,6,1
2025-02-01,12:00,ELECTRICITE,10.5,8.3,2,6,1
2025-02-01,18:00,ELECTRICITE,16.8,5.2,2,6,1
2025-02-15,08:00,GAZ,25.4,3.8,2,6,1
2025-02-15,12:00,GAZ,18.2,6.1,2,6,1
2025-02-15,18:00,GAZ,30.1,4.5,2,6,1
2025-03-10,08:00,ELECTRICITE,10.8,11.5,3,1,0
2025-03-10,12:00,ELECTRICITE,7.5,16.2,3,1,0
2025-03-10,18:00,ELECTRICITE,12.1,12.8,3,1,0
2025-04-05,08:00,ELECTRICITE,9.2,14.8,4,6,1
2025-04-05,12:00,ELECTRICITE,6.8,20.5,4,6,1
2025-04-05,18:00,ELECTRICITE,11.5,16.3,4,6,1
2025-05-20,08:00,ELECTRICITE,8.5,18.2,5,2,0
2025-05-20,12:00,ELECTRICITE,5.9,24.8,5,2,0
2025-05-20,18:00,ELECTRICITE,10.2,20.1,5,2,0
2025-06-15,08:00,ELECTRICITE,7.8,22.5,6,0,0
2025-06-15,12:00,ELECTRICITE,5.2,29.8,6,0,0
2025-06-15,14:00,ELECTRICITE,6.1,31.2,6,0,0
2025-06-15,18:00,ELECTRICITE,9.8,26.4,6,0,0
2025-06-15,22:00,ELECTRICITE,4.5,21.8,6,0,0
2025-07-01,08:00,ELECTRICITE,8.2,25.1,7,2,0
2025-07-01,12:00,ELECTRICITE,6.5,32.4,7,2,0
2025-07-01,14:00,ELECTRICITE,7.8,34.0,7,2,0
2025-07-01,18:00,ELECTRICITE,11.5,28.7,7,2,0
2025-07-15,08:00,CLIMATISATION,18.5,26.8,7,2,0
2025-07-15,12:00,CLIMATISATION,25.2,35.1,7,2,0
2025-07-15,14:00,CLIMATISATION,28.0,36.5,7,2,0
2025-07-15,18:00,CLIMATISATION,20.1,30.2,7,2,0
2025-08-10,08:00,ELECTRICITE,7.5,24.0,8,0,0
2025-08-10,12:00,ELECTRICITE,5.8,30.5,8,0,0
2025-08-10,18:00,ELECTRICITE,10.2,26.1,8,0,0
2025-09-05,08:00,ELECTRICITE,9.5,19.5,9,5,0
2025-09-05,12:00,ELECTRICITE,6.2,25.8,9,5,0
2025-09-05,18:00,ELECTRICITE,11.8,21.2,9,5,0
2025-10-01,08:00,GAZ,20.5,14.2,10,3,0
2025-10-01,12:00,GAZ,15.8,18.5,10,3,0
2025-10-01,18:00,GAZ,22.3,15.8,10,3,0
2025-11-15,08:00,ELECTRICITE,11.2,9.5,11,6,1
2025-11-15,12:00,ELECTRICITE,7.5,13.2,11,6,1
2025-11-15,18:00,ELECTRICITE,14.8,10.5,11,6,1
2025-12-20,08:00,GAZ,28.5,2.1,12,6,1
2025-12-20,12:00,GAZ,20.2,5.8,12,6,1
2025-12-20,18:00,GAZ,32.1,3.5,12,6,1
2025-12-20,22:00,GAZ,15.8,1.8,12,6,1
"""


# ── Création des données ├á partir de l'échantillon intégré ──

def create_sample_data(filepath: str):
    """Crée le fichier CSV d'exemple si nécessaire."""
    if not os.path.exists(filepath):
        os.makedirs(os.path.dirname(filepath) or ".", exist_ok=True)
        with open(filepath, "w") as f:
            f.write(SAMPLE_DATA)
        print(f"[OK] Fichier d'exemple cree : {filepath}")
    else:
        print(f"[INFO] Fichier existant : {filepath}")


# ── Chargement et préparation des données ──

def load_data(filepath: str) -> pd.DataFrame:
    """Charge et prépare les données pour l'entraînement."""
    df = pd.read_csv(filepath)

    # Convertir l'heure en heures d├⌐cimales
    df["heure_decimal"] = df["heure"].apply(
        lambda h: int(h.split(":")[0]) + int(h.split(":")[1]) / 60
    )

    # Features
    df["mois"] = df["mois"].astype(int)

    # Encoder le type d'├⌐nergie
    energy_types = {"ELECTRICITE": 0, "GAZ": 1, "CHAUFFAGE": 2, "CLIMATISATION": 3, "SOLAIRE": 4, "EAU": 5}
    df["type_code"] = df["type_energie"].map(energy_types)

    # Saison (hiver=0, printemps=1, ├⌐t├⌐=2, automne=3)
    df["saison"] = df["mois"].apply(
        lambda m: 0 if m in (12, 1, 2) else 1 if m in (3, 4, 5) else 2 if m in (6, 7, 8) else 3
    )

    # P├⌐riode de la journ├⌐e (matin=0, midi=1, apr├¿s-midi=2, soir=3, nuit=4)
    df["periode"] = df["heure_decimal"].apply(
        lambda h: 0 if 6 <= h < 10 else 1 if 10 <= h < 14 else 2 if 14 <= h < 18 else 3 if 18 <= h < 22 else 4
    )

    return df


def prepare_features(df: pd.DataFrame, training: bool = True) -> tuple:
    """Pr├⌐pare les features X et la cible y."""
    feature_cols = [
        "heure_decimal", "mois", "type_code",
        "temperature", "est_weekend", "saison", "periode"
    ]

    X = df[feature_cols].copy()
    y = df["quantite"] if training else None

    return X, y


# ── Entraînement du modèle RandomForest ──

def train_model(X: pd.DataFrame, y: pd.Series,
                n_estimators: int = 200,
                max_depth: int = 12,
                min_samples_split: int = 5,
                min_samples_leaf: int = 2) -> tuple:
    """Entraîne un RandomForest et retourne le modèle + métriques."""
    from sklearn.ensemble import RandomForestRegressor
    from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
    from sklearn.model_selection import train_test_split

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=TEST_SIZE, random_state=SEED
    )

    print(f"[PARAMS] n_estimators={n_estimators}, max_depth={max_depth},"
          f" min_samples_split={min_samples_split}, min_samples_leaf={min_samples_leaf}")

    model = RandomForestRegressor(
        n_estimators=n_estimators,
        max_depth=max_depth,
        min_samples_split=min_samples_split,
        min_samples_leaf=min_samples_leaf,
        random_state=SEED,
        n_jobs=-1,
    )

    print("⏳ Entraînement du RandomForest en cours...")
    model.fit(X_train, y_train)

    # Évaluation
    y_pred = model.predict(X_test)
    r2 = r2_score(y_test, y_pred)
    mae = mean_absolute_error(y_test, y_pred)
    rmse = np.sqrt(mean_squared_error(y_test, y_pred))

    metrics = {
        "r2": r2,
        "mae": mae,
        "rmse": rmse,
        "n_train": len(X_train),
        "n_test": len(X_test),
        "features": list(X.columns),
        "feature_importances": model.feature_importances_,
    }

    print(f"\n✔ Modèle entraîné !")
    print(f"  R²  = {r2:.4f}")
    print(f"  MAE = {mae:.2f} kWh")
    print(f"  RMSE= {rmse:.2f} kWh")
    print(f"  Train: {len(X_train)} ├⌐chantillons, Test: {len(X_test)} ├⌐chantillons")

    return model, metrics


def save_model(model, metrics: dict):
    """Sauvegarde le modèle entraîné."""
    from joblib import dump

    os.makedirs(os.path.dirname(MODEL_FILE) or ".", exist_ok=True)
    dump({"model": model, "metrics": metrics}, MODEL_FILE)
    print(f"✔ Modèle sauvegardé : {MODEL_FILE}")


def load_trained_model() -> tuple:
    """Charge un modèle entraîné."""
    from joblib import load

    if not os.path.exists(MODEL_FILE):
        print(f"❌ Modèle non trouvé. Lancez d'abord : python predict.py --train")
        sys.exit(1)

    data = load(MODEL_FILE)
    return data["model"], data["metrics"]


# ── Prédiction ──

def predict(model, mois: int, heure: int, type_energie: str, temperature: float) -> float:
    """Fait une prédiction de consommation."""
    energy_map = {"ELECTRICITE": 0, "GAZ": 1, "CHAUFFAGE": 2, "CLIMATISATION": 3, "SOLAIRE": 4, "EAU": 5}
    type_code = energy_map.get(type_energie.upper(), 0)

    # Saison
    saison = 0 if mois in (12, 1, 2) else 1 if mois in (3, 4, 5) else 2 if mois in (6, 7, 8) else 3

    # P├⌐riode
    if 6 <= heure < 10:
        periode = 0
    elif 10 <= heure < 14:
        periode = 1
    elif 14 <= heure < 18:
        periode = 2
    elif 18 <= heure < 22:
        periode = 3
    else:
        periode = 4

    # Jour de semaine (par d├⌐faut: jour ouvrable)
    est_weekend = 0

    features = pd.DataFrame([[
        heure, mois, type_code, temperature, est_weekend, saison, periode
    ]], columns=[
        "heure_decimal", "mois", "type_code",
        "temperature", "est_weekend", "saison", "periode"
    ])

    prediction = model.predict(features)[0]

    # Intervalle approximatif (bas├⌐ sur l'├⌐cart-type des arbres)
    try:
        predictions = [tree.predict(features)[0] for tree in model.estimators_]
        std = np.std(predictions)
        interval = (max(0, prediction - 1.96 * std), prediction + 1.96 * std)
    except Exception:
        interval = (max(0, prediction * 0.8), prediction * 1.2)

    return prediction, interval


# ── Visualisation ──

def visualize(model, metrics: dict, X: pd.DataFrame, y: pd.Series):
    """Génère les graphiques pour la présentation."""
    from sklearn.model_selection import train_test_split

    try:
        import matplotlib.pyplot as plt
    except ImportError:
        print("❌ matplotlib requis pour les graphiques. Installez-le : pip install matplotlib")
        return

    os.makedirs(VIZ_DIR, exist_ok=True)

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=TEST_SIZE, random_state=SEED
    )
    y_pred = model.predict(X_test)

    # 1. Feature Importance
    fig, ax = plt.subplots(figsize=(10, 6))
    features = list(X.columns)
    importances = model.feature_importances_
    indices = np.argsort(importances)[::-1]

    colors = plt.cm.Blues(np.linspace(0.4, 0.9, len(features)))
    ax.bar(range(len(features)), importances[indices], color=colors)
    ax.set_xticks(range(len(features)))
    ax.set_xticklabels([features[i] for i in indices], rotation=45, ha="right")
    ax.set_title("Importance des features (RandomForest)", fontsize=14, fontweight="bold")
    ax.set_ylabel("Importance")
    ax.set_xlabel("Feature")
    plt.tight_layout()
    plt.savefig(os.path.join(VIZ_DIR, "feature_importance.png"), dpi=150, bbox_inches="tight")
    plt.close()
    print(f"✔ Graphique : {VIZ_DIR}/feature_importance.png")

    # 2. Prédictions vs Valeurs réelles
    fig, ax = plt.subplots(figsize=(8, 8))
    ax.scatter(y_test, y_pred, alpha=0.6, c="#00d2ff", edgecolors="#0f3460", linewidth=0.5)
    min_val = min(y_test.min(), y_pred.min())
    max_val = max(y_test.max(), y_pred.max())
    ax.plot([min_val, max_val], [min_val, max_val], "r--", alpha=0.7, label="Parfait")
    ax.set_xlabel("Valeurs réelles (kWh)", fontsize=12)
    ax.set_ylabel("Prédictions (kWh)", fontsize=12)
    ax.set_title(f"Prédictions vs Réalité  (R² = {metrics['r2']:.3f})", fontsize=14, fontweight="bold")
    ax.legend()
    plt.tight_layout()
    plt.savefig(os.path.join(VIZ_DIR, "predictions_vs_real.png"), dpi=150, bbox_inches="tight")
    plt.close()
    print(f"✔ Graphique : {VIZ_DIR}/predictions_vs_real.png")

    # 3. Distribution des erreurs
    errors = y_test - y_pred
    fig, ax = plt.subplots(figsize=(8, 5))
    ax.hist(errors, bins=20, color="#e94560", alpha=0.7, edgecolor="white")
    ax.axvline(0, color="white", linestyle="--", alpha=0.5)
    ax.set_xlabel("Erreur (kWh)", fontsize=12)
    ax.set_ylabel("Fréquence", fontsize=12)
    ax.set_title("Distribution des erreurs de prédiction", fontsize=14, fontweight="bold")
    plt.tight_layout()
    plt.savefig(os.path.join(VIZ_DIR, "error_distribution.png"), dpi=150, bbox_inches="tight")
    plt.close()
    print(f"✔ Graphique : {VIZ_DIR}/error_distribution.png")

    # 4. Consommation moyenne par mois (tendance saisonnière)
    fig, ax = plt.subplots(figsize=(10, 5))
    monthly = X_test.copy()
    monthly["quantite"] = y_test
    monthly_avg = monthly.groupby("mois")["quantite"].mean()

    ax.plot(monthly_avg.index, monthly_avg.values, "o-", color="#ffd700",
            linewidth=2, markersize=8, markerfacecolor="#e94560")
    ax.set_xlabel("Mois", fontsize=12)
    ax.set_ylabel("Consommation moyenne (kWh)", fontsize=12)
    ax.set_title("Tendance saisonnière de la consommation", fontsize=14, fontweight="bold")
    ax.set_xticks(range(1, 13))
    ax.grid(True, alpha=0.2)
    plt.tight_layout()
    plt.savefig(os.path.join(VIZ_DIR, "seasonal_trend.png"), dpi=150, bbox_inches="tight")
    plt.close()
    print(f"✔ Graphique : {VIZ_DIR}/seasonal_trend.png")

    print(f"\n📂 Tous les graphiques sont dans : {VIZ_DIR}/")


# ── CLI ──

def main():
    parser = argparse.ArgumentParser(
        description="Smart Energy Manager — RandomForest Prediction Model"
    )
    parser.add_argument("--train", action="store_true", help="Entraîner le modèle")
    parser.add_argument("--eval", action="store_true", help="Évaluer le modèle existant")
    parser.add_argument("--visualize", action="store_true", help="Générer les graphiques (après --train)")
    parser.add_argument("--predict", action="store_true", help="Faire une prédiction")
    parser.add_argument("--mois", type=int, default=6, help="Mois (1-12)")
    parser.add_argument("--heure", type=int, default=14, help="Heure (0-23)")
    parser.add_argument("--type", type=str, default="ELECTRICITE", help="Type d'énergie")
    parser.add_argument("--temperature", type=float, default=25.0, help="Température extérieure (°C)")
    parser.add_argument("--n-estimators", type=int, default=200, help="Nombre d'arbres (défaut: 200)")
    parser.add_argument("--max-depth", type=int, default=12, help="Profondeur max des arbres (défaut: 12)")
    parser.add_argument("--min-samples-split", type=int, default=5, help="Échantillons min pour diviser (défaut: 5)")
    parser.add_argument("--min-samples-leaf", type=int, default=2, help="Échantillons min par feuille (défaut: 2)")
    parser.add_argument("--export", type=str, help="Exporter les données d'entraînement au format CSV")
    parser.add_argument("--data", type=str, default=DATA_FILE, help="Fichier de données CSV")

    args = parser.parse_args()

    # Créer les données d'exemple
    create_sample_data(args.data)

    if args.train:
        df = load_data(args.data)
        print(f"[DATA] {len(df)} lignes chargees")
        X, y = prepare_features(df)
        model, metrics = train_model(
            X, y,
            n_estimators=args.n_estimators,
            max_depth=args.max_depth,
            min_samples_split=args.min_samples_split,
            min_samples_leaf=args.min_samples_leaf,
        )
        save_model(model, metrics)

        # Métriques pour le parsing Java
        print(f"[METRICS] R2={metrics['r2']:.4f} MAE={metrics['mae']:.2f} RMSE={metrics['rmse']:.2f}")
        print(f"[METRICS] Train={metrics['n_train']} Test={metrics['n_test']}")

        # Afficher les features les plus importantes
        print("[FEATURES] Top 3:")
        feat_imp = sorted(zip(metrics["features"], metrics["feature_importances"]),
                         key=lambda x: x[1], reverse=True)
        for feat, imp in feat_imp[:3]:
            print(f"   {feat}: {imp:.3f}")

    if args.eval:
        df = load_data(args.data)
        X, y = prepare_features(df)
        model, metrics = load_trained_model()

        print(f"[METRICS] R2={metrics['r2']:.4f} MAE={metrics['mae']:.2f} RMSE={metrics['rmse']:.2f}")
        print(f"[METRICS] Train={metrics['n_train']} Test={metrics['n_test']}")

    if args.visualize:
        df = load_data(args.data)
        X, y = prepare_features(df)
        model, metrics = load_trained_model()
        visualize(model, metrics, X, y)

    if args.predict:
        model, metrics = load_trained_model()
        prediction, interval = predict(
            model, args.mois, args.heure, args.type, args.temperature
        )
        print(f"[PREDICT] {prediction:.2f} kWh estimes")
        print(f"[INTERVAL] [{interval[0]:.2f}, {interval[1]:.2f}] kWh")

    if args.export:
        df = load_data(args.data)
        X, y = prepare_features(df)
        df_export = X.copy()
        df_export["quantite"] = y
        df_export.to_csv(args.export, index=False)
        print(f"✔ Données exportées : {args.export} ({len(df_export)} lignes)")


if __name__ == "__main__":
    main()
