/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * I metadati del query builder <b>mai letti sull'EDT</b> ({@code BUG-024}).
 *
 * <p>Il codice ereditato da SQLeo ({@code DiagramLoader}, {@code ViewObjects}, {@code MaskReferences}) chiede i metadati
 * in modo sincrono, dall'EDT, e ne usa subito il risultato: rifarlo tutto a richiamate sarebbe una riscrittura. Questa
 * classe tiene il loro modo di chiamare ma cambia <em>dove</em> avviene la lettura:
 * <ul>
 *   <li>fuori dall'EDT la chiamata va dritta alla sorgente;</li>
 *   <li>sull'EDT la lettura parte in un thread a parte; se finisce entro {@link #QUICK_MS} ms (il caso normale: le
 *       definizioni sono già nella cache del client, precaricate all'apertura della scheda) il risultato si usa subito,
 *       senza nulla a schermo; altrimenti compare una piccola finestra modale «Lettura dal server: …» con una barra
 *       indeterminata, che si chiude da sola a lettura finita. Mentre la finestra è aperta l'EDT continua a smistare gli
 *       eventi (ridisegni, altre richieste in coda): l'interfaccia non si congela; la modalità impedisce solo di
 *       cambiare il diagramma a metà caricamento. Poi il diagramma si completa sull'EDT, come prima.</li>
 * </ul>
 * Ogni risposta resta in memoria per la vita dell'istanza (una per operazione: un'aggiunta di tabella, un caricamento
 * del modello), così {@link #offEdt} può leggere in un colpo solo, con una sola attesa, tutto quello che l'operazione
 * chiederà dopo, e le chiamate successive dall'EDT trovano la risposta pronta. Non è una cache di lunga durata: la
 * validità dei metadati resta affare della sorgente (nel programma, il lettore dei metadati con la sua invalidazione).
 */
public final class OffEdtQbMetadata implements QbMetadata {

    /** Oltre questa attesa (ms) sull'EDT compare la finestra «Lettura dal server». */
    public static final long QUICK_MS = 120;

    private final QbMetadata source;
    private final Component owner;
    private final Map<String, Optional<Object>> answers = new ConcurrentHashMap<>();

    private OffEdtQbMetadata(QbMetadata source, Component owner) {
        this.source = Objects.requireNonNull(source, "source");
        this.owner = owner;
    }

    /**
     * @param source i metadati della facciata; {@code null} = nessun metadato (risultato {@code null})
     * @param owner  componente sopra cui mostrare l'attesa (di solito il query builder); può essere {@code null}
     */
    public static OffEdtQbMetadata wrap(QbMetadata source, Component owner) {
        if (source == null) {
            return null;
        }
        if (source instanceof OffEdtQbMetadata already) {
            return already;
        }
        return new OffEdtQbMetadata(source, owner);
    }

    /** La sorgente avvolta. */
    public QbMetadata source() {
        return source;
    }

    // ---------------------------------------------------------------- QbMetadata

    @Override
    public List<String> tables(String catalog) throws SQLException {
        return ask("tables", catalog, null, () -> List.copyOf(source.tables(catalog)));
    }

    @Override
    public List<String> views(String catalog) throws SQLException {
        return ask("views", catalog, null, () -> List.copyOf(source.views(catalog)));
    }

    @Override
    public String find(String catalog, String table) throws SQLException {
        return ask("find", catalog, table, () -> source.find(catalog, table));
    }

    @Override
    public List<Column> columns(String catalog, String table) throws SQLException {
        return ask("columns", catalog, table, () -> List.copyOf(source.columns(catalog, table)));
    }

    @Override
    public List<ForeignKey> importedKeys(String catalog, String table) throws SQLException {
        return ask("importedKeys", catalog, table, () -> List.copyOf(source.importedKeys(catalog, table)));
    }

    @Override
    public List<ForeignKey> exportedKeys(String catalog, String table) throws SQLException {
        return ask("exportedKeys", catalog, table, () -> List.copyOf(source.exportedKeys(catalog, table)));
    }

    // ---------------------------------------------------------------- lettura fuori dall'EDT

    /** Una lettura della sorgente (o più letture insieme) che può fallire con {@link SQLException}. */
    @FunctionalInterface
    public interface Reading<T> {
        T read() throws SQLException;
    }

    /**
     * Esegue {@code reading} fuori dall'EDT e ne restituisce il risultato. Chiamata dall'EDT, attende senza congelare
     * l'interfaccia (vedi la descrizione della classe), con {@code what} nel messaggio dell'attesa; da un altro thread
     * esegue e basta. Dentro {@code reading} le chiamate a questa istanza vanno alla sorgente e ne ricordano la
     * risposta: è il modo di preparare in una volta sola ciò che servirà poi sull'EDT.
     */
    public <T> T offEdt(String what, Reading<T> reading) throws SQLException {
        if (!SwingUtilities.isEventDispatchThread()) {
            return reading.read();
        }
        Callable<T> task = reading::read;
        return waitFor(owner, what, task);
    }

    private <T> T ask(String method, String catalog, String table, Reading<T> reading) throws SQLException {
        String key = method + '\u0000' + lower(catalog) + '\u0000' + lower(table);
        Optional<Object> known = answers.get(key);
        if (known != null) {
            @SuppressWarnings("unchecked")
            T value = (T) known.orElse(null);
            return value;
        }
        T value = offEdt(table, reading); // senza tabella: elenco di tabelle o viste
        answers.put(key, Optional.ofNullable(value));
        return value;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /** Esegue {@code task} in un thread a parte e lo attende dall'EDT (vedi la descrizione della classe). */
    private static <T> T waitFor(Component owner, String what, Callable<T> task) throws SQLException {
        JDialog[] shown = new JDialog[1];
        FutureTask<T> future = new FutureTask<>(task) {
            @Override
            protected void done() {
                // chiude la finestra d'attesa, se c'è: gira sull'EDT, dentro il ciclo della finestra modale (se la
                // lettura finisce prima che la finestra si apra, la chiusura trova shown[0] nullo e non fa nulla)
                SwingUtilities.invokeLater(() -> {
                    if (shown[0] != null) {
                        shown[0].dispose();
                    }
                });
            }
        };
        Thread worker = new Thread(future, "RamaSQL - metadati del query builder");
        worker.setDaemon(true);
        worker.start();
        try {
            try {
                return future.get(QUICK_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException slow) {
                if (!GraphicsEnvironment.isHeadless()) {
                    while (!future.isDone()) {
                        shown[0] = waitDialog(owner, what);
                        shown[0].setVisible(true); // modale: l'EDT continua a smistare gli eventi finché done() la chiude
                        shown[0].dispose();
                    }
                    shown[0] = null;
                }
                return future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Lettura dei metadati interrotta", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SQLException sql) {
                throw sql;
            }
            if (cause instanceof RuntimeException rt) {
                throw rt;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new SQLException(cause);
        }
    }

    /** La finestra d'attesa: messaggio e barra indeterminata, senza pulsante di chiusura (si chiude da sola). */
    private static JDialog waitDialog(Component owner, String what) {
        Window window = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        JDialog d = new JDialog(window, Dialog.ModalityType.DOCUMENT_MODAL);
        d.setName("qb.metadata.wait");
        d.setUndecorated(true);
        d.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        String message = what == null || what.isBlank()
                ? QbRuntime.host().text("querybuilder.message.waitMetadata.list", "Lettura dell'elenco delle tabelle dal server…")
                : new MessageFormat(QbRuntime.host().text("querybuilder.message.waitMetadata", "Lettura dal server: {0}…"))
                        .format(new Object[] {what});
        JLabel label = new JLabel(message);
        label.setName("qb.metadata.wait.message");
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        JPanel content = new JPanel(new BorderLayout(0, QbRuntime.scale(8)));
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(QbRuntime.host().color(QbColor.BORDER)),
                BorderFactory.createEmptyBorder(QbRuntime.scale(12), QbRuntime.scale(16), QbRuntime.scale(12),
                        QbRuntime.scale(16))));
        content.add(label, BorderLayout.CENTER);
        content.add(bar, BorderLayout.SOUTH);
        d.setContentPane(content);
        d.pack();
        d.setLocationRelativeTo(owner != null && owner.isShowing() ? owner : window);
        return d;
    }
}
