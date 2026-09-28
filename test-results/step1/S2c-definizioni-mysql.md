# S2c — definizioni di vista rilette da MySQL (campionario per T8.2)

`VIEW_DEFINITION` di `information_schema.VIEWS`, testo esatto del server. Il nome del catalogo di test
(`ramasql_test_…`) è casuale a ogni esecuzione.

## v01_semplice — fixture

Sorgente scritta dall'utente:

```sql
SELECT titolo, anno, prezzo FROM libri WHERE anno >= 1980
```

Definizione riletta dal server:

```sql
select `ramasql_test_s2c_mulldqej3ffs`.`libri`.`titolo` AS `titolo`,`ramasql_test_s2c_mulldqej3ffs`.`libri`.`anno` AS `anno`,`ramasql_test_s2c_mulldqej3ffs`.`libri`.`prezzo` AS `prezzo` from `ramasql_test_s2c_mulldqej3ffs`.`libri` where (`ramasql_test_s2c_mulldqej3ffs`.`libri`.`anno` >= 1980)
```

Dopo `ViewDefinitionNormalizer` (bozza):

```sql
select `libri`.`titolo`,`libri`.`anno`,`libri`.`prezzo` from `libri` where `libri`.`anno` >= 1980
```

Parser del query builder sulla normalizzata: rappresentabile

## v02_join2 — fixture

Sorgente scritta dall'utente:

```sql
SELECT l.titolo, e.nome AS editore FROM libri l INNER JOIN editori e ON l.id_editore = e.id
```

Definizione riletta dal server:

```sql
select `l`.`titolo` AS `titolo`,`e`.`nome` AS `editore` from (`ramasql_test_s2c_mulldqej3ffs`.`libri` `l` join `ramasql_test_s2c_mulldqej3ffs`.`editori` `e` on((`l`.`id_editore` = `e`.`id`)))
```

Dopo `ViewDefinitionNormalizer` (bozza):

```sql
select `l`.`titolo`,`e`.`nome` AS `editore` from `libri` `l` join `editori` `e` on `l`.`id_editore` = `e`.`id`
```

Parser del query builder sulla normalizzata: rappresentabile

## v03_join3 — fixture

Sorgente scritta dall'utente:

```sql
SELECT a.cognome, a.nome, l.titolo FROM autori a INNER JOIN libri_autori la ON la.id_autore = a.id INNER JOIN libri l ON la.id_libro = l.id
```

Definizione riletta dal server:

```sql
select `a`.`cognome` AS `cognome`,`a`.`nome` AS `nome`,`l`.`titolo` AS `titolo` from ((`ramasql_test_s2c_mulldqej3ffs`.`autori` `a` join `ramasql_test_s2c_mulldqej3ffs`.`libri_autori` `la` on((`la`.`id_autore` = `a`.`id`))) join `ramasql_test_s2c_mulldqej3ffs`.`libri` `l` on((`la`.`id_libro` = `l`.`id`)))
```

Dopo `ViewDefinitionNormalizer` (bozza):

```sql
select `a`.`cognome`,`a`.`nome`,`l`.`titolo` from `autori` `a` join `libri_autori` `la` on `la`.`id_autore` = `a`.`id` join `libri` `l` on `la`.`id_libro` = `l`.`id`
```

Parser del query builder sulla normalizzata: rappresentabile

## v04_alias — fixture

Sorgente scritta dall'utente:

```sql
SELECT s.id AS codice, s.cognome AS cognome_socio, s.email AS posta FROM soci s
```

Definizione riletta dal server:

```sql
select `s`.`id` AS `codice`,`s`.`cognome` AS `cognome_socio`,`s`.`email` AS `posta` from `ramasql_test_s2c_mulldqej3ffs`.`soci` `s`
```

Dopo `ViewDefinitionNormalizer` (bozza):

```sql
select `s`.`id` AS `codice`,`s`.`cognome` AS `cognome_socio`,`s`.`email` AS `posta` from `soci` `s`
```

Parser del query builder sulla normalizzata: rappresentabile

## v05_aggregata — fixture

Sorgente scritta dall'utente:

```sql
SELECT e.nome AS editore, COUNT(l.id) AS n_libri, AVG(l.prezzo) AS prezzo_medio FROM editori e INNER JOIN libri l ON l.id_editore = e.id GROUP BY e.nome HAVING COUNT(l.id) >= 2
```

Definizione riletta dal server:

```sql
select `e`.`nome` AS `editore`,count(`l`.`id`) AS `n_libri`,avg(`l`.`prezzo`) AS `prezzo_medio` from (`ramasql_test_s2c_mulldqej3ffs`.`editori` `e` join `ramasql_test_s2c_mulldqej3ffs`.`libri` `l` on((`l`.`id_editore` = `e`.`id`))) group by `e`.`nome` having (count(`l`.`id`) >= 2)
```

Dopo `ViewDefinitionNormalizer` (bozza):

```sql
select `e`.`nome` AS `editore`,count(`l`.`id`) AS `n_libri`,avg(`l`.`prezzo`) AS `prezzo_medio` from `editori` `e` join `libri` `l` on `l`.`id_editore` = `e`.`id` group by `e`.`nome` having count(`l`.`id`) >= 2
```

Parser del query builder sulla normalizzata: rappresentabile

## v06_funzioni — fixture

Sorgente scritta dall'utente:

```sql
SELECT CONCAT(s.nome, ' ', s.cognome) AS socio, YEAR(s.data_iscrizione) AS anno_iscrizione, IFNULL(s.email, 'nessuna') AS email FROM soci s
```

Definizione riletta dal server:

```sql
select concat(`s`.`nome`,' ',`s`.`cognome`) AS `socio`,year(`s`.`data_iscrizione`) AS `anno_iscrizione`,ifnull(`s`.`email`,'nessuna') AS `email` from `ramasql_test_s2c_mulldqej3ffs`.`soci` `s`
```

Dopo `ViewDefinitionNormalizer` (bozza):

```sql
select concat(`s`.`nome`,' ',`s`.`cognome`) AS `socio`,year(`s`.`data_iscrizione`) AS `anno_iscrizione`,ifnull(`s`.`email`,'nessuna') AS `email` from `soci` `s`
```

Parser del query builder sulla normalizzata: rappresentabile

## v07_left_join_is_null — fixture

Sorgente scritta dall'utente:

```sql
SELECT a.cognome, a.nome FROM autori a LEFT JOIN libri_autori la ON la.id_autore = a.id WHERE la.id_libro IS NULL
```

Definizione riletta dal server:

```sql
select `a`.`cognome` AS `cognome`,`a`.`nome` AS `nome` from (`ramasql_test_s2c_mulldqej3ffs`.`autori` `a` left join `ramasql_test_s2c_mulldqej3ffs`.`libri_autori` `la` on((`la`.`id_autore` = `a`.`id`))) where (`la`.`id_libro` is null)
```

Dopo `ViewDefinitionNormalizer` (bozza):

```sql
select `a`.`cognome`,`a`.`nome` from `autori` `a` left join `libri_autori` `la` on `la`.`id_autore` = `a`.`id` where `la`.`id_libro` is null
```

Parser del query builder sulla normalizzata: rappresentabile

## v08_sottoquery — fixture

Sorgente scritta dall'utente:

```sql
SELECT l.titolo, l.prezzo FROM libri l WHERE l.prezzo > (SELECT AVG(l2.prezzo) FROM libri l2)
```

Definizione riletta dal server:

```sql
select `l`.`titolo` AS `titolo`,`l`.`prezzo` AS `prezzo` from `ramasql_test_s2c_mulldqej3ffs`.`libri` `l` where (`l`.`prezzo` > (select avg(`l2`.`prezzo`) from `ramasql_test_s2c_mulldqej3ffs`.`libri` `l2`))
```

Dopo `ViewDefinitionNormalizer` (bozza):

```sql
select `l`.`titolo`,`l`.`prezzo` from `libri` `l` where `l`.`prezzo` > (select avg(`l2`.`prezzo`) from `libri` `l2`)
```

Parser del query builder sulla normalizzata: rappresentabile

## v09_vista_su_vista — fixture

Sorgente scritta dall'utente:

```sql
SELECT v.editore, v.titolo FROM v02_join2 v WHERE v.editore LIKE 'E%'
```

Definizione riletta dal server:

```sql
select `ramasql_test_s2c_mulldqej3ffs`.`v`.`editore` AS `editore`,`ramasql_test_s2c_mulldqej3ffs`.`v`.`titolo` AS `titolo` from `ramasql_test_s2c_mulldqej3ffs`.`v02_join2` `v` where (`ramasql_test_s2c_mulldqej3ffs`.`v`.`editore` like 'E%')
```

Dopo `ViewDefinitionNormalizer` (bozza):

```sql
select `v`.`editore`,`v`.`titolo` from `v02_join2` `v` where `v`.`editore` like 'E%'
```

Parser del query builder sulla normalizzata: rappresentabile

## v10_order_limit — fixture

Sorgente scritta dall'utente:

```sql
SELECT l.titolo, l.anno FROM libri l ORDER BY l.anno DESC, l.titolo LIMIT 5
```

Definizione riletta dal server:

```sql
select `l`.`titolo` AS `titolo`,`l`.`anno` AS `anno` from `ramasql_test_s2c_mulldqej3ffs`.`libri` `l` order by `l`.`anno` desc,`l`.`titolo` limit 5
```

Dopo `ViewDefinitionNormalizer` (bozza):

```sql
select `l`.`titolo`,`l`.`anno` from `libri` `l` order by `l`.`anno` desc,`l`.`titolo` limit 5
```

Parser del query builder sulla normalizzata: rappresentabile

## v11_distinct — fixture

Sorgente scritta dall'utente:

```sql
SELECT DISTINCT a.nazionalita FROM autori a WHERE a.nazionalita IS NOT NULL
```

Definizione riletta dal server:

```sql
select distinct `a`.`nazionalita` AS `nazionalita` from `ramasql_test_s2c_mulldqej3ffs`.`autori` `a` where (`a`.`nazionalita` is not null)
```

Dopo `ViewDefinitionNormalizer` (bozza):

```sql
select distinct `a`.`nazionalita` from `autori` `a` where `a`.`nazionalita` is not null
```

Parser del query builder sulla normalizzata: rappresentabile

## v_alunni_classi — scuola (schema reale, vista di prova)

Sorgente scritta dall'utente:

```sql
SELECT a.cognome, a.nome, c.nome AS classe, c.aula FROM alunni a LEFT JOIN classi c ON a.classe_id = c.id
```

Definizione riletta dal server:

```sql
select `a`.`cognome` AS `cognome`,`a`.`nome` AS `nome`,`c`.`nome` AS `classe`,`c`.`aula` AS `aula` from (`ramasql_test_s2c_scuola_mulldqjjcx1c`.`alunni` `a` left join `ramasql_test_s2c_scuola_mulldqjjcx1c`.`classi` `c` on((`a`.`classe_id` = `c`.`id`)))
```

Dopo `ViewDefinitionNormalizer` (bozza):

```sql
select `a`.`cognome`,`a`.`nome`,`c`.`nome` AS `classe`,`c`.`aula` from `alunni` `a` left join `classi` `c` on `a`.`classe_id` = `c`.`id`
```

Parser del query builder sulla normalizzata: rappresentabile

## v_corsi_per_classe — scuola (schema reale, vista di prova)

Sorgente scritta dall'utente:

```sql
SELECT c.nome AS classe, COUNT(cc.corso_id) AS n_corsi, SUM(k.ore_settimanali) AS ore FROM classi c INNER JOIN corsi_classi cc ON cc.classe_id = c.id INNER JOIN corsi k ON cc.corso_id = k.id GROUP BY c.nome
```

Definizione riletta dal server:

```sql
select `c`.`nome` AS `classe`,count(`cc`.`corso_id`) AS `n_corsi`,sum(`k`.`ore_settimanali`) AS `ore` from ((`ramasql_test_s2c_scuola_mulldqjjcx1c`.`classi` `c` join `ramasql_test_s2c_scuola_mulldqjjcx1c`.`corsi_classi` `cc` on((`cc`.`classe_id` = `c`.`id`))) join `ramasql_test_s2c_scuola_mulldqjjcx1c`.`corsi` `k` on((`cc`.`corso_id` = `k`.`id`))) group by `c`.`nome`
```

Dopo `ViewDefinitionNormalizer` (bozza):

```sql
select `c`.`nome` AS `classe`,count(`cc`.`corso_id`) AS `n_corsi`,sum(`k`.`ore_settimanali`) AS `ore` from `classi` `c` join `corsi_classi` `cc` on `cc`.`classe_id` = `c`.`id` join `corsi` `k` on `cc`.`corso_id` = `k`.`id` group by `c`.`nome`
```

Parser del query builder sulla normalizzata: rappresentabile

