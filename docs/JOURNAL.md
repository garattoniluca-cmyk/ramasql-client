# JOURNAL.md — Diario cronologico (più recente in alto)

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
