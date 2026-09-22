/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb.spike;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatLightLaf;
import com.sqleo.querybuilder.DiagramEntity;
import com.sqleo.querybuilder.DiagramRelation;
import com.sqleo.querybuilder.QueryActions;
import com.sqleo.querybuilder.QueryBuilder;

import it.ramasql.qb.BasicQbHost;
import it.ramasql.qb.QbRuntime;
import it.ramasql.qb.QbSql;

/**
 * Disegna il query builder fuori schermo sotto FlatLaf chiaro, con i metadati finti della biblioteca e una
 * query a 5 tabelle, e salva l'immagine ({@code component.paint} su {@link BufferedImage}: nessun controllo del
 * desktop). Usato in-process dal test S1 e, come {@code main}, in JVM figlie con {@code -Dflatlaf.uiScale} dal test S5.
 */
final class QbRender {

    static final String QUERY_5_TABELLE =
            "SELECT a.cognome, l.titolo, e.nome, p.data_prestito"
                    + " FROM autori a INNER JOIN libri_autori la ON a.id = la.id_autore"
                    + " INNER JOIN libri l ON la.id_libro = l.id"
                    + " INNER JOIN editori e ON l.id_editore = e.id"
                    + " LEFT JOIN prestiti p ON l.id = p.id_libro"
                    + " WHERE l.anno > 2000 ORDER BY l.titolo";

    /** Esito di un disegno. */
    record Esito(int entita, int relazioni, String sqlDelModello, int larghezza, int altezza, float scalaFlatLaf,
                 List<String> difetti, List<String> avvisi) {
    }

    private QbRender() {
    }

    /** Host di prova: metadati finti, nessun database; raccoglie gli avvisi invece di mostrarli. */
    static final class HostDiProva extends BasicQbHost {
        final List<String> avvisi = new ArrayList<>();
        private final Connection connection = FakeBiblioteca.connection();

        @Override
        public Connection connection() {
            return connection;
        }

        @Override
        public String catalog() {
            return FakeBiblioteca.CATALOGO;
        }

        @Override
        public void alert(String message) {
            avvisi.add(message);
        }
    }

    /**
     * @param file          PNG da scrivere
     * @param scalaGrafica  fattore applicato con {@code Graphics2D.scale} (come fa il JRE in HiDPI); 1 = nessuno
     */
    static Esito disegna(Path file, double scalaGrafica) throws Exception {
        AtomicReference<Esito> esito = new AtomicReference<>();
        AtomicReference<Throwable> errore = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                esito.set(disegnaSullEdt(file, scalaGrafica));
            } catch (Throwable t) {
                errore.set(t);
            }
        });
        if (errore.get() != null) {
            throw new IllegalStateException("errore durante il disegno del query builder", errore.get());
        }
        return esito.get();
    }

    private static Esito disegnaSullEdt(Path file, double scalaGrafica) throws Exception {
        if (!FlatLightLaf.setup()) {
            throw new IllegalStateException("FlatLaf chiaro non installabile");
        }
        HostDiProva host = new HostDiProva();
        QbRuntime.setHost(host);

        QueryBuilder qb = new QueryBuilder(host);
        JFrame frame = new JFrame("RamaSQL - spike S1/S5");
        try {
            frame.getContentPane().add(qb);
            Dimension size = new Dimension(host.scale(1280), host.scale(760));
            frame.setSize(size);
            frame.addNotify(); // visualizzabile (peer e layout) senza mostrarlo sul desktop
            frame.validate();
            // Windows limita la finestra (anche non mostrata) alla dimensione dello schermo: il contenuto
            // si dimensiona a parte, così la resa non dipende dal monitor collegato.
            dimensiona(frame, size);

            qb.setQueryModel(QbSql.parse(QUERY_5_TABELLE));
            Action disponi = qb.getActionMap().get(QueryActions.ENTITIES_ARRANGE_GRID);
            disponi.actionPerformed(new java.awt.event.ActionEvent(qb, 0, "disponi"));
            dimensiona(frame, size);

            Container content = frame.getContentPane();
            int w = (int) Math.ceil(content.getWidth() * scalaGrafica);
            int h = (int) Math.ceil(content.getHeight() * scalaGrafica);
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.scale(scalaGrafica, scalaGrafica);
            content.paint(g);
            g.dispose();
            Files.createDirectories(file.getParent());
            ImageIO.write(image, "png", file.toFile());

            List<DiagramEntity> entita = new ArrayList<>();
            List<DiagramRelation> relazioni = new ArrayList<>();
            List<String> difetti = new ArrayList<>();
            marginiMisurati = 0;
            visita(content, entita, relazioni, difetti);
            float scalaFlatLaf = com.formdev.flatlaf.util.UIScale.getUserScaleFactor();
            return new Esito(entita.size(), relazioni.size(), qb.getQueryModel().toString(false), w, h, scalaFlatLaf,
                    difetti, host.avvisi);
        } finally {
            frame.dispose();
        }
    }

    /** Conta entità e relazioni e cerca i difetti di resa verificabili da programma. */
    private static void visita(Component c, List<DiagramEntity> entita, List<DiagramRelation> relazioni, List<String> difetti) {
        if (c instanceof DiagramEntity e) {
            entita.add(e);
        }
        if (c instanceof DiagramRelation r) {
            relazioni.add(r);
            if (r.getWidth() <= 1 && r.getHeight() <= 1) { // un join orizzontale è alto 1 px: va bene
                difetti.add("join non disegnato: relazione con area " + r.getWidth() + "x" + r.getHeight());
            }
        }
        if (c.isShowing() || c.isVisible()) {
            String testo = c instanceof JLabel l ? l.getText() : c instanceof AbstractButton b ? b.getText() : null;
            if (c instanceof JLabel && c.getParent() instanceof com.sqleo.querybuilder.DiagramField && Color.red.equals(c.getForeground())) {
                difetti.add("campo segnato in rosso come mancante: «" + testo + "»");
            }
            if (testo != null && !testo.isBlank() && c.getWidth() > 0) {
                FontMetrics fm = c.getFontMetrics(c.getFont());
                int larghezzaTesto = fm.stringWidth(testo);
                if (c.getWidth() < larghezzaTesto) {
                    difetti.add("testo tagliato: «" + testo + "» largo " + larghezzaTesto + " px in un componente di "
                            + c.getWidth() + " px (" + c.getClass().getSimpleName() + ")");
                }
                if (c.getHeight() < fm.getAscent()) {
                    difetti.add("testo schiacciato: «" + testo + "» alto " + fm.getHeight() + " px in un componente di "
                            + c.getHeight() + " px (" + c.getClass().getSimpleName() + ")");
                }
                margine(c, testo, difetti);
                Color sfondo = sfondoEffettivo(c);
                double contrasto = contrasto(c.getForeground(), sfondo);
                if (contrasto < 3.0) {
                    difetti.add(String.format("colore poco leggibile: «%s» contrasto %.1f (testo %s su sfondo %s, %s)",
                            testo, contrasto, hex(c.getForeground()), hex(sfondo), c.getClass().getSimpleName()));
                }
            }
            if (c instanceof JTree t) {
                int altezzaFont = t.getFontMetrics(t.getFont()).getHeight();
                if (t.getRowHeight() > 0 && t.getRowHeight() < altezzaFont) {
                    difetti.add("albero della query: righe alte " + t.getRowHeight() + " px con font alto " + altezzaFont + " px");
                }
                if (t.getRowHeight() > altezzaFont * 2) {
                    difetti.add("albero della query: righe troppo alte (" + t.getRowHeight() + " px con font alto "
                            + altezzaFont + " px): scala applicata due volte");
                }
            }
        }
        if (c instanceof Container k) {
            for (Component figlio : k.getComponents()) {
                visita(figlio, entita, relazioni, difetti);
            }
        }
    }

    /** Margine minimo, in pixel, tra il testo (con la sua icona) e il bordo visibile che lo racchiude. */
    static final int MARGINE_MINIMO = 2;
    /** Testi a cui il controllo del margine si è applicato nell'ultimo disegno (evidenza nel rapporto). */
    static int marginiMisurati;

    /**
     * Controllo «almeno {@value #MARGINE_MINIMO} px tra testo e bordo», dove il bordo è misurabile:
     * <ul>
     * <li>testi dentro un'entità del diagramma (campi {@code DiagramField} e intestazione): il bordo è il riquadro
     *     dell'entità, cioè l'area interna di {@code DiagramAbstractEntity} (dimensione meno i suoi insets);</li>
     * <li>pulsanti con il bordo disegnato: il bordo è il pulsante stesso.</li>
     * </ul>
     * La posizione del testo si calcola con {@link SwingUtilities#layoutCompoundLabel}, come fanno i renderer Swing.
     */
    private static void margine(Component c, String testo, List<String> difetti) {
        javax.swing.JComponent jc;
        Icon icona;
        int hAlign, vAlign, hPos, vPos, gap;
        if (c instanceof JLabel l) {
            jc = l; icona = l.getIcon(); hAlign = l.getHorizontalAlignment(); vAlign = l.getVerticalAlignment();
            hPos = l.getHorizontalTextPosition(); vPos = l.getVerticalTextPosition(); gap = l.getIconTextGap();
        } else if (c instanceof AbstractButton b) {
            jc = b; icona = b.getIcon(); hAlign = b.getHorizontalAlignment(); vAlign = b.getVerticalAlignment();
            hPos = b.getHorizontalTextPosition(); vPos = b.getVerticalTextPosition(); gap = b.getIconTextGap();
        } else {
            return;
        }
        Container entita = SwingUtilities.getAncestorOfClass(com.sqleo.querybuilder.DiagramAbstractEntity.class, c);
        boolean pulsanteConBordo = entita == null && c instanceof AbstractButton b && b.isBorderPainted()
                && !(c instanceof javax.swing.JMenuItem);
        if (entita == null && !pulsanteConBordo) {
            return;
        }
        java.awt.Insets in = jc.getInsets();
        java.awt.Rectangle vista = new java.awt.Rectangle(in.left, in.top,
                jc.getWidth() - in.left - in.right, jc.getHeight() - in.top - in.bottom);
        java.awt.Rectangle rIcona = new java.awt.Rectangle();
        java.awt.Rectangle rTesto = new java.awt.Rectangle();
        SwingUtilities.layoutCompoundLabel(jc, jc.getFontMetrics(jc.getFont()), testo, icona, vAlign, hAlign, vPos, hPos,
                vista, rIcona, rTesto, gap);
        java.awt.Rectangle contenuto = rIcona.width > 0 ? rTesto.union(rIcona) : rTesto;
        java.awt.Rectangle riquadro;
        if (entita != null) {
            java.awt.Insets ie = entita.getInsets();
            java.awt.Rectangle interno = new java.awt.Rectangle(ie.left, ie.top,
                    entita.getWidth() - ie.left - ie.right, entita.getHeight() - ie.top - ie.bottom);
            contenuto = SwingUtilities.convertRectangle(c, contenuto, entita);
            riquadro = interno;
        } else {
            riquadro = new java.awt.Rectangle(0, 0, c.getWidth(), c.getHeight());
        }
        marginiMisurati++;
        int sinistra = contenuto.x - riquadro.x;
        int destra = riquadro.x + riquadro.width - (contenuto.x + contenuto.width);
        if (sinistra < MARGINE_MINIMO || destra < MARGINE_MINIMO) {
            difetti.add("testo attaccato al bordo: «" + testo + "» margine sinistro " + sinistra + " px, destro " + destra
                    + " px (minimo " + MARGINE_MINIMO + ", " + c.getClass().getSimpleName()
                    + (entita != null ? " nell'entità del diagramma" : "") + ")");
        }
    }

    private static Color sfondoEffettivo(Component c) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (p.isOpaque() && p.getBackground() != null) {
                return p.getBackground();
            }
        }
        Color panel = UIManager.getColor("Panel.background");
        return panel != null ? panel : Color.WHITE;
    }

    private static double contrasto(Color a, Color b) {
        double la = luminanza(a);
        double lb = luminanza(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminanza(Color c) {
        return 0.2126 * canale(c.getRed()) + 0.7152 * canale(c.getGreen()) + 0.0722 * canale(c.getBlue());
    }

    private static double canale(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** Rapporto leggibile dal processo padre: una riga per voce. */
    static String rapporto(Esito e) {
        StringBuilder sb = new StringBuilder();
        sb.append("entita=").append(e.entita()).append('\n');
        sb.append("relazioni=").append(e.relazioni()).append('\n');
        sb.append("immagine=").append(e.larghezza()).append('x').append(e.altezza()).append('\n');
        sb.append("scalaFlatLaf=").append(e.scalaFlatLaf()).append('\n');
        sb.append("sql=").append(e.sqlDelModello()).append('\n');
        sb.append("margini testo-bordo controllati (minimo ").append(MARGINE_MINIMO).append(" px)=")
                .append(marginiMisurati).append('\n');
        for (String a : e.avvisi()) {
            sb.append("avviso=").append(a.replace('\n', ' ')).append('\n');
        }
        for (String d : e.difetti()) {
            sb.append("difetto=").append(d).append('\n');
        }
        return sb.toString();
    }

    /** JVM figlia del test S5: {@code QbRender <file.png> <rapporto.txt>}; la scala arriva da {@code -Dflatlaf.uiScale}. */
    public static void main(String[] args) throws Exception {
        Esito e = disegna(Paths.get(args[0]), 1.0);
        Files.writeString(Paths.get(args[1]), rapporto(e));
        System.exit(0);
    }

    /** Porta il pannello radice alla dimensione voluta e ne rifà la disposizione. */
    private static void dimensiona(JFrame frame, Dimension size) {
        javax.swing.JRootPane root = frame.getRootPane();
        root.setSize(size);
        root.validate();
    }
}
