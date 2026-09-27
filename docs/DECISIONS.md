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
Data: 2026-09-27 · Stato: **accettata — decisa dall'utente** (Step 12)

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

