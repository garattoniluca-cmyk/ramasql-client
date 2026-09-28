/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.grid;

import java.util.List;
import java.util.Objects;

import javax.swing.table.AbstractTableModel;

import it.ramasql.core.data.PendingChanges;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.SqlTypes;

/**
 * {@code TableModel} sopra {@link PendingChanges}: le righe della pagina più, se la griglia è modificabile, la
 * <b>riga d'inserimento</b> vuota sempre in fondo. Scrivere nella riga d'inserimento crea una riga pendente (e ne
 * compare un'altra vuota sotto). I valori sono testo; {@code null} = NULL (o cella non impostata di una riga nuova).
 */
public final class DataGridModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private transient PendingChanges pending;
    private final boolean editable;
    /** Vero mentre la griglia legge una pagina nuova: niente modifiche, andrebbero perse con la pagina vecchia. */
    private boolean locked;

    DataGridModel(PendingChanges pending, boolean editable) {
        this.pending = pending;
        this.editable = editable;
    }

    /** Nuova pagina di dati: sostituisce le modifiche (chi chiama ha già controllato che non ce ne siano). */
    void replace(PendingChanges newPending) {
        this.pending = newPending;
        fireTableDataChanged();
    }

    public PendingChanges pending() {
        return pending;
    }

    public boolean isEditable() {
        return editable;
    }

    /** Blocca (o sblocca) le modifiche mentre si legge una pagina ({@code BUG-017}). */
    void setLocked(boolean locked) {
        this.locked = locked;
    }

    /** Vero mentre si legge una pagina: niente modifiche. */
    boolean isLocked() {
        return locked;
    }

    /** Righe di dati (esclusa la riga d'inserimento). */
    public int dataRowCount() {
        return pending.rowCount();
    }

    /** Vero per la riga vuota d'inserimento in fondo. */
    public boolean isInsertRow(int row) {
        return editable && row == pending.rowCount();
    }

    public ColumnDef column(int column) {
        return pending.column(column);
    }

    public List<ColumnDef> columns() {
        return java.util.stream.IntStream.range(0, pending.columnCount()).mapToObj(pending::column).toList();
    }

    @Override
    public int getRowCount() {
        return pending.rowCount() + (editable ? 1 : 0);
    }

    @Override
    public int getColumnCount() {
        return pending.columnCount();
    }

    @Override
    public String getColumnName(int column) {
        return pending.column(column).name();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        return isInsertRow(row) ? null : pending.value(row, column);
    }

    /** Falso per la riga d'inserimento e per le celle non impostate (DEFAULT / AUTO_INCREMENT) delle righe nuove. */
    public boolean isSet(int row, int column) {
        return !isInsertRow(row) && pending.isSet(row, column);
    }

    /**
     * Colonna che il client non scrive mai: AUTO_INCREMENT, generata, oppure <b>binaria</b>. Il contenuto binario
     * (BINARY, VARBINARY, i BLOB, BIT) si vede come {@code 0x…} e si copia, ma in v1 non si modifica: servirebbe un
     * editor dedicato (`IDEA-011`), e lasciarlo scrivere a mano significherebbe salvare testo al posto dei byte.
     */
    public boolean isReadOnlyColumn(int column) {
        ColumnDef c = pending.column(column);
        return c.autoIncrement() || c.generated() || SqlTypes.isBinary(c.dataType());
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        if (!editable || locked || isReadOnlyColumn(column)) {
            return false;
        }
        return isInsertRow(row) || pending.kind(row) != PendingChanges.RowKind.DELETED;
    }

    /**
     * Valore digitato nella cella. Un testo vuoto su una cella che era NULL (o non impostata) non cambia nulla: chi apre
     * l'editor su un NULL e lo richiude senza scrivere non deve trasformarlo in stringa vuota.
     */
    @Override
    public void setValueAt(Object value, int row, int column) {
        if (!isCellEditable(row, column)) {
            return;
        }
        String text = value == null ? null : value.toString();
        if (isInsertRow(row)) {
            if (text == null || text.isEmpty()) {
                return;
            }
            int created = pending.addRow();
            pending.setValue(created, column, text);
            fireTableRowsInserted(created + 1, created + 1);
            fireTableRowsUpdated(created, created);
            return;
        }
        String current = pending.value(row, column);
        if ((text == null || text.isEmpty()) && current == null) {
            return;
        }
        if (Objects.equals(text, current) && pending.isSet(row, column)) {
            return;
        }
        pending.setValue(row, column, text);
        fireTableRowsUpdated(row, row);
    }
}
