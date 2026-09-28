# REVISIONE-APPLE.md — Revisione «alla Apple» di ogni schermata (T12.7)

## 1. Introduzione

**Data:** 2026-09-28 · **Test:** T12.7 di `ROADMAP.md` · **Revisore:** revisione di design in sola lettura del codice.

**Materiale.** Inventario generato dal programma vero (`test-results/step12/T12.7-inventario.txt`: 49 schermate su MariaDB, con i controlli visibili di ciascuna) e le 30 fotografie `test-results/step12/T12.7-NN-*.png`. Metro di giudizio: `DESIGN.md` §1-bis (colonna «v1»), §2 (principi), §4 (corrispondenza con Workbench) e `DESIGN-SYSTEM.md` (aspetto).

**Metodo.** Per ogni schermata ogni controllo visibile riceve la domanda: «serve a uno dei 9 requisiti?». Il verdetto è **resta**, **togliere**, **unire con …** o **spostare in …**. Ciò che sta nella colonna «v1» serve per definizione. La revisione cerca doppioni, controlli senza scopo, testi ridondanti e rumore visivo (allineamenti, gerarchia, densità, testi tagliati). Le linguette interne (per esempio «editor di tabelle › Indici») sono trattate nella sezione della schermata principale. Le procedure guidate («importa › …», «dump › …») e i menu hanno una sezione per ogni passo.

**I 9 requisiti usati come metro** (`DESIGN.md` §1-bis):

| # | Requisito |
|---|---|
| **R1** | Connessioni MariaDB/MySQL: profili, prova connessione, una connessione per volta, password chiesta a ogni connessione |
| **R2** | Creare e modificare tabelle: editor delle colonne, `CREATE`/`ALTER` per differenze, rinomina, svuota, elimina (e cataloghi) |
| **R3** | Indici, chiavi esterne e integrità referenziale, con i controlli prima e la verifica sul server dopo |
| **R4** | InnoDB e MyISAM: scelta e conversione dell'engine |
| **R5** | Editor di query visivo e SQL, con **l'SQL sempre mostrato** (anteprima di ogni operazione e registro) |
| **R6** | Viste create con l'editor grafico |
| **R7** | Importazione CSV/JSON, dump selettivo o totale, esecuzione di uno script `.sql` |
| **R8** | Modello logico / ER: retroingegneria, relazioni logiche, PNG |
| **R9** | Tabelle aperte in modalità data-entry: griglia, scheda record, **Conferma** esplicita, appunti a blocchi, CSV |

La riga «Aula» di §1-bis non è un requisito numerato. È un criterio che vale per tutti: italiano, conferme sulle operazioni pericolose, errori spiegati, suggerimenti su ogni elemento, carattere ingrandibile. Nelle tabelle compare come «Aula».

**Esito in breve.** Nessuna schermata contiene funzioni fuori dal perimetro v1. Tutti i controlli servono a un requisito. Le osservazioni riguardano tre cose:

- qualche doppione, soprattutto *Esegui* e *Interrompi*, che compaiono sia nella barra principale sia in quella della scheda;
- testi ridondanti o ambigui;
- alcuni difetti visivi nelle fotografie: testi tagliati, un pannello vuoto, un riquadro di fondo sbagliato, linguette su due righe, la parola «null».

Gli interventi sono nel §3.

---

## 2. Schermate

### «schermata iniziale»

Fotografia 01. La barra ha 10 pulsanti con le scritte. Senza connessione è attivo solo *Connetti*: gli altri sono visibili e disabilitati, come chiede `DESIGN-SYSTEM.md` §3.1.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «toolbar.connect» *Connetti* | R1 | resta |
| «toolbar.newQuery» *Nuova query* | R5 | resta |
| «toolbar.newVisualQuery» *Nuova query visiva* | R5 | resta (vedi §3, «per dopo» n. 9: l'etichetta lunga fa scattare la barra compatta) |
| «toolbar.newTable» *Nuova tabella* | R2 | resta |
| «toolbar.newView» *Nuova vista* | R6 | resta |
| «toolbar.import» *Importa* | R7 | resta |
| «toolbar.export» *Esporta/Dump* | R7 | resta |
| «toolbar.erModel» *Modello ER* | R8 | resta |
| «toolbar.run» *Esegui* | R5 | resta |
| «toolbar.stop» *Interrompi* | R5 | resta |
| «tile.MariaDB locale» (tessera della connessione) | R1 | resta |
| «tile.more» *Altre azioni* (⋯) | R1 | resta. Fa lo stesso del tasto destro, ma è l'unica via che si vede |
| «tile.new» *Nuova connessione* | R1 | resta |

Aspetto: è la schermata più pulita del programma. Titolo `display`, un sottotitolo, tessere ariose e tessera tratteggiata. Nessun intervento.

### «area di lavoro»

Fotografia 02. Sono gli stessi 10 pulsanti della barra; *Connetti* diventa *Disconnetti*.

| Controllo | Serve a | Verdetto |
|---|---|---|
| Barra: 10 pulsanti (vedi «schermata iniziale») | R1, R2, R5–R8 | restano |
| «navigator.filter» (Filtra tabelle…) | Aula (navigatore, §3.2) | resta |
| «navigator.tree» | R2, R3, R4, R6 (tutti gli oggetti) | resta |
| «zone.sqlPanel» linguette Registro · Anteprima · Messaggi | R5 | resta: è il «SQL sempre mostrato» |
| «panel.log.filter» | R5 | resta |
| «panel.log.export» *Esporta…* | R5 (registro come `.sql`) | resta. Va **rinominato** in *Esporta registro…* (§3, «da fare ora» n. 7): oggi lo stesso «Esporta…» compare nel menu del navigatore con il significato di dump |
| «panel.log.table» (#, Ora, Origine, SQL, Esito, Durata, Righe) | R5 | resta |

Aspetto:

- Con la connessione aperta la barra perde **tutte** le scritte e mostra solo le icone. Nella fotografia 01, con *Connetti*, le scritte ci stanno per circa 13 px; la parola *Disconnetti*, più lunga, fa scattare la barra compatta a 1164 px di larghezza. Un'aula con proiettore a 1280 px è vicina alla soglia. Vedi §3, «per dopo» n. 9.
- La barra di stato dice «Nessun catalogo scelto» anche mentre si lavora in schede di un catalogo preciso: informazione poco utile. Vedi «per dopo» n. 12.

### «menu barra › Importa»

| Controllo | Serve a | Verdetto |
|---|---|---|
| «import.menu.data» *Importa dati da un file CSV o JSON…* | R7 | resta |
| «import.menu.script» *Esegui script SQL…* | R7 | resta: `DESIGN.md` §3.9 la vuole come voce distinta del menu Importa |

### «menu barra › Modello ER»

| Controllo | Serve a | Verdetto |
|---|---|---|
| «er.menu.new» *Nuovo modello dal catalogo (scegline uno nel navigatore)* | R8 | resta. L'istruzione tra parentesi, però, sta nel nome della voce: dovrebbe stare nel suggerimento, lasciando *Nuovo modello dal catalogo…* disabilitata. Vedi «per dopo» n. 13 |
| «er.menu.open» *Apri modello…* | R8 | resta |

### «menu navigatore › SERVER»

| Controllo | Serve a | Verdetto |
|---|---|---|
| «nav.menu.newCatalog» *Nuovo catalogo…* | R2 (crea catalogo, §3.2) | resta |
| «nav.menu.showSystem» *Mostra cataloghi di sistema* | Aula (§3.2: nascosti per default; utile per spiegare `information_schema`) | resta |

### «menu navigatore › CATALOG»

| Controllo | Serve a | Verdetto |
|---|---|---|
| «nav.menu.newTable» *Nuova tabella…* | R2 | resta. Esiste anche nella barra, ma è il menu contestuale di Workbench, non un terzo menu |
| «nav.menu.export» *Esporta…* | R7 (dump del catalogo) | resta |
| «nav.menu.dropCatalog» *Elimina catalogo* | R2 | resta (conferma rafforzata) |
| «nav.menu.refresh» *Aggiorna* | Aula (F5) | resta |

### «menu navigatore › TABLE»

Il menu ha 9 voci: è il più lungo del programma. Sono tutte in `DESIGN.md` §3.2.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «nav.menu.openTable» *Apri tabella* | R9 | resta |
| «nav.menu.designTable» *Progetta tabella…* | R2, R3, R4 | resta. Il nome non coincide con la scheda che apre («libri (struttura)») né con `DESIGN.md` §3.2 («Modifica struttura»). Vedi «per dopo» n. 14 |
| «nav.menu.importData» *Importa dati…* | R7 | resta |
| «nav.menu.export» *Esporta…* | R7 | resta |
| «nav.menu.rename» *Rinomina…* | R2 | resta |
| «nav.menu.truncate» *Svuota* | R2 | resta (conferma rafforzata) |
| «nav.menu.dropTable» *Elimina* | R2 | resta (conferma rafforzata) |
| «nav.menu.showCreate» *Mostra SQL di creazione* | R5 | resta |
| «nav.menu.copyName» *Copia nome* | R5 (aiuta a scrivere SQL a mano) | resta |

### «menu navigatore › VIEW»

| Controllo | Serve a | Verdetto |
|---|---|---|
| «nav.menu.openView» *Apri vista* | R6 (dati in sola lettura) | resta |
| «nav.menu.editView» *Modifica vista* | R6 | resta |
| «nav.menu.export» *Esporta…* | R7 | resta |
| «nav.menu.dropView» *Elimina* | R6 | resta |
| «nav.menu.showCreate» *Mostra SQL di creazione* | R5 | resta |

### «editor SQL»

Fotografia 03. La linguetta «editor SQL › Esito» è trattata qui.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «sqlEditor.runCurrent» *Esegui* | R5 | resta. È un **doppione** di *Esegui* della barra principale, voluto da `DESIGN.md` §2 e `DESIGN-SYSTEM.md` §3.5. Vedi «per dopo» n. 4 |
| «sqlEditor.runAll» *Esegui tutto* | R5 | resta |
| «sqlEditor.cancel» *Interrompi* | R5 | resta; è un doppione di *Interrompi* della barra, come sopra |
| «sqlEditor.open» *Apri…* | R5 | resta |
| «sqlEditor.save» *Salva* | R5 | resta |
| «sqlEditor.text» | R5 | resta |
| «sqlEditor.results» linguette [Esito] | R5 | resta: i risultati di ogni SELECT vi si aggiungono come sotto-schede |
| «sqlEditor.outcomes» (#, Istruzione, Esito, Durata, Avvisi) | R5 | resta |

Aspetto: la riga corrente è evidenziata in **giallo**, il colore predefinito di RSyntaxTextArea. Il sistema visivo prevede `#F5F8FF`: il token `Tokens.SQL_CURRENT_LINE` esiste ma nessuno lo usa. Vedi «da fare ora» n. 5.

### «griglia dati»

Fotografia 04.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «dataGrid.view.grid» / «dataGrid.view.form» *Griglia / Scheda* | R9 | resta |
| «dataGrid.page.previous» / «dataGrid.page.next» | R9 (paginazione a 1000 righe) | resta |
| «dataGrid.export» *Esporta CSV…* | R9 | resta |
| «dataGrid.discard» *Scarta* | R9 | resta |
| «dataGrid.confirm» *Conferma* | R9 | resta: è l'azione principale |
| «dataGrid.table» | R9 | resta |
| «dataGrid.rowHeader» (numeri di riga) | R9 | resta |

Aspetto:

- **Difetto grave:** a 1164 px la barra non ci sta e ***Conferma* esce dal bordo destro**: se ne legge solo «Conf». È l'azione principale del requisito R9. La pillola «Nessuna modifica in sospeso» ruba circa 170 px per dire ciò che i pulsanti disabilitati dicono già. Vedi «per dopo» n. 1.
- **Difetto:** nell'intestazione il tipo è scritto «INTUNSIGNED» e «SMALLINTUNSIGNED», senza spazio; la scheda record scrive correttamente «INT UNSIGNED». Vedi «per dopo» n. 2.

### «griglia dati con la scheda record»

Fotografia 05.

| Controllo | Serve a | Verdetto |
|---|---|---|
| I 7 controlli della barra (vedi «griglia dati») | R9 | restano |
| «recordForm.previous» / «recordForm.next» *Precedente / Successivo* | R9 | resta: scorre i record, non le pagine |
| «recordForm.new» *Nuovo* | R9 | resta |
| «recordForm.delete» *Elimina* | R9 | resta |
| «recordForm.field.*» (un campo per colonna) | R9 | resta |

Aspetto:

- Ha lo stesso difetto di *Conferma* tagliata della griglia.
- Tra la riga dei pulsanti del record e i campi c'è una fascia vuota di circa 35 px che separa senza motivo i comandi dal contenuto. Si sistema con il n. 1 «per dopo».
- Etichette allineate a destra, con nome in grassetto e tipo in grigio: ben fatte.

### «editor di tabelle»

Fotografia 06. Tratta anche le linguette Colonne, Indici, Chiavi esterne, Opzioni e SQL.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «tableeditor.tabs» Colonne · Indici · Chiavi esterne · Opzioni · SQL | R2, R3, R4, R5 | resta: sono le schede di Workbench ridotte (`DESIGN.md` §4) |
| **Colonne** «columns.table»: Nome, Tipo, Lunghezza/valori | R2 | resta |
| **Colonne**: caselle PK · NN · UQ · AI · UN | R2 (PK anche R3) | restano: sono le 5 di §1-bis |
| **Colonne**: Default, Commento | R2 | restano |
| **Colonne** «columns.add» / «columns.remove» *Aggiungi colonna / Togli colonna* | R2 | resta |
| «tableeditor.revert» *Annulla modifiche* | R2 | resta |
| «tableeditor.apply» *Applica* | R2, R5 (porta all'anteprima) | resta |
| **Indici** «indexes.table» (Nome, Tipo, Colonne), *Nuovo indice*, *Elimina indice* | R3 | resta |
| **Indici** «indexes.columns» + «indexes.columnChoice» + «indexes.addColumn» / «indexes.removeColumn» | R3 (indice su più colonne) | resta. Il testo **«Aggiungi colonna / Togli colonna» è identico** a quello della scheda Colonne ma fa un'altra cosa: aggiunge la colonna all'indice, non alla tabella. Va rinominato in *Aggiungi all'indice / Togli dall'indice* («da fare ora» n. 8) |
| **Indici** «indexes.up» / «indexes.down» *Sposta su / giù* | R3 (ordine delle colonne nell'indice) | resta |
| **Indici** «indexes.verifyData» *Verifica dati* | R3 (UNIQUE su dati con duplicati) | resta |
| **Chiavi esterne** «fks.table» (Nome, Tabella riferita, Colonne, ON DELETE, ON UPDATE), *Nuova / Elimina chiave esterna* | R3 | resta |
| **Chiavi esterne** «fks.pairs» + *Aggiungi coppia / Togli coppia* | R3 (FK su più colonne) | resta |
| **Chiavi esterne** «fks.verifyData» *Verifica dati* | R3 (righe orfane) | resta |
| **Opzioni** «options.name» | R2 | resta |
| **Opzioni** «options.engine» (2 voci) | R4 | resta |
| **Opzioni** «options.charset», «options.collation» | R2 | resta |
| **Opzioni** «options.autoIncrement», «options.comment» | R2 | resta |
| **SQL** (anteprima viva, nessun controllo) | R5 | resta |

Aspetto:

- L'intestazione della colonna «Lunghezza/valori» è tagliata in «Lunghezza/…».
- La colonna di sinistra senza nome, cioè il segnaposto della riga, è ammessa.
- La testata «libri · catalogo · InnoDB · come sul server» e la fascia dei pulsanti in basso a destra sono pulite.

### «tabella nuova»

Fotografia 07. I controlli e le linguette sono gli stessi di «editor di tabelle». L'unica differenza è la linguetta «SQL · 1», che conta le istruzioni in attesa.

| Controllo | Serve a | Verdetto |
|---|---|---|
| Tutti i controlli di «editor di tabelle» (Colonne, Indici, Chiavi esterne, Opzioni) | R2, R3, R4 | restano |
| «tableeditor.tabs» con il contatore «SQL · 1» | R5 | resta: dice allo studente che c'è SQL da vedere |
| «tableeditor.apply» *Applica* (primario, attivo) | R2, R5 | resta |

Aspetto: la colonna `id` proposta già con PK, NN e AI è un buon valore predefinito, «alla Apple». Nessun intervento oltre a quelli di «editor di tabelle».

### «query visiva»

Fotografia 08. Tratta anche le linguette «query visiva › Grafico» e «query visiva › Esito».

| Controllo | Serve a | Verdetto |
|---|---|---|
| «visualQuery.view.graphic» / «visualQuery.view.sql» *Grafica / SQL* | R5 | resta |
| «visualQuery.saveAsView» *Salva come vista…* | R6 | resta |
| «visualQuery.save» *Salva .sql…* | R5 | resta |
| «visualQuery.stop» *Interrompi* | R5 | resta, ma è un **doppione** di *Interrompi* della barra principale |
| «visualQuery.run» *Esegui* | R5 | resta, ma è un **doppione**: nella fotografia sono attivi e pieni d'accento **due** *Esegui*, quello della barra e quello della scheda, e quest'ultimo è tagliato al bordo destro. Vedi «per dopo» n. 4 |
| «visualQuery.diagram» linguette [Grafico] | R5 | **togliere la striscia di linguette**: una sola linguetta, «Grafico», non offre scelte. È un resto di SQLeo dopo `hideSyntaxTab()`. Vedi «per dopo» n. 5 |
| «qb.objects» (tabelle e viste del catalogo) | R5 | resta |
| albero della query (SELECT, FROM…) | R5 | resta |
| «sqlEditor.results» [Esito] + «sqlEditor.outcomes» | R5 | resta |

Aspetto:

- L'area dei risultati si riduce a una striscia di circa 10 px.
- L'etichetta del catalogo con l'icona a cilindro sembra un pulsante (stesso peso visivo di *Salva come vista…*).
- Vedi «per dopo» n. 4 e 5.

### «importa › 1 file»

Fotografia 09.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «import.file.choose» *Scegli…* | R7 | resta |
| «import.kind» (CSV / JSON) | R7 | resta |
| «import.charset» (4 voci) | R7 | resta |
| «import.separator» (4 voci) | R7 | resta |
| «import.header» *La prima riga contiene i nomi delle colonne* | R7 | resta |
| «import.back» *Indietro* (disabilitato al passo 1) | R7 | resta: la posizione resta fissa in tutti i passi |
| «import.next» *Avanti* | R7 | resta |

Aspetto: il campo del file vuoto e grigio, a tutta larghezza, sembra disabilitato. È accettabile perché si riempie solo con *Scegli…*.

### «importa › 1 file scelto»

Fotografia 10. I controlli sono gli stessi di «importa › 1 file».

| Controllo | Serve a | Verdetto |
|---|---|---|
| I 7 controlli di «importa › 1 file» | R7 | restano |
| (testo) «import.file.detected»: «“soci.csv”: CSV, codifica UTF-8 (…), separatore punto e virgola ;. Se l'anteprima non torna…» | R7 | resta, ma va **accorciato**. Ripete i valori già scritti nelle tre liste qui sopra ed è tagliato a destra, così la parte utile («correggi qui») non si legge. Vedi «per dopo» n. 6 |

### «importa › 2 anteprima»

Fotografia 11.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «import.preview.table» | R7 | resta |
| «import.back» / «import.next» | R7 | resta |

Aspetto: «100 righe e 5 colonne nel file; qui sotto le prime 20.» è un buon esempio di testo breve. Nessun intervento.

### «importa › 3 tabella esistente»

Fotografia 12.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «import.target.existing» *In una tabella esistente* + «import.target.table» | R7 | resta |
| «import.target.new» *In una tabella nuova* + «import.target.newName» | R7 | resta |
| «import.mapping.table» (Colonna del file, Esempio, Colonna della tabella) | R7 | resta |
| «import.back» / «import.next» | R7 | resta |

### «importa › 3 tabella nuova»

Fotografia 13.

| Controllo | Serve a | Verdetto |
|---|---|---|
| Scelta, lista e campo del nome (come sopra) | R7 | restano |
| «import.newTable.table» (Colonna, Tipo, NULL, Perché questo tipo) | R7, R2 | resta |
| «import.target.addKey» *Aggiungi la chiave primaria «id»* | R7, R3 | resta |
| (testo) anteprima `CREATE TABLE` | R5 | resta |
| «import.back» / «import.next» | R7 | resta |

Aspetto, **difetto:** nella fotografia la fascia rossa dell'errore («Nel catalogo c'è già una tabella o una vista “soci”…») **occupa tutto il passo**. Le scelte, il campo del nome da correggere, la tabella delle colonne e l'SQL non si vedono più, e lo studente non vede dove intervenire. Vedi «per dopo» n. 3.

### «importa › 4 opzioni»

Fotografia 14.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «import.option.truncate» *Svuota la tabella prima di importare (TRUNCATE TABLE)* | R7 | resta. Qui è disabilitata e sotto c'è la spiegazione: corretto |
| «import.option.duplicates» (errore / ignora) | R7 | resta |
| «import.option.emptyNull» *Un valore vuoto vale NULL* | R7 | resta |
| «import.option.dates» (4 voci) | R7 | resta |
| «import.back» / «import.next» | R7 | resta |

Aspetto: il riassunto di tre righe sotto le opzioni (righe, motivo per cui «Svuota» è spento, colonne con date) è utile e ben scritto.

### «importa › 5 importa»

Fotografia 15.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «import.run.stop» *Interrompi* | R7 | resta. **Difetto:** il testo è tagliato in «Interro…». Vedi «da fare ora» n. 6 |
| «import.run.rejected» (Riga, Motivo) | R7 (rapporto riga per riga) | resta |
| «import.back» *Indietro* | R7 | resta |
| «import.next» *Importa* | R7, R5 (prima l'anteprima) | resta |

### «dump › 1 cosa»

Fotografia 16.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «dump.what.allCatalogs» *Tutti i cataloghi* | R7 (dump totale) | resta |
| «dump.what.catalogs» (casella, Catalogo) | R7 | resta |
| «dump.what.objects» (casella, Oggetto, Tipo, Contenuto) | R7 (dump selettivo; struttura / dati / entrambi) | resta |
| «dump.back» / «dump.next» | R7 | resta |

Aspetto: con 8 schede aperte le linguette dell'area di lavoro **vanno su due righe** (fotografie 16–19). La riga della scheda attiva si sposta, un comportamento noto di Swing che disorienta. Vedi «da fare ora» n. 4.

### «dump › 2 opzioni»

Fotografia 17.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «dump.option.drop» *Prima di ogni CREATE, elimina l'oggetto se c'è già (DROP … IF EXISTS)* | R7 | resta |
| «dump.option.createDatabase» *Ricrea i cataloghi con il loro nome (CREATE DATABASE e USE)* | R7 | resta |
| «dump.option.foreignKeys» *Spegni i controlli delle chiavi esterne durante il ripristino* | R7 | resta |
| «dump.option.rows» *Righe per ogni INSERT* | R7 (INSERT estesi, §3.10) | resta |
| «dump.file.choose» *Scegli…* | R7 | resta |
| «dump.back» / «dump.next» | R7 | resta |

Aspetto, **difetto:** l'ultima riga di spiegazione («Il file non contiene CREATE DATABASE né USE: …») è tagliata a metà altezza dal bordo inferiore del passo. Vedi «per dopo» n. 3.

### «dump › 3 esporta»

Fotografia 18.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «dump.run.stop» *Interrompi* | R7 | resta. **Difetto:** «Interro…», come nell'importazione. Vedi «da fare ora» n. 6 |
| «dump.back» *Indietro* | R7 | resta |
| «dump.next» *Esporta* | R7 | resta |

Aspetto: «Il dump legge soltanto: sul server non cambia nulla.» è una frase esemplare per l'aula.

### «esegui script»

Fotografia 19.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «script.file.choose» *Scegli…* | R7 | resta |
| «script.target» *Catalogo* | R7 | resta. **Difetto:** prima di scegliere il file la lista è vuota e mostra la parola **«null»**. Vedi «da fare ora» n. 2 |
| «script.onError.stop» / «script.onError.continue» *Fermati / Continua* | R7 | resta. Il valore predefinito giusto (*Fermati*) è già scelto |
| «script.stop» *Interrompi* | R7 | resta |
| «script.run» *Esegui* | R7, R5 | resta |
| «script.failures» (Riga, Istruzione, Errore) | R7 | resta |

Aspetto:

- *Esegui* e *Interrompi* stanno a metà pagina, accanto alla barra d'avanzamento. Nelle due procedure guidate, invece, l'azione principale è sempre in basso a destra: la stessa famiglia di schermate ha due impaginazioni.
- Tra «File» e «Catalogo» resta uno spazio vuoto: è la riga del riassunto del file, ancora vuota.
- Vedi «per dopo» n. 7.

### «modello ER»

Fotografia 20 (finestra propria).

| Controllo | Serve a | Verdetto |
|---|---|---|
| «er.suggest» *Suggerisci relazioni* | R8 | resta |
| «er.layout» *Disponi* | R8 | resta |
| «er.refresh» *Aggiorna dal database* | R8 | resta |
| «er.export» *Esporta PNG…* | R8 | resta |
| «er.zoomOut» / «er.zoomIn» (− / +, con 100 %) | R8 | resta |
| «er.save» *Salva* | R8 | resta |
| «er.suggestions.table» (casella, Relazione, Fiducia) | R8 | resta **solo se ci sono proposte** |
| «er.suggestions.close» *Chiudi* | R8 | resta |
| «er.suggestions.accept» *Accetta le spuntate* | R8 | resta |

Aspetto, **doppione:**

- Quando non c'è niente da suggerire la fascia azzurra lo dice già («Nessuna relazione da suggerire…»). Eppure a destra si apre lo stesso il pannello «Relazioni suggerite», **vuoto**, con *Chiudi* e *Accetta le spuntate* attivi: un terzo dello schermo per non dire nulla. Vedi «da fare ora» n. 3.
- L'intestazione «Fiducia» è tagliata in «Fidu…».
- Il diagramma è ottimo: zampa di gallina, badge N:M, linee instradate.

### «menu modello ER › relazione»

| Controllo | Serve a | Verdetto |
|---|---|---|
| «er.relationship.physical» *Chiave esterna «fk_libri_editori» del database (si cambia dall'editor di tabelle)* | R8, R3 | resta. È una voce informativa: spiega perché una relazione fisica non si cancella dal modello. Didattica, e non esiste un posto migliore |

### «finestra preview.dialog»

Fotografie 21 (conferma normale) e 22 (conferma rafforzata).

| Controllo | Serve a | Verdetto |
|---|---|---|
| «preview.copy» *Copia nell'editor* | R5 | resta |
| «preview.cancel» *Annulla* | R5 | resta |
| «preview.execute» *Esegui* / *Elimina* | R5 | resta |
| (rafforzata) «preview.typeToConfirm» *Scrivi libri per confermare* | Aula (operazioni pericolose, §2) | resta |
| (testo) sottotitolo «SQL che verrà eseguito · 1 istruzione · origine: Editor SQL» | R5 | **togliere «SQL che verrà eseguito ·»**: ripete parola per parola il titolo della finestra, scritto 50 px più in alto. Vedi «da fare ora» n. 1 |
| (testo) «Controlla l'SQL qui sopra: premendo Esegui verrà eseguito così com'è.» | R5 | resta: è l'unica frase che dice cosa fare |

Aspetto: le pillole «Modifica» e «Distruttiva» e la fascia rossa con il nome da riscrivere sono esattamente come le descrive `DESIGN-SYSTEM.md` §3.7.

### «finestra catalog.create.dialog»

Fotografia 23.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «catalog.create.name» | R2 | resta |
| «catalog.create.charset» | R2 | resta |
| «catalog.create.collation» | R2 | resta |
| «dialog.cancel» *Annulla* | — | resta |
| «dialog.confirm» *Mostra SQL…* | R5 | resta: dice onestamente che il passo successivo è l'anteprima |

Aspetto:

- Le etichette sono allineate a sinistra, mentre in «finestra profile.dialog» e «finestra settings.dialog» sono allineate a destra: incoerenza piccola. Vedi «per dopo» n. 10.
- Tra la nota su utf8mb4 e i pulsanti c'è troppo spazio vuoto.

### «finestra profile.dialog»

Fotografia 24.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «profile.name» | R1 | resta |
| «profile.host» | R1 | resta |
| «profile.port» | R1 | resta |
| «profile.user» | R1 | resta |
| «profile.catalog» (facoltativo) | R1 | resta |
| «profile.note» | R1 | resta: §3.1 la prevede, serve al docente per le istruzioni della classe |
| «profile.test» *Prova connessione* | R1 | resta |
| «dialog.cancel» / «dialog.confirm» *Annulla / Salva* | R1 | resta |

Aspetto:

- La nota «La password non si salva: te la chiedo a ogni connessione.» è ottima.
- Sotto *Prova connessione* ci sono circa 70 px vuoti prima dei pulsanti, uno spazio morto che allunga la finestra.

### «finestra settings.dialog»

Fotografia 25.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «settings.language» *Lingua* (1 voce: Italiano) | Aula (§3.12) | resta. Oggi ha una sola voce, ma è una delle quattro impostazioni stabilite ed è già pronta per l'inglese |
| «settings.fontSize» *Dimensione carattere* | Aula (proiettore) | resta |
| «settings.rowLimit» *Limite righe* | R9 | resta |
| «settings.workDirectory» *Cartella di lavoro* + «settings.browse» *Sfoglia…* | R5, R7 (file .sql e .csv) | resta. Il pulsante *Sfoglia…* fa parte della stessa voce |
| «dialog.cancel» / «dialog.confirm» *Annulla / Salva* | — | resta |

Aspetto: è una finestra esemplare, con quattro voci, una riga di spiegazione ciascuna e nient'altro. È l'unico dialogo senza titolo `heading` dentro la finestra; qui va bene, perché basta il titolo della finestra.

### «finestra about.dialog»

Fotografia 26.

| Controllo | Serve a | Verdetto |
|---|---|---|
| (testo) licenza, sorgenti, SQLeo, Workbench | Aula (§3.12, obbligo GPL) | resta |
| «about.libraries» (Libreria, A cosa serve, Licenza) | Aula (attribuzioni) | resta |
| «about.close» *Chiudi* | — | resta |

Aspetto, **difetto:**

- Il testo della licenza è un `JTextArea` senza scorrimento. Quando la finestra viene ristretta allo schermo, il testo si interrompe a metà frase («…accanto alla cartella del programma; chi») e il seguito non si può leggere.
- Le colonne della tabella tagliano le descrizioni con «…».
- Vedi «per dopo» n. 8.

### «finestra guide.dialog»

Fotografia 27.

| Controllo | Serve a | Verdetto |
|---|---|---|
| (testo) Guida rapida | Aula (§3.12) | resta |
| «guide.close» *Chiudi* | — | resta |

Aspetto, **difetto:** dietro *Chiudi* si vede un rettangolo di fondo diverso (`bg.window` `#F6F7F9` su una finestra `bg.surface` bianca). Viene dal pannello interno di `DialogButtons`, che è opaco. Lo stesso rettangolo compare in «finestra er.tables.dialog». Vedi «da fare ora» n. 9.

### «finestra connection.error.dialog»

Fotografia 28.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «connect.error.toggle» *Messaggio originale del server* | R1, Aula (errore originale + spiegazione) | resta |
| «dialog.confirm» *OK* | — | resta |

Aspetto: è come la descrive `DESIGN-SYSTEM.md` §3.7, con il titolo umano, una riga di cosa fare e il messaggio originale ripiegabile in carattere mono. Nessun intervento.

### «finestra er.tables.dialog»

Fotografia 29.

| Controllo | Serve a | Verdetto |
|---|---|---|
| «er.tables.table» (casella, nome) | R8 | resta. Le colonne interne si chiamano «A» e «B», ma l'intestazione non si vede, quindi non è un problema |
| «er.tables.all» / «er.tables.none» *Tutte / Nessuna* | R8 | resta |
| «dialog.cancel» *Annulla* | — | resta |
| «er.tables.confirm» *Crea il modello* | R8 | resta |

Aspetto: c'è il rettangolo di fondo sbagliato dietro *Annulla* / *Crea il modello* (vedi «finestra guide.dialog», «da fare ora» n. 9). La frase «Il database non viene toccato.» è ottima.

### «finestra showCreate.dialog»

Fotografia 30.

| Controllo | Serve a | Verdetto |
|---|---|---|
| (testo) «Letto dal server con: SHOW CREATE TABLE» + codice | R5 | resta: mostra anche la query che ha letto il codice |
| «showCreate.copy» *Copia* | R5 | resta |
| «showCreate.close» *Chiudi* | — | resta |

---

### «query visiva con due tabelle»

Aggiunta dopo la revisione indipendente dello Step 12 (2026-09-28): la stessa scheda con *prestiti* e *libri* nel diagramma, per vedere i controlli che compaiono solo con le tabelle (le caselle delle colonne, la «×» di ogni tabella, i nodi dei join). Tratta anche le sue linguette e le finestre del query builder («query visiva › finestra MaskJoin», «… MaskCondition», «… MaskAlias», «… MaskReferences», «… MaskExpression»).

| Controllo | Serve a | Verdetto |
|---|---|---|
| caselle delle colonne «qb.field.select» | R5 | resta: spuntata = la colonna nel risultato (il suggerimento lo dice) |
| «qb.entity.close» *×* | R5 | resta |
| finestra del join: operatore «qb.join.operator» | R5 | resta; le voci spiegano il confronto |
| finestra della condizione: AND/OR, lato sinistro, operatore, *SUBQUERY*, lato destro | R5 | resta; ogni operatore ha la sua spiegazione (LIKE, IN, IS NULL, BETWEEN…) |
| finestra dell'alias: nome della tabella (sola lettura) e alias | R5 | resta; le etichette erano in inglese («identifier:», «alias:»), ora in italiano |
| finestra dei riferimenti: due elenchi | R5 | resta |
| finestra dell'espressione: alias ed espressione | R5 | resta; l'etichetta «Alias:» e l'avviso «Please, set a valid alias.» erano in inglese, ora in italiano |

### «menu query visiva › diagramma»

| Controllo | Serve a | Verdetto |
|---|---|---|
| *Compatta le tabelle*, *Disponi a griglia*, *Disponi in automatico* | R5 | resta (solo aspetto del diagramma, l'SQL non cambia) |
| *Togli tutte le tabelle* | R5 | resta (chiede conferma) |
| *Salva come immagine* | R5 | resta |
| *Copia SQL* | R5 | resta |

### «menu query visiva › albero della query»

| Controllo | Serve a | Verdetto |
|---|---|---|
| *DISTINCT (senza righe doppie)* | R5 | resta; era «distinct» in inglese |
| *Aggiungi espressione…*, *Aggiungi sottoquery* | R5 | resta |
| *Aggiungi condizione…* due volte (WHERE e HAVING) | R5 | **unire in due nomi diversi**: ora *Aggiungi condizione WHERE…* e *Aggiungi condizione HAVING…* (applicato) |
| *Aggiungi a GROUP BY*, *Aggiungi a ORDER BY*, *Allinea alle colonne selezionate* | R5 | resta |
| *UNION* | R5 | resta |
| *Modifica…*, *Togli*, *Togli tutto* | R5 | resta |

### «menu query visiva › tabella»

| Controllo | Serve a | Verdetto |
|---|---|---|
| *Ordina per nome*, *Compatta* | R5 | resta (solo aspetto) |
| *Seleziona tutto*, *Deseleziona tutto* | R5 | resta |
| *Apri le tabelle che la referenziano*, *Apri le tabelle referenziate*, *Riferimenti…* | R5 | resta |

### «menu query visiva › campo»

| Controllo | Serve a | Verdetto |
|---|---|---|
| *Nel risultato (SELECT)* | R5 | resta; era «select» in inglese; fa la stessa cosa della casella della colonna, ma dal menu si raggiunge anche da tastiera |
| *Aggiungi condizione WHERE…*, *Aggiungi condizione HAVING…*, *Aggiungi espressione…* | R5 | resta |

### «menu query visiva › join»

| Controllo | Serve a | Verdetto |
|---|---|---|
| *Solo le righe che corrispondono* / *Tutte le righe di …* (due) | R5 | resta: il tipo di join in parole semplici |
| *Condizione…* | R5 | resta |
| *Togli* | R5 | resta |

## 3. Interventi proposti

Criteri:

- **«Da fare ora»**: interventi piccoli, localizzati, che non cambiano il comportamento delle funzioni v1. Nessun test esistente controlla il testo o il componente toccato; ho verificato con una ricerca nei test sotto `app/src/test`.
- **«Per dopo»**: da annotare in `BUGS.md`. Toccano la disposizione, il modulo `sqleo-qb`, testi controllati dai test o scelte di `DESIGN.md`.

Nessun intervento toglie funzioni della colonna v1, l'anteprima SQL, il registro o i suggerimenti.

### 3.1 Da fare ora

1. **Sottotitolo dell'anteprima: togliere il prefisso ripetuto.**
   - **Dove:** `app/src/main/resources/it/ramasql/app/messages.properties:478-479` (`preview.subtitle.one` / `.many`), usato in `pipeline/PreviewDialog.java:84`.
   - **Cosa:** `SQL che verrà eseguito · %s istruzione · origine: %s` → `%s istruzione · origine: %s` (e lo stesso per `.many`).
   - **Motivo:** ripete il titolo della finestra (`preview.title`, riga 477) 50 px più in alto.
   - **Requisito:** R5.
   - **Rischio:** nullo; nessun test cerca questo testo.

2. **«null» nella lista del catalogo di «Esegui script».**
   - **Dove:** `app/src/main/java/it/ramasql/app/dump/ScriptRunTab.java:189-190`, nel renderer di «script.target».
   - **Cosa:** trattare `value == null` come `NONE`, cioè mostrare `script.target.none` («(scegli il catalogo)», `messages.properties:1033`) invece di `String.valueOf(null)`.
   - **Motivo:** la parola «null» è gergo di programmazione che appare allo studente.
   - **Requisito:** R7.
   - **Rischio:** nullo; cambia solo come si disegna una lista vuota.

3. **Modello ER: non aprire il pannello «Relazioni suggerite» quando è vuoto.**
   - **Dove:** `app/src/main/java/it/ramasql/app/er/ErModelPanel.java:298`.
   - **Cosa:** `showSuggestions(true)` → `showSuggestions(!list.isEmpty())`. La fascia azzurra dice già «Nessuna relazione da suggerire».
   - **Motivo:** è un doppione e un pannello vuoto che occupa un terzo della finestra.
   - **Requisito:** R8.
   - **Rischio:** basso. `T116T1111ModelloErSulServerTest` controlla `suggestionsVisible()` solo con 5 proposte. `Schermate.java:211` apre il modello «con l'elenco dei suggerimenti aperto», ma su `biblioteca`, che ha tutte le FK, l'elenco è vuoto: la fotografia T12.7-20 non mostrerà più il pannello e l'inventario perderà le tre righe «er.suggestions.*». Le schermate coperte non cambiano.

4. **Linguette dell'area di lavoro su una riga sola.**
   - **Dove:** `app/src/main/java/it/ramasql/app/MainFrame.java:322`, dopo `workTabs.setName(...)`.
   - **Cosa:** aggiungere `workTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);`. FlatLaf aggiunge da sé le frecce e l'elenco delle schede nascoste.
   - **Motivo:** oltre 6–7 schede le linguette vanno su due righe e la riga attiva salta (fotografie 16–19). È rumore visivo e disorienta.
   - **Requisito:** trasversale (Aula), tocca tutte le schede.
   - **Rischio:** basso; i test selezionano le schede per indice o per componente, non per coordinate.

5. **Riga corrente dell'editor SQL nel colore del sistema visivo.**
   - **Dove:** `app/src/main/java/it/ramasql/app/editor/SqlEditor.java:248`, dopo `setHighlightCurrentLine(true)`.
   - **Cosa:** aggiungere `textArea.setCurrentLineHighlightColor(Tokens.SQL_CURRENT_LINE);`. Il token `#F5F8FF` esiste già (`theme/Tokens.java:71`) ma non è usato da nessuna parte.
   - **Motivo:** oggi la riga corrente è gialla (`#FFFFAA`, predefinito di RSyntaxTextArea), unico colore fuori dai token nella schermata principale (`DESIGN-SYSTEM.md` §1.1).
   - **Requisito:** R5.
   - **Rischio:** nullo.

6. **«Interrompi» tagliato in «Interro…» nell'importazione e nel dump.**
   - **Dove:**
     - `app/src/main/java/it/ramasql/app/importer/ImportWizard.java:218-219`;
     - `app/src/main/java/it/ramasql/app/dump/DumpWizard.java:162-163`.
   - **Causa:** `tableeditor/Ui.java:52-53` fissa la misura preferita con il carattere normale; subito dopo `Styles.outline(...)` passa al semibold (`$rama.emphasis.font`) e il testo non ci sta più.
   - **Cosa:** dopo `Styles.outline(stopButton, …)` rimisurare con `stopButton.setPreferredSize(null)`, poi rimettere l'altezza `Tokens.CONTROL_HEIGHT` sulla nuova larghezza. `ScriptRunTab.java:132-133` ha lo stesso schema e conviene allinearlo.
   - **Requisito:** R7.
   - **Rischio:** basso; cambia solo la larghezza di un pulsante.

7. **«Esporta…» del Registro: dire che cosa esporta.**
   - **Dove:** `messages.properties:527` (`panel.log.export`), usato in `sqlpanel/SqlPanel.java:108`.
   - **Cosa:** `Esporta…` → `Esporta registro…`.
   - **Motivo:** nel programma ci sono già *Esporta/Dump* (barra), *Esporta…* (menu del navigatore = dump) ed *Esporta CSV…* (griglia). Un secondo «Esporta…» senza oggetto con un significato diverso è ambiguo, e `DESIGN-SYSTEM.md` §4 chiede «verbi d'azione» con l'oggetto.
   - **Requisito:** R5.
   - **Rischio:** nullo per i test, che cercano il pulsante per nome («panel.log.export»). Va aggiornata la parola in `DESIGN-SYSTEM.md` §3.6.

8. **Indici: non chiamare «Aggiungi colonna» due cose diverse.**
   - **Dove:** `messages.properties:608-609` (`tableeditor.indexes.addColumn` / `.removeColumn`), usati in `tableeditor/IndexesTab.java:111` e seguenti.
   - **Cosa:** `Aggiungi colonna` / `Togli colonna` → `Aggiungi all'indice` / `Togli dall'indice`.
   - **Motivo:** nella scheda Colonne lo stesso testo aggiunge una colonna **alla tabella**; nella scheda Indici ne aggiunge una **all'indice**. Per uno studente la differenza è tutto.
   - **Requisito:** R3.
   - **Rischio:** nullo; nessun test cerca questi testi, perché i test usano «indexes.addColumn».

9. **Rettangolo di fondo sbagliato dietro i pulsanti dei dialoghi.**
   - **Dove:** `app/src/main/java/it/ramasql/app/DialogButtons.java:45`.
   - **Cosa:** subito dopo `JPanel right = new JPanel(...)` aggiungere `right.setOpaque(false);`.
   - **Motivo:** il pannello interno è opaco con `Panel.background` (`#F6F7F9`) e disegna un rettangolo grigio dietro i pulsanti sui dialoghi bianchi (fotografie 27 e 29, verificato sui pixel).
   - **Requisito:** trasversale (Aula / qualità grafica).
   - **Rischio:** nullo.

### 3.2 Per dopo (da annotare in `BUGS.md`)

1. **Griglia: *Conferma* esce dalla finestra** (fotografie 04 e 05).
   - **Dove:** `grid/DataGrid.java:384-390`.
   - **Motivo:** a 1164 px la barra non ci sta e l'azione principale di R9 si legge «Conf».
   - **Proposta:** quando lo spazio manca, *Esporta CSV…* e l'indicatore di caricamento perdono prima la scritta. Inoltre la pillola «Nessuna modifica in sospeso» si nasconde quando non c'è nulla in sospeso, perché i pulsanti spenti lo dicono già.
   - **Requisito:** R9.
   - **Rischio:** medio. `T410T411ConfirmFlowTest`, `T419ValidationAndUndoTest` e `T49NoImplicitWriteTest` controllano il testo `grid.counter.none`.

2. **Intestazione della griglia: «INTUNSIGNED» senza spazio** (fotografia 04).
   - **Dove:** `grid/GridHeaderRenderer.java:83-95`.
   - **Motivo:** il codice aggiunge `" UNSIGNED"` con lo spazio, ma nel disegno lo spazio sparisce. Nella stessa riga «DECIMAL(6,2) · NN» gli spazi ci sono, e `RecordForm` scrive «INT UNSIGNED». Va capito perché (carattere, `fit()`, `drawString`) prima di correggere.
   - **Requisito:** R9.
   - **Rischio:** basso.

3. **Passi delle procedure guidate che non scorrono o si coprono.**
   - **Dove e motivo:**
     - `importer/ImportWizard.java:486`: la fascia d'errore `targetBanner` del passo 3 occupa tutta l'altezza e nasconde il campo da correggere (fotografia 13);
     - `dump/DumpWizard.java:320`: il riassunto delle opzioni è tagliato dal bordo inferiore (fotografia 17).
   - **Proposta:** misurare le fasce di testo a capo sulla larghezza vera, oppure mettere il corpo del passo in uno scorrimento verticale.
   - **Requisito:** R7.
   - **Rischio:** medio (disposizione).

4. **Doppioni di *Esegui* e *Interrompi*.**
   - **Dove:** la barra principale (`MainFrame.java`, `toolbar.run` / `toolbar.stop`), l'editor SQL (`editor/SqlEditor.java`, «sqlEditor.runCurrent» / «sqlEditor.cancel») e la query visiva (`visual/VisualQueryTab.java:218-222`).
   - **Motivo:** nella fotografia 08 due *Esegui* primari sono accesi insieme, e quello della scheda è tagliato. La scelta è di `DESIGN.md` §2 e `DESIGN-SYSTEM.md` §3.5, quindi va decisa con l'utente. Proposta: nella query visiva tenere solo quelli della barra principale; nell'editor SQL tenere *Esegui tutto*, che la barra non ha.
   - **Requisito:** R5, principio «una sola via ovvia».
   - **Rischio:** medio: i test usano «visualQuery.run».

5. **Linguetta singola «Grafico» della query visiva.**
   - **Dove:** `visual/VisualQueryTab.java:107` (`hideSyntaxTab()`), con la striscia di linguette del `QueryBuilder` nel modulo `sqleo-qb`.
   - **Motivo:** una striscia di linguette con una sola voce non offre scelte.
   - **Proposta:** senza la linguetta «Sintassi», mostrare il diagramma senza striscia. La modifica va nel modulo SQLeo, con la nota di modifica GPL.
   - **Requisito:** R5.
   - **Rischio:** medio (codice ereditato).

6. **Testo «rilevato» del passo 1 dell'importazione.**
   - **Dove:** `messages.properties:778` (`import.file.detected.csv`), mostrato in `importer/ImportWizard.java:370`.
   - **Motivo:** ripete i valori già visibili nelle tre liste ed è tagliato su una riga.
   - **Proposta:** «Formato, codifica e separatore letti dal file: se l'anteprima non torna, correggili qui.» in un'etichetta che va a capo.
   - **Requisito:** R7.
   - **Rischio:** basso, ma il testo è l'evidenza stampata da `T96T98ImportSulServerTest` (riga 98).

7. **«Esegui script» impaginato come le procedure guidate.**
   - **Dove:** `dump/ScriptRunTab.java:219-230`.
   - **Proposta:** *Esegui* e *Interrompi* nella fascia in basso a destra, come *Avanti* / *Importa* / *Esporta*. La lista del catalogo resta disabilitata finché non c'è un file.
   - **Requisito:** R7.
   - **Rischio:** basso.

8. **Finestra «Informazioni»: il testo della licenza non scorre.**
   - **Dove:** `AboutDialog.java:60-69, 102`.
   - **Proposta:** mettere il `JTextArea` in uno `JScrollPane` e allargare le colonne della tabella delle librerie o mandarle a capo.
   - **Requisito:** Aula (obbligo GPL di mostrare la licenza).
   - **Rischio:** basso.

9. **Barra compatta che scatta a 1164 px con *Disconnetti*.**
   - **Dove:** `MainFrame.java:456-475` (`compactToolbar`).
   - **Motivo:** tutte le scritte spariscono per una ventina di pixel. `DESIGN-SYSTEM.md` §3.1 chiama il pulsante «Query visiva» nel gruppo *Crea*; l'etichetta `toolbar.newVisualQuery` (`messages.properties:63`) dice «Nuova query visiva».
   - **Proposta:** un livello intermedio (prima si accorcia l'etichetta più lunga), oppure l'etichetta «Query visiva».
   - **Requisito:** trasversale.
   - **Rischio:** medio: `ShellLayoutTest.java:105` controlla le 10 etichette esatte.

10. **Allineamento delle etichette nei dialoghi.**
    - **Dove:** `CreateCatalogDialog` (etichette a sinistra) contro `ProfileDialog` e `SettingsDialog` (a destra).
    - **Proposta:** una regola sola, a destra come nelle impostazioni, in `DESIGN-SYSTEM.md` §3.7. Da valutare anche gli spazi morti sotto *Prova connessione* e sotto la nota del catalogo.
    - **Rischio:** basso.

11. **Colori della sintassi dell'editor SQL.**
    - **Dove:** `editor/SqlEditor.java:242-253`.
    - **Motivo:** l'editor non applica `SqlText.applyScheme(...)` né `Tokens.SQL_SELECTION`, a differenza di anteprima, registro e scheda SQL dell'editor di tabelle. Si nota appena si scrive una query: i colori sono quelli predefiniti di RSyntaxTextArea.
    - **Requisito:** R5.
    - **Rischio:** basso-medio (carattere e stili dei token).

12. **Barra di stato: «Nessun catalogo scelto».**
    - **Dove:** `messages.properties:102` (`status.catalog.none`).
    - **Proposta:** mostrare il catalogo della scheda attiva, oppure niente, invece di un'informazione che nella fotografia contraddice la scheda aperta («libri» di un catalogo preciso).
    - **Requisito:** R1.
    - **Rischio:** basso.

13. **Voce di menu che contiene un'istruzione.**
    - **Dove:** `messages.properties:1052` (`er.menu.new.noCatalog`), `MainFrame.java:962`.
    - **Proposta:** *Nuovo modello dal catalogo…* disabilitata, con «scegline uno nel navigatore» nel suggerimento, come per le altre voci spente.
    - **Requisito:** R8.
    - **Rischio:** basso (test per nome «er.menu.new»).

14. **Nome della voce «Progetta tabella…».**
    - **Dove:** `messages.properties:453` (`nav.menu.designTable`).
    - **Proposta:** allinearlo alla scheda che apre («(struttura)») e a `DESIGN.md` §3.2 («Modifica struttura…»), oppure aggiornare il documento. Workbench usa «Alter Table…».
    - **Requisito:** R2.
    - **Rischio:** basso.

---

## 4. Verifica delle due regole con un numero

| Regola | Misura (T12.7-inventario.txt) | Esito |
|---|---|---|
| Barra degli strumenti **≤ 10 pulsanti** | **10**: *Connetti/Disconnetti*, *Nuova query*, *Nuova query visiva*, *Nuova tabella*, *Nuova vista*, *Importa*, *Esporta/Dump*, *Modello ER*, *Esegui*, *Interrompi*. Corrisponde uno per uno all'elenco di `DESIGN.md` §2 | **rispettata** (al limite: non c'è posto per un undicesimo pulsante, e va bene così) |
| Impostazioni **= 4 voci** | **4** in «finestra settings.dialog»: *Lingua*, *Dimensione carattere*, *Limite righe*, *Cartella di lavoro*. *Sfoglia…* fa parte della quarta voce; *Annulla* e *Salva* sono i pulsanti della finestra | **rispettata**, esattamente come `DESIGN.md` §3.12 |

**Copertura:** tutte le schermate dell'inventario hanno la loro sezione nel §2, con il nome tra caporali: 36 titoli, che coprono le 49 voci contando le linguette interne di editor SQL, editor di tabelle, tabella nuova e query visiva.

## 5. Esito degli interventi (2026-09-28, Step 12)

Applicati nel codice:
- **Da fare ora** 1, 2, 3, 5, 6, 7, 8, 9: sottotitolo dell'anteprima senza ripetizione; «(scegli il catalogo)» invece di «null»; il pannello dei suggerimenti del modello ER si apre solo se ce ne sono; riga corrente dell'editor SQL con `sql.currentLine`; i pulsanti si misurano con il carattere che hanno davvero (niente più «Interro…»); *Esporta registro…*; *Aggiungi all'indice* / *Togli dall'indice*; nessun rettangolo grigio dietro i pulsanti dei dialoghi.
- **Da fare ora** 4 (linguette su una riga che scorre): provato e tolto, perché con FlatLaf la disposizione a scorrimento del `JTabbedPane` si rompe; annotato in `BUGS.md` (`BUG-036`).
- **Per dopo** 1 (*Conferma* fuori dalla finestra): la barra della griglia si stringe a gradini (prima *Esporta CSV…*, poi *Griglia/Scheda*, poi *Scarta* restano con la sola icona); *Conferma* resta sempre intera (`theme.Compact`).
- **Per dopo** 2 («INTUNSIGNED»): nell'intestazione le opzioni sono in sigla come le altre, «INT · UN · NN · AI» (al carattere piccolo lo spazio fra «INT» e «UNSIGNED» non si vedeva).
- **Per dopo** 3 (passi delle procedure guidate): ogni passo dell'importazione e del dump scorre in verticale se non ci sta.
- **Per dopo** 8 (testo delle Informazioni): il testo scorre.
- **Per dopo** 9 (barra compatta a 1164 px): gli spazi fra i gruppi si stringono prima di togliere le scritte, così alla dimensione normale la barra mostra icone e nomi.
- **Per dopo** 11 (colori della sintassi dell'editor SQL): stessi colori di anteprima e registro, selezione `sql.selection`.

Annotati in `BUGS.md` per dopo: 4 (`BUG-028`, da decidere con l'utente), 5 (`BUG-029`), 6 (`BUG-030`), 7 (`BUG-031`), 10 (`BUG-032`), 12 (`BUG-033`), 13 (`BUG-034`), 14 (`BUG-035`).
