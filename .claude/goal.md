# goal.md — Esecuzione autonoma: step 1–6

Contratto di fine lavoro per il `/goal` (`ADR-014`). Si legge **a ogni ripresa del lavoro** insieme a `.claude/loop.md`, `CLAUDE.md`, `docs/ROADMAP.md` (step e test), `docs/JOURNAL.md` (voce più recente) e `docs/BUGS.md`.

## Condizione da incollare in `/goal`

```
Porta RamaSQL Client dallo Step 1 allo Step 6 compreso di docs/ROADMAP.md, seguendo .claude/goal.md e .claude/loop.md. Il goal è raggiunto solo quando nell'ultimo turno compare l'output completo di `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify.ps1` con la riga finale `VERIFY: PASS` (build verde, nessun test fallito, soglie di test per step1…step6 raggiunte, ogni test U/I/M degli step 1–6 con esito ✅ e la sua evidenza in docs/JOURNAL.md), `git status` è pulito, esiste un commit per ogni step 1–6, e docs/JOURNAL.md contiene una voce per step più il resoconto finale di cui parla .claude/goal.md. Vincoli: solo i cataloghi con prefisso ramasql_test_ si creano, modificano o cancellano (gli altri cataloghi si leggono soltanto, o si copiano dentro ramasql_test_*); credenziali solo in docs/local DBs.txt, mai in file tracciati, mai nei log o nei MD; nessun git push; vietato abbassare le soglie o cambiare la logica di scripts/verify.ps1, cancellare, disattivare, saltare o indebolire test o criteri di accettazione per farli passare, e dichiarare ✅ senza evidenza; perimetro solo colonna «v1» di docs/DESIGN.md §1-bis, niente gestione transazioni, installer escluso. Se lo stesso errore si ripete 5 volte documentalo in docs/BUGS.md e prosegui con il lavoro non dipendente. Fermati comunque dopo 12 ore di lavoro, scrivendo il resoconto finale in docs/JOURNAL.md.
```

## Autorizzazioni date dall'utente (2026-09-21)

- **Esecuzione autonoma degli step 1–6** senza fermarsi a fine step: sospesa per questi step la regola n. 2 di `CLAUDE.md` (stop per revisione). I **test N con Navicat** li farà l'utente alla fine, su tutti gli step insieme: si preparano ma non si aspettano.
- **Database:** server locali di sviluppo (MariaDB 11.5 su `127.0.0.1:3306`, MySQL 8.0 su `127.0.0.1:3307`). I test usano l'utente **`ramasql_test`** (password alla riga `test=` di `docs/local DBs.txt`; `scripts/verify.ps1` la carica da solo nelle variabili `RAMASQL_IT_*`). I cataloghi dell'utente (`bibliotecasoft`, `ciccio`, `new_schema`, `scuola`) sono utili come casi reali: si **leggono** o si **copiano** dentro `ramasql_test_*`, non si modificano. L'utente `root` (righe `mariadb=` / `mysql=`) si usa solo se serve un permesso che `ramasql_test` non ha, e va scritto nel diario.
- **Git:** commit locali, **uno per step** (`Step N: <titolo>`) più eventuali commit intermedi. **Nessun push**: lo farà l'utente dopo la revisione.
- **Dipendenze Maven** dal repository centrale, se hanno licenza compatibile con GPL-3 e sono registrate in `docs/ARCHITECTURE.md` §7 (FlatLaf, RSyntaxTextArea, MariaDB Connector/J, Jackson, Commons CSV, AssertJ, AssertJ-Swing…). Nessun altro software da installare sul PC.
- **Sorgenti di SQLeo:** clonare `https://github.com/ojwanganto/SQLeo` al commit `86d3c4684cd549260873c3813dd14a7d8e017833` in `spikes/sqleo-upstream/` (cartella esclusa da git); il codice estratto va nel modulo `sqleo-qb` secondo la regola 6 di `CLAUDE.md` e `ADR-002`, con `sqleo-qb/UPSTREAM.md`.
- **Decisioni aperte** (D-06 routine/trigger in sola lettura, driver unico o doppio dopo S4, dettagli emersi durante il lavoro): le prende l'agente, registrandole come ADR con stato «accettata — decisa dall'agente, da rivedere».

## Vincoli non negoziabili

1. **Mai** abbassare le soglie o cambiare la logica di conteggio di `scripts/verify.ps1`, né togliere test o righe di test da `docs/ROADMAP.md`. Mai cancellare, disattivare (`@Disabled`, `assumeTrue` usato per saltare, `-DskipTests`), svuotare o indebolire test per far passare la verifica. Se un test della roadmap si rivela sbagliato o impossibile, **non lo si tocca**: si scrive in `docs/BUGS.md` perché, e lo si lascia senza ✅ (lo step resta FAIL finché l'utente non decide).
2. **✅ solo con evidenza.** Nella tabella dello step in `docs/JOURNAL.md` ogni riga `| <ID> | ✅ | <evidenza> |` cita la prova: nome della classe di test, numero di test superati, file di schermata in `test-results/stepN/`, SQL del registro, righe lette dal server. Mai ✅ a parole.
3. **Solo cataloghi `ramasql_test_*`** in scrittura. Ogni test d'integrazione crea il proprio catalogo con nome univoco e lo distrugge alla fine, anche se fallisce.
4. **Test d'integrazione su entrambi i server:** ogni test `@Tag("it")` gira su MariaDB **e** su MySQL (test parametrico o doppio). Se un server non risponde, il test fallisce: non si salta.
5. Le regole di `CLAUDE.md` restano tutte valide, tranne la n. 2 come detto sopra. In particolare: pipeline «anteprima SQL» (3), solo MariaDB/MySQL (4), perimetro v1 e **niente transazioni** (5), codice SQLeo solo in `sqleo-qb` con intestazioni e note di modifica (6), licenze (7), credenziali (8), interfaccia in italiano con testi nei file di risorse (11).
6. **Installer escluso** (Step 13, a fine progetto): niente jpackage, Inno Setup, Setup.exe.
7. Blocco: dopo 5 tentativi falliti sullo stesso problema, voce in `docs/BUGS.md` con diagnosi, poi si prosegue con ciò che non dipende da quel problema.
8. Esito **NO-GO** di uno spike (S1, S2a o S2d): si documenta in `docs/SPIKE-STEP1.md` e nel diario con le alternative, si lascia la S senza ✅ e **si prosegue con gli step 2–6**, che non dipendono dal query builder. La scelta di cambiare strada spetta all'utente.

## Convenzioni di test

- **JUnit 6.** Ogni test di uno step porta `@Tag("stepN")` (sulla classe o sul metodo). I test d'integrazione portano anche `@Tag("it")`; i test d'interfaccia anche `@Tag("ui")`.
- **Unità (U):** nei moduli `core`, `model`, `sqleo-qb`, `app`, senza database.
- **Integrazione (I):** nel modulo `it-tests`, contro i due server, credenziali da variabili d'ambiente (`ItConfig`).
- **Manuali (M):** si **automatizzano quando possibile**, con test Swing in-process (AssertJ-Swing o `Robot`) taggati `ui`, che controllano l'interfaccia e poi **verificano sul server** via JDBC ciò che l'interfaccia dice di aver fatto. L'evidenza visiva è un'immagine della finestra salvata disegnando il componente (`component.paint` su `BufferedImage`) in `test-results/stepN/<ID>.png`. Non si usa il controllo del desktop dell'utente. Le prove d'uso con una persona (T2.9, T7.11) si segnano «da fare con l'utente» nel diario, senza ✅, e si elencano nel resoconto: per questi soli ID il verificatore non basta a chiudere lo step → vedi sotto.
- **Casi reali:** le due viste di `bibliotecasoft` e lo schema `scuola` si usano in copia per S2c, S2d e i test di metadati.

**Test che richiedono una persona** (T2.9 «test dei 10 secondi»): sono gli unici che l'agente non può eseguire. Per non bloccare il goal, per questi soli la riga del diario è `| T2.9 | ✅ | predisposto: procedura pronta, da eseguire con l'utente |` e il resoconto finale li elenca tra le cose da fare con l'utente. Nessun altro test può usare questa formula.

## Definizione di «fatto» per step

Test e risultati attesi: `docs/ROADMAP.md`. Soglie minime di test superati (congelate in `scripts/verify.ps1`):

| Step | Contenuto | Test superati (di cui `it`) |
|---|---|---|
| 1 | Spike: estrazione query builder SQLeo nel modulo `sqleo-qb` (S1), query e viste grafiche su MariaDB e MySQL (S2a), parser su 20 query (S2b), viste reali del server (S2c), **query nidificate** (S2d), driver (S4), resa HiDPI (S5), canvas ER (S6), appunti a blocchi (S7) → `docs/SPIKE-STEP1.md` con GO / NO-GO per ciascuno | ≥ 8 (≥ 4) |
| 2 | Shell a tre zone, connessioni, `ServerInfo`, diagnosi errori, import/export profili | ≥ 8 (≥ 2) |
| 3 | Metadati, navigatore, pipeline SQL (`SqlScript`, `SqlExecutor`, registro), anteprima, operazioni sull'albero, fixture `biblioteca` e `biblioteca_myisam` in `it-tests/fixtures/` | ≥ 12 (≥ 4) |
| 4 | Editor SQL, **data-entry** con appunti a blocchi e pulsante Conferma, scheda record | ≥ 45 (≥ 4) |
| 5 | Editor di tabelle, `TableDiff` (≥ 60 casi), engine InnoDB/MyISAM | ≥ 60 (≥ 20) |
| 6 | **Indici e chiavi esterne con controlli prima e verifica sul server dopo** | ≥ 45 (≥ 12) |

Gli step si fanno **in ordine**. A fine step: documenti aggiornati (`JOURNAL`, `DECISIONS`, `ARCHITECTURE`, `CONSOLE`, `BUGS`, stato in `ROADMAP`, `CLAUDE.md` «Stato attuale»), poi commit.

## Resoconto finale (in testa a `docs/JOURNAL.md`)

Step completati e non; output finale di `scripts/verify.ps1`; esito GO/NO-GO degli spike; decisioni prese dall'agente da rivedere; difetti aperti; **elenco dei test N da fare con Navicat**, step per step, con cosa guardare; prove d'uso da fare con una persona; come avviare il programma per provarlo (`avvia.cmd`) e cosa si può fare ora.
