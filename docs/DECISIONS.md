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
