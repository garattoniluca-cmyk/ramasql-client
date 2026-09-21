/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.sqlgen;

import it.ramasql.core.CoreMessages;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.FkAction;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.SqlTypes;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.PrecheckWarning.Code;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Controlli preventivi di una chiave esterna proposta, fatti <b>solo sui metadati</b> (FEASIBILITY.md F-03):
 * spiegano in anticipo gli errori 1005/1215/1822 del server. Più la query che trova le righe orfane.
 */
public final class FkPrecheck {

    private FkPrecheck() {
    }

    /**
     * @param child  tabella che riceve la FK
     * @param fk     la FK proposta
     * @param parent tabella riferita (la stessa {@code child} per una FK autoreferenziale)
     * @return gli avvisi, vuoto se non c'è nulla da segnalare
     */
    public static List<PrecheckWarning> check(TableDef child, ForeignKeyDef fk, TableDef parent) {
        List<PrecheckWarning> out = new ArrayList<>();
        if (!child.isInnoDb()) {
            out.add(warn(Code.FK_CHILD_NOT_INNODB, "precheck.fk.childEngine", child.name(), child.engine()));
        }
        if (!parent.isInnoDb()) {
            out.add(warn(Code.FK_PARENT_NOT_INNODB, "precheck.fk.parentEngine", parent.name(), parent.engine()));
        }
        if (fk.columns().isEmpty() || fk.columns().size() != fk.refColumns().size()) {
            out.add(warn(Code.FK_COLUMN_COUNT_MISMATCH, "precheck.fk.columnCount",
                    fk.columns().size(), fk.refColumns().size()));
            return out;
        }
        boolean allFound = true;
        for (int i = 0; i < fk.columns().size(); i++) {
            Optional<ColumnDef> c = child.column(fk.columns().get(i));
            Optional<ColumnDef> p = parent.column(fk.refColumns().get(i));
            if (c.isEmpty()) {
                out.add(warn(Code.FK_COLUMN_NOT_FOUND, "precheck.fk.columnNotFound", fk.columns().get(i),
                        child.name()));
            }
            if (p.isEmpty()) {
                out.add(warn(Code.FK_COLUMN_NOT_FOUND, "precheck.fk.columnNotFound", fk.refColumns().get(i),
                        parent.name()));
            }
            if (c.isEmpty() || p.isEmpty()) {
                allFound = false;
                continue;
            }
            compareColumns(child, c.get(), parent, p.get(), out);
            boolean setNull = fk.onDelete() == FkAction.SET_NULL || fk.onUpdate() == FkAction.SET_NULL;
            if (setNull && !c.get().nullable()) {
                out.add(warn(Code.FK_SET_NULL_ON_NOT_NULL, "precheck.fk.setNullNotNull", c.get().name()));
            }
        }
        if (allFound && !isLeftPrefixOfAnIndex(parent, fk.refColumns())) {
            out.add(warn(Code.FK_REFERENCED_NOT_INDEXED, "precheck.fk.notIndexed",
                    String.join(", ", fk.refColumns()), parent.name()));
        }
        return out;
    }

    private static void compareColumns(TableDef child, ColumnDef c, TableDef parent, ColumnDef p,
            List<PrecheckWarning> out) {
        String typeC = SqlTypes.canonical(c.dataType());
        String typeP = SqlTypes.canonical(p.dataType());
        boolean text = SqlTypes.isText(typeC) && SqlTypes.isText(typeP);
        boolean charLike = (typeC.equals("CHAR") || typeC.equals("VARCHAR"))
                && (typeP.equals("CHAR") || typeP.equals("VARCHAR"));
        if (!typeC.equals(typeP) && !charLike) {
            out.add(typeMismatch(c, p));
        } else if (SqlTypes.isInteger(typeC)) {
            if (c.unsigned() != p.unsigned()) {
                out.add(warn(Code.FK_SIGN_MISMATCH, "precheck.fk.sign", c.name(), describe(c), p.name(),
                        describe(p)));
            }
        } else if (!text && !sameArgs(c, p)) {
            out.add(typeMismatch(c, p));      // DECIMAL(10,2) contro DECIMAL(8,2), DATETIME(6) contro DATETIME…
        } else if (!text && c.unsigned() != p.unsigned()) {
            out.add(typeMismatch(c, p));
        }
        if (text) {
            // la lunghezza di CHAR/VARCHAR può differire; charset e collation no
            String collationC = effective(c.collation(), child.collation());
            String collationP = effective(p.collation(), parent.collation());
            String charsetC = effective(c.charset(), child.charset());
            String charsetP = effective(p.charset(), parent.charset());
            boolean differs = (collationC != null && collationP != null && !collationC.equalsIgnoreCase(collationP))
                    || (charsetC != null && charsetP != null && !charsetC.equalsIgnoreCase(charsetP));
            if (differs) {
                out.add(warn(Code.FK_COLLATION_MISMATCH, "precheck.fk.collation", c.name(),
                        collationC != null ? collationC : charsetC, p.name(),
                        collationP != null ? collationP : charsetP));
            }
        }
    }

    private static boolean sameArgs(ColumnDef a, ColumnDef b) {
        String argsA = SqlTypes.canonicalArgs(a);
        String argsB = SqlTypes.canonicalArgs(b);
        return argsA == null ? argsB == null : argsA.equals(argsB);
    }

    private static PrecheckWarning typeMismatch(ColumnDef c, ColumnDef p) {
        return warn(Code.FK_TYPE_MISMATCH, "precheck.fk.type", c.name(), describe(c), p.name(), describe(p));
    }

    private static String describe(ColumnDef c) {
        return c.fullType() + (c.unsigned() ? " UNSIGNED" : "");
    }

    private static String effective(String own, String tableDefault) {
        return own != null ? own : tableDefault;
    }

    /** InnoDB chiede che le colonne riferite siano le prime di un indice, nello stesso ordine. */
    static boolean isLeftPrefixOfAnIndex(TableDef table, List<String> columns) {
        for (IndexDef index : table.indexes()) {
            if (index.columns().size() < columns.size()) {
                continue;
            }
            boolean prefix = true;
            for (int i = 0; i < columns.size(); i++) {
                prefix &= index.columns().get(i).equalsIgnoreCase(columns.get(i));
            }
            if (prefix) {
                return true;
            }
        }
        return false;
    }

    /**
     * Query delle <b>righe orfane</b>: le righe della tabella figlia che farebbero fallire la creazione della FK
     * (errore 1452). Le righe con un NULL in una colonna della FK non sono controllate dal server e sono escluse.
     */
    public static String orphanRowsQuery(TableDef child, ForeignKeyDef fk) {
        String refCatalog = fk.refCatalog() != null ? fk.refCatalog() : child.catalog();
        List<String> on = new ArrayList<>();
        List<String> notNull = new ArrayList<>();
        for (int i = 0; i < fk.columns().size(); i++) {
            String c = "figlia." + SqlIdentifiers.quote(fk.columns().get(i));
            on.add("riferita." + SqlIdentifiers.quote(fk.refColumns().get(i)) + " = " + c);
            notNull.add(c + " IS NOT NULL");
        }
        return "SELECT figlia.* FROM " + SqlIdentifiers.qualified(child.catalog(), child.name()) + " AS figlia"
                + " LEFT JOIN " + SqlIdentifiers.qualified(refCatalog, fk.refTable()) + " AS riferita"
                + " ON " + String.join(" AND ", on)
                + " WHERE " + String.join(" AND ", notNull)
                + " AND riferita." + SqlIdentifiers.quote(fk.refColumns().get(0)) + " IS NULL";
    }

    private static PrecheckWarning warn(Code code, String key, Object... args) {
        return new PrecheckWarning(code, CoreMessages.get(key, args));
    }
}
