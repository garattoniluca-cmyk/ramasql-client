# S2b - SQL -> modello -> SQL: campionario di 20 query «da aula» (schema biblioteca)

Generato da `it.ramasql.qb.spike.S2bParserCampionarioTest`. «ok» = il parser produce un modello e l'SQL rigenerato equivale all'originale (confronto normalizzato: spazi, maiuscole, backtick, AS/INNER/OUTER/ASC facoltativi).

| # | Costrutto | Esito | SQL originale | SQL rigenerato | Avvisi del parser / motivo |
|---|---|---|---|---|---|
| 1 | SELECT semplice | ok | `SELECT titolo, anno FROM libri` | `SELECT titolo, anno FROM `libri`` |  |
| 2 | WHERE con confronto | ok | `SELECT titolo, prezzo FROM libri WHERE prezzo > 20` | `SELECT titolo, prezzo FROM `libri` WHERE prezzo > 20` |  |
| 3 | alias di colonna e di tabella | ok | `SELECT l.titolo AS titolo_libro, l.anno FROM libri l WHERE l.anno >= 2000` | `SELECT l.`titolo` AS titolo_libro, l.`anno` FROM `libri` l WHERE l.anno >= 2000` |  |
| 4 | INNER JOIN a 2 tabelle | ok | `SELECT l.titolo, e.nome FROM libri l INNER JOIN editori e ON l.id_editore = e.id` | `SELECT l.`titolo`, e.`nome` FROM `libri` l INNER JOIN `editori` e  ON l.`id_editore` = e.`id`` |  |
| 5 | JOIN a 3 tabelle con tabella ponte | ok | `SELECT a.cognome, l.titolo FROM autori a INNER JOIN libri_autori la ON a.id = la.id_autore INNER JOIN libri l ON la.id_libro = l.id` | `SELECT a.`cognome`, l.`titolo` FROM `autori` a INNER JOIN `libri_autori` la  ON a.`id` = la.`id_autore` INNER JOIN `libri` l  ON la.`id_libro` = l.`id`` |  |
| 6 | LEFT JOIN | ok | `SELECT s.cognome, p.data_prestito FROM soci s LEFT JOIN prestiti p ON s.id = p.id_socio` | `SELECT s.`cognome`, p.`data_prestito` FROM `soci` s LEFT OUTER JOIN `prestiti` p  ON s.`id` = p.`id_socio`` |  |
| 7 | RIGHT JOIN | ok | `SELECT e.nome, l.titolo FROM libri l RIGHT JOIN editori e ON l.id_editore = e.id` | `SELECT e.`nome`, l.`titolo` FROM `libri` l RIGHT OUTER JOIN `editori` e  ON l.`id_editore` = e.`id`` |  |
| 8 | COUNT con GROUP BY | ok | `SELECT e.nome, COUNT(l.id) AS quanti FROM editori e INNER JOIN libri l ON e.id = l.id_editore GROUP BY e.nome` | `SELECT e.`nome`, COUNT( l.id ) AS quanti FROM `editori` e INNER JOIN `libri` l  ON e.`id` = l.`id_editore` GROUP BY e.nome` |  |
| 9 | GROUP BY con HAVING | ok | `SELECT l.id_editore, AVG(l.prezzo) AS prezzo_medio FROM libri l GROUP BY l.id_editore HAVING AVG(l.prezzo) > 15` | `SELECT l.`id_editore`, AVG( l.prezzo ) AS prezzo_medio FROM `libri` l GROUP BY l.id_editore HAVING AVG(l.prezzo) > 15` |  |
| 10 | ORDER BY su due colonne, DESC | ok | `SELECT titolo, anno, prezzo FROM libri ORDER BY anno DESC, titolo` | `SELECT titolo, anno, prezzo FROM `libri` ORDER BY anno DESC, titolo ASC` |  |
| 11 | LIMIT | ok | `SELECT titolo, prezzo FROM libri ORDER BY prezzo DESC LIMIT 10` | `SELECT titolo, prezzo FROM `libri` ORDER BY prezzo DESC LIMIT 10` |  |
| 12 | LIMIT con scostamento | ok | `SELECT cognome, nome FROM soci ORDER BY cognome LIMIT 20, 10` | `SELECT cognome, nome FROM `soci` ORDER BY cognome ASC LIMIT 20, 10` |  |
| 13 | DISTINCT | ok | `SELECT DISTINCT nazionalita FROM autori` | `SELECT DISTINCT nazionalita FROM `autori`` |  |
| 14 | IN con elenco | ok | `SELECT cognome, nome FROM autori WHERE nazionalita IN ('Italia', 'Francia', 'Spagna')` | `SELECT cognome, nome FROM `autori` WHERE nazionalita IN ('Italia','Francia','Spagna')` |  |
| 15 | BETWEEN | ok | `SELECT titolo FROM libri WHERE anno BETWEEN 1990 AND 2000` | `SELECT titolo FROM `libri` WHERE anno BETWEEN 1990 AND 2000` |  |
| 16 | LIKE | ok | `SELECT cognome, nome FROM soci WHERE cognome LIKE 'Ro%'` | `SELECT cognome, nome FROM `soci` WHERE cognome LIKE 'Ro%'` |  |
| 17 | IS NULL (prestiti non resi) | ok | `SELECT p.id, p.data_prestito FROM prestiti p WHERE p.data_reso IS NULL` | `SELECT p.`id`, p.`data_prestito` FROM `prestiti` p WHERE p.data_reso IS NULL` |  |
| 18 | AND, OR e parentesi, IS NOT NULL | ok | `SELECT titolo FROM libri WHERE (anno < 1950 OR anno > 2010) AND prezzo IS NOT NULL` | `SELECT titolo FROM `libri` WHERE (anno < 1950 OR anno > 2010) AND prezzo IS NOT NULL` |  |
| 19 | backtick e nomi qualificati catalogo.tabella | ok | `SELECT `l`.`titolo`, `e`.`nome` FROM `biblioteca`.`libri` `l` INNER JOIN `biblioteca`.`editori` `e` ON `l`.`id_editore` = `e`.`id` WHERE `e`.`citta` = 'Milano'` | `SELECT `l`.`titolo`, `e`.`nome` FROM `biblioteca`.`libri` `l` INNER JOIN `biblioteca`.`editori` `e`  ON `l`.`id_editore` = `e`.`id` WHERE `e`.`citta` = 'Milano'` |  |
| 20 | sottoquery con IN | ok | `SELECT s.cognome, s.nome FROM soci s WHERE s.id IN (SELECT p.id_socio FROM prestiti p WHERE p.data_reso IS NULL)` | `SELECT s.`cognome`, s.`nome` FROM `soci` s WHERE s.id IN (SELECT p.`id_socio` FROM `prestiti` p WHERE p.data_reso IS NULL)` |  |

## Costrutti deboli (fuori dal criterio): devono risultare «non rappresentabile»

| # | Costrutto | Esito | SQL originale | SQL rigenerato (che andrebbe perso/cambiato) | Avvisi del parser / motivo |
|---|---|---|---|---|---|
| 21 | JOIN … ON a=b AND condizione | non rappresentabile (intercettato) | `SELECT l.titolo, e.nome FROM libri l INNER JOIN editori e ON l.id_editore = e.id AND e.citta = 'Milano'` | `SELECT l.`titolo`, e.`nome` FROM `libri` l INNER JOIN `editori` e  ON l.`id_editore` = e.`id` WHERE e.`citta` = 'Milano'` | !!! WARNING conditions on join are converted into where clause( e.`citta` = 'Milano' )!!!; l'SQL rigenerato differisce dall'originale (da: «d_editore and e.citta='Milano'» ≠ «d_editore where e.citta='Milano'») |
| 22 | JOIN … USING (…) | non rappresentabile (intercettato) | `SELECT p.data_prestito, s.cognome FROM prestiti p INNER JOIN soci s USING (id_socio)` | `SELECT p.data_prestito, s.cognome FROM `soci` id_socio` | Table or alias not found: p; l'SQL rigenerato differisce dall'originale (da: «nome from prestiti p join soci s using(i» ≠ «nome from soci id_socio») |
| 23 | CROSS JOIN | non rappresentabile (intercettato) | `SELECT l.titolo, e.nome FROM libri l CROSS JOIN editori e` | `SELECT l.`titolo`, e.`nome` FROM `libri` l, `editori` e` | l'SQL rigenerato differisce dall'originale (da: «om libri l cross join editori e» ≠ «om libri l,editori e») |
| 24 | commenti -- | non rappresentabile (intercettato) | `SELECT titolo -- il titolo FROM libri -- tutti i libri WHERE anno > 2000` | `SELECT titolo FROM `libri` WHERE anno > 2000` | il testo non è una SELECT valida: tre nomi di fila senza operatore né virgola |
| 25 | ORDER BY e LIMIT dentro una sottoquery | non rappresentabile (intercettato) | `SELECT s.cognome FROM soci s WHERE s.id IN (SELECT p.id_socio FROM prestiti p ORDER BY p.data_prestito DESC LIMIT 5)` | `SELECT s.`cognome` FROM `soci` s WHERE s.id IN (SELECT p.`id_socio` FROM `prestiti` p) ORDER BY p.data_prestito DESC LIMIT 5 )` | l'SQL rigenerato differisce dall'originale (da: «prestiti p order by p.data_prestito desc» ≠ «prestiti p)order by p.data_prestito desc») |
| 26 | UNION ALL | non rappresentabile (intercettato) | `SELECT cognome FROM autori UNION ALL SELECT cognome FROM soci` | `SELECT cognome FROM `autori` UNION SELECT cognome FROM `soci`` | !!! UNION ALL changed in UNION syntax !!!; l'SQL rigenerato differisce dall'originale (da: «ori union all select cognome from soci» ≠ «ori union select cognome from soci») |
| 27 | funzione finestra ROW_NUMBER() OVER (…) | non rappresentabile (intercettato) | `SELECT titolo, ROW_NUMBER() OVER (PARTITION BY id_editore ORDER BY prezzo DESC) AS pos FROM libri` | `SELECT titolo, ROW_NUMBER( )( PARTITION BY id_editore Order by prezzo DESC ) AS pos FROM `libri`` | l'SQL rigenerato differisce dall'originale (da: «w_number()over(partition by id_editore o» ≠ «w_number()(partition by id_editore order») |
| 28 | WITH RECURSIVE | non rappresentabile (intercettato) | `WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 5) SELECT i FROM n` | `SELECT i FROM (SELECT 1 AS i UNION SELECT i + 1 FROM `n` WHERE i < 5) n` | !!! UNION ALL changed in UNION syntax !!!; l'SQL rigenerato differisce dall'originale (da: «with recursive n(select 1 i un» ≠ «select i from(select 1 i union») |

Costrutti deboli intercettati: 8 su 8. Per questi la vista grafica si disattiva e il testo resta quello dell'utente.

**Totale: 20 ok su 20** (criterio: almeno 15). Query su cui il parser ha lanciato un'eccezione (gestita): 0.
