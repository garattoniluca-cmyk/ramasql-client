/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package com.sqleo.querybuilder;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import it.ramasql.qb.BasicQbHost;
import it.ramasql.qb.QbMetadata;
import it.ramasql.qb.QbOption;

/**
 * Metadati finti dello schema canonico «biblioteca» (docs/ROADMAP.md), senza database, per le prove dello Step 12:
 * ogni lettura può essere rallentata ({@link #delayMs}) e viene registrata con il thread che l'ha fatta (per dimostrare
 * che nessuna lettura avviene sull'EDT, {@code BUG-024}).
 */
final class BibliotecaFinta implements QbMetadata {

    static final String CATALOGO = "biblioteca";

    private static final Map<String, String[][]> TABELLE = new LinkedHashMap<>();
    /** FK: nome, tabella primaria, colonna primaria, tabella esterna, colonna esterna. */
    private static final String[][] FK = {
            {"fk_libri_editori", "editori", "id", "libri", "id_editore"},
            {"fk_la_libri", "libri", "id", "libri_autori", "id_libro"},
            {"fk_la_autori", "autori", "id", "libri_autori", "id_autore"},
            {"fk_prestiti_libri", "libri", "id", "prestiti", "id_libro"},
            {"fk_prestiti_soci", "soci", "id", "prestiti", "id_socio"},
    };

    static {
        // colonna, tipo, chiave primaria (P)
        TABELLE.put("autori", new String[][] {{"id", "INT", "P"}, {"cognome", "VARCHAR(60)", ""},
                {"nome", "VARCHAR(60)", ""}, {"nazionalita", "VARCHAR(40)", ""}});
        TABELLE.put("editori", new String[][] {{"id", "INT", "P"}, {"nome", "VARCHAR(80)", ""},
                {"citta", "VARCHAR(60)", ""}});
        TABELLE.put("libri", new String[][] {{"id", "INT", "P"}, {"titolo", "VARCHAR(200)", ""}, {"isbn", "CHAR(13)", ""},
                {"anno", "SMALLINT", ""}, {"prezzo", "DECIMAL(8,2)", ""}, {"id_editore", "INT", ""}});
        TABELLE.put("libri_autori", new String[][] {{"id_libro", "INT", "P"}, {"id_autore", "INT", "P"}});
        TABELLE.put("soci", new String[][] {{"id", "INT", "P"}, {"tessera", "CHAR(8)", ""}, {"cognome", "VARCHAR(60)", ""},
                {"nome", "VARCHAR(60)", ""}, {"email", "VARCHAR(120)", ""}, {"nato_il", "DATE", ""}});
        TABELLE.put("prestiti", new String[][] {{"id", "INT", "P"}, {"id_libro", "INT", ""}, {"id_socio", "INT", ""},
                {"data_prestito", "DATE", ""}, {"data_reso", "DATE", ""}});
    }

    /** Una lettura: che cosa, di quale tabella, se è avvenuta sull'EDT. */
    record Lettura(String metodo, String tabella, boolean suEdt, String thread) {
    }

    volatile long delayMs;
    final List<Lettura> letture = new CopyOnWriteArrayList<>();
    final AtomicInteger inCorso = new AtomicInteger();

    private void leggi(String metodo, String tabella) {
        letture.add(new Lettura(metodo, tabella, SwingUtilities.isEventDispatchThread(), Thread.currentThread().getName()));
        inCorso.incrementAndGet();
        try {
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            inCorso.decrementAndGet();
        }
    }

    List<Lettura> sullEdt() {
        return letture.stream().filter(Lettura::suEdt).toList();
    }

    @Override
    public List<String> tables(String catalog) throws SQLException {
        leggi("tables", null);
        return List.copyOf(TABELLE.keySet());
    }

    @Override
    public List<String> views(String catalog) throws SQLException {
        leggi("views", null);
        return List.of();
    }

    @Override
    public String find(String catalog, String table) throws SQLException {
        leggi("find", table);
        for (String t : TABELLE.keySet()) {
            if (t.equalsIgnoreCase(table)) {
                return t;
            }
        }
        return null;
    }

    @Override
    public List<Column> columns(String catalog, String table) throws SQLException {
        leggi("columns", table);
        List<Column> out = new ArrayList<>();
        String[][] cols = table == null ? null : TABELLE.get(table.toLowerCase());
        if (cols != null) {
            for (String[] c : cols) {
                out.add(new Column(c[0], c[1], "P".equals(c[2])));
            }
        }
        return out;
    }

    @Override
    public List<ForeignKey> importedKeys(String catalog, String table) throws SQLException {
        leggi("importedKeys", table);
        List<ForeignKey> out = new ArrayList<>();
        for (String[] fk : FK) {
            if (fk[3].equalsIgnoreCase(table)) {
                out.add(new ForeignKey(fk[0], fk[1], fk[2], fk[3], fk[4]));
            }
        }
        return out;
    }

    @Override
    public List<ForeignKey> exportedKeys(String catalog, String table) throws SQLException {
        leggi("exportedKeys", table);
        List<ForeignKey> out = new ArrayList<>();
        for (String[] fk : FK) {
            if (fk[1].equalsIgnoreCase(table)) {
                out.add(new ForeignKey(fk[0], fk[1], fk[2], fk[3], fk[4]));
            }
        }
        return out;
    }

    /** Facciata di prova: questi metadati, catalogo «biblioteca», join ad archi o a linee spezzate, avvisi raccolti. */
    static final class Host extends BasicQbHost {
        final BibliotecaFinta metadati = new BibliotecaFinta();
        final List<String> avvisi = new CopyOnWriteArrayList<>();
        private final boolean archi;

        Host(boolean archi) {
            this.archi = archi;
        }

        @Override
        public String catalog() {
            return CATALOGO;
        }

        @Override
        public QbMetadata metadata() {
            return metadati;
        }

        @Override
        public boolean option(QbOption o) {
            return o == QbOption.RELATION_ARCS ? archi : super.option(o);
        }

        @Override
        public void alert(String message) {
            avvisi.add(message);
        }
    }
}
