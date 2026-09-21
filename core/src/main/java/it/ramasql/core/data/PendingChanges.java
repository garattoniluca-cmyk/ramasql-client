/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.data;

import it.ramasql.core.CoreMessages;
import it.ramasql.core.metadata.ColumnDef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Modello a <b>modifiche pendenti</b> del data-entry, senza Swing: le righe lette dal server più ciò che
 * l'utente ha inserito, modificato ed eliminato e non ha ancora confermato. Non scrive mai sul database:
 * {@link #toRowChanges()} consegna le modifiche al generatore DML, e dopo l'esecuzione di ogni istruzione
 * si chiama {@link #markSaved} o {@link #markError}.
 *
 * <ul>
 *   <li>I valori sono testo; {@code null} è il NULL di SQL. Nelle righe nuove una cella può essere
 *       <i>non impostata</i> ({@link #isSet} falso): sarà omessa dall'INSERT (DEFAULT, AUTO_INCREMENT).</li>
 *   <li>Una riga nuova senza alcuna cella impostata (la riga d'inserimento vuota) non è una modifica pendente.</li>
 *   <li>Riportare una cella al valore originale ritira la modifica; eliminare una riga nuova la toglie e basta.</li>
 *   <li><b>Incolla</b> ({@link #paste}) e <b>riempi</b> ({@link #fill}) sono operazioni uniche per
 *       {@link #undoLastPaste()}: l'annullamento ripristina le sole celle toccate e toglie le righe create.</li>
 *   <li>Cella vuota ({@code null}) incollata: su una riga esistente → NULL; su una riga nuova → non impostata.</li>
 * </ul>
 * Non è sicuro tra thread: si usa dall'EDT.
 */
public final class PendingChanges {

    public enum RowKind { UNCHANGED, INSERTED, MODIFIED, DELETED }

    /** Stato di una riga rispetto al server. */
    public enum State { PENDENTE, SALVATA, IN_ERRORE }

    /**
     * Esito di un incolla.
     *
     * @param cellsWritten     celle scritte
     * @param rowsAdded        righe nuove create oltre l'ultima
     * @param discardedColumns colonne del blocco scartate perché oltre l'ultima colonna della tabella
     * @param skippedCells     celle saltate (colonne AUTO_INCREMENT o generate, righe eliminate)
     */
    public record PasteResult(int cellsWritten, int rowsAdded, int discardedColumns, int skippedCells) {
    }

    private static final class Row {
        final long id;
        String[] original;       // null = riga nuova
        String[] current;
        boolean[] set;
        boolean deleted;
        String error;

        Row(long id, String[] original, int width) {
            this.id = id;
            this.original = original;
            this.current = original == null ? new String[width] : original.clone();
            this.set = new boolean[width];
            if (original != null) {
                java.util.Arrays.fill(set, true);
            }
        }
    }

    private record CellBefore(long rowId, int column, String value, boolean set) {
    }

    private record UndoStep(List<CellBefore> cells, List<Long> addedRows) {
    }

    private final List<ColumnDef> columns;
    private final List<String[]> loaded = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private long nextId = 1;
    private UndoStep lastPaste;
    private UndoStep recording;

    /**
     * @param columns    colonne della griglia, nell'ordine di visualizzazione
     * @param loadedRows righe lette dal server (una lista di celle per riga, {@code null} = NULL)
     */
    public PendingChanges(List<ColumnDef> columns, List<? extends List<String>> loadedRows) {
        this.columns = List.copyOf(columns);
        for (List<String> r : loadedRows) {
            if (r.size() != columns.size()) {
                throw new IllegalArgumentException("Riga con " + r.size() + " celle, attese " + columns.size());
            }
            loaded.add(r.toArray(new String[0]));
        }
        discard();
    }

    // ---------------------------------------------------------------- lettura

    public int columnCount() {
        return columns.size();
    }

    public ColumnDef column(int column) {
        return columns.get(column);
    }

    public int rowCount() {
        return rows.size();
    }

    /** Identificativo stabile della riga (non cambia quando altre righe spariscono). */
    public long rowId(int row) {
        return rows.get(row).id;
    }

    /** Indice attuale della riga, -1 se non c'è più. */
    public int indexOf(long rowId) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id == rowId) {
                return i;
            }
        }
        return -1;
    }

    public String value(int row, int column) {
        return rows.get(row).current[column];
    }

    /** Falso solo per le celle non impostate di una riga nuova. */
    public boolean isSet(int row, int column) {
        return rows.get(row).set[column];
    }

    public RowKind kind(int row) {
        return kind(rows.get(row));
    }

    private RowKind kind(Row r) {
        if (r.deleted) {
            return RowKind.DELETED;
        }
        if (r.original == null) {
            return RowKind.INSERTED;
        }
        for (int c = 0; c < r.current.length; c++) {
            if (!Objects.equals(r.original[c], r.current[c])) {
                return RowKind.MODIFIED;
            }
        }
        return RowKind.UNCHANGED;
    }

    private boolean isPending(Row r) {
        return switch (kind(r)) {
            case UNCHANGED -> false;
            case INSERTED -> anySet(r);
            case MODIFIED, DELETED -> true;
        };
    }

    private static boolean anySet(Row r) {
        for (boolean s : r.set) {
            if (s) {
                return true;
            }
        }
        return false;
    }

    public State state(int row) {
        Row r = rows.get(row);
        if (r.error != null) {
            return State.IN_ERRORE;
        }
        return isPending(r) ? State.PENDENTE : State.SALVATA;
    }

    /** Messaggio dell'errore del server sulla riga, se è {@link State#IN_ERRORE}. */
    public Optional<String> errorMessage(int row) {
        return Optional.ofNullable(rows.get(row).error);
    }

    public boolean isCellModified(int row, int column) {
        Row r = rows.get(row);
        if (r.deleted) {
            return false;
        }
        return r.original == null ? r.set[column] : !Objects.equals(r.original[column], r.current[column]);
    }

    /** Il blocco da copiare negli appunti (estremi inclusi); le celle non impostate valgono NULL. */
    public ClipboardBlock copy(int firstRow, int lastRow, int firstColumn, int lastColumn) {
        List<List<String>> block = new ArrayList<>();
        for (int r = firstRow; r <= lastRow; r++) {
            List<String> cells = new ArrayList<>();
            for (int c = firstColumn; c <= lastColumn; c++) {
                cells.add(value(r, c));
            }
            block.add(cells);
        }
        return new ClipboardBlock(block);
    }

    // ---------------------------------------------------------------- modifica

    /** Imposta una cella ({@code null} = NULL esplicito). Su una riga eliminata non fa nulla. */
    public void setValue(int row, int column, String value) {
        write(rows.get(row), column, value, true);
    }

    /** Svuota una cella: non impostata su una riga nuova, NULL su una riga esistente. */
    public void clearValue(int row, int column) {
        Row r = rows.get(row);
        write(r, column, null, r.original != null);
    }

    private boolean write(Row r, int column, String value, boolean set) {
        if (r.deleted) {
            return false;
        }
        if (recording != null) {
            recording.cells().add(new CellBefore(r.id, column, r.current[column], r.set[column]));
        }
        r.current[column] = value;
        r.set[column] = set;
        r.error = null;
        return true;
    }

    /** Aggiunge in fondo una riga nuova, tutta da impostare; restituisce il suo indice. */
    public int addRow() {
        Row r = new Row(nextId++, null, columns.size());
        rows.add(r);
        if (recording != null) {
            recording.addedRows().add(r.id);
        }
        return rows.size() - 1;
    }

    /** Marca la riga come eliminata; una riga nuova sparisce subito. */
    public void deleteRow(int row) {
        Row r = rows.get(row);
        if (r.original == null) {
            rows.remove(row);
        } else {
            r.deleted = true;
            r.error = null;
        }
    }

    /** Ritira l'eliminazione pendente di una riga. */
    public void restoreRow(int row) {
        rows.get(row).deleted = false;
        rows.get(row).error = null;
    }

    /** <b>Scarta</b>: butta tutte le modifiche pendenti e torna alle righe lette dal server (operazione locale). */
    public void discard() {
        rows.clear();
        for (String[] r : loaded) {
            rows.add(new Row(nextId++, r, columns.size()));   // r non è mai modificato: Row lavora su una copia
        }
        lastPaste = null;
        recording = null;
    }

    // ---------------------------------------------------------------- incolla

    /**
     * Stende il blocco a partire dalla cella attiva: oltre l'ultima riga crea righe nuove, oltre l'ultima
     * colonna scarta; le colonne AUTO_INCREMENT e generate sono saltate. Non scrive nulla sul database.
     */
    public PasteResult paste(int startRow, int startColumn, ClipboardBlock block) {
        recording = new UndoStep(new ArrayList<>(), new ArrayList<>());
        int written = 0;
        int added = 0;
        int skipped = 0;
        int usableColumns = Math.max(0, Math.min(block.columnCount(), columns.size() - startColumn));
        for (int br = 0; br < block.rowCount(); br++) {
            int target = startRow + br;
            while (target >= rows.size()) {
                addRow();
                added++;
            }
            Row r = rows.get(target);
            for (int bc = 0; bc < usableColumns; bc++) {
                int column = startColumn + bc;
                if (r.deleted || isReadOnly(columns.get(column))) {
                    skipped++;
                    continue;
                }
                String value = block.cell(br, bc);
                write(r, column, value, value != null || r.original != null);
                written++;
            }
        }
        lastPaste = recording;
        recording = null;
        return new PasteResult(written, added, block.columnCount() - usableColumns, skipped);
    }

    /** Un solo valore su una selezione rettangolare (estremi inclusi): le riempie tutte; annullabile come un incolla. */
    public PasteResult fill(int firstRow, int lastRow, int firstColumn, int lastColumn, String value) {
        recording = new UndoStep(new ArrayList<>(), new ArrayList<>());
        int written = 0;
        int skipped = 0;
        for (int row = firstRow; row <= lastRow; row++) {
            Row r = rows.get(row);
            for (int column = firstColumn; column <= lastColumn; column++) {
                if (r.deleted || isReadOnly(columns.get(column))) {
                    skipped++;
                } else {
                    write(r, column, value, value != null || r.original != null);
                    written++;
                }
            }
        }
        lastPaste = recording;
        recording = null;
        return new PasteResult(written, 0, 0, skipped);
    }

    private static boolean isReadOnly(ColumnDef column) {
        return column.autoIncrement() || column.generated();
    }

    public boolean canUndoPaste() {
        return lastPaste != null;
    }

    /** Ritira l'ultimo incolla (o riempi) come operazione unica; falso se non c'è nulla da annullare. */
    public boolean undoLastPaste() {
        if (lastPaste == null) {
            return false;
        }
        List<CellBefore> cells = lastPaste.cells();
        for (int i = cells.size() - 1; i >= 0; i--) {
            CellBefore before = cells.get(i);
            int index = indexOf(before.rowId());
            if (index >= 0) {
                Row r = rows.get(index);
                r.current[before.column()] = before.value();
                r.set[before.column()] = before.set();
                r.error = null;
            }
        }
        for (long id : lastPaste.addedRows()) {
            int index = indexOf(id);
            if (index >= 0) {
                rows.remove(index);
            }
        }
        lastPaste = null;
        return true;
    }

    // ---------------------------------------------------------------- contatori

    public int insertCount() {
        return count(RowKind.INSERTED);
    }

    public int updateCount() {
        return count(RowKind.MODIFIED);
    }

    public int deleteCount() {
        return count(RowKind.DELETED);
    }

    private int count(RowKind wanted) {
        return (int) rows.stream().filter(r -> kind(r) == wanted && isPending(r)).count();
    }

    public boolean hasPending() {
        return rows.stream().anyMatch(this::isPending);
    }

    /** «3 inserimenti · 1 modifica · 0 eliminazioni». */
    public String summary() {
        return plural("pending.insert", insertCount()) + " · " + plural("pending.update", updateCount()) + " · "
                + plural("pending.delete", deleteCount());
    }

    private static String plural(String key, int count) {
        return count + " " + CoreMessages.get(key + (count == 1 ? ".one" : ".many"));
    }

    // ---------------------------------------------------------------- validazione

    /** Errore di validazione della cella, se è una cella che finirà nell'SQL e non è valida. */
    public Optional<String> validationError(int row, int column) {
        Row r = rows.get(row);
        RowKind kind = kind(r);
        if (kind == RowKind.DELETED || kind == RowKind.UNCHANGED || !isPending(r)) {
            return Optional.empty();
        }
        if (kind == RowKind.MODIFIED && !isCellModified(row, column)) {
            return Optional.empty();
        }
        ColumnDef def = columns.get(column);
        if (def.generated()) {
            return Optional.empty();
        }
        String value = r.current[column];
        if (r.set[column] && value == null && !def.nullable() && !def.autoIncrement()) {
            return Optional.of(CoreMessages.get("validate.notNull", def.name()));
        }
        ValueValidator.Result result = ValueValidator.validate(value, def);
        return result.valid() ? Optional.empty() : Optional.of(result.message());
    }

    /** Coppie {riga, colonna} delle celle non valide. */
    public List<int[]> invalidCells() {
        List<int[]> out = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < columns.size(); c++) {
                if (validationError(r, c).isPresent()) {
                    out.add(new int[] {r, c});
                }
            }
        }
        return out;
    }

    /** La <i>Conferma</i> è possibile: c'è qualcosa in sospeso e nessuna cella non valida. */
    public boolean canConfirm() {
        return hasPending() && invalidCells().isEmpty();
    }

    // ---------------------------------------------------------------- verso il DML e ritorno

    /**
     * Le modifiche da eseguire (pendenti e in errore), nell'ordine della griglia; l'ordine d'esecuzione
     * (DELETE, UPDATE, INSERT) lo stabilisce il generatore DML.
     */
    public List<RowChange> toRowChanges() {
        List<RowChange> out = new ArrayList<>();
        for (Row r : rows) {
            if (!isPending(r)) {
                continue;
            }
            switch (kind(r)) {
                case INSERTED -> out.add(RowChange.insert(r.id, values(r, true, false)));
                case MODIFIED -> out.add(RowChange.update(r.id, originalValues(r), values(r, false, true)));
                case DELETED -> out.add(RowChange.delete(r.id, originalValues(r)));
                default -> { }
            }
        }
        return out;
    }

    private Map<String, String> values(Row r, boolean onlySet, boolean onlyChanged) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int c = 0; c < columns.size(); c++) {
            boolean wanted = onlySet ? r.set[c] : !onlyChanged || !Objects.equals(r.original[c], r.current[c]);
            if (wanted) {
                map.put(columns.get(c).name(), r.current[c]);
            }
        }
        return map;
    }

    private Map<String, String> originalValues(Row r) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int c = 0; c < columns.size(); c++) {
            map.put(columns.get(c).name(), r.original[c]);
        }
        return map;
    }

    /** L'istruzione della riga è riuscita. */
    public void markSaved(long rowId) {
        markSaved(rowId, Map.of());
    }

    /**
     * L'istruzione della riga è riuscita: la riga diventa lo stato del server (una riga eliminata sparisce).
     *
     * @param refreshedValues valori riletti dal server da sovrapporre (es. l'{@code id} AUTO_INCREMENT di una riga nuova)
     */
    public void markSaved(long rowId, Map<String, String> refreshedValues) {
        int index = indexOf(rowId);
        if (index < 0) {
            return;
        }
        Row r = rows.get(index);
        lastPaste = null;
        if (r.deleted) {
            rows.remove(index);
            loaded.removeIf(l -> l == r.original);
            return;
        }
        for (int c = 0; c < columns.size(); c++) {
            for (Map.Entry<String, String> e : refreshedValues.entrySet()) {
                if (e.getKey().equalsIgnoreCase(columns.get(c).name())) {
                    r.current[c] = e.getValue();
                }
            }
        }
        boolean wasNew = r.original == null;
        String[] saved = r.current.clone();
        if (wasNew) {
            loaded.add(saved);
        } else {
            loaded.set(indexOfLoaded(r.original), saved);
        }
        r.original = saved;
        java.util.Arrays.fill(r.set, true);
        r.error = null;
    }

    private int indexOfLoaded(String[] original) {
        for (int i = 0; i < loaded.size(); i++) {
            if (loaded.get(i) == original) {
                return i;
            }
        }
        throw new IllegalStateException("Riga originale non trovata");
    }

    /** L'istruzione della riga è fallita: la riga resta da correggere, con il messaggio spiegato. */
    public void markError(long rowId, String message) {
        int index = indexOf(rowId);
        if (index >= 0) {
            rows.get(index).error = message == null ? "" : message;
        }
    }
}
