# S2d (parte U) - query nidificate: SQL -> modello -> SQL

Generato da `it.ramasql.qb.spike.S2dQueryNidificateTest`.

- **grafico ok**: il modello contiene i nodi `SubQuery`/`DerivedTable` attesi e l'SQL rigenerato equivale all'originale;
- **grafico con riscrittura**: nodi giusti e nessun pezzo di testo perso, ma l'SQL rigenerato ha un'altra forma;
- **solo testo**: da modificare nell'editor SQL.

| Costrutto | Esito | Nodi SubQuery | Nodi DerivedTable | Livelli | SQL originale | SQL rigenerato | Avvisi / motivo |
|---|---|---|---|---|---|---|---|
| WHERE ... IN (SELECT) | grafico ok | 1 | 0 | 1 | `SELECT s.cognome, s.nome FROM soci s WHERE s.id IN (SELECT p.id_socio FROM prestiti p WHERE p.data_reso IS NULL)` | `SELECT s.`cognome`, s.`nome` FROM `soci` s WHERE s.id IN (SELECT p.`id_socio` FROM `prestiti` p WHERE p.data_reso IS NULL)` |  |
| WHERE ... NOT IN (SELECT) | grafico ok | 1 | 0 | 1 | `SELECT l.titolo FROM libri l WHERE l.id NOT IN (SELECT p.id_libro FROM prestiti p)` | `SELECT l.`titolo` FROM `libri` l WHERE l.id NOT IN (SELECT p.`id_libro` FROM `prestiti` p)` |  |
| WHERE EXISTS (SELECT correlata) | grafico ok | 1 | 0 | 1 | `SELECT a.cognome FROM autori a WHERE EXISTS (SELECT la.id_libro FROM libri_autori la WHERE la.id_autore = a.id)` | `SELECT a.`cognome` FROM `autori` a WHERE EXISTS (SELECT la.`id_libro` FROM `libri_autori` la WHERE la.id_autore = a.id)` |  |
| WHERE confronto con (SELECT MAX...) | grafico ok | 1 | 0 | 1 | `SELECT l.titolo, l.prezzo FROM libri l WHERE l.prezzo = (SELECT MAX(l2.prezzo) FROM libri l2)` | `SELECT l.`titolo`, l.`prezzo` FROM `libri` l WHERE l.prezzo = (SELECT MAX( l2.prezzo ) FROM `libri` l2)` |  |
| sottoquery nella lista SELECT | grafico ok | 1 | 0 | 1 | `SELECT e.nome, (SELECT COUNT(l.id) FROM libri l WHERE l.id_editore = e.id) AS quanti_libri FROM editori e` | `SELECT e.`nome`, (SELECT COUNT( l.id ) FROM `libri` l WHERE l.id_editore = e.id) AS quanti_libri FROM `editori` e` |  |
| tabella derivata in FROM | grafico ok | 0 | 1 | 1 | `SELECT t.id_editore, t.prezzo_medio FROM (SELECT l.id_editore, AVG(l.prezzo) AS prezzo_medio FROM libri l GROUP BY l.id_editore) t WHERE t.prezzo_medio > 20` | `SELECT t.id_editore, t.prezzo_medio FROM (SELECT l.`id_editore`, AVG( l.prezzo ) AS prezzo_medio FROM `libri` l GROUP BY l.id_editore) t WHERE t.prezzo_medio > 20` |  |
| CTE WITH (riferita con alias) | solo testo | 0 | 1 | 1 | `WITH recenti AS (SELECT l.id, l.titolo FROM libri l WHERE l.anno >= 2020) SELECT r.titolo FROM recenti r ORDER BY r.titolo` | `SELECT `r`.`titolo` FROM (SELECT l.`id`, l.`titolo` FROM `libri` l WHERE l.anno >= 2020) recenti, `r` ORDER BY r.titolo ASC` | l'SQL rigenerato differisce dall'originale (da: «with recenti(select l.id,l.tit» ≠ «select r.titolo from(select l.») |
| CTE WITH (riferita senza alias) | grafico con riscrittura | 0 | 1 | 1 | `WITH recenti AS (SELECT l.id, l.titolo FROM libri l WHERE l.anno >= 2020) SELECT recenti.titolo FROM recenti ORDER BY recenti.titolo` | `SELECT recenti.titolo FROM (SELECT l.`id`, l.`titolo` FROM `libri` l WHERE l.anno >= 2020) recenti ORDER BY recenti.titolo ASC` | l'SQL rigenerato differisce dall'originale (da: «with recenti(select l.id,l.tit» ≠ «select recenti.titolo from(sel») |
| due livelli di annidamento | grafico ok | 2 | 0 | 2 | `SELECT s.cognome FROM soci s WHERE s.id IN (SELECT p.id_socio FROM prestiti p WHERE p.id_libro IN (SELECT l.id FROM libri l WHERE l.anno < 1950))` | `SELECT s.`cognome` FROM `soci` s WHERE s.id IN (SELECT p.`id_socio` FROM `prestiti` p WHERE p.id_libro IN (SELECT l.`id` FROM `libri` l WHERE l.anno < 1950))` |  |

**Totale: 7 «grafico ok» su 9.**
