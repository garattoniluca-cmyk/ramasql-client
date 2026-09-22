# BUGS.md — Difetti aperti, idee rinviate, idee parcheggiate

Tre sezioni. I difetti si numerano `BUG-nnn`; le idee `IDEA-nnn`. Un'idea entra in uno step solo per decisione dell'utente (regola 5 di `CLAUDE.md`).

## 1. Difetti aperti

| ID | Difetto | Origine | Quando |
|---|---|---|---|
| ~~BUG-001~~ | ~~Icone PNG ereditate da SQLeo di origine incerta (Silk CC-BY, due ignote) e due file ereditati **GPL-2.0-only** (JasperSoft)~~ — **risolto il 2026-09-22**: icone sostituite da `QbDrawnIcon` (disegnate nel codice, nitide a ogni scala), `TransferableObject` e `QueryModelTreeCellRenderer` riscritti da zero a partire dall'uso nel modulo; test `LicenzeERisorseTest` impedisce il ritorno di licenze GPL-2-only e di immagini | revisione Step 1 | chiuso |
| BUG-002 | **A-capo dentro una cella copiata verso Excel arriva come CR+LF**: Java su Windows converte i fine riga dello `StringSelection`. Rimedio noto: `Transferable` proprio con flavor `text/plain;charset=utf-16` su `InputStream` | spike S7 | Step 4 |
| BUG-003 | **Calc → client: tabulazioni e a-capo dentro una cella diventano uno spazio** nel formato testo degli appunti di LibreOffice (limite di Calc). Rimedio possibile: leggere il flavor HTML degli appunti | spike S7 | Step 4 (valutare) |
| BUG-004 | **Resa del query builder** (elenco da S5): barra del titolo delle entità vuota; piccolo vuoto tra linea del join e bordo; linee che passano dietro altre entità; colori fissi (tema scuro [dopo]); spessore delle linee non scalato | spike S5 | Step 7 |
| BUG-005 | **`SQLFormatter.sort` riordina i join** (e ne cambia tipo/operandi in `toString`): con join misti INNER/LEFT la riscrittura può non essere equivalente. Oggi protegge solo `QbSql.check`: la facciata dello Step 7 deve renderlo **obbligatorio** prima di ogni `setQueryModel`. Effetto visibile: la vista reale `v_prestiti_dettaglio` non si riapre graficamente | spike S2c | Step 7–8 |
| BUG-006 | **Facciata `sqleo-qb` incompleta**: mancano operazioni sul diagramma (i test usano `QbAccessoDiProva`, classe nostra nel pacchetto `com.sqleo.querybuilder` dentro `it-tests`: pacchetto diviso su due moduli, da eliminare), notifica di modifica Grafico→SQL, caricamento tabelle fuori dall'EDT; la scheda «SQL» interna del QB va nascosta a favore dell'editor dell'app | spike S1/S2a | Step 7 |
| BUG-007 | **Stato `static` nel query builder** (opzioni globali in `QueryBuilder`, parser non rientrante serializzato in `QbSql`): innocuo con opzioni uguali per tutte le schede; da riprovare con due schede (T7.8, R-06) | spike S1 | Step 7 |
| BUG-008 | **Driver provato su MySQL 8.0.40, non 8.4** (non disponibile sul PC di sviluppo) | spike S4 | quando c'è un server 8.4 |
| BUG-009 | `mvnw verify` richiede **Excel e LibreOffice installati** (test S7 con tag `office`): su un PC senza, quei test falliscono (giustamente non saltano). Durante la verifica gli appunti di sistema vengono usati e poi ripristinati: non copiare/incollare mentre gira | spike S7 | noto |
| BUG-011 | **Entità del diagramma che non si allarga dopo l'aggiunta di un filtro**: l'icona del filtro occupa spazio e il nome del campo viene troncato (`prez…` in `S2d-grafico-due-livelli.png`, `data_restit…` in `S2d-grafico-in.png`, `test-results/step1/`). Il controllo automatico di S5 guarda solo la scena a 5 tabelle | spike S2d (grafico) | Step 7 |
| BUG-010 | Il **normalizzatore delle definizioni di vista** è una bozza nei test (`it-tests/.../step1/ViewDefinitionNormalizer`): non gestisce parentesi aritmetiche, join annidati a destra, alias di tabella uguale al nome del catalogo; l'introducer `_utf8mb4'…'` è coperto solo da test senza database | spike S2c | Step 8 |

Note dai server (non difetti del client, ma da sapere): le **emoji nei commenti** di colonna/tabella diventano `?` su MariaDB e MySQL (i commenti sono utf8mb3); `getGeneratedKeys` dopo un `INSERT` multi-riga dà solo la prima chiave (il data-entry inserisce riga per riga).

Emersi dall'analisi di SQLeo — **rimossi nell'estrazione dello Step 1** (restano sotto controllo con il test T7.1):
- limite di 3 tabelle per diagramma e richiesta di donazione (`DiagramLoader.createAndJoin`, `Application.isFullVersion`);
- chiamata a Google Analytics (`MDIMenubar`, `_Version.VERSION_TRACK`);
- stato `static` nel query builder (`QueryBuilder.identifierQuoteString`, `selectAllColumns`…): rischio di interferenza tra due schede (R-06, test T7.8).

## 1-bis. Note tecniche
- `NoDefaultCurrentDirectoryInExePath=1` su questo PC: negli script chiamare `.\mvnw.cmd` / `"%~dp0mvnw.cmd"`. Risolto in `avvia.cmd` (Step 0).
- Le sessioni avviate prima dell'installazione del JDK vedono ancora Java 8: `avvia.cmd` cerca da solo il JDK 25.

## 2. Forse dopo — parcheggiate su indicazione dell'utente, nessun impegno

| ID | Idea | Nota |
|---|---|---|
| IDEA-001 | **Gestione delle transazioni**: interruttore autocommit, pulsanti Commit/Rollback nell'editor SQL; conferma del data-entry avvolta in `START TRANSACTION … COMMIT` con rollback automatico in caso d'errore; import «tutto o niente»; dump con istantanea coerente | Utente, 2026-09-21: «per ora non aggiungere gestione transazioni; segna tra le feature da mettere forse dopo». In v1: sempre autocommit (`ADR-010`). Punto d'innesto futuro: `SqlExecutor` |

## 3. Dopo — naturale v1.x, già ragionate in `DESIGN.md` (voci **[dopo]**)

| ID | Idea | Requisito |
|---|---|---|
| IDEA-010 | Elenco a discesa dei valori ammessi sulle colonne con FK nel data-entry (primo candidato v1.1) | 9, 3 |
| IDEA-011 | Filtri per colonna nella griglia; editor BLOB e JSON; export JSON e SQL INSERT | 9 |
| IDEA-012 | Più connessioni aperte insieme; password salvate (Windows DPAPI, D-08); opzioni SSL; colori delle connessioni | 1 |
| IDEA-013 | Riordino colonne, duplica struttura; indici FULLTEXT, prefissi e direzione | 2, 3 |
| IDEA-014 | Editor SQL: EXPLAIN, formattatore, cronologia, ripristino delle schede non salvate, «query interne» nel registro | 5 |
| IDEA-015 | Viste: ALGORITHM, SQL SECURITY, WITH CHECK OPTION | 6 |
| IDEA-016 | Import: JSON Lines, appiattimento degli annidati, «aggiorna su duplicato», file degli scarti; export per tabella in cartella CSV/JSON | 7 |
| IDEA-017 | Modello ER: note, colori, livelli di dettaglio, SVG/PDF/stampa, «Crea chiave esterna…» da relazione logica, join del query builder suggeriti dalle relazioni logiche | 8 |
| IDEA-018 | File di politica `aula.json` (connessioni preinstallate, blocco `DROP DATABASE`, cataloghi nascosti); tema scuro; guida integrata estesa | aula |
| IDEA-019 | Supporto ufficiale Linux/macOS (il jar già gira) | — |
| IDEA-020 | Routine, trigger, eventi modificabili (in v1 sola lettura, D-06) | — |
