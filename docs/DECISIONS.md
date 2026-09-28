# DECISIONS.md — Registro delle decisioni architetturali

Una voce per decisione. Le decisioni scartate o superate restano qui, non nei commenti del codice.
Stati: **accettata** · **proposta** (in attesa di conferma dell'utente o dell'esito di uno spike) · **superata**.

---

## ADR-001 — Java 25 + Swing come piattaforma del client
Data: 2026-09-21 · Stato: accettata — confermata dallo spike dello Step 1 (S1, S2, S5: GO; `docs/SPIKE-STEP1.md`)

**Contesto:** client Windows per MariaDB/MySQL che deve *ereditare* l'editor visivo di query di SQLeo. SQLeo è Java Swing, GPL, fermo dal 2017. Candidati: Java, C#, C++.

**Decisione:** Java 25 LTS (Temurin), interfaccia Swing con FlatLaf. Il runtime viene incluso nell'installer, quindi sui PC non serve Java.

**Alternative scartate:** C#/.NET (ottimo su Windows, ma impone di riscrivere ≈11.600 righe di query builder con parser inverso: è la parte più difficile del prodotto); C++/Qt o base Workbench (costo e rischio massimi; Workbench non ha query builder e non è realisticamente riusabile); JavaFX (il QB è Swing: due toolkit nella stessa app). Confronto completo in `ANALYSIS.md` §3–§4.

**Conseguenze:** aspetto non nativo al 100% (mitigato da FlatLaf); portabilità Linux/macOS gratuita; obbligo GPL (`ADR-003`). Ripiego se S1 fallisse su JDK 25: JDK 21.

---

## ADR-002 — Applicazione nuova con il solo query builder di SQLeo estratto in un modulo, non un fork dell'intero SQLeo
Data: 2026-09-21 · Stato: accettata — confermata dallo spike S1 (GO; `docs/SPIKE-STEP1.md`)

**Contesto:** SQLeo intero è un client multi-DBMS con interfaccia MDI datata, comparatori, pivot, gestione driver: l'opposto di «più semplice possibile».

**Decisione:** modulo Maven `sqleo-qb` con `com.sqleo.querybuilder.**` e il minimo di `common`; i ≈25 riferimenti al resto di SQLeo sostituiti dall'interfaccia `QbHost`. Rimossi: limite a 3 tabelle della versione non «completa», richiesta di donazione, telemetria Google Analytics, supporto ad altri DBMS. Tutto il resto del client è codice nuovo.

**Alternative scartate:** fork integrale da semplificare (ci si porta dietro 36.000 righe non volute); riscrittura del QB ispirata a SQLeo (perde il vantaggio del riuso).

**Conseguenze:** il codice ereditato resta confinato, tracciato in `sqleo-qb/UPSTREAM.md`, modificato il meno possibile. Lo stato `static` del QB impone inizialmente un QB attivo per volta (R-06).

---

## ADR-003 — Licenza del prodotto: GPL-3.0-or-later
Data: 2026-09-21 · Stato: proposta (D-02)

**Contesto:** il codice SQLeo è «GPL v2 or any later version»; il prodotto che lo incorpora è opera derivata. Le librerie che vogliamo (FlatLaf, Jackson, Commons CSV) sono Apache-2.0, **incompatibile con GPL-2-only ma compatibile con GPL-3**.

**Decisione:** distribuire l'intero prodotto sotto GPL-3.0-or-later, esercitando la clausola «or later» di SQLeo. Intestazioni originali conservate, note di modifica sui file toccati, `NOTICE` con attribuzioni (SQLeonardo 2004, SQLeo 2012), sorgenti forniti con ogni rilascio.

**Conseguenze:** il client non potrà diventare software proprietario finché contiene codice SQLeo. Per uno strumento didattico è un vantaggio, non un limite. Ammesse solo dipendenze compatibili (`ARCHITECTURE.md` §7).

---

## ADR-004 — Riferimenti funzionali: SQLeo per l'editor visivo di query e viste, MySQL Workbench per tutto il resto
Data: 2026-09-21 · Stato: accettata (indicazione esplicita dell'utente)

**Decisione:** le funzionalità generali (connessioni, navigatore, editor SQL, griglia, editor di tabelle/indici/FK, import, dump, retroingegneria ER) si progettano **sul modello di MySQL Workbench**, in forma ridotta: stessa organizzazione, stessi nomi e scorciatoie dove possibile. La tabella di corrispondenza in `DESIGN.md` §4 dice per ogni funzione di Workbench se è ripresa, ridotta, sostituita o esclusa. Solo l'editor visivo di query e la creazione grafica delle viste vengono da SQLeo (Workbench non ne ha).

**Scostamenti deliberati da Workbench:** (1) la revisione dello script SQL vale per *ogni* operazione e il registro è sempre visibile (requisito didattico); (1-bis) appunti su blocchi rettangolari di celle nel data-entry; niente Commit/Rollback/autocommit (`ADR-010`); (2) il modello ER ammette relazioni logiche indipendenti dalle FK e non vincola il database (requisito 8), mentre in Workbench relazione = FK; (3) il dump è generato via JDBC e non con `mysqldump`.

**Conseguenze:** Workbench è riferimento di **comportamento**, non di codice (C++/GRT, non riusabile — `ANALYSIS.md` §3). Chi impara su RamaSQL si ritrova su Workbench. **Precisazione successiva dell'utente (`ADR-009`):** Workbench è «super-pro»; indica *come* fare le cose, non *quante* farne. Il perimetro lo dà la lista dei requisiti.

---

## ADR-005 — Pipeline unica «anteprima SQL»: nessun SQL eseguito senza essere mostrato e registrato
Data: 2026-09-21 · Stato: accettata (è il requisito 5 tradotto in architettura)

**Decisione:** ogni operazione grafica produce un `SqlScript` tramite generatori puri (`core.sqlgen`), lo mostra in anteprima e lo esegue tramite l'unico `SqlExecutor`, che alimenta il registro. Dettagli in `ARCHITECTURE.md` §4.

**Conseguenze:** i generatori sono collaudabili senza database; il registro di una lezione è uno script rieseguibile; nessuna funzione può «fare cose di nascosto». Costo accettato: anche le operazioni banali passano per un clic in più, sempre.

---

## ADR-006 — Installer: jlink + jpackage (app-image) + Inno Setup, più ZIP portabile
Data: 2026-09-21 · Stato: proposta (da confermare con S3 su un PC dell'aula)

**Decisione:** runtime Java ridotto incluso nell'applicazione; Inno Setup produce un `Setup.exe` in italiano, **per-utente senza diritti di amministratore**, con installazione silenziosa per il tecnico e file di connessioni facoltativo. In parallelo uno ZIP portabile.

**Alternative scartate:** `jpackage --type msi/exe` diretto (richiede WiX, meno controllo su lingua e installazione per-utente); Launch4j con JRE esterno (reintroduce «installa Java»); GraalVM native-image (Swing su Windows ancora delicato); MSIX (richiede firma e criteri di laboratorio favorevoli).

**Conseguenze:** ≈45–60 MB; senza firma del codice compare l'avviso SmartScreen (D-05).

---

## ADR-007 — Dump e import implementati nel client via JDBC, senza strumenti esterni
Data: 2026-09-21 · Stato: accettata

**Decisione:** il dump si genera con `SHOW CREATE …` e INSERT a lotti in streaming; l'import usa INSERT parametrici a lotti. Niente `mysqldump.exe`, niente `LOAD DATA LOCAL INFILE`.

**Motivi:** sui PC d'aula gli strumenti client non ci sono; `mysqldump` di MySQL e di MariaDB divergono; `LOCAL INFILE` è spesso disabilitato; e soprattutto l'SQL deve essere mostrabile.

**Conseguenze:** più lento di `mysqldump` su database grandi (irrilevante in aula); fedeltà da garantire con test di round-trip (R-08). L'esecutore di script accetta comunque i dump prodotti da `mysqldump`/Workbench.

---

## ADR-008 — Modello ER come documento locale indipendente dal database
Data: 2026-09-21 · Stato: accettata (requisito 8)

**Decisione:** file `.rsqlmodel` (JSON) con entità (istantanea delle colonne), relazioni **fisiche** (rigenerate dalle FK) e **logiche** (disegnate o suggerite, solo nel file), impaginazione e note. Il modello non scrive mai sul database da solo; l'unico ponte è il comando esplicito «Crea chiave esterna…», che passa dalla pipeline SQL.

**Alternative scartate:** salvare il modello in tabelle di servizio nel database (sporca i database degli studenti, richiede privilegi); relazioni solo da FK come Workbench (contraddice il requisito).

---

## ADR-009 — Perimetro della v1: minimale/medio, «alla Apple»; la lista dei requisiti decide il *cosa*, Workbench solo il *come*
Data: 2026-09-21 · Stato: accettata (indicazione esplicita dell'utente)

**Contesto:** la prima stesura del design, prendendo Workbench come riferimento, tendeva ad accumulare funzioni «da professionisti» (EXPLAIN, autocommit, password cifrate, file di politica, editor BLOB, esportazioni multiple…).

**Decisione:** la v1 contiene solo ciò che serve ai 9 requisiti dell'utente, nella forma più semplice che funzioni: minimale nell'aspetto, media nelle funzioni, semplice nell'uso. Fa fede la colonna «v1» di `DESIGN.md` §1-bis. Le altre idee restano scritte ma marcate **[dopo]** (naturale v1.x) o **[forse dopo]** (parcheggiate). Workbench resta il modello di flusso e di nomi (`ADR-004`), non un elenco di funzioni da replicare.

**Conseguenze:** meno codice, meno test, meno cose da spiegare in aula. Ogni proposta di aggiunta passa da `BUGS.md`, non entra in uno step «già che ci siamo».

---

## ADR-010 — Data-entry a modifiche pendenti con conferma esplicita; nessuna gestione delle transazioni in v1
Data: 2026-09-21 · Stato: accettata (indicazioni esplicite dell'utente)

**Contesto:** requisito 9 (apertura tabelle in modalità data-entry, appunti su sotto-intervalli rettangolari). L'utente vuole che l'inserimento avvenga solo con un pulsante esplicito, e — indicazione successiva — che per ora **non** si aggiunga gestione di transazioni (Commit, Rollback, ecc.), da segnare tra le funzioni «forse dopo».

**Decisione:** la griglia accumula modifiche pendenti (inserimenti, modifiche, eliminazioni, incolla a blocchi) e **non scrive nulla** finché l'utente non preme **Conferma**; **Scarta** le butta (operazione locale). Conferma → anteprima delle `INSERT/UPDATE/DELETE` → esecuzione una alla volta in **autocommit**, con arresto al primo errore: le righe già scritte risultano salvate, la colpevole è indicata, le altre restano pendenti. Il client non genera mai `START TRANSACTION`/`COMMIT`/`ROLLBACK` e non offre interruttore autocommit; vale anche per import (lotti in autocommit) e dump (nessuna istantanea transazionale).

**Alternative scartate:** scrittura automatica al cambio riga (stile Access/Navicat: comoda ma nasconde il momento in cui i dati vengono scritti, e contraddice l'indicazione); conferma avvolta in una transazione con rollback automatico (più robusta, ma è gestione delle transazioni: rinviata per scelta dell'utente).

**Conseguenze:** comportamento identico su InnoDB e MyISAM (nessun caso speciale da spiegare). Un errore a metà di una conferma lascia uno stato parziale ma **sempre visibile** nella griglia. L'eventuale introduzione futura delle transazioni è confinata in `SqlExecutor` (`ARCHITECTURE.md` §4).

---

## ADR-011 — Indici e chiavi esterne: controlli prima, verifica sul server dopo
Data: 2026-09-21 · Stato: accettata (l'utente: «indici, chiavi esterne vanno messe e verificate»)

**Decisione:** indici e FK sono pienamente nella v1 (crea, modifica, elimina). Prima di generare l'SQL il client esegue controlli locali (engine, tipi, indice riferito, SET NULL/NOT NULL) e, a richiesta, sui dati (righe orfane, duplicati per UNIQUE). **Dopo ogni applicazione** rilegge `information_schema` e confronta ciò che trova con ciò che era stato chiesto (`core.verify`), mostrando «✔ verificato sul server» o le differenze.

**Motivi:** MySQL/MariaDB in certi casi accettano un `ALTER` e producono qualcosa di diverso dal richiesto (nomi assegnati d'ufficio, indice implicito per la FK, FK ignorate in silenzio su MyISAM nelle vecchie versioni). In un contesto didattico «l'ho creata» deve significare «esiste davvero».

**Conseguenze:** una lettura di metadati in più dopo ogni DDL (trascurabile). La stessa funzione di confronto alimenta i test d'integrazione T6.4.

---

## ADR-012 — Validazione a quattro livelli: test U, I, M e controllo incrociato dell'utente con Navicat
Data: 2026-09-21 · Stato: accettata (indicazioni dell'utente)

**Decisione:** ogni step della roadmap elenca **test specifici** con identificativo, procedura e risultato atteso: **U** (unità, automatici), **I** (integrazione automatica su MariaDB e MySQL), **M** (manuali, svolti da Claude come QA in creazione), **N** (controlli dell'utente con **Navicat** su ciò che il client ha creato: Design Table → Fields/Indexes/Foreign Keys/Options, viste, dati, esecuzione dei dump, sincronizzazione struttura/dati). Uno step si chiude solo con U/I/M superati con evidenza; i test N fanno parte della revisione dell'utente.

**Motivi:** un secondo strumento, maturo e indipendente, è il modo più economico per scoprire se il client «si racconta» cose diverse da ciò che c'è sul server. Schema canonico di prova: `biblioteca` (+ variante MyISAM senza FK).

---

## ADR-013 — Repository GitHub pubblico da subito
Data: 2026-09-21 · Stato: accettata (indicazione dell'utente; chiude D-04)

**Decisione:** repository pubblico su GitHub fin dalla fase di analisi. Coerente con la GPL-3 (`ADR-003`). Conseguenza pratica, già regola 8 di `CLAUDE.md`: nessuna credenziale, indirizzo di server privato o dato personale nel repository — nemmeno nei documenti.

---

## ADR-014 — Esecuzione autonoma degli step 1–6 con /goal e /loop
Data: 2026-09-21 · Stato: accettata (richiesta esplicita dell'utente)

**Contesto:** l'utente chiede di arrivare in esecuzione autonoma fino allo Step 6 compreso, con agenti che verificano, controllano, correggono e chiudono ogni punto, usando `/goal` e `/loop` come nell'altro progetto (`gestionaleFormazione`, ADR-018/036).

**Decisione:** sospesa per gli step 1–6 la regola n. 2 di `CLAUDE.md` (stop a ogni step). Il lavoro è guidato da `/goal` (condizione in `.claude/goal.md`) in modalità automatica, con `/loop` + `.claude/loop.md` come rete di sicurezza; permessi in `.claude/settings.json`. «Fatto» = `scripts\verify.ps1` stampa `VERIFY: PASS`: build verde, nessun test fallito, per ogni step un numero minimo di test superati con `@Tag("stepN")` (di cui una quota `@Tag("it")` contro MariaDB e MySQL), e ogni test U/I/M della roadmap con esito ✅ ed evidenza in `JOURNAL.md`. Soglie congelate oggi. Commit locale per step, nessun push. Test N con Navicat e prove d'uso con una persona restano all'utente, a fine esecuzione.

**Conseguenze:** le decisioni aperte fino allo Step 6 le prende l'agente, marcate «da rivedere»; al termine l'utente rivede diario, commit e decisioni, esegue i test N e fa il push.

---

## ADR-015 — Driver unico: MariaDB Connector/J anche per MySQL
Data: 2026-09-21 · Stato: accettata — decisa dall'agente, da rivedere (esito dello spike S4, `docs/SPIKE-STEP1.md`)

**Decisione:** un solo driver JDBC, **MariaDB Connector/J 3.5.x** (LGPL-2.1+), per MariaDB e per MySQL, con URL `jdbc:mariadb://host:porta/catalogo`. MySQL Connector/J non entra nel prodotto.

**Motivi:** nello spike tutti i controlli (connessione, TLS, `information_schema`, `DatabaseMetaData`, `KILL QUERY`, `Statement.cancel()`, chiavi generate, tipi, autenticazione) sono verdi su MariaDB 11.5 e MySQL 8.0 con lo stesso driver. Un driver solo = un solo comportamento da spiegare e da collaudare.

**Conseguenze:** i parametri di connessione del prodotto devono permettere `caching_sha2_password` (predefinito di MySQL 8.4) anche senza TLS. Le differenze nei metadati tra i due server (`int(10) unsigned` vs `int unsigned`, default tra apici o no, «nessun default») si normalizzano in `core.metadata`, non nel driver. Riserva: provato su MySQL 8.0.40, non su 8.4 (non disponibile sul PC di sviluppo) → `BUG-008`.

---

## ADR-016 — Parametri di connessione e connessione di servizio
Data: 2026-09-22 · Stato: accettata — decisa dall'agente, da rivedere (Step 2)

**Decisione:** `Session` apre due connessioni con MariaDB Connector/J (`ADR-015`): la **principale**, sul catalogo scelto, per ciò che l'utente esegue; la **di servizio**, senza catalogo, per `KILL QUERY` e letture interne. Parametri del driver (`Session.driverProperties`): `connectTimeout=8000` (il tentativo complessivo è limitato a 10 s da `ConnectionAttempt`, annullabile); `autocommit=true` (`ADR-010`: mai transazioni); `allowPublicKeyRetrieval=true` (serve a `caching_sha2_password`, predefinito di MySQL 8.4, quando non c'è TLS — provato con un utente dedicato su MySQL 8.0); `tinyInt1isBit=false` (`TINYINT(1)` resta un numero, come lo scrive lo studente); `connectionAttributes=program_name:RamaSQL Client` (il client si riconosce nell'elenco dei processi del server). Utente e password non compaiono mai nell'indirizzo JDBC; la password vive solo in memoria per la durata della sessione.

**Motivi:** in aula i server sono quasi sempre locali o in rete di laboratorio senza TLS; `allowPublicKeyRetrieval` è il compromesso che fa funzionare MySQL 8.4 «di serie» senza configurare certificati. La connessione di servizio senza catalogo resta valida anche se il catalogo corrente viene eliminato.

**Conseguenze:** `allowPublicKeyRetrieval` espone a un attacco man-in-the-middle sulla rete del laboratorio: accettabile per un client didattico, da rivalutare con le opzioni SSL (**[dopo]**, `IDEA-012`). Limite noto: il driver vuole la password come `String` nelle proprietà durante l'apertura.

---

## ADR-017 — Routine, trigger ed eventi in sola lettura (D-06)
Data: 2026-09-22 · Stato: accettata — decisa dall'agente, da rivedere (Step 3, chiude D-06 di `ANALYSIS.md` §8)

**Contesto:** il navigatore mostra anche procedure, funzioni, trigger ed eventi (`DESIGN.md` §3.2). Scriverne un editor dedicato non è nella colonna «v1» di `DESIGN.md` §1-bis; l'utente ha indicato «sola lettura» come orientamento (D-06).

**Decisione:** in v1 procedure, funzioni, trigger ed eventi sono **solo elencati** (`MetadataReader.routines`: nome, tipo, per i trigger tabella e momento, per le funzioni il tipo restituito, per gli eventi lo stato) e il loro **testo si mostra a richiesta** con `SHOW CREATE PROCEDURE|FUNCTION|TRIGGER|EVENT` (`MetadataReader.showCreate`), letto dal canale interno dei metadati (non va nel registro). Nessun generatore, nessuna finestra di modifica, nessuna voce «Nuova…/Elimina» specifica: chi vuole crearli o cambiarli usa l'**editor SQL**, che passa comunque da `SqlExecutor` (anteprima dei rischi, registro, `DELIMITER` gestito dal separatore). Se il server non restituisce il testo (oggetto sparito, permesso mancante) il client lo dice invece di fallire.

**Motivi:** in un corso di basi di dati questi oggetti si leggono più di quanto si scrivano; un editor dedicato (parametri, corpo, `DEFINER`, `SQL SECURITY`, pianificazione degli eventi) sarebbe la parte più complessa del client per la parte meno usata. Il testo di `SHOW CREATE` è già l'SQL che lo studente deve imparare a scrivere.

**Conseguenze:** nessun rischio di modifiche involontarie a oggetti scritti da altri. Limite osservato sui server di sviluppo: su **MySQL 8 con il log binario attivo** un utente senza `SUPER` non può creare funzioni né trigger (errore 1419, `log_bin_trust_function_creators`): il client li mostra se esistono, ma i test automatici con l'utente di prova li coprono solo su MariaDB (su MySQL: procedura ed evento). La modifica dedicata resta tra le idee (`IDEA-020`).

---

## ADR-018 — Griglia di data-entry: comportamenti scelti
Data: 2026-09-22 · Stato: accettata — decisa dall'agente, da rivedere (Step 4, `DESIGN.md` §3.3)

**Decisione:** (1) con modifiche in sospeso **paginazione e ordinamento sono bloccati** (pulsanti disabilitati con la spiegazione «conferma o scarta prima»): così nulla si perde e nulla si scrive di nascosto; (2) il clic sull'intestazione **seleziona la colonna** (DESIGN §3.3), l'ordinamento è nel menu del tasto destro sull'intestazione; (3) l'esportazione CSV usa **UTF-8 con BOM, separatore `;` e virgola decimale** (Excel italiano); le righe marcate da eliminare non si esportano, le modifiche in sospeso sì, come si vedono; (4) *Scarta* chiede conferma; (5) colonne AUTO_INCREMENT e generate non modificabili in cella (coerente con l'incolla, che le salta); (6) un editor aperto su NULL e chiuso vuoto lascia NULL (per la stringa vuota: «Modifica in una finestra…»). Dopo una Conferma l'integrazione **rilegge la riga** dal server, così i DEFAULT e gli AUTO_INCREMENT compaiono come li ha scritti il server.

**Motivi:** la regola «nessuna scrittura implicita» è più semplice da spiegare se la griglia non permette di «andare altrove» con lavoro in sospeso; il resto segue le abitudini dell'aula (Excel italiano) e Workbench.

## ADR-019 — Cablaggio delle schede dell'area di lavoro e dei «porti» verso il server
Data: 2026-09-23 · Stato: accettata — decisa dall'agente, da rivedere (Step 4-6, `ARCHITECTURE.md` §4-5)

**Problema.** Gli step 4-6 avevano prodotto i componenti (editor SQL, griglia di data-entry, editor di tabelle) con le loro interfacce verso il server — `SqlRunner`, `GridDataSource`, `TableApplier`, `CatalogTables`, `DataCheck` — ma **solo** implementazioni finte nei test: nessun SQL nato da quei componenti aveva mai raggiunto un server, e l'area di lavoro non apriva schede. I test M della roadmap, che devono «verificare sul server ciò che l'interfaccia dice di aver fatto» (`.claude/goal.md`), non erano quindi rispettati.

**Decisione.** Le implementazioni di produzione vivono in `it.ramasql.app.workspace`, il livello che già conosce la sessione: `PipelineSqlRunner`, `GridApplier`, `PipelineTableApplier`, `PipelineDataCheck`, `MetadataCatalogTables`, `MetadataCompletionSource`, più `WorkTabs` per le schede. In particolare:

1. **Tutto passa dalla pipeline.** Ogni componente riceve un'implementazione che chiama `SqlPipeline.propose(…)`: anteprima, conferma, esecuzione, registro. Non esiste una scorciatoia: `SqlPipeline` non ha un metodo che esegua senza anteprima.
2. **Anche le letture della griglia sono SQL visibile.** `TableGridDataSource` legge le pagine con il `SqlExecutor`, quindi le `SELECT` della griglia compaiono nel registro. Per questo `SqlExecutor` ha ora un limite di righe **per singola lettura** (`run(script, listener, righe)`): la griglia chiede `righePerPagina + 1` senza toccare il limite generale dell'editor. Le colonne si nominano una per una — mai `SELECT *` — così una colonna aggiunta da altri non sfasa le celle.
3. **Una sola regola di conversione dei valori in testo** (`ResultCells`), usata da griglia ed editor: il testo che si legge è quello che il generatore DML riscriverà, altrimenti un andata-e-ritorno cambierebbe date e decimali.
4. **Le finestre modali delle schede si iniettano** da `WorkspacePrompts.gridPrompts()/editorPrompts()/tableEditorPrompts()`, come già per l'anteprima: nei test sono finte, quindi nessun test apre finestre vere.
5. **Schede.** Una tabella e una vista hanno una sola scheda ciascuna (un secondo «Apri» la riporta davanti); **ogni «Nuova tabella…» apre una scheda propria** e il titolo della linguetta segue il nome della tabella. Chiudere una scheda con lavoro in sospeso chiede «Conferma, scarta o resta?»: «Conferma» manda le modifiche all'anteprima e **lascia la scheda aperta**, perché l'esito e le righe rifiutate dal server si devono vedere.
6. **Lettura di una pagina sincrona sull'EDT**, come vuole il contratto di `GridDataSource`: misurata in T4.7 (prima pagina sotto i 10 ms su un milione di righe, ordinamento su colonna senza indice circa 200-260 ms). Resta un limite noto (`BUG-017`): con tabelle molto più grandi o un server remoto va spostata in sottofondo.

**Motivi.** Tenere le implementazioni di produzione in un solo pacchetto lascia i componenti verificabili da soli (test veloci, senza database) e permette ai test M di pilotare il **programma vero** contro i due server. Far passare anche le letture dal registro costa poco e serve alla didattica: in aula si vede sempre l'SQL che il client ha eseguito, comprese le `SELECT` che riempiono la griglia.

**Conseguenze.** I test d'interfaccia contro i server condividono un pacchetto di supporto (`it.ramasql.app.servertest`: `DbServer`, `ClientApp`, `Probe`) che avvia il programma vero senza mostrare finestre e verifica gli esiti con una connessione separata.

## ADR-020 — Suggerimenti esplicativi su tutto il programma, anche sulle voci delle liste
Data: 2026-09-27 · Stato: **accettata — decisa dall'utente** (Step 12) · **realizzata** il 2026-09-28, come descritto in `ADR-028`

**Decisione dell'utente:** «un sistema di tooltip quando il mouse va sopra, su tutto il programma e persino sul contenuto delle liste a discesa, che spieghi con un trafiletto di testo completo tutte le caratteristiche della scelta che si sta facendo. Questo sistema è fondamentale in un ambito come questo.»

**Come lo si realizza (scelte tecniche, da rivedere quando si fa lo Step 12):**
1. **Un solo meccanismo** nel pacchetto `theme` (un gestore dei suggerimenti e un unico stile, `DESIGN-SYSTEM.md` §3.9), usato da tutti i componenti: niente `setToolTipText` sparsi con stili diversi.
2. **Voci delle liste a discesa**: un renderer comune per le `JComboBox` che mostra il suggerimento della voce evidenziata accanto alla lista aperta. Serve anche a liste costruite dal server (collation, tabelle del catalogo): la spiegazione si compone dai dati (es. charset, sensibilità a maiuscole e accenti) quando non esiste un testo scritto a mano.
3. **Testi solo nei file di risorse**, con chiavi prevedibili (`<componente>.tooltip`, `<lista>.<voce>.tooltip`), così si traducono e si correggono senza toccare il codice.
4. **Copertura garantita da un test** (T12.9–T12.11): un componente interattivo senza suggerimento, una voce di lista senza spiegazione o un testo troppo povero fanno fallire la build. È l'unico modo perché la regola regga nel tempo, a ogni schermata nuova degli step 7–11.
5. **Anche da tastiera** (Ctrl+F1), per coerenza con T12.4 e con l'accessibilità.

**Nota sulla tempistica.** Il lavoro è pianificato nello Step 12, ma gli step 7–11 aggiungeranno molte schermate nuove: conviene che da subito ogni componente nuovo nasca **con** il suo suggerimento, altrimenti allo Step 12 si dovrà scrivere tutto in una volta. Da concordare con l'utente se anticipare il meccanismo (punti 1–3).

## ADR-021 — Esecuzione autonoma degli step 7–8 con /goal e /loop
Data: 2026-09-27 · Stato: **accettata — decisa dall'utente**

**Decisione dell'utente:** lanciare di notte, in autonomia, gli step **7 (query editor visivo) e 8 (viste grafiche)**, «con tutte le verifiche e i test del caso». In un primo momento l'utente aveva chiesto fino allo Step 12, poi ha ridotto il piano allo Step 8 compreso. Stesso schema di `ADR-014` (step 1–6): contratto in `.claude/goal.md`, giro di lavoro in `.claude/loop.md`, giudice unico `scripts/verify.ps1`.

**Cosa cambia rispetto agli step 1–6:**
1. **`scripts/verify.ps1` esteso agli step 7–8**, con soglie nuove congelate il 2026-09-27 (step 7: ≥ 60 test di cui ≥ 10 di integrazione; step 8: ≥ 30 di cui ≥ 10) e il controllo del diario fino allo step 8. Le soglie degli step 1–6 **non cambiano**.
2. **Le lezioni degli step 1–6 diventano regole** del contratto: ogni riga M si prova con il programma vero contro i due server (niente componenti collegati solo a finte), revisore indipendente a ogni step, una sola corsa Maven per volta, evidenze che dichiarano il fallimento.
3. **Adattamento deciso prima di partire:** il campionario di query dello Step 7 lo costruisce l'agente, in attesa dell'elenco di esercizi dell'utente.
4. **Prove che richiedono una persona:** T2.9 e T7.11 — righe «predisposto» nel diario, elencate nel resoconto. Test N: T7.12 e T8.8, all'utente.
5. Gli step 9–13 non si toccano; i suggerimenti su tutto il programma (`ADR-020`) restano allo Step 12.

## ADR-022 — Query visiva: come il query builder di SQLeo entra nel programma
Data: 2026-09-27 · Stato: accettata — decisa dall'agente, da rivedere (Step 7, `DESIGN.md` §3.7, `ARCHITECTURE.md` §5)

**Decisione.**
1. **Una scheda, due viste sincronizzate.** La scheda «Query visiva» incorpora l'editor SQL dello Step 4 (esecuzione, risultati, errori spiegati, salvataggio `.sql`) e mette il diagramma al posto del testo quando si sceglie *Grafica*. In Grafica comanda il diagramma: a ogni gesto il testo si rigenera (`QueryBuilder.fireQueryChanged`). In SQL comanda il testo: tornando alla Grafica passa da `QbSql.check` e il diagramma si ricostruisce solo se è rappresentabile; altrimenti si resta sul testo, intatto, con un avviso (regola di `BUG-005`: al diagramma non arriva mai un modello che `check` rifiuta). La scheda SQL interna del query builder è nascosta.
2. **Il query builder non parla con il server.** La facciata del programma (`AppQbHost`) non gli passa alcuna connessione: tabelle, colonne e chiavi esterne arrivano dal canale dei metadati del client (`QbHost.metadata()`, lo stesso del navigatore, con la sua cache) — chiude `BUG-016`. La vecchia lettura con `DatabaseMetaData` resta, raccolta in `JdbcQbMetadata`, solo per chi passa una connessione JDBC (le prove del modulo).
3. **Esecuzione nel catalogo della scheda.** Il query builder scrive i nomi senza catalogo (`FROM libri`), come uno studente; la sessione però può essere in un altro catalogo o in nessuno. Prima della query si esegue ``USE `catalogo` ``: è un'istruzione vera, nell'anteprima e nel registro (nessun SQL nascosto). Origine nel registro: «Query visiva».
4. **SQL «da studente».** Niente alias automatici sulle colonne (`editori.nome AS editori_nome` in SQLeo) né sulle tabelle (`` `libri` libri ``): l'alias di tabella nasce solo se la stessa tabella entra due volte.
5. **Join in parole semplici.** Un clic sul nodo del join apre un menu: «Solo le righe che corrispondono» (INNER), «Tutte le righe di *tabella*» (LEFT/RIGHT), «Condizione…», «Togli il join». Niente FULL OUTER JOIN (MySQL e MariaDB non lo conoscono; in SQLeo si otteneva spuntando le due caselle della maschera).
6. **Resa dai token.** Colori del diagramma chiesti alla facciata (`QbColor` → `Tokens`), intestazione unica delle entità (nome e «×», niente barra del titolo vuota), nodi dei join rotondi, linee scalate, albero della query senza backtick.
7. **Salvataggio `.sql`:** solo il testo; il diagramma si ricostruisce dal testo quando lo si riapre (niente impaginazione salvata: era la decisione rimasta aperta in `DESIGN.md` §3.7).

**Motivi.** Riusare l'editor collaudato evita una seconda esecuzione e una seconda gestione degli errori; tenere il query builder lontano da JDBC mantiene vera la regola «ogni SQL del client passa dal client»; gli alias automatici e il FULL JOIN producevano SQL che uno studente non scriverebbe o che il server rifiuta.

## ADR-023 — Viste: salvataggio, archivio dei sorgenti, riapertura a tre livelli
Data: 2026-09-27 · Stato: accettata — decisa dall'agente, da rivedere (Step 8, `DESIGN.md` §3.8, `FEASIBILITY.md` F-06)

**Decisione.**
1. *Nuova vista* (barra) apre la query visiva in **modalità vista**: una riga con «Nome della vista» e *Salva vista*. La stessa riga compare con *Salva come vista…* da una query visiva qualsiasi. Nessuna opzione ALGORITHM / SQL SECURITY / CHECK OPTION ([dopo]).
2. Una vista nuova si salva con `CREATE VIEW` (se il nome è già usato, l'errore 1050 del server lo dice); una vista riaperta con *Modifica vista* con `CREATE OR REPLACE VIEW`, e da lì non si rinomina. Prima del `CREATE` c'è ``USE `catalogo` ``: i nomi non qualificati della SELECT il server li risolve nel catalogo corrente della sessione, non in quello della vista (trovato scrivendo T8.4: senza, la vista fallirebbe o userebbe le tabelle di un altro catalogo).
3. **Archivio dei sorgenti** in `%APPDATA%\RamaSQL\viste.json` (`ViewSourceStore`, con `formatVersion`): per indirizzo del profilo (`utente@host:porta`), catalogo e vista, il testo scritto dall'utente **e** la definizione riletta dal server subito dopo il salvataggio. Il sorgente vale solo se la definizione sul server è ancora quella: se qualcuno ha cambiato la vista da un altro programma, si passa al livello 2.
4. **Riapertura a tre livelli** (`ViewReopening`): 1) sorgente archiviato; 2) definizione del server normalizzata (`ViewDefinitionNormalizer`, ora codice di prodotto in `core`) e disegnata se il parser la rappresenta; 3) altrimenti il testo, con l'avviso del motivo — si può comunque modificare e salvare.
5. Una vista che il server non riesce a leggere (1356: usa tabelle o colonne sparite) si segnala con l'errore del server spiegato in italiano, invece di un generico «non trovata».

**Motivi.** Il server non conserva il testo scritto: senza archivio la vista di uno studente tornerebbe riscritta e irriconoscibile. Confrontare la definizione evita di riaprire un testo vecchio dopo una modifica fatta altrove.

## ADR-024 — Esecuzione autonoma degli step 9–12 con /goal e /loop
Data: 2026-09-28 · Stato: **accettata — decisa dall'utente**

**Decisione dell'utente:** «un rush» che porta a programmare gli step **9 (importazione CSV e JSON), 10 (dump e ripristino), 11 (modello ER e retroingegneria) e 12 (rifiniture per l'aula)**, **esclusi installer e distribuzione** (Step 13) e quindi anche la sperimentazione in aula (Step 14). Stesso schema di `ADR-014` e `ADR-021`: contratto in `.claude/goal.md`, giro di lavoro in `.claude/loop.md`, giudice unico `scripts/verify.ps1`.

**Cosa cambia rispetto agli step 7–8:**
1. **`scripts/verify.ps1` esteso agli step 9–12**, con soglie nuove congelate il 2026-09-28 (step 9: ≥ 50 test di cui ≥ 10 di integrazione; step 10: ≥ 40 / ≥ 12; step 11: ≥ 50 / ≥ 8; step 12: ≥ 40 / ≥ 6) e il controllo del diario fino allo step 12. Le soglie degli step 1–8 **non cambiano**.
2. **Versione portabile** (`scripts/crea-portabile.ps1`, jpackage con runtime incluso, in `dist\`): chiesta dall'utente il 2026-09-28 per provare il programma in aula prima dell'installer; a fine rush l'agente la rigenera e controlla che si avvii. Non sostituisce lo Step 13 (installer, firma, Windows pulito).
3. **Adattamenti decisi prima di partire** (dettaglio in `.claude/goal.md`): dati di prova dello Step 9 costruiti dall'agente (CSV «come lo salva Excel italiano», JSON, 1 M di righe generato nel test); dump totale di T10.7 provato su copie `ramasql_test_*` dei cataloghi dell'utente, mai sui cataloghi stessi; metà Navicat di T10.8 all'utente se manca il file; T11.11, T12.5, T12.6, T12.13 automatizzati con misure invece di stampa, Resource Monitor e proiettore; revisione «alla Apple» di T12.7 fatta da un sotto-agente con le skill di design, con le rimozioni registrate come decisioni da rivedere.
4. **Prove che richiedono una persona:** T2.9, T7.11, **T12.14** (ed eventualmente la metà Navicat di T10.8) — righe «predisposto» nel diario, elencate nel resoconto. Test N: T9.9, T10.10, T11.12, all'utente.
5. **Limite di tempo:** 24 ore di lavoro (12 per gli step 7–8), dato che gli step sono quattro.

## ADR-025 — Importazione: lettori propri, deduzione su tutto il file, inserimento a lotti senza transazioni
Data: 2026-09-28 · Stato: accettata — decisa dall'agente, da rivedere (Step 9, `DESIGN.md` §3.9, `ARCHITECTURE.md` §3 `core.importer`)

**Decisione.**
1. **Lettori propri, in streaming.** CSV: un lettore scritto nel progetto (`CsvParser`, convenzioni di Excel e RFC 4180: virgolette con separatori e a-capo, virgolette raddoppiate, righe vuote saltate), perché il riconoscimento di codifica e separatore e i messaggi d'errore in italiano con la riga servono comunque a noi; **Apache Commons CSV non entra** fra le dipendenze (era previsto in `ARCHITECTURE.md` §7). JSON: il `JsonParser` in streaming di Jackson (già dipendenza), un oggetto per volta; oggetti e elenchi annidati diventano testo JSON (`DESIGN.md` §1-bis: «annidati appiattiti» è [dopo]).
2. **Codifica** dai primi 64 kB: BOM → UTF-8/UTF-16; UTF-8 valido → UTF-8; altrimenti **Windows-1252** (il «CSV» di Excel in italiano). **Separatore**: quello con cui i primi record hanno lo stesso numero di campi, a parità prima il punto e virgola. Tutto correggibile al passo 1.
3. **Deduzione dei tipi su tutte le righe** (una lettura in sottofondo al passo 2, `FileAnalyzer`), non su un campione: niente `VARCHAR(20)` smentito alla riga 50 000. Regole in `TypeInference` (interi, BIGINT, virgola decimale italiana, date `gg/mm/aaaa`/`mm/gg/aaaa`/ISO, valori logici, zeri iniziali = codice di testo, CHAR per lunghezza fissa, TEXT oltre 255). La **nuova tabella** riceve una chiave primaria: la colonna `id` del file se ha interi tutti diversi (con `AUTO_INCREMENT`), altrimenti una colonna `id` aggiunta — senza chiave la tabella nel data-entry sarebbe di sola lettura.
4. **Conversione prima del server** (`ValueConverter` + `ValueValidator`, lo stesso controllo del data-entry): testo in colonna numerica, data inesistente, vuoto in colonna NOT NULL, testo troppo lungo si scartano con riga e motivo in italiano, senza arrivare al server.
5. **Inserimento a lotti in autocommit, dall'esecutore** (`SqlExecutor.submitBatchInsert`, l'unico punto che esegue SQL, T3.9): un'**istruzione preparata**. Su **InnoDB** un lotto (fino a 1000 righe) è **una sola `INSERT` con più righe di valori**, quindi un solo commit per lotto: con un commit per riga un milione di righe richiedeva più di un quarto d'ora su MySQL (misurato), con i lotti circa 20 s. Un'istruzione InnoDB che fallisce non lascia righe, quindi le righe di quel lotto si ritentano **una per volta** per trovare le colpevoli e inserire le altre. Su **MyISAM** (non annulla un'istruzione fallita) le righe vanno una per volta in un lotto JDBC; per avere l'esito di ogni riga anche su MariaDB il driver ha `useBulkStmts=false` (con il protocollo «bulk» MariaDB dava un solo esito per tutto il lotto). **Nessuna transazione**: *Interrompi* ferma dopo l'istruzione in corso e le righe inserite restano, contate nel rapporto.
6. **Ciò che si mostra.** L'anteprima della pipeline mostra `TRUNCATE TABLE` (opzione «svuota prima», conferma rafforzata) o `CREATE TABLE` (tabella nuova) e l'`INSERT … VALUES (?, ?, …)` preparata; il titolo dice righe, lotti e righe per istruzione. Nel registro: una riga per l'istruzione preparata con la nota «lotti · righe inserite · rifiutate · duplicati»; nell'esportazione del registro è commentata (i valori erano nel file).
7. **«Ignora duplicati»** non usa `INSERT IGNORE` (che trasformerebbe in avvisi anche troncamenti, valori non validi e chiavi esterne mancanti, inserendo dati storpiati): il client conta come «duplicato ignorato» la riga rifiutata con l'errore 1062, e ogni altro errore resta un errore.

**Motivi.** Correttezza del rapporto riga per riga su entrambi i server, velocità accettabile in aula senza transazioni (`ADR-010`), e nessun dato storpiato in silenzio.

**Conseguenze.** L'anteprima mostra l'istruzione preparata nella forma a una riga (`VALUES (?, ?)`), mentre su InnoDB si esegue la forma a più righe dello stesso lotto: lo dice il titolo («fino a 1000 righe per istruzione INSERT»). Un valore vuoto in una colonna NOT NULL con valore predefinito si scarta (con l'istruzione preparata il predefinito non si può chiedere riga per riga): per usarlo, la colonna non si importa.

## ADR-026 — Dump e ripristino: file unico in streaming, esecuzione di file con registro riassunto e sessione controllata
Data: 2026-09-28 · Stato: accettata — decisa dall'agente, da rivedere (Step 10, `DESIGN.md` §3.10, `ARCHITECTURE.md` §3 `core.dump`)

**Decisione.**
1. **Il dump** (`core.dump.Dumper` + `DumpWriter`) scrive **un solo file `.sql` UTF-8 in streaming**: le strutture da `SHOW CREATE TABLE/VIEW` (canale dei metadati), le righe da una `SELECT` per tabella **letta a flusso** dall'esecutore (`submitStreamRead`, `fetchSize` a blocchi, registrata nel registro), in `INSERT` estesi (100 righe o 1 MB per istruzione, scelta dell'utente). Ordine per dipendenze (`DumpOrder`): tabelle riferite prima, viste dopo, viste su viste in ordine; con chiavi **circolari o che riferiscono la stessa tabella** il file spegne i controlli delle chiavi esterne durante il ripristino anche senza l'opzione, e lo dice. **Routine, trigger ed eventi non entrano** (`DESIGN.md` §1-bis: v1 = tabelle e viste); se un catalogo ne ha, l'esito lo dice. Le viste perdono `DEFINER` e il nome del proprio catalogo; se ne usano un altro, l'esito lo avvisa.
2. **Valori fedeli.** Numeri come li scrive il server; binari in esadecimale; testi con apici e barre come `mysqldump`. I `TIMESTAMP` si leggono come `UNIX_TIMESTAMP()` e si scrivono in **UTC**, con `SET TIME_ZONE='+00:00'` nel file (un dump del server della scuola ripristinato su un PC con un altro fuso non sposta le ore, e l'ora ripetuta del cambio d'ora non è ambigua); i `FLOAT` si leggono come `CAST(… AS DOUBLE)` (il server li scrive con 6 cifre). Intestazione e chiusura del file salvano e rimettono `SQL_MODE` (`NO_AUTO_VALUE_ON_ZERO`), `TIME_ZONE` e `FOREIGN_KEY_CHECKS`; la riga finale «Fine del dump» dice che il file è completo.
3. **Nessun file a metà.** Il dump si scrive in `<nome>.part` e si rinomina solo se finisce; interrotto o fallito, il file che c'era con lo stesso nome resta com'era. *Interrompi* durante la lettura di una tabella è sempre un'interruzione (anche quando il server ha già cominciato a preparare le righe), mai un dump che sembra completo.
4. **Il ripristino** è «Esegui script SQL…» (`ScriptRunTab`), che esegue **qualunque** file `.sql` (del client, di `mysqldump`/`mariadb-dump`, di Navicat) a flusso (`ScriptReader`, stessa divisione in istruzioni dell'editor), un'istruzione alla volta in autocommit dall'esecutore (`submitScriptFile`). Prima una **lettura di prova** (`ScriptPreview`): numero di istruzioni; le prime 30 (abbreviate se lunghissime) più, senza tetto, quelle che scelgono, creano o eliminano cataloghi e le distruttive trovate più avanti, perché anteprima e conferma rafforzata le vedano; cataloghi nominati anche dentro i commenti eseguibili `/*!…*/` e come `catalogo.tabella`; impostazioni della sessione che il file cambia; codifica (UTF-8, altrimenti Windows-1252 con un avviso); dump del client incompleto; collation usate.
5. **Catalogo di destinazione scelto dall'utente.** Nessuna scelta predefinita (*Esegui* spento finché non si sceglie), se il file non ha `USE` né `CREATE DATABASE`; se li ha, il catalogo è quello del file e non se ne può scegliere un altro (le istruzioni dopo il `USE` andrebbero comunque lì).
6. **Collation fra server diversi.** Le collation che il server di destinazione non conosce (MariaDB 11 scrive `utf8mb4_uca1400_ai_ci`, MySQL 8 `utf8mb4_0900_ai_ci`) si sostituiscono con la predefinita dello stesso set di caratteri su quel server (`CollationCompat`): la sostituzione si dice nel riepilogo del file, si vede nell'anteprima e finisce così nel registro. Su MariaDB ≥ 10.10 i nomi completi delle collation si leggono da `COLLATION_CHARACTER_SET_APPLICABILITY`.
7. **Registro riassunto.** Di un file si registrano una per una solo le istruzioni che cambiano struttura, sessione o transazione, le distruttive e quelle non riuscite (al più 5000), più **una riga riassuntiva** con le righe del file, i conteggi e gli avvisi del server; gli `INSERT` dei dati sono contati lì e si leggono nel file (un dump con un INSERT per riga ne ha milioni: nel registro e nel pannello sommergerebbero memoria e interfaccia). Nell'**esportazione del registro** le istruzioni di un file diventano un solo commento che rimanda al file: rieseguirne solo alcune (i `DROP` sì, i `CREATE` abbreviati no) distruggerebbe dati. Il pannello aggiunge le righe del registro a blocchi, con un solo passaggio sull'EDT.
8. **Fermarsi e rimettere a posto.** «Fermati» o «Continua» li sceglie l'utente, ma ci si ferma comunque se non riesce un `USE`/`CREATE DATABASE` o se cade la connessione. Prima e dopo il file l'esecutore legge le impostazioni della sessione (`sql_mode`, `time_zone`, controlli delle chiavi e di unicità, set di caratteri) e segue `LOCK TABLES`/`BEGIN`: se il file le lascia cambiate (di solito perché si è fermato prima della sua chiusura), l'esito propone **dalla pipeline** le istruzioni che le rimettono (`SET SESSION …`, `UNLOCK TABLES`); se lascia aperta una transazione lo dice chiaramente e **non** genera `COMMIT`/`ROLLBACK` (regola 5): decide l'utente, dall'editor. Gli avvisi del server (troncamenti in modalità non rigorosa) si contano e si mostrano, tranne le note di `IF [NOT] EXISTS` e le sintassi deprecate.

**Motivi.** Memoria costante con tabelle e file di qualunque dimensione; nessun file o sessione lasciati a metà senza dirlo; ripristino possibile da un server all'altro; ciò che si esegue è ciò che si è visto (regola 3), senza sommergere il registro.

**Conseguenze.** Il registro non contiene il testo di ogni INSERT di un file (il file sì). Un dump del client ripristinato con un altro programma che ignora `TIME_ZONE` scriverebbe i TIMESTAMP in UTC: `mysql`/`mariadb` e Navicat lo applicano. Una riga con un valore oltre gli 8 MB può superare il `max_allowed_packet` predefinito del server: il dump lo avvisa.

## ADR-027 — Modello ER: finestra propria, istantanea nel file, relazioni instradate attorno alle entità
Data: 2026-09-28 · Stato: accettata — decisa dall'agente, da rivedere (Step 11, `DESIGN.md` §3.11, `ARCHITECTURE.md` §6)

**Decisione.**
1. **Finestra propria, non una scheda.** `DESIGN.md` §3.11 dice «documento `.rsqlmodel` aperto in una scheda»; ma le schede dell'area di lavoro esistono solo con una connessione, e T11.7 chiede di riaprire il modello **senza connessione**. Il modello si apre quindi in una **finestra sua** (`ErModelWindow`), che vive indipendentemente dalla connessione (come i modelli di Workbench, separati dagli editor SQL). Si apre dal pulsante **Modello ER** della barra (menu: *Nuovo modello dal catalogo «…»*, *Apri modello…*; spento senza connessione, come gli altri) e dalla voce **File → Apri modello ER…**, l'unica via senza connessione: il menu *File* passa da quattro a cinque voci.
2. **«Nuovo modello dal catalogo…»** chiede quali tabelle (tutte spuntate in partenza); la retroingegneria (`ReverseEngineer`) legge le tabelle scelte dal lettore dei metadati **in sottofondo** e il modello tiene un'**istantanea** di colonne, chiavi e engine: si apre anche senza connessione. Scelte tutte = il catalogo intero, e *Aggiorna dal database* aggiunge anche le tabelle nuove; scelte alcune = solo quelle.
3. **Il modello non tocca mai il database**: nessuna istruzione passa dall'esecutore (registro invariato, T11.6). Le relazioni **fisiche** vengono dalle chiavi esterne e si rigenerano; le **logiche** (disegnate trascinando da colonna a colonna o accettate dal suggeritore) vivono solo nel file. Il file `.rsqlmodel` è JSON con `formatVersion` (versione futura → errore chiaro) e si salva in sottofondo.
4. **Disegno.** Java2D, notazione a zampa di gallina come Workbench; fisiche a tratto pieno grigio, logiche tratteggiate nel colore d'accento; entità mancanti dal database con il bordo rosso tratteggiato; tabelle ponte con la pillola «N:M». Le relazioni sono linee **ortogonali instradate attorno alle entità** (`ErRouter`: griglia sparsa sulle coordinate utili e A* con un costo per ogni curva e per i tratti già usati da un'altra relazione): nessuna linea passa sotto una tabella, e le relazioni parallele scelgono corsie diverse. Durante il trascinamento si ricalcolano solo le relazioni dell'entità trascinata; al rilascio tutte.
5. **Disposizione automatica** (`AutoLayout`): griglia a spirale con le tabelle più collegate al centro, senza sovrapposizioni, misurata con i caratteri veri. **PNG** al doppio della scala dello schermo.

**Motivi.** Riaprire un modello senza connessione è un requisito (T11.7); un diagramma con linee che passano sotto le tabelle non si legge (lo stesso difetto visto nel query builder, BUG-023).

**Conseguenze.** Una finestra in più da chiudere (chiedendo di salvare se ci sono modifiche); il menu *File* ha una voce in più (test T2 aggiornato). Notazione «1 — N» semplificata, note, colori per gruppi, SVG/PDF restano [dopo].

## ADR-028 — Rifiniture per l'aula: suggerimenti realizzati, tutto da tastiera, finestre dentro lo schermo
Data: 2026-09-28 · Stato: accettata — decisa dall'agente, da rivedere (Step 12; realizza `ADR-020`)

**Decisione.**
1. **Suggerimenti (`ADR-020` realizzato).** Un solo disegno, `RamaToolTipUI`: riquadro chiaro con bordo, testo che va a capo da solo entro 360 px al carattere normale (in proporzione al carattere scelto, ma mai oltre i tre quinti dello schermo), prima riga fra `**` come titolo, righe «Attenzione:» nel colore d'avviso; compare dopo mezzo secondo e resta 60 s (il tempo di leggerlo ad alta voce). I testi stanno nei file di risorse con chiavi prevedibili: `<nome del componente>.tooltip` (applicate da `Tips.fromNames` a tutta la finestra), `<tabella>.column.<n>.tooltip` per le intestazioni, `<linguette>.<n>.tooltip`, `<lista>.<voce>.tooltip` per le voci. Le **voci delle liste** spiegano sé stesse **accanto** alla lista aperta (`ComboTips`: a destra o a sinistra, all'altezza della voce, mai sopra la lista; segue mouse e frecce); le liste costruite dal server (collation, set di caratteri, tabelle, colonne) compongono la spiegazione dai dati (`Tips.collation`, `Tips.charset`, `Tips.column`). Il query builder usa lo stesso meccanismo per linguette, elenco e nodi dell'albero (testi nel suo file, `qb_it.properties`). Copertura e qualità sono test della build (T12.9–T12.11).
2. **Da tastiera.** **Ctrl+F1** apre il suggerimento del componente con il fuoco, sotto di lui (`KeyTips`); in alberi, tabelle ed elenchi quello della voce scelta (in una cella senza suggerimento proprio, quello dell'intestazione; senza voce scelta, quello dell'area); **Esc** lo chiude; niente riquadri vuoti. **F6 / Maiusc+F6** passano fra le aree della finestra (barra, navigatore, scheda, pannello SQL): i divisori non usano più F6 per sé; i pulsanti della barra non sono fermate di Tab (si raggiungono con F6, frecce e Spazio). **Maiusc+F10** (o il tasto del menu) apre il menu del nodo del navigatore, al rilascio del tasto. **Alt+Giù** o **F4** aprono la lista di una cella (tipo, tipo d'indice, tabella riferita, azioni…); nella lista aperta le frecce spostano solo l'evidenziazione e la spiegazione accanto (`ComboBox.noActionOnKeyNavigation`), **Invio** sceglie. Provato con l'esercitazione T6.11 intera fatta solo con tasti veri (T12.4).
3. **Finestre dentro lo schermo** (proiettore 1024×768, portatile 1366×768, carattere fino a 28). Ogni finestra di dialogo, comprese quelle dei messaggi e del query builder, passa da `Screens.fit`: se non ci sta si riduce e la parte centrale scorre, mentre i pulsanti restano fissi e in vista; i messaggi vanno a capo a 60 caratteri anche dentro le parole lunghe come i percorsi (`RamaOptionPaneUI`); i popup (suggerimenti, liste, menu) restano dentro lo schermo; le liste lunghe si accorciano perché la lista aperta ci stia. Le barre (strumenti, griglia dei dati) quando lo spazio manca tolgono prima gli spazi fra i gruppi, poi le scritte di alcuni pulsanti lasciando l'icona (`Compact`), mai quella dell'azione principale (*Conferma*, *Esegui*). I passi delle procedure guidate scorrono se non ci stanno.
4. **Revisione «alla Apple»** (T12.7) su un inventario generato dal programma vero (ogni schermata, ogni controllo visibile, fotografie): `docs/REVISIONE-APPLE.md`. Nessun controllo fuori dalla colonna v1; applicati gli interventi piccoli (doppioni di testo, «null» in una lista, un pannello vuoto, righe di linguette su due livelli, pulsanti tagliati, nomi ambigui, *Conferma* fuori dalla finestra, testo delle Informazioni che non scorreva, tipo «INT UNSIGNED» che sembrava una parola sola, colori della sintassi dell'editor SQL); gli altri sono in `BUGS.md` (028–035), uno (i doppioni di *Esegui*) da decidere con l'utente.
5. **Informazioni e sorgenti.** «Informazioni su» elenca ogni libreria contenuta nel programma (confrontata con i jar veri) con la sua licenza, le attribuzioni a SQLeo/SQLeonardo e dove sono i sorgenti; la versione portabile porta con sé `RamaSQL-sorgenti.zip` (l'ultimo commit, `git archive`) accanto a `LICENZA.txt`.

**Motivi.** In aula si proietta a 1024×768 con il carattere grande e non tutti usano il mouse; un suggerimento o un pulsante fuori dallo schermo non esiste. Un meccanismo unico, con i testi nei file di risorse e la copertura nei test, regge anche alle schermate future.

**Conseguenze.** Chi aggiunge un componente deve dargli un nome e una chiave `.tooltip`, altrimenti T12.9 fallisce. Nella lista aperta le frecce non scelgono più la voce da sole: serve Invio (o il clic), come in Windows. Con lo schermo stretto la barra mostra le icone senza scritte (i nomi restano nei suggerimenti).
