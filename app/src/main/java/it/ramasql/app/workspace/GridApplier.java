/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import it.ramasql.app.Texts;
import it.ramasql.app.editor.ErrorExplainer;
import it.ramasql.app.grid.DataGrid;
import it.ramasql.app.pipeline.PipelineView;
import it.ramasql.app.pipeline.SqlPipeline;
import it.ramasql.core.data.RowChange;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlOrigin;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.exec.StatementResult;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.DmlGenerator;

/**
 * Collega la <b>Conferma</b> della griglia di data-entry alla pipeline «anteprima SQL»: prende le modifiche in
 * sospeso, le trasforma in istruzioni con {@link DmlGenerator} (DELETE, poi UPDATE, poi INSERT), le mostra
 * nell'anteprima e — solo se l'utente sceglie <em>Esegui</em> — le fa eseguire <b>riga per riga</b> in autocommit,
 * fermandosi al primo errore ({@code ADR-010}: nessuna transazione, mai un {@code START TRANSACTION}).
 *
 * <p>All'esito ogni riga sa com'è andata: quelle riuscite diventano lo stato del server
 * ({@link DataGrid#markSaved}), quella fallita resta in sospeso con l'errore del server spiegato in italiano
 * ({@link DataGrid#markError}), le successive restano in sospeso e intatte. Se tutte sono riuscite la pagina si
 * rilegge dal server, così i valori calcolati dal server — l'{@code id} AUTO_INCREMENT di una riga nuova, un
 * {@code DEFAULT}, un {@code ON UPDATE CURRENT_TIMESTAMP} — si vedono in griglia.
 */
public final class GridApplier {

    private final SqlPipeline pipeline;
    private final PipelineView view;
    private Consumer<ScriptResult> onDone = r -> { };

    public GridApplier(SqlPipeline pipeline, PipelineView view) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.view = Objects.requireNonNull(view, "view");
    }

    /** Collega la griglia: da qui in poi la sua <em>Conferma</em> passa per l'anteprima. */
    public void bind(DataGrid grid, TableDef table) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(table, "table");
        grid.setOnConfirm(changes -> confirm(grid, table, changes));
    }

    /** Chi viene avvisato a esecuzione conclusa (test e schede); l'esito è {@code null} se l'utente ha annullato. */
    public void setOnDone(Consumer<ScriptResult> onDone) {
        this.onDone = onDone == null ? r -> { } : onDone;
    }

    /**
     * Anteprima ed esecuzione delle modifiche di una griglia. Si chiama sull'EDT (lo fa la griglia); l'esecuzione
     * avviene fuori dall'EDT e gli esiti tornano qui sull'EDT.
     */
    public void confirm(DataGrid grid, TableDef table, List<RowChange> changes) {
        List<DmlGenerator.RowStatement> rows = DmlGenerator.generateWithRows(table, changes);
        if (rows.isEmpty()) {
            return;
        }
        String origin = SqlOrigin.GRID.label();
        List<SqlStatement> statements = new ArrayList<>(rows.size());
        for (DmlGenerator.RowStatement r : rows) {
            statements.add(SqlStatement.of(r.sql(), origin));
        }
        SqlScript script = new SqlScript(Texts.get("grid.confirm.title", table.name()), origin, statements);
        pipeline.propose(script).whenComplete((result, error) ->
                SwingUtilities.invokeLater(() -> apply(grid, rows, result)));
    }

    /** Riporta l'esito sulle righe: riuscite salvate, la fallita marcata, le altre in sospeso. */
    private void apply(DataGrid grid, List<DmlGenerator.RowStatement> rows, ScriptResult result) {
        if (result == null) {
            view.message(PipelineView.MessageKind.INFO, Texts.get("grid.confirm.cancelled"));
            onDone.accept(null);
            return;
        }
        int saved = 0;
        for (StatementResult r : result.results()) {
            if (r.index() < 0 || r.index() >= rows.size()) {
                continue;
            }
            RowChange change = rows.get(r.index()).change();
            if (r.isOk()) {
                grid.markSaved(change.rowId(), refreshed(change));
                saved++;
            } else {
                grid.markError(change.rowId(), explain(r));
            }
        }
        if (saved == rows.size()) {
            view.message(PipelineView.MessageKind.SUCCESS, Texts.get("grid.confirm.done", rows.size()));
            grid.reload();   // i valori calcolati dal server (AUTO_INCREMENT, DEFAULT) si vedono in griglia
        } else {
            view.message(PipelineView.MessageKind.ERROR, Texts.get("grid.confirm.partial", saved, rows.size()));
        }
        onDone.accept(result);
    }

    /** I valori da mostrare dopo il salvataggio: quelli scritti (l'eventuale rilettura arriva dopo, col reload). */
    private static Map<String, String> refreshed(RowChange change) {
        return change.kind() == RowChange.Kind.DELETE ? Map.of() : change.newValues();
    }

    /** Messaggio del server più la spiegazione in italiano, quando c'è (1062, 1451, 1452…). */
    private static String explain(StatementResult result) {
        StatementResult.ServerError e = result.error();
        if (e == null) {
            return Texts.get("grid.read.noAnswer");
        }
        String original = e.code() == 0 ? e.message() : "[" + e.code() + "] " + e.message();
        return ErrorExplainer.explain(e.code()).map(s -> original + " — " + s).orElse(original);
    }

}
