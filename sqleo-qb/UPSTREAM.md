# UPSTREAM.md — provenienza del codice ereditato da SQLeo

Questo modulo contiene codice derivato da **SQLeo Visual Query Builder** (a sua volta derivato da **SQLeonardo**).
Regola 6 di `CLAUDE.md`: il codice ereditato vive solo qui, conserva le intestazioni di copyright originali e ogni file
modificato porta, subito sotto l'intestazione, la nota «Modificato per RamaSQL Client (2026): …».

## Origine

| | |
|---|---|
| Progetto | SQLeo Visual Query Builder |
| Deposito | `https://github.com/ojwanganto/SQLeo` (clone in `spikes/sqleo-upstream/`, cartella esclusa da git) |
| Commit | `86d3c4684cd549260873c3813dd14a7d8e017833` |
| Versione | 2017.09.rc1 |
| Licenza | GNU GPL versione 2 **o successiva** → ridistribuito sotto GPL-3.0-or-later (`ADR-003`) |
| Autori originali | © 2004 nickyb@users.sourceforge.net (SQLeonardo); © 2012–2013 anudeepgade@users.sourceforge.net (SQLeo); `I18n` «contributed by JasperSoft Corp.» |
| Estrazione | 2026-09-21, spike S1 dello Step 1 |

## File copiati (52 sorgenti Java + 13 immagini; 2 sorgenti e le 13 immagini poi sostituiti, vedi sotto)

Percorsi relativi a `src/` dell'originale, copiati in `sqleo-qb/src/main/java/`.
**M** = modificato (dettaglio più sotto), **=** = identico all'originale, **S** = sostituito da riscrittura pulita (2026-09-22, vedi «Problema di licenza risolto»).

`com/sqleo/querybuilder/` (43 file, l'intero pacchetto):

| File | | File | |
|---|---|---|---|
| `BaseMask.java` | = | `MaskReferences.java` | M |
| `BrowserDnD.java` | = | `ObjectsListCellRenderer.java` | M |
| `BrowserItems.java` | = | `QueryActions.java` | M |
| `BrowserPopup.java` | M | `QueryBuilder.java` | M |
| `DiagramAbstractEntity.java` | M | `QueryModel.java` | M |
| `DiagramEntity.java` | M | `QueryModelTreeCellRenderer.java` | **S** |
| `DiagramField.java` | M | `QueryStyledDocument.java` | = |
| `DiagramLayout.java` | = | `ViewBrowser.java` | M |
| `DiagramLoader.java` | M | `ViewDiagram.java` | M |
| `DiagramQuery.java` | M | `ViewObjects.java` | M |
| `DiagramRelation.java` | M | `ViewSyntax.java` | = |
| `MaskAlias.java` | M | `beans/Entity.java`, `beans/EntityField.java`, `beans/Tag.java` | = |
| `MaskCondition.java` | M | `dnd/DragMouseAdapter.java`, `dnd/EntityDropTargetListener.java`, `dnd/EntityTransferHandler.java`, `dnd/RelationDropTargetListener.java`, `dnd/RelationTransferHandler.java` | = (`dnd/TransferableObject.java`: **S**) |
| `MaskExpression.java` | M | `syntax/DerivedTable.java`, `QueryExpression.java`, `QueryTokens.java`, `SubQuery.java`, `_ReservedWords.java` | = |
| | | `syntax/QuerySpecification.java` | M (2026-09-27) |
| `MaskJoin.java` | M | `syntax/SQLFormatter.java`, `syntax/SQLParser.java` | M |

`com/sqleo/common/` (9 file, il minimo che serve al pacchetto sopra):

| File | | Perché serve |
|---|---|---|
| `gui/AbstractDialogConfirm.java` | = | base delle maschere (`BaseMask`) |
| `gui/AbstractDialogModal.java` | M | base di `AbstractDialogConfirm` |
| `gui/BorderLayoutPanel.java` | M | pannello di base di tutte le viste |
| `gui/CommandButton.java` | M | pulsanti delle maschere |
| `gui/ParenthesisMatcher.java` | = | evidenziazione delle parentesi nella scheda SQL |
| `gui/TextView.java` | M | scheda SQL del query builder |
| `util/I18n.java` | M (ridotto) | testi |
| `util/SQLHelper.java` | M (ridotto) | elenco delle funzioni di aggregazione |
| `util/Text.java` | = | utilità sulle stringhe |

Immagini (`src/images/` → `sqleo-qb/src/main/resources/images/`): 13 PNG (`bullet_key.png`, `bullet_key_filter.png`,
`bullet_pink.png`, `chart_organisation.png`, `database_table.png`, `filter.png`, `filter_where.png`, `layout.png`,
`page_white_database.png`, `sum.png`, `table_relationship.png`, `table_sort.png`, `textfield.png`).
**Sostituite da icone disegnate nel codice il 2026-09-22** (origine non dichiarata: vedi «Problema di licenza risolto»);
la cartella `images/` non esiste più.

**Non copiati:** i file di lingua `com/sqleo/common/locale/sqleo_*.properties` (i testi passano da `QbHost.text`; l'italiano
è nel file nostro `src/main/resources/it/ramasql/qb/qb_it.properties`, le chiavi sono quelle originali).

## File modificati e perché

Interventi di tipo ricorrente:
- **F** = riferimenti ad `Application` / `Preferences` / `_Version` / finestre MDI / `ConnectionAssistant` / `ConnectionHandler`
  sostituiti dalla facciata `it.ramasql.qb.QbHost` (`QbRuntime.host()`, `QbRuntime.scale`, `QbRuntime.scaledDimension`);
- **R** = rimozione di una funzione fuori perimetro (elenco nel paragrafo successivo).

| File | Cosa e perché |
|---|---|
| `common/gui/AbstractDialogModal.java`, `BorderLayoutPanel.java`, `CommandButton.java` | F: dimensioni scalate dalla facciata |
| `common/gui/TextView.java` | F + R: tolti completamento automatico (`SuggestionsView`, legato alle finestre MDI) e `CompoundUndoManager`/`LinePainter`/`TextLineNumber` (non copiati: file senza licenza GPL dichiarata, vedi sotto); `QueryStyledDocument` al posto di `SQLStyledDocument` |
| `common/util/I18n.java` | ridotto a `getString`/`getFormattedString`, che delegano a `QbHost.text` |
| `common/util/SQLHelper.java` | ridotto alla costante `SQL_AGGREGATES` (il resto dipendeva da `Application` e dalle connessioni di SQLeo) |
| `querybuilder/QueryBuilder.java` | F: costruttore `QueryBuilder(QbHost)`, `getHost()`, connessione richiesta alla facciata; backtick come virgolette predefinite degli identificatori; scheda SQL con `QueryStyledDocument`; ricarica del modello con `invokeLater` (in origine un thread in attesa attiva che toccava Swing fuori dall'EDT); R: trova/sostituisci, `ClientQueryBuilder`; divisore scalato; 2026-09-22: le colonne di una tabella derivata nella SELECT esterna non prendono più, al caricamento, l'alias automatico del campo (`t.col AS t_col`), che cambiava i nomi delle colonne del risultato (trovato dallo spike S2d grafico) |
| `querybuilder/DiagramLoader.java` | R: **limite di 3 tabelle** e messaggio a pagamento in `createAndJoin`; R: metadati manuali; caricamento **sincrono** (tolti finestra modale di attesa e thread: toccava Swing fuori dall'EDT e poteva bloccarsi se il thread finiva prima di `show()`; ora funziona anche senza finestra antenata); F: avvisi; metadati JDBC **per catalogo** (in MySQL/MariaDB il prefisso di `` `catalogo`.`tabella` `` è il catalogo JDBC, lo schema non esiste); join automatici anche da `QbHost.joinHints`; senza connessione `checkTable` non lancia più `NullPointerException` |
| `querybuilder/DiagramRelation.java` | R: azione «save to definition file» (metadati manuali); F: opzione archi/linee (`QbOption.RELATION_ARCS`); posizione dei campi con `SwingUtilities.convertPoint` invece di `getLocationOnScreen` (falliva in silenzio, join non disegnato, se il diagramma non era a schermo); linee in grigio medio e ancore scalate (resa) |
| `querybuilder/DiagramAbstractEntity.java` | titolo vuoto e nessuna icona nella barra delle entità (sotto FlatLaf si leggeva «DiagramAbstractEntity»); ricerca dei campi senza badare ai backtick (la colonna di una condizione scritta senza backtick compariva in rosso come «mancante») |
| `querybuilder/DiagramEntity.java` | F: icona; R: azioni «Show content» e «Show definition» (aprivano finestre MDI di SQLeo) |
| `querybuilder/DiagramField.java` | F: icona e dimensioni; 2026-09-22: icone del campo in WHERE chieste alla facciata (`QbIcon.QB_WHERE`, `QB_KEYANDWHERE`) invece che ai campi statici del renderer dell'albero, riscritto; R: trasformazione a tabella incrociata; `GROUP_CONCAT` nativo al posto della pseudo-funzione di SQLeo; margine destro di 4 px scalati sull'etichetta del campo (il nome più lungo toccava il bordo dell'entità: controllo «≥ 2 px tra testo e bordo» dello spike S5, 2026-09-22) |
| `querybuilder/BrowserPopup.java` | R: pseudo-funzioni di SQLeo tolte dall'elenco delle aggregate |
| `querybuilder/DiagramQuery.java`, `ObjectsListCellRenderer.java` | F: icone (`QbIcon`), campi `Icon` invece di `ImageIcon` |
| `querybuilder/MaskAlias.java`, `MaskExpression.java`, `MaskJoin.java`, `ViewBrowser.java` | F: titolo dei messaggi, dimensioni e altezza di riga |
| `querybuilder/MaskReferences.java` | R: metadati manuali; metadati JDBC per catalogo; F: dimensioni |
| `querybuilder/QueryActions.java` | R: azione trova/sostituisci; F: cartella del salvataggio immagine |
| `querybuilder/QueryModel.java` | **aggiunta la clausola `LIMIT`** (`getLimit`/`setLimit`, resa in `toString`) |
| `querybuilder/ViewDiagram.java` | R: filigrana col nome del programma originale nell'immagine esportata |
| `querybuilder/ViewObjects.java` | F: elenco degli oggetti letto dal catalogo della facciata |
| `querybuilder/syntax/SQLFormatter.java` | R: scrittura della posizione delle entità nell'SQL come commento (dipendeva da `Preferences` e dalle finestre MDI); **2026-09-27 (Step 7, BUG-005): `sort` dei join riscritto con ordine stabile** — l'originale raggruppava i join per tabella in comune e spostava anche join già in un ordine valido: `l JOIN la … LEFT JOIN p … JOIN a (su la) … LEFT JOIN s (su p)` usciva come `… LEFT JOIN p … LEFT JOIN s … JOIN a`, testo diverso e query dichiarata non rappresentabile (con join misti INNER/LEFT l'ordine è parte del significato). Ora si tiene l'ordine scritto e si sposta un join solo se non si aggancia a nessuna tabella già dichiarata (relazioni disegnate «a pezzi» nel diagramma); una seconda condizione fra le stesse due tabelle segue ancora il join della sua coppia. Tolto `moveUp`, usato solo dal vecchio `sort`. Effetto: la vista reale `v_prestiti_dettaglio` di `bibliotecasoft` si riapre graficamente (attesa di `S2cVisteRiletteTest` aggiornata). Test: `T72OrdineDeiJoinTest`, `T72CampionarioTest` (Q54), `T73CampionarioSuiServerTest` |
| `querybuilder/syntax/SQLParser.java` | F: avvisi (una finestra di conferma è diventata un avviso); R: sintassi di join esterno `(+)` di un altro DBMS; **`LIMIT` letto nel modello** (`doParseLimit`; dentro una sottoquery resta non supportato, con avviso); **corretto** l'ultimo `DESC` di `ORDER BY` perso davanti a `LIMIT`; **stato statico azzerato a ogni analisi** (le CTE di una query contaminavano la successiva); **corretto il verso dei join** (spike S2c): con la `ON` scritta «tabella_aggiunta.col = tabella_precedente.col» il parser metteva come «primaria» la tabella aggiunta, e `a LEFT JOIN b ON b.x = a.y` veniva rigenerato come `b LEFT JOIN a` (significato diverso); ora gli operandi si scambiano (operatore specchiato per `<`, `>`, `<=`, `>=`) — `doParseFrom`, variabile `joinedRef`, e `mirrorOperator`. Test: `S2cVersoDeiJoinTest`. Di conseguenza `QbSql.check` (codice nostro) considera uguali `a = b` e `b = a` per operandi semplici (`QbSqlUguaglianzeTest`) |

### Modifiche dello Step 7 (2026-09-27)

Ogni file qui sotto porta nell'intestazione la nota «Modificato per RamaSQL Client (2026-09-27): …» e, nel codice, un
commento `RamaSQL (2026-09-27…)` accanto a ogni punto toccato.

| File | Cosa e perché |
|---|---|
| `querybuilder/QueryBuilder.java` | `metadata()` (metadati dalla facciata, `BUG-016`); ascoltatori di modifica `addQueryChangeListener`/`fireQueryChanged` (la vista SQL del client si aggiorna a ogni gesto, `BUG-006`); `hideSyntaxTab()` (la scheda SQL interna si nasconde: la vista SQL è quella del client, `BUG-006`); larghezza iniziale del pannello di sinistra (albero ed elenco restavano larghi pochi pixel); `autoAliasColumns` spento |
| `querybuilder/DiagramLoader.java` | tabelle, colonne, chiavi primarie ed esterne da `QbHost.metadata()` invece che da `DatabaseMetaData` (`BUG-016`); tolti i metodi JDBC rimasti senza uso; alias automatico di una tabella solo se è già nel diagramma (in origine sempre: «`libri` libri») |
| `querybuilder/ViewObjects.java` | elenco di tabelle e viste da `QbHost.metadata()`; tolte le due liste a discesa (schema, che in MySQL/MariaDB non esiste, e tipo); `objectNames()` |
| `querybuilder/MaskReferences.java` | tabelle referenziate/referenzianti da `QbHost.metadata()` |
| `querybuilder/ViewBrowser.java` | ogni aggiornamento dell'albero della query avvisa il `QueryBuilder` (`fireQueryChanged`) |
| `querybuilder/DiagramRelation.java` | colori e spessore delle linee dalla facciata e scalati (`BUG-004`); nodo del join rotondo; un clic apre il menu del join con parole semplici («Solo le righe che corrispondono», «Tutte le righe di …»), niente FULL OUTER JOIN; il cambio di tipo avvisa il `QueryBuilder` |
| `querybuilder/MaskJoin.java` | cambia solo l'operatore (il tipo si sceglie dal menu del nodo; le due caselle davano un FULL OUTER JOIN sconosciuto a MySQL/MariaDB); bordo del token |
| `querybuilder/DiagramAbstractEntity.java` | intestazione unica con nome e «×» (barra del titolo vuota tolta, `BUG-004`); nessun alias automatico sulle colonne |
| `querybuilder/DiagramEntity.java` | nome senza backtick, alias solo se diverso dal nome |
| `querybuilder/DiagramField.java` | con l'icona del filtro l'entità si allarga se serve, invece di troncare il nome del campo (`BUG-011`) |
| `querybuilder/ViewDiagram.java` | colori del fondo e dei campi dalla facciata (`BUG-004`) |
| `querybuilder/QueryModelTreeCellRenderer.java` (riscrittura nostra) | l'albero della query mostra i nomi senza backtick |
| `querybuilder/MaskCondition.java` | la casella SUBQUERY si attiva anche con gli operatori di confronto (`prezzo > (SELECT AVG…)`), non solo con IN ed EXISTS; lo stato dei campi si ricalcola all'apertura (con `=`, il primo operatore, la casella restava spenta) |
| `querybuilder/syntax/QuerySpecification.java` | con la lista SELECT vuota e almeno una tabella si scrive `SELECT *` (in origine `SELECT FROM …`, SQL non valido, dopo aver tolto tutte le spunte) |

Correzioni dopo la revisione indipendente dello Step 7 (stessa data, stesse note nelle intestazioni):
`DiagramRelation` legge il verso del join dal **token** (quello che finisce nell'SQL, che il formatter può girare) per
il menu, il suggerimento e il colore delle mezze linee, non dall'entità «primaria» del diagramma: prima il menu poteva
dire «Tutte le righe di X» mentre l'SQL teneva tutte le righe dell'altra tabella; `QueryBuilder`: elenco delle tabelle in
alto (70% della colonna di sinistra), colore e testo italiano per colonne e tabelle mancanti, attesa limitata delle
notifiche durante un caricamento; `DiagramLoader`: avvisi alla facciata della scheda, in italiano; `DiagramField`:
altezza della casella di spunta pari a quella della sua icona (a 150% le righe si toccavano). Codice nostro: `QbRuntime`
con una facciata «del solo thread corrente» usata da `QbSql.check` per raccogliere gli avvisi (prima sostituiva per un
attimo la facciata di tutto il processo), `QbSql.equivalent`.

## Rimosso rispetto all'originale

- `Application.isFullVersion()` e il **limite di 3 tabelle per diagramma** con la relativa richiesta di denaro;
- chiamata al servizio di statistiche web e costante di tracciamento della versione (stavano in `MDIMenubar` e `_Version`: mai copiati);
- trasformazione a tabella incrociata e pseudo-funzioni eseguibili solo dall'esecutore di SQLeo;
- definizione manuale dei metadati (`ManualDBMetaData`, `ManualTableMetaData`, `CSVRelationDefinition`, «save to definition file»);
- riferimenti ad altri DBMS (sintassi `(+)`, commenti, casi particolari): il modulo serve solo MariaDB e MySQL;
- finestre MDI e tutto `com.sqleo.environment.**`: «Show content», «Show definition», trova/sostituisci, completamento automatico, preferenze, posizioni delle entità salvate nell'SQL, filigrana nell'immagine esportata;
- `CompoundUndoManager`, `LinePainter`, `TextLineNumber`: nell'originale sono copie di esempi di tips4java **senza intestazione GPL**; per prudenza non sono stati copiati (nel client l'editor SQL è RSyntaxTextArea);
- i 18 file di lingua di SQLeo.

Controllo (futuro test T7.1): nel modulo non devono comparire `isFullVersion`, `Donate`, `google-analytics`, `VERSION_TRACK`,
né nomi di altri DBMS. Verificato a mano il 2026-09-21: zero occorrenze. Attenzione per chi scriverà T7.1: cercare i nomi
dei DBMS **a parola intera** (`addOrderByClause` contiene «derby», `VERTICAL_SPLIT` e simili non c'entrano).

Residui tolti dopo la revisione dello Step 1 (2026-09-21), in vista di T7.1: i commenti nostri che contenevano la sigla
delle finestre interne di SQLeo a parola intera sono stati riformulati («finestre interne di SQLeo») in `TextView`,
`DiagramEntity`, `SQLFormatter` e `QbHost`; in `DiagramLoader.checkTable` è stato eliminato il blocco **già commentato
nell'originale** (`// fix ticket #119`) che chiamava l'avviso della classe `Application`; nelle note di modifica e nei
Javadoc nostri non compare più la forma `Application.` seguita da un membro. Tutte le note «Modificato per RamaSQL
Client» dei 27 file modificati riportano la data completa **2026-09-21** (GPLv2 §2a). Le modifiche successive
aggiungono sotto l'intestazione una nota con la propria data (es. `syntax/SQLFormatter.java`, 2026-09-27, BUG-005).

## Problema di licenza risolto (trovato e risolto il 2026-09-22)

Due file copiati avevano nell'intestazione originale la **GPL versione 2 senza «or any later version»**
(Copyright 2005-2006 JasperSoft Corporation): `querybuilder/QueryModelTreeCellRenderer.java` e
`querybuilder/dnd/TransferableObject.java`. Una GPL-2.0-only non si può combinare in un prodotto GPL-3.0-or-later
(`ADR-003`). **Sostituiti da riscrittura pulita** il 2026-09-22: i file originali sono stati eliminati e al loro posto,
con lo stesso nome e pacchetto (i chiamanti non cambiano), ci sono due classi nuove, codice nostro GPL-3.0-or-later,
scritte **senza consultare l'originale**, a partire solo da come le usava il resto del modulo (`EntityTransferHandler`,
`RelationTransferHandler`, i due `*DropTargetListener`, `ViewBrowser`, `BrowserItems`, `DiagramField`) e dalla
documentazione di `Transferable`/`DefaultTreeCellRenderer`:
- `dnd/TransferableObject`: `Transferable` per un oggetto dentro la stessa JVM; offre, per la classe dell'oggetto e le
  sue superclassi, il flavor `DataFlavor(Classe.class, nome)` atteso dai bersagli e il flavor JVM-locale;
- `QueryModelTreeCellRenderer`: renderer dell'albero della query (icona per tipo di nodo, testi «Query» e «Sottoquery»
  al posto delle etichette interne `ROOTQUERY`/`SUBQUERY`, chiavi `querybuilder.tree.*` in `qb_it.properties`).
  I campi statici `whereIcon`/`keyAndWhereIcon` non esistono più: `DiagramField` chiede le icone alla facciata.

Gli altri file JasperSoft (`dnd/*`, `ObjectsListCellRenderer`, `beans/EntityField`) sono «version 2 or later»: nessun
problema. Test: `LicenzeERisorseTest` (nessun file di `src/main` con licenza GPL-2.0-only), `TransferableObjectTest`,
`QueryModelTreeCellRendererTest`.

**Icone:** le 13 PNG ereditate (origine non dichiarata da SQLeo: dieci apparentemente dal set «Silk», due di origine
ignota) sono state **sostituite da icone disegnate nel codice** il 2026-09-22: `it.ramasql.qb.QbDrawnIcon` (Java2D,
griglia logica 16×16 moltiplicata per la scala, nitide in HiDPI), restituite da `BasicQbHost.icon(QbIcon)`. Nel modulo
non resta alcuna immagine (`LicenzeERisorseTest`, `QbDrawnIconTest`).

## Codice nostro nel modulo (non derivato, GPL-3.0-or-later)

`it/ramasql/qb/`: `QbHost` (facciata), `BasicQbHost`, `QbRuntime`, `QbIcon`, `QbDrawnIcon` (icone disegnate), `QbOption`, `JoinHint`, `QbSql`
(SQL → modello → SQL, `isRepresentable`), `QbParseException`; risorsa `qb_it.properties`. Riscritture pulite del 2026-09-22 (stessi nomi dei file ereditati sostituiti):
`com/sqleo/querybuilder/QueryModelTreeCellRenderer.java`, `com/sqleo/querybuilder/dnd/TransferableObject.java`.

Aggiunte dello Step 7 (2026-09-27): `it/ramasql/qb/QbMetadata` (metadati che il diagramma chiede alla facciata,
`BUG-016`), `it/ramasql/qb/QbColor` (colori del diagramma, token di `DESIGN-SYSTEM.md`, `BUG-004`); nel pacchetto del
query builder, per raggiungerne i membri di pacchetto: `com/sqleo/querybuilder/QbOperations.java` (facciata operativa del
diagramma per il programma e per i test: aggiungere tabelle, spuntare colonne, cambiare il tipo dei join, filtri,
raggruppamenti, ordinamenti, sottoquery, lettura dello stato; `BUG-006`) e `com/sqleo/querybuilder/JdbcQbMetadata.java`
(la vecchia lettura con `DatabaseMetaData` di `DiagramLoader`, raccolta qui come ripiego per chi passa solo una
connessione JDBC: le prove del modulo; il programma non la usa). La classe di prova `QbAccessoDiProva`, che stava nel
pacchetto `com.sqleo.querybuilder` dentro il modulo `it-tests` (pacchetto diviso su due moduli), è stata eliminata.
