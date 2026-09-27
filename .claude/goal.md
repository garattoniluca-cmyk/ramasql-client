# goal.md — Esecuzione autonoma: step 7–8

Contratto di fine lavoro per il `/goal` (`ADR-021`). Si legge **a ogni ripresa del lavoro** insieme a `.claude/loop.md`, `CLAUDE.md`, `docs/ROADMAP.md` (step e test), `docs/JOURNAL.md` (voce più recente) e `docs/BUGS.md`.

Il contratto degli step 1–6 (`ADR-014`) è stato rispettato e chiuso il 2026-09-23 (commit `Step 1` … `Step 6`); il suo testo resta nella storia di git.

## Condizione da incollare in `/goal`

```
Porta RamaSQL Client dallo Step 7 allo Step 8 compreso di docs/ROADMAP.md, seguendo .claude/goal.md e .claude/loop.md. Il goal è raggiunto solo quando nell'ultimo turno compare l'output completo di `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify.ps1` con la riga finale `VERIFY: PASS` (build verde, nessun test fallito, soglie di test per step1…step8 raggiunte, ogni test U/I/M degli step 1–8 con esito ✅ e la sua evidenza in docs/JOURNAL.md), `git status` è pulito, esiste un commit per ogni step 7–8, e docs/JOURNAL.md contiene una voce per step più il resoconto finale di cui parla .claude/goal.md. Vincoli: solo i cataloghi con prefisso ramasql_test_ si creano, modificano o cancellano (gli altri cataloghi si leggono soltanto, o si copiano dentro ramasql_test_*); credenziali solo in docs/local DBs.txt, mai in file tracciati, mai nei log o nei MD; nessun git push; vietato abbassare le soglie o cambiare la logica di scripts/verify.ps1, cancellare, disattivare, saltare o indebolire test o criteri di accettazione per farli passare, e dichiarare ✅ senza evidenza; gli step 1–6 già chiusi non devono regredire; ogni test M passa dal programma vero contro MariaDB e MySQL; perimetro solo colonna «v1» di docs/DESIGN.md §1-bis, niente gestione transazioni, niente lavoro sugli step 9–13. Se lo stesso errore si ripete 5 volte documentalo in docs/BUGS.md e prosegui con il lavoro non dipendente. Fermati comunque dopo 12 ore di lavoro, scrivendo il resoconto finale in docs/JOURNAL.md.
```

## Autorizzazioni date dall'utente (2026-09-27)

- **Esecuzione autonoma degli step 7 e 8**, di notte, senza fermarsi a fine step: sospesa per questi step la regola n. 2 di `CLAUDE.md` (stop per revisione). I **test N con Navicat** (T7.12, T8.8) li farà l'utente alla fine: si preparano ma non si aspettano. Gli step 9–12 **non si toccano**.
- **Database:** gli stessi server locali di sviluppo degli step 1–6 (MariaDB 11.5 su `127.0.0.1:3306`, MySQL 8.0 su `127.0.0.1:3307`), utente **`ramasql_test`** (password alla riga `test=` di `docs/local DBs.txt`; `scripts/verify.ps1` la carica da solo nelle variabili `RAMASQL_IT_*`). I cataloghi dell'utente (`bibliotecasoft`, `ciccio`, `new_schema`, `scuola`) si **leggono** o si **copiano** dentro `ramasql_test_*`, non si modificano; le due viste reali di `bibliotecasoft` servono a T8.7b. `root` (righe `mariadb=` / `mysql=`) solo se serve un permesso che `ramasql_test` non ha, scrivendolo nel diario.
- **Git:** commit locali, **uno per step** (`Step 7: <titolo>`, `Step 8: <titolo>`) più eventuali commit intermedi. **Nessun push.**
- **Dipendenze Maven** dal repository centrale, se hanno licenza compatibile con GPL-3 e sono registrate in `docs/ARCHITECTURE.md` §7. Nessun altro software da installare sul PC.
- **Decisioni aperte** emerse durante il lavoro: le prende l'agente, registrandole come ADR con stato «accettata — decisa dall'agente, da rivedere».

## Adattamenti già decisi (per non fermarsi di notte)

- **Step 7, campionario di query (T7.2):** l'elenco di esercizi del corso che la roadmap chiede all'utente non c'è ancora. L'agente costruisce il campionario (≥ 40 query «da aula» sulla `biblioteca`, partendo dalle 20 dello spike S2b) e lo registra come decisione da rivedere; l'utente potrà aggiungere le sue query in seguito.
- **Difetti dello spike già assegnati a questi step** (`docs/BUGS.md`): `BUG-004`, `BUG-006`, `BUG-007`, `BUG-011`, `BUG-016` (Step 7), `BUG-005` (Step 7–8), `BUG-010` (Step 8). Vanno chiusi o, se non chiudibili, motivati nel diario.
- **Suggerimenti su tutto il programma (`ADR-020`)** restano allo Step 12: non si anticipano.

## Vincoli non negoziabili

1. **Mai** abbassare le soglie o cambiare la logica di conteggio di `scripts/verify.ps1`, né togliere test o righe di test da `docs/ROADMAP.md`. Mai cancellare, disattivare (`@Disabled`, `assumeTrue` usato per saltare, `-DskipTests`), svuotare o indebolire test per far passare la verifica. Se un test della roadmap si rivela sbagliato o impossibile, **non lo si tocca**: si scrive in `docs/BUGS.md` perché, e lo si lascia senza ✅ (lo step resta FAIL finché l'utente non decide).
2. **✅ solo con evidenza.** Nella tabella dello step in `docs/JOURNAL.md` ogni riga `| <ID> | ✅ | <evidenza> |` cita la prova: classe di test, numero di test superati, file in `test-results/stepN/`, SQL del registro, righe lette dal server. Mai ✅ a parole.
3. **Solo cataloghi `ramasql_test_*`** in scrittura. Ogni test d'integrazione crea il proprio catalogo con nome univoco e lo distrugge alla fine, anche se fallisce.
4. **Test d'integrazione su entrambi i server:** ogni test `@Tag("it")` gira su MariaDB **e** su MySQL. Se un server non risponde, il test fallisce: non si salta.
5. **Nessuna regressione:** gli step 1–6 sono PASS; se uno torna FAIL, si ripara prima di tutto il resto.
6. Le regole di `CLAUDE.md` restano tutte valide, tranne la n. 2 come detto sopra: pipeline «anteprima SQL» (3), solo MariaDB/MySQL (4), perimetro v1 e **niente transazioni** (5), codice SQLeo solo in `sqleo-qb` con intestazioni e note di modifica (6), licenze (7), credenziali (8), interfaccia in italiano con testi nei file di risorse (11).
7. **Niente lavoro sugli step 9–13** (importazione, dump, modello ER, rifiniture, installer).
8. Blocco: dopo 5 tentativi falliti sullo stesso problema, voce in `docs/BUGS.md` con diagnosi, poi si prosegue con ciò che non dipende da quel problema.

## Lezioni degli step 1–6 (valgono come regole)

- **Un componente con test a finte non è «fatto».** Negli step 4–6 editor, griglia ed editor di tabelle avevano molti test ma nessuna strada verso il server. Ogni riga M si prova con il **programma vero** (`it.ramasql.app.servertest`: `DbServer`, `ClientApp`, `Probe`) contro i due server, e l'esito si **ricontrolla sul server** con una connessione separata. Le finte vanno bene per i test U di componente, non per chiudere una riga M. Per lo Step 7 in particolare: la scheda «Query visiva» si apre dal pulsante *Nuova query visiva* della barra, le sue query passano dalla pipeline e finiscono nel registro.
- **Revisore indipendente a ogni step**, prima del commit: negli step 4–6 ha trovato un difetto bloccante (valori binari rovinati e falso «salvata») che nessun test aveva visto. Le sue osservazioni si correggono, non si discutono via.
- **Mai lanciare Maven mentre gira `scripts\verify.ps1`**: le due corse scrivono nelle stesse cartelle `target` e la verifica viene corrotta. Una corsa per volta.
- **Evidenze oneste:** un test fallito scrive «Esito: FALLITO» nel proprio file di evidenza; i tempi nel diario si citano come intervalli (cambiano a ogni corsa).
- **Differenze fra server** (es. `BUG-018`): si asserisce ciò che è vero su ciascuno e lo si dichiara nel Javadoc e nel diario, senza far finta che valga per entrambi.

## Convenzioni di test

- **JUnit 6.** Ogni test di uno step porta `@Tag("stepN")`; i test d'integrazione anche `@Tag("it")`; quelli d'interfaccia anche `@Tag("ui")`.
- **Unità (U):** nei moduli `core`, `model`, `sqleo-qb`, `app`, senza database.
- **Integrazione (I):** nel modulo `it-tests` (o `app`, con `@Tag("it")`, quando serve il programma vero), contro i due server, credenziali da variabili d'ambiente.
- **Manuali (M):** si **automatizzano**, con test Swing in-process taggati `ui` (e `it` se toccano il server), che controllano l'interfaccia e poi **verificano sul server**. Evidenza visiva: il componente disegnato su `BufferedImage` in `test-results/stepN/<ID>.png`. Non si usa il controllo del desktop dell'utente.

**Test che richiedono una persona** — gli unici che l'agente non può eseguire; per non bloccare il goal, per questi soli la riga del diario è `| <ID> | ✅ | predisposto: procedura pronta, da eseguire con l'utente (<file della procedura>) |` e il resoconto finale li elenca: **T2.9** (test dei 10 secondi) e **T7.11** (prova d'uso del query builder). Nessun altro test può usare questa formula.

## Definizione di «fatto» per step

Test e risultati attesi: `docs/ROADMAP.md`. Soglie minime di test superati (congelate in `scripts/verify.ps1`):

| Step | Contenuto | Test superati (di cui `it`) |
|---|---|---|
| 7 | Query editor visivo: modulo `sqleo-qb` definitivo e facciata, scheda «Query visiva» Grafica/SQL sincronizzate, join dalle FK, «non rappresentabile», query nidificate, due schede senza interferenze, resa HiDPI | ≥ 60 (≥ 10) |
| 8 | Viste grafiche: `CREATE OR REPLACE VIEW`, archivio dei sorgenti, normalizzatore, ripiego su SQL, viste nidificate, viste reali di `bibliotecasoft` | ≥ 30 (≥ 10) |

Gli step si fanno **in ordine**. A fine step: documenti aggiornati (`JOURNAL`, `DECISIONS`, `ARCHITECTURE`, `CONSOLE`, `BUGS`, stato in `ROADMAP`, «Stato attuale» di `CLAUDE.md`), poi commit.

## Resoconto finale (in testa a `docs/JOURNAL.md`)

Step completati e non; output finale di `scripts/verify.ps1`; decisioni prese dall'agente da rivedere; difetti aperti; **test N da fare con Navicat** (T7.12, T8.8, con cosa guardare e cosa ricostruire a mano, perché i cataloghi dei test si distruggono); prove d'uso da fare con una persona; come avviare il programma (`avvia.cmd`) e cosa si può fare ora.
