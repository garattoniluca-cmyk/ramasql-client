# CLAUDE.md — Indice e regole di ingaggio

## Cos'è questo progetto (5 righe)

RamaSQL Client (nome di lavoro): client desktop **Windows** per **MariaDB e MySQL**, pensato per l'uso **in aula con studenti**. Eredita l'**editor visivo di query** di SQLeo (Java Swing, GPL) e gli costruisce attorno un client volutamente semplice: connessioni, editor di tabelle/indici/chiavi esterne, editor SQL, viste create graficamente, import CSV/JSON, dump selettivo, modello ER con retroingegneria. Principio didattico cardine: **ogni operazione mostra sempre l'SQL che esegue**. Riferimento funzionale (non di codice): MySQL Workbench.

## Stack

Java 25 LTS (Temurin) · Swing + FlatLaf · RSyntaxTextArea · modulo `sqleo-qb` estratto da SQLeo · MariaDB Connector/J (+ MySQL Connector/J se lo spike lo richiede) · Jackson, Commons CSV · Maven (wrapper) multi-modulo · JUnit 6 · a fine progetto: jpackage (runtime incluso) + Inno Setup 6 (installer per-utente, senza admin) + ZIP portabile. Licenza del prodotto: **GPL-3.0-or-later** (`ADR-003`). Motivazioni in `docs/ANALYSIS.md`, decisioni in `docs/DECISIONS.md`.

## Comandi essenziali

```bash
avvia.cmd              # compila e apre il programma (doppio clic)
.\mvnw.cmd verify      # build + test
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify.ps1   # verifica per step (VERIFY: PASS/FAIL)
```
Elenco completo in `docs/CONSOLE.md`. Su questo PC i comandi nella cartella corrente vanno chiamati con `.\`.

## Mappa di `docs/` — quando leggere quale

| File | Quando leggerlo |
|---|---|
| `docs/JOURNAL.md` | **sempre, a inizio sessione** — cosa è stato fatto, cosa manca |
| `docs/BUGS.md` | a inizio sessione — difetti aperti, idee emerse |
| `docs/ROADMAP.md` | **prima di iniziare un task** — step corrente, verifiche di accettazione |
| `docs/DECISIONS.md` | prima di ogni scelta architetturale, per non ridiscutere il già deciso |
| `docs/ANALYSIS.md` | scelta dello stack (Java vs C# vs C++), analisi di SQLeo, rischi, **decisioni aperte (§8)** |
| `docs/FEASIBILITY.md` | fattibilità **punto per punto** di ogni requisito, con limiti e alternative |
| `docs/DESIGN.md` | **documento di design del prodotto**: funzionalità, schermate, comportamento, fuori perimetro |
| `docs/ARCHITECTURE.md` | prima di scrivere codice: moduli, pacchetti, pipeline SQL, integrazione SQLeo |
| `docs/GLOSSARY.md` | termini di dominio (modello logico vs fisico, relazione logica, anteprima SQL…) |
| `docs/CONSOLE.md` | comandi di build, test, packaging |

## Regole non negoziabili

1. Prima di scrivere codice: leggere `JOURNAL.md` (ultime voci), `BUGS.md`, lo step corrente di `ROADMAP.md` e i documenti pertinenti.
2. **Uno step alla volta.** A fine step: **tutti i test di validazione dello step** (`ROADMAP.md`: U, I, M) superati con evidenza, voce in `JOURNAL.md`, stop per la revisione dell'utente, che esegue i controlli incrociati con **Navicat** (test N). Se Navicat mostra qualcosa di diverso da ciò che il client dichiara, è un difetto del client. Niente anticipo di step successivi senza richiesta. **Eccezione in vigore:** esecuzione autonoma degli step 1–6 (`ADR-014`), regolata da `.claude/goal.md` e `.claude/loop.md`; verifica con `scripts\verify.ps1`.
3. **Ogni operazione che tocca il database passa dalla pipeline «anteprima SQL»** (`ARCHITECTURE.md` §4): la GUI genera SQL, lo mostra, poi lo esegue. Nessuna scorciatoia che esegua SQL non mostrato/registrato.
4. **Solo MariaDB e MySQL.** Nessuna astrazione multi-DBMS: il codice ereditato da SQLeo per altri database si rimuove, non si mantiene.
5. **Semplicità prima di completezza, «alla Apple»**: minimale nell'aspetto, medio nelle funzioni, semplice nell'uso; il pubblico sono studenti. Fa fede la colonna «v1» di `DESIGN.md` §1-bis: una funzione che non è lì non si aggiunge (nemmeno se Workbench ce l'ha); si annota in `BUGS.md` tra i «dopo» o i «forse dopo». **Niente gestione delle transazioni** in v1: connessione sempre in autocommit, il client non genera mai `START TRANSACTION`/`COMMIT`/`ROLLBACK`.
5-bis. **Riferimenti funzionali** (`ADR-004`): per l'editor visivo di query e viste il riferimento è **SQLeo**; per **tutte le altre funzionalità** è **MySQL Workbench** (`github.com/mysql/mysql-workbench`) — stessa organizzazione, nomi e scorciatoie, in forma ridotta. Prima di progettare una schermata si guarda come la fa Workbench e si consulta la tabella di corrispondenza in `DESIGN.md` §4. Solo comportamento: nessun codice di Workbench si copia.
6. Il codice derivato da SQLeo vive **solo** nel modulo `sqleo-qb`, conserva le intestazioni di copyright originali e ogni file modificato riporta la nota di modifica (obbligo GPL). Il resto dell'app parla con quel modulo solo tramite la facciata (`ARCHITECTURE.md` §5).
7. Dipendenze: solo licenze compatibili con GPL-3 (Apache-2.0, BSD, MIT, LGPL, GPL). Ogni nuova dipendenza → riga in `ARCHITECTURE.md` §7 con licenza.
8. Credenziali di database mai negli MD, mai nel codice, mai in git. I test d'integrazione leggono la connessione da variabili d'ambiente / file locale ignorato da git.
9. Test distruttivi solo su cataloghi di test dedicati (prefisso `ramasql_test_`), mai su altri database dello stesso server.
10. Ogni task completato → `JOURNAL.md`. Ogni scelta architetturale → ADR in `DECISIONS.md`. Ogni difetto o idea → `BUGS.md`. Tutti gli MD stanno in locale nel progetto: `CLAUDE.md` in root, il resto in `docs/`.
11. Interfaccia e documentazione in italiano (testi in file di risorse, predisposti per l'inglese). Modifiche chirurgiche.

## Stato attuale

**Analisi iniziale: completata** (2026-09-21). Scelto lo stack (Java/Swing, `ADR-001`), redatti fattibilità, design, architettura e roadmap con i test di validazione per step. Perimetro fissato dall'utente: **minimale/medio, «alla Apple»**; 9 requisiti (gli 8 iniziali + data-entry con appunti a blocchi e conferma esplicita); **niente gestione transazioni** (forse dopo); indici e FK verificati sul server; validazione incrociata dell'utente con **Navicat**.

**Step 0 — Fondamenta: completato** (2026-09-21): progetto Maven a 5 moduli, finestra vuota con FlatLaf, `avvia.cmd`. Database locali pronti (MariaDB :3306, MySQL :3307, utente `ramasql_test`; credenziali solo in `docs/local DBs.txt`). **Installer solo a fine progetto** (Step 13).

**Step 1 — Spike: completato, GO** (2026-09-22, `docs/SPIKE-STEP1.md`): query builder di SQLeo estratto in `sqleo-qb` e funzionante su JDK 25 con i due server; query nidificate grafiche (CTE solo testo); driver unico MariaDB Connector/J (`ADR-015`). **In corso:** esecuzione autonoma degli step 2–6 (`ADR-014`); stato per step: `scripts\verify.ps1`.
