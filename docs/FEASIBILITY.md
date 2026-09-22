# FEASIBILITY.md — Fattibilità punto per punto

Ogni requisito della richiesta originale (2026-09-21) con: **esito**, soluzione, limiti/condizioni, alternative, step in cui si realizza o si verifica.
Legenda: ✅ fattibile pienamente · 🟡 fattibile con condizioni o limiti · 🔴 non garantibile così come formulato.
Ragionamento esteso e rischi (R-xx) in `ANALYSIS.md`; decisioni aperte (D-xx) in `ANALYSIS.md` §8.

## Riepilogo

| ID | Requisito | Esito |
|---|---|---|
| F-00 | Basarsi su SQLeo per ereditare il query editor visivo | ✅ (con obbligo GPL) |
| F-01 | Gestire connessioni MariaDB e MySQL | ✅ |
| F-02 | Creare/modificare tabelle | ✅ |
| F-03 | Indici, chiavi esterne, integrità referenziale | ✅ |
| F-04 | Formati tabella InnoDB e MyISAM | ✅ (FK non disponibili su MyISAM: limite del server) |
| F-05 | Query editor visivo + editor raw, SQL sempre mostrato | ✅ |
| F-05bis | Query nidificate (sottoquery, tabelle derivate, CTE) in query e viste | ✅ sottoquery e tabelle derivate (provate in S2d sui due server) · 🟡 CTE solo come testo |
| F-06 | Creazione viste con l'editor grafico | ✅ creazione · 🟡 riapertura grafica di viste esistenti |
| F-07a | Importazione CSV | ✅ |
| F-07b | Importazione JSON | ✅ (strutture annidate: 🟡) |
| F-07c | Dump selettivo e totale, dati e struttura | ✅ |
| F-08 | Modello logico / ER indipendente dalle FK, con retroingegneria, non vincolante | ✅ |
| F-13 | Apertura tabelle in modalità data-entry, appunti su blocchi rettangolari, conferma esplicita dell'inserimento | ✅ |
| F-09 | Applicazione Windows con installer | ✅ (avvisi SmartScreen senza firma: 🟡) |
| F-10 | Client il più semplice possibile, per l'aula | ✅ |
| F-11 | Workbench come riferimento funzionale | ✅ (solo riferimento, nessun codice) |
| F-12 | Organizzazione MD come `gestionaleFormazione` | ✅ fatto |

**Perimetro:** la v1 è un progetto **minimale/medio**, «alla Apple», guidato dalla lista dei requisiti dell'utente. Ciò che in questo documento va oltre la colonna «v1» di `DESIGN.md` §1-bis è fattibile ma **rinviato** ([dopo]) o **parcheggiato** ([forse dopo]: la gestione delle transazioni). Dove qui sotto si parla di esecuzione «in transazione» o di «rollback», in v1 si intende esecuzione in autocommit con arresto al primo errore. Workbench è il modello di *come* fare, non l'elenco di *cosa* fare. **Validazione:** test automatici e manuali per step in `ROADMAP.md`, più il controllo incrociato dell'utente con **Navicat**.

**Nessun requisito è irrealizzabile.** Tre hanno condizioni (F-06 riapertura, F-07b annidati, F-09 firma). La fattibilità di F-00 — da cui dipende tutto — è alta sulla carta ma va **dimostrata nello Step 1** prima di investire nel resto.

---

## F-00 — Ereditare il query editor visivo di SQLeo ✅
**Verificato:** sorgenti Java Swing disponibili, licenza GPL-2-or-later, query builder di ≈11.600 righe con ≈25 punti di contatto verso il resto di SQLeo, nessuna libreria esterna, backtick MySQL già gestiti.
**Soluzione:** estrazione del pacchetto `com.sqleo.querybuilder` (+ le poche classi `common` necessarie) nel modulo Maven `sqleo-qb`; sostituzione dei riferimenti a `Application`/`Preferences`/MDI con una facciata; rimozione del limite «3 tabelle» e di ogni riferimento a versione completa/donazioni.
**Condizioni:** il prodotto intero diventa GPL-3.0-or-later con sorgenti disponibili (D-02).
**Limiti:** codice datato con stato `static`: un solo query builder attivo per volta finché non bonificato (R-06).
**Verifica:** Step 1, spike S1, S2, S5.

## F-01 — Connessioni MariaDB e MySQL ✅
**Soluzione:** profili di connessione (nome, tipo server rilevato automaticamente, host, porta, utente, catalogo predefinito, SSL sì/no, colore); prova connessione; import/export dei profili in JSON per la distribuzione in aula. Driver JDBC incluso nel prodotto: lo studente non configura nulla.
**Limiti in v1:** niente tunnel SSH, niente socket/named pipe (in aula non servono; annotati come evoluzione).
**Condizione:** esito di S4 sul driver unico (R-04); password secondo D-08.
**Step:** 2.

## F-02 — Creare/modificare tabelle ✅
**Soluzione:** editor a schede sul modello di Workbench — *Colonne* (nome, tipo, lunghezza, NULL, default, AUTO_INCREMENT, UNSIGNED, commento, PK), *Indici*, *Chiavi esterne*, *Opzioni* (engine, charset/collation, commento), *SQL* (anteprima viva). Alla conferma un **generatore a differenze** confronta il modello originale con quello modificato ed emette `CREATE TABLE` oppure il minimo `ALTER TABLE`.
**Limiti in v1:** partizioni, colonne generate, `CHECK` e tipi spaziali si conservano se presenti (il generatore non li tocca) ma non si editano graficamente.
**Rischio:** R-07 → test di andata/ritorno su entrambi i server.
**Step:** 5.

## F-03 — Indici, chiavi esterne, integrità referenziale ✅
**Pienamente in v1 e verificati** (ribadito dall'utente). **Verifica dopo l'applicazione:** eseguito l'`ALTER`, il client rilegge `information_schema` (`STATISTICS`, `KEY_COLUMN_USAGE`, `REFERENTIAL_CONSTRAINTS`) e conferma che indice/FK esistono come richiesti («✔ verificato sul server») o elenca le differenze. Validazione esterna: schede *Indexes* e *Foreign Keys* di Navicat (test T6.12–T6.14).
**Soluzione:** indici `PRIMARY`, `UNIQUE`, `INDEX` (FULLTEXT e prefissi: [dopo]), multi-colonna con ordine; FK con tabella/colonne riferite, `ON DELETE`/`ON UPDATE` (`RESTRICT`, `CASCADE`, `SET NULL`, `NO ACTION`). **Controlli preventivi didattici** prima di generare l'SQL: tipi compatibili tra colonna e riferimento, indice presente sulla colonna riferita, `SET NULL` su colonna `NOT NULL`, engine InnoDB su entrambi i lati, e — a richiesta — ricerca delle righe orfane che farebbero fallire l'`ALTER` (con la query mostrata).
**Step:** 6.

## F-04 — InnoDB e MyISAM ✅
**Soluzione:** scelta dell'engine alla creazione e conversione (`ALTER TABLE … ENGINE=`), engine visibile nell'albero e nel modello ER.
**Limite (del server, non del client):** MyISAM non supporta FK né transazioni. La scheda *Chiavi esterne* su tabella MyISAM è disabilitata con spiegazione; la conversione InnoDB→MyISAM di una tabella con FK viene bloccata con messaggio chiaro. Ottimo spunto didattico, da documentare nella guida.
**Step:** 5–6.

## F-05 — Query editor visivo + editor raw + SQL sempre mostrato ✅
**Soluzione:** scheda «Query» con due viste sincronizzate — **Grafica** (SQLeo: tabelle trascinate, join disegnati, colonne, condizioni, raggruppamenti, ordinamenti) e **SQL** (RSyntaxTextArea con evidenziazione e completamento). Grafica→SQL è sempre possibile; SQL→Grafica passa da `SQLParser` e, quando la query non è rappresentabile, la vista grafica si disattiva con avviso senza perdere il testo. Risultati in griglia sotto.
«SQL mostrato sempre per ogni operazione» è una regola di architettura, non di una singola schermata: **pipeline anteprima SQL** + **registro SQL** (`ARCHITECTURE.md` §4).
**Limiti:** R-03 (costrutti non coperti dal parser).
**Step:** 4 (editor raw, pipeline) e 7 (visivo).

## F-05bis — Query nidificate in query e viste ✅ sottoquery e tabelle derivate · 🟡 CTE solo testo (esito dello spike S2d)
**Domanda dell'utente (2026-09-21):** il client supporta le query nidificate nelle query e nelle viste?
**Cosa dice il codice di SQLeo** (letto il 2026-09-21, **non ancora eseguito**): il modello sintattico ha classi dedicate — `SubQuery` (sottoquery nelle condizioni: `IN (…)`, confronti, `EXISTS`, e nella lista `SELECT`) e `DerivedTable` (sottoquery nel `FROM` con alias); il parser (`SQLParser`) riconosce anche le **CTE `WITH`**, che tratta come tabelle derivate con nome. Nell'interfaccia ogni sottoquery compare come **nodo proprio nell'albero della query** (`ViewBrowser`, voci «SUBQUERY»), che si apre e si modifica come una query a sé; le tabelle derivate compaiono nel diagramma come entità con il loro alias.
**Viste:** una vista è una query con un nome, quindi vale lo stesso supporto. Una vista che usa un'altra vista non richiede nulla di speciale: nel diagramma la vista di base è un'entità come una tabella.
**Limiti attesi:** funzioni finestra (`OVER`), `UNION` complesse e sottoquery correlate molto articolate potrebbero non essere rappresentabili graficamente → si modificano come testo, senza perdita (regola R-03). Le CTE ricorsive (`WITH RECURSIVE`) quasi certamente no.
**Correzione a un'affermazione precedente:** in `ANALYSIS.md` R-03 e in `ROADMAP.md` T7.7 le CTE erano indicate come «non rappresentabili»; il codice dice il contrario. Il test T7.7 ora usa una funzione finestra; le CTE sono verificate in S2d.
**Esito dello spike S2d (2026-09-21, `docs/SPIKE-STEP1.md`):** eseguito su MariaDB 11.5 e MySQL 8.0. Rappresentabili graficamente, con SQL rigenerato che dà le stesse righe dell'originale su entrambi i server: sottoquery in `WHERE` (`IN`, `NOT IN`, `EXISTS` correlata, confronto con `(SELECT MAX/AVG…)`), sottoquery nella lista `SELECT`, tabella derivata in `FROM`, due livelli di annidamento, vista su vista, vista con sottoquery. **Le CTE `WITH` NO**: il parser le trasforma in tabelle derivate perdendo il testo `WITH` (e, se riferite con alias, produce SQL non eseguibile) → `QbSql.check` le dichiara «non rappresentabili» e si modificano come testo, senza perdita. Si corregge quindi di nuovo l'affermazione qui sopra: le CTE sono riconosciute dal parser ma **non** sono utilizzabili graficamente in v1. Non rappresentabili anche: `ORDER BY`/`LIMIT` dentro una sottoquery, `JOIN … USING`, `CROSS JOIN`, condizioni aggiuntive nella `ON` (`ON a=b AND cond`), `UNION ALL`, commenti `--` nel testo. Regola: non si apre mai graficamente ciò che `QbSql.check` rifiuta.
**Verifica:** spike S2d (Step 1), test T7.7b (Step 7) e T8.7b (Step 8), su MariaDB e MySQL.

## F-06 — Viste con l'editor grafico ✅ / 🟡
**Creazione ✅:** lo stesso query builder in «modalità vista»: nome vista + opzioni → `CREATE OR REPLACE VIEW … AS <query>` in anteprima.
**Riapertura grafica di viste esistenti 🟡:** il server restituisce una definizione riscritta (R-02). Strategia a tre livelli: (1) sorgente originale conservato nel progetto locale, se la vista è nata in questo client; (2) normalizzatore + `SQLParser` sulla definizione del server; (3) ripiego nell'editor SQL. Non si garantisce il livello 2 per ogni vista scritta altrove.
**Step:** 8; verifica anticipata in S2.

## F-07a — Importazione CSV ✅
**Soluzione:** procedura guidata: file → rilevamento di separatore, codifica, intestazione → anteprima → destinazione (tabella esistente con mappatura colonne, oppure **nuova tabella** con tipi dedotti e `CREATE TABLE` in anteprima) → opzioni (svuota prima, ignora duplicati, valori NULL) → esecuzione a lotti in transazione con rapporto errori per riga.
**Nota:** si usano `INSERT` parametrici via JDBC, non `LOAD DATA LOCAL INFILE` (spesso disabilitato sui server e meno «mostrabile»). Nel registro compare l'istruzione preparata e il conteggio dei lotti, non milioni di INSERT.
**Step:** 9.

## F-07b — Importazione JSON ✅ / 🟡
**Soluzione:** supportati **array di oggetti piatti** e **JSON Lines**; lettura in streaming (file grandi). Stessa procedura del CSV.
**Limite 🟡:** oggetti/array annidati non vengono normalizzati su più tabelle: si importano come testo JSON in una colonna `JSON`/`LONGTEXT`, oppure si appiattiscono con notazione `a.b` a scelta.
**Step:** 9.

## F-07c — Dump selettivo e totale ✅
**Soluzione:** procedura guidata con albero a caselle (catalogo → tabelle, viste; routine/trigger in sola copia del `SHOW CREATE`), scelta **struttura / dati / entrambi** per oggetto, opzioni (`DROP IF EXISTS`, `CREATE DATABASE`, INSERT estesi, un file unico). Generatore proprio via JDBC, identico su MySQL e MariaDB, indipendente da `mysqldump`. **Ripristino:** esecuzione di uno script `.sql` con avanzamento e arresto al primo errore (o continua), utilizzabile anche con dump prodotti da `mysqldump`/Workbench.
**Export dati** in CSV/JSON dalla griglia dei risultati (complemento naturale dell'import).
**Rischio:** R-08 → test di round-trip.
**Step:** 10.

## F-13 — Data-entry, appunti a blocchi, conferma esplicita ✅
**Requisito (aggiunto il 2026-09-21):** aprire le tabelle in modalità data-entry; appunti su sotto-intervalli rettangolari di celle; l'inserimento si conferma con un pulsante esplicito; nessuna gestione di transazioni per ora.
**Soluzione:** `JTable` con modello a **modifiche pendenti** (riga inserita / modificata / eliminata) e selezione a celle (`setCellSelectionEnabled`): Swing gestisce nativamente gli intervalli rettangolari. Copia = testo tabulato negli appunti (il formato che Excel, Calc e Fogli leggono e scrivono); incolla = analisi del testo tabulato, stesura dalla cella attiva, creazione di righe nuove oltre la fine, validazione per tipo di colonna. Nessuna scrittura finché non si preme **Conferma**: il generatore produce `INSERT/UPDATE/DELETE` che passano dall'anteprima SQL e si eseguono una alla volta in autocommit; **Scarta** butta le modifiche pendenti (operazione locale). Scheda record come vista alternativa sullo stesso modello.
**Limiti:** serve PK o UNIQUE per `UPDATE`/`DELETE` (senza: sola lettura); senza transazioni, un errore a metà lascia scritte le righe precedenti — il client le marca come salvate e tiene pendenti le altre, quindi lo stato è sempre chiaro; incolla di blocchi molto grandi (> 10.000 righe) → si consiglia l'import CSV.
**Rischio:** modifica concorrente della stessa riga da un altro studente → si controlla il numero di righe interessate (0 = riga sparita → errore).
**Step:** 4 (prova anticipata degli appunti in S7).

## F-08 — Modello logico / ER, retroingegneria, non vincolante ✅
**Interpretazione del requisito:** il diagramma mostra le relazioni *logiche* tra le tabelle anche quando nel database **non esistono FK** (tipico: tabelle MyISAM, o database «da aula» senza vincoli). Il modello non impone nulla al database e il database non impone nulla al modello.
**Soluzione:**
- **Retroingegneria:** dal catalogo si leggono tabelle, colonne, PK, indici e FK reali → entità e relazioni «fisiche» (linea continua).
- **Relazioni logiche:** lo studente ne disegna altre a mano (linea tratteggiata), con cardinalità; in più il client **suggerisce** relazioni per convenzione di nomi e tipi (`cliente_id` → `cliente.id`, `id_cliente`, colonna omonima alla PK di un'altra tabella), da accettare o scartare.
- **Persistenza:** il modello (posizioni, relazioni logiche, note, colori) è un file locale `.rsqlmodel` (JSON), separato dal database. «Aggiorna dal database» riallinea le entità senza perdere l'impaginazione.
- **Non vincolante, ma utile:** su una relazione logica, comando facoltativo «Crea la chiave esterna…» che apre l'editor FK precompilato (con anteprima SQL). Nessuna sincronizzazione automatica modello→database in v1.
- Disposizione automatica, zoom, esportazione PNG/SVG/PDF per le relazioni di laboratorio.
**Fuori perimetro v1:** progettazione diretta (forward engineering) di un intero schema dal diagramma.
**Step:** 11; canvas validato in S6.

## F-09 — Applicazione Windows con installer ✅ / 🟡
**Quando:** solo alla fine, dopo una versione stabile (indicazione dell'utente); fino ad allora si usa `avvia.cmd`.
**Soluzione:** `jlink` produce un runtime ridotto; `jpackage --type app-image` crea l'applicazione con `RamaSQL.exe`; **Inno Setup** la impacchetta in un `Setup.exe` in italiano, installazione **per-utente senza diritti di amministratore** (con opzione «per tutti gli utenti» se lanciato da admin), collegamenti, associazione `.rsqlmodel`/`.sql` facoltativa, disinstallazione pulita. In parallelo **ZIP portabile**. Dimensione attesa 45–60 MB. Requisito: Windows 10/11 x64.
**Condizione 🟡:** senza firma del codice SmartScreen mostra «app non riconosciuta» al primo avvio dell'installer (D-05, R-05). Non blocca, ma in aula va spiegato o risolto con la firma / distribuzione tramite il tecnico.
**Step:** prova anticipata in S3; produzione nello Step 13.

## F-10 — Semplicità per l'aula ✅
Vedi `ANALYSIS.md` §5 e `DESIGN.md` §2: finestra unica, tre zone, perimetro ridotto, reti di protezione, profili di connessione distribuibili, messaggi in italiano, nessuna telemetria. Collaudo con studenti reali nello Step 14.

## F-11 — Workbench come riferimento ✅
Usato per decidere *cosa* offrire (schede dell'editor tabelle, procedure guidate di import/export, reverse engineering EER) e *cosa togliere*. Nessun codice di Workbench viene copiato (è C++/GTK/.NET: non riusabile; e la coerenza di licenza resta semplice).

## F-12 — Organizzazione MD ✅
`CLAUDE.md` in root come indice e regole; `docs/` con JOURNAL, BUGS, ROADMAP, DECISIONS, ANALYSIS, FEASIBILITY, DESIGN, ARCHITECTURE, GLOSSARY, CONSOLE — stessa impostazione di `gestionaleFormazione`.
