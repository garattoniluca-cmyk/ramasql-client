# JOURNAL.md — Diario cronologico (più recente in alto)

## 2026-09-22 — Step 1: spike di fattibilità ✅ (esecuzione autonoma, `ADR-014`)
Esito dettagliato in `docs/SPIKE-STEP1.md`: **nessun NO-GO**; `ADR-001` (Java/Swing) e `ADR-002` (app nuova + query builder estratto) confermate; driver unico MariaDB Connector/J (`ADR-015`, da rivedere).

Fatto:
- **SQLeo** clonato al commit `86d3c46` in `spikes/sqleo-upstream/` (escluso da git). Estratto nel modulo `sqleo-qb`: `com.sqleo.querybuilder` + 9 classi di `common`; 27 file modificati con nota datata, elenco in `sqleo-qb/UPSTREAM.md`. Facciata `it.ramasql.qb` (`QbHost`, `BasicQbHost`, `QbRuntime`, `QbSql`, `QbIcon`, `QbOption`, `JoinHint`). Rimossi limite a 3 tabelle, donazione, pivot, metadati manuali, sintassi Oracle, azioni MDI; Google Analytics mai copiato.
- Difetti del parser ereditato corretti: `LIMIT` perso, `DESC` finale trasformato in `ASC`, **verso dei join scambiato** (`a LEFT JOIN b ON b.x = a.y` → `b LEFT JOIN a`), stato statico che contaminava la query successiva, alias automatici che cambiavano i nomi delle colonne con le tabelle derivate, larghezza delle entità (testo a 1 px dal bordo).
- **Licenze** (revisione): due piccoli file ereditati erano **GPL-2.0-only** (JasperSoft) e le 13 icone PNG di origine incerta → `TransferableObject` e `QueryModelTreeCellRenderer` **riscritti da zero** a partire dall'uso nel resto del modulo, senza consultare l'originale (nota di trasparenza: un comando di ricerca ha mostrato per errore all'agente 14 righe di uno dei due file, ma erano righe di modifica nostre del 2026-09-21 — riferimenti a `QbIcon` — non codice JasperSoft; la nuova classe non ne riprende lo schema); le 13 PNG **sostituite da icone disegnate nel codice** (`QbDrawnIcon`, nitide a 200%); `LicenzeERisorseTest` ne impedisce il ritorno; `NOTICE` completato con le attribuzioni JasperSoft/SQLeonardo.
- `it-tests`: infrastruttura riusabile `ItServers` (test parametrici sui due server, falliscono se un server non risponde), `TestCatalog` (catalogo `ramasql_test_*` univoco, distrutto anche in caso di errore), `TestResults`. Driver MariaDB Connector/J 3.5.10.
- Core: logica pura degli step 3–6 già scritta in anticipo (generatori SQL, classificatore di rischio, separatore di istruzioni, DML, appunti a blocchi, modifiche pendenti; 394 test U), commit `ae5e8be`.
- **Utente `ramasql_test_sha2` creato con `root` sul MySQL locale** (plugin `caching_sha2_password`, privilegi solo su `ramasql_test_*`), per provare l'autenticazione predefinita di MySQL 8.4: unica operazione fatta con `root`, da rivedere (`goal.md` lo prevede quando `ramasql_test` non basta).

Validazione (test con `@Tag("step1")`, eseguiti da `scripts\verify.ps1`; evidenze in `test-results/step1/`):
| Test | Esito | Evidenza |
|---|---|---|
| S1 | ✅ GO | `S1S5QueryBuilderResaTest`, `S1FacciataEStatoStaticoTest` (sqleo-qb): compila con `--release 25` senza `Application`/MDI; **5 entità e 4 join** nel diagramma (limite 3 tabelle rimosso, passa da `createAndJoin`) → `S1-qb-5-tabelle.png`, `S1-controlli.txt`; elenco file toccati in `sqleo-qb/UPSTREAM.md`, verificato contro tutti i 52 file originali dal revisore |
| S2a | ✅ GO | `S2aGraficoSqlTest` (it, 2 server): query a 4 tabelle costruita **dal diagramma** con FK reali; 16 righe identiche e nello stesso ordine su MariaDB e MySQL, uguali alla query di riferimento → `S2a-mariadb.txt`, `S2a-mysql.txt`, `S2a-qb-*.png` |
| S2b | ✅ GO | `S2bParserCampionarioTest`: **20/20** rappresentabili (soglia 15), nessuna eccezione; più 8 costrutti deboli (USING, CROSS JOIN, ON con condizione, `--`, ORDER BY/LIMIT in sottoquery, UNION ALL, OVER, WITH RECURSIVE) intercettati da `QbSql.check` senza perdita di testo → `S2b-esiti.md` |
| S2d | ✅ GO con riserve | `S2dQueryNidificateTest` (U), `S2dNidificateSuiServerTest` e `S2dGraficoNidificateTest` (it+ui, 2 server): IN, NOT IN, EXISTS, confronto con MAX/AVG, sottoquery nella SELECT, tabella derivata, due livelli → **aperti nel pannello, modificati nella sottoquery, SQL esterno aggiornato ed eseguito** con stesse righe sui due server; vista su vista e vista con sottoquery riaperte; **CTE = solo testo** (dichiarata non rappresentabile, l'originale gira) → `S2d-esiti.md`, `S2d-server-esiti.md`, `S2d-grafico-esiti.md`, 7 PNG `S2d-grafico-*.png`. `FEASIBILITY.md` F-05bis aggiornata. Difetto visivo `BUG-011` |
| S2c | ✅ GO con riserve | `S2cVisteRiletteTest` (it, 2 server), `ViewDefinitionNormalizerTest`: 11 viste + viste reali; **11/11 riaperte** su entrambi i server con il normalizzatore (6/11 e 5/11 senza); esito atteso fissato vista per vista; `v_statistiche_libri` sì, `v_prestiti_dettaglio` no (join riordinati, `BUG-005`) → editor SQL. Strategia a tre livelli confermata → `S2c-esiti.md`, `S2c-definizioni-*.md` |
| S4 | ✅ GO con riserve | `S4SingleDriverTest` (it): 11 controlli × 2 server (connessione, TLS, `information_schema`, `DatabaseMetaData`, `KILL QUERY` in pochi ms, `cancel()`, chiavi generate, tipi) + 4 controlli `caching_sha2_password` con l'utente dedicato (con `allowPublicKeyRetrieval` e con TLS: riuscito; senza parametri a cache fredda: errore RSA documentato) → `S4-*.txt`, `S4-sha2-senza-parametri-cache-fredda.txt`. **Driver unico** (`ADR-015`). Riserva: server MySQL 8.0.40, non 8.4 (non disponibile sul PC; `BUG-008`) |
| S5 | ✅ GO con riserve | `S1S5QueryBuilderResaTest`: FlatLaf chiaro a 100/150/200% in JVM separate + scala del JRE; nessun testo tagliato, **margine ≥ 2 px** tra testo e bordo (25 testi per scala), contrasto ≥ 3:1 → `S5-100.png`, `S5-150.png`, `S5-200.png`, `S5-*-controlli.txt`. Correzioni rimaste per lo Step 7 in `BUG-004` |
| S6 | ✅ GO | `ErCanvasSpikeTest` (app, ui): 30 entità, 40 relazioni, zampa di gallina; trascinamento (le relazioni seguono), pan, zoom sul cursore con eventi sintetici; `paint` medio 2,5 ms al 100% con un'entità in movimento a ogni fotogramma (soglia 16 ms) → `S6-misure.txt`, `S6-er-canvas*.png` |
| S7 | ✅ GO con riserve | `BlockClipboardSpikeTest` (app, ui, office): copia 3×4 e incolla 20×3 nella JTable; **Excel reale** (COM nascosto) nei due sensi, 60/60 celle con tab, a-capo, virgolette; **Calc** nei due sensi (Calc→client perde tab/a-capo in cella: limite di Calc, `BUG-003`; client→Calc passa dal motore d'importazione di Calc, non da un Ctrl+V letterale) → `S7-*.txt`, `S7-blocco.png`. A-capo verso Excel come CR+LF (`BUG-002`). Appunti dell'utente salvati e ripristinati; Excel protetto da chiusure di cartelle altrui |

**Difetto trovato e corretto in `scripts/verify.ps1` (da rivedere dall'utente):** lo script definiva `$OK = '✅'` e poco dopo `$ok = @{}` per i conteggi; in PowerShell i nomi di variabile non distinguono maiuscole e minuscole, quindi la seconda assegnazione sovrascriveva la prima e il controllo del diario cercava la stringa «System.Collections.Hashtable»: **nessuno step poteva mai risultare PASS**, qualunque cosa ci fosse nel diario (provato: `$OK=…; $ok=@{}; $OK` → `System.Collections.Hashtable`). Correzione minima: la costante si chiama ora `$SPUNTA` (2 righe). Soglie, conteggio dei tag e regole del diario **invariati**; il controllo ora fa esattamente ciò che dice l'intestazione dello script.
Instabilità corretta: il test Calc→client leggeva gli appunti subito dopo il segnale «copiato»; sotto il carico della build completa Calc non li aveva ancora pubblicati (appunti vuoti, 1 fallimento su 4 esecuzioni). Ora aspetta fino a 15 s che il testo ci sia, poi fa tutti i controlli di prima.

`scripts\verify.ps1`: build verde, 1047 test, 0 falliti, 0 saltati; **step 1 = 146 test superati (45 `it`), diario completo → PASS**.

Revisione indipendente (sotto-agente revisore): 17 osservazioni, tutte chiuse o registrate in `BUGS.md` (BUG-001…011). Test N: lo Step 1 non crea oggetti per l'utente, nessun controllo Navicat. Controllo manuale da fare con l'utente: **Ctrl+V letterale in LibreOffice Calc** di un blocco copiato dal client.

## 2026-09-21 — Step 0: fondamenta ✅
Autorizzato dall'utente («sì a tutte e tre»): nome RamaSQL Client, licenza GPL-3, installazione JDK, utente di test.

Fatto:
- **JDK 25** Temurin 25.0.4.1 installato con winget (`JAVA_HOME` di sistema impostata dall'installer).
- **Utente `ramasql_test`** su MariaDB e MySQL: tutti i privilegi sui soli database `ramasql_test_*`, sola lettura su `bibliotecasoft` / `scuola`. Verificato: crea e distrugge `ramasql_test_prova`, **non** può creare un catalogo fuori prefisso (negato dal server). Credenziali in `docs/local DBs.txt`.
- **Progetto Maven** a 5 moduli (`core`, `model`, `sqleo-qb`, `app`, `it-tests`), Java 25, FlatLaf 3.7.2, JUnit 6.1.3, wrapper Maven 3.3.4 → Maven 3.9.16 (nessuna installazione di Maven). Codice minimo: `ProductInfo`, `ModelFormat`, facciata `QbHost` (segnaposto), finestra `MainFrame` con icona provvisoria e testi in `messages.properties`.
- `avvia.cmd`: compila e apre la finestra; trova da solo il JDK 25. `.editorconfig`, `.gitattributes`.

Validazione:
| Test | Esito | Evidenza |
|---|---|---|
| T0.1 build da clone pulito senza Maven | ✅ | `mvnw verify` BUILD SUCCESS, 6 test (1+1+1+2+1), da clone in cartella temporanea |
| T0.2 finestra FlatLaf | ✅ | `avvia.cmd` → finestra «RamaSQL Client dev» in 6 s |
| T0.3 nessuna credenziale versionata | ✅ | ricerca su tutti i file versionabili: solo falsi positivi (`ItConfig` legge da variabili d'ambiente, `mvnw` cita `MVNW_PASSWORD`); `local DBs.txt`, script e log di ripristino ignorati da git |
| T0.4 LICENSE e NOTICE | ✅ | GPL-3 (674 righe), NOTICE con SQLeo e SQLeonardo |

Difetti trovati e risolti durante lo step: `avvia.cmd` falliva con «mvnw.cmd non è riconosciuto» (su questo PC `NoDefaultCurrentDirectoryInExePath=1`) e poi usava Java 8 (sessione aperta prima dell'installazione del JDK) → percorso esplicito e ricerca automatica del JDK. Segnalato dall'utente con uno screenshot.

**Indicazioni dell'utente durante lo step:**
- **Installer solo alla fine**, dopo una versione stabile: tolte dalla roadmap le build installabili intermedie e lo spike S3 (spostato nello Step 13).
- **Verificare il supporto alle query nidificate in query e viste** → letto il codice di SQLeo: supporto esplicito per sottoquery (`SubQuery`), tabelle derivate (`DerivedTable`) e CTE `WITH`, ciascuna aperta come nodo proprio dell'albero della query. Non ancora eseguito: nuovo spike **S2d** e test T7.7b / T8.7b. Corretta l'affermazione precedente che dava le CTE per non rappresentabili (`FEASIBILITY.md` F-05bis).

**Prossimo:** Step 1 — spike di fattibilità (S1 estrazione del query builder, S2a–S2d query e viste incluse le nidificate e le due viste reali di `bibliotecasoft`, S4 driver, S5 resa, S6 canvas ER, S7 appunti a blocchi). Piano dello step da approvare prima del codice.

## 2026-09-21 — Ambiente database di sviluppo pronto (chiude D-03)
Sul PC di sviluppo c'erano già due server, con l'accesso `root` perso: **MariaDB 11.5.2** (`C:\Program Files\MariaDB 11.5`, porta 3306, servizio Windows `MariaDB`) e **MySQL 8.0.40** (`C:\mysql`, installazione da ZIP, porta 3307, senza servizio). Su richiesta dell'utente (database solo di sviluppo, nessun dato critico):
- reimpostata la password di `root@localhost`, `@127.0.0.1`, `@::1` su entrambi con la procedura ufficiale `--init-file` (script locale `ripristina-root.local.ps1`, escluso da git); dati non toccati;
- MySQL registrato come servizio Windows `MySQL80` ad avvio automatico (prima andava avviato a mano: causa dell'errore 2002/10061 in Navicat);
- credenziali e parametri in `docs/local DBs.txt`, **escluso da git** (il repository è pubblico);
- l'utente ha collegato entrambi i server a **Navicat** (connessioni `MariaDBLocal` e `MySQLlocal`): l'ambiente per i test di tipo N è pronto.

Cataloghi dell'utente presenti: `bibliotecasoft`, `ciccio`, `new_schema` (MariaDB); `scuola` (MySQL). **Precisazione dell'utente: sono tutti cancellabili, ma utili per i nostri test locali** → si usano come casi reali; per non consumarli, i test automatici lavorano su **copie** nei cataloghi `ramasql_test_*`. Inventario (sola lettura): `bibliotecasoft` = 5 tabelle InnoDB (`amministratori`, `generi`, `libri` ≈413 righe, `prestiti`, `utenti`) + **2 viste** (`v_prestiti_dettaglio`, `v_statistiche_libri`) + 7 FK; `scuola` = 4 tabelle InnoDB (`alunni`, `classi`, `corsi`, `corsi_classi`) + 3 FK, con tabella ponte N:M; `ciccio` = 1 tabella; `new_schema` vuoto. Usi previsti: le due viste reali per lo spike S2c e lo Step 8 (riapertura grafica di viste scritte altrove); `bibliotecasoft` e `scuola` per retroingegneria ER (Step 11), query visive su FK reali (Step 7), round-trip del dump tra MariaDB e MySQL (Step 10). Entrambi i server usano `mysql_native_password` per root. Solo TCP/IP, SSL non necessario in locale.

Da fare / segnalato all'utente: su entrambi i server esiste un `root@%` (raggiungibile dalla rete) con la vecchia password persa — consigliato `DROP USER` prima di portare il PC in aula; creare l'utente `ramasql_test` con privilegi solo su `ramasql_test_%` per i test d'integrazione.

Lezione per gli script PowerShell 5.1 del progetto (servirà per `build-installer.ps1`): con `$ErrorActionPreference='Stop'` un avviso su stderr di un eseguibile nativo, se rediretto con `2>&1`, diventa errore fatale; e `Start-Process -Wait` attende anche i processi figli.

## 2026-09-21 — Step A: analisi, scelta dello stack, fattibilità, design, piano con test
Richiesta dell'utente: client Windows (o Java) per MariaDB/MySQL basato su SQLeo per ereditarne l'editor visivo di query; semplice, da usare in aula con studenti; Workbench come riferimento funzionale per tutto il resto. Consegne richieste: (1) scelta dell'architettura (Java, C++, C#), (2) fattibilità tecnologica e documento di design, (3) piano a step con verifiche fino all'installer. Organizzazione dei file MD come in `gestionaleFormazione`.

**Indagine svolta** (nessun codice scritto):
- Clonato e analizzato `ojwanganto/SQLeo`: Java Swing, GPL-2-or-later, build Ant, target Java 7, ultima versione 2017.09.rc1 (identica su SourceForge, progetto fermo). 215 file / ≈48.000 righe; query builder `com.sqleo.querybuilder` 43 file / ≈11.600 righe, con ≈25 riferimenti al resto dell'applicazione (estraibile dietro una facciata). Backtick MySQL già gestiti dal parser. Trovati e da rimuovere: limite a 3 tabelle per diagramma nella versione non «completa» (`DiagramLoader.createAndJoin`), ping a Google Analytics (`MDIMenubar`).
- PC di sviluppo: presenti JRE 8 e .NET SDK 10; mancano JDK, Maven, Inno Setup (→ Step 0).

**Decisioni** (`DECISIONS.md`): Java 25 + Swing + FlatLaf (`ADR-001`, da confermare con lo spike); app nuova con il solo query builder estratto nel modulo `sqleo-qb` (`ADR-002`); licenza GPL-3.0-or-later obbligata dall'eredità SQLeo (`ADR-003`); SQLeo riferimento per query/viste visive, Workbench per tutto il resto (`ADR-004`); pipeline unica «anteprima SQL» (`ADR-005`); installer jlink + jpackage + Inno Setup + ZIP portabile (`ADR-006`); dump/import via JDBC (`ADR-007`); modello ER come file locale indipendente dal database (`ADR-008`).

**Indicazioni dell'utente arrivate durante il lavoro, tutte recepite:**
1. Workbench è il riferimento per le funzionalità generali → tabella di corrispondenza in `DESIGN.md` §4.
2. Primo progetto **minimale/medio**: non replicare Workbench, che è «super-pro»; partire dalla lista dei requisiti → `DESIGN.md` §1-bis (colonne «v1» / «[dopo]»), `ADR-009`.
3. Requisito dimenticato: **apertura tabelle in modalità data-entry** → requisito 9, `DESIGN.md` §3.3, `FEASIBILITY.md` F-13.
4. **Appunti su sotto-intervalli rettangolari** di tabella → selezione a celle, copia/incolla a blocchi compatibile con Excel/Calc.
5. **Insert con pulsante esplicito** → modifiche pendenti + pulsante «Conferma»; nessuna scrittura implicita.
6. **Niente gestione transazioni per ora** (Commit, Rollback…): segnata tra i «forse dopo» → `ADR-010`, `DESIGN.md` §4-bis, `BUGS.md`. La conferma esegue in autocommit, riga per riga, con arresto al primo errore.
7. **Indici e chiavi esterne vanno messi e verificati** → pienamente in v1, controlli prima + rilettura dal server dopo ogni applicazione (`ADR-011`); righe orfane riportate in v1.
8. **Nel piano i test specifici per validare ogni step** → `ROADMAP.md` riscritta: ≈150 test con ID, tipo (U/I/M/N), procedura, risultato atteso; schema canonico `biblioteca`.
9. L'utente userà **Navicat** per verificare i database creati dal client → test di tipo **N** in ogni step che crea oggetti (`ADR-012`).
10. Programma **«alla Apple»**: minimale, medio come funzionalità, semplice → principio in testa a `DESIGN.md` §2, regola 5 di `CLAUDE.md`, test T12.7.
11. Repository **GitHub pubblico** → `ADR-013` (chiude D-04).

**Interpretazione da far confermare all'utente:** tra l'indicazione 5 («pulsante commit esplicito») e la 6 («niente Commit/Rollback») si è inteso: il pulsante esplicito di conferma dell'inserimento resta (chiamato «Conferma», non «Commit», per non confonderlo con la transazione SQL); ciò che si rinvia è la gestione delle transazioni SQL.

**Documenti prodotti:** `CLAUDE.md`, `README.md`, `docs/` → ANALYSIS, FEASIBILITY, DESIGN, ARCHITECTURE, ROADMAP, DECISIONS, JOURNAL, BUGS, GLOSSARY, CONSOLE.

**Prossimo:** revisione dell'utente; chiusura di D-01 (nome), D-02 (conferma GPL-3), D-03 (server MariaDB + MySQL di prova, PC dell'aula per lo spike S3) → Step 0 → Step 1 (spike, go/no-go).
