# DESIGN.md — Documento di design del prodotto

Versione 0.2 — 2026-09-21 (perimetro minimale/medio, data-entry, appunti a blocchi, conferma esplicita, niente transazioni, indici/FK verificati, stile «alla Apple»). Descrive **cosa** fa RamaSQL Client e **come si presenta**. Il *come è costruito* è in `ARCHITECTURE.md`; il *quando* in `ROADMAP.md`. Una funzione che non compare qui non fa parte della v1 (regola 5 di `CLAUDE.md`).

## 1. Visione e pubblico

Client desktop per MariaDB/MySQL usato da **studenti delle superiori durante le lezioni di basi di dati** e dal docente che proietta. Deve poter essere spiegato in dieci minuti. Tre promesse:

1. **Si capisce subito** — una finestra, poche voci, italiano.
2. **Insegna l'SQL mentre lo si usa** — ogni clic mostra l'SQL che produce.
3. **Fa vedere le relazioni** — query e viste si costruiscono disegnando; il database si legge come diagramma ER.

Non è uno strumento per amministratori di database.

## 1-bis. Perimetro della v1: progetto minimale/medio (**questa sezione prevale sul resto del documento**)

Indicazione dell'utente (2026-09-21): il primo progetto è **minimale/medio**. **Il *cosa* lo decide la lista dei requisiti dell'utente (8 iniziali + il 9º, data-entry); Workbench indica solo *come* farlo** (organizzazione, nomi, flusso) e non è un elenco di funzioni da replicare: è uno strumento «super-pro», questo no. Nelle sezioni §3 le voci marcate **[dopo]** sono idee già ragionate ma **fuori dalla v1**; tutto ciò che non è marcato è v1.

| # | Requisito dell'utente | **v1 — si fa** | **[dopo] — rinviato** |
|---|---|---|---|
| 1 | Connessioni MariaDB/MySQL | profili (nome, host, porta, utente, catalogo), prova connessione con errori chiari, tipo server rilevato, import/export profili per l'aula; **una connessione attiva per volta**; password chiesta alla connessione | più sessioni insieme, password salvate (DPAPI), opzioni SSL, colori, SSH |
| 2 | Creare/modificare tabelle | editor con Colonne (nome, tipo, lunghezza, PK, NN, UQ, AI, UNSIGNED, default, commento), `CREATE`/`ALTER` a differenze, rinomina/elimina/svuota | riordino colonne, duplica struttura, elementi avanzati (conservati, non editabili) |
| 3 | Indici, FK, integrità referenziale — **pienamente in v1 e verificati** (ribadito dall'utente) | scheda Indici (PRIMARY/UNIQUE/INDEX, multi-colonna, crea/modifica/elimina), scheda Chiavi esterne (colonne, tabella e colonne riferite, ON DELETE/ON UPDATE: RESTRICT, CASCADE, SET NULL, NO ACTION; crea/modifica/elimina); **controlli prima** (engine InnoDB sui due lati, tipi compatibili, indice sulla colonna riferita, SET NULL su NOT NULL, **righe orfane**); **verifica dopo**: a ogni applicazione il client rilegge i metadati dal server e conferma che l'indice/la FK esiste esattamente come richiesto; errori 1451/1452 spiegati; indici e FK visibili nel navigatore e nel modello ER | FULLTEXT, prefissi d'indice, elenco a discesa dei valori FK in griglia |
| 4 | InnoDB e MyISAM | scelta e conversione dell'engine, icona nell'albero, FK disabilitate su MyISAM con spiegazione | — |
| 5 | Query editor + raw + SQL sempre mostrato | editor visivo SQLeo; editor SQL con evidenziazione, completamento, esegui corrente/tutto, interrompi, apri/salva; **anteprima SQL su ogni operazione + registro** | EXPLAIN, formattatore, cronologia, ripristino schede, «query interne» nel registro; **[forse dopo]** gestione transazioni (autocommit, Commit, Rollback) |
| 6 | Viste con editor grafico | crea/sostituisci vista dal query builder; riapertura grafica a tre livelli con ripiego su SQL | opzioni ALGORITHM / SQL SECURITY / CHECK OPTION |
| 7 | Import JSON, CSV; dump selettivo e totale | import CSV e JSON (array di oggetti piatti) in tabella esistente o nuova; dump con scelta oggetti e struttura/dati/entrambi in un file `.sql`; esecuzione di uno script `.sql` | JSON Lines, annidati appiattiti, «aggiorna su duplicato», file degli scarti, export per tabella in cartella |
| 8 | Modello logico / ER | retroingegneria, relazioni fisiche da FK + **relazioni logiche** a mano e **suggerite per nome**, disposizione automatica, salvataggio `.rsqlmodel`, aggiorna dal database, esporta PNG | note, colori, livelli di dettaglio, SVG/PDF, «Crea chiave esterna…» dal modello, join suggeriti al query builder dalle relazioni logiche |
| 9 | **Apertura tabelle in modalità data-entry** (aggiunto dall'utente il 2026-09-21) | doppio clic su una tabella → **griglia modificabile** come in Workbench: riga vuota in fondo per l'inserimento, modifica in cella, elimina righe, NULL impostabile, valori predefiniti e AUTO_INCREMENT rispettati; **nessuna scrittura implicita: l'inserimento si conferma con un pulsante esplicito («Conferma»)** → anteprima `INSERT/UPDATE/DELETE` → esecuzione in autocommit; pulsante *Scarta* per buttare le modifiche pendenti; **appunti su sotto-intervalli rettangolari di celle**: selezione a blocco, copia come testo tabulato compatibile con Excel/Calc, incolla di un blocco a partire dalla cella attiva con creazione di nuove righe, taglia/cancella sul blocco; **scheda record** (un record per volta, campi in colonna — il «Form Editor» di Workbench) per tabelle con molte colonne; sfoglia con limite 1000 e paginazione, ordina; esporta CSV. Richiede PK o UNIQUE, altrimenti sola lettura con spiegazione | elenco a discesa dei valori ammessi sulle colonne FK (primo candidato per la v1.1), filtri per colonna, editor BLOB/JSON, export JSON/INSERT; **[forse dopo]** conferma avvolta in una transazione |
| — | Aula | italiano, conferme sulle operazioni pericolose, spiegazione degli errori più comuni, **suggerimenti esplicativi su ogni elemento, comprese le singole voci delle liste a discesa** (aggiunto dall'utente il 2026-09-27), carattere ingrandibile, profili distribuibili, nessuna telemetria | file di politica `aula.json`, tema scuro, guida integrata estesa |

## 2. Principi d'interfaccia

- **«Alla Apple»** (indicazione dell'utente): minimale nell'aspetto, medio nelle funzioni, semplice nell'uso. In pratica: **una sola via ovvia** per fare ogni cosa (niente tre menu per la stessa azione); **valori predefiniti giusti** al posto delle opzioni (le opzioni avanzate non si nascondono in un pannello: non esistono); molto spazio bianco, poche icone chiare con testo, nessuna barra affollata; finestre di dialogo con al massimo una decisione per volta; parole semplici al posto del gergo («Conferma», non «Apply changes to data source»); ogni schermata deve reggere la domanda «cosa posso togliere?». La profondità (SQL, indici, FK) c'è, ma si incontra solo quando la si cerca.
- **Finestra unica a tre zone** (non riconfigurabili): *Navigatore* a sinistra · *Area di lavoro* a schede al centro · *Pannello SQL* in basso (Registro / Anteprima / Messaggi), ridimensionabile e comprimibile ma sempre presente.
- **Barra strumenti corta** (≤ 10 pulsanti con testo): Connetti · Nuova query · Nuova query visiva · Nuova tabella · Nuova vista · Importa · Esporta/Dump · Modello ER · Esegui · Interrompi.
- **Anteprima SQL prima di ogni modifica**: finestra «SQL che verrà eseguito» con *Esegui*, *Copia nell'editor*, *Annulla*. Sempre, senza opzioni per saltarla.
- **Operazioni pericolose** (`DROP`, `TRUNCATE`, `DELETE`/`UPDATE` senza `WHERE`, ripristino su catalogo non vuoto): conferma con il nome dell'oggetto da riscrivere o casella esplicita.
- **Errori**: messaggio originale del server (codice + testo) + una riga di spiegazione in italiano per i più frequenti + posizione evidenziata nell'editor quando disponibile.
- **Suggerimenti ovunque** (indicazione dell'utente, 2026-09-27: «fondamentale in un ambito come questo»): passando con il mouse su **qualunque** elemento — pulsanti, voci di menu, campi, caselle, intestazioni, nodi del navigatore — compare un **trafiletto di testo completo** che spiega cosa fa e che cosa comporta. Vale anche per **ogni singola voce delle liste a discesa**: scegliendo il tipo di una colonna, l'engine, la collation, l'azione ON DELETE o il tipo d'indice, lo studente legge la spiegazione della voce su cui si trova *prima* di sceglierla (es. «MyISAM: non supporta le chiavi esterne né le transazioni; …»). È il modo in cui il programma insegna mentre si usa: la profondità si incontra quando la si cerca, e il suggerimento è il punto in cui la si cerca. Specifiche grafiche in `DESIGN-SYSTEM.md` §3.9, scelte tecniche in `ADR-020`.
- **Aspetto**: FlatLaf chiaro (adatto al proiettore); dimensione carattere regolabile con Ctrl+rotella e dalle impostazioni. **[dopo]** tema scuro.
- **Scorciatoie** allineate a Workbench dove sensato: Ctrl+Invio esegue l'istruzione corrente, Ctrl+Maiusc+Invio tutto lo script, Ctrl+T nuova scheda query, F5 aggiorna il navigatore.

## 3. Mappa delle funzionalità

### 3.1 Connessioni (req. 1)
- Schermata iniziale con le connessioni salvate come **tessere** (nome, host, tipo e versione del server all'ultima connessione, colore).
- Dati di un profilo: nome, host, porta (3306), utente, catalogo predefinito (facoltativo), nota. La password si chiede alla connessione e resta in memoria per la sessione. **[dopo]** password salvata (D-08), opzioni SSL (in v1: comportamento predefinito del driver), colore.
- *Prova connessione* con diagnosi leggibile (host irraggiungibile, porta chiusa, accesso negato, SSL richiesto, plugin di autenticazione).
- Tipo di server **rilevato**, non chiesto: MariaDB o MySQL + versione, mostrati nella barra di stato.
- **Importa/Esporta profili** (JSON senza password) → il docente distribuisce `connessioni-3A.json`; l'installer può preinstallarne uno.
- **Una connessione attiva per volta** in v1 (cambiare connessione chiude le schede della precedente, con conferma). **[dopo]** più sessioni contemporanee.
- Fuori v1: tunnel SSH, socket/pipe, gestione utenti e privilegi.

### 3.2 Navigatore degli oggetti
- Albero: Server → Cataloghi → **Tabelle** (con icona diversa per InnoDB/MyISAM) → Colonne, Indici, Chiavi esterne · **Viste** · Routine / Trigger / Eventi (sola lettura, D-06).
- Filtro rapido per nome. Cataloghi di sistema nascosti per default.
- Menu contestuale essenziale: *Apri dati* · *Modifica struttura* · *Nuova…* · *Rinomina* · *Duplica struttura* · *Svuota* · *Elimina* · *Mostra SQL di creazione* · *Copia nome* · *Esporta…* · *Aggiungi al modello ER* · *Usa in una query visiva*.
- Creazione/eliminazione di cataloghi (con charset/collation).

### 3.3 Apertura tabelle in modalità data-entry (req. 9) e griglia dei risultati
- **Doppio clic su una tabella = data-entry**: la tabella si apre in una scheda con griglia modificabile (modello: Result Grid di Workbench). Limite righe predefinito (1000), paginazione, ordinamento per colonna. **[dopo]** filtro per colonna.
- **Inserimento**: riga vuota sempre presente in fondo; Tab/Invio per avanzare di cella; i campi lasciati vuoti usano il DEFAULT della colonna, gli AUTO_INCREMENT restano vuoti e si leggono dopo la *Conferma*.
- **Modifica ed eliminazione**: in cella; righe modificate/nuove/eliminate evidenziate con colori diversi finché non applicate.
- **Conferma esplicita dell'inserimento** (richiesta dell'utente: «insert con pulsante commit esplicito»): **nessuna scrittura implicita**. Inserimenti, modifiche ed eliminazioni restano *pendenti* nella griglia — uscire dalla riga, cambiare pagina o ordinare **non** salva nulla — finché l'utente non preme il pulsante **Conferma** nella barra della scheda (Ctrl+S). Accanto, **Scarta** butta le modifiche pendenti e ricarica i dati (è un'operazione locale: sul server non è stato scritto nulla). Un contatore mostra «3 inserimenti · 1 modifica · 0 eliminazioni in sospeso»; i due pulsanti sono attivi solo se c'è qualcosa in sospeso.
- **Conferma** → anteprima delle `INSERT/UPDATE/DELETE` generate (chiave = PK o UNIQUE nel `WHERE`) → esecuzione **una istruzione alla volta, in autocommit**. Al primo errore (duplicato, FK violata, NOT NULL) ci si ferma: le righe già scritte risultano salvate, la riga colpevole viene indicata con l'errore spiegato, le restanti restano pendenti per la correzione.
- **Niente gestione delle transazioni in v1** (indicazione dell'utente): nessun `START TRANSACTION`/`COMMIT`/`ROLLBACK` generato dal client, nessun pulsante Commit/Rollback di transazione, nessun interruttore autocommit. La connessione lavora sempre in autocommit. Chi vuole una transazione la scrive a mano nell'editor SQL. **[forse dopo]** — vedi §4-bis.
- Chiudere la scheda, cambiare tabella o disconnettersi con modifiche in sospeso → domanda «Conferma, scarta o resta?».
- **Appunti su intervalli rettangolari** (richiesta dell'utente): la selezione è **a celle**, non a righe — trascinamento o Maiusc+frecce/clic selezionano un sotto-rettangolo qualsiasi della tabella (clic sull'intestazione = colonna intera, sul numero di riga = riga intera, Ctrl+A = tutto).
  - **Copia (Ctrl+C)**: il blocco va negli appunti come testo tabulato (tabulazioni tra celle, a-capo tra righe), il formato che Excel, LibreOffice Calc e Fogli Google incollano direttamente in celle. Variante *Copia con intestazioni*. NULL copiato come cella vuota.
  - **Incolla (Ctrl+V)**: un blocco rettangolare proveniente da un foglio di calcolo, da un'altra griglia del client o da testo tabulato viene steso **a partire dalla cella attiva**, cella per cella; se supera l'ultima riga si creano **nuove righe** (inserimento in blocco: si incolla un elenco da Excel e si ottengono N `INSERT`); se supera l'ultima colonna, l'eccedenza è scartata con avviso. Un singolo valore incollato su una selezione di più celle le riempie tutte.
  - **Taglia (Ctrl+X) / Canc** sull'intervallo: celle portate a NULL (o a vuoto se la colonna è NOT NULL testuale).
  - Ogni valore incollato è validato contro il tipo della colonna; le celle non valide sono marcate e bloccano la *Conferma* finché non corrette. Colonne AUTO_INCREMENT e di sola lettura saltate con avviso.
  - L'incolla non scrive nulla sul database: produce modifiche pendenti come la digitazione, quindi passa dal pulsante *Conferma* → anteprima SQL. *Annulla* (Ctrl+Z) ritira l'ultimo incolla in blocco.
  - Nelle griglie di sola lettura (risultati di query, viste) vale la sola copia.
- **Scheda record**: interruttore Griglia ⇄ Scheda; un record per volta con i campi incolonnati, etichetta = nome colonna + tipo, pulsanti precedente/successivo/nuovo/elimina. Stesse modifiche pendenti e stessi pulsanti **Conferma**/**Scarta**.
- NULL distinguibile (grigio «NULL») e impostabile da menu contestuale/Canc; editor a finestra per testo lungo; date come testo validato. **[dopo]** editor BLOB e JSON.
- Tabelle senza PK/UNIQUE e viste: sola lettura, con spiegazione del perché.
- **[dopo]** sulle colonne con FK: elenco a discesa dei valori ammessi dalla tabella riferita.
- La stessa griglia, in **sola lettura**, mostra i risultati dell'editor SQL e del query editor visivo.
- Esporta: CSV e copia negli appunti. **[dopo]** JSON, SQL INSERT.

### 3.4 Editor SQL «raw» (req. 5)
- RSyntaxTextArea: evidenziazione MySQL/MariaDB, completamento di cataloghi/tabelle/colonne/parole chiave, parentesi abbinate, trova/sostituisci, commenta righe. **[dopo]** formattazione.
- Esegui istruzione al cursore / selezione / script intero; **Interrompi** (`KILL QUERY` da connessione di servizio).
- Risultati multipli in sotto-schede; per le istruzioni senza risultato: righe interessate, durata, avvisi (`SHOW WARNINGS`).
- Apri/salva file `.sql`. Connessione sempre in autocommit: il client non gestisce transazioni (chi le vuole le scrive a mano nello script).
- **[dopo]** `EXPLAIN` con un clic · cronologia per connessione · ripristino delle schede non salvate. **[forse dopo]** gestione transazioni: interruttore autocommit, pulsanti Commit/Rollback (§4-bis).

### 3.5 Pannello SQL (req. 5 — «SQL mostrato sempre»)
- **Registro**: ogni istruzione eseguita dal client per conto dell'utente, con ora, connessione, origine (Editor, Editor tabelle, Griglia, Import, Dump, Modello ER…), esito, durata, righe. Filtrabile; doppio clic → copia in una scheda dell'editor; esportabile come `.sql` («il compito di oggi in SQL»).
- **Anteprima**: l'SQL dell'operazione grafica *in corso*, aggiornato dal vivo mentre si compila una finestra (es. editor tabelle).
- **Messaggi**: errori e avvisi.
- **[dopo]** interruttore «mostra anche le query interne» (lettura metadati), utile al docente per spiegare `information_schema`.

### 3.6 Editor di tabelle (req. 2, 3, 4)
Schede, sul modello di Workbench ridotto all'essenziale:
- **Colonne** — griglia: nome · tipo (elenco dei tipi comuni + testo libero) · lunghezza/valori · PK · NN · UQ · AI · UNSIGNED · default · commento. **[dopo]** riordino con trascinamento. Tipi proposti raggruppati: numerici, testo, data/ora, altri.
- **Indici** — nome, tipo (PRIMARY/UNIQUE/INDEX), una o più colonne in ordine; crea, modifica, elimina. Avvisi: indice duplicato di uno esistente, UNIQUE su dati che contengono duplicati (con la query che li trova). **[dopo]** FULLTEXT, prefisso e direzione.
- **Chiavi esterne** — nome, colonne, tabella e colonne riferite, ON DELETE / ON UPDATE (RESTRICT, CASCADE, SET NULL, NO ACTION); crea, modifica (= elimina + ricrea), elimina. **Controlli prima** (`FEASIBILITY.md` F-03), mostrati come avvisi accanto alla riga: engine InnoDB sui due lati, tipi compatibili, indice sulla colonna riferita, SET NULL su colonna NOT NULL, e **righe orfane** (pulsante «Verifica dati»: mostra la query e le righe che farebbero fallire il vincolo).
- **Verifica dopo l'applicazione** (indici e FK): eseguito l'`ALTER`, il client **rilegge i metadati dal server** e confronta ciò che trova con ciò che era stato chiesto (nome, colonne e loro ordine, unicità, tabella riferita, azioni). Esito mostrato all'utente: «✔ verificato sul server» oppure l'elenco delle differenze (es. nome assegnato dal server, indice creato implicitamente da una FK). Indici e FK compaiono subito nel navigatore.
- **Opzioni** — **engine (InnoDB / MyISAM)**, charset, collation, AUTO_INCREMENT iniziale, commento. Cambio engine con spiegazione delle conseguenze (FK, transazioni).
- **SQL** — anteprima viva del `CREATE`/`ALTER` risultante.
- *Applica* → anteprima → esecuzione istruzione per istruzione con esito; in caso d'errore ci si ferma, si mostra cosa è già stato applicato (il DDL in MySQL/MariaDB non è transazionale) e si ricarica lo stato reale.
- Conservati ma non editabili in v1: partizioni, colonne generate, CHECK, tipi spaziali.

### 3.7 Query editor visivo (req. 5) — da SQLeo
- Scheda «Query visiva»: elenco tabelle/viste del catalogo a sinistra → si trascinano nel **diagramma**; i **join** si disegnano tra i campi (o si propongono da FK e relazioni logiche del modello ER); clic sul join per tipo (INNER/LEFT/RIGHT) e condizione.
- Albero della query: SELECT (colonne, espressioni, alias, aggregati, DISTINCT) · FROM · WHERE · GROUP BY · HAVING · ORDER BY; sottoquery e tabelle derivate per quanto SQLeo supporta; `LIMIT`.
- Vista **SQL** sincronizzata (§3.4); dal testo si torna al grafico se il parser lo consente, altrimenti avviso non bloccante.
- Esegui → griglia risultati. Salva come `.sql` (il diagramma si ricostruisce dal testo; l'impaginazione si salva in un commento di coda o file affiancato — da decidere nello Step 7).
- Pulsante **«Salva come vista…»** (→ §3.8).
- Rimozioni rispetto a SQLeo: limite 3 tabelle, versione «completa», pivot, riferimenti a DBMS diversi, definizione manuale dei metadati.

### 3.8 Viste (req. 6)
- *Nuova vista* apre il query editor visivo in **modalità vista**: campo nome, `OR REPLACE`. **[dopo]** ALGORITHM, SQL SECURITY, WITH CHECK OPTION.
- Anteprima: `CREATE [OR REPLACE] VIEW … AS …`.
- *Modifica vista*: strategia a tre livelli di `FEASIBILITY.md` F-06 (sorgente locale → parser su definizione del server → editor SQL).
- *Apri dati* della vista come per una tabella (sola lettura salvo viste aggiornabili: v1 sempre sola lettura).

### 3.9 Importazione (req. 7)
Procedura guidata unica «Importa dati» — **CSV** e **JSON** (dettagli e limiti in `FEASIBILITY.md` F-07a/b):
1. File e formato (rilevati; codifica, separatore, virgolette, riga d'intestazione).
2. Anteprima delle prime righe.
3. Destinazione: tabella esistente (mappatura colonne, con abbinamento automatico per nome) o nuova tabella (tipi dedotti, modificabili; `CREATE TABLE` in anteprima).
4. Opzioni: svuota prima · in caso di duplicato: errore / ignora · stringa vuota = NULL · formato date. **[dopo]** «aggiorna su duplicato».
5. Esecuzione a lotti in autocommit con avanzamento; *Interrompi* ferma l'import (le righe già inserite restano, e il rapporto dice quante). Rapporto finale: righe lette, inserite, scartate con motivo. **[dopo]** file degli scarti. **[forse dopo]** import «tutto o niente» in transazione.

«Esegui script SQL…» (ripristino di un dump) è una voce distinta del menu Importa.

### 3.10 Dump / Esportazione (req. 7)
Procedura guidata «Esporta / Dump»:
1. **Cosa**: albero a caselle — server → cataloghi → tabelle, viste, (routine, trigger, eventi). Scorciatoie: *tutto il catalogo*, *tutti i cataloghi*.
2. **Come**, per oggetto o in blocco: *struttura* · *dati* · *struttura + dati*.
3. Opzioni: `DROP … IF EXISTS` · `CREATE DATABASE`/`USE` · INSERT estesi (n righe per istruzione) · disabilita controlli FK durante il ripristino · un file `.sql`. **[dopo]** dati in CSV/JSON per tabella in una cartella.
4. Esecuzione con avanzamento; lettura in streaming (tabelle grandi senza esaurire la memoria). **[forse dopo]** istantanea coerente in transazione (in aula nessuno scrive durante il dump).

### 3.11 Modello ER (req. 8)
- Documento `.rsqlmodel` aperto in una scheda.
- **Retroingegneria**: «Nuovo modello dal catalogo…» → scelta tabelle → entità con colonne (PK, FK, tipi) e relazioni dalle FK reali. **[dopo]** livelli di dettaglio.
- **Relazioni logiche**: disegnate a mano trascinando da colonna a colonna, o accettate dai **suggerimenti** (convenzioni di nome + compatibilità di tipo); tratteggiate, con cardinalità (1:1, 1:N, N:M indicativa) e etichetta. Non toccano il database.
- Notazione a **zampa di gallina**; opzionale notazione semplificata «1 — N».
- Disposizione automatica, zoom, trascinamento. **[dopo]** panoramica, note testuali, colori per gruppi di tabelle.
- *Aggiorna dal database*: aggiunge/aggiorna/segna come mancanti le entità senza perdere le posizioni.
- Ponte verso il resto: doppio clic su entità → editor tabella. **[dopo]** relazione logica → «Crea chiave esterna…» · «Nuova query visiva con queste tabelle» con join proposti anche dalle relazioni logiche.
- Esporta PNG. **[dopo]** SVG, PDF, stampa.
- Fuori v1: generazione di un intero schema dal diagramma, sincronizzazione bidirezionale.

### 3.12 Modalità aula e impostazioni
- Impostazioni ridotte a **quattro voci**: lingua, dimensione carattere, limite righe, cartella di lavoro.
- **[dopo]** **File di politica** facoltativo (`aula.json`, nella cartella d'installazione o passato dall'installer) con cui il docente/tecnico può: preinstallare connessioni, vietare il salvataggio delle password, bloccare `DROP DATABASE`, fissare il limite righe, nascondere cataloghi.
- Guida rapida (un PDF/HTML breve in italiano, aperto dal menu) e «Informazioni su» con licenze e attribuzioni (SQLeo, SQLeonardo, librerie).

## 4. Corrispondenza con MySQL Workbench (riferimento funzionale, `ADR-004`)

**Regola:** tolto l'editor visivo di query e viste (che viene da SQLeo), per le funzionalità generali il riferimento è **MySQL Workbench** (`github.com/mysql/mysql-workbench`): stessa organizzazione concettuale, stessi nomi dove possibile, stesse scorciatoie. **Workbench dice *come*, non *cosa*:** si riprende solo ciò che serve ai requisiti dell'utente (§1-bis); tutto il resto di Workbench, che è uno strumento professionale, resta fuori. Nella tabella, «come Workbench» significa «stesso flusso, nei limiti della v1 di §1-bis». Chi impara su RamaSQL deve ritrovarsi su Workbench senza sorprese. In caso di dubbio su *come* debba comportarsi una funzione, si guarda come fa Workbench e si semplifica; ci si discosta solo per motivi didattici, annotandolo qui. È un riferimento di **comportamento**: nessun codice di Workbench viene copiato.

| Workbench | RamaSQL | Trattamento |
|---|---|---|
| Home screen con tessere delle connessioni | §3.1 schermata iniziale a tessere | **come Workbench** |
| Manage Server Connections (Standard TCP/IP, SSL, test) | §3.1 profilo di connessione | **ridotto**: solo TCP/IP; niente SSH, socket/pipe, parametri avanzati |
| Navigator → Schemas (Tables, Views, Stored Procedures, Functions) | §3.2 navigatore | **come Workbench**; routine/trigger/eventi in sola lettura |
| Schema Inspector / Table Inspector | «Mostra SQL di creazione» + proprietà essenziali | **ridotto** |
| SQL Editor: schede query, esecuzione al cursore/selezione/script, Stop, autocommit, Commit/Rollback, completamento, formattazione (beautify), snippet | §3.4 editor SQL | **ridotto**: esecuzione, Stop, completamento; **niente autocommit/Commit/Rollback** (forse dopo), niente beautify, snippet, aiuto contestuale |
| Result Grid con modifica e Apply → finestra «Review the SQL Script»; Form Editor; copia di righe | §3.3 data-entry: griglia modificabile con pulsante **Conferma** esplicito, scheda record, anteprima SQL | **come Workbench** nel flusso (modifiche pendenti → Apply → revisione dello script → esecuzione) — ed è il modello della nostra pipeline: da noi la revisione vale per **ogni** operazione. Scostamento voluto: gli **appunti lavorano su blocchi rettangolari di celle** (Workbench copia per righe) |
| Output → Action Output / History | §3.5 pannello SQL (Registro, Messaggi) | **come Workbench, potenziato**: sempre visibile, con origine dell'istruzione ed esportazione `.sql` |
| Limit Rows (1000) | §3.3 limite righe predefinito 1000 | **come Workbench** |
| Safe Updates (blocco UPDATE/DELETE senza WHERE su chiave) | §2 conferme per operazioni pericolose | **adattato**: conferma esplicita invece del blocco via `sql_safe_updates` |
| Table Editor: schede Columns (PK, NN, UQ, B, UN, ZF, AI, G), Indexes, Foreign Keys, Triggers, Partitioning, Options | §3.6 editor di tabelle | **ridotto**: Colonne (PK, NN, UQ, UN, AI), Indici, Chiavi esterne, Opzioni, SQL; niente Triggers/Partitioning/colonne generate |
| Create Schema / Alter Schema (charset, collation) | §3.2 crea/elimina catalogo | **come Workbench** |
| Create View (solo editor di testo) | §3.8 viste | **sostituito** dall'editor grafico SQLeo; l'editor di testo resta come ripiego |
| Query builder visivo | — (Workbench non ne ha) | **da SQLeo** §3.7 |
| Table Data Import Wizard (CSV, JSON) | §3.9 importazione | **come Workbench**: stessi passi (file → destinazione esistente/nuova → mappatura colonne → esecuzione) |
| Table Data Export Wizard / Export Resultset | §3.3 esporta risultato, §3.10 dati per tabella | **come Workbench** |
| Server → Data Export (selezione schemi/oggetti, *Dump Structure and Data / Data Only / Structure Only*, file unico o cartella, opzioni) | §3.10 dump | **come Workbench** nelle scelte offerte; **diverso sotto**: generatore proprio via JDBC, non `mysqldump` (motivi in `ANALYSIS.md` §4) |
| Server → Data Import / Restore | §3.9 «Esegui script SQL…» | **come Workbench**, ridotto al file unico |
| Database → Reverse Engineer → EER Diagram | §3.11 retroingegneria e diagramma | **come Workbench** per notazione (zampa di gallina), livelli di dettaglio, disposizione automatica |
| EER: relazioni = FK del modello, Forward Engineer, Synchronize Model | §3.11 relazioni **logiche** non vincolanti | **diverso per requisito**: in Workbench ogni relazione *è* una FK; da noi il modello è indipendente dalle FK e non vincola il database. Niente forward engineering/sincronizzazione in v1 |
| EXPLAIN / Visual Explain | §3.4 `EXPLAIN` tabellare | **ridotto** |
| Server Status, Client Connections, Users and Privileges, Status/System Variables, Startup/Shutdown, Logs, Options File | — | **escluso** |
| Performance Dashboard / Reports, Performance Schema setup | — | **escluso** |
| Migration Wizard, Schema Transfer, Compare/Synchronize Schemas | — | **escluso** |
| Scripting Shell (Python/GRT), plugin | — | **escluso** |
| Preferences (decine di pagine) | §3.12 una pagina | **ridotto** |

## 4-bis. Forse dopo (idee parcheggiate, nessun impegno)

Diverse dalle voci **[dopo]** (che sono la naturale v1.x): qui stanno le funzioni che l'utente ha chiesto esplicitamente di **non** fare ora e di rivalutare più avanti.

- **Gestione delle transazioni** (indicazione del 2026-09-21): interruttore autocommit, pulsanti Commit/Rollback nell'editor SQL, conferma del data-entry avvolta in `START TRANSACTION … COMMIT` con rollback automatico in caso d'errore, import «tutto o niente», dump con istantanea coerente. In v1 la connessione è sempre in autocommit e il client non genera mai istruzioni di transazione.

L'elenco vive anche in `BUGS.md` (sezione «Forse dopo»), dove si aggiungono le nuove idee.

## 5. Fuori perimetro della v1 (deliberatamente)
Amministrazione server (utenti, privilegi, variabili, stato, log) · performance/piani grafici · migrazione tra DBMS · replica · editor grafico di routine/trigger/eventi · tunnel SSH · confronto/sincronizzazione di schemi · forward engineering dal modello · altri DBMS · aggiornamento automatico · plugin/scripting.

## 6. Requisiti non funzionali
- **Piattaforma**: Windows 10/11 x64 (il jar resta eseguibile su Linux/macOS, non supportato ufficialmente in v1).
- **Installazione**: per-utente senza admin, < 100 MB su disco, avvio < 3 s su PC da laboratorio (SSD, 8 GB).
- **Reattività**: nessuna operazione di rete o di database sul thread dell'interfaccia; tutto ciò che dura > 200 ms mostra avanzamento ed è interrompibile.
- **Robustezza**: caduta di connessione gestita con riconnessione proposta; schede e testo non salvato recuperati al riavvio.
- **Dati locali**: `%APPDATA%\RamaSQL\` (profili, cronologia, preferenze); nessun dato lascia il PC.
- **Accessibilità minima**: tutto raggiungibile da tastiera, contrasto adeguato al proiettore.
- **Licenza**: GPL-3.0-or-later; sorgenti disponibili con ogni rilascio.
- **Dati degli studenti** (indicazione dell'utente, 2026-09-27): gli alunni sono identificati **solo con nome e cognome**; tutti gli altri dati stanno sulle piattaforme istituzionali della scuola. Sul server si conservano soltanto le **lezioni e i test** (i database su cui lavorano gli studenti), che il docente a fine anno deve comunque **consegnare e mostrare**: nessun dato sensibile. Conseguenze per il progetto: niente campi o funzioni che raccolgano altri dati personali; l'esportazione di fine anno si appoggia al dump (§3.10).
- **Messa in produzione** (indicazione dell'utente, 2026-09-27): le password attuali (`docs/local DBs.txt`) sono **solo dei server locali di sviluppo**. Per l'uso in aula si useranno **password nuove e robuste** e un **server/database diverso**; le credenziali di sviluppo non si riportano mai in produzione.
