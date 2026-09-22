# SPIKE-STEP1.md — Esito dello spike di fattibilità (Step 1)

Data: 2026-09-21. Ambiente: JDK 25.0.4 Temurin, MariaDB 11.5.2 (`127.0.0.1:3306`), MySQL 8.0.40 (`127.0.0.1:3307`), utente di test `ramasql_test`, solo cataloghi `ramasql_test_*`. Evidenze in `test-results/step1/`. I test sono JUnit con `@Tag("step1")` (più `it` / `ui`): girano a ogni `mvnw verify`.

## Riepilogo

| Spike | Domanda | Esito | Dove sono le prove |
|---|---|---|---|
| S1 | Il query builder di SQLeo, estratto, gira su JDK 25? | **GO** | modulo `sqleo-qb`, `S1S5QueryBuilderResaTest`, `S1FacciataEStatoStaticoTest`, `S1-qb-5-tabelle.png`, `sqleo-qb/UPSTREAM.md` |
| S2a | Grafico→SQL su entrambi i server | **GO** | `S2aGraficoSqlTest`, `S2a-<server>.txt/.png` |
| S2b | SQL→grafico, 20 query da aula | **GO** (20/20) | `S2bParserCampionarioTest`, `S2b-esiti.md` |
| S2c | Viste rilette dal server | **GO con riserve** | `S2cVisteRiletteTest`, `ViewDefinitionNormalizerTest`, `S2c-esiti.md`, `S2c-definizioni-<server>.md` |
| S2d | Query nidificate | **GO con riserve** (CTE = solo testo) | `S2dQueryNidificateTest`, `S2dNidificateSuiServerTest`, `S2d-esiti.md`, `S2d-server-esiti.md` |
| S4 | Driver unico? | **GO con riserve** → driver unico MariaDB Connector/J (`ADR-015`) | `S4SingleDriverTest` (11 controlli × 2 server), `S4-*.txt` |
| S5 | Resa sotto FlatLaf a 100/150/200% | **GO con riserve** (elenco correzioni per lo Step 7) | `S1S5QueryBuilderResaTest`, `S5-100/150/200.png` |
| S6 | Canvas ER Java2D | **GO** | `ErCanvasSpikeTest`, `S6-er-canvas*.png`, `S6-misure.txt` |
| S7 | Appunti a blocchi con Excel e Calc | **GO con riserve** | `BlockClipboardSpikeTest`, `S7-*.txt`, `S7-blocco.png` |

**Nessun NO-GO.** `ADR-001` (Java/Swing) e `ADR-002` (app nuova + query builder estratto) sono confermate.

## S1 — estrazione del query builder
- Copiati 52 sorgenti (`com.sqleo.querybuilder` per intero, 43 file, + 9 di `com.sqleo.common`) e 13 icone; niente da `com.sqleo.environment`. 27 file modificati, ciascuno con la nota «Modificato per RamaSQL Client (2026)»; elenco e motivi in `sqleo-qb/UPSTREAM.md`.
- Compila con `--release 25` senza riferimenti ad `Application`, `Preferences`, MDI. Tutto passa dalla facciata `it.ramasql.qb.QbHost` (`BasicQbHost`, `QbRuntime`, `QbIcon`, `QbOption`, `JoinHint`) e da `QbSql` (`parse`, `check`, `isRepresentable`, `normalize`).
- Rimossi: `isFullVersion` e limite a 3 tabelle (provato con **5 tabelle e 4 join** nel diagramma), donazione, pivot, metadati manuali, sintassi `(+)` di Oracle, azioni MDI, filigrana. Google Analytics e `VERSION_TRACK` non sono mai stati copiati. Tre classi di terzi senza intestazione GPL (tips4java) non sono state copiate.
- Difetti del parser ereditato corretti durante lo spike: `LIMIT` perduto; ultimo `DESC` di `ORDER BY` prima di `LIMIT` trasformato in `ASC`; **verso dei join** scambiato quando la `ON` è scritta «nuova = precedente» (`a LEFT JOIN b ON b.x = a.y` diventava `b LEFT JOIN a`); stato statico del parser che contaminava la query successiva dopo una CTE.
- Stato `static` residuo (R-06): opzioni globali in `QueryBuilder`, parser non rientrante (serializzato in `QbSql`). Con un solo tipo di server e opzioni uguali in ogni scheda è innocuo; resta la strategia «opzioni uguali per tutti» dell'adattatore. Da riprovare con due schede in T7.8.

## S2a — grafico→SQL
Query a 4 tabelle (`libri`, `editori`, `libri_autori`, `autori`) costruita **dal diagramma** con il QB collegato al server vero: tabelle, colonne e i 3 join letti dalle FK reali via MariaDB Connector/J, su entrambi i server, senza correzioni alla mappatura catalogo/schema. L'SQL generato dà 16 righe identiche e nello stesso ordine su MariaDB e MySQL, uguali alla query di riferimento scritta a mano.

## S2b — SQL→grafico
20/20 rappresentabili, nessuna eccezione: select semplice, WHERE, alias, INNER/LEFT/RIGHT JOIN, 3 tabelle, COUNT+GROUP BY, HAVING, ORDER BY, LIMIT (anche con scostamento), DISTINCT, IN, BETWEEN, LIKE, IS NULL, parentesi AND/OR, backtick e `catalogo`.`tabella`, sottoquery IN.

## S2d — query nidificate
| Costrutto | Grafico | Sui due server |
|---|---|---|
| `WHERE … IN (SELECT)` / `NOT IN` | ok (nodo `SubQuery`) | originale = rigenerato, MariaDB = MySQL |
| `EXISTS` correlata | ok | idem |
| confronto con `(SELECT MAX/AVG…)` | ok | idem |
| sottoquery nella lista `SELECT` | ok | idem |
| tabella derivata in `FROM` | ok (nodo `DerivedTable`) | idem |
| due livelli di annidamento | ok (2 `SubQuery`) | idem |
| vista su vista; vista con sottoquery | riaperte dalla definizione del server | il rigenerato dà le righe della vista |
| **CTE `WITH`** | **solo testo** (`QbSql.check` la rifiuta) | l'originale gira su entrambi |

Riserva: per la CTE il modello perde il testo `WITH` (senza alias la riscrive come tabella derivata, equivalente; con alias la riscrittura non è eseguibile). **Regola per lo Step 7: non si apre mai graficamente ciò che `QbSql.check` rifiuta.** `LIMIT` dentro una sottoquery non è supportato. → `FEASIBILITY.md` F-05bis aggiornata.

## S2c — viste rilette dal server (R-02)
- 11 viste di prova + viste su schemi reali (copie in `ramasql_test_*`: di `bibliotecasoft` solo struttura e dati non personali).
- **Con il normalizzatore** (bozza: toglie il qualificatore del catalogo, introducer di charset, parentesi ridondanti): 11/11 riaperte graficamente su MariaDB e 11/11 su MySQL; **senza**: 6/11 e 5/11. La definizione normalizzata è verificata equivalente sul server per ogni vista.
- Viste reali: `v_statistiche_libri` si riapre; `v_prestiti_dettaglio` (4 join con un `LEFT JOIN` in mezzo) no, perché `SQLFormatter.sort` riordina i join e `check` rifiuta → si apre nell'editor SQL (livello 3), senza perdite.
- **Strategia a tre livelli confermata** (sorgente locale → parser sulla definizione normalizzata → editor SQL). Il livello 2 rende più del previsto, ma solo con il normalizzatore, che diventa un componente vero dello Step 8 (campionario per T8.2 già salvato).

## S4 — driver
MariaDB Connector/J 3.5.10 contro entrambi i server: connessione, TLS `sslMode=trust`, `information_schema` (COLUMNS, STATISTICS, KEY_COLUMN_USAGE, REFERENTIAL_CONSTRAINTS), `DatabaseMetaData`, `KILL QUERY` su `SLEEP(30)` (interrotto in pochi ms, sessione riutilizzabile), `Statement.cancel()`, `getGeneratedKeys`, tipi (DECIMAL, DATE, DATETIME, TINYINT(1), BIT, UNSIGNED, date zero): tutto verde su tutti e due. **Driver unico.**

Riserve:
1. **`caching_sha2_password`** provato con l'utente dedicato `ramasql_test_sha2` (creato con `root`, privilegi solo su `ramasql_test_*`): riesce con `allowPublicKeyRetrieval=true` senza TLS e con `sslMode=trust`; senza nessuno dei due, a cache fredda del server, fallisce con «RSA public key is not available client side» → il prodotto imposta `allowPublicKeyRetrieval` (`ADR-016`). Evidenze `S4-autenticazione-mysql.txt`, `S4-sha2-senza-parametri-cache-fredda.txt`.
2. Il server è MySQL 8.0.40, non 8.4 come scritto in roadmap.
3. `getGeneratedKeys` dopo un `INSERT` multi-riga restituisce solo la prima chiave → il data-entry inserisce riga per riga (è già il design).
4. Differenze dei server da normalizzare in `core.metadata`: `int(10) unsigned` (MariaDB) vs `int unsigned` (MySQL); default di stringa `'IT'` vs `IT`; «nessun default» = testo `NULL` vs null.
5. Le emoji nei **commenti** di colonna diventano `?` su entrambi i server (commenti in utf8mb3): limite del server; nei dati le emoji funzionano.

## S5 — resa
A 100/150/200% nessun testo tagliato (margine ≥ 2 px tra testo e bordo), nessun contrasto sotto 3:1; icone disegnate nel codice, nitide a ogni scala; albero della query in italiano. Già corretti: titolo tecnico sulle entità, join non disegnati fuori schermo, linee dei join troppo chiare, misure fisse in pixel. **Correzioni da fare nello Step 7:** barra del titolo delle entità vuota, piccolo vuoto tra linea del join e bordo, linee che passano dietro altre entità con la disposizione a griglia, colori fissi (tema scuro [dopo]), etichette lunghe a filo del bordo, spessore delle linee non scalato.

## S6 — canvas ER
30 entità, 40 relazioni, zampa di gallina leggibile (tre dita + cerchio/barra; doppia barra sul lato uno). `paint` medio su 1600×1000: 1,97 ms al 100% (max 3,23), 1,42 ms al 50%, 1,30 ms al 200% — soglia 16 ms. Trascinamento, pan, zoom sul cursore, esportazione PNG dell'intero diagramma provati con eventi sintetici. Limite del prototipo: le linee ortogonali non aggirano le entità (da curare nello Step 11).

## S7 — appunti a blocchi
- JTable con selezione a celle: copia 3×4 e incolla 20×3 (18 righe nuove) corretti.
- **Excel reale** (automazione COM, finestra nascosta): JTable→Excel celle giuste, con tab, virgolette, accenti, emoji; l'a-capo in cella arriva come CR+LF (Java su Windows). Excel→JTable: 60/60 celle identiche. Excel racchiude tra virgolette solo le celle con tab o a-capo: il lettore è tollerante. Excel reinterpreta `007`, `1/2`, `=1+1` (comportamento suo).
- **LibreOffice Calc** (headless): Calc→JTable con `.uno:Copy` reale: celle al posto giusto, ma il formato testo di Calc sostituisce tab e a-capo in cella con uno spazio. JTable→Calc: in headless `.uno:Paste` non è possibile (apre la finestra «Importazione testo»); provato facendo leggere a LibreOffice gli appunti di sistema e passando il testo al suo motore d'importazione con i valori predefiniti di quella finestra: tutto corretto. **Non è un Ctrl+V letterale → resta un controllo manuale per l'utente** (vedi resoconto finale).
- I test sovrascrivono gli appunti dell'utente (li ripristinano se contenevano testo).

## Conseguenze sul piano
- Stime confermate. Lo Step 7 parte da un modulo già funzionante; vanno aggiunti alla facciata: operazioni sul diagramma (oggi i test usano un helper di pacchetto), notifica di modifica Grafico→SQL, caricamento tabelle fuori dall'EDT, uso dell'editor dell'app al posto della scheda SQL interna.
- Lo Step 8 deve includere il normalizzatore e rivedere `SQLFormatter.sort` (o un criterio di equivalenza meno testuale).
- Licenze del modulo `sqleo-qb` sistemate già nello Step 1 (revisione): due file GPL-2.0-only riscritti da zero, icone PNG di origine incerta sostituite da icone disegnate nel codice (`BUG-001`, chiuso).
