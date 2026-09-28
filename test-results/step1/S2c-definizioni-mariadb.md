# S2c — definizioni di vista rilette da MariaDB (campionario per T8.2)

`VIEW_DEFINITION` di `information_schema.VIEWS`, testo esatto del server. Il nome del catalogo di test
(`ramasql_test_…`) è casuale a ogni esecuzione.

## v01_semplice — fixture

Sorgente scritta dall'utente:

```sql
SELECT titolo, anno, prezzo FROM libri WHERE anno >= 1980
```

Definizione riletta dal server:

```sql
select `ramasql_test_s2c_mula363jrs38`.`libri`.`titolo` AS `titolo`,`ramasql_test_s2c_mula363jrs38`.`libri`.`anno` AS `anno`,`ramasql_test_s2c_mula363jrs38`.`libri`.`prezzo` AS `prezzo` from `ramasql_test_s2c_mula363jrs38`.`libri` where `ramasql_test_s2c_mula363jrs38`.`libri`.`anno` >= 1980
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
select `l`.`titolo` AS `titolo`,`e`.`nome` AS `editore` from (`ramasql_test_s2c_mula363jrs38`.`libri` `l` join `ramasql_test_s2c_mula363jrs38`.`editori` `e` on(`l`.`id_editore` = `e`.`id`))
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
select `a`.`cognome` AS `cognome`,`a`.`nome` AS `nome`,`l`.`titolo` AS `titolo` from ((`ramasql_test_s2c_mula363jrs38`.`autori` `a` join `ramasql_test_s2c_mula363jrs38`.`libri_autori` `la` on(`la`.`id_autore` = `a`.`id`)) join `ramasql_test_s2c_mula363jrs38`.`libri` `l` on(`la`.`id_libro` = `l`.`id`))
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
select `s`.`id` AS `codice`,`s`.`cognome` AS `cognome_socio`,`s`.`email` AS `posta` from `ramasql_test_s2c_mula363jrs38`.`soci` `s`
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
select `e`.`nome` AS `editore`,count(`l`.`id`) AS `n_libri`,avg(`l`.`prezzo`) AS `prezzo_medio` from (`ramasql_test_s2c_mula363jrs38`.`editori` `e` join `ramasql_test_s2c_mula363jrs38`.`libri` `l` on(`l`.`id_editore` = `e`.`id`)) group by `e`.`nome` having count(`l`.`id`) >= 2
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
select concat(`s`.`nome`,' ',`s`.`cognome`) AS `socio`,year(`s`.`data_iscrizione`) AS `anno_iscrizione`,ifnull(`s`.`email`,'nessuna') AS `email` from `ramasql_test_s2c_mula363jrs38`.`soci` `s`
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
select `a`.`cognome` AS `cognome`,`a`.`nome` AS `nome` from (`ramasql_test_s2c_mula363jrs38`.`autori` `a` left join `ramasql_test_s2c_mula363jrs38`.`libri_autori` `la` on(`la`.`id_autore` = `a`.`id`)) where `la`.`id_libro` is null
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
select `l`.`titolo` AS `titolo`,`l`.`prezzo` AS `prezzo` from `ramasql_test_s2c_mula363jrs38`.`libri` `l` where `l`.`prezzo` > (select avg(`l2`.`prezzo`) from `ramasql_test_s2c_mula363jrs38`.`libri` `l2`)
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
select `v`.`editore` AS `editore`,`v`.`titolo` AS `titolo` from `ramasql_test_s2c_mula363jrs38`.`v02_join2` `v` where `v`.`editore` like 'E%'
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
select `l`.`titolo` AS `titolo`,`l`.`anno` AS `anno` from `ramasql_test_s2c_mula363jrs38`.`libri` `l` order by `l`.`anno` desc,`l`.`titolo` limit 5
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
select distinct `a`.`nazionalita` AS `nazionalita` from `ramasql_test_s2c_mula363jrs38`.`autori` `a` where `a`.`nazionalita` is not null
```

Dopo `ViewDefinitionNormalizer` (bozza):

```sql
select distinct `a`.`nazionalita` from `autori` `a` where `a`.`nazionalita` is not null
```

Parser del query builder sulla normalizzata: rappresentabile

## v_prestiti_dettaglio — bibliotecasoft (vista reale)

Sorgente scritta dall'utente:

```sql
-- non disponibile (vista nata altrove)
```

Definizione riletta dal server:

```sql
select `p`.`id` AS `id`,`p`.`stato` AS `stato`,`p`.`data_prestito` AS `data_prestito`,`p`.`data_restituzione_prevista` AS `data_restituzione_prevista`,`p`.`data_restituzione_effettiva` AS `data_restituzione_effettiva`,`p`.`note` AS `note`,`u`.`id` AS `utente_id`,concat(`u`.`nome`,' ',`u`.`cognome`) AS `utente_nome`,`u`.`email` AS `utente_email`,`u`.`codice_fiscale` AS `codice_fiscale`,`l`.`id` AS `libro_id`,`l`.`titolo` AS `libro_titolo`,`l`.`autore` AS `libro_autore`,`l`.`isbn` AS `libro_isbn`,`g`.`nome` AS `libro_genere`,concat(`a`.`nome`,' ',`a`.`cognome`) AS `operatore_nome`,to_days(coalesce(`p`.`data_restituzione_effettiva`,curdate())) - to_days(`p`.`data_restituzione_prevista`) AS `giorni_ritardo` from ((((`ramasql_test_s2c_bibliotecasoft_mula368qjghh`.`prestiti` `p` join `ramasql_test_s2c_bibliotecasoft_mula368qjghh`.`utenti` `u` on(`p`.`utente_id` = `u`.`id`)) join `ramasql_test_s2c_bibliotecasoft_mula368qjghh`.`libri` `l` on(`p`.`libro_id` = `l`.`id`)) left join `ramasql_test_s2c_bibliotecasoft_mula368qjghh`.`generi` `g` on(`l`.`genere_id` = `g`.`id`)) join `ramasql_test_s2c_bibliotecasoft_mula368qjghh`.`amministratori` `a` on(`p`.`admin_id` = `a`.`id`))
```

Dopo `ViewDefinitionNormalizer` (bozza):

```sql
select `p`.`id`,`p`.`stato`,`p`.`data_prestito`,`p`.`data_restituzione_prevista`,`p`.`data_restituzione_effettiva`,`p`.`note`,`u`.`id` AS `utente_id`,concat(`u`.`nome`,' ',`u`.`cognome`) AS `utente_nome`,`u`.`email` AS `utente_email`,`u`.`codice_fiscale`,`l`.`id` AS `libro_id`,`l`.`titolo` AS `libro_titolo`,`l`.`autore` AS `libro_autore`,`l`.`isbn` AS `libro_isbn`,`g`.`nome` AS `libro_genere`,concat(`a`.`nome`,' ',`a`.`cognome`) AS `operatore_nome`,to_days(coalesce(`p`.`data_restituzione_effettiva`,curdate())) - to_days(`p`.`data_restituzione_prevista`) AS `giorni_ritardo` from `prestiti` `p` join `utenti` `u` on `p`.`utente_id` = `u`.`id` join `libri` `l` on `p`.`libro_id` = `l`.`id` left join `generi` `g` on `l`.`genere_id` = `g`.`id` join `amministratori` `a` on `p`.`admin_id` = `a`.`id`
```

Parser del query builder sulla normalizzata: rappresentabile

## v_statistiche_libri — bibliotecasoft (vista reale)

Sorgente scritta dall'utente:

```sql
-- non disponibile (vista nata altrove)
```

Definizione riletta dal server:

```sql
select `l`.`id` AS `id`,`l`.`titolo` AS `titolo`,`l`.`autore` AS `autore`,`l`.`isbn` AS `isbn`,`l`.`editore` AS `editore`,`l`.`anno_pubblicazione` AS `anno_pubblicazione`,`g`.`nome` AS `genere`,`l`.`numero_copie` AS `numero_copie`,`l`.`copie_disponibili` AS `copie_disponibili`,count(case when `p`.`stato` = 'attivo' then 1 end) AS `prestiti_attivi`,count(`p`.`id`) AS `totale_prestiti` from ((`ramasql_test_s2c_bibliotecasoft_mula368qjghh`.`libri` `l` left join `ramasql_test_s2c_bibliotecasoft_mula368qjghh`.`generi` `g` on(`l`.`genere_id` = `g`.`id`)) left join `ramasql_test_s2c_bibliotecasoft_mula368qjghh`.`prestiti` `p` on(`l`.`id` = `p`.`libro_id`)) where `l`.`attivo` = 1 group by `l`.`id`
```

Dopo `ViewDefinitionNormalizer` (bozza):

```sql
select `l`.`id`,`l`.`titolo`,`l`.`autore`,`l`.`isbn`,`l`.`editore`,`l`.`anno_pubblicazione`,`g`.`nome` AS `genere`,`l`.`numero_copie`,`l`.`copie_disponibili`,count(case when `p`.`stato` = 'attivo' then 1 end) AS `prestiti_attivi`,count(`p`.`id`) AS `totale_prestiti` from `libri` `l` left join `generi` `g` on `l`.`genere_id` = `g`.`id` left join `prestiti` `p` on `l`.`id` = `p`.`libro_id` where `l`.`attivo` = 1 group by `l`.`id`
```

Parser del query builder sulla normalizzata: rappresentabile

