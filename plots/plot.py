import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np
import os
import sys

# Verifica che sia stato fornito il nome del progetto
if len(sys.argv) < 2:
    print("Uso: python plot_refined.py <nome_progetto>")
    print("Esempio: python plot_refined.py STORM")
    sys.exit(1)

project_name = sys.argv[1]

# Carica i dati dal CSV
df = pd.read_csv('results_fold'+project_name+'.csv')

# Pulisci i nomi delle colonne (rimuovi spazi extra)
df.columns = df.columns.str.strip()


# Crea una directory per salvare i grafici
output_dir = 'plots/'+project_name
os.makedirs(output_dir, exist_ok=True)

# Imposta il font Arial per tutti i grafici
plt.rcParams['font.family'] = 'Arial'
plt.rcParams['font.size'] = 10

# Informazioni sul dataset
print("="*60)
print(f"GENERAZIONE BOX PLOT - PROGETTO: {project_name}")
print("="*60)
print(f"Dataset: {len(df)} righe")
print(f"Modelli: {', '.join(df['Model'].unique())}")
print("="*60)

# Definisci le metriche
metrics = ['Precision', 'Recall', 'F1', 'AUC', 'Kappa', 'Accuracy', 'NPofB20']

# Colori tenui per ogni modello
model_colors = {
    'NaiveBayes': '#FFE699',      # Giallo tenue
    'RandomForest': '#FFAD99',    # Rosso/salmone tenue
    'IBk': '#99C2FF'              # Blu tenue
}

# Colori per i punti (leggermente più saturi ma non troppo)
point_colors = {
    'NaiveBayes': '#FFD966',      # Giallo medio
    'RandomForest': '#FF8566',    # Rosso medio
    'IBk': '#6699FF'              # Blu medio
}

# Crea un box plot per ogni metrica
for metric in metrics:
    fig, ax = plt.subplots(figsize=(10, 7))

    # Prepara i dati per ogni modello
    models = df['Model'].unique()
    positions = range(len(models))

    # Crea box plot per ogni modello con il suo colore
    for i, model in enumerate(models):
        model_data = df[df['Model'] == model][metric]

        # Box plot con linee più sottili
        bp = ax.boxplot([model_data], positions=[i], widths=0.6,
                        patch_artist=True,
                        boxprops=dict(facecolor=model_colors[model],
                                      color='#333333', linewidth=1),
                        medianprops=dict(color='#333333', linewidth=1.5),
                        whiskerprops=dict(color='#333333', linewidth=0.8),
                        capprops=dict(color='#333333', linewidth=0.8),
                        flierprops=dict(marker='o', markerfacecolor=point_colors[model],
                                        markersize=4, alpha=0.5, markeredgecolor='#333333',
                                        markeredgewidth=0.5))

        # Aggiungi i punti individuali
        y_data = model_data.values
        x_data = [i] * len(y_data)
        # Aggiungi un po' di jitter per vedere meglio i punti sovrapposti
        x_jitter = x_data + (np.random.random(len(x_data)) - 0.5) * 0.15
        ax.scatter(x_jitter, y_data, color=point_colors[model],
                   alpha=0.25, s=15, edgecolors='none')

    # Aggiungi le medie
    means = df.groupby('Model')[metric].mean()
    ax.plot(positions, means.values, 'D', color='#333333',
            markersize=8, label='Mean', zorder=10, markeredgewidth=0.8,
            markerfacecolor='white')

    # Aggiungi statistiche sul grafico
    for i, model in enumerate(models):
        model_data = df[df['Model'] == model][metric]
        mean_val = model_data.mean()
        std_val = model_data.std()

        # Posiziona le statistiche sopra ogni box
        y_pos = model_data.max() + (model_data.max() - model_data.min()) * 0.05
        ax.text(i, y_pos, f'μ={mean_val:.3f}\nσ={std_val:.3f}',
                ha='center', va='bottom', fontsize=9, fontfamily='Arial',
                bbox=dict(boxstyle='round,pad=0.4', facecolor='white',
                          alpha=0.9, edgecolor='#CCCCCC', linewidth=0.8))

    # Personalizza il grafico
    ax.set_title(f'{project_name} - {metric} Distribution by Model',
                 fontsize=14, fontweight='normal', pad=15, fontfamily='Arial')
    ax.set_xlabel('Model', fontsize=11, fontweight='normal', fontfamily='Arial')
    ax.set_ylabel(metric, fontsize=11, fontweight='normal', fontfamily='Arial')
    ax.set_xticks(positions)
    ax.set_xticklabels(models, fontsize=10, fontfamily='Arial')
    ax.grid(True, alpha=0.2, axis='y', linestyle='-', color='#DDDDDD', linewidth=0.5)
    ax.legend(loc='upper right', fontsize=9, framealpha=0.95, edgecolor='#CCCCCC',
              prop={'family': 'Arial'})

    # Aggiungi il range sul lato destro
    ax.text(1.02, 0.5, f'Range:\n{df[metric].min():.3f} - {df[metric].max():.3f}',
            transform=ax.transAxes, fontsize=8, fontfamily='Arial',
            verticalalignment='center',
            bbox=dict(boxstyle='round,pad=0.4', facecolor='white',
                      alpha=0.95, edgecolor='#CCCCCC', linewidth=0.8))

    # Sfondo bianco pulito
    ax.set_facecolor('white')
    fig.patch.set_facecolor('white')

    # Rimuovi i bordi superiore e destro
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['left'].set_color('#CCCCCC')
    ax.spines['left'].set_linewidth(0.8)
    ax.spines['bottom'].set_color('#CCCCCC')
    ax.spines['bottom'].set_linewidth(0.8)

    # Tick più sottili
    ax.tick_params(axis='both', which='major', labelsize=9, width=0.8,
                   length=4, color='#CCCCCC')

    plt.tight_layout()

    # Salva il grafico
    filename = f'boxplot_{metric.lower()}_refined.png'
    filepath = os.path.join(output_dir, filename)
    plt.savefig(filepath, dpi=300, bbox_inches='tight', facecolor='white')
    print(f"✓ Salvato: {filename}")
    plt.close()

# Crea anche un riepilogo con tutti i box plot in una griglia
print("\n" + "="*60)
print("CREAZIONE GRIGLIA COMPLETA")
print("="*60)

fig, axes = plt.subplots(3, 3, figsize=(18, 14))
axes = axes.flatten()
fig.patch.set_facecolor('white')

for idx, metric in enumerate(metrics):
    ax = axes[idx]

    # Prepara i dati per ogni modello
    models = df['Model'].unique()
    positions = range(len(models))

    # Crea box plot per ogni modello con il suo colore
    for i, model in enumerate(models):
        model_data = df[df['Model'] == model][metric]

        bp = ax.boxplot([model_data], positions=[i], widths=0.6,
                        patch_artist=True,
                        boxprops=dict(facecolor=model_colors[model],
                                      color='#333333', linewidth=0.8),
                        medianprops=dict(color='#333333', linewidth=1.2),
                        whiskerprops=dict(color='#333333', linewidth=0.6),
                        capprops=dict(color='#333333', linewidth=0.6),
                        flierprops=dict(marker='o', markerfacecolor=point_colors[model],
                                        markersize=3, alpha=0.5, markeredgecolor='#333333',
                                        markeredgewidth=0.4))

    # Aggiungi le medie
    means = df.groupby('Model')[metric].mean()
    ax.plot(positions, means.values, 'D', color='#333333',
            markersize=6, label='Mean' if idx == 0 else '', zorder=10,
            markeredgewidth=0.6, markerfacecolor='white')

    ax.set_title(f'{metric}', fontsize=12, fontweight='normal', fontfamily='Arial')
    ax.set_xlabel('')
    ax.set_ylabel('Score', fontsize=9, fontfamily='Arial')
    ax.set_xticks(positions)
    ax.set_xticklabels(models, rotation=0, fontsize=8, fontfamily='Arial')
    ax.grid(True, alpha=0.2, axis='y', linestyle='-', color='#DDDDDD', linewidth=0.5)
    ax.set_facecolor('white')

    # Rimuovi bordi superiore e destro
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['left'].set_color('#CCCCCC')
    ax.spines['left'].set_linewidth(0.6)
    ax.spines['bottom'].set_color('#CCCCCC')
    ax.spines['bottom'].set_linewidth(0.6)

    # Tick più sottili
    ax.tick_params(axis='both', which='major', labelsize=8, width=0.6,
                   length=3, color='#CCCCCC')

    if idx == 0:
        ax.legend(loc='upper right', fontsize=8, framealpha=0.95,
                  edgecolor='#CCCCCC', prop={'family': 'Arial'})

# Rimuovi gli assi extra
for idx in range(len(metrics), len(axes)):
    axes[idx].axis('off')

plt.suptitle(f'{project_name} Project - Complete Performance Metrics Overview',
             fontsize=15, fontweight='normal', y=0.995, fontfamily='Arial')
plt.tight_layout()
plt.savefig(os.path.join(output_dir, 'boxplot_summary_grid_refined.png'),
            dpi=300, bbox_inches='tight', facecolor='white')
print("✓ Salvato: boxplot_summary_grid_refined.png")
plt.close()

# Crea legenda dei colori
print("\n" + "="*60)
print("SCHEMA COLORI TENUI:")
print("="*60)
print("NaiveBayes:   Giallo tenue (#FFE699)")
print("RandomForest: Rosso/salmone tenue (#FFAD99)")
print("IBk:          Blu tenue (#99C2FF)")
print("="*60)

print("\n" + "="*60)
print(f"✓ Generati {len(metrics)} box plot individuali + 1 griglia completa")
print(f"  Progetto: {project_name}")
print("  Font: Arial")
print("  Stile: Minimale e professionale")
print("="*60)