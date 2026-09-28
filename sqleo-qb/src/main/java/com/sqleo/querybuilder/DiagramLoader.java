/*
 *
 * Modified by SQLeo Visual Query Builder :: java database frontend with join definitions
 * Copyright (C) 2012 anudeepgade@users.sourceforge.net
 * 
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
 * Modificato per RamaSQL Client (2026-09-21): (1) rimosso il limite di 3 tabelle per diagramma con la relativa richiesta a pagamento (createAndJoin); (2) rimossa la definizione manuale dei metadati (file di join definiti a mano); (3) caricamento sincrono: tolti la finestra modale di attesa e il thread, che modificava componenti Swing fuori dall EDT e poteva bloccarsi se il thread finiva prima di show(); ora funziona anche senza finestra antenata; (4) avvisi tramite la facciata QbHost; (5) metadati JDBC letti per catalogo (MySQL/MariaDB non hanno schemi) e join automatici anche da QbHost.joinHints; (6) senza connessione checkTable non lancia piu NullPointerException; (7) tolto da checkTable il blocco gia commentato in origine (fix ticket #119) che chiamava gli avvisi della classe Application di SQLeo.
 * Modificato per RamaSQL Client (2026-09-27): tabelle, colonne, chiavi primarie e chiavi esterne chieste alla facciata
 * (QbHost.metadata(), BUG-016) invece che a DatabaseMetaData; tolti i metodi JDBC rimasti senza uso; una tabella riceve un
 * alias automatico solo se e' gia' nel diagramma (in origine sempre: «`libri` libri»).
 * Modificato per RamaSQL Client (2026-09-28): (1) BUG-024: i metadati dell'operazione (nome esatto, colonne e, se si
 * propongono i join, chiavi esterne della tabella o delle tabelle collegate) si leggono prima, tutti insieme, fuori
 * dall'EDT (it.ramasql.qb.OffEdtQbMetadata: se la lettura e' lenta compare un'attesa che non congela l'interfaccia),
 * poi il diagramma si completa sull'EDT come prima, con le risposte gia' in memoria; (2) BUG-023: aggiunta una tabella
 * (fuori dal caricamento di un modello), il diagramma la sistema in modo che nessun join passi sotto un'altra tabella
 * (ViewDiagram.onEntityAdded).
 */

package com.sqleo.querybuilder;

import java.awt.Color;
import java.awt.Dialog;
import java.awt.Frame;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.sqleo.common.util.I18n;
import it.ramasql.qb.JoinHint;
import it.ramasql.qb.QbMetadata;
import it.ramasql.qb.QbRuntime;
import com.sqleo.querybuilder.syntax.QueryTokens;


public class DiagramLoader extends JDialog implements Runnable
{
	static public final int DEFAULT = 0;
	static public final int ALL_FOREIGN_TABLES = 1;
	static public final int ALL_PRIMARY_TABLES = 2;
	
	private JLabel message;
	
	private int mode;
	private boolean autoJoinRequested;
	
	private QueryBuilder builder;
	private QueryTokens.Table table;
	// RamaSQL (2026-09-28, BUG-024): i metadati di questa operazione (letti fuori dall'EDT da prefetch)
	private QbMetadata md;
	
	private DiagramLoader(Frame owner)
	{
		super(owner);
	}
	
	private DiagramLoader(Dialog owner)
	{
		super(owner);
	}

	public static void run(int mode, QueryBuilder builder, QueryTokens.Table table, boolean autojoin)
	{
		// RamaSQL: caricamento sincrono, senza finestra modale di attesa ne thread (vedi nota in testa al file)
		DiagramLoader loader = null;
		
		if(SwingUtilities.getWindowAncestor(builder) instanceof Frame)
			loader = new DiagramLoader((Frame)SwingUtilities.getWindowAncestor(builder));
		else if(SwingUtilities.getWindowAncestor(builder) instanceof Dialog)
			loader = new DiagramLoader((Dialog)SwingUtilities.getWindowAncestor(builder));
		else
			loader = new DiagramLoader((Frame)null);
		
		loader.message = new JLabel("",JLabel.CENTER);
		
		loader.autoJoinRequested = autojoin;
		loader.builder = builder;
		loader.table = table;
		loader.mode = mode;
		
		loader.run();
	}
	
	public void run()
	{
		try
		{
			md = builder.metadata(); // RamaSQL (2026-09-28, BUG-024)
			prefetch();
			switch(mode)
			{
				case ALL_FOREIGN_TABLES: addAllForeignTables();break;
				case ALL_PRIMARY_TABLES: addAllPrimaryTables();break;
				default: addTable(table);
			}
		}
		catch(SQLException sqle)
		{
			// #394 Designer: reversing query doesn't warn on closed connection 
			// System.out.println("[ DiagramLoader::run ]\n" + sqle);
			// RamaSQL (2026-09-27): alla facciata della scheda (che lo porta nel pannello Messaggi), in italiano; in origine
			// alla facciata di processo, che nel programma non ha una scheda a cui dirlo
			builder.getHost().alert(I18n.getFormattedString("querybuilder.message.metadataError",
					"Cannot read the metadata: {0}", new Object[]{sqle.getMessage()}));
		}
		finally
		{
			this.dispose();
		}
	}

	private void addTable(QueryTokens.Table table)
		throws SQLException
	{
		message.setText( I18n.getFormattedString("querybuilder.message.loading","Loading: {0}", new Object[]{"" + table.getIdentifier()}));
		boolean tableExists = checkTable(table);

		// fix #78 do not autoalias fields in subqueries
		// if(( QueryBuilder.autoAlias || (builder.browser.getQueryItem() instanceof BrowserItems.DiagramQueryTreeItem)) && table.getAlias()==null)
		if( QueryBuilder.autoAlias && table.getAlias()==null)
		{
			// RamaSQL (2026-09-27): l'alias serve solo se la tabella c'e' gia' (in origine: sempre, «`libri` libri»)
			if(builder.diagram.getEntity(table)==null)
			{
				createAndJoin(table, tableExists);
				return;
			}
			table.setAlias(table.getName());

			for(int i=0; builder.diagram.getEntity(table)!=null; i++)
			{
				if(mode==DEFAULT)
					table.setAlias(table.getName() + "_" + (char)(65+i));
				else
					return;
			}
		}
		else if(builder.diagram.getEntity(table)!=null)
		{
			if(mode==DEFAULT)
			{
				this.setVisible(false);
				JOptionPane.showMessageDialog(this,I18n.getString("querybuilder.message.tableLoadedAliasDisabled","Table already loaded and aliasing disabled!"), table.getIdentifier(), JOptionPane.WARNING_MESSAGE);
			}
			return;		    		
		}
		createAndJoin(table, tableExists);

	}

	private void createAndJoin(QueryTokens.Table table,boolean tableExists) throws SQLException{
		DiagramEntity item = creatEntity(table,tableExists);
		builder.diagram.addEntity(item);

		/* lo deve fare se provengo da: click su browser, da reference o da open all
		non lo deve fare se sto facendo setQueryModel! */
		if(!builder.isLoading() && QueryBuilder.selectAllColumns)
			item.setColumnSelections(true);

		if(autoJoinRequested && QueryBuilder.autoJoin)
			doAutoJoin(item);

		// RamaSQL (2026-09-28, BUG-023): posto per la tabella nuova senza join che passino sotto altre tabelle (durante il
		// caricamento di un modello la disposizione si fa una volta sola, alla fine: QueryBuilder.onLoad)
		if(!builder.isLoading())
			builder.diagram.onEntityAdded(item);
	}

	/*
	 * RamaSQL (2026-09-28, BUG-024): legge in una volta sola, fuori dall'EDT, tutto quello che l'operazione chiedera' ai
	 * metadati (gli stessi argomenti che useranno checkTable, creatEntity, addAll*Tables e doAutoJoin); le chiamate
	 * successive, sull'EDT, trovano le risposte in memoria. Una tabella che non esiste non e' un errore qui: la segnala
	 * checkTable come prima.
	 */
	private void prefetch()
		throws SQLException
	{
		if(!(md instanceof it.ramasql.qb.OffEdtQbMetadata)) return;
		final it.ramasql.qb.OffEdtQbMetadata off = (it.ramasql.qb.OffEdtQbMetadata)md;
		final String modelSchema = builder.getQueryModel().getSchema();
		final String rootCatalog = catalogFor(modelSchema == null ? table.getSchema() : modelSchema);
		final String otherCatalog = catalogFor(modelSchema);
		final boolean keys = autoJoinRequested && QueryBuilder.autoJoin
				&& (mode!=DEFAULT || builder.diagram.getEntities().length > 0);
		final String rootName = table.getName();
		final int readMode = mode;
		off.offEdt(rootName, () -> {
			ArrayList<String> names = new ArrayList<String>();
			if(readMode==ALL_FOREIGN_TABLES)
				for(QbMetadata.ForeignKey fk : off.exportedKeys(rootCatalog, rootName)) names.add(fk.foreignTable());
			else if(readMode==ALL_PRIMARY_TABLES)
				for(QbMetadata.ForeignKey fk : off.importedKeys(rootCatalog, rootName)) names.add(fk.primaryTable());
			else
				names.add(rootName);
			String catalog = readMode==DEFAULT ? rootCatalog : otherCatalog;
			for(String name : names)
			{
				String found = off.find(catalog, name);
				String exact = found!=null ? found : name;
				off.columns(catalog, exact);
				if(keys)
				{
					off.importedKeys(catalog, exact);
					off.exportedKeys(catalog, exact);
				}
			}
			return null;
		});
	}

	
	
	// RamaSQL (2026-09-27, BUG-016): tabelle collegate lette dai metadati della facciata (in origine: DatabaseMetaData)
	private void addAllForeignTables()
		throws SQLException
	{
		if(md==null) return; // RamaSQL (2026-09-28): i metadati dell'operazione (in origine: builder.metadata() qui)
		message.setText(I18n.getString("querybuilder.message.reading","reading...") );

		String schema = builder.getQueryModel().getSchema() == null ? table.getSchema() : builder.getQueryModel().getSchema();
		String catalog = catalogFor(schema);
		ArrayList<String> names = new ArrayList<String>();
		for(QbMetadata.ForeignKey fk : md.exportedKeys(catalog, table.getName())) names.add(fk.foreignTable());
		addTables(names);
	}

	private void addAllPrimaryTables()
		throws SQLException
	{
		if(md==null) return; // RamaSQL (2026-09-28): i metadati dell'operazione
		message.setText(I18n.getString("querybuilder.message.reading","reading..."));

		String schema = builder.getQueryModel().getSchema() == null ? table.getSchema() : builder.getQueryModel().getSchema();
		String catalog = catalogFor(schema);
		ArrayList<String> names = new ArrayList<String>();
		for(QbMetadata.ForeignKey fk : md.importedKeys(catalog, table.getName())) names.add(fk.primaryTable());
		addTables(names);
	}

	private void addTables(java.util.List<String> names)
		throws SQLException
	{
		java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<String>(names);
		for(String name : unique)
		{
			if(!name.equalsIgnoreCase(table.getName()))
				addTable(new QueryTokens.Table(null, name));
		}
	}
	
	private boolean checkTable(QueryTokens.Table table)
		throws SQLException
	{
		// RamaSQL (2026-09-27, BUG-016): esistenza e nome esatto dai metadati della facciata (in origine: DatabaseMetaData,
		// con un secondo tentativo in maiuscolo o minuscolo che ora fa QbMetadata.find)
		// RamaSQL (2026-09-28, BUG-024): md = i metadati dell'operazione, gia' letti fuori dall'EDT
		if(md==null) return true; // RamaSQL: senza metadati non si puo verificare (in origine: NullPointerException)

		String schema = builder.getQueryModel().getSchema() == null ? table.getSchema() : builder.getQueryModel().getSchema();
		String catalog = catalogFor(schema); // RamaSQL: vedi catalogFor; lo schema JDBC non esiste in MySQL/MariaDB

		String found = md.find(catalog, table.getName());
		if(found!=null && !found.equals(table.getName()))
			table.setName(found);
		return found!=null;
	}
	
	private DiagramEntity creatEntity(QueryTokens.Table table, boolean tableExists)
		throws SQLException
	{
		DiagramEntity item = new DiagramEntity(builder,table);
		if(!tableExists){
			item.setFontColorAndToolTip(QueryBuilder.missingColor(), QueryBuilder.missingTableTip(table.getName())); // RamaSQL (2026-09-27): colore del token, testo italiano
		}
		// RamaSQL (2026-09-27, BUG-016): colonne e chiave primaria dai metadati della facciata (in origine: DatabaseMetaData)
		// RamaSQL (2026-09-28, BUG-024): md = i metadati dell'operazione, gia' letti fuori dall'EDT
		item.setEnabled(md!=null);

		if(md!=null)
		{
			String name = item.getQueryToken().getName();
			String schema = builder.getQueryModel().getSchema() == null ? item.getQueryToken().getSchema() : builder.getQueryModel().getSchema();
			String catalog = catalogFor(schema); // RamaSQL: vedi catalogFor

			int pos = 0;
			for(QbMetadata.Column c : md.columns(catalog, name))
			{
				DiagramField field = item.addField(++pos, c.name(), c.primaryKey() ? "PRIMARY" : null);
				field.setToolTipText(c.name() + " : " + c.type());
			}
		}
		item.pack();
		
		return item;
	}
	
	private void doAutoJoin(DiagramEntity source)
		throws SQLException
	{
		// RamaSQL (2026-09-28, BUG-024): md = i metadati dell'operazione, gia' letti fuori dall'EDT
		if(builder.diagram.getEntities().length > 1)
		{
			String name = source.getQueryToken().getName();
			String schema = builder.getQueryModel().getSchema() == null ? source.getQueryToken().getSchema() : builder.getQueryModel().getSchema();
			String catalog = catalogFor(schema); // RamaSQL: vedi catalogFor

			message.setText( I18n.getFormattedString("querybuilder.message.loading.relations","check {0}'s relations ", new Object[]{"" + table.getIdentifier()}));

			// RamaSQL: se la facciata fornisce suggerimenti (FK reali + relazioni logiche del modello ER) si usano quelli,
			// altrimenti le chiavi esterne dei metadati (2026-09-27: della facciata, non piu' di DatabaseMetaData)
			java.util.List<JoinHint> hints = builder.getHost().joinHints(name);
			if(hints!=null && !hints.isEmpty()){
				join(hints,source);
			}else if(md!=null){
				java.util.List<JoinHint> keys = new ArrayList<JoinHint>();
				for(QbMetadata.ForeignKey fk : md.importedKeys(catalog, name))
					keys.add(new JoinHint(fk.name(), fk.primaryTable(), fk.primaryColumn(), fk.foreignTable(), fk.foreignColumn()));
				for(QbMetadata.ForeignKey fk : md.exportedKeys(catalog, name))
					keys.add(new JoinHint(fk.name(), fk.primaryTable(), fk.primaryColumn(), fk.foreignTable(), fk.foreignColumn()));
				join(keys,source);
			}

		}
	}
	
	/* RamaSQL: join suggeriti dalla facciata (QbHost.joinHints) */
	private void join(java.util.List<JoinHint> hints, DiagramEntity source)
	{
		String sourceName = source.getQueryToken().getName();
		for(JoinHint hint : hints)
		{
			boolean ispk;
			if(sourceName.equalsIgnoreCase(hint.primaryTable())) ispk = true;
			else if(sourceName.equalsIgnoreCase(hint.foreignTable())) ispk = false;
			else continue;
			
			DiagramEntity itemP = ispk ? source : builder.diagram.getEntity(null, hint.primaryTable());
			DiagramEntity itemF = ispk ? builder.diagram.getEntity(null, hint.foreignTable()) : source;
			
			if(itemP!=null && itemF!=null && !itemP.getQueryToken().toString().equalsIgnoreCase(itemF.getQueryToken().toString()))
			{
				DiagramField fP = itemP.getField(hint.primaryColumn(),true);
				DiagramField fF = itemF.getField(hint.foreignColumn(),true);
				if(fP==null || fF==null) continue;
				
				builder.diagram.join(itemP,fP,itemF,fF);
				builder.diagram.getRelations()[builder.diagram.getRelationCount()-1].setName(hint.name());
			}
		}
	}
	
	/* RamaSQL: in MySQL/MariaDB il prefisso di `catalogo`.`tabella` (che SQLeo chiama schema) e il catalogo JDBC;
	   lo schema JDBC non esiste. Senza prefisso vale il catalogo indicato dalla facciata (null = database corrente). */
	private String catalogFor(String schema)
	{
		return schema != null ? schema : builder.getHost().catalog();
	}
}
