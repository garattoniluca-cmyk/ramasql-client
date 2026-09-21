Esecuzione autonoma di RamaSQL Client — step 1–6 (`ADR-014`). Questo giro serve a far avanzare il lavoro verso il goal, anche se il /goal si è fermato.

1. Leggi `.claude/goal.md` (contratto e vincoli), `CLAUDE.md`, lo step corrente di `docs/ROADMAP.md`, la voce più recente di `docs/JOURNAL.md` e `docs/BUGS.md`. Controlla `git status` e `git log --oneline -5`.
2. Se ci sono modifiche non committate di un lavoro interrotto, riprendile: completale oppure riportale a uno stato coerente, mai perderle alla cieca.
3. Esegui `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify.ps1`. Se l'ultima riga è `VERIFY: PASS`, `git status` è pulito, esiste un commit per ogni step 1–6 e il resoconto finale è in testa a `docs/JOURNAL.md`: scrivi una riga di conferma e termina il loop.
4. Se uno step che era PASS adesso è FAIL, **quello viene prima di tutto**: è una regressione, si ripara prima di andare avanti.
5. Altrimenti prendi il **primo step non PASS**, in ordine da 1 a 6, e lavora così, usando i sotto-agenti per il lavoro parallelo e indipendente:
   a. elenca i test dello step (tabella in `docs/ROADMAP.md`) ancora senza ✅ nel diario e le soglie mancanti mostrate dal verificatore;
   b. **implementa** il più piccolo passo utile, dentro il perimetro v1 di `docs/DESIGN.md` §1-bis e nell'architettura di `docs/ARCHITECTURE.md`;
   c. **verifica**: scrivi i test che dimostrano il criterio (`@Tag("stepN")`, più `it` / `ui`), eseguili con `.\mvnw.cmd -q verify` o sul solo modulo; i test d'integrazione girano su MariaDB **e** MySQL;
   d. **controlla** con un sotto-agente revisore, diverso da chi ha scritto il codice, che rilegga il cambiamento contro la riga di roadmap, `docs/DESIGN.md` e le regole di `CLAUDE.md` (pipeline anteprima SQL, niente transazioni, SQL mostrato, italiano, credenziali) e cerchi difetti reali;
   e. **correggi**: se un test fallisce o il revisore trova un difetto, correggi il codice, mai il test per farlo passare; riesegui;
   f. **concludi punto per punto**: per ogni test superato aggiungi la riga `| <ID> | ✅ | <evidenza> |` nella tabella dello step in `docs/JOURNAL.md`;
   g. quando `scripts\verify.ps1` dà PASS per lo step: aggiorna i documenti (JOURNAL, DECISIONS, ARCHITECTURE, CONSOLE, BUGS, stato in ROADMAP, «Stato attuale» di CLAUDE.md), prepara nel diario i test N per Navicat di quello step, e fai il commit `Step N: <titolo>`.
6. Stesso errore per 5 tentativi: voce in `docs/BUGS.md` con diagnosi, poi prosegui con ciò che non dipende da quel problema.
7. Mai: `git push`, scritture su cataloghi diversi da `ramasql_test_*`, credenziali in file tracciati o nei log, modifiche a soglie o logica di `scripts\verify.ps1`, test saltati, disattivati o svuotati, ✅ senza evidenza, funzioni fuori dal perimetro v1, gestione delle transazioni, installer, controllo del desktop dell'utente.
8. A fine giro, se il lavoro è in corso, pianifica il prossimo controllo tra 20–30 minuti.
