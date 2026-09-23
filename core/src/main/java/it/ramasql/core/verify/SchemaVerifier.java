/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.verify;

import it.ramasql.core.CoreMessages;
import it.ramasql.core.metadata.FkAction;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.IndexKind;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.verify.VerificationIssue.Kind;
import it.ramasql.core.verify.VerificationIssue.Severity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * <b>Verifica dopo l'applicazione</b> (ADR-011, DESIGN §3.6): confronta indici e chiavi esterne <i>chiesti</i> (il
 * {@link TableDef} modificato nell'editor) con quelli <i>riletti dal server</i> ({@code MetadataReader}, cioè
 * {@code information_schema.STATISTICS}, {@code KEY_COLUMN_USAGE}, {@code REFERENTIAL_CONSTRAINTS}) e produce l'elenco
 * delle differenze in italiano. Puro: nessun accesso al server.
 *
 * <h2>Regole</h2>
 * <ul>
 *   <li>Nomi di indici, vincoli e colonne confrontati <b>senza distinzione tra maiuscole e minuscole</b>, come fa il
 *       server (su entrambi, per indici e colonne; per le FK con {@code lower_case_table_names} ≠ 0).</li>
 *   <li><b>Bloccanti</b>: oggetto chiesto e assente; nome diverso da quello chiesto; colonne diverse o in un altro
 *       ordine; unicità diversa; tabella o colonne riferite diverse; azioni ON DELETE / ON UPDATE diverse; indice o FK
 *       presenti sul server ma non chiesti (o di cui si era chiesta l'eliminazione).</li>
 *   <li><b>Informazioni</b> (non bloccanti): indice creato dal server da sé per una chiave esterna (le sue prime
 *       colonne sono quelle di una FK presente); indice rimasto dopo l'eliminazione della FK che l'aveva fatto creare;
 *       nome assegnato dal server a una FK chiesta senza nome; RESTRICT ↔ NO ACTION (vedi sotto).</li>
 *   <li><b>RESTRICT e NO ACTION</b>: il generatore scrive sempre l'azione in modo esplicito e i due server la
 *       riportano come scritta, quindi di norma coincidono. Se differiscono, su InnoDB (l'unico engine con FK dei
 *       due server) le due regole sono <i>identiche</i>: InnoDB controlla il vincolo subito in entrambi i casi e non ha
 *       vincoli differiti (manuale MySQL, «FOREIGN KEY Constraints»: «NO ACTION … is equivalent to RESTRICT»;
 *       MariaDB lo stesso). Segnalarlo come errore direbbe allo studente che il vincolo è sbagliato quando si comporta
 *       esattamente come chiesto: è quindi un'<b>informazione</b>, che spiega l'equivalenza. Qualunque altra
 *       differenza di azione è bloccante.</li>
 *   <li>FK autoreferenziale di una tabella rinominata: se si passa l'originale, i riferimenti al suo vecchio nome si
 *       leggono come riferimenti al nome nuovo (il server aggiorna da sé il vincolo, come fa {@code TableDiff}).</li>
 * </ul>
 */
public final class SchemaVerifier {

    private SchemaVerifier() {
    }

    /** Verifica senza conoscere lo stato di partenza (tabella nuova, o originale non disponibile). */
    public static Verification verify(TableDef requested, TableDef actual) {
        return verify(null, requested, actual);
    }

    /**
     * @param original  stato letto prima della modifica ({@code null} = tabella nuova o non noto): serve a riconoscere
     *                  le eliminazioni e le FK autoreferenziali di una tabella rinominata
     * @param requested stato chiesto (il modello modificato)
     * @param actual    stato riletto dal server dopo l'applicazione; {@code null} = tabella non trovata
     */
    public static Verification verify(TableDef original, TableDef requested, TableDef actual) {
        Objects.requireNonNull(requested, "requested");
        List<VerificationIssue> out = new ArrayList<>();
        if (actual == null) {
            out.add(issue(Severity.BLOCKING, Kind.TABLE_MISSING, requested.name(), "verify.table.missing",
                    requested.name()));
            return new Verification(requested.name(), out);
        }
        verifyIndexes(original, requested, actual, out);
        verifyForeignKeys(original, requested, actual, out);
        return new Verification(requested.name(), out);
    }

    // ================================================================ indici

    private static void verifyIndexes(TableDef original, TableDef requested, TableDef actual,
            List<VerificationIssue> out) {
        List<IndexDef> act = actual.indexes();
        boolean[] matched = new boolean[act.size()];

        for (IndexDef r : requested.indexes()) {
            int a = r.isPrimary() ? indexOf(act, IndexDef::isPrimary, matched)
                    : indexOf(act, i -> !i.isPrimary() && i.name().equalsIgnoreCase(r.name()), matched);
            if (a < 0 && !r.isPrimary()) {
                int same = indexOf(act, i -> !i.isPrimary() && i.kind() == r.kind()
                        && sameNames(i.columns(), r.columns()), matched);
                if (same >= 0) {
                    matched[same] = true;
                    out.add(issue(Severity.BLOCKING, Kind.INDEX_NAME_DIFFERENT, r.name(), "verify.index.name",
                            r.name(), act.get(same).name()));
                    continue;
                }
            }
            if (a < 0) {
                out.add(issue(Severity.BLOCKING, Kind.INDEX_MISSING, r.name(),
                        r.isPrimary() ? "verify.pk.missing" : "verify.index.missing", r.name(), list(r.columns())));
                continue;
            }
            matched[a] = true;
            IndexDef x = act.get(a);
            if (!r.isPrimary() && x.kind() != r.kind()) {
                out.add(issue(Severity.BLOCKING, Kind.INDEX_UNIQUENESS, r.name(), "verify.index.uniqueness",
                        r.name(), kindText(r), kindText(x)));
            }
            if (!sameNames(r.columns(), x.columns())) {
                boolean reordered = sameSet(r.columns(), x.columns());
                out.add(issue(Severity.BLOCKING, reordered ? Kind.INDEX_COLUMN_ORDER : Kind.INDEX_COLUMNS, r.name(),
                        reordered ? "verify.index.order" : "verify.index.columns", label(r), list(r.columns()),
                        list(x.columns())));
            }
        }

        for (int a = 0; a < act.size(); a++) {
            if (matched[a]) {
                continue;
            }
            IndexDef x = act.get(a);
            boolean wasThere = original != null && (x.isPrimary() ? original.primaryKey().isPresent()
                    : original.index(x.name()).isPresent());
            ForeignKeyDef served = wasThere ? null : servedFk(x, actual, requested);
            if (served != null) {
                out.add(issue(Severity.INFO, Kind.INDEX_IMPLICIT_FOR_FK, x.name(), "verify.index.implicit",
                        x.name(), list(x.columns()), served.name()));
            } else if (!wasThere && !x.isPrimary() && droppedFkNamed(original, requested, x.name())) {
                out.add(issue(Severity.INFO, Kind.INDEX_LEFT_BY_DROPPED_FK, x.name(), "verify.index.leftByFk",
                        x.name(), list(x.columns())));
            } else {
                out.add(issue(Severity.BLOCKING, Kind.INDEX_UNEXPECTED, x.name(),
                        wasThere ? "verify.index.notDropped" : "verify.index.unexpected", label(x), list(x.columns())));
            }
        }
    }

    /**
     * Una FK (del server o chiesta) le cui colonne sono le prime dell'indice: l'indice può essere quello che il server
     * crea da sé per lei (lo crea anche quando MyISAM ignora la FK).
     */
    private static ForeignKeyDef servedFk(IndexDef index, TableDef actual, TableDef requested) {
        if (index.isPrimary()) {
            return null;
        }
        List<ForeignKeyDef> candidates = new ArrayList<>(actual.foreignKeys());
        requested.foreignKeys().stream().filter(f -> f.name() != null).forEach(candidates::add);
        for (ForeignKeyDef fk : candidates) {
            if (fk.columns().size() <= index.columns().size()
                    && sameNames(fk.columns(), index.columns().subList(0, fk.columns().size()))) {
                return fk;
            }
        }
        return null;
    }

    private static boolean droppedFkNamed(TableDef original, TableDef requested, String name) {
        return original != null && original.foreignKey(name).isPresent() && requested.foreignKey(name).isEmpty();
    }

    // ================================================================ chiavi esterne

    private static void verifyForeignKeys(TableDef original, TableDef requested, TableDef actual,
            List<VerificationIssue> out) {
        List<ForeignKeyDef> act = actual.foreignKeys();
        boolean[] matched = new boolean[act.size()];
        Set<String> originalNames = new HashSet<>();
        if (original != null) {
            original.foreignKeys().forEach(f -> originalNames.add(lower(f.name())));
        }

        for (ForeignKeyDef raw : requested.foreignKeys()) {
            ForeignKeyDef r = selfReferenceRenamed(raw, original, requested);
            int a;
            if (r.name() == null) {
                // senza nome: la FK del server con la stessa struttura, preferendo quelle nate adesso
                a = indexOf(act, f -> !originalNames.contains(lower(f.name())) && sameTarget(r, f, requested,
                        actual), matched);
                if (a < 0) {
                    a = indexOf(act, f -> sameTarget(r, f, requested, actual), matched);
                }
                if (a >= 0) {
                    out.add(issue(Severity.INFO, Kind.FK_NAME_ASSIGNED, act.get(a).name(), "verify.fk.nameAssigned",
                            act.get(a).name(), list(r.columns()), r.refTable()));
                }
            } else {
                a = indexOf(act, f -> f.name().equalsIgnoreCase(r.name()), matched);
                if (a < 0) {
                    int same = indexOf(act, f -> sameTarget(r, f, requested, actual), matched);
                    if (same >= 0) {
                        matched[same] = true;
                        out.add(issue(Severity.BLOCKING, Kind.FK_NAME_DIFFERENT, r.name(), "verify.fk.name", r.name(),
                                act.get(same).name()));
                        compareFk(r, act.get(same), requested, actual, out);
                        continue;
                    }
                }
            }
            if (a < 0) {
                String name = r.name() == null ? "(" + list(r.columns()) + ")" : r.name();
                out.add(issue(Severity.BLOCKING, Kind.FK_MISSING, name,
                        actual.isInnoDb() ? "verify.fk.missing" : "verify.fk.missingEngine", name, list(r.columns()),
                        r.refTable(), actual.engine()));
                continue;
            }
            matched[a] = true;
            compareFk(r, act.get(a), requested, actual, out);
        }

        for (int a = 0; a < act.size(); a++) {
            if (!matched[a]) {
                ForeignKeyDef x = act.get(a);
                boolean wasThere = originalNames.contains(lower(x.name()));
                out.add(issue(Severity.BLOCKING, Kind.FK_UNEXPECTED, x.name(),
                        wasThere ? "verify.fk.notDropped" : "verify.fk.unexpected", x.name(), list(x.columns()),
                        x.refTable()));
            }
        }
    }

    private static void compareFk(ForeignKeyDef r, ForeignKeyDef x, TableDef requested, TableDef actual,
            List<VerificationIssue> out) {
        String name = r.name() == null ? x.name() : r.name();
        if (!sameNames(r.columns(), x.columns())) {
            boolean reordered = sameSet(r.columns(), x.columns());
            out.add(issue(Severity.BLOCKING, reordered ? Kind.FK_COLUMN_ORDER : Kind.FK_COLUMNS, name,
                    reordered ? "verify.fk.order" : "verify.fk.columns", name, list(r.columns()), list(x.columns())));
        }
        if (!sameRefTable(r, x, requested, actual)) {
            out.add(issue(Severity.BLOCKING, Kind.FK_REFERENCED_TABLE, name, "verify.fk.refTable", name,
                    refText(r, requested), refText(x, actual)));
        }
        if (!sameNames(r.refColumns(), x.refColumns())) {
            out.add(issue(Severity.BLOCKING, Kind.FK_REFERENCED_COLUMNS, name, "verify.fk.refColumns", name,
                    list(r.refColumns()), list(x.refColumns())));
        }
        compareAction(name, "ON DELETE", r.onDelete(), x.onDelete(), Kind.FK_ON_DELETE, out);
        compareAction(name, "ON UPDATE", r.onUpdate(), x.onUpdate(), Kind.FK_ON_UPDATE, out);
    }

    private static void compareAction(String fk, String clause, FkAction wanted, FkAction found, Kind kind,
            List<VerificationIssue> out) {
        if (wanted == found) {
            return;
        }
        if (isRestrictLike(wanted) && isRestrictLike(found)) {
            out.add(issue(Severity.INFO, Kind.FK_ACTION_EQUIVALENT, fk, "verify.fk.actionEquivalent", fk, clause,
                    wanted.sql(), found.sql()));
        } else {
            out.add(issue(Severity.BLOCKING, kind, fk, "verify.fk.action", fk, clause, wanted.sql(), found.sql()));
        }
    }

    private static boolean isRestrictLike(FkAction a) {
        return a == FkAction.RESTRICT || a == FkAction.NO_ACTION;
    }

    /** Stesse colonne, stessa tabella e stesse colonne riferite (le azioni si confrontano dopo). */
    private static boolean sameTarget(ForeignKeyDef r, ForeignKeyDef x, TableDef requested, TableDef actual) {
        return sameNames(r.columns(), x.columns()) && sameRefTable(r, x, requested, actual)
                && sameNames(r.refColumns(), x.refColumns());
    }

    private static boolean sameRefTable(ForeignKeyDef r, ForeignKeyDef x, TableDef requested, TableDef actual) {
        String catR = r.refCatalog() != null ? r.refCatalog() : requested.catalog();
        String catX = x.refCatalog() != null ? x.refCatalog() : actual.catalog();
        boolean sameCatalog = catR == null || catX == null || catR.equalsIgnoreCase(catX);
        return sameCatalog && r.refTable().equalsIgnoreCase(x.refTable());
    }

    /** Una FK autoreferenziale che nomina ancora il vecchio nome della tabella rinominata. */
    private static ForeignKeyDef selfReferenceRenamed(ForeignKeyDef fk, TableDef original, TableDef requested) {
        if (original == null || original.name().equalsIgnoreCase(requested.name())
                || !fk.refTable().equalsIgnoreCase(original.name())) {
            return fk;
        }
        boolean sameCatalog = fk.refCatalog() == null || original.catalog() == null
                || fk.refCatalog().equalsIgnoreCase(original.catalog());
        return sameCatalog ? fk.withReference(fk.refCatalog(), requested.name(), fk.refColumns()) : fk;
    }

    // ================================================================ attrezzi

    private static <T> int indexOf(List<T> items, Predicate<T> test, boolean[] taken) {
        for (int i = 0; i < items.size(); i++) {
            if (!taken[i] && test.test(items.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean sameNames(List<String> a, List<String> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equalsIgnoreCase(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameSet(List<String> a, List<String> b) {
        return a.size() == b.size() && a.stream().map(SchemaVerifier::lower).toList()
                .containsAll(b.stream().map(SchemaVerifier::lower).toList())
                && b.stream().map(SchemaVerifier::lower).toList()
                .containsAll(a.stream().map(SchemaVerifier::lower).toList());
    }

    private static String list(List<String> names) {
        return String.join(", ", names);
    }

    private static String label(IndexDef i) {
        return i.isPrimary() ? "PRIMARY KEY" : i.name();
    }

    private static String kindText(IndexDef i) {
        return i.kind() == IndexKind.UNIQUE ? "UNIQUE" : "INDEX";
    }

    private static String refText(ForeignKeyDef fk, TableDef table) {
        String cat = fk.refCatalog();
        return cat == null || cat.equalsIgnoreCase(table.catalog()) ? fk.refTable() : cat + "." + fk.refTable();
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static VerificationIssue issue(Severity severity, Kind kind, String object, String key, Object... args) {
        return new VerificationIssue(severity, kind, object, CoreMessages.get(key, args));
    }
}
