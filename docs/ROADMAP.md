# ROADMAP.md — Piano di sviluppo a step, con i test di validazione

**Perimetro:** progetto **minimale/medio**, «alla Apple». Ogni step realizza solo la colonna «v1» di `DESIGN.md` §1-bis; le voci **[dopo]** e **[forse dopo]** non si toccano, nemmeno «già che ci siamo».

**Regole:** uno step alla volta, in ordine. Uno step è chiuso solo quando **tutti i suoi test di validazione sono superati, ciascuno con un'evidenza** (output del test, schermata, file prodotto) riportata in `JOURNAL.md`; poi **stop per la revisione dell'utente**, che include il suo **controllo incrociato con Navicat**. Le decisioni aperte D-xx sono in `ANALYSIS.md` §8.

Stato: ✅ completato · 🔄 in corso · ⏳ da fare

| Step | Titolo | Dim. | Stato |
|---|---|---|---|
| A | Analisi, fattibilità, design, piano | S | ✅ 2026-09-21 |
| 0 | Fondamenta | S | ✅ 2026-09-21 |
| 1 | **Spike di fattibilità (go/no-go)** | M | ⏳ |
| 2 | Shell dell'applicazione e connessioni | M | ⏳ |
| 3 | Navigatore, pipeline SQL, pannello SQL | M | ⏳ |
| 4 | Editor SQL raw e **data-entry** (griglia, appunti a blocchi, conferma esplicita) | L | ⏳ |
| 5 | Editor di tabelle (colonne, opzioni, engine) | L | ⏳ |
| 6 | **Indici, chiavi esterne, integrità referenziale** (con verifica sul server) | M | ⏳ |
| 7 | Query editor visivo (integrazione SQLeo) | L | ⏳ |
| 8 | Viste grafiche | M | ⏳ |
| 9 | Importazione CSV e JSON | M | ⏳ |
| 10 | Dump selettivo/totale e ripristino | M | ⏳ |
| 11 | Modello ER e retroingegneria | L | ⏳ |
| 12 | Rifiniture per l'aula e guida rapida | S | ⏳ |
| 13 | **Installer e distribuzione** | M | ⏳ |
| 14 | Collaudo in aula e rilascio 1.0 | M | ⏳ |

S = una sessione · M = 2–3 sessioni · L = 4+ sessioni (stime da affinare dopo lo Step 1).

**Tappe:** M1 «Client di base» = fine Step 6 (già usabile in aula) · M2 «Visivo» = fine Step 8 · M3 «Completo» = fine Step 11 · M4 «Rilascio» = fine Step 14.
**Installer: solo alla fine** (indicazione dell'utente, 2026-09-21): `Setup.exe` e ZIP portabile si fanno nello Step 13, **dopo una versione stabile**. Fino ad allora il programma si prova con `avvia.cmd`. Lo spike sull'installer (ex S3) è spostato nello Step 13.

## Come si valida uno step

Quattro tipi di test, indicati nella colonna **Tipo**:

| Tipo | Cos'è | Chi | Evidenza |
|---|---|---|---|
| **U** | test di unità automatico (JUnit), senza database | `mvnw verify` | report dei test |
| **I** | test d'integrazione automatico contro MariaDB **e** MySQL reali (cataloghi `ramasql_test_*` creati e distrutti dal test) | `mvnw verify` con `RAMASQL_IT_*` | report dei test, per server |
| **M** | test manuale sull'applicazione, con procedura e risultato atteso scritti qui | Claude (QA in creazione) | schermata / registro SQL esportato |
| **N** | **controllo incrociato con Navicat**: l'utente apre con Navicat ciò che il client ha creato e confronta | **utente** | esito comunicato in revisione |

Il tipo **N** è la validazione indipendente: uno strumento terzo, maturo, deve vedere gli oggetti **esattamente come il client dichiara di averli creati**. Se Navicat mostra qualcosa di diverso, è un difetto del client fino a prova contraria → `BUGS.md`.

### Schema di prova canonico: «biblioteca»
Usato in tutti gli step (script in `it-tests/fixtures/`, creato dallo Step 3):
`autori(id PK AI, cognome, nome, nazionalita)` · `libri(id PK AI, titolo, isbn UNIQUE, anno, prezzo DECIMAL, id_editore)` · `editori(id PK AI, nome, citta)` · `libri_autori(id_libro, id_autore, PK composta)` · `soci(id PK AI, tessera UNIQUE, cognome, nome, email, nato_il DATE)` · `prestiti(id PK AI, id_libro, id_socio, data_prestito, data_reso NULL)`; FK: `libri→editori`, `libri_autori→libri/autori` (CASCADE), `prestiti→libri/soci` (RESTRICT). Dati: ≈ 50 autori, 200 libri, 100 soci, 500 prestiti, con accenti, apostrofi, emoji, NULL.
**Casi reali già presenti sul PC di sviluppo** (cancellabili, ma utili: si usano in copia): `bibliotecasoft` su MariaDB (5 tabelle, **2 viste**, 7 FK) e `scuola` su MySQL (4 tabelle, 3 FK, tabella ponte). Si affiancano allo schema canonico negli spike S2c/S6 e negli step 7, 8, 10, 11: sono database scritti da altri, quindi mettono alla prova il client su ciò che non ha creato lui.
Variante **`biblioteca_myisam`**: stesse tabelle in MyISAM, **senza alcuna FK** (serve a Step 6 e 11). Variante **`grande`**: una tabella da 1 milione di righe generata.

## Metodo di esecuzione (vale per ogni step)

1. **Preparazione** — rilettura di `JOURNAL.md`, `BUGS.md`, dello step; D-xx richieste chiuse, altrimenti stop e domanda.
2. **Piano dello step** — elenco concreto dei task → approvazione dell'utente prima del codice.
3. **Sviluppo** in task piccoli; i test U/I dello step si scrivono **insieme** al codice, non dopo; `mvnw -q verify` dopo ogni task.
4. **Validazione** — esecuzione di tutti i test U/I/M dello step; tabella degli esiti in `JOURNAL.md`.
5. **Documentazione** — ADR, `ARCHITECTURE.md`/`DESIGN.md`/`CONSOLE.md`, `BUGS.md`, stato qui sopra.
6. **Stop** — revisione dell'utente + test **N** con Navicat. Commit su richiesta: `Step N: <titolo>`.

## Dipendenze

```
A ─ 0 ─ 1 (spike) ─ 2 ─ 3 ─┬─ 4 ─┬─ 5 ─ 6 ──────────────┬─ 11 (ER) ─┐
                            │     └─ 9 (import) ─ 10 (dump)│          ├─ 12 ─ 13 ─ 14
                            └─ 7 (visivo) ─ 8 (viste) ─────┘──────────┘
```
7–8 possono precedere 5–6 se si vuole mostrare presto la parte distintiva.

---

## Step 0 — Fondamenta
**Richiede:** D-01 (nome), D-02 (licenza), D-04 (repository).
**Task:** JDK 25 Temurin e Inno Setup 6 sul PC di sviluppo · `git init`, `.gitignore`, `.editorconfig`, `LICENSE` GPL-3, `NOTICE`, `README.md` · Maven padre + moduli vuoti `core`, `model`, `sqleo-qb`, `app`, `it-tests` con wrapper · finestra vuota con FlatLaf · `docs/CONSOLE.md`.

| ID | Tipo | Procedura | Risultato atteso |
|---|---|---|---|
| T0.1 | U | clone pulito in una cartella nuova → `mvnw verify` (Maven **non** installato) | build verde; 1 test fittizio eseguito per ciascun modulo |
| T0.2 | M | `mvnw -pl app exec:java` | si apre la finestra vuota con tema FlatLaf chiaro, titolo e icona del prodotto |
| T0.3 | M | ricerca nel repository di `password`, `jdbc:`, indirizzi di server | nessuna credenziale; `.gitignore` copre `*.local.*`, `target/`, `dist/` |
| T0.4 | M | controllo file | `LICENSE` (GPL-3), `NOTICE` con attribuzioni SQLeonardo/SQLeo presenti |

## Step 1 — Spike di fattibilità (go/no-go)
**Richiede:** D-03 (✅ server locali). Codice in `spikes/`, usa-e-getta; risultati in `docs/SPIKE-STEP1.md`.

| ID | Tipo | Domanda → procedura | Superato se |
|---|---|---|---|
| S1 | M | **Il query builder di SQLeo, estratto, gira su JDK 25?** Estrarre `com.sqleo.querybuilder` + minimo `common`, sostituire `Application`/`Preferences`/MDI con stub, compilare con `--release 25`, aprirlo in un `JFrame` nostro | compila senza riferimenti a `Application`/MDI; finestra funzionante; elenco dei file toccati; limite 3 tabelle rimosso (5 tabelle nel diagramma) |
| S2a | M | **Grafico→SQL su entrambi i server:** costruire sulla `biblioteca` una query a 4 tabelle con join, WHERE, GROUP BY, ORDER BY | l'SQL generato è eseguito senza errori su MariaDB **e** MySQL con lo stesso risultato |
| S2b | U | **SQL→grafico:** campionario di 20 query «da aula» passate a `SQLParser` | tabella esiti: ok / non rappresentabile; nessuna eccezione non gestita; ≥ 15 ok |
| S2d | M+U | **Query nidificate** (richiesta dell'utente): sottoquery in `WHERE` (`IN`, `NOT IN`, `EXISTS`, confronto con `(SELECT MAX…)`), sottoquery nella lista `SELECT`, tabella derivata in `FROM`, CTE `WITH`, due livelli di annidamento; una vista che usa un'altra vista; una vista che contiene una sottoquery. Per ciascuna: costruzione grafica, SQL→grafico→SQL, esecuzione su MariaDB e MySQL | tabella esiti per costrutto (grafico ok / solo testo); stesso risultato sui due server; nessuna perdita di testo. Esito → aggiorna `FEASIBILITY.md` F-05bis |
| S2c | I | **Viste rilette dal server:** creare 10 viste, rileggere `information_schema.VIEWS.VIEW_DEFINITION` su entrambi i server, passarle al parser (con bozza di normalizzatore) | tabella esiti per server; strategia a tre livelli confermata o corretta (R-02) |
| S4 | I | **Driver unico?** MariaDB Connector/J contro MySQL 8.4 e MariaDB 11: connessione, `caching_sha2_password`, metadati di colonne/indici/FK, `KILL QUERY` | tutti verdi → driver unico; altrimenti doppio driver → ADR |
| S5 | M | **Resa:** QB sotto FlatLaf chiaro a 100/150/200% di scala | nessun testo tagliato o colore illeggibile; elenco correzioni necessarie |
| S6 | M | **Canvas ER:** prototipo Java2D con 30 entità e 40 relazioni | trascinamento e zoom fluidi, zampa di gallina, esportazione PNG |
| S7 | M | **Appunti a blocchi:** `JTable` con selezione a celle; copia 3×4 → Excel e LibreOffice Calc; blocco 20×3 da Excel → incolla | i blocchi cadono nelle celle giuste nei due sensi, inclusi valori con tabulazione, a-capo, virgolette |

**Esito:** **GO / GO con riserve / NO-GO** per ciascuno. NO-GO su S1, S2a o S2d rimette in discussione `ADR-001`/`ADR-002` (ripiego: fork integrale di SQLeo su JDK 21; estremo: riesame C#). Stime aggiornate.

## Step 2 — Shell dell'applicazione e connessioni
**Task:** struttura a tre zone · schermata iniziale a tessere · `ConnectionProfile` e archivio (senza password) · prova connessione con diagnosi · `ServerInfo` · import/export profili · barra di stato · impostazioni (4 voci) · i18n.

| ID | Tipo | Procedura | Risultato atteso |
|---|---|---|---|
| T2.1 | I | connessione a MariaDB e a MySQL | `ServerInfo` riporta tipo e versione corretti per entrambi |
| T2.2 | U | classificazione degli errori di connessione da eccezioni simulate | 5 cause distinte riconosciute |
| T2.3 | M | provocare: host inesistente, porta chiusa, password errata, utente senza accesso al catalogo, catalogo inesistente | 5 messaggi distinti, in italiano, che dicono cosa correggere; codice originale del server visibile |
| T2.4 | M | host irraggiungibile (IP non instradabile) | interfaccia reattiva durante l'attesa; *Annulla* funziona; timeout ≤ 10 s |
| T2.5 | U | serializzazione profili → JSON → profili | identici; il JSON **non contiene** alcun campo password |
| T2.6 | M | esporta profili → importa con un altro utente Windows | tessere presenti, connessione riuscita dopo aver digitato la password |
| T2.7 | M | dopo l'uso, ricerca in `%APPDATA%\RamaSQL` della password usata | assente |
| T2.9 | M | «test dei 10 secondi»: una persona che non ha mai visto il client deve connettersi partendo da una tessera | ci riesce senza aiuto |

## Step 3 — Navigatore, pipeline SQL, pannello SQL
**Richiede:** D-06.
**Task:** `core.metadata` · albero con caricamento pigro e filtro · `core.exec` (`SqlScript`, `SqlExecutor`, `SqlLog`, classi di rischio) · finestra di anteprima · pannello Registro/Anteprima/Messaggi · operazioni sull'albero: crea/elimina catalogo, rinomina/svuota/elimina tabella, mostra SQL di creazione · fixture `biblioteca`.

| ID | Tipo | Procedura | Risultato atteso |
|---|---|---|---|
| T3.1 | I | lettura metadati di `biblioteca` e `biblioteca_myisam` su entrambi i server | tabelle, colonne (tipo, NULL, default, AI), indici, FK, engine, viste = attesi (confronto con un JSON di riferimento) |
| T3.2 | I | catalogo generato con 500 tabelle | lettura dell'elenco < 2 s |
| T3.3 | M | aprire l'albero su `biblioteca` | icone distinte InnoDB/MyISAM; colonne, indici e FK sotto ogni tabella; cataloghi di sistema nascosti; filtro per nome funzionante |
| T3.4 | U | classificazione di rischio di 30 istruzioni (`SELECT`, `INSERT`, `UPDATE` con/senza WHERE, `DROP`, `TRUNCATE`, `ALTER`…) | SAFE / MODIFIES / DESTRUCTIVE corretti |
| T3.5 | M | *Elimina tabella* → anteprima → **Annulla** | sul server la tabella esiste ancora; registro: zero istruzioni |
| T3.6 | M | *Crea catalogo*, *Rinomina tabella*, *Svuota*, *Elimina* → **Esegui** | ogni operazione: anteprima mostrata, poi riga nel registro con origine, esito, durata; albero aggiornato da solo |
| T3.7 | M | `DROP`/`TRUNCATE` | conferma rafforzata richiesta |
| T3.8 | M | esportare il registro come `.sql` e rieseguirlo su un catalogo vuoto | stesso risultato finale |
| T3.9 | U | test di architettura: ricerca nel codice di `execute(`, `executeUpdate(`, `executeQuery(` | presenti solo in `SqlExecutor` e nel canale metadati |
| T3.10 | **N** | Navicat: aprire il server dopo T3.6 | catalogo creato con charset/collation scelti; tabella rinominata; tabella eliminata assente |

## Step 4 — Editor SQL raw e data-entry
**Task (editor):** RSyntaxTextArea + completamento dai metadati · separatore di istruzioni (`DELIMITER`, commenti, stringhe) · esegui al cursore/selezione/script · interrompi · risultati multipli · apri/salva `.sql`. Connessione sempre in autocommit, nessuna gestione transazioni.
**Task (data-entry, req. 9):** griglia con paginazione e ordinamento · modello a modifiche pendenti · riga d'inserimento · selezione a celle e **appunti su blocchi rettangolari** · validazione per tipo · pulsanti **Conferma**/**Scarta** con contatore → DML in anteprima, eseguito riga per riga · scheda record · NULL e testo lungo · esporta CSV.

| ID | Tipo | Procedura | Risultato atteso |
|---|---|---|---|
| T4.1 | U | separatore di istruzioni: 30 casi (`;` dentro stringhe e commenti, `DELIMITER //`, commenti `--`, `#`, `/* */`, backtick, script vuoto, ultima istruzione senza `;`) | istruzioni separate esattamente come atteso |
| T4.2 | M | script di 50 istruzioni miste (DDL, DML, SELECT) con `DELIMITER` | eseguito in ordine; risultati multipli in sotto-schede; righe interessate e durata per ciascuna |
| T4.3 | M | `SELECT SLEEP(30)` → *Interrompi* | interrotto in < 2 s; la sessione resta utilizzabile |
| T4.4 | M | digitare `SELECT * FROM li` + Ctrl+Spazio; poi `libri.` | propone `libri`, `libri_autori`; poi le colonne di `libri` |
| T4.5 | M | `UPDATE soci SET nome='x'` (senza WHERE) | conferma esplicita richiesta |
| T4.6 | M | errore di sintassi voluto | messaggio del server + spiegazione in italiano + posizione evidenziata |
| T4.7 | I | tabella `grande` (1 M righe): apertura, pagina 2, ordinamento | prima pagina < 1 s; memoria stabile (< 300 MB) |
| T4.8 | U | generatore DML da modifiche pendenti: inserimento con DEFAULT e AI omessi, UPDATE solo delle colonne cambiate, WHERE su PK semplice e composta, su UNIQUE in assenza di PK, NULL, apostrofi, emoji, DECIMAL, DATE | SQL atteso per ogni caso; nessuna istruzione di transazione generata |
| T4.9 | M | **nessuna scrittura implicita:** inserire 3 righe in `soci`, cambiare riga, ordinare, cambiare pagina, **senza** premere Conferma | registro: zero istruzioni; sul server nulla; contatore «3 inserimenti in sospeso» |
| T4.10 | M | 3 modifiche + 1 inserimento + 1 eliminazione → **Conferma** | anteprima con esattamente 5 istruzioni corrette, senza `START TRANSACTION`/`COMMIT`; dopo l'esecuzione i dati sul server coincidono; l'`id` AI della riga nuova compare in griglia |
| T4.11 | M | 3 inserimenti di cui il 2º con `tessera` duplicata → Conferma | 1º scritto e marcato salvato; 2º indicato con errore 1062 spiegato; 3º ancora pendente; corretto il 2º → Conferma → tutto scritto |
| T4.12 | M | **Scarta** con modifiche pendenti; poi chiusura scheda con modifiche pendenti | Scarta ripristina i dati del server; alla chiusura domanda «Conferma, scarta o resta?» |
| T4.13 | M | inserimento in `prestiti` con `id_socio` inesistente | errore 1452 spiegato in italiano, riga resta pendente |
| T4.14 | M | tabella senza PK né UNIQUE; vista | sola lettura, con spiegazione; copia consentita |
| T4.15 | U | appunti: blocco → testo tabulato → blocco, con tabulazioni, a-capo, virgolette, NULL, stringa vuota | andata e ritorno senza alterazioni (convenzione Excel) |
| T4.16 | M | selezionare un blocco 3×4 in mezzo alla griglia → Ctrl+C → incollare in **Excel** e in **LibreOffice Calc**; ripetere con *Copia con intestazioni* | 3×4 (o 4×4) celle corrette in entrambi |
| T4.17 | M | copiare da Excel un blocco 20×3 → cella attiva sulla riga d'inserimento di `autori` → Ctrl+V → Conferma | 20 righe pendenti → 20 `INSERT` in anteprima → 20 righe sul server |
| T4.18 | M | incollare un blocco 2×2 **sopra** celle esistenti; incollare un valore su una selezione 5×1; incollare un blocco più largo della tabella | celle sovrascritte come pendenti (→ `UPDATE`); 5 celle riempite; eccedenza scartata con avviso |
| T4.19 | M | incollare testo in una colonna INT e una data non valida | celle marcate, Conferma bloccata finché non corrette; Ctrl+Z ritira l'intero incolla |
| T4.20 | M | Ctrl+X / Canc su un blocco con colonne NULL e NOT NULL | NULL dove ammesso, vuoto/avviso dove no |
| T4.21 | M | scheda record: inserire un socio completo e modificarne un altro → Conferma | stesso comportamento della griglia |
| T4.22 | M | esporta CSV di `libri` e riaprilo in Excel | colonne e accenti corretti |
| T4.23 | **N** | Navicat: aprire `soci`, `autori`, `prestiti` dopo T4.10–T4.17 | righe inserite/modificate/eliminate esattamente come nel client; accenti, apostrofi, emoji, NULL (non stringa «NULL») corretti |

## Step 5 — Editor di tabelle
**Task:** modello di modifica · schede Colonne/Opzioni/SQL · `TableDiff` → `CREATE`/`ALTER` minimo · engine InnoDB/MyISAM con avvisi · charset/collation · esecuzione passo-passo con ricarica dello stato reale in caso d'errore.

| ID | Tipo | Procedura | Risultato atteso |
|---|---|---|---|
| T5.1 | U | `TableDiff`: ≥ 60 casi — nuova tabella; aggiungi/rimuovi/rinomina colonna; cambio tipo, lunghezza, NULL, default (incluso `CURRENT_TIMESTAMP`, stringa vuota, NULL), AI, UNSIGNED, commento; PK semplice e composta aggiunta/tolta/cambiata; engine; charset/collation; rinomina tabella; combinazioni; **nessuna modifica → zero istruzioni** | SQL atteso per MariaDB e per MySQL, con identificatori tra backtick (nomi con spazi e parole riservate inclusi) |
| T5.2 | I | andata/ritorno sui due server: per 20 casi di T5.1 crea → applica ALTER → rileggi metadati | modello riletto = modello atteso |
| T5.3 | I | tabella con CHECK, colonna generata, partizioni (create via script) → modifica di un'altra colonna dal generatore | gli elementi non editabili sopravvivono identici |
| T5.4 | M | creare `editori` e `libri` della `biblioteca` da zero con l'editor | l'anteprima viva coincide **carattere per carattere** con l'SQL eseguito (confronto con il registro) |
| T5.5 | M | cambiare `libri.titolo` da VARCHAR(100) a VARCHAR(20) con dati più lunghi | avviso di possibile troncamento prima di eseguire; errore del server spiegato se rifiutato |
| T5.6 | M | sequenza di 3 ALTER in cui il 2º fallisce | l'utente vede cosa è stato applicato e cosa no; l'editor ricarica lo stato reale dal server |
| T5.7 | M | conversione InnoDB→MyISAM di `editori` (riferita da FK) e di una tabella libera; conversione inversa | la prima bloccata con spiegazione; le altre eseguite; icona nell'albero aggiornata |
| T5.8 | M | creare una tabella con nome `ordine dettagli` e colonna `order` | creata correttamente (backtick) |
| T5.9 | **N** | Navicat → *Design Table* su ogni tabella creata/modificata in T5.4–T5.8 | scheda **Fields**: nomi, tipi, lunghezze, Not Null, default, Auto Increment, Unsigned, commenti, chiave primaria identici a quanto impostato nel client; scheda **Options**: engine, charset, collation identici; *DDL* di Navicat equivalente all'SQL mostrato dal client |

## Step 6 — Indici, chiavi esterne, integrità referenziale
**Task:** schede Indici e Chiavi esterne (crea, modifica, elimina) · controlli **prima** (engine, tipi, indice riferito, SET NULL/NOT NULL, righe orfane, duplicati per UNIQUE) · **verifica dopo** (rilettura metadati e confronto con il richiesto) · estensione di `TableDiff` · indici e FK nel navigatore · spiegazione errori 1451/1452/1062/1215/1005.

| ID | Tipo | Procedura | Risultato atteso |
|---|---|---|---|
| T6.1 | U | `TableDiff` indici: crea/elimina/rinomina INDEX, UNIQUE, PRIMARY; multi-colonna con ordine; modifica colonne di un indice (= DROP + ADD nello stesso ALTER) | SQL atteso per entrambi i server |
| T6.2 | U | `TableDiff` FK: crea con ciascuna delle 4 azioni per ON DELETE e ON UPDATE (16 combinazioni); FK composta; elimina; modifica (= DROP FK poi ADD, **ordine corretto**); FK autoreferenziale | SQL atteso; sintassi `DROP FOREIGN KEY` corretta per entrambi i server |
| T6.3 | U | controlli preventivi: INT vs INT UNSIGNED, INT vs BIGINT, VARCHAR con collation diverse, colonna riferita senza indice, SET NULL su colonna NOT NULL, tabella MyISAM su uno dei due lati | ciascun caso produce il suo avviso **senza contattare il server** |
| T6.4 | I | **verifica dopo:** per ogni indice e FK di T6.1–T6.2, applicare e rileggere da `information_schema` (`STATISTICS`, `KEY_COLUMN_USAGE`, `REFERENTIAL_CONSTRAINTS`) | il verificatore dichiara «conforme»; con un caso alterato ad arte (FK creata con azione diversa) dichiara la differenza |
| T6.5 | I | integrità reale sui due server: con FK RESTRICT, `DELETE` del padre → errore 1451; `INSERT` del figlio orfano → 1452; con CASCADE, `DELETE` del padre → figli eliminati; con SET NULL → figli a NULL | comportamento del server = dichiarato dalla FK creata dal client |
| T6.6 | M | costruire con l'editor tutte le FK e gli indici della `biblioteca` partendo dalle tabelle nude | dopo ogni *Esegui* compare «✔ verificato sul server»; indici e FK visibili nel navigatore |
| T6.7 | M | aggiungere una FK `prestiti.id_socio→soci.id` con 3 righe orfane presenti: prima «Verifica dati», poi eseguire comunque | le 3 righe orfane sono elencate con la query usata; l'ALTER fallisce con 1452 spiegato; corrette le righe, la FK si crea e risulta verificata |
| T6.8 | M | aggiungere UNIQUE su `soci.email` con duplicati presenti | avviso con la query che trova i duplicati; errore 1062 spiegato se si procede |
| T6.9 | M | scheda Chiavi esterne su tabella di `biblioteca_myisam` | disabilitata, con spiegazione (MyISAM non supporta FK) e proposta di convertire in InnoDB |
| T6.10 | M | dal data-entry (Step 4): eliminare un editore con libri (RESTRICT), eliminare un libro con autori (CASCADE) | 1451 spiegato; nel secondo caso le righe di `libri_autori` spariscono (verificato riaprendo la tabella) |
| T6.11 | M | **Tappa M1** — esercitazione completa: creare la `biblioteca` da zero **solo con il client** (tabelle, indici, FK, dati con data-entry e incolla da Excel); esportare il registro SQL; rieseguirlo su un catalogo vuoto | il secondo catalogo è identico al primo (confronto metadati + `CHECKSUM TABLE`) |
| T6.12 | **N** | Navicat → *Design Table* → schede **Indexes** e **Foreign Keys** su ogni tabella di T6.6 | per ogni indice: nome, colonne nell'ordine giusto, tipo (Normal/Unique); per ogni FK: nome, campi, tabella e campi riferiti, **On Delete / On Update** identici a quanto scelto nel client |
| T6.13 | **N** | Navicat: provare a violare l'integrità (inserire un prestito con socio inesistente; eliminare un editore con libri) | Navicat riceve gli stessi errori 1452/1451 → i vincoli sono reali sul server, non solo nel client |
| T6.14 | **N** | Navicat → *Reverse Database to Model* (o ER Diagram) sulla `biblioteca` creata in T6.11 | tutte le relazioni attese compaiono nel diagramma di Navicat |

## Step 7 — Query editor visivo (SQLeo)
**Richiede:** dall'utente un elenco di query/esercizi tipici del corso per il campionario.
**Task:** modulo `sqleo-qb` definitivo (dal lavoro di S1) + `UPSTREAM.md` · `QbHost` e `QueryBuilderPanel` · scheda «Query visiva» con viste Grafica/SQL sincronizzate · join proposti dalle FK · esecuzione e risultati · gestione «non rappresentabile» · resa FlatLaf/HiDPI · salvataggio `.sql`.

| ID | Tipo | Procedura | Risultato atteso |
|---|---|---|---|
| T7.1 | U | ricerca nel modulo `sqleo-qb` di `isFullVersion`, `Donate`, `google-analytics`, `VERSION_TRACK`, nomi di altri DBMS | zero occorrenze |
| T7.2 | U | parser: campionario ≥ 40 query da aula (join interni/esterni, alias, funzioni aggregate, GROUP BY/HAVING, ORDER BY, LIMIT, DISTINCT, sottoquery in WHERE, IN, BETWEEN, LIKE, IS NULL, backtick, nomi qualificati) — SQL→modello→SQL | per ogni query: esito atteso (rappresentabile sì/no) rispettato; per le rappresentabili, l'SQL rigenerato è **equivalente** |
| T7.3 | I | per le query rappresentabili di T7.2: eseguire originale e rigenerata sui due server | stesso risultato (stesse righe) |
| T7.4 | M | trascinare `libri`, `editori`, `libri_autori`, `autori`, `prestiti` (5 tabelle) | tutte accettate; join proposti dalle FK; nessun avviso di limite |
| T7.5 | M | clic su un join → LEFT; aggiungere filtro `anno > 2000`, raggruppare per editore con `COUNT(*)`, ordinare | l'SQL nella vista testo si aggiorna a ogni gesto; *Esegui* mostra il risultato atteso |
| T7.6 | M | modificare l'SQL a mano (aggiungere una colonna) → tornare alla vista grafica | il diagramma riflette la modifica |
| T7.7 | M | scrivere una query con funzione finestra (`ROW_NUMBER() OVER …`) → vista grafica | avviso «non rappresentabile graficamente», testo intatto, esecuzione possibile |
| T7.7b | M | **query nidificate:** costruire graficamente «libri con prezzo sopra la media» (sottoquery in WHERE), «editori con almeno un libro» (`EXISTS`), «numero di prestiti per socio» come tabella derivata in FROM e poi filtrata; aprire la sottoquery dall'albero della query, modificarla, tornare alla query esterna | ogni sottoquery si apre e si modifica come una query a sé; l'SQL esterno si aggiorna; risultati corretti su entrambi i server |
| T7.8 | M | due schede «Query visiva» aperte su tabelle diverse, lavorare alternando | nessuna interferenza (rischio R-06) |
| T7.9 | M | scala schermo 100% e 150% | nessun testo tagliato nel diagramma e nelle maschere |
| T7.10 | M | controllo GPL: ogni file ereditato modificato ha nota di modifica; intestazioni originali intatte; `UPSTREAM.md` elenca i file | conforme |
| T7.11 | M | prova d'uso: persona che non conosce il client, consegna «elenco dei soci con il numero di prestiti, solo chi ne ha più di 3» | completata in < 5 minuti senza aiuto |
| T7.12 | **N** | Navicat → *Query*: incollare l'SQL prodotto in T7.5 e T7.11 ed eseguirlo | stesso risultato del client |

## Step 8 — Viste grafiche
**Task:** modalità vista del QB · `CREATE OR REPLACE VIEW` in anteprima · archivio dei sorgenti originali · normalizzatore delle definizioni del server · ripiego su editor SQL · viste nel navigatore e in griglia (sola lettura).

| ID | Tipo | Procedura | Risultato atteso |
|---|---|---|---|
| T8.1 | U | generatore: `CREATE VIEW`, `CREATE OR REPLACE VIEW`, `DROP VIEW`, nomi con backtick | SQL atteso |
| T8.2 | U | normalizzatore: 15 definizioni reali prese da `information_schema.VIEWS` di MariaDB e MySQL (dal lavoro di S2c) | forma accettata dal parser per i casi rappresentabili; mai eccezioni |
| T8.3 | I | creare 10 viste sui due server, rileggerle, riaprirle (livello 2) | tabella esiti; per le riaperte, l'SQL rigenerato produce gli stessi dati della vista |
| T8.4 | M | creare graficamente `v_prestiti_aperti` (3 tabelle, filtro `data_reso IS NULL`) → salvare → chiudere → *Modifica vista* | riaperta graficamente identica (livello 1); aggiungere una colonna → `CREATE OR REPLACE` → la vista restituisce la nuova colonna |
| T8.5 | M | *Modifica vista* su 5 viste create **fuori** dal client (script) | ciascuna si apre in grafico o, se non rappresentabile, nell'editor SQL con avviso; nessun blocco, nessuna perdita |
| T8.6 | M | aprire i dati di una vista | griglia in sola lettura, copia a blocchi consentita |
| T8.7 | M | eliminare una tabella usata da una vista, poi aprire la vista | errore del server spiegato (vista non valida) |
| T8.7b | M | **viste nidificate:** vista con sottoquery (`v_libri_sopra_media`); vista costruita su un'altra vista (`v_riepilogo` che usa `v_prestiti_aperti`); le due viste reali di `bibliotecasoft` | create graficamente e riaperte; la vista-su-vista mostra l'altra vista come «tabella» nel diagramma; eliminare la vista di base segnala che l'altra non è più valida |
| T8.8 | **N** | Navicat → *Views*: aprire le viste di T8.4 (dati e *Design View*) | esistono, restituiscono gli stessi dati, la definizione è quella attesa |

## Step 9 — Importazione CSV e JSON
**Task:** lettori in streaming · rilevamento formato/codifica · deduzione tipi · procedura guidata a 5 passi · inserimento a lotti in autocommit · rapporto finale.

| ID | Tipo | Procedura | Risultato atteso |
|---|---|---|---|
| T9.1 | U | lettore CSV: separatori `;` `,` tab; virgolette con a-capo e virgolette raddoppiate; UTF-8 con e senza BOM; Windows-1252; riga finale vuota; righe con colonne in più/in meno | record letti come atteso; codifica e separatore rilevati |
| T9.2 | U | deduzione dei tipi: interi, decimali con virgola e con punto, date `gg/mm/aaaa` e ISO, booleani, testo lungo, colonna tutta vuota, zeri iniziali (CAP, telefono → testo) | tipo proposto atteso per ogni colonna |
| T9.3 | U | lettore JSON: array di oggetti piatti; chiavi mancanti in alcuni oggetti; valori null; oggetto annidato (→ testo JSON); file non valido | righe attese; errore chiaro con posizione per il file non valido |
| T9.4 | I | import di `soci.csv` (100 righe) in tabella esistente e di `libri.json` (200) in **nuova tabella** sui due server | righe sul server = righe del file; tipi della nuova tabella = dedotti |
| T9.5 | I | CSV da 1 M di righe | completato; memoria stabile; *Interrompi* a metà ferma l'import e il rapporto dice quante righe sono state inserite |
| T9.6 | M | procedura guidata completa su `soci.csv` esportato da Excel italiano (`;`, Windows-1252, date italiane, accenti) | anteprima corretta al passo 2; mappatura automatica per nome; `CREATE TABLE` in anteprima se nuova tabella; nel registro l'`INSERT` preparato + numero di lotti |
| T9.7 | M | file con 5 righe errate su 100 (duplicato, data impossibile, testo in colonna numerica, NOT NULL vuoto, FK inesistente) | 95 importate, 5 elencate nel rapporto con riga e motivo in italiano |
| T9.8 | M | opzioni: *svuota prima*; *ignora duplicati* | comportamento dichiarato; *svuota prima* passa dalla conferma rafforzata |
| T9.9 | **N** | Navicat: aprire le tabelle importate in T9.6 | numero di righe, accenti, date, decimali e NULL corretti; tipi delle colonne della nuova tabella sensati |

## Step 10 — Dump e ripristino
**Task:** selezione ad albero · struttura/dati/entrambi per oggetto · ordinamento per dipendenze · scrittura in streaming · esecutore di script con avanzamento.

| ID | Tipo | Procedura | Risultato atteso |
|---|---|---|---|
| T10.1 | U | scrittura dei valori: NULL, apostrofi, backslash, a-capo, emoji, BLOB (esadecimale), DECIMAL, date zero, BIT, stringa vuota | letterali SQL corretti |
| T10.2 | U | ordinamento: tabelle prima delle viste; viste che dipendono da viste; FK circolari (→ `SET FOREIGN_KEY_CHECKS=0`) | ordine atteso |
| T10.3 | I | **round-trip totale** sui due server: dump di `biblioteca` → ripristino in catalogo nuovo | stessi oggetti; metadati identici (colonne, indici, **FK con le stesse azioni**, engine, charset); `CHECKSUM TABLE` identico per ogni tabella; viste funzionanti |
| T10.4 | I | **dump selettivo**: solo struttura di `autori`, solo dati di `editori`, entrambi di `libri` | il file contiene esattamente: 1 `CREATE` per `autori` senza INSERT, soli INSERT per `editori`, entrambi per `libri`; nient'altro |
| T10.5 | I | casi difficili: `biblioteca_myisam`, tabella con nome con spazi, BLOB da 5 MB, tabella `grande` | round-trip riuscito; memoria stabile |
| T10.6 | I | dump da MariaDB → ripristino su MySQL, e viceversa, per `biblioteca` | riuscito; eventuali differenze (collation) documentate e gestite |
| T10.7 | M | procedura guidata: tutti i cataloghi dell'utente (dump **totale**) con `DROP IF EXISTS` e `CREATE DATABASE` | file unico; ripristinato su server vuoto ricrea tutto |
| T10.8 | M | ripristinare con il client un dump prodotto da `mysqldump` e uno prodotto da **Navicat** (*Dump SQL File*) | eseguiti senza errori, con avanzamento |
| T10.9 | M | script con un errore a metà: opzione «fermati» e opzione «continua» | comportamento dichiarato; rapporto con istruzione e riga dell'errore |
| T10.10 | **N** | Navicat → *Execute SQL File* con il dump prodotto dal client (T10.3 e T10.4) su un catalogo vuoto | eseguito senza errori; struttura (Fields, Indexes, Foreign Keys) e dati identici all'originale; *Structure Synchronization*/*Data Synchronization* di Navicat tra originale e ripristinato: **nessuna differenza** |

## Step 11 — Modello ER e retroingegneria
**Task:** modulo `model` · formato `.rsqlmodel` · retroingegneria · canvas Java2D · relazioni logiche a mano · suggeritore per nome · disposizione automatica · aggiorna dal database · apertura editor tabella dall'entità · esporta PNG.

| ID | Tipo | Procedura | Risultato atteso |
|---|---|---|---|
| T11.1 | U | serializzazione `.rsqlmodel`: salva → carica | modello identico; `formatVersion` presente; file di versione futura → errore chiaro |
| T11.2 | U | suggeritore: `id_editore`→`editori.id`, `editore_id`, `editoreId`, `id_libro`/`id_autore` in tabella ponte, singolare/plurale (`socio`/`soci`, `libro`/`libri`, `author`/`authors`), tipi incompatibili, falsi amici (`id_esterno` senza tabella) | proposte attese con punteggio; nessuna proposta per i falsi amici |
| T11.3 | U | cardinalità da metadati: FK su colonna UNIQUE → 1:1; FK NOT NULL → obbligatoria; tabella ponte → N:M indicata | attese |
| T11.4 | I | retroingegneria di `biblioteca` sui due server | 6 entità, 5 relazioni **fisiche** corrette |
| T11.5 | I | retroingegneria di **`biblioteca_myisam`** (nessuna FK) + suggeritore | 0 relazioni fisiche; **5/5 relazioni attese proposte**, 0 proposte errate |
| T11.6 | M | su `biblioteca_myisam`: accettare i suggerimenti, disegnarne una a mano, salvare | relazioni logiche tratteggiate; registro SQL: **zero istruzioni** (il database non è stato toccato) |
| T11.7 | M | chiudere, disconnettersi, riaprire il `.rsqlmodel` **senza connessione** | diagramma identico, posizioni comprese |
| T11.8 | M | sul server: aggiungere una colonna a `soci`, eliminare `prestiti` → *Aggiorna dal database* | colonna nuova presente; `prestiti` segnata come mancante; posizioni conservate; relazioni logiche conservate |
| T11.9 | M | disposizione automatica su un catalogo di 30 tabelle; zoom; trascinamento | entità non sovrapposte, tabelle più riferite al centro; interazione fluida (anche con 100 entità generate) |
| T11.10 | M | doppio clic su un'entità | si apre l'editor della tabella |
| T11.11 | M | esporta PNG e proiettarlo / stamparlo in A4 | leggibile; zampe di gallina e tratteggio distinguibili |
| T11.12 | **N** | Navicat → ER Diagram / *Reverse Database to Model* su `biblioteca` e su `biblioteca_myisam` | su `biblioteca` le relazioni di Navicat = relazioni **fisiche** del client; su `biblioteca_myisam` Navicat non ne mostra nessuna mentre il client mostra quelle logiche → è la conferma del requisito 8 («indipendente dalle chiavi esterne») |

## Step 12 — Rifiniture per l'aula e guida rapida
**Task:** spiegazioni in italiano degli errori frequenti · dimensione carattere · guida rapida · «Informazioni su» con licenze · revisione «alla Apple» di ogni schermata (cosa si può togliere?) · giro sui difetti in `BUGS.md`.

| ID | Tipo | Procedura | Risultato atteso |
|---|---|---|---|
| T12.1 | U | tabella errori: 1044, 1045, 1049, 1054, 1062, 1064, 1146, 1215/1005, 1451, 1452, 2003 | ciascuno ha una spiegazione in italiano |
| T12.2 | M | provocare ciascun errore di T12.1 dall'applicazione | messaggio originale + spiegazione comprensibile a uno studente |
| T12.3 | U | ricerca di stringhe d'interfaccia nel codice fuori dai file di risorse | zero |
| T12.4 | M | esercitazione T6.11 eseguita **solo da tastiera** | possibile |
| T12.5 | M | monitor di rete (Resource Monitor) durante una sessione d'uso completa | connessioni solo verso il server di database |
| T12.6 | M | carattere al massimo su proiettore 1024×768 e su portatile 1366×768 | nessuna finestra di dialogo esce dallo schermo |
| T12.7 | M | revisione «alla Apple»: per ogni schermata, elenco dei controlli visibili e domanda «serve a uno degli 9 requisiti?» | ciò che non serve è rimosso; barra strumenti ≤ 10 pulsanti; impostazioni = 4 voci |
| T12.8 | M | «Informazioni su» | GPL-3, attribuzioni SQLeonardo/SQLeo, elenco librerie e licenze, dove trovare i sorgenti |

## Step 13 — Installer e distribuzione
**Quando:** solo dopo una versione stabile (indicazione dell'utente). **Richiede:** D-05 (firma), D-07 (aggiornamenti), un PC dell'aula o una macchina virtuale Windows pulita. Include lo spike d'installazione prima previsto nello Step 1 (jlink → jpackage → Inno Setup, utente senza admin, ZIP da chiavetta).
**Task:** `packaging/build-installer.ps1` definitivo · `jdeps`/`jlink` · icone e metadati dell'exe · script Inno Setup (it, per-utente/tutti, associazioni, file di connessioni facoltativo, disinstallazione, aggiornamento sopra versione precedente) · ZIP portabile · pacchetto sorgenti (obbligo GPL) · eventuale firma.

Tutti i test su **macchine virtuali Windows 10 e Windows 11 pulite, senza Java**.

| ID | Tipo | Procedura | Risultato atteso |
|---|---|---|---|
| T13.1 | M | `build-installer.ps1` da clone pulito | produce `Setup.exe`, ZIP portabile, archivio sorgenti; versione coerente in exe, installer, «Informazioni su» |
| T13.2 | M | installare come **utente standard** | nessuna richiesta di elevazione; voce nel menu Start; avvio; connessione a un server; esecuzione di T6.11 ridotto |
| T13.3 | M | installare da amministratore «per tutti gli utenti» | disponibile per un secondo utente |
| T13.4 | M | installare la versione N+1 sopra la N | profili e impostazioni conservati |
| T13.5 | M | disinstallare | nessun residuo in cartella programmi; dati utente conservati o rimossi secondo la scelta |
| T13.6 | M | `Setup.exe /VERYSILENT` con file di connessioni | installato senza interazione; tessere delle connessioni presenti al primo avvio |
| T13.7 | M | ZIP portabile da chiavetta su PC senza diritti | si avvia e funziona; non scrive fuori dalla propria cartella e da `%APPDATA%` |
| T13.8 | M | utente Windows `Niccolò Rossi` (spazio e accento nel percorso) | installazione e avvio regolari |
| T13.9 | M | misure | installer < 80 MB; installato < 150 MB; avvio < 3 s su PC da laboratorio |
| T13.10 | M | antivirus del laboratorio + SmartScreen | comportamento annotato; se firmato: nessun avviso |
| T13.11 | M | contenuto del rilascio | `Setup.exe`, ZIP, sorgenti, `LICENSE`, note di versione |

## Step 14 — Collaudo in aula e rilascio 1.0
**Task:** lezione pilota con una classe · raccolta difetti e attriti · correzioni · guida del docente (installazione in laboratorio, file di connessioni, esercitazioni d'esempio) · rilascio 1.0.

| ID | Tipo | Procedura | Risultato atteso |
|---|---|---|---|
| T14.1 | M | installazione su tutti i PC del laboratorio seguendo la guida scritta | riuscita su tutti |
| T14.2 | M | lezione reale: gli studenti svolgono T6.11 + una query visiva + una vista | completata senza interventi tecnici del docente sul client; attriti annotati in `BUGS.md` |
| T14.3 | **N** | Navicat: il docente apre 3 database a campione creati dagli studenti | struttura, indici, FK e dati coerenti con quanto gli studenti vedono nel client |
| T14.4 | U+I | `mvnw verify` sulla build di rilascio | tutto verde sui due server |
| T14.5 | M | **lista di regressione**: riesecuzione dei test M contrassegnati come critici (T3.5, T4.9–T4.11, T4.16–T4.17, T5.4, T6.6–T6.7, T7.4–T7.5, T8.4, T9.6, T10.7, T11.6, T13.2) sulla build installata | tutti superati |
| T14.6 | M | difetti bloccanti aperti | zero |

## Cosa serve dall'utente e quando

| Quando | Cosa |
|---|---|
| Prima dello Step 0 | D-01 nome · D-02 conferma GPL-3 · D-04 repository |
| Prima dello Step 1 | ✅ D-03 chiusa (server locali) |
| Step 3 | D-06 routine/trigger in sola lettura |
| **A ogni stop di revisione (Step 3–11, 14)** | **controlli incrociati con Navicat** (test di tipo N) ed esito |
| Step 7 | elenco di query/esercizi tipici del corso, per il campionario del parser |
| Step 13 | D-05 firma del codice · D-07 aggiornamenti · un PC dell'aula o una VM Windows pulita |
| Step 14 | una classe e un'ora di laboratorio |
