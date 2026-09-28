# ARCHITECTURE.md — Architettura

Versione 0.2 — 2026-09-21. Da leggere prima di scrivere codice, per sapere dove va collocato. Le motivazioni sono in `ANALYSIS.md` e `DECISIONS.md`.

## 1. Vista d'insieme

```
┌──────────────────────────── app (Swing + FlatLaf) ────────────────────────────┐
│  Shell · Navigatore · Schede (Editor SQL, Griglia, Editor tabelle,             │
│  Query visiva, Vista, Import, Dump, Modello ER) · Pannello SQL                 │
└───────┬───────────────────────────┬───────────────────────────┬───────────────┘
        │                           │                           │
        ▼                           ▼                           ▼
┌──────────────┐          ┌───────────────────┐        ┌────────────────┐
│    core      │◄─────────│  sqleo-qb (GPL,   │        │     model      │
│ connessioni  │ facciata │  derivato SQLeo)  │        │ modello ER,    │
│ metadati     │  QbHost  │ diagramma, parser │        │ .rsqlmodel     │
│ generatore   │          │ SQL inverso       │        │ suggerimenti   │
│ DDL/DML      │          └───────────────────┘        └────────────────┘
│ pipeline SQL │
│ import/dump  │──► JDBC ──► MariaDB / MySQL
└──────────────┘
```

Regola delle dipendenze: `app` → `core`, `model`, `sqleo-qb` · `model` → `core` · `sqleo-qb` → (solo JDK + interfaccia `QbHost`) · `core` → (JDK, driver, Jackson). **`core` e `model` non dipendono da Swing**: sono collaudabili senza interfaccia.

## 2. Struttura del repository

```
ramaSQLClient/
├─ CLAUDE.md · LICENSE (GPL-3.0) · NOTICE (attribuzioni) · README.md
├─ pom.xml                      progetto padre Maven; mvnw / mvnw.cmd
├─ docs/
├─ core/                        it.ramasql.core.*
├─ model/                       it.ramasql.model.*
├─ sqleo-qb/                    com.sqleo.querybuilder.* (+ classi common minime)
│   └─ UPSTREAM.md              versione d'origine, elenco dei file modificati e perché
├─ app/                         it.ramasql.app.*  (main, UI, risorse, i18n, icone SVG)
├─ packaging/
│   ├─ build-installer.ps1      jlink → jpackage app-image → Inno Setup → ZIP
│   ├─ ramasql.iss              script Inno Setup
│   └─ aula.sample.json
├─ it-tests/                    test d'integrazione contro server reali
└─ spikes/                      prove dello Step 1 (non entrano nel prodotto)
```

## 3. Modulo `core`

| Pacchetto | Responsabilità |
|---|---|
| `core.connection` | **(Step 2)** `ConnectionProfile` (senza password), `ProfileStore` (JSON in `%APPDATA%\RamaSQL\connessioni.json`, import/export con `formatVersion`), `AppData` (cartella dati; proprietà `ramasql.appdata` per i test), `AppSettings` (4 voci), `JsonFiles` (scrittura atomica), `ConnectionErrorClassifier` → `ConnectionFailure` (causa + messaggio italiano + originale del server), `Session` sempre in **autocommit** (principale + di servizio, `ServerInfo`, catalogo letto all'apertura), `ConnectionAttempt` (asincrono, annullabile, scadenza 10 s), `InternalQueries` (unico punto con SQL interno: `VERSION()`, `CONNECTION_ID()`, `DATABASE()`, `KILL QUERY`). Parametri JDBC: `ADR-016`. **(Step 8)** `ViewSourceStore`: archivio dei sorgenti originali delle viste (`viste.json`, livello 1 della riapertura) |
| `core.metadata` | **(Step 3)** `MetadataReader` (canale interno `MetadataQueries`): cataloghi (`CatalogInfo`, flag di sistema), elenco tabelle senza colonne (`TableSummary`, veloce), `TableDef` completo a richiesta, viste, routine/trigger/eventi in sola lettura (`RoutineInfo`, `ADR-017`), `showCreate*`, collation. `MetadataNormalizer` rende **identici** i `TableDef` letti da MariaDB e MySQL (larghezza degli interi, default tra apici, `NULL` testuale, `current_timestamp()`, JSON). Cache per sessione con `invalidate*` e `MetadataListener`. **(Step 8)** `ViewDefinitionNormalizer`: ripulisce le definizioni di vista rilette dal server (livello 2) senza cambiarne il significato |
| `core.sqlgen` | **Generatori SQL puri** (Step 3: `ObjectDdl`, `TreeScripts` per le operazioni del navigatore) (nessun accesso al DB): `TableDiff(original, edited) → List<SqlStatement>`, `CREATE/ALTER/DROP`, indici, FK, viste, DML della griglia, quoting degli identificatori, differenze MariaDB/MySQL guidate da `ServerInfo`. È il cuore collaudabile a tappeto. **(Step 8)** `ViewDdl`: `CREATE [OR REPLACE] VIEW` (preceduto da `USE` del catalogo), `DROP VIEW` |
| `core.exec` | **(Step 3)** Pipeline (§4): `SqlScript`, `SqlOrigin`, `SqlExecutor` (unico esecutore dell'utente: thread dedicato, una istruzione alla volta, arresto al primo errore, `KILL QUERY` dalla connessione di servizio, limite righe, invalidazione dei metadati dopo DDL, **mai transazioni**), `ScriptResult`/`StatementResult`/`ResultTable`, `SqlLog` (registro con origine/esito/durata/righe; esportazione `.sql` rieseguibile con le istruzioni fallite commentate), `ConfirmationPolicy` (conferma rafforzata con nome da riscrivere), `RiskClassifier`, `StatementSplitter` |
| `core.data` | Lettura paginata/streaming dei risultati; **modello a modifiche pendenti** del data-entry (`RowChange` inserita/modificata/eliminata → DML, stato salvata/pendente/in errore); codifica e decodifica degli **appunti a blocchi** (testo tabulato, convenzione Excel) e validazione per tipo — tutto senza Swing, quindi collaudabile |
| `core.verify` | **Verifica dopo l'applicazione**: confronta indici e FK richiesti con quelli riletti dal server e produce l'elenco delle differenze |
| `core.importer` | **(Step 9, `ADR-025`)** `ImportFile` (file, CSV o JSON, formato; `detect` legge i primi 64 kB), `EncodingDetector`, `CsvSniffer`, `CsvParser` (streaming), `JsonArrayReader` (Jackson in streaming), `ImportSource`/`SourceRow`, `FileAnalyzer` (lettura completa in sottofondo: colonne, anteprima, `ColumnProfile` → `TypeInference`), `ValueParsing` (virgola decimale, date), `ValueConverter` (valori del file → testo per la colonna, scarti con motivo), `ImportPlanner` (abbinamento per nome, tabella nuova), `ImportPlan` (SQL per l'anteprima: `TRUNCATE`/`CREATE TABLE` + `INSERT` preparata), `ImportRows` (i lotti per l'esecutore), `ImportReport`. L'esecuzione è `SqlExecutor.submitBatchInsert` (`BatchInsert`, `BatchSource`, `BatchResult`) |
| `core.dump` | Selezione oggetti, ordinamento per dipendenze, scrittura `.sql` in streaming, esecutore di script per il ripristino |
| `core.policy` | Lettura di `aula.json` (modalità aula) |

## 4. Pipeline «anteprima SQL» (regola 3 di `CLAUDE.md`)

Tutto ciò che modifica il database segue **un solo percorso**:

```
azione UI ──► core.sqlgen ──► SqlScript (istruzioni + origine + pericolosità)
                                   │
                     ┌─────────────▼─────────────┐
                     │ PreviewDialog (app)       │  Applica / Copia nell'editor / Annulla
                     └─────────────┬─────────────┘
                                   ▼
                          SqlExecutor.run(script)
                     esegue 1 istruzione alla volta, fuori dall'EDT
                                   │
                 ┌─────────────────┼──────────────────┐
                 ▼                 ▼                  ▼
              SqlLog        eventi di esito     invalidazione metadati
           (Pannello SQL)   (UI: avanzamento)     (Navigatore si aggiorna)
```

- `SqlExecutor` è l'**unico** punto del prodotto che chiama `Statement.execute` per conto dell'utente. Le letture di metadati usano un canale interno separato, registrato come «interno» (visibile a richiesta).
- Ogni `SqlStatement` porta: testo, origine, classe di rischio (`SAFE`, `MODIFIES`, `DESTRUCTIVE`). **Import a lotti** (Step 9, `ADR-025`): lo script mostrato termina con l'`INSERT` preparata (`BatchInsert.statement()`), che `SqlExecutor.submitBatchInsert` esegue a lotti con le righe di un `BatchSource`; nel registro una riga con l'istruzione preparata e la nota dei lotti e delle righe.
- Anche l'editor SQL «raw» esegue tramite `SqlExecutor` (niente anteprima, ma registro e controlli `DESTRUCTIVE`/senza-WHERE sì).
- **Nessuna gestione delle transazioni in v1**: la sessione è in autocommit, `SqlExecutor` non emette mai `START TRANSACTION`/`COMMIT`/`ROLLBACK`; uno script si esegue istruzione per istruzione e si ferma al primo errore riportando cosa è stato applicato. L'eventuale aggiunta futura (forse dopo) resta confinata qui.
- Concorrenza: un esecutore a thread singolo per sessione; la UI riceve eventi su EDT (`SwingWorker`/`invokeLater`). Interruzione tramite connessione di servizio.

## 5. Modulo `sqleo-qb` — integrazione di SQLeo

**Origine:** `com.sqleo.querybuilder.**` di SQLeo 2017.09.rc1 + il minimo di `com.sqleo.common.*` che serve (`BorderLayoutPanel`, `Text`, parti di `SQLHelper`, `I18n` ridotto). Nient'altro.

**Facciata** — un'interfaccia sostituisce ogni riferimento a `Application`, `Preferences`, `MDI*`, `ConnectionAssistant`:

```java
public interface QbHost {
    Connection connection();               // connessione JDBC della sessione
    String     catalog();
    Icon       icon(QbIcon id);            // icone fornite dall'app (SVG FlatLaf)
    String     text(String key, String defaultText);   // i18n
    int        scale(int px);              // HiDPI
    boolean    option(QbOption o);         // es. archi vs linee per i join
    void       alert(String message);
    List<JoinHint> joinHints(String table);// FK reali + relazioni logiche del modello ER
}
```

L'app usa il QB solo attraverso `QueryBuilderPanel` (adattatore nel modulo `app`, Step 7): `setSql(String)`, `getSql()`, `addTable(name)`, ascoltatore di modifiche, `isRepresentable(sql)`.

**Stato reale dopo lo Step 1** (pacchetto `it.ramasql.qb`, codice nostro): `QbHost` + `BasicQbHost` da estendere (`connection()`, `catalog()`, `icon(QbIcon)`, `text`, `scale`, `option(QbOption)`, `alert`, `joinHints` → `List<JoinHint>`; testi italiani in `qb_it.properties`); `QbRuntime.setHost(host)` (un host per processo, connessione per istanza); `QbSql.parse(sql)` → `QueryModel` (lancia `QbParseException`), `QbSql.check(sql)` → `Result(representable, model, regenerated, warnings, reason)` che non lancia mai, `isRepresentable`, `normalize`. Pannello: `new QueryBuilder(host)`, `setQueryModel(model)` sull'EDT, `getQueryModel().toString(true)`. **Regola:** non si passa mai al pannello un modello che `check` rifiuta (`BUG-005`).

**Stato dopo lo Step 7 (2026-09-27, `ADR-022`).**
- *Metadati:* `QbHost.metadata()` → `QbMetadata` (tabelle, viste, colonne, chiavi esterne). Il programma risponde con `app.visual.MetadataQbMetadata`, sopra il `MetadataReader` della sessione (canale interno, cache): il query builder **non riceve connessioni** e non esegue nulla (`AppQbHost.connection()` = `null`, `BUG-016` chiuso). `com.sqleo.querybuilder.JdbcQbMetadata` resta come ripiego per chi passa solo una connessione JDBC (prove del modulo).
- *Colori:* `QbHost.color(QbColor)` → i token di `Tokens`.
- *Operazioni:* `com.sqleo.querybuilder.QbOperations` (codice nostro nel pacchetto ereditato): aggiungere/togliere tabelle, spuntare colonne, tipo dei join, WHERE/HAVING/GROUP BY/ORDER BY, condizioni con sottoquery, tabelle derivate, nodi dell'albero, lettura dello stato, `load(check)` che accetta solo modelli rappresentabili. Notifica di modifica: `QueryBuilder.addQueryChangeListener`.
- *Scheda:* `app.visual.VisualQueryTab` = `SqlEditor` (testo, esecuzione, risultati, file) + diagramma come vista alternativa; esecuzione con `CatalogScopedRunner` (`USE` del catalogo + la query, dalla pipeline, origine «Query visiva»). Modalità vista (Step 8): nome, *Salva vista* → `workspace.PipelineViewSaver` (`ViewDdl`, pipeline, `ViewSourceStore`), *Modifica vista* → `ViewReopening` (livelli 1-2-3).
- *Stato `static` residuo* (`BUG-007`): opzioni del `QueryBuilder` uguali per tutte le schede, icone in cache, parser serializzato in `QbSql`; `QbRuntime` ha un host di processo (`AppQbHost.installGlobal()`) per le parti statiche, mentre catalogo e metadati sono per scheda. Due schede insieme: provato da T7.8.

**Interventi previsti sul codice ereditato** (ciascuno annotato in `UPSTREAM.md` e nell'intestazione del file):
1. sostituzione dei riferimenti esterni con `QbHost`;
2. rimozione di `isFullVersion()` e del limite a 3 tabelle; rimozione di pivot, metadati manuali, riferimenti ad altri DBMS;
3. stato `static` (`QueryBuilder.identifierQuoteString`, `selectAllColumns`, …): in prima battuta **un solo QB attivo per volta** garantito dall'adattatore; bonifica in istanza se lo spike mostra che basta poco;
4. parser: casi MySQL/MariaDB emersi dai test (backtick, `LIMIT`, nomi qualificati `catalogo`.`tabella`);
5. resa: colori/font da `UIManager` per convivere con FlatLaf chiaro/scuro, scala HiDPI.

Niente rifattorizzazioni «estetiche»: il modulo resta il più vicino possibile all'originale, così le modifiche sono tracciabili.

## 6. Modulo `model` — modello ER

- `ErModel` (entità, relazioni, note, layout) serializzato in `.rsqlmodel` (JSON con `formatVersion`).
- Entità identificate da `catalogo.tabella`; contengono un'**istantanea** delle colonne presa alla retroingegneria/aggiornamento → il modello si apre anche senza connessione.
- `Relationship { kind: PHYSICAL | LOGICAL, from, to, colonne, cardinalità, etichetta }`. Le `PHYSICAL` si rigenerano dal catalogo; le `LOGICAL` appartengono solo al file.
- `RelationshipSuggester`: regole per nome (`<tabella>_id`, `id_<tabella>`, `<tabella>Id`, omonimia con PK altrui, singolare/plurale italiano e inglese semplice) + compatibilità di tipo; produce proposte con punteggio, mai applicate da sole.
- `AutoLayout`: disposizione a livelli (tabelle più riferite al centro) — algoritmo semplice proprio; niente librerie di grafi.
- Il canvas (`app.er`) è Java2D: entità come nodi trascinabili, connettori ortogonali, zampa di gallina, zoom/pan, esportazione PNG/SVG (JFreeSVG o scrittura diretta)/PDF via stampa.

## 7. Dipendenze e licenze

| Libreria | Uso | Licenza | Compatibile GPL-3 |
|---|---|---|---|
| SQLeo (codice incorporato) | query builder | GPL-2.0-or-later | ✅ (si esercita «or later») |
| FlatLaf (+ extras per SVG) | aspetto | Apache-2.0 | ✅ |
| FlatLaf Extras (stessa versione di FlatLaf) | icone SVG disegnate da noi (`FlatSVGIcon`), sistema visivo `docs/DESIGN-SYSTEM.md` | Apache-2.0 | ✅ |
| RSyntaxTextArea 4.0.1, AutoComplete 4.0.0 | editor SQL (Step 4) | BSD-3-Clause | ✅ |
| MariaDB Connector/J 3.5.10 | driver **unico**, anche per MySQL (`ADR-015`) | LGPL-2.1+ | ✅ |
| Jackson databind 2.22.2 | JSON (profili, impostazioni) | Apache-2.0 | ✅ |
| ~~Apache Commons CSV~~ | non usata: il lettore CSV è del progetto (`ADR-025`) | — | — |
| JUnit 5, AssertJ, AssertJ-Swing | test | EPL-2.0 / Apache-2.0 | ✅ (solo test, non distribuite) |

Ogni nuova dipendenza si aggiunge qui **prima** di entrare nel `pom.xml`.

## 8. Packaging

```
mvnw -pl app -am package
  → app/target/ramasql-<ver>.jar + lib/
jlink  (moduli: java.base, java.desktop, java.sql, java.naming, java.logging, java.prefs, jdk.crypto.ec, …)
  → runtime ridotto (~35–45 MB)
jpackage --type app-image --name RamaSQL --icon … --runtime-image …
  → dist/RamaSQL/ (RamaSQL.exe + runtime + app)
Inno Setup (ramasql.iss): PrivilegesRequired=lowest, lingua it, aula.json facoltativo
  → dist/RamaSQL-Setup-<ver>.exe
Compress-Archive dist/RamaSQL → dist/RamaSQL-<ver>-portable.zip
```

L'app non è modulare (JPMS): gira sul classpath dentro l'immagine; `jdeps` elenca i moduli JDK necessari per `jlink`. Versione unica definita nel `pom.xml` padre e propagata a exe, installer e «Informazioni su».

## 9. Test

| Livello | Cosa | Dove |
|---|---|---|
| Unità | `core.sqlgen` (ogni differenza di tabella → SQL atteso, per MariaDB e MySQL), separatore di istruzioni, deduzione tipi, suggeritore di relazioni, serializzazione del modello, **parser SQLeo** con campionario MySQL | `*/src/test` — girano sempre |
| Integrazione | andata/ritorno reale: crea → altera → rileggi metadati → confronta; FK e errori attesi; import; dump → ripristino → checksum; viste | `it-tests`, contro MariaDB **e** MySQL (D-03), cataloghi `ramasql_test_*` creati e distrutti dal test |
| UI (fumo) | avvio, connessione, apertura tabella, anteprima SQL, query visiva a 2 tabelle | AssertJ-Swing, pochi e stabili |
| Installer | installazione/avvio/disinstallazione su Windows pulito senza Java e senza admin | manuale con lista di controllo (`ROADMAP.md` Step 13) |
| **Controllo incrociato (N)** | l'utente apre con **Navicat** ciò che il client ha creato (Design Table: Fields, Indexes, Foreign Keys, Options; viste; dati; dump) e confronta | a ogni revisione di step (`ROADMAP.md`, test di tipo N) |

Comando unico di verifica previsto: `mvnw verify` (unità + integrazione se le variabili `RAMASQL_IT_*` sono definite).
