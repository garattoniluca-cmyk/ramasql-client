# JOURNAL.md — Diario cronologico (più recente in alto)

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
