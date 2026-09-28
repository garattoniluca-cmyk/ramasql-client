# goal.md — Esecuzione autonoma: step 9–12

Contratto di fine lavoro per il `/goal` (`ADR-024`). Si legge **a ogni ripresa del lavoro** insieme a `.claude/loop.md`, `CLAUDE.md`, `docs/ROADMAP.md` (step e test), `docs/JOURNAL.md` (voce più recente) e `docs/BUGS.md`.

I contratti degli step 1–6 (`ADR-014`, chiuso il 2026-09-23) e 7–8 (`ADR-021`, chiuso il 2026-09-28) sono stati rispettati; il loro testo resta nella storia di git.

## Condizione da incollare in `/goal`

```
Porta RamaSQL Client dallo Step 9 allo Step 12 compreso di docs/ROADMAP.md (esclusi installer e distribuzione: step 13 e 14 non si toccano), seguendo .claude/goal.md e .claude/loop.md. Il goal è raggiunto solo quando nell'ultimo turno compare l'output completo di `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify.ps1` con la riga finale `VERIFY: PASS` (build verde, nessun test fallito, soglie di test per step1…step12 raggiunte, ogni test U/I/M degli step 1–12 con esito ✅ e la sua evidenza in docs/JOURNAL.md), `git status` è pulito, esiste un commit per ogni step 9–12, la versione portabile aggiornata è stata creata con scripts\crea-portabile.ps1 e si avvia, e docs/JOURNAL.md contiene una voce per step più il resoconto finale di cui parla .claude/goal.md. Vincoli: solo i cataloghi con prefisso ramasql_test_ si creano, modificano o cancellano (gli altri cataloghi si leggono soltanto, o si copiano dentro ramasql_test_*); credenziali solo in docs/local DBs.txt, mai in file tracciati, mai nei log o nei MD; nessun git push; vietato abbassare le soglie o cambiare la logica di scripts/verify.ps1, cancellare, disattivare, saltare o indebolire test o criteri di accettazione per farli passare, e dichiarare ✅ senza evidenza; gli step 1–8 già chiusi non devono regredire; ogni test M passa dal programma vero contro MariaDB e MySQL; perimetro solo colonna «v1» di docs/DESIGN.md §1-bis, niente gestione transazioni, niente installer. Se lo stesso errore si ripete 5 volte documentalo in docs/BUGS.md e prosegui con il lavoro non dipendente. Fermati comunque dopo 24 ore di lavoro, scrivendo il resoconto finale in docs/JOURNAL.md.
```

## Autorizzazioni date dall'utente (2026-09-28)

- **Esecuzione autonoma degli step 9, 10, 11 e 12**, «un rush», senza fermarsi a fine step: sospesa per questi step la regola n. 2 di `CLAUDE.md` (stop per revisione). **Esclusi installer e distribuzione** (step 13) e la sperimentazione in aula (step 14). I **test N con Navicat** (T9.9, T10.10, T11.12) li farà l'utente alla fine: si preparano ma non si aspettano.
- **Database:** gli stessi server locali di sviluppo (MariaDB 11.5 su `127.0.0.1:3306`, MySQL 8.0 su `127.0.0.1:3307`), utente **`ramasql_test`** (password alla riga `test=` di `docs/local DBs.txt`; `scripts/verify.ps1` la carica da solo nelle variabili `RAMASQL_IT_*`). I cataloghi dell'utente (`bibliotecasoft`, `ciccio`, `new_schema`, `scuola`) si **leggono** o si **copiano** dentro `ramasql_test_*`, non si modificano. `root` (righe `mariadb=` / `mysql=`) solo se serve un permesso che `ramasql_test` non ha, scrivendolo nel diario.
- **Strumenti già presenti sul PC** che i test possono usare: `mariadb-dump.exe` / `mysqldump.exe` in `C:\Program Files\MariaDB 11.5\bin` (T10.8), Excel e LibreOffice (già usati dai test `office`).
- **Versione portabile:** a fine lavoro l'agente la rigenera con `scripts\crea-portabile.ps1` (jpackage, runtime incluso, in `dist\`, escluso da git) e controlla che `RamaSQL.exe` apra la finestra. È una cartella da provare in aula, **non** l'installer dello Step 13.
- **Git:** commit locali, **uno per step** (`Step 9: <titolo>` … `Step 12: <titolo>`) più eventuali commit intermedi. **Nessun push.**
- **Dipendenze Maven** dal repository centrale, se hanno licenza compatibile con GPL-3 e sono registrate in `docs/ARCHITECTURE.md` §7. Nessun altro software da installare sul PC.
- **Decisioni aperte** emerse durante il lavoro: le prende l'agente, registrandole come ADR con stato «accettata — decisa dall'agente, da rivedere».

## Adattamenti già decisi (per non fermarsi)

- **Dati di prova dello Step 9:** i file dell'utente non ci sono. L'agente costruisce `soci.csv` (100 righe) e `libri.json` (200) coerenti con la `biblioteca`, e il `soci.csv` di T9.6 come lo scrive **Excel italiano** (`;`, Windows-1252, date `gg/mm/aaaa`, virgola decimale, accenti) — se possibile facendolo salvare davvero a Excel via automazione, altrimenti riproducendone byte per byte il formato, e lo dichiara nel diario. Il CSV da 1 M di righe di T9.5 si genera durante il test (non va in git).
- **T10.7 (dump totale):** i cataloghi dell'utente si possono **leggere** per il dump, ma non si può ripristinare su di loro né svuotare il server. La prova di ripristino «su server vuoto» si fa su **copie** `ramasql_test_*` dei cataloghi dell'utente: dump totale di quelle copie con `DROP IF EXISTS` e `CREATE DATABASE`, eliminazione, ripristino dal file, confronto con l'originale. In più si produce (solo lettura) il dump totale reale e si verifica che contenga tutti i cataloghi dell'utente.
- **T10.8:** il dump di `mysqldump`/`mariadb-dump` lo produce il test. Il dump di **Navicat** richiede Navicat: se l'utente ha messo un file in `it-tests/fixtures/navicat/*.sql` lo si usa; altrimenti la metà Navicat di T10.8 vale come prova con una persona (riga «predisposto», procedura pronta) e il resoconto lo dice.
- **T11.11:** si automatizza tutto ciò che si misura (PNG in scala A4 a 150 dpi e a 1024×768, spessori delle linee, zampe di gallina e tratteggio distinguibili per contrasto e forma); la prova di stampa/proiezione vera va nell'elenco delle prove d'uso del resoconto.
- **T12.5:** invece di Resource Monitor, il test registra con `Get-NetTCPConnection` (o equivalente) le connessioni del processo del programma durante una sessione completa.
- **T12.6, T12.13:** schermo 1024×768 e 1366×768 **emulati** come in T7.9 (scala FlatLaf e dimensione dello schermo simulate), carattere al massimo; si controlla che nessuna finestra, dialogo o suggerimento esca dai bordi.
- **T12.7 (revisione «alla Apple»):** la fa un sotto-agente revisore con le skill di design, schermata per schermata, contro `docs/DESIGN-SYSTEM.md` e la colonna v1 di `docs/DESIGN.md` §1-bis; ciò che si toglie è una decisione dell'agente da rivedere (ADR). Criteri misurati dal test: barra degli strumenti ≤ 10 pulsanti, impostazioni = 4 voci.
- **Difetti già assegnati allo Step 12** (`docs/BUGS.md`): `BUG-017`, `BUG-021`, `BUG-023`, `BUG-024`, `BUG-025`. Vanno chiusi o, se non chiudibili, motivati nel diario.

## Vincoli non negoziabili

1. **Mai** abbassare le soglie o cambiare la logica di conteggio di `scripts/verify.ps1`, né togliere test o righe di test da `docs/ROADMAP.md`. Mai cancellare, disattivare (`@Disabled`, `assumeTrue` usato per saltare, `-DskipTests`), svuotare o indebolire test per far passare la verifica. Se un test della roadmap si rivela sbagliato o impossibile, **non lo si tocca**: si scrive in `docs/BUGS.md` perché, e lo si lascia senza ✅ (lo step resta FAIL finché l'utente non decide).
2. **✅ solo con evidenza.** Nella tabella dello step in `docs/JOURNAL.md` ogni riga `| <ID> | ✅ | <evidenza> |` cita la prova: classe di test, numero di test superati, file in `test-results/stepN/`, SQL del registro, righe lette dal server. Mai ✅ a parole.
3. **Solo cataloghi `ramasql_test_*`** in scrittura. Ogni test d'integrazione crea il proprio catalogo con nome univoco e lo distrugge alla fine, anche se fallisce. Un dump che contiene `DROP DATABASE` / `CREATE DATABASE` si esegue **solo** se tutti i cataloghi che nomina iniziano con `ramasql_test_` (controllo nel test, prima di eseguire).
4. **Test d'integrazione su entrambi i server:** ogni test `@Tag("it")` gira su MariaDB **e** su MySQL. Se un server non risponde, il test fallisce: non si salta.
5. **Nessuna regressione:** gli step 1–8 sono PASS; se uno torna FAIL, si ripara prima di tutto il resto.
6. Le regole di `CLAUDE.md` restano tutte valide, tranne la n. 2 come detto sopra: pipeline «anteprima SQL» (3) — anche import e ripristino mostrano l'SQL (le istruzioni preparate e il numero di lotti, lo script) e finiscono nel registro —, solo MariaDB/MySQL (4), perimetro v1 e **niente transazioni** (5: l'import va a lotti in autocommit), codice SQLeo solo in `sqleo-qb` con intestazioni e note di modifica (6), licenze (7), credenziali (8), interfaccia in italiano con testi nei file di risorse (11).
7. **Niente installer** (Step 13) e niente lavoro sullo Step 14.
8. Blocco: dopo 5 tentativi falliti sullo stesso problema, voce in `docs/BUGS.md` con diagnosi, poi si prosegue con ciò che non dipende da quel problema.

## Lezioni degli step 1–8 (valgono come regole)

- **Un componente con test a finte non è «fatto».** Ogni riga M si prova con il **programma vero** (`it.ramasql.app.servertest`: `DbServer`, `ClientApp`, `Probe`) contro i due server, e l'esito si **ricontrolla sul server** con una connessione separata. Le finte vanno bene per i test U di componente, non per chiudere una riga M. Ogni funzione nuova si apre **dal punto dell'interfaccia dove l'utente la cerca** (menu del navigatore, barra, menu contestuali), e il test passa da lì — non da una chiamata diretta al metodo.
- **Revisore indipendente a ogni step**, prima del commit. Negli step 7–8 ha trovato difetti bloccanti (menu del join che diceva il contrario dell'SQL, testo perso, lavoro cancellato da una seconda apertura, letture sull'EDT) e test che promettevano più di quanto dimostravano. Le sue osservazioni si correggono, non si discutono via.
- **Niente lavoro lungo sull'EDT**: letture dal server, import, dump e ripristino girano in sottofondo, con avanzamento e *Interrompi*; l'interfaccia resta reattiva.
- **Mai lanciare Maven mentre gira `scripts\verify.ps1`**: le due corse scrivono nelle stesse cartelle `target` e la verifica viene corrotta. Una corsa per volta.
- **Evidenze oneste:** un test fallito scrive «Esito: FALLITO» nel proprio file di evidenza; le immagini si catturano quando mostrano ciò che dichiarano (es. un avviso prima che il salvataggio lo nasconda); i tempi nel diario si citano come intervalli.
- **Differenze fra server** (es. `BUG-018`): si asserisce ciò che è vero su ciascuno e lo si dichiara nel Javadoc e nel diario, senza far finta che valga per entrambi.
- **Grafica «alla Apple»** (richiesta esplicita dell'utente): un solo pulsante primario per schermata, testi in parole semplici, spaziature e colori dai token di `docs/DESIGN-SYSTEM.md`; ogni schermata nuova si guarda in PNG a 100% e 150% prima del commit.

## Convenzioni di test

- **JUnit 6.** Ogni test di uno step porta `@Tag("stepN")`; i test d'integrazione anche `@Tag("it")`; quelli d'interfaccia anche `@Tag("ui")`.
- **Unità (U):** nei moduli `core`, `model`, `sqleo-qb`, `app`, senza database.
- **Integrazione (I):** nel modulo `it-tests` (o `app`, con `@Tag("it")`, quando serve il programma vero), contro i due server, credenziali da variabili d'ambiente.
- **Manuali (M):** si **automatizzano**, con test Swing in-process taggati `ui` (e `it` se toccano il server), che controllano l'interfaccia e poi **verificano sul server**. Evidenza visiva: il componente disegnato su `BufferedImage` in `test-results/stepN/<ID>.png`. Non si usa il controllo del desktop dell'utente.

**Test che richiedono una persona** — gli unici che l'agente non può eseguire; per non bloccare il goal, per questi soli la riga del diario è `| <ID> | ✅ | predisposto: procedura pronta, da eseguire con l'utente (<file della procedura>) |` e il resoconto finale li elenca: **T2.9** (test dei 10 secondi), **T7.11** (prova d'uso del query builder), **T12.14** (prova con uno studente sui suggerimenti) e, solo se manca il file di Navicat, la metà Navicat di **T10.8** (in quel caso la riga cita sia l'evidenza della metà `mysqldump` sia la procedura). Nessun altro test può usare questa formula.

## Definizione di «fatto» per step

Test e risultati attesi: `docs/ROADMAP.md`. Soglie minime di test superati (congelate in `scripts/verify.ps1`):

| Step | Contenuto | Test superati (di cui `it`) |
|---|---|---|
| 9 | Importazione CSV e JSON: lettori in streaming, codifiche e separatori, deduzione dei tipi, procedura guidata, lotti in autocommit, rapporto degli errori, *Interrompi* | ≥ 50 (≥ 10) |
| 10 | Dump e ripristino: letterali, ordinamento per dipendenze, round-trip sui due server e fra i due server, dump selettivo, esecutore di script con avanzamento e scelta fermati/continua | ≥ 40 (≥ 12) |
| 11 | Modello ER: `.rsqlmodel`, retroingegneria, suggeritore, relazioni logiche, aggiorna dal database, disposizione, esporta PNG | ≥ 50 (≥ 8) |
| 12 | Rifiniture: errori spiegati, suggerimenti su tutto il programma e sulle singole voci delle liste, tastiera, schermi piccoli, «Informazioni su», revisione «alla Apple», difetti assegnati | ≥ 40 (≥ 6) |

Gli step si fanno **in ordine**. A fine step: documenti aggiornati (`JOURNAL`, `DECISIONS`, `ARCHITECTURE`, `CONSOLE`, `BUGS`, stato in `ROADMAP`, «Stato attuale» di `CLAUDE.md`), poi commit.

## Resoconto finale (in testa a `docs/JOURNAL.md`)

Step completati e non; output finale di `scripts/verify.ps1`; decisioni prese dall'agente da rivedere; difetti aperti; **test N da fare con Navicat** (T9.9, T10.10, T11.12, con cosa guardare e cosa ricostruire a mano, perché i cataloghi dei test si distruggono, e i file da usare: CSV/JSON di prova, dump prodotti); prove d'uso da fare con una persona; dov'è la **versione portabile** aggiornata (`dist\…`) e come avviarla; cosa si può fare ora.
