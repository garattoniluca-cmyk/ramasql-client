# JOURNAL.md — Diario cronologico (più recente in alto)

## 2026-09-27 — Preparazione dell'esecuzione autonoma degli step 7–8 (`ADR-021`)

Richiesta dell'utente: lanciare di notte, in autonomia, gli step successivi. Chiesti prima fino allo Step 12, poi ridotti dall'utente **fino allo Step 8 compreso** (query editor visivo e viste grafiche). Preparati il contratto (`.claude/goal.md`, con le lezioni degli step 1–6 trasformate in regole), il giro di lavoro (`.claude/loop.md`) e `ADR-021`; adattamento deciso prima di partire: il campionario di query dello Step 7 lo costruisce l'agente, in attesa dell'elenco di esercizi dell'utente.
Resta da fare prima del lancio l'estensione di `scripts/verify.ps1` agli step 7–8 (soglie nuove congelate, controllo del diario fino allo step 8): senza di essa il verificatore controllerebbe solo gli step 1–6 e il goal risulterebbe raggiunto a vuoto. La modifica del verificatore richiede l'approvazione dell'utente.

## 2026-09-27 — Indicazioni dell'utente dopo la chiusura degli step 1–6

- **Password dei server locali**: sono solo di sviluppo e vanno mostrate all'utente quando servono (le avevo tenute fuori dalla chat per eccesso di zelo: la regola 8 riguarda git e i documenti, non il proprietario del PC). Provate tutte e quattro contro i server: funzionano. In produzione si useranno password robuste e un altro server → `DESIGN.md` §6.
- **Dati degli studenti**: solo nome e cognome; il resto è sulle piattaforme istituzionali; sul server solo lezioni e test, da consegnare e mostrare a fine anno. Nessun dato sensibile → `DESIGN.md` §6.
- **Suggerimenti (tooltip) su tutto il programma, anche sulle singole voci delle liste a discesa**, con un trafiletto completo che spiega la scelta: «fondamentale in un ambito come questo» → Step 12 (compito + test T12.9–T12.14), `DESIGN.md` §1-bis e §2, `DESIGN-SYSTEM.md` §3.9, `ADR-020`. Da decidere con l'utente se anticipare il meccanismo, così le schermate degli step 7–11 nascono già con i loro suggerimenti.

## 2026-09-23 — RESOCONTO dell'esecuzione autonoma degli step 1–6 (`ADR-014`)

**Esito: step 1, 2, 3, 4, 5 e 6 completati.** La **Tappa M1** della roadmap («client di base, già usabile in aula») è raggiunta: la biblioteca si costruisce dall'inizio alla fine **solo con il client** — catalogo, tabelle, indici, chiavi esterne, dati — e il registro SQL esportato, rieseguito su un catalogo vuoto, dà due cataloghi identici (metadati e `CHECKSUM TABLE`). Un commit per step: `Step 1` … `Step 6`. **Nessun push**: lo farà l'utente dopo la revisione.

### Output di `scripts\verify.ps1`

```
Credenziali di integrazione: caricate
[INFO] BUILD SUCCESS
Build Maven: OK
Test eseguiti: 1230 - falliti: 0 - saltati: 0

Step | superati (it)  | soglia (it) | diario       | esito
1    |   146 (  45)  |    8 (  4)  | completo     | PASS
2    |   122 (  17)  |    8 (  2)  | completo     | PASS
3    |   196 (  61)  |   12 (  4)  | completo     | PASS
4    |   323 (  12)  |   45 (  4)  | completo     | PASS
5    |   188 (  84)  |   60 ( 20)  | completo     | PASS
6    |   249 ( 126)  |   45 ( 12)  | completo     | PASS

VERIFY: PASS
```

### Spike dello Step 1: nessun NO-GO

S1 GO · S2a GO · S2b GO · S2c GO con riserve · S2d GO con riserve · S4 GO con riserve · S5 GO con riserve · S6 GO · S7 GO con riserve. Dettaglio in `docs/SPIKE-STEP1.md`; le riserve sono difetti aperti, tutti registrati in `docs/BUGS.md` e assegnati a uno step successivo.

### Il difetto di fondo trovato durante il lavoro (e corretto)

Gli step 4, 5 e 6 erano stati sviluppati «a componenti»: editor SQL, griglia di data-entry ed editor di tabelle avevano molti test, ma le loro interfacce verso il server (`SqlRunner`, `GridDataSource`, `TableApplier`, `CatalogTables`, `DataCheck`) erano implementate **solo da finte nei test**, e l'area di lavoro non apriva nessuna scheda: **nessun SQL nato da quei componenti aveva mai raggiunto un server**, e gli errori del server nelle prove erano stringhe scritte a mano. I test di tipo M, che per contratto (`.claude/goal.md`) devono «verificare sul server ciò che l'interfaccia dice di aver fatto», non erano quindi rispettati. È stato costruito il cablaggio di produzione (`ADR-019`) e **tutte** le righe M degli step 4–6 sono state riscritte in modo che piloti il **programma vero** contro MariaDB **e** MySQL, controllando gli esiti con una connessione separata. Da qui i difetti veri emersi (sotto) e il fatto che il programma adesso si può usare.

### La revisione indipendente degli step 4-6, e cosa ha trovato

Un sotto-agente revisore, diverso da chi ha scritto il codice, ha riletto tutto il cablaggio nuovo e i test nuovi contro `CLAUDE.md`, `DESIGN.md` e `ARCHITECTURE.md`. Ha trovato **un difetto bloccante, otto da correggere e sei debolezze nei test**: sono stati corretti tutti prima di chiudere lo Step 6.

Il bloccante meritava di essere trovato: i valori **binari** (BINARY, VARBINARY, i BLOB, BIT) si rovinavano nell'andata e ritorno. La griglia li mostra come `0x48656C6C6F`, ma il generatore li riscriveva **fra apici**, cioè salvava il testo «0x48656C6C6F» al posto dei cinque byte; e, peggio, una condizione `WHERE` su una **chiave binaria** (una PK `BINARY(16)` con un UUID: caso normale in aula) non trovava nessuna riga, il server rispondeva **OK con zero righe** e il client marcava la riga «salvata» senza aver cambiato niente. Corretto su tre fronti (`BUG-019`, chiuso): letterale `X'…'`, celle binarie non modificabili in v1, e un `UPDATE`/`DELETE` riuscito che non tocca nessuna riga ora è un'anomalia da segnalare, non un successo — una rete di sicurezza che serve anche quando è un altro utente a cambiare la riga sotto il naso.

Gli altri, tutti corretti: doppia *Conferma* mentre l'esecuzione era in corso (le stesse `INSERT` partivano due volte); `reload()`/cambio pagina che lanciavano un'eccezione sull'EDT se il server non rispondeva, lasciando l'interfaccia muta e il chiamante appeso; barra degli strumenti che non si aggiornava (*Interrompi* non si accendeva mai, *Nuova tabella* non si riabilitava) e che mentiva sui pulsanti spenti dicendo «Arriva in una prossima versione» per funzioni che invece esistono; chiusura della finestra che buttava via le modifiche in sospeso **senza chiedere niente**; `MetadataCatalogTables` che, ingoiando un errore di lettura, faceva sparire in silenzio il blocco della conversione a MyISAM (ora, se non si è potuto controllare, si blocca e si dice perché); «annullato dall'utente» e «fallito» indistinguibili a valle della pipeline; interruzione riportata come successo dall'editor di tabelle; chiusura di una scheda Query mentre gira, senza avviso.

Sui test, il revisore ha trovato tre asserzioni che passavano anche a funzione rotta — la più grave verificava la «spiegazione in italiano» del 1062 accettando la parola «Duplicate», che è il messaggio grezzo del server — un aiutante che dichiarava «salvata» una riga **sparita** dalla griglia, due Javadoc che promettevano più di quanto il test dimostrasse (T6.7 vale in pieno solo su MySQL, per `BUG-018`) e la convenzione fragile per cui un test fallito lasciava comunque in `test-results/` file che sembravano evidenze valide. Ora l'evidenza di un test fallito dice «Esito: FALLITO» con il motivo.

Restano aperti due punti segnalati e non ancora provati sui server: `BUG-020` (i `TIME` fuori dall'intervallo di un giorno, che MariaDB e MySQL ammettono) e `BUG-021` («Verifica dati» taglia a 200 righe senza dirlo).

### Decisioni prese dall'agente, da rivedere

| ADR | Cosa |
|---|---|
| `ADR-015` | Driver unico MariaDB Connector/J anche per MySQL |
| `ADR-016` | Parametri di connessione e connessione di servizio |
| `ADR-017` | Routine, trigger ed eventi in **sola lettura** (chiude D-06) |
| `ADR-018` | Comportamenti della griglia di data-entry |
| `ADR-019` | **Cablaggio delle schede e dei «porti» verso il server**: tutto passa dalla pipeline, anche le `SELECT` che riempiono la griglia (quindi si vedono nel registro); una sola regola di conversione dei valori in testo; finestre modali iniettabili; schede e domanda «Conferma, scarta o resta?» alla chiusura |

Altre scelte minori, dichiarate dove servono: l'utente `ramasql_test_sha2` creato con `root` sul MySQL locale nello Step 1 (unica operazione fatta con `root`); il test d'architettura T3.9 ammette **tre** classi che eseguono SQL (`SqlExecutor`, `MetadataQueries`, `InternalQueries`) e non due come dice la riga della roadmap.

### Difetti aperti (dettaglio in `docs/BUGS.md`)

Chiusi durante questo lavoro: `BUG-001` (licenze e icone ereditate), `BUG-002` (a-capo verso Excel), `BUG-019` (valori binari rovinati e falso «salvata», trovato dalla revisione).
Aperti e assegnati: `BUG-003` (Calc → client perde tab e a-capo in cella), `BUG-004` `BUG-006` `BUG-007` `BUG-011` (resa e facciata del query builder → Step 7), `BUG-005` (`SQLFormatter.sort` riordina i join → Step 7-8), `BUG-010` (normalizzatore delle viste → Step 8), `BUG-016` (le letture `DatabaseMetaData` del query builder non passano dal registro → Step 7), `BUG-017` (la griglia legge una pagina sull'EDT → Step 12), `BUG-021` («Verifica dati» taglia a 200 righe senza dirlo → Step 12), `BUG-020` (i `TIME` fuori dall'intervallo di un giorno, da provare sui server).
Da sapere, non difetti del client: `BUG-008` (MySQL 8.0.40, non 8.4), `BUG-009` (la verifica richiede Excel e LibreOffice), `BUG-012` (AUTO_INCREMENT dopo la riesecuzione del registro), `BUG-013` (su MySQL `ramasql_test` non crea funzioni e trigger), `BUG-014` (RESTRICT vs NO ACTION), `BUG-015` (connessione di servizio condivisa), **`BUG-018`**.

**`BUG-018` merita una riga a sé, perché cambia cosa si insegna in aula:** aggiungendo una chiave esterna a una tabella che ha **righe orfane**, MySQL rifiuta con l'errore 1452, **MariaDB la crea e le righe orfane restano**. Stesso client, stesso driver, stesso SQL (T6.7, provato su MariaDB 11.5.2 e MySQL 8.0.40). Su MariaDB l'unica difesa è il controllo **prima** del client («Verifica dati»), che su entrambi i server trova ed elenca le righe fuori posto.

### Test N da fare con Navicat (tocca all'utente)

I test automatici **distruggono i propri cataloghi** (`ramasql_test_*`) alla fine, quindi in Navicat non c'è nulla da guardare subito: la prova va **ricostruita a mano nel client**, che è anche il modo di provarlo. Il percorso più economico copre quasi tutti i controlli in una volta.

**Preparazione (nel client, ~15 minuti).** Avvia con `avvia.cmd`, connettiti al server, poi:
1. navigatore → tasto destro sul server → *Nuovo catalogo…* → `ramasql_test_navicat` (charset `utf8mb4`);
2. *Nuova tabella…* → crea `editori` (id INT UNSIGNED PK AI, nome VARCHAR(80) NOT NULL con indice UNIQUE, citta VARCHAR(60));
3. *Nuova tabella…* → `libri` (id PK AI, titolo VARCHAR(150) NOT NULL, isbn CHAR(13) con UNIQUE, anno SMALLINT UNSIGNED, prezzo DECIMAL(6,2) NOT NULL DEFAULT 0.00, id_editore INT UNSIGNED) con indice su `id_editore` e chiave esterna `fk_libri_editori` → `editori(id)` **ON DELETE RESTRICT ON UPDATE CASCADE**; commento della tabella «Catalogo dei libri»;
4. *Nuova tabella…* → `ordine dettagli` con colonne `order` (INT NOT NULL) e `select` (VARCHAR(45) DEFAULT 'nuovo');
5. *Apri tabella* su `editori` → inserisci 3 righe e premi **Conferma**; su `libri` → inserisci 2 righe, incollane altre da Excel, modificane una, eliminane una, **Conferma**;
6. prova a eliminare dal data-entry un editore che ha libri (deve arrivare l'errore **1451** spiegato);
7. *Esporta registro…* dal pannello SQL, per avere lo script di tutto ciò che hai fatto.

**Poi, in Navicat:**

| Test | Cosa guardare |
|---|---|
| **T3.10** | *Edit Database* su `ramasql_test_navicat`: charset e collation quelli scelti nel client. Rinomina, svuota ed elimina una tabella dal client e ricontrolla l'elenco in Navicat: deve corrispondere |
| **T4.23** | Apri `editori` e `libri`: le righe inserite, modificate ed eliminate **esattamente** come nel client; accenti, apostrofi, emoji al loro posto; le celle vuote sono **NULL** e non la stringa «NULL» |
| **T5.9** | *Design Table* su `editori`, `libri`, `ordine dettagli` → scheda **Fields**: nomi, tipi, lunghezze, Not Null, default, Auto Increment, Unsigned, commenti, chiave primaria identici a quanto impostato; scheda **Options**: engine, charset, collation, commento; **DDL** equivalente all'SQL mostrato dal client |
| **T6.12** | *Design Table* → schede **Indexes** e **Foreign Keys**: per ogni indice nome, colonne **nell'ordine giusto** e tipo (Normal/Unique); per ogni chiave esterna nome, campi, tabella e campi riferiti, **On Delete / On Update** identici a quanto scelto nel client |
| **T6.13** | Prova a violare l'integrità **da Navicat**: inserisci un libro con `id_editore` inesistente (atteso 1452) ed elimina un editore che ha libri (atteso 1451). Se Navicat riceve gli stessi errori, i vincoli sono veri sul server e non solo nel client |
| **T6.14** | *Reverse Database to Model* (o ER Diagram) sul catalogo: la relazione `libri → editori` deve comparire nel diagramma |

Se Navicat mostra qualcosa di diverso da ciò che il client dichiara, **è un difetto del client** (regola 2 di `CLAUDE.md`): va in `docs/BUGS.md`.

### Prove d'uso da fare con una persona

- **T2.9 «test dei 10 secondi»**: far connettere qualcuno che non ha mai visto il programma, senza istruzioni, entro 10 secondi. Procedura pronta in `test-results/step2/T2.9-procedura.md`.
- **Ctrl+V letterale in LibreOffice Calc** di un blocco copiato dal client (l'incolla via motore d'importazione di Calc è già provato; manca il Ctrl+V «a mano», `BUG-003`).
- **T7.11** (prova d'uso dell'editor visivo) arriverà con lo Step 7.

### Come provarlo, e cosa si può fare adesso

Doppio clic su **`avvia.cmd`** (compila e apre il programma; trova da sé il JDK 25). Oggi il client:

- **si connette** a MariaDB e MySQL da tessere, senza salvare le password, con diagnosi degli errori in italiano;
- **naviga** il server: cataloghi, tabelle con icona dell'engine, colonne, indici, chiavi esterne, viste, routine in sola lettura, filtro per nome;
- **mostra sempre l'SQL**: ogni operazione passa dall'anteprima e finisce nel **registro**, che si può filtrare ed esportare come `.sql` rieseguibile (comprese le `SELECT` che riempiono la griglia);
- **opera sull'albero**: crea/elimina cataloghi, rinomina/svuota/elimina tabelle, mostra l'SQL di creazione, con conferma rafforzata (riscrivere il nome) per `DROP` e `TRUNCATE`;
- **editor SQL**: evidenziazione, completamento dai metadati, esecuzione al cursore/selezione/script con `DELIMITER`, risultati multipli, *Interrompi*, errori del server spiegati in italiano con la riga evidenziata, apri/salva `.sql`;
- **data-entry**: griglia paginata e ordinabile (provata su **un milione di righe**), modifiche in sospeso con **Conferma**/**Scarta**, appunti su blocchi rettangolari compatibili con Excel, validazione per tipo, scheda record, esportazione CSV; nessuna scrittura implicita, mai;
- **editor di tabelle**: colonne, opzioni, engine InnoDB/MyISAM con avvisi, indici e chiavi esterne con i controlli **prima** e la **verifica sul server dopo**; esito parziale leggibile quando il server si ferma a metà, con rilettura dello stato reale.

Non c'è ancora (step successivi): editor visivo di query e viste, importazione CSV/JSON, dump, modello ER, installer. **Niente gestione delle transazioni** in v1: la connessione è sempre in autocommit (`ADR-010`).

---

## 2026-09-23 — Step 6: indici, chiavi esterne, integrità referenziale ✅ (esecuzione autonoma)

Ultimo degli step ad avere solo prove «di componente» per le righe M: le schede Indici e Chiavi esterne c'erano, ma nessun indice e nessuna chiave esterna nata dall'editor era mai arrivata a un server, e gli errori 1451/1452/1062 erano stringhe scritte nei test. Ora tutte le righe M passano dal **programma vero** contro **MariaDB e MySQL**, e ogni affermazione è ricontrollata con una connessione separata del test su `information_schema` (`STATISTICS`, `KEY_COLUMN_USAGE`, `REFERENTIAL_CONSTRAINTS`) e con `CHECKSUM TABLE`.

**Scoperta importante (differenza fra i due server, `BUG-018`).** Aggiungendo una chiave esterna a una tabella che ha **righe orfane**, MySQL 8.0.40 rifiuta con l'errore 1452, **MariaDB 11.5.2 la crea e le righe orfane restano**: il vincolo non protegge i dati già presenti. Stesso client, stesso driver, stesso SQL. Conseguenza didattica: su MariaDB l'unica difesa è il **controllo prima** del client («Verifica dati», `ADR-011`), che infatti trova ed elenca le righe orfane su entrambi i server. Da spiegare in aula; registrato in `docs/BUGS.md`.

Validazione (test con `@Tag("step6")`; evidenze in `test-results/step6/`):

| Test | Esito | Evidenza |
|---|---|---|
| T6.1 | ✅ | `TableDiffIndexTest` (21 test U): crea/elimina/rinomina INDEX, UNIQUE e PRIMARY, indici multi-colonna con l'ordine delle colonne, modifica delle colonne di un indice resa come `DROP` + `ADD` **nello stesso `ALTER`**; SQL atteso per entrambi i server, identificatori tra backtick |
| T6.2 | ✅ | `TableDiffForeignKeyTest` (36 test U): creazione con **tutte le 16 combinazioni** di `ON DELETE` × `ON UPDATE` (RESTRICT, CASCADE, SET NULL, NO ACTION), FK composta, eliminazione, modifica resa come `DROP FOREIGN KEY` **e poi** `ADD CONSTRAINT` nell'ordine corretto, FK autoreferenziale; sintassi `DROP FOREIGN KEY` giusta per MariaDB e MySQL |
| T6.3 | ✅ | `FkPrecheckTest` (33 U) + `T63PrecheckWarningsTest` (2 U): ogni caso produce il suo avviso **senza contattare il server** — INT vs INT UNSIGNED («Segno diverso: «id_socio» è INT UNSIGNED ma la colonna riferita «id» è INT…»), INT vs BIGINT, VARCHAR con collation diverse, colonna riferita senza indice, SET NULL su colonna NOT NULL, tabella MyISAM su uno dei due lati → `tableeditor-T6.3.txt` e 6 schermate `tableeditor-T6.3-*.png` |
| T6.4 | ✅ | `T64VerificaDopoTest` (it, **110 test** = 55 casi × 2 server) + `SchemaVerifierTest` (17 U): per ogni indice di T6.1 (I01–I20) e ogni FK di T6.2 (F01–F32, di cui F01–F16 le 16 combinazioni) l'oggetto viene applicato e riletto da `information_schema`: **52 casi su 55 «conforme»** e **3 casi alterati ad arte** (FK creata con azione diversa) in cui il verificatore **dichiara la differenza** — che è esattamente ciò che la riga chiede → `T6.4-mariadb.txt`, `T6.4-mysql.txt` |
| T6.5 | ✅ | `T65IntegritaRealeTest` (it, 8 test): struttura e FK generate dal client ed eseguite con `SqlExecutor`, poi il comportamento vero del server: con RESTRICT `DELETE` del padre → **1451**; `INSERT` del figlio orfano → **1452**; con CASCADE `DELETE` del padre → figli eliminati; con SET NULL → figli a NULL. Esiti presi da `StatementResult` e dal registro → `T6.5-mariadb.txt`, `T6.5-mysql.txt` |
| T6.6 | ✅ | `T66T67SulServerTest` (it, ui, 2 server) + `T66VerificationTest` (3 U): partendo da tabelle **nude**, con l'editor si creano `uq_libri_isbn` (UNIQUE), `ix_libri_editore` (INDEX) e `fk_libri_editori` in un solo `ALTER`; l'esito dice «✔ verificato sul server» e la verifica è vera: `information_schema` riporta i due indici e la FK con nome, tabella riferita, colonne e azioni (`ON DELETE RESTRICT ON UPDATE CASCADE`) **identici a quanto chiesto**; indici e chiavi esterne compaiono nel navigatore → `T6.6-T6.7-server-*.txt`, `T6.6-server-*.png` |
| T6.7 | ✅ | `T66T67SulServerTest` (it, ui, 2 server) + `T67T68DataCheckTest` (3 U): con 3 righe orfane, «Verifica dati» esegue davvero la query `SELECT figlia.* … LEFT JOIN … WHERE riferita.id IS NULL`, la mostra e elenca le **3 righe**, avvisando che il server rifiuterebbe con 1452. Eseguendo comunque: su **MySQL** errore **1452** spiegato e nessuna FK creata (come chiede la roadmap); su **MariaDB** il server **accetta** e le 3 righe orfane restano (`BUG-018`, verificato). Poi le 3 righe si correggono **dal data-entry del client** (eliminate con Conferma) e la chiave si crea e risulta «✔ verificato sul server» su **entrambi** i server → `T6.7-orfane-server-*.png`, `T6.7-creata-server-*.png` |
| T6.8 | ✅ | `T68T69SulServerTest` (it, ui, 2 server): UNIQUE su `soci.email` con 2 valori duplicati (e un NULL) → la query di controllo con `GROUP BY … HAVING` viene eseguita e mostra **2 valori duplicati**, avvisando dell'errore 1062; procedendo, il server rifiuta con **1062** spiegato («Valore duplicato: esiste già una riga…») e l'indice **non** viene creato (verificato in `information_schema.STATISTICS`) → `T6.8-T6.9-server-*.txt`, `T6.8-duplicati-server-*.png` |
| T6.9 | ✅ | `T68T69SulServerTest` (it, ui, 2 server) + `T69MyIsamTest` (1 U): su una tabella **MyISAM** la scheda Chiavi esterne mostra la spiegazione invece della tabella («La tabella … usa l'engine MyISAM, che non supporta le chiavi esterne… converti la tabella in InnoDB»), aggiungere una FK non fa niente, ed è offerto il pulsante di conversione; accettandolo la tabella diventa **InnoDB sul server** e la scheda torna utilizzabile → `T6.9-myisam-server-*.png`, `T6.9-convertita-server-*.png` |
| T6.10 | ✅ | `T610T611SulServerTest` (it, ui, 2 server): dal **data-entry**, eliminare un editore che ha libri → «[1451] Cannot delete or update a parent row… — Non si può eliminare o modificare questa riga: altre righe la usano attraverso una chiave esterna», la riga resta in griglia e sul server l'editore c'è ancora; eliminare un libro che ha autori (FK CASCADE) → il libro sparisce **e con lui le righe di `libri_autori`**, verificato sul server e **riaprendo la tabella nel client** → `T6.10-server-*.txt`, `T6.10-restrict-*.png`, `T6.10-cascade-*.png` |
| T6.11 | ✅ | `T610T611SulServerTest` (it, ui, 2 server) — **Tappa M1**: la biblioteca costruita **solo con il client** — catalogo creato dal navigatore, 4 tabelle (`editori`, `autori`, `libri`, `libri_autori`) con 2 indici UNIQUE e **3 chiavi esterne** (di cui 2 CASCADE su chiave primaria composta) dall'editor di struttura, dati dal data-entry: 3 editori e 3 libri digitati e **20 autori incollati a blocco** come da Excel — poi **registro esportato** come `.sql` (141 righe, `T6.11-registro-*.sql`), secondo catalogo creato dal client e script **rieseguito dall'editor SQL** (37 istruzioni, tutte OK). Confronto finale: per tutte e 4 le tabelle **metadati identici** (`TableDiff` = 0 istruzioni) e **`CHECKSUM TABLE` uguale** → `T6.11-server-*.txt`, `T6.11-server-*.png` |

Test N dello Step 6: **T6.12**, **T6.13**, **T6.14** da fare con l'utente in Navicat; istruzioni nel resoconto finale.

## 2026-09-23 — Step 5: editor di tabelle ✅ (esecuzione autonoma)

Anche qui la revisione ha trovato che l'editor di tabelle **non aveva mai parlato con un server**: `TableApplier`, `CatalogTables` e `DataCheck` erano implementati solo da finte nei test, gli errori del server (1265, 1050…) erano stringhe scritte a mano dentro i test, e l'editor non era raggiungibile dall'interfaccia. Aggiunto il cablaggio di produzione (nel commit dello Step 4 per la parte comune):

- **`PipelineTableApplier`**: manda le istruzioni dell'editor alla pipeline «anteprima SQL» e, finita l'esecuzione, **rilegge la tabella dal server** con il `MetadataReader` — anche quando qualcosa è andato storto, così l'editor mostra lo stato reale e non quello che credeva.
- **`MetadataCatalogTables`**: le altre tabelle del catalogo lette dal canale dei metadati, per scegliere la tabella riferita da una chiave esterna e per sapere chi riferisce la tabella in modifica (blocco di MyISAM) **senza eseguire SQL**.
- **`PipelineDataCheck`**: «Verifica dati» esegue la sua `SELECT` dalla pipeline, quindi la query che cerca orfani o duplicati **si vede** nell'anteprima e nel registro.
- L'editor si apre dal navigatore («Progetta tabella…», «Nuova tabella…») e dal pulsante *Nuova tabella*.

**Difetto trovato dai nuovi test e corretto:** «Nuova tabella…» riusava sempre la stessa scheda, quindi non si potevano creare due tabelle nuove di fila e, dopo aver applicato, quella scheda restava «nuova». Ora ogni tabella nuova apre una scheda propria e il titolo della linguetta segue il nome della tabella. Aggiunte anche le spiegazioni in italiano mancanti per gli errori **1265**, **1406**, **3780** e **1832** (il server rifiutava e il client non sapeva spiegare perché).

Validazione (test con `@Tag("step5")`; evidenze in `test-results/step5/`):

| Test | Esito | Evidenza |
|---|---|---|
| T5.1 | ✅ | `TableDiffColumnsTest` (84 test U): **82 casi** parametrici (la roadmap ne chiede ≥ 60; il test `iCasiSonoAlmenoSessanta` fa fallire la build sotto quella soglia), ognuno con l'SQL atteso per MariaDB e per MySQL e il controllo «identificatori sempre tra backtick». Coperto tutto l'elenco: nuova tabella, aggiungi/rimuovi/rinomina colonna, cambio tipo/lunghezza/NULL/default (`CURRENT_TIMESTAMP`, stringa vuota, NULL), AI, UNSIGNED, commento, PK semplice e composta, engine, charset/collation, rinomina tabella, combinazioni, nomi con spazi e parole riservate, più 8 casi «nessuna modifica → 0 istruzioni» |
| T5.2 | ✅ | `T52AndataRitornoTest` (it, **78 test** = 39 casi × 2 server, contro i 20 chiesti): per ogni caso `CREATE` + `ALTER` applicati con `SqlExecutor` e metadati riletti → 39/39 «riletta = attesa (equals): sì» e 39/39 «diff(riletta, attesa): 0 istruzioni» su MariaDB 11.5.2 e MySQL 8.0.40 → `T5.2-mariadb.txt`, `T5.2-mysql.txt` |
| T5.3 | ✅ | `T53ElementiAvanzatiTest` (it, 2 test): tabella `misure` creata via script con 2 CHECK, 2 colonne generate (STORED e VIRTUAL) e `PARTITION BY HASH … PARTITIONS 4`; modificando un'altra colonna il generatore emette **solo** il `MODIFY` di `nota`, e `SHOW CREATE TABLE` resta identico riga per riga (10/10 su MariaDB, 11/11 su MySQL, tolta la riga di `nota`), con 5 elementi avanzati identici prima e dopo → `T5.3-mariadb.txt`, `T5.3-mysql.txt` |
| T5.4 | ✅ | `T54T58SulServerTest` (it, ui, 2 server) + `T54CreateBibliotecaTest` (2 U): `editori` e `libri` costruite da zero cella per cella nell'editor del programma e **applicate sul server**; l'anteprima viva coincide **carattere per carattere** con le righe del **registro** (confronto voce per voce, esito OK) e con l'SQL eseguito; la tabella riletta dal server dà `TableDiff(riletta, voluta) = 0 istruzioni`; l'esito dice «✔ verificato sul server»; il commento «Catalogo dei libri» si rilegge da `information_schema` → `T5.4-T5.8-server-*.txt`, `T5.4-editori-server-*.png` |
| T5.5 | ✅ | `T55T56T57AlterSulServerTest` (it, ui, 2 server) + `T55TruncationTest` (3 U): `libri.titolo` da VARCHAR(150) a VARCHAR(20) con titoli più lunghi → avviso **prima** («⚠ titolo: passando da VARCHAR(150) a VARCHAR(20) i valori più lunghi di 20 caratteri potrebbero essere troncati o rifiutati dal server») e conferma esplicita; **rifiutata → 0 istruzioni**; accettata → **il server rifiuta davvero** («✘ Errore 1406: Data too long for column 'titolo' at row 1» + «Un valore non entra nella colonna: è troppo lungo per il tipo scelto…»), la colonna sul server è ancora VARCHAR(150) e l'editor ha ricaricato lo stato reale → `T5.5-T5.6-T5.7-server-*.txt`, `T5.5-avviso-server-*.png`, `T5.5-errore-server-*.png` |
| T5.6 | ✅ | `T55T56T57AlterSulServerTest` (it, ui, 2 server) + `T56PartialApplyTest` (2 U): piano di **3 istruzioni** (RENAME TABLE, ALTER DROP FOREIGN KEY, ALTER MODIFY `data_prestito` a TINYINT) con fallimento **reale** del server: «✘ Istruzioni applicate: 2 su 3; il server si è fermato alla n. 3», elenco Applicata/Non riuscita, errore 1264 del server; sul server `prestiti_storico` esiste e `prestiti` no (le due prime sono passate), e l'editor ha ricaricato lo stato reale (`data_prestito` = DATE, non TINYINT) → `T5.6-server-*.png`. *Nota:* nel piano reale l'istruzione che fallisce è la 3ª e non la 2ª come nell'esempio della roadmap, perché il generatore mette `RENAME TABLE` e `DROP FOREIGN KEY` prima delle modifiche di colonna; ciò che la riga chiede — esito parziale visibile e stato reale ricaricato — è dimostrato |
| T5.7 | ✅ | `T55T56T57AlterSulServerTest` (it, ui, 2 server) + `T57EngineTest` (4 U): `editori` → MyISAM **bloccata** («MyISAM non supporta le chiavi esterne. È riferita dalle chiavi esterne di altre tabelle: libri.fk_libri_editori»), engine sul server ancora InnoDB e **0 istruzioni** eseguite; `note_libere` (libera) convertita per davvero InnoDB → MyISAM → InnoDB, verificato in `information_schema.TABLES`, e **l'icona del navigatore segue l'engine** a ogni passaggio (`NavigatorIcons.TABLE_MYISAM` / `TABLE_INNODB`) → `T5.7-bloccata-server-*.png`, `T5.7-myisam-server-*.png` |
| T5.8 | ✅ | `T54T58SulServerTest` (it, ui, 2 server) + `T58ReservedNamesTest` (1 U): tabella **«ordine dettagli»** con colonne **«order»** e **«select»** creata sul server; `information_schema.COLUMNS` conferma le due colonne; una riga di prova scritta con `` `order` `` = 7 legge `select` = 'nuovo' (il DEFAULT è arrivato al server) → `T5.8-server-*.png` |

Test N: **T5.9** da fare con l'utente in Navicat. I cataloghi dei test si distruggono alla fine, quindi la prova va rifatta a mano nel client: istruzioni nel resoconto finale.

## 2026-09-23 — Step 4: editor SQL e data-entry ✅ (esecuzione autonoma)

**Difetto di fondo trovato dalla revisione e corretto qui.** I componenti dello Step 4 c'erano già, con molti test, ma erano **staccati dal server**: `SqlRunner`, `GridDataSource` e le finestre modali avevano solo implementazioni finte nei test, e l'area di lavoro non apriva nessuna scheda. Nessun SQL nato dall'editor o dalla griglia aveva mai raggiunto un server, quindi la regola dei test M («si automatizzano… e poi **verificano sul server** ciò che l'interfaccia dice di aver fatto», `.claude/goal.md`) non era rispettata. Costruito il cablaggio di produzione:

- **`TableGridDataSource`**: legge una pagina passando dal `SqlExecutor` (`SELECT` con le colonne nominate una per una — mai `SELECT *` — e `LIMIT righePerPagina + 1` per sapere se c'è un'altra pagina), quindi **ogni lettura finisce nel registro** e in aula si vede. In `SqlExecutor` è stato aggiunto il limite di righe *per singola lettura* (`run(script, listener, righe)`): la griglia pagina per conto suo e il limite generale dell'editor non va toccato.
- **`GridApplier`**: la *Conferma* diventa `DmlGenerator` → anteprima → esecuzione **riga per riga** in autocommit; le righe riuscite diventano lo stato del server, quella fallita resta in sospeso con l'errore del server **spiegato in italiano**, le successive intatte. Se tutte riescono la pagina si rilegge, così i valori calcolati dal server (`AUTO_INCREMENT`, `DEFAULT`) si vedono in griglia.
- **`PipelineSqlRunner`**: l'editor SQL esegue dalla pipeline e riceve l'esito di ogni istruzione mentre lo script va avanti; *Interrompi* è il `KILL QUERY` della connessione di servizio.
- **`WorkTabs`** e le voci del navigatore «Apri tabella», «Apri vista», «Progetta tabella…», «Nuova tabella…», più i pulsanti *Nuova query*, *Nuova tabella*, *Esegui*, *Interrompi* accesi quando hanno senso: da qui **il programma si può usare davvero**. Chiudere una scheda con modifiche in sospeso chiede «Conferma, scarta o resta?».
- **`ResultCells`**: una sola regola per convertire in testo i valori letti dal server, usata dalla griglia e dall'editor — se lettura e scrittura non usassero la stessa forma, un andata-e-ritorno cambierebbe le date.
- Le finestre modali delle schede arrivano da `WorkspacePrompts.gridPrompts()/editorPrompts()/tableEditorPrompts()`: nei test sono finte, così **nessun test apre finestre vere**.
- I supporti dei test d'interfaccia sono diventati il pacchetto condiviso `it.ramasql.app.servertest` (`DbServer`, `ClientApp`, `Probe`), usato dagli step 3-6: pilotano il **programma vero** (finestra mai mostrata) contro i due server e controllano l'esito con una connessione separata.

Validazione (test con `@Tag("step4")`; evidenze in `test-results/step4/`; i numeri delle misure sono quelli dell'ultima esecuzione di `scripts\verify.ps1`):

| Test | Esito | Evidenza |
|---|---|---|
| T4.1 | ✅ | `StatementSplitterTest` (33 test U): i 30 casi chiesti — `;` dentro stringhe e commenti, `DELIMITER //`, commenti `--`, `#`, `/* */`, backtick, script vuoto, ultima istruzione senza `;` — sul lessico di `SqlLexer`; il blocco `DELIMITER` con procedura è poi provato **sul server** in T4.2 |
| T4.2 | ✅ | `T42T43T46EditorSulServerTest` (it, 2 server) + `T42ExecuteTest` (8 U): script di **50 istruzioni** (1 CREATE, 30 INSERT, 5 UPDATE, 1 ALTER, 3 SELECT, una procedura in blocco `DELIMITER`, una CALL, 8 SELECT) eseguite **tutte e in ordine** in poche decine di millisecondi, 12 risultati tabellari, un esito per istruzione nella tabella dell'editor (righe lette / righe interessate / durata), procedura `conta_per_anno` verificata in `information_schema.ROUTINES` → `T4.2-T4.3-T4.6-server-*.txt`, `T4.2-server-*.png`, `editor-T4.2-script50*.png` |
| T4.3 | ✅ | `T42T43T46EditorSulServerTest` (it, 2 server) + `T43CancelTest` (5 U): `SELECT SLEEP(30)` → *Interrompi* → interrotto in **meno di 20 ms** (limite 2000; la cifra esatta della corsa è nell'evidenza); subito dopo `SELECT COUNT(*) FROM soci` dà 100, uguale al server: la sessione resta utilizzabile → `T4.3-server-*.png` |
| T4.4 | ✅ | `T44CompletionTest` (10 U): `SELECT * FROM li` + Ctrl+Spazio propone `libri` e `libri_autori`; `libri.` propone le colonne di `libri`; nessuna proposta dentro stringhe e commenti → `editor-T4.4-li.txt`, `editor-T4.4-libri-punto.txt` e le due PNG. Nel programma il completamento è alimentato da `MetadataCompletionSource` (canale metadati, non esegue SQL dell'utente) |
| T4.5 | ✅ | `T45ConfirmDestructiveTest` (28 U): `UPDATE soci SET nome='x'` senza WHERE, `DELETE` senza WHERE, `DROP TABLE`, `DROP DATABASE`, `TRUNCATE`, `ALTER … DROP COLUMN` → conferma sempre chiesta e, se rifiutata, **0 istruzioni consegnate**; `DROP`/`TRUNCATE` chiedono di riscrivere il nome → `editor-T4.5-conferme.txt`, `editor-T4.5-conferma-rafforzata.txt` |
| T4.6 | ✅ | `T42T43T46EditorSulServerTest` (it, 2 server) + `T46ErrorTest` (6 U) + `ErrorExplainerTest` (16 U): `SELEC * FROM …` → **errore 1064** del server riportato per intero, più «In parole semplici: l'SQL non è scritto correttamente…», più «alla riga 2» e il testo evidenziato nell'editor → `T4.2-T4.3-T4.6-server-*.txt`, `T4.6-server-*.png` |
| T4.7 | ✅ | `T47GrandeTabellaTest` (it, 2 server): tabella da **1 000 000 di righe**; prima pagina in **meno di 10 ms** su entrambi i server (limite 1000 ms), pagina 2 in pochi millisecondi, ordinamento su colonna **senza indice** circa **200-260 ms**; memoria **57-58 MB** (limite 300); in griglia **1000 righe su 1 000 000** e `LIMIT 1001` nel registro → `T4.7-mariadb.txt`, `T4.7-mysql.txt`, `T4.7-*.png` |
| T4.8 | ✅ | `DmlGeneratorTest` (21 U) e `SqlLiteralsTest` (21 U): INSERT con DEFAULT e AUTO_INCREMENT omessi, UPDATE delle sole colonne cambiate, WHERE su PK semplice e composta e su UNIQUE in assenza di PK, NULL, apostrofi, emoji, DECIMAL, DATE; un test controlla che **nessuna istruzione di transazione** venga mai generata (`ADR-010`) |
| T4.9 | ✅ | `T49T412PendentiSulServerTest` (it, 2 server) + `T49NoImplicitWriteTest` (U): 3 inserimenti e 1 modifica, poi *ordina* e *cambia pagina* (entrambi **rifiutati** con l'avviso «Prima conferma o scarta le modifiche in sospeso…») → **0 istruzioni di scrittura nel registro**, sul server le righe sono ancora 100 e la prima riga è identica campo per campo; contatore «3 inserimenti · 1 modifica · 0 eliminazioni in sospeso» → `T4.9-T4.12-server-*.txt`, `T4.9-server-*.png` |
| T4.10 | ✅ | `T410T411ConfermaSulServerTest` (it, 2 server) + `T410T411ConfirmFlowTest` (2 U): 3 modifiche + 1 inserimento + 1 eliminazione → anteprima con **esattamente 5 istruzioni** (1 DELETE, 3 UPDATE, 1 INSERT) e **nessuna** `START TRANSACTION`/`BEGIN`/`COMMIT`/`ROLLBACK`/`SAVEPOINT`; dopo l'esecuzione la riga eliminata non c'è più sul server, la nuova ha l'`id` assegnato dal server e il cognome modificato si rilegge; l'`id` AUTO_INCREMENT compare in griglia → `T4.10-T4.11-server-*.txt`, `T4.10-anteprima-*.png`, `T4.10-dopo-*.png` |
| T4.11 | ✅ | `T410T411ConfermaSulServerTest` (it, 2 server): 3 inserimenti con il 2º a `tessera` duplicata → 1º **salvato**, 2º **in errore** «[1062] Duplicate entry 'T500001' for key 'uq_soci_tessera' — Valore duplicato: esiste già una riga con lo stesso valore…», 3º **ancora in sospeso**; sul server è entrata solo la prima. Corretta la tessera, la Conferma successiva scrive tutto e le tre tessere si rileggono → `T4.11-errore-*.png` |
| T4.12 | ✅ | `T49T412PendentiSulServerTest` (it, 2 server): *Scarta* → nessuna modifica in sospeso, **0 istruzioni**, e la pagina riletta dal server coincide campo per campo con i dati del server; chiudendo la scheda con una modifica in sospeso la domanda è «Ci sono modifiche in sospeso (…). Conferma, scarta o resta?» — «Resta» lascia la scheda aperta con la modifica al suo posto, «Scarta» chiude senza scrivere niente (registro a 0, righe sul server invariate) |
| T4.13 | ✅ | `T413T414VincoliESolaLetturaTest` (it, 2 server): inserimento in `prestiti` con `id_socio = 999999` → «[1452] … — Il valore non esiste nella tabella riferita dalla chiave esterna: prima va inserita la riga «padre»»; la riga **resta in griglia come inserimento in sospeso** e sul server non entra nulla; corretto l'`id_socio`, la stessa riga si salva → `T4.13-T4.14-server-*.txt`, `T4.13-*.png` |
| T4.14 | ✅ | `T413T414VincoliESolaLetturaTest` (it, 2 server) + `T414ReadOnlyTest` (2 U): `note_libere` (nessuna PK, nessun UNIQUE) → Conferma spenta e spiegazione «…il client non saprebbe quale riga modificare. Puoi copiare i dati.»; vista `v_prestiti_aperti` aperta in sola lettura con la sua spiegazione; **copia consentita**, blocco 2×2 verificato negli appunti → `T4.14-senza-chiave-*.png`, `T4.14-vista-*.png` |
| T4.15 | ✅ | `ClipboardBlockTest` (19 U): blocco → testo tabulato → blocco con tabulazioni, a-capo, virgolette, NULL e stringa vuota; andata e ritorno senza alterazioni, convenzione Excel (virgolette raddoppiate, campo quotato quando serve) |
| T4.16 | ✅ | `T416BlockSelectionCopyTest` (2 U) + `Bug002SystemClipboardTest` (1 U): blocco 3×4 in mezzo alla griglia e *Copia con intestazioni* (4×4) → `T4.16-copia.txt`, `T4.16-selezione.txt`, `T4.16-blocco-3x4.png`; l'a-capo dentro una cella arriva come solo LF grazie al flavor legato a `UNICODE TEXT` (`BUG-002` **chiuso**), letto **da un altro processo** → `BUG-002-appunti-di-sistema.txt`. L'incolla in **Excel e Calc reali** è provato nello spike S7 dello Step 1 (`test-results/step1/S7-excel-incolla.txt`, `S7-calc-incolla.txt`) con lo stesso `BlockTransferable`; resta da fare con l'utente il Ctrl+V letterale in Calc |
| T4.17 | ✅ | `T417IncollaSulServerTest` (it, 2 server) + `T417PasteNewRowsTest` (U): blocco **20×3** dagli appunti sulla riga d'inserimento di `autori` → contatore «20 inserimenti · 0 modifiche · 0 eliminazioni in sospeso», anteprima con **20 INSERT**, e sul server **20 righe in più** (50 → 70) con apostrofi (`Nicolò D'Angiò`) e accenti (`italiana è così`) integri → `T4.17-server-*.txt`, `T4.17-incollate-*.png`, `T4.17-anteprima-*.png`, `T4.17-dopo-*.png` |
| T4.18 | ✅ | `T418PasteOverExistingTest` (1 U): blocco 2×2 sopra celle esistenti → celle sovrascritte come pendenti (→ `UPDATE`); un valore su una selezione 5×1 → 5 celle riempite; blocco più largo della tabella → eccedenza scartata con l'avviso «…colonne in più scartate» → `T4.18-incolla-sopra.txt`, `T4.18-incolla-sopra.png` |
| T4.19 | ✅ | `T419ValidationAndUndoTest` (1 U) + `ValueValidatorTest` (81 U): incollati «2026-02-30», «12.50», «abc» in colonne DATE/DECIMAL/INT → **2 celle non valide** segnate in rosso con il perché («Data non valida…», «serve un numero intero»), Conferma bloccata (`onConfirm` non chiamato); Ctrl+Z ritira **l'intero incolla** (6 celle) → `T4.19-validazione.txt`, `T4.19-celle-non-valide.png`, `T4.19-corrette.png` |
| T4.20 | ✅ | `T420CutClearNullTest` (2 U): Ctrl+X su un blocco 2×5 → negli appunti il blocco originale, celle svuotate con NULL dove ammesso e stringa vuota sulla NOT NULL (`UPDATE … SET nome = '', email = NULL …`); Ctrl+Z ripristina; Canc sulla riga nuova lascia le celle **non impostate**, e l'INSERT le omette → `T4.20-taglia-canc.txt`, `T4.20-null.txt`, 2 PNG |
| T4.21 | ✅ | `T421RecordFormTest` (1 U): scheda record «Record 3 di 12», modifica visibile in griglia, «Nuovo» → 6 campi → «Record 13 di 13», valore non valido con contorno rosso e Conferma spenta, Ctrl+S dalla scheda → `UPDATE` + `INSERT` identici a quelli della griglia, Elimina dalla scheda → «1 inserimento · 1 modifica · 1 eliminazione in sospeso» → `T4.21-scheda-record.txt`, 2 PNG |
| T4.22 | ✅ | `T422ExportCsvTest` (1 U): CSV riletto dal disco (695 byte) con **BOM UTF-8** (`EF BB BF`), separatore «;», decimali con la virgola, righe CR+LF, campi quotati quando contengono «;», tabulazioni o a-capo → `T4.22-esporta-csv.txt` e il file `T4.22-soci.csv` |

Difetti registrati: `BUG-017` (la griglia legge una pagina sull'EDT; misurato in T4.7: oggi il blocco è breve). Test N: **T4.23** da fare con l'utente in Navicat; i cataloghi dei test si distruggono alla fine, quindi la prova va rifatta a mano nel client — istruzioni nel resoconto finale.

## 2026-09-23 — Step 3: navigatore, pipeline SQL, pannello SQL ✅ (esecuzione autonoma)

Ripreso il lavoro interrotto il 2026-09-22 (vedi la voce di PAUSA più sotto): il codice compilava e tutti i test passavano; mancavano le voci di diario. Rimosso un residuo che falsava il conteggio: il worktree `.claude/worktrees/agent-a8bc4347cb3b56e8a` (pulito, HEAD `ae5e8be` già in cronologia) conteneva un `core/target/test-report/open-test-report.xml` **vecchio** che `scripts/verify.ps1` avrebbe contato insieme a quelli veri, gonfiando i totali di ogni step.

Fatto:
- **core.metadata**: `MetadataReader` (canale interno, con cache e invalidazione), `MetadataQueries` (unico punto che interroga `information_schema`), `MetadataNormalizer` (differenze MariaDB/MySQL), modello `TableDef`/`ColumnDef`/`IndexDef`/`ForeignKeyDef`/`ViewDef`/`TableSummary`/`CatalogInfo`/`CollationInfo`/`RoutineInfo`, routine, trigger ed eventi **in sola lettura** (`ADR-017`, chiude D-06).
- **core.exec — pipeline «anteprima SQL»**: `SqlLexer`/`StatementSplitter`/`SqlScript`/`SqlStatement` (separazione delle istruzioni), `RiskClassifier` con `RiskLevel` SAFE/MODIFIES/DESTRUCTIVE, `ConfirmationPolicy` (conferma rafforzata con riscrittura del nome), `SqlExecutor` come **unico** esecutore, `SqlLog` con origine/esito/durata ed esportazione `.sql` rieseguibile, `DdlTargets` per invalidare la cache dopo il DDL, `ScriptResult`/`StatementResult`/`ResultTable`.
- **core.sqlgen**: `ObjectDdl`, `TreeScripts`, `SqlIdentifiers` (backtick sempre), per le operazioni sull'albero.
- **app**: navigatore ad albero con caricamento pigro, icone per engine, filtro per nome; finestra di anteprima e `SqlPipeline` (non esiste un metodo che esegua senza anteprima); pannello Registro/Anteprima/Messaggi con filtro, ordinamento ed esportazione; `SessionWorkspace` come cablaggio di sessione; sistema visivo di `docs/DESIGN-SYSTEM.md` (token, icone disegnate nel codice, temi) con i suoi test.
- **it-tests**: fixture `biblioteca` e `biblioteca_myisam` (InnoDB e MyISAM) con JSON di riferimento, usate da qui in avanti.

Validazione (test con `@Tag("step3")`: **196 superati, di cui 61 di integrazione**; evidenze in `test-results/step3/`):
| Test | Esito | Evidenza |
|---|---|---|
| T3.1 | ✅ | `T31MetadatiBibliotecaTest` (3 it): metadati di `biblioteca` e `biblioteca_myisam` confrontati con `fixtures/*.expected.json` → `T3.1-mariadb.txt` (11.5.2-MariaDB) e `T3.1-mysql.txt` (8.0.40), 677 righe ciascuno, «esito: UGUALE al riferimento» per entrambe le fixture, «TableDiff.diff(letto, letto): 0 istruzioni»; `T3.1-confronto-server.txt`: 6 tabelle per fixture, `TableDef` identici (equals) sui due server |
| T3.2 | ✅ | `T32CatalogoGrandeTest` (2 it): catalogo con 500 tabelle, elenco letto con **una sola** interrogazione di `information_schema.TABLES` → `T3.2-mariadb.txt` 500 righe in **80 ms**, `T3.2-mysql.txt` 500 righe in **2 ms**, limite 2000 ms, «esito: SUPERATO» su entrambi |
| T3.3 | ✅ | `T33NavigatorTest` (2 it) + `NavigatorSpecialIndexesTest` (2 it): `T3.3-mariadb.txt`/`-mysql.txt` — 6 tabelle InnoDB (icona `table.innodb`) e 6 MyISAM (`table.myisam`), colonne/indici/FK sotto ogni tabella (`libri`: 6 colonne, 3 indici, FK `fk_libri_editori`), 2 viste e routine in sola lettura, cataloghi di sistema nascosti (`information_schema, mysql, performance_schema, sys`), filtro «libri» → 4 tabelle, registro 0 istruzioni; schermate `T3.3-*.png`, `T3.3-filtro-*.png`, `T3.3-icone-*.png` |
| T3.4 | ✅ | `RiskClassifierTest` (57 test U): il campionario contiene **esattamente 30** istruzioni (11 SAFE, 10 MODIFIES, 9 DESTRUCTIVE), controllato dal test `sonoEsattamenteTrentaIstruzioni`; più `casiParticolari` (`ALTER … DROP`, `DROP PARTITION`, `CREATE OR REPLACE`, CTE con `DELETE`, `UPDATE` con `@where`) e `involucri` (24 casi: `SET STATEMENT … FOR`, `ANALYZE`, `EXPLAIN ANALYZE`), con `ConfirmationPolicyTest` 15, `WrappedStatementsTest` 15, `DdlTargetsTest` 4; prova sul server in `involucri-mariadb.txt`/`-mysql.txt` |
| T3.5 | ✅ | `T35CancelTest` (2 it): anteprima «DROP TABLE \`…\`.\`libri\`;» con conferma STRONG (riscrivere «libri») e scelta **CANCEL**; verifica da connessione separata del test: `libri` presente, **200 righe** come prima; registro **0 istruzioni**; Messaggi «annullato, nulla è stato eseguito» → `T3.5-mariadb.txt`, `T3.5-mysql.txt`, `T3.5-*.png`, `anteprima.png` |
| T3.6 | ✅ | `T36OperationsTest` (2 it) + `SqlExecutorServerTest` (16 it): `T3.6-interfaccia-*.txt` — 6 operazioni, ognuna con anteprima e riga di registro «Navigatore» con esito e durata: `CREATE DATABASE … CHARACTER SET latin1 COLLATE latin1_swedish_ci` (verificato in `SCHEMATA`), `RENAME TABLE` 4 ms, `TRUNCATE` 4 ms (`COUNT(*)`=0), `DROP TABLE` 4 ms, `DROP TABLE editori` **errore 1451 spiegato** («0 su 1 applicate»), `DROP DATABASE` 13 ms; albero aggiornato da solo → `T3.6-1…6-*.png`, `T3.6-registro-*.png` |
| T3.7 | ✅ | `T37StrongConfirmationTest` (2 it) + `ConfirmationPolicyTest` (15 U): per `DROP TABLE libri`, `TRUNCATE prestiti` e `DROP DATABASE` il livello è STRONG e **4 nomi sbagliati** ciascuno (troncato, maiuscolo, con suffisso, «CONFERMO») lasciano *Esegui* disabilitato e il clic senza effetto; col nome esatto si abilita. Dopo 3 anteprime annullate: 0 istruzioni nel registro, `prestiti` ancora 500 righe → `T3.7-mariadb.txt`, `T3.7-mysql.txt`, 6 PNG |
| T3.8 | ✅ | `T38ExportReplayTest` (2 it): 15 istruzioni di fixture + 4 operazioni dal navigatore → registro esportato `T3.8-registro-interfaccia-*.sql` (**1224 righe**, `USE` e l'istruzione fallita commentate) → rieseguito su un catalogo vuoto: **17 istruzioni tutte OK**, `TableDef` uguali per 5 tabelle (AUTO_INCREMENT compreso: editori 21, libri 201, scrittori 51, soci 101), 2 viste, `CHECKSUM TABLE` origine = copia → `T3.8-confronto-*.txt` («esito: STESSO RISULTATO»), `T3.8-senza-catalogo-*.txt` («ORIGINE MAI TOCCATA») |
| T3.9 | ✅ | `T39ArchitetturaSqlTest` (3 test U): `T3.9-sorgenti.txt` — 212 file `.java` di core/app/model/sqleo-qb, commenti e stringhe esclusi: `execute(`/`executeQuery(`/`executeUpdate(` **solo** in `SqlExecutor`, `MetadataQueries` e `InternalQueries`. `T3.9-bytecode.txt` — 436 classi, 165 riferimenti a `java.sql.*`/`org.mariadb.*`: creazione ed esecuzione di istruzioni solo nelle 3 classi ammesse, tutto il resto in un elenco esplicito di sole letture, «VIOLAZIONI: nessuna» |

**Due precisazioni sul T3.9, perché la riga della roadmap dice meno di quello che il test ammette:**
1. le classi ammesse sono **tre**, non due: `SqlExecutor` (SQL dell'utente), `MetadataQueries` (canale metadati) e `InternalQueries` (canale interno della sessione: versione del server, `KILL QUERY`, catalogo corrente). È la struttura di `ARCHITECTURE.md` §4-5, ma non è ciò che si legge nella riga T3.9: la riga resta invariata e la differenza è dichiarata qui;
2. il query builder ereditato (`sqleo-qb`) legge i metadati con `DatabaseMetaData.getTables/getColumns/getImportedKeys`: l'SQL lo compone il driver, quindi **non passa dall'anteprima né dal registro**. Il test lo mette in un elenco esplicito di letture consentite. Registrato come `BUG-016`.

Test N: `T3.10` da fare con l'utente in Navicat. Attenzione: i cataloghi di T3.6 vengono distrutti dai test, quindi la prova va preparata a mano nel client — procedura in `test-results/step3/T3.10-navicat.md`.

## 2026-09-22 — PAUSA richiesta dall'utente (lavoro interrotto, NON in commit)
Commit fatti: Step 1 (`a7ce5f5`), Step 2 (`b2fbfce`). Nel working tree, non committati: Step 3 completo ma con le correzioni del revisore **a metà** (agente fermato mentre estendeva `T45ConfirmDestructiveTest`), sistema visivo `docs/DESIGN-SYSTEM.md` + tema/icone **a metà** (agente fermato mentre scriveva i test delle schermate del tema), componenti Step 4 (griglia, editor SQL), Step 5–6 (test sui server T5.2, T5.3, T6.4, T6.5, `core.verify`, componente editor di tabelle), correzione `JsonFiles` (file bloccato da antivirus). Il codice potrebbe non compilare. **Alla ripresa:** `git status`, riprendere le correzioni dello Step 3 (elenco nella revisione: RiskClassifier SET STATEMENT/ANALYZE, export del registro, ZEROFILL e indici speciali, T3.9 sul bytecode, SqlExecutor, KILL dedicato, testi, conferma dell'editor) e il tema, poi `scriptserify.ps1`.

## 2026-09-22 — Step 2: shell dell'applicazione e connessioni ✅ (esecuzione autonoma)
Fatto:
- **core.connection**: `ConnectionProfile` (senza password), `ProfileStore` (JSON in `%APPDATA%\RamaSQL\connessioni.json`, import/export con `formatVersion`, conferma prima di sostituire profili omonimi, file illeggibile messo da parte con nome univoco e avvio sempre garantito), `JsonFiles` (scrittura atomica con `force`), `AppData` (cartella dati, sovrascrivibile con `ramasql.appdata` nei test), `AppSettings` (4 voci), `ConnectionErrorClassifier` (11 cause, messaggio italiano «cosa correggere» + codice originale), `Session` (connessione principale + di servizio, **sempre autocommit**, catalogo letto entro la scadenza), `ConnectionAttempt` (asincrono, annullabile, scadenza complessiva 10 s), `InternalQueries` (unico punto con SQL interno). Parametri JDBC in `ADR-016`.
- **app**: finestra a tre zone (Navigatore, schede, Pannello SQL con Registro/Anteprima/Messaggi), barra con 10 pulsanti con testo (non ancora realizzati = disabilitati), barra di stato (connessione, server, catalogo), schermata iniziale a tessere, finestra del profilo con *Prova connessione*, richiesta password, attesa con *Annulla*, finestra d'errore con messaggio originale, *Disconnetti* con conferma se ci sono schede, Impostazioni a 4 voci, menu File/Aiuto. Rete sempre fuori dall'EDT; finestre modali dietro l'interfaccia `Prompts` (sostituibile nei test).
- Revisione indipendente: nessun difetto bloccante; 4 da correggere (schermata di connessione bloccabile da una query dopo la scadenza, importazione che sovrascriveva senza chiedere, cambio di connessione con conferma non raggiungibile dall'interfaccia, file profili illeggibile che poteva impedire l'avvio) e 11 note: **tutti corretti**, ciascuno con un test.

Validazione (test con `@Tag("step2")`; evidenze in `test-results/step2/`):
| Test | Esito | Evidenza |
|---|---|---|
| T2.1 | ✅ | `SessionServerTest#t21_laSessioneRiconosceTipoEVersioneDelServer` (it, 2 server): «MariaDB 11.5.2» e «MySQL 8.0.40», confrontati con `VERSION()`/`@@version_comment` letti dal test; autocommit attivo anche per il server; connessione di servizio con `CONNECTION_ID` diverso → `T2.1-mariadb.txt`, `T2.1-mysql.txt`; in più utente MySQL `caching_sha2_password` senza TLS → `T2.1-mysql-caching-sha2.txt` |
| T2.2 | ✅ | `ConnectionErrorClassifierTest` (28 test): 16 eccezioni simulate, 11 cause distinte (host sconosciuto, porta chiusa, tempo scaduto, accesso negato, catalogo senza permesso, catalogo inesistente, troppe connessioni, host non ammesso, SSL, plugin di autenticazione, altro), messaggi tutti diversi; host come «classlab» non scambiati per errori SSL |
| T2.3 | ✅ | `T23ConnectionErrorsTest` (ui, contro i 2 server, dal clic sulla tessera): host inesistente, porta chiusa, password errata (1045), catalogo senza permesso (1044), catalogo inesistente (1049) → **5 messaggi distinti in italiano** che nominano il campo da correggere, con il messaggio originale del server → `T2.3-mariadb.txt`, `T2.3-mysql.txt`, 10 schermate `T2.3-*.png` |
| T2.4 | ✅ | `T24UnreachableHostTest` (ui, host `10.255.255.1`): clic che torna in 81 ms, latenza massima dell'EDT 47 ms durante l'attesa (limite 200), *Annulla* in 78 ms (limite 1000), nessuna finestra tardiva; senza annullare: diagnosi TIMEOUT dopo 8028 ms (limite 10 000) → `T2.4-annulla.txt`, `T2.4-tempo-scaduto.txt`, `T2.4-attesa.png`, `T2.4-tempo-scaduto.png` |
| T2.5 | ✅ | `ProfileStoreTest` (13 test): profili → JSON → profili identici; nessuna chiave con «pass», «pwd», «secret» a nessun livello; campo `password` inserito a mano nel file ignorato e non riscritto; `formatVersion` |
| T2.6 | ✅ | `T26T27ProfilesOnDiskTest#t26_…` (ui, 2 server): esportazione → importazione in un'**altra cartella dati** (secondo utente simulato: l'unico stato per utente è quella cartella) → tessere «Aula 3A - MariaDB»/«Aula 3A - MySQL» presenti → connessione riuscita dopo aver digitato la password → `T2.6.txt`, `T2.6-tessere-importate.png`, `T2.6-connesso.png` |
| T2.7 | ✅ | `T26T27ProfilesOnDiskTest#t27_…`: ciclo d'uso completo (crea profilo, prova, connetti, disconnetti, riconnetti, esporta): password chiesta 3 volte, mai ricordata; ricerca byte per byte (UTF-8, UTF-16LE, UTF-16BE) in tutti i file della cartella dati e nell'esportazione → **assente** (controprova: il nome utente si trova) → `T2.7.txt`. Il revisore ha verificato anche `test-results/`, i report e la vera `%APPDATA%` |
| T2.9 | ✅ | predisposto: procedura pronta, da eseguire con l'utente (`test-results/step2/T2.9-procedura.md`) |

Schermate generali: `shell.png`, `home-tessere.png`, `profilo.png`, `impostazioni.png` (controllate a vista). Test N: nessuno per questo step (non crea oggetti sul server).

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
