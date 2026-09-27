# S2c — viste rilette dal server e riaperte nel query builder

Generato da `it.ramasql.it.step1.S2cVisteRiletteTest`.

- **L1** sorgente locale riaperta dal parser (n.d. = vista nata altrove, sorgente non disponibile);
- **L2** definizione di `information_schema.VIEWS` → `ViewDefinitionNormalizer` → parser; «sì» vuol dire anche che
  l'SQL rigenerato dal modello, eseguito, dà le stesse righe della vista;
- **L2 senza norm.** lo stesso senza normalizzatore (per misurare quanto serve);
- **L3** il testo del server è eseguibile così com'è e dà le righe della vista (ripiego nell'editor SQL);
- **Apertura**: il livello più alto che la strategia a tre livelli userebbe.

| Gruppo | Vista | Server | Righe | L1 | L2 | L2 senza norm. | L3 | Apertura | Motivo se L2 = no | Riscrittura del modello |
|---|---|---|---|---|---|---|---|---|---|---|
| fixture | `v01_semplice` | MariaDB | 11 | sì | sì | sì | sì | grafica (L1) |  |  |
| fixture | `v02_join2` | MariaDB | 20 | sì | sì | no | sì | grafica (L1) |  |  |
| fixture | `v03_join3` | MariaDB | 23 | sì | sì | no | sì | grafica (L1) |  |  |
| fixture | `v04_alias` | MariaDB | 8 | sì | sì | sì | sì | grafica (L1) |  |  |
| fixture | `v05_aggregata` | MariaDB | 5 | sì | sì | no | sì | grafica (L1) |  |  |
| fixture | `v06_funzioni` | MariaDB | 8 | sì | sì | sì | sì | grafica (L1) |  |  |
| fixture | `v07_left_join_is_null` | MariaDB | 1 | sì | sì | no | sì | grafica (L1) |  |  |
| fixture | `v08_sottoquery` | MariaDB | 9 | sì | sì | sì | sì | grafica (L1) |  |  |
| fixture | `v09_vista_su_vista` | MariaDB | 11 | sì | sì | sì | sì | grafica (L1) |  |  |
| fixture | `v10_order_limit` | MariaDB | 5 | sì | sì | sì | sì | grafica (L1) |  |  |
| fixture | `v11_distinct` | MariaDB | 3 | sì | sì | sì | sì | grafica (L1) |  |  |
| fixture | `v01_semplice` | MySQL | 11 | sì | sì | sì | sì | grafica (L1) |  |  |
| fixture | `v02_join2` | MySQL | 20 | sì | sì | no | sì | grafica (L1) |  |  |
| fixture | `v03_join3` | MySQL | 23 | sì | sì | no | sì | grafica (L1) |  |  |
| fixture | `v04_alias` | MySQL | 8 | sì | sì | sì | sì | grafica (L1) |  |  |
| fixture | `v05_aggregata` | MySQL | 5 | sì | sì | no | sì | grafica (L1) |  |  |
| fixture | `v06_funzioni` | MySQL | 8 | sì | sì | sì | sì | grafica (L1) |  |  |
| fixture | `v07_left_join_is_null` | MySQL | 1 | sì | sì | no | sì | grafica (L1) |  |  |
| fixture | `v08_sottoquery` | MySQL | 9 | sì | sì | no | sì | grafica (L1) |  |  |
| fixture | `v09_vista_su_vista` | MySQL | 11 | sì | sì | sì | sì | grafica (L1) |  |  |
| fixture | `v10_order_limit` | MySQL | 5 | sì | sì | sì | sì | grafica (L1) |  |  |
| fixture | `v11_distinct` | MySQL | 3 | sì | sì | sì | sì | grafica (L1) |  |  |
| bibliotecasoft (vista reale) | `v_prestiti_dettaglio` | MariaDB | 5 | n.d. | sì | no | sì | grafica (L2) |  |  |
| bibliotecasoft (vista reale) | `v_statistiche_libri` | MariaDB | 413 | n.d. | sì | no | sì | grafica (L2) |  |  |
| scuola (schema reale, vista di prova) | `v_alunni_classi` | MySQL | 15 | sì | sì | no | sì | grafica (L1) |  |  |
| scuola (schema reale, vista di prova) | `v_corsi_per_classe` | MySQL | 5 | sì | sì | no | sì | grafica (L1) |  |  |

**MariaDB:** 13 viste su 13 riaperte dalla definizione del server (senza normalizzatore: 7); con la sorgente locale l'apertura grafica copre 13 su 13; le altre vanno nell'editor SQL senza perdita.

**MySQL:** 13 viste su 13 riaperte dalla definizione del server (senza normalizzatore: 6); con la sorgente locale l'apertura grafica copre 13 su 13; le altre vanno nell'editor SQL senza perdita.

Esito atteso fissato nel test PER VISTA e PER SERVER (`ATTESI`, oltre alle soglie): viste della fixture
L1 = L2 = sì su entrambi i server; `v_statistiche_libri` (MariaDB, reale) L2 = sì; `v_prestiti_dettaglio`
(MariaDB, reale) L2 = sì dal 2026-09-27 (prima L2 = no: il modello rigenerava i join in un ordine
diverso, BUG-005, corretto con l'ordine stabile dei join di `SQLFormatter.sort` nello Step 7);
viste su `scuola` (MySQL) L1 = L2 = sì. Differenze dall'atteso in questa esecuzione: 0.

Soglie fissate nel test (per server): viste della fixture al livello 2 ≥ 10 su 11, al livello 1 ≥ 10; viste sugli schemi reali al livello 2 ≥ 1 su 2.
