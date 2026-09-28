/*
 * SQLeonardo :: java database frontend
 * Copyright (C) 2004 nickyb@users.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 *
 * Modificato per RamaSQL Client (2026-09-21): l'elenco degli oggetti si legge dal catalogo indicato dalla facciata (QbHost.catalog) invece che senza catalogo.
 * Modificato per RamaSQL Client (2026-09-27): elenco letto dai metadati della facciata (QbHost.metadata(), BUG-016) invece
 * che da DatabaseMetaData; tolte le due liste a discesa (filtro per schema, che in MySQL/MariaDB non esiste, e per
 * tipo): l'elenco mostra le tabelle e poi le viste del catalogo, sempre tutte; objectNames() per la facciata.
 * Modificato per RamaSQL Client (2026-09-28, T12.9): suggerimento (tooltip) dell'elenco delle tabelle.
 */

package com.sqleo.querybuilder;

import com.sqleo.common.util.I18n;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListModel;

import com.sqleo.common.gui.BorderLayoutPanel;
import com.sqleo.querybuilder.beans.Entity;
import com.sqleo.querybuilder.dnd.EntityTransferHandler;
import com.sqleo.querybuilder.syntax.QueryTokens;

import it.ramasql.qb.QbMetadata;


public class ViewObjects extends BorderLayoutPanel
{
	public final static String ALL_TABLE_TYPES = "All";
	private QueryBuilder builder;

	private JList jListObjects;
	// RamaSQL: resta (non visibile) perche' altre classi ereditate lo leggono
	public JComboBox jComboBoxSchemas = new JComboBox();

	ViewObjects(QueryBuilder builder)
	{
		this.builder = builder;
		initComponents();
	}

	private void initComponents()
	{
		jListObjects = new JList();
		jListObjects.setDragEnabled(true);
		jListObjects.setName("qb.objects");
		// RamaSQL (2026-09-28, T12.9): cosa si fa con l'elenco
		jListObjects.setToolTipText(I18n.getString("querybuilder.objects.tooltip",
			"Tables and views of the catalog: drag one into the diagram, or double-click it."));

		jListObjects.setTransferHandler(new EntityTransferHandler());

		jListObjects.setCellRenderer(new ObjectsListCellRenderer());
		jListObjects.addMouseListener(new ClickHandler());

		setComponentCenter(new JScrollPane(jListObjects));
	}

	// RamaSQL (2026-09-27): tabelle e poi viste, dai metadati della facciata
	void onConnectionChanged() throws SQLException
	{
		Vector vObjects = new Vector();
		QbMetadata md = builder.metadata();
		if (md != null)
		{
			for (String t : md.tables(null)) vObjects.addElement(new Entity(null, t));
			for (String v : md.views(null)) vObjects.addElement(new Entity(null, v));
		}
		jListObjects.setListData(vObjects);
	}

	void onModelChanged()
	{
	}

	/** RamaSQL: l'elenco (per la facciata e i test). */
	JList objectsList()
	{
		return jListObjects;
	}

	/** RamaSQL: i nomi mostrati nell'elenco, nell'ordine. */
	List<String> objectNames()
	{
		List<String> out = new ArrayList<String>();
		ListModel model = jListObjects.getModel();
		for (int i = 0; i < model.getSize(); i++)
			out.add(((Entity)model.getElementAt(i)).getEntityName());
		return out;
	}

	private class ClickHandler extends MouseAdapter
	{
		public void mousePressed(MouseEvent e)
		{
			if (e.getClickCount() == 2)
			{
				Entity selectedItem = (Entity)jListObjects.getSelectedValue();
				if (selectedItem == null) return;
				QueryTokens.Table token = new QueryTokens.Table(selectedItem.getSchema(), selectedItem.getEntityName());
				DiagramLoader.run(DiagramLoader.DEFAULT, builder, token, true);
			}
		}
	}
}
