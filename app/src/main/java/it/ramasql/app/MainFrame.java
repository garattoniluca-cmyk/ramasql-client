/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.app;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ResourceBundle;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

import it.ramasql.core.ProductInfo;

/** Finestra principale. Step 0: vuota; le tre zone (navigatore, schede, pannello SQL) arrivano nello Step 2. */
public class MainFrame extends JFrame {

    private static final ResourceBundle TEXTS = ResourceBundle.getBundle("it.ramasql.app.messages");

    public MainFrame() {
        super(ProductInfo.title());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImages(AppIcon.images());

        JLabel welcome = new JLabel(TEXTS.getString("welcome"), SwingConstants.CENTER);
        welcome.setFont(welcome.getFont().deriveFont(Font.PLAIN, 18f));
        welcome.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        add(welcome, BorderLayout.CENTER);

        setPreferredSize(new Dimension(1100, 700));
        pack();
        setLocationRelativeTo(null);
    }
}
