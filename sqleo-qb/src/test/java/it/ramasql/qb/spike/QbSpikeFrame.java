/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb.spike;

import java.awt.BorderLayout;
import java.sql.Connection;
import java.sql.DriverManager;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.formdev.flatlaf.FlatLightLaf;
import com.sqleo.querybuilder.QueryBuilder;

import it.ramasql.qb.BasicQbHost;
import it.ramasql.qb.QbRuntime;
import it.ramasql.qb.QbSql;

/**
 * Lanciatore dimostrativo dello spike S1: apre il query builder estratto da SQLeo in un JFrame nostro, sotto
 * FlatLaf chiaro. Non è un test e non fa parte del prodotto.
 *
 * <p>Connessione dalle variabili d'ambiente {@code RAMASQL_IT_MARIADB_URL}, {@code RAMASQL_IT_MARIADB_USER},
 * {@code RAMASQL_IT_MARIADB_PASSWORD} (mai credenziali nel codice); serve un driver JDBC nel classpath
 * (il modulo non ne dichiara: aggiungerlo al classpath all'avvio). Senza variabili si usano i metadati finti
 * della biblioteca ({@link FakeBiblioteca}), così la finestra si può provare comunque.
 */
public final class QbSpikeFrame {

    private QbSpikeFrame() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(QbSpikeFrame::apri);
    }

    private static void apri() {
        FlatLightLaf.setup();

        String url = System.getenv("RAMASQL_IT_MARIADB_URL");
        Connection connection;
        String origine;
        try {
            if (url == null || url.isBlank()) {
                connection = FakeBiblioteca.connection();
                origine = "metadati finti «biblioteca» (variabili RAMASQL_IT_MARIADB_* assenti)";
            } else {
                connection = DriverManager.getConnection(url, System.getenv("RAMASQL_IT_MARIADB_USER"),
                        System.getenv("RAMASQL_IT_MARIADB_PASSWORD"));
                origine = connection.getMetaData().getDatabaseProductName() + " - catalogo " + connection.getCatalog();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Connessione non riuscita: " + e.getMessage());
            return;
        }

        JFrame frame = new JFrame("RamaSQL - spike S1: query builder di SQLeo - " + origine);
        BasicQbHost host = new BasicQbHost() {
            @Override
            public Connection connection() {
                return connection;
            }

            @Override
            public String catalog() {
                try {
                    return connection.getCatalog();
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            public void alert(String message) {
                JOptionPane.showMessageDialog(frame, message);
            }
        };
        QbRuntime.setHost(host);
        QueryBuilder qb = new QueryBuilder(host);

        // striscia in basso: l'SQL si vede sempre (principio didattico del client)
        JTextArea sql = new JTextArea(5, 80);
        JButton daGrafico = new JButton("Grafico → SQL");
        daGrafico.addActionListener(e -> sql.setText(qb.getQueryModel().toString(true)));
        JButton versoGrafico = new JButton("SQL → Grafico");
        versoGrafico.addActionListener(e -> {
            QbSql.Result r = QbSql.check(sql.getText());
            if (r.representable()) {
                qb.setQueryModel(r.model());
            } else {
                JOptionPane.showMessageDialog(frame, "Query non rappresentabile nella vista grafica:\n" + r.reason());
            }
        });
        JPanel pulsanti = new JPanel();
        pulsanti.add(daGrafico);
        pulsanti.add(versoGrafico);
        JPanel sud = new JPanel(new BorderLayout());
        sud.add(new JScrollPane(sql), BorderLayout.CENTER);
        sud.add(pulsanti, BorderLayout.EAST);

        frame.getContentPane().add(qb, BorderLayout.CENTER);
        frame.getContentPane().add(sud, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(host.scale(1200), host.scale(800));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
