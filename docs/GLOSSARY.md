# GLOSSARY.md — Termini del progetto

| Termine | Significato in questo progetto |
|---|---|
| **Catalogo** | Ciò che MySQL/MariaDB chiamano *database* o *schema* (`CREATE DATABASE`). Nell'interfaccia si usa «database»; nei documenti tecnici «catalogo» per non confonderlo con il server. |
| **Connessione / profilo** | Dati salvati per raggiungere un server (nome, host, porta, utente, catalogo). Senza password in v1. Distribuibile agli studenti come file JSON. |
| **Sessione** | Una connessione aperta. In v1 una sola per volta, sempre in **autocommit**. |
| **Navigatore** | L'albero degli oggetti a sinistra (server → cataloghi → tabelle, viste…). |
| **Data-entry** | Apertura di una tabella in griglia modificabile (requisito 9): inserimento, modifica, eliminazione, appunti a blocchi. |
| **Modifiche pendenti** | Inserimenti, modifiche ed eliminazioni fatti in griglia ma non ancora scritti sul server. Visibili con colori e contatore. |
| **Conferma** | Il pulsante esplicito che trasforma le modifiche pendenti in `INSERT/UPDATE/DELETE` (mostrati in anteprima) e li esegue. È il «commit esplicito» chiesto dall'utente; **non** è un `COMMIT` di transazione SQL. |
| **Scarta** | Butta le modifiche pendenti e ricarica i dati. Operazione locale: non è un `ROLLBACK`. |
| **Blocco / intervallo rettangolare** | Selezione di celle contigue su più righe e colonne; unità di lavoro degli appunti (copia, incolla, taglia). Formato negli appunti: testo tabulato, compatibile con Excel/Calc. |
| **Scheda record** | Vista alternativa del data-entry: un record per volta con i campi incolonnati (il «Form Editor» di Workbench). |
| **Anteprima SQL** | Finestra «SQL che verrà eseguito», mostrata prima di **ogni** operazione che modifica il database. |
| **Registro SQL** | Elenco cronologico di tutto l'SQL eseguito dal client per conto dell'utente, con origine ed esito; esportabile come script. |
| **Pipeline SQL** | Il percorso unico azione → generatore → anteprima → `SqlExecutor` → registro (`ARCHITECTURE.md` §4). |
| **Query editor visivo / QB** | L'editor grafico di query ereditato da SQLeo: tabelle trascinate in un diagramma, join disegnati. |
| **Rappresentabile** | Detto di una query che il QB sa mostrare come diagramma. Le non rappresentabili (CTE, funzioni finestra…) si modificano solo come testo. |
| **Editor raw** | L'editor SQL testuale. |
| **Vista grafica** | Vista (`CREATE VIEW`) costruita con il QB. **Sorgente originale**: l'SQL scritto dall'utente, conservato in locale perché il server ne restituisce una versione riscritta. |
| **Engine** | Motore di memorizzazione della tabella: **InnoDB** (FK, transazioni) o **MyISAM** (né FK né transazioni). |
| **Integrità referenziale** | Garanzia data dalle chiavi esterne: un figlio non può riferire un padre inesistente. **Riga orfana**: riga figlia il cui padre non esiste; impedisce di creare la FK. |
| **Controlli prima / verifica dopo** | Per indici e FK: controlli locali e sui dati prima di generare l'SQL; rilettura dei metadati dal server dopo l'esecuzione, con confronto («✔ verificato sul server»). |
| **Modello ER / modello logico** | Documento `.rsqlmodel` con il diagramma entità-relazioni di un catalogo. Indipendente dal database: non lo modifica e non ne è vincolato. |
| **Retroingegneria** | Costruzione del modello ER leggendo un catalogo esistente. |
| **Relazione fisica** | Relazione del modello che corrisponde a una FK reale (linea continua). |
| **Relazione logica** | Relazione che esiste solo nel modello, disegnata a mano o accettata da un suggerimento (linea tratteggiata). È ciò che rende il modello «indipendente dalle chiavi esterne». |
| **Suggeritore** | Propone relazioni logiche da convenzioni di nome (`id_editore` → `editori.id`) e compatibilità di tipo. Non applica mai nulla da solo. |
| **Dump** | Script `.sql` con struttura e/o dati di oggetti scelti (selettivo) o di tutto (totale). **Ripristino**: esecuzione di uno script `.sql`. |
| **v1 / [dopo] / [forse dopo]** | v1: ciò che si realizza ora (`DESIGN.md` §1-bis). [dopo]: naturale v1.x, già ragionato. [forse dopo]: parcheggiato su indicazione dell'utente (oggi: la gestione delle transazioni). |
| **Test U / I / M / N** | Unità · integrazione su MariaDB e MySQL · manuale (Claude) · controllo incrociato dell'utente con **Navicat**. |
| **`biblioteca`** | Schema canonico di prova (autori, libri, editori, libri_autori, soci, prestiti). **`biblioteca_myisam`**: stesso schema in MyISAM senza FK. |
| **Spike** | Prova tecnica usa-e-getta dello Step 1, che risponde a una domanda di fattibilità con esito go/no-go. |
| **SQLeo / SQLeonardo** | Il progetto GPL (2012) da cui si eredita il QB, e il suo progenitore (2004). |
