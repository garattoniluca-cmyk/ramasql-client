/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it.step7;

import java.util.List;

/**
 * Copia del campionario di T7.2 per T7.3 ({@link T73CampionarioSuiServerTest}).
 *
 * <p>L'originale è {@code sqleo-qb/src/test/java/it/ramasql/qb/T72Campionario.java} (lì la descrizione completa): questo
 * modulo non vede le classi di test di {@code sqleo-qb}. Il blocco fra i marcatori «inizio/fine campionario» dev'essere
 * <b>identico</b> nei due file: lo controlla {@code T72CampionarioTest#ilCampionarioEUgualeInDueModuli}. Per cambiare il
 * campionario si modifica l'originale e si ricopia qui il blocco.
 */
public final class Campionario {

    private Campionario() {
    }

    /**
     * Una query del campionario.
     *
     * @param n               numero progressivo
     * @param costrutto       cosa esercita
     * @param sql             testo come lo scriverebbe uno studente
     * @param rappresentabile esito atteso di {@code QbSql.check}: vero se la query deve riaprirsi nella vista grafica
     * @param nota            per le non rappresentabili: perché il costrutto resta solo testo
     */
    public record Caso(int n, String costrutto, String sql, boolean rappresentabile, String nota) {
        @Override
        public String toString() {
            return "Q" + n + " " + costrutto;
        }
    }

    private static Caso si(int n, String costrutto, String sql) {
        return new Caso(n, costrutto, sql, true, "");
    }

    private static Caso no(int n, String costrutto, String sql, String nota) {
        return new Caso(n, costrutto, sql, false, nota);
    }

    // --- inizio campionario ---
    public static final List<Caso> CASI = List.of(
            // 1-20: dallo spike S2b (10, 11, 12 con spareggio in ORDER BY)
            si(1, "SELECT semplice", "SELECT titolo, anno FROM libri"),
            si(2, "WHERE con confronto", "SELECT titolo, prezzo FROM libri WHERE prezzo > 20"),
            si(3, "alias di colonna e di tabella",
                    "SELECT l.titolo AS titolo_libro, l.anno FROM libri l WHERE l.anno >= 2000"),
            si(4, "INNER JOIN a 2 tabelle",
                    "SELECT l.titolo, e.nome FROM libri l INNER JOIN editori e ON l.id_editore = e.id"),
            si(5, "JOIN a 3 tabelle con tabella ponte",
                    "SELECT a.cognome, l.titolo FROM autori a INNER JOIN libri_autori la ON a.id = la.id_autore"
                            + " INNER JOIN libri l ON la.id_libro = l.id"),
            si(6, "LEFT JOIN",
                    "SELECT s.cognome, p.data_prestito FROM soci s LEFT JOIN prestiti p ON s.id = p.id_socio"),
            si(7, "RIGHT JOIN",
                    "SELECT e.nome, l.titolo FROM libri l RIGHT JOIN editori e ON l.id_editore = e.id"),
            si(8, "COUNT con GROUP BY",
                    "SELECT e.nome, COUNT(l.id) AS quanti FROM editori e INNER JOIN libri l ON e.id = l.id_editore"
                            + " GROUP BY e.nome"),
            si(9, "GROUP BY con HAVING",
                    "SELECT l.id_editore, AVG(l.prezzo) AS prezzo_medio FROM libri l GROUP BY l.id_editore"
                            + " HAVING AVG(l.prezzo) > 15"),
            si(10, "ORDER BY su più colonne, DESC",
                    "SELECT titolo, anno, prezzo FROM libri ORDER BY anno DESC, titolo, id"),
            si(11, "ORDER BY con LIMIT", "SELECT titolo, prezzo FROM libri ORDER BY prezzo DESC, id LIMIT 10"),
            si(12, "LIMIT con scostamento", "SELECT cognome, nome FROM soci ORDER BY cognome, nome, id LIMIT 20, 10"),
            si(13, "DISTINCT", "SELECT DISTINCT nazionalita FROM autori"),
            si(14, "IN con elenco di testi",
                    "SELECT cognome, nome FROM autori WHERE nazionalita IN ('italiana', 'francese', 'spagnola')"),
            si(15, "BETWEEN", "SELECT titolo FROM libri WHERE anno BETWEEN 1990 AND 2000"),
            si(16, "LIKE", "SELECT cognome, nome FROM soci WHERE cognome LIKE 'Ro%'"),
            si(17, "IS NULL (prestiti non resi)",
                    "SELECT p.id, p.data_prestito FROM prestiti p WHERE p.data_reso IS NULL"),
            si(18, "AND, OR e parentesi, IS NOT NULL",
                    "SELECT titolo FROM libri WHERE (anno < 1950 OR anno > 2010) AND isbn IS NOT NULL"),
            si(19, "backtick e nomi qualificati catalogo.tabella",
                    "SELECT `l`.`titolo`, `e`.`nome` FROM `biblioteca`.`libri` `l` INNER JOIN `biblioteca`.`editori` `e`"
                            + " ON `l`.`id_editore` = `e`.`id` WHERE `e`.`citta` = 'Bologna'"),
            si(20, "sottoquery con IN",
                    "SELECT s.cognome, s.nome FROM soci s WHERE s.id IN (SELECT p.id_socio FROM prestiti p"
                            + " WHERE p.data_reso IS NULL)"),

            // 21-49: aggiunte dello Step 7
            si(21, "COUNT(*)", "SELECT COUNT(*) AS totale FROM libri"),
            si(22, "MIN, MAX, SUM", "SELECT MIN(prezzo) AS minimo, MAX(prezzo) AS massimo, SUM(prezzo) AS somma FROM libri"),
            si(23, "COUNT DISTINCT", "SELECT COUNT(DISTINCT id_socio) AS soci_con_prestiti FROM prestiti"),
            si(24, "AVG per gruppo, ORDER BY sull'alias",
                    "SELECT e.nome, AVG(l.prezzo) AS media FROM editori e INNER JOIN libri l ON e.id = l.id_editore"
                            + " GROUP BY e.nome ORDER BY media DESC, e.nome"),
            si(25, "HAVING COUNT(*) (soci con più di 3 prestiti)",
                    "SELECT s.cognome, s.nome, COUNT(*) AS prestiti FROM soci s INNER JOIN prestiti p ON s.id = p.id_socio"
                            + " GROUP BY s.id, s.cognome, s.nome HAVING COUNT(*) > 3 ORDER BY s.cognome, s.nome, s.id"),
            si(26, "LEFT JOIN con IS NULL (libri senza editore)",
                    "SELECT l.titolo FROM libri l LEFT JOIN editori e ON l.id_editore = e.id WHERE e.id IS NULL"),
            si(27, "LEFT JOIN con COUNT (anche chi ha zero prestiti)",
                    "SELECT s.cognome, s.nome, COUNT(p.id) AS n FROM soci s LEFT JOIN prestiti p ON s.id = p.id_socio"
                            + " GROUP BY s.id, s.cognome, s.nome"),
            si(28, "catena di 4 tabelle (INNER)",
                    "SELECT s.cognome, l.titolo, e.nome, p.data_prestito FROM prestiti p INNER JOIN soci s ON p.id_socio = s.id"
                            + " INNER JOIN libri l ON p.id_libro = l.id INNER JOIN editori e ON l.id_editore = e.id"),
            si(29, "join misti: INNER poi LEFT",
                    "SELECT l.titolo, e.nome, p.data_prestito FROM libri l INNER JOIN editori e ON l.id_editore = e.id"
                            + " LEFT JOIN prestiti p ON l.id = p.id_libro"),
            si(30, "join misti: LEFT poi INNER",
                    "SELECT e.nome, l.titolo, p.data_prestito FROM editori e LEFT JOIN libri l ON e.id = l.id_editore"
                            + " INNER JOIN prestiti p ON l.id = p.id_libro"),
            si(31, "join misti a stella: LEFT, INNER, INNER",
                    "SELECT l.titolo, e.nome, a.cognome FROM libri l LEFT JOIN editori e ON l.id_editore = e.id"
                            + " INNER JOIN libri_autori la ON l.id = la.id_libro INNER JOIN autori a ON la.id_autore = a.id"),
            si(32, "catena di 3 tabelle con due LEFT",
                    "SELECT a.cognome, l.titolo FROM autori a LEFT JOIN libri_autori la ON a.id = la.id_autore"
                            + " LEFT JOIN libri l ON la.id_libro = l.id"),
            si(33, "RIGHT JOIN con IS NULL (libri senza editore)",
                    "SELECT l.titolo FROM editori e RIGHT JOIN libri l ON e.id = l.id_editore WHERE e.id IS NULL"),
            si(34, "NOT IN con sottoquery (libri non in prestito)",
                    "SELECT l.titolo FROM libri l WHERE l.id NOT IN (SELECT p.id_libro FROM prestiti p WHERE p.data_reso IS NULL)"),
            si(35, "EXISTS correlata",
                    "SELECT e.nome FROM editori e WHERE EXISTS (SELECT l.id FROM libri l WHERE l.id_editore = e.id)"),
            si(36, "NOT EXISTS correlata (soci senza prestiti aperti)",
                    "SELECT s.cognome, s.nome FROM soci s WHERE NOT EXISTS (SELECT p.id FROM prestiti p WHERE p.id_socio = s.id"
                            + " AND p.data_reso IS NULL)"),
            si(37, "confronto con (SELECT AVG…)",
                    "SELECT l.titolo, l.prezzo FROM libri l WHERE l.prezzo > (SELECT AVG(l2.prezzo) FROM libri l2)"),
            si(38, "sottoquery nella lista SELECT",
                    "SELECT e.nome, (SELECT COUNT(*) FROM libri l WHERE l.id_editore = e.id) AS n_libri FROM editori e"),
            si(39, "tabella derivata in FROM, filtrata (libri prestati più di 2 volte)",
                    "SELECT t.id_libro, t.n FROM (SELECT p.id_libro, COUNT(*) AS n FROM prestiti p GROUP BY p.id_libro) t"
                            + " WHERE t.n > 2"),
            si(40, "NOT LIKE", "SELECT titolo FROM libri WHERE titolo NOT LIKE 'Il %'"),
            si(41, "IN con elenco di numeri", "SELECT titolo, anno FROM libri WHERE id_editore IN (1, 2, 3)"),
            si(42, "CONCAT", "SELECT CONCAT(cognome, ' ', nome) AS nominativo FROM autori"),
            si(43, "YEAR in SELECT e in WHERE",
                    "SELECT id, YEAR(data_prestito) AS anno FROM prestiti WHERE YEAR(data_prestito) = 2025"),
            si(44, "UPPER", "SELECT UPPER(cognome) AS cognome_maiuscolo FROM soci"),
            si(45, "IFNULL e COALESCE",
                    "SELECT nome, IFNULL(citta, 'sconosciuta') AS citta, COALESCE(citta, nome) AS dove FROM editori"),
            si(46, "DATEDIFF",
                    "SELECT p.id, DATEDIFF(p.data_reso, p.data_prestito) AS giorni FROM prestiti p WHERE p.data_reso IS NOT NULL"),
            si(47, "ROUND ed espressione aritmetica", "SELECT titolo, ROUND(prezzo * 1.22, 2) AS prezzo_ivato FROM libri"),
            si(48, "CASE WHEN",
                    "SELECT titolo, CASE WHEN prezzo < 10 THEN 'economico' WHEN prezzo < 20 THEN 'medio' ELSE 'caro' END AS fascia"
                            + " FROM libri"),
            si(49, "espressione aritmetica in WHERE", "SELECT titolo, prezzo FROM libri WHERE prezzo * 2 > 30"),
            si(50, "GROUP BY su un'espressione (prestiti per anno)",
                    "SELECT YEAR(p.data_prestito) AS anno, COUNT(*) AS n FROM prestiti p GROUP BY YEAR(p.data_prestito)"
                            + " ORDER BY anno"),
            si(51, "DISTINCT su un join",
                    "SELECT DISTINCT e.citta FROM editori e INNER JOIN libri l ON e.id = l.id_editore WHERE e.citta IS NOT NULL"),
            si(52, "nomi qualificati senza backtick",
                    "SELECT l.titolo, p.data_prestito FROM biblioteca.libri l INNER JOIN biblioteca.prestiti p"
                            + " ON l.id = p.id_libro WHERE p.data_reso IS NULL"),
            si(53, "IN con sottoquery su un join",
                    "SELECT a.cognome, a.nome FROM autori a WHERE a.id IN (SELECT la.id_autore FROM libri_autori la"
                            + " INNER JOIN libri l ON la.id_libro = l.id WHERE l.anno < 1950)"),

            si(54, "join misti ad albero: INNER, LEFT, INNER, LEFT (libri, autori, prestiti eventuali, soci)",
                    "SELECT l.titolo, a.cognome, p.data_prestito, s.cognome FROM libri l"
                            + " INNER JOIN libri_autori la ON l.id = la.id_libro LEFT JOIN prestiti p ON l.id = p.id_libro"
                            + " INNER JOIN autori a ON la.id_autore = a.id LEFT JOIN soci s ON p.id_socio = s.id"),
            si(55, "RIGHT JOIN dopo INNER JOIN",
                    "SELECT a.cognome, l.titolo FROM libri l INNER JOIN libri_autori la ON l.id = la.id_libro"
                            + " RIGHT JOIN autori a ON la.id_autore = a.id"),

            // 56-66: costrutti che DEVONO restare solo testo
            no(56, "funzione finestra ROW_NUMBER() OVER (…)",
                    "SELECT titolo, ROW_NUMBER() OVER (PARTITION BY id_editore ORDER BY prezzo DESC) AS pos FROM libri",
                    "il modello non ha finestre: PARTITION BY/ORDER BY dentro OVER si perderebbero o cambierebbero"),
            no(57, "CTE WITH",
                    "WITH recenti AS (SELECT l.id, l.titolo FROM libri l WHERE l.anno >= 1990)"
                            + " SELECT r.titolo FROM recenti r ORDER BY r.titolo",
                    "il parser sostituisce la CTE con una tabella derivata: il testo cambierebbe (F-05bis: CTE solo testo)"),
            no(58, "WITH RECURSIVE",
                    "WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 5) SELECT i FROM n",
                    "ricorsione non rappresentabile in un diagramma"),
            no(59, "UNION ALL", "SELECT cognome FROM autori UNION ALL SELECT cognome FROM soci",
                    "il modello ereditato conosce solo UNION: ALL andrebbe perso (risultato diverso)"),
            no(60, "JOIN … USING (…)",
                    "SELECT la.id_autore, p.data_prestito FROM libri_autori la INNER JOIN prestiti p USING (id_libro)",
                    "il modello di join vuole una ON colonna = colonna; USING andrebbe riscritto"),
            no(61, "CROSS JOIN", "SELECT l.titolo, e.nome FROM libri l CROSS JOIN editori e",
                    "il modello non ha il tipo CROSS: diventerebbe «FROM libri l, editori e» (stesso risultato,"
                            + " ma il testo dell'utente cambierebbe, R-03)"),
            no(62, "JOIN … ON a = b AND condizione",
                    "SELECT l.titolo, e.nome FROM libri l LEFT JOIN editori e ON l.id_editore = e.id AND e.citta = 'Bologna'",
                    "il parser sposta la condizione in WHERE: con LEFT JOIN il significato cambia"),
            no(63, "commenti --",
                    "SELECT titolo -- il titolo\nFROM libri -- tutti i libri\nWHERE anno > 2000",
                    "il modello non conserva i commenti: andrebbero persi"),
            no(64, "ORDER BY e LIMIT dentro una sottoquery",
                    "SELECT s.cognome FROM soci s WHERE s.id IN (SELECT p.id_socio FROM prestiti p"
                            + " ORDER BY p.data_prestito DESC LIMIT 5)",
                    "il modello ha ORDER BY/LIMIT solo per la query principale (e il server rifiuta LIMIT in IN)"),
            no(65, "testo rotto (parentesi non chiusa)", "SELECT titolo FROM libri WHERE (anno > 2000",
                    "non è una SELECT valida"),
            no(66, "testo rotto (clausola a metà)", "SELECT titolo, FROM libri WHERE",
                    "non è una SELECT valida"),

            // 67-: altre query rappresentabili, le più comuni nei primi esercizi
            si(67, "SELECT *", "SELECT * FROM editori"),
            si(68, "tabella.* in un join",
                    "SELECT l.*, e.nome FROM libri l INNER JOIN editori e ON l.id_editore = e.id"),
            si(69, "testo con apice raddoppiato", "SELECT nome FROM editori WHERE citta = 'L''Aquila'"),
            si(70, "intervallo di date",
                    "SELECT id, data_prestito FROM prestiti WHERE data_prestito >= '2025-03-01' AND data_prestito < '2025-04-01'"),
            si(71, "LIMIT … OFFSET …", "SELECT titolo, anno FROM libri ORDER BY anno DESC, id LIMIT 5 OFFSET 10"),
            si(72, "ORDER BY su un'aggregata",
                    "SELECT id_editore, COUNT(*) AS n FROM libri GROUP BY id_editore ORDER BY COUNT(*) DESC, id_editore"),
            si(73, "alias di colonna senza AS", "SELECT titolo titolo_libro, prezzo costo FROM libri WHERE prezzo < 10"),
            si(74, "NOT BETWEEN e OR", "SELECT titolo, anno FROM libri WHERE anno NOT BETWEEN 1900 AND 2000 OR anno IS NULL"),
            si(75, "HAVING con SUM e join",
                    "SELECT e.nome, SUM(l.prezzo) AS valore FROM editori e INNER JOIN libri l ON e.id = l.id_editore"
                            + " GROUP BY e.nome HAVING SUM(l.prezzo) > 200"));
    // --- fine campionario ---
}
