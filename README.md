# RamaSQL Client

Client desktop **semplice** per **MariaDB e MySQL**, pensato per l'uso **in aula**. Minimale nell'aspetto, medio nelle funzioni.

- Connessioni MariaDB / MySQL
- Creazione e modifica di tabelle, **indici**, **chiavi esterne** (verificati sul server), engine InnoDB e MyISAM
- Apertura delle tabelle in **data-entry**, con copia/incolla di blocchi rettangolari di celle da e verso Excel/Calc e conferma esplicita delle modifiche
- **Editor visivo di query** (ereditato da [SQLeo](https://github.com/ojwanganto/SQLeo)) ed editor SQL testuale
- **Viste** create graficamente
- Importazione **CSV** e **JSON**, **dump** selettivo o totale (struttura, dati o entrambi)
- **Modello ER** con retroingegneria e relazioni logiche indipendenti dalle chiavi esterne
- **L'SQL di ogni operazione è sempre mostrato** prima di essere eseguito, e registrato

> **Stato: fase di analisi e progettazione.** Non esiste ancora codice. Si parte dai documenti.

## Documentazione (in italiano)

| Documento | Contenuto |
|---|---|
| [CLAUDE.md](CLAUDE.md) | indice e regole del progetto |
| [docs/ANALYSIS.md](docs/ANALYSIS.md) | perché Java e non C# o C++; analisi di SQLeo; rischi; decisioni aperte |
| [docs/FEASIBILITY.md](docs/FEASIBILITY.md) | fattibilità requisito per requisito |
| [docs/DESIGN.md](docs/DESIGN.md) | documento di design del prodotto |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | moduli, pipeline SQL, integrazione di SQLeo, packaging |
| [docs/ROADMAP.md](docs/ROADMAP.md) | piano a step con i test di validazione, fino all'installer |
| [docs/DECISIONS.md](docs/DECISIONS.md) | registro delle decisioni (ADR) |
| [docs/JOURNAL.md](docs/JOURNAL.md) · [docs/BUGS.md](docs/BUGS.md) · [docs/GLOSSARY.md](docs/GLOSSARY.md) · [docs/CONSOLE.md](docs/CONSOLE.md) | diario, difetti e idee, glossario, comandi |

## Tecnologia

Java 25 · Swing + FlatLaf · modulo `sqleo-qb` estratto da SQLeo · MariaDB Connector/J · Maven · installer Windows con runtime incluso (jpackage + Inno Setup): **sui PC non serve installare Java**.

## Licenza

**GPL-3.0-or-later** — vedi [LICENSE](LICENSE). Il progetto incorpora codice derivato da *SQLeo Visual Query Builder* (© 2012 anudeepgade) e *SQLeonardo* (© 2004 nickyb), distribuiti sotto GPL v2 «or any later version»; attribuzioni in [NOTICE](NOTICE). MySQL Workbench è usato unicamente come riferimento funzionale: nessun suo codice è incluso.
