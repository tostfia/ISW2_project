# Analisi What-If dell'Impatto del Refactoring sulla Propensione ai Bug

[![SonarCloud](https://sonarcloud.io/images/project_badges/sonarcloud-white.svg)](https://sonarcloud.io/project/overview?id=tostfia_ISW2_project)

Studio  che esplora come il refactoring guidato da metriche possa ridurre la difettosità nei metodi software, utilizzando machine learning e analisi statica del codice sui progetti open source Apache Bookkeeper e Apache Storm.



### Progetti Analizzati
- **Apache Bookkeeper** - Sistema di storage distribuito
- **Apache Storm** - Piattaforma di elaborazione stream real-time

##  Obiettivi 

**RQ1**: Quale modello di machine learning predice meglio i metodi difettosi?

**RQ2**: Quanto potremmo ridurre i bug rifattorizzando preventivamente il codice problematico?

##  Come Funziona

### Raccolta Dati
 Collegato i bug tracciati in Jira con le modifiche al codice in Git, ricostruendo la storia evolutiva di ogni metodo attraverso le varie release del software. Per ogni metodo sono stati  estratti metriche che descrivono sia la sua struttura che la sua storia.

### Metriche Analizzate

**Metriche Strutturali** (cosa possiamo cambiare con il refactoring):
- Lunghezza del codice
- Complessità ciclomatica e cognitiva
- Profondità di annidamento
- Numero di parametri
- Code smells rilevati

**Metriche Storiche** (traccia dell'evoluzione):
- Quante volte è stato modificato
- Quanti sviluppatori ci hanno lavorato
- Quantità di codice aggiunto/rimosso nel tempo

###  Modelli Predittivi
Sono stati addestrati e confrontati tre classificatori di machine learning (Random Forest, IBk, Naive Bayes) per prevedere quali metodi diventeranno difettosi. Random Forest è emerso come il più affidabile su entrambi i progetti.

### Simulazione What-If
Una volta identificate le metriche più correlate ai bug, abbiamo selezionato i metodi più problematici e li abbiamo rifattorizzati applicando tecniche di Extract Method. Poi abbiamo chiesto al modello: "Se questo codice fosse stato così fin dall'inizio, quanti bug avremmo evitato?"

## Scoperte Principali

### Modelli Predittivi
**Random Forest** ha dimostrato le migliori performance su entrambi i progetti, bilanciando precisione e capacità di identificare i metodi realmente problematici.

### Fattori di Rischio

**Apache Bookkeeper** è sensibile alle metriche strutturali:
- La lunghezza del codice (LOC) è il principale indicatore modificabile
- Metodi lunghi e complessi tendono ad accumulare difetti
- Il refactoring strutturale ha un impatto significativo

**Apache Storm** è dominato dalla storia evolutiva:
- Il numero di autori e revisioni conta più della struttura
- La profondità di annidamento è l'indicatore strutturale principale
- I bug emergono da interazioni complesse e modifiche incrementali

### Impatto del Refactoring

I risultati dimostrano che:

1. **Il refactoring preventivo funziona**, ma l'efficacia dipende dal tipo di progetto
2. **Non tutti i progetti sono uguali**: Bookkeeper beneficia maggiormente di interventi strutturali, Storm richiede attenzione alla gestione del processo di sviluppo
3. **Le metriche storiche dominano**: la frequenza di modifica e il numero di contributori sono i predittori più forti
4. **Gli interventi mirati contano**: rifattorizzare strategicamente i metodi più a rischio può prevenire difetti significativi

##  Tecnologie e Strumenti

- **JavaParser** - Estrazione automatica delle metriche strutturali
- **PMD** - Rilevamento code smells
- **Weka** - Addestramento e validazione dei modelli ML
- **SonarCloud** - Analisi continua della qualità
- **Jira + Git** - Tracciamento dell'evoluzione dei bug


##  Contesto Accademico

**Università**: Università degli Studi di Roma "Tor Vergata"  
**Corso**: Ingegneria del Software 2 (ISW2)  
**Dipartimento**: Ingegneria Informatica  
**Anno Accademico**: 2024/2025  
**Autrice**: Sofia Tosti (Matricola 0369460)  




