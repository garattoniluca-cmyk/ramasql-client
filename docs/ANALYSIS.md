# ANALYSIS.md — Analisi tecnica e scelta dello stack

Data: 2026-09-21. Il documento risponde alla prima domanda della richiesta: **qual è il modo migliore di costruire il client Windows + installer: Java, C++ o C#?** Le decisioni che ne derivano sono in `DECISIONS.md`; la fattibilità punto per punto in `FEASIBILITY.md`.

## 1. Sintesi (la scelta in 10 righe)

**Java 25 + Swing, riusando direttamente il codice del query builder di SQLeo; installer prodotto con jpackage (runtime Java incluso) + Inno Setup.**

Il vincolo che decide tutto è «ereditare il query editor visivo di SQLeo». SQLeo è **Java Swing**: in Java quel componente si *riusa* (≈11.600 righe già funzionanti, con parser SQL inverso incluso); in C# o C++ si dovrebbe *riscrivere*, ed è la parte più difficile dell'intero prodotto. Lo storico punto debole di Java sul desktop — «bisogna installare Java» — non esiste più: jpackage impacchetta un runtime ridotto dentro l'applicazione, e lo studente installa un normale `.exe` senza sapere che sotto c'è Java. In più il prodotto risulta eseguibile anche su Linux/macOS a costo quasi zero (utile per gli studenti a casa), cosa che la richiesta ammette («windows (o java)»).

## 2. Cosa è stato verificato su SQLeo (clone del 2026-09-21)

Repository `ojwanganto/SQLeo`: mirror GitHub del progetto SourceForge *SQLeo Visual Query Builder*, a sua volta derivato da *SQLeonardo* (2004).

| Aspetto | Rilevato | Conseguenza |
|---|---|---|
| Linguaggio / UI | Java, **Swing**, MDI (finestre interne), Look&Feel Metal | Il riuso impone Swing come toolkit dell'app (§4) |
| Licenza | **GPL v2 «or (at your option) any later version»** (intestazione di ogni file) | Il nostro prodotto è opera derivata → deve essere GPL. Il «or later» permette **GPL-3**, necessaria per usare librerie Apache-2.0 (`ADR-003`) |
| Stato | Ultima versione **2017.09.rc1** (17-09-2017), identica su SourceForge; progetto fermo | Nessun upstream da seguire: si fa un fork definitivo, senza preoccuparsi di future fusioni |
| Dimensione | 215 file, ≈48.000 righe; build Ant, target Java 7, nessuna dipendenza esterna nel query builder | Compilabile con JDK moderno con ritocchi minimi (da confermare nello spike S1) |
| Query builder | pacchetto `com.sqleo.querybuilder`: 43 file, **≈11.600 righe** (diagramma, drag&drop, maschere join/condizioni, modello sintattico, `SQLParser` per il percorso inverso SQL→diagramma, `SQLFormatter`) | È esattamente la parte da ereditare |
| Accoppiamento del QB col resto | ≈25 riferimenti: `Application` (icone, finestra, alert), `Preferences` (scala, opzioni), `I18n`, `ConnectionAssistant/Handler`, `SQLHelper`, alcune finestre MDI | **Estraibile**: si sostituiscono con una piccola facciata (`ARCHITECTURE.md` §5). Stima: 2–4 giorni |
| Identificatori MySQL | il parser usa `DatabaseMetaData.getIdentifierQuoteString()` → backtick gestiti | Buon segno per la retroingegneria delle viste (rischio R-02) |
| Test esistenti | solo `SQLParserTest` (JUnit 4, 2 casi) | Si aggiunge una batteria di test sul parser con SQL tipico MySQL/MariaDB |
| **Limite «3 tabelle»** | `DiagramLoader.createAndJoin`: se `!Application.isFullVersion()` il diagramma accetta max 3 tabelle e chiede una donazione | Da **rimuovere** (lecito sotto GPL). Senza questo, l'editor sarebbe inutilizzabile in aula |
| **Telemetria** | `MDIMenubar` chiama Google Analytics (`_Version.VERSION_TRACK`) | Da **rimuovere**: niente traffico verso terzi da PC di studenti. Non è nel pacchetto QB, quindi sparisce con l'estrazione |
| API interne JDK | `sun.boot.class.path`, caricamento dinamico driver via `URLClassLoader`, driver ODBC `sun.jdbc.odbc` | Tutto fuori dal QB (gestione driver generici): non ci serve, i driver sono inclusi nel prodotto |
| Altro codice SQLeo | explorer, content/grid, comparatore dati, pivot HTML, CSV-JDBC, definizione tabelle | **Non si riusa**: è multi-DBMS, datato e più complesso di quanto serva a uno studente. Resta consultabile come riferimento |

**Conclusione sull'eredità:** non si fa un fork dell'intera applicazione SQLeo per poi «semplificarla» (ci si porterebbe dietro MDI, multi-DBMS, preferenze, comparatori). Si costruisce un'**applicazione nuova** e vi si innesta il **solo query builder**, estratto in un modulo a sé (`ADR-002`).

## 3. Confronto delle tre strade

Criteri pesati sul caso reale: riuso di SQLeo (decisivo), semplicità d'installazione in laboratorio, tempi, manutenibilità da parte di una persona, resa su Windows.

| Criterio | **Java (Swing)** | **C# (.NET 10, WinForms/WPF)** | **C++ (Qt o stack Workbench)** |
|---|---|---|---|
| Riuso query builder SQLeo | ✅ **diretto** (stesso linguaggio e toolkit) | 🔴 riscrittura completa di ≈11,6k righe, incluso un parser SQL inverso; IKVM/bridge Java↔.NET non praticabile per componenti Swing dentro una UI .NET | 🔴 riscrittura completa |
| Riuso MySQL Workbench | riferimento funzionale soltanto | riferimento funzionale soltanto | 🔴 teoricamente codice riusabile, in pratica no: ≈1M righe, GRT + Python + wrapper .NET su Windows, build notoriamente difficile, e comunque **non** ha un query builder visivo |
| Aspetto «nativo» su Windows | 🟡 buono con FlatLaf (tema moderno, HiDPI), non nativo al 100% | ✅ ottimo | 🟡 buono con Qt |
| Installer | ✅ jpackage + Inno Setup: exe per-utente, senza admin, runtime incluso (~45–60 MB) | ✅ self-contained + Inno Setup/MSIX (~70–150 MB) | ✅ (~30 MB) |
| Driver MariaDB/MySQL | ✅ Connector/J (JDBC), metadati ricchi (`DatabaseMetaData`) su cui SQLeo già poggia | ✅ MySqlConnector (MIT) | 🟡 Connector/C, più lavoro manuale |
| Tempi per arrivare alla 1.0 | **più brevi** (la parte difficile esiste già) | +2–3 mesi per il solo query builder, con rischio alto | i più lunghi |
| Rischio tecnico | basso-medio (codice del 2017 su JDK 25: da provare) | alto (reimplementare fedelmente il QB) | molto alto |
| Portabilità (studenti a casa con Mac/Linux) | ✅ gratis | 🔴 no (WinForms/WPF) · 🟡 Avalonia | ✅ con Qt |
| Vincolo di licenza | GPL (derivato SQLeo) | anche riscrivendo «a calco» resterebbe il dubbio di opera derivata | Workbench è GPL-2; Qt LGPL/GPL |
| Toolchain già presente sul PC di sviluppo | solo JRE 8 → va installato JDK 25 | .NET SDK 10 presente | da installare |

**Perché non C#**, che sarebbe la scelta naturale per «un'app Windows con installer» in assenza di vincoli: perché il valore distintivo del prodotto — l'unico client con editor grafico di query e viste — andrebbe ricostruito da zero. Ha senso solo se un giorno si decidesse di abbandonare SQLeo.

**Perché non C++:** costo e rischio massimi, beneficio nullo per questo pubblico.

### 3.1 «Ma Workbench è in C++: perché allora Java?» (domanda dell'utente, 2026-09-21)

Perché i due riferimenti hanno ruoli diversi: da **SQLeo si prende codice** (≈11.600 righe di editor visivo, riusabili solo restando in Java Swing), da **Workbench si prende comportamento** (com'è fatta una schermata, in che ordine si fanno le cose), e un comportamento si copia guardandolo, in qualunque linguaggio. Il linguaggio va scelto sul codice che si riusa.

Partire dal codice di Workbench non è un'alternativa reale: non è «un programma C++» ma un nucleo C++ con tre interfacce distinte (su Windows uno strato C#/.NET via C++/CLI, su Linux GTK, su macOS Cocoa), un sistema di oggetti interno (GRT) e molto Python; è enorme e difficile da compilare fuori da Oracle; è un client per MySQL, con incompatibilità note verso MariaDB; e soprattutto **non ha un editor visivo di query**, cioè manca proprio della funzione che distingue questo prodotto. In C++ si riscriverebbe l'editor visivo *e* tutto il resto, senza riusare nulla.

**In fondo sono tutte chiamate SQL** (osservazione dell'utente): verso il database ogni client fa la stessa cosa — apre una connessione e invia testo SQL — e il server non sa in che linguaggio è scritto chi lo chiama. La parte «database» è quindi equivalente in Java, C# o C++, e non c'è alcuna prestazione nativa da sfruttare (il tempo si spende sul server e in rete). **Tutta la differenza tra i linguaggi sta nell'interfaccia**, ed è lì che Java parte con la parte più costosa già scritta. È anche ciò che rende sensati il principio «SQL sempre mostrato» (se il client è solo SQL, mostrarlo è mostrare tutto) e la validazione incrociata con Navicat (strumenti diversi, stessi metadati sullo stesso server).

La scelta si riaprirebbe solo rinunciando a SQLeo; in quel caso il candidato sarebbe C#, non C++.

## 4. Scelte interne allo stack Java

| Tema | Scelta | Alternative scartate |
|---|---|---|
| Toolkit UI | **Swing** | JavaFX: il QB è Swing e andrebbe incapsulato in `SwingNode` (drag&drop, focus e HiDPI problematici); due toolkit nella stessa app = doppia complessità. SWT: stesso problema |
| Aspetto | **FlatLaf** (Apache-2.0): tema chiaro/scuro, HiDPI, icone SVG | Metal/Nimbus (datati), Look&Feel di sistema (resa HiDPI irregolare) |
| JDK | **25 LTS** (Temurin); jpackage/jlink inclusi | 21 LTS: accettabile come ripiego se lo spike trovasse incompatibilità; 8/11: niente jpackage moderno |
| Editor SQL | **RSyntaxTextArea** + AutoComplete (BSD-3): evidenziazione, completamento di tabelle/colonne, ricerca | `SQLStyledDocument` di SQLeo (rudimentale) |
| Driver | **MariaDB Connector/J 3.x** (LGPL-2.1) come unico driver se lo spike S4 conferma che copre MySQL 8.0/8.4 (`caching_sha2_password`, metadati); altrimenti si aggiunge **MySQL Connector/J** (GPL-2 + Universal FOSS Exception) e si sceglie in base al tipo di server | un driver generico configurabile dall'utente: inutile complessità |
| Build | **Maven** multi-modulo con wrapper (`mvnw`), così non serve installare Maven | Ant (com'è SQLeo: niente gestione dipendenze), Gradle (più potente, più fragile tra versioni) |
| JSON / CSV | Jackson (Apache-2.0) in streaming, Commons CSV (Apache-2.0) | parser fatti a mano |
| Canvas del modello ER | **canvas proprio in Java2D** con notazione a zampa di gallina, ispirato alla resa di SQLeo per coerenza visiva | JGraphX (BSD, non mantenuto), riuso diretto di `ViewDiagram` (troppo legato al modello di query). Da confermare nello spike S6 |
| Dump | **generatore proprio via JDBC** (`SHOW CREATE …` + INSERT a lotti) | invocare `mysqldump.exe`: non è detto sia presente sui PC d'aula, versioni diverse tra MySQL e MariaDB, e l'SQL non sarebbe «mostrabile» passo passo |
| Installer | **jpackage `--type app-image` + Inno Setup 6**: installazione per-utente senza privilegi, lingua italiana, associazioni file, disinstallazione; più uno **ZIP portabile** per i laboratori blindati | `jpackage --type msi/exe` diretto: richiede WiX, meno controllo su installazione per-utente e lingua. Launch4j + JRE esterno: reintroduce «installa Java». GraalVM native-image: Swing/AWT su Windows ancora delicato |
| Test | JUnit 5; integrazione contro server reali MariaDB **e** MySQL (D-03); smoke test UI con AssertJ-Swing solo sui flussi critici | — |

## 5. Come rendere il client «il più semplice possibile» per l'aula

Indicazione dell'utente: programma **«alla Apple» — minimale nell'aspetto, medio nelle funzioni, semplice nell'uso**. Una sola via ovvia per ogni azione, valori predefiniti giusti al posto delle opzioni, niente che non serva ai requisiti. Linee guida che informano `DESIGN.md`:

1. **Una finestra, tre zone fisse**: albero degli oggetti a sinistra, area di lavoro a schede al centro, **pannello SQL sempre visibile in basso**. Niente MDI, niente finestre flottanti, niente layout personalizzabili (meno modi di «perdersi» = meno interruzioni per il docente).
2. **L'SQL è sempre in vista** (requisito 5): ogni azione grafica genera SQL → anteprima → «Applica». Lo studente impara l'SQL *guardando* cosa fa il client. Il registro conserva tutto ciò che è stato eseguito, copiabile nell'editor.
3. **Solo ciò che serve a un corso di basi di dati**: niente amministrazione server, utenti/privilegi, performance, replica, migrazioni, **gestione delle transazioni** (forse dopo), routine/trigger/eventi in modifica (in v1 si vedono nell'albero in sola lettura, D-06).
4. **Reti di protezione**: conferma esplicita per `DROP`, `TRUNCATE`, `UPDATE`/`DELETE` senza `WHERE`; limite righe predefinito nelle griglie; messaggi d'errore del server mostrati per intero con una riga di spiegazione in italiano per gli errori più comuni (1062, 1451, 1452, 1064, 1045…).
5. **Distribuzione da aula**: il docente prepara un file di connessioni (senza password) che l'installer o lo studente importa con un clic; installazione senza diritti di amministratore; versione portabile su chiavetta.
6. **Zero rete non richiesta**: nessuna telemetria, nessun controllo aggiornamenti automatico (D-07).
7. **Italiano** come lingua dell'interfaccia; parole chiave SQL ovviamente in originale.

## 6. Compatibilità dichiarata

MariaDB **10.6 → 11.x/12.x** (LTS correnti) e MySQL **8.0 / 8.4 LTS** (9.x «best effort»). Differenze note da gestire nel generatore SQL e nei metadati: `CHECK` (MySQL ≥ 8.0.16, MariaDB ≥ 10.2), colonne generate, `utf8mb4` e collation predefinite diverse (`utf8mb4_0900_ai_ci` vs `utf8mb4_general_ci`/`uca1400`), tipo `JSON` (alias di `LONGTEXT` in MariaDB), `SEQUENCE` solo MariaDB (fuori perimetro), autenticazione `caching_sha2_password` (MySQL) vs `mysql_native_password`/`ed25519` (MariaDB). Il client rileva il server da `SELECT VERSION()` e adatta le opzioni offerte.

## 7. Rischi

| ID | Rischio | Prob. | Impatto | Mitigazione |
|---|---|---|---|---|
| R-01 | Il QB di SQLeo (2017, target Java 7) non compila o si comporta male su JDK 25 / HiDPI / FlatLaf | bassa | alto | **Spike S1/S5** prima di ogni altra cosa; ripiego JDK 21 |
| R-02 | Retroingegneria delle viste: MySQL/MariaDB **riscrivono** la definizione (`information_schema.VIEWS`: nomi qualificati, backtick ovunque, alias espliciti, parentesi nei JOIN) e `SQLParser` può non digerirla | **media** | medio | Spike S2 con un campionario di viste; normalizzatore prima del parser; in più il client **salva il sorgente originale** della vista nel progetto locale e lo preferisce; se il parsing fallisce la vista si apre nell'editor SQL (mai bloccare) |
| R-03 | `SQLParser` non copre costrutti comuni in aula (`LIMIT`, `UNION`, subquery, CTE `WITH`, funzioni finestra) | media | medio | Batteria di test; regola chiara: ciò che il diagramma non rappresenta si modifica in SQL grezzo, con avviso «non rappresentabile graficamente» |
| R-04 | Driver unico MariaDB verso MySQL 8.4 (autenticazione, metadati FK/indici) | media | basso | Spike S4; ripiego: doppio driver |
| R-05 | Installazione nei laboratori: niente admin, antivirus/SmartScreen che blocca un exe non firmato | **alta** | medio | Installer per-utente + ZIP portabile; valutare firma del codice (D-05); prova su un PC reale dell'aula nello spike S3 |
| R-06 | Qualità/manutenibilità del codice ereditato (stile 2004–2012, stato statico: `QueryBuilder.identifierQuoteString` ecc. è `static`) | certa | basso-medio | Confinarlo nel modulo `sqleo-qb` dietro facciata; **un solo QB attivo per finestra/scheda alla volta** o bonifica mirata degli static; non rifattorizzare oltre il necessario |
| R-07 | ALTER TABLE generato male = perdita di dati degli studenti | bassa | alto | Generatore DDL a differenze con test su tutti i casi; anteprima SQL obbligatoria; test d'integrazione andata/ritorno (crea → altera → rileggi) su entrambi i server |
| R-08 | Dump/restore non fedele (charset, BLOB, ordine FK, viste dipendenti) | media | medio | `SET FOREIGN_KEY_CHECKS=0`, `SET NAMES utf8mb4`, ordine: tabelle → dati → viste; BLOB in esadecimale; test di round-trip con confronto di checksum |
| R-09 | Obblighi GPL trascurati (sorgenti, intestazioni, note di modifica) | bassa | medio | Regola 6 di `CLAUDE.md`; `LICENSE`, `NOTICE` e «Informazioni su» con attribuzioni; sorgenti pubblicati insieme all'installer |

## 8. Decisioni aperte (da chiudere con l'utente)

| ID | Decisione | Proposta | Serve entro |
|---|---|---|---|
| D-01 | **Nome del prodotto** e identificativi (`groupId`, cartella d'installazione) | «RamaSQL Client», `it.ramasql` | Step 0 |
| D-02 | **Licenza GPL-3.0-or-later e sorgenti pubblici**: è una conseguenza obbligata dell'ereditare SQLeo; va solo confermato che è accettabile (per uso in aula lo è senz'altro) | accettare | Step 0 |
| ~~D-03~~ *(chiusa il 2026-09-21: MariaDB 11.5.2 su localhost:3306 e MySQL 8.0.40 su localhost:3307, entrambi servizi Windows sul PC di sviluppo; resta da creare l'utente `ramasql_test`; il PC dell'aula per S3 resta da procurare)* | **Server per sviluppo e test**: serve un MariaDB **e** un MySQL raggiungibili, con un utente che possa creare/distruggere cataloghi `ramasql_test_*`. Docker in locale? un server MariaDB già disponibile all'utente + un MySQL 8.4 da qualche parte? | Docker Desktop in locale con due container; in alternativa server indicati dall'utente | Step 1 |
| ~~D-04~~ | Repository git | **Chiusa il 2026-09-21:** GitHub, repository **pubblico** da subito, su indicazione dell'utente | — |
| D-05 | Firma del codice dell'installer (certificato OV ≈ 200–400 €/anno, o Azure Trusted Signing ≈ 10 €/mese) contro gli avvisi SmartScreen | partire senza; decidere dopo la prova in aula (S3) | Step 13 |
| D-06 | Routine, trigger, eventi: in v1 **sola lettura** nell'albero (si vede il `SHOW CREATE`), modifica solo via editor SQL | sì | Step 3 |
| D-07 | Controllo aggiornamenti: nessuno in v1 (il docente ridistribuisce l'installer) | nessuno | Step 13 |
| D-08 *(rinviata con la funzione: in v1 le password non si salvano)* | Password delle connessioni: non salvate per default; se l'utente spunta «ricorda», cifrate con **Windows DPAPI** (via JNA) e quindi leggibili solo da quell'utente su quel PC. Su PC condivisi d'aula può essere preferibile vietarlo del tutto da file di configurazione | come descritto, con interruttore «aula» che disabilita il salvataggio | Step 2 |
