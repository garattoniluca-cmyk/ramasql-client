# BUGS.md — Difetti aperti, idee rinviate, idee parcheggiate

Tre sezioni. I difetti si numerano `BUG-nnn`; le idee `IDEA-nnn`. Un'idea entra in uno step solo per decisione dell'utente (regola 5 di `CLAUDE.md`).

## 1. Difetti aperti

Nessuno (non esiste ancora codice).

Da tenere d'occhio, emersi dall'analisi di SQLeo (diventano difetti se sopravvivono all'estrazione, test T7.1):
- limite di 3 tabelle per diagramma e richiesta di donazione (`DiagramLoader.createAndJoin`, `Application.isFullVersion`);
- chiamata a Google Analytics (`MDIMenubar`, `_Version.VERSION_TRACK`);
- stato `static` nel query builder (`QueryBuilder.identifierQuoteString`, `selectAllColumns`…): rischio di interferenza tra due schede (R-06, test T7.8).

## 2. Forse dopo — parcheggiate su indicazione dell'utente, nessun impegno

| ID | Idea | Nota |
|---|---|---|
| IDEA-001 | **Gestione delle transazioni**: interruttore autocommit, pulsanti Commit/Rollback nell'editor SQL; conferma del data-entry avvolta in `START TRANSACTION … COMMIT` con rollback automatico in caso d'errore; import «tutto o niente»; dump con istantanea coerente | Utente, 2026-09-21: «per ora non aggiungere gestione transazioni; segna tra le feature da mettere forse dopo». In v1: sempre autocommit (`ADR-010`). Punto d'innesto futuro: `SqlExecutor` |

## 3. Dopo — naturale v1.x, già ragionate in `DESIGN.md` (voci **[dopo]**)

| ID | Idea | Requisito |
|---|---|---|
| IDEA-010 | Elenco a discesa dei valori ammessi sulle colonne con FK nel data-entry (primo candidato v1.1) | 9, 3 |
| IDEA-011 | Filtri per colonna nella griglia; editor BLOB e JSON; export JSON e SQL INSERT | 9 |
| IDEA-012 | Più connessioni aperte insieme; password salvate (Windows DPAPI, D-08); opzioni SSL; colori delle connessioni | 1 |
| IDEA-013 | Riordino colonne, duplica struttura; indici FULLTEXT, prefissi e direzione | 2, 3 |
| IDEA-014 | Editor SQL: EXPLAIN, formattatore, cronologia, ripristino delle schede non salvate, «query interne» nel registro | 5 |
| IDEA-015 | Viste: ALGORITHM, SQL SECURITY, WITH CHECK OPTION | 6 |
| IDEA-016 | Import: JSON Lines, appiattimento degli annidati, «aggiorna su duplicato», file degli scarti; export per tabella in cartella CSV/JSON | 7 |
| IDEA-017 | Modello ER: note, colori, livelli di dettaglio, SVG/PDF/stampa, «Crea chiave esterna…» da relazione logica, join del query builder suggeriti dalle relazioni logiche | 8 |
| IDEA-018 | File di politica `aula.json` (connessioni preinstallate, blocco `DROP DATABASE`, cataloghi nascosti); tema scuro; guida integrata estesa | aula |
| IDEA-019 | Supporto ufficiale Linux/macOS (il jar già gira) | — |
| IDEA-020 | Routine, trigger, eventi modificabili (in v1 sola lettura, D-06) | — |
