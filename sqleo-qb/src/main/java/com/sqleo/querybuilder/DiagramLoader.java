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
 */

package com.sqleo.querybuilder;

import java.awt.Color;
import java.awt.Dialog;
import java.awt.Frame;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.ListIterator;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.sqleo.common.util.I18n;
import it.ramasql.qb.JoinHint;
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
			QbRuntime.host().alert("[ DiagramLoader::run ]\n" + sqle);
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
	}

	
	
	private void addTables(ResultSet rs, int rsSchemaIndex, int rsTableIndex)
		throws SQLException
	{
		ArrayList list = new ArrayList();
		
		while(rs.next())
		{
			String schemaName = rs.getString(rsSchemaIndex);
			String tableName = rs.getString(rsTableIndex).trim();
			
			if(builder.getQueryModel().getSchema()!=null) schemaName = null;
			if(schemaName!=null) schemaName = schemaName.trim();
			
			list.add(new QueryTokens.Table(schemaName,tableName));
		}
		rs.close();
					
		for(ListIterator iter = list.listIterator(); iter.hasNext();)
		{
			addTable((QueryTokens.Table)iter.next());
		}
	}
	
	private void addAllForeignTables()
		throws SQLException
	{
		DatabaseMetaData dbmd = builder.getConnection().getMetaData();
		message.setText(I18n.getString("querybuilder.message.reading","reading...") );
		
		String schema = builder.getQueryModel().getSchema() == null ? table.getSchema() : builder.getQueryModel().getSchema();		
		String catalog = catalogFor(schema); schema = null; // RamaSQL: vedi catalogFor
		addTables(dbmd.getExportedKeys(catalog, schema, table.getName()) ,6,7);

	}
	
	private void addAllPrimaryTables()
		throws SQLException
	{
		DatabaseMetaData dbmd = builder.getConnection().getMetaData();
		message.setText(I18n.getString("querybuilder.message.reading","reading..."));
		
		String schema = builder.getQueryModel().getSchema() == null ? table.getSchema() : builder.getQueryModel().getSchema();
		String catalog = catalogFor(schema); schema = null; // RamaSQL: vedi catalogFor
		
		addTables(dbmd.getImportedKeys(catalog, schema, table.getName()) ,2,3);
	}
	
	private boolean checkTable(QueryTokens.Table table)
		throws SQLException
	{
		if(builder.getConnection()==null) return true; // RamaSQL: senza connessione non si puo verificare (in origine: NullPointerException)
		DatabaseMetaData dbmd = builder.getConnection().getMetaData();
		
		String name = table.getName();
		String schema = builder.getQueryModel().getSchema() == null ? table.getSchema() : builder.getQueryModel().getSchema();
		String catalog = catalogFor(schema); // RamaSQL: vedi catalogFor; lo schema JDBC non esiste in MySQL/MariaDB

		ResultSet rs = dbmd.getTables(catalog,null,name,null);
		boolean exists = rs.next();
		rs.close();
		
		if(!exists)
		{
			if(dbmd.storesLowerCaseIdentifiers())
			{
				name = name!=null ? name.toLowerCase() : null;
				schema = schema!=null ? schema.toLowerCase() : null;
				catalog = catalog!=null ? catalog.toLowerCase() : null;
			}
			else if(dbmd.storesUpperCaseIdentifiers())
			{
				name = name!=null ? name.toUpperCase() : null;
				schema = schema!=null ? schema.toUpperCase() : null;
				catalog = catalog!=null ? catalog.toUpperCase() : null;
			}
			
			rs = dbmd.getTables(catalog,null,name,null);
			if(exists = rs.next())
			{
				table.setName(name);
				if(builder.getQueryModel().getSchema() == null)
					table.setSchema(schema);
			}
			rs.close();
		}
		
		return exists;
	}
	
	private DiagramEntity creatEntity(QueryTokens.Table table, boolean tableExists)
		throws SQLException
	{
		DiagramEntity item = new DiagramEntity(builder,table);
		if(!tableExists){
			item.setFontColorAndToolTip(Color.red, table.getName()  + " : !!! missing !!! ");
		}
		item.setEnabled(builder.getConnection()!=null);
		
		if(builder.getConnection()!=null)
		{
			DatabaseMetaData dbmetadata = builder.getConnection().getMetaData();
			Hashtable primary = this.getPrimaryKeys(dbmetadata,item);
			
			String name = item.getQueryToken().getName();
			String schema = builder.getQueryModel().getSchema() == null ? item.getQueryToken().getSchema() : builder.getQueryModel().getSchema();
			String catalog = catalogFor(schema); schema = null; // RamaSQL: vedi catalogFor

			ResultSet rsColumns = dbmetadata.getColumns(catalog, schema, name, "%");
			while(rsColumns.next())
			{
				String columnName	= rsColumns.getString(4).trim();
				String typeName		= rsColumns.getString(6);
				int size	= rsColumns.getInt(7);
				int pos		= rsColumns.getInt(17);
				
				DiagramField field = item.addField(pos,columnName,primary.get(columnName));
				field.setToolTipText(columnName + " : " + typeName + "(" + size + ")");
			}
			rsColumns.close();
		}
		item.pack();
		
		return item;
	}
	
	private Hashtable getPrimaryKeys(DatabaseMetaData dbmetadata, DiagramEntity item)
	{
		Hashtable primary = new Hashtable();
		
		try
		{
			String name = item.getQueryToken().getName();
			String schema = builder.getQueryModel().getSchema() == null ? item.getQueryToken().getSchema() : builder.getQueryModel().getSchema();
			String catalog = catalogFor(schema); schema = null; // RamaSQL: vedi catalogFor

			ResultSet rsPK = dbmetadata.getPrimaryKeys(catalog, schema, name);
			while(rsPK.next())
				// il nome della chiave puo essere null
				primary.put(rsPK.getString(4).trim(), rsPK.getString(6)== null ? "PRIMARY" : rsPK.getString(6));
			rsPK.close();
		}
		catch (SQLException sqle)
		{
			System.out.println("[ DiagramLoader::getPrimaryKeys ]\n" + sqle);
		}
		
		return primary;
	}
	
	private void doAutoJoin(DiagramEntity source)
		throws SQLException
	{
		if(builder.diagram.getEntities().length > 1)
		{
			DatabaseMetaData dbmetadata = builder.getConnection().getMetaData();
			
			String name = source.getQueryToken().getName();
			String schema = builder.getQueryModel().getSchema() == null ? source.getQueryToken().getSchema() : builder.getQueryModel().getSchema();
			String catalog = catalogFor(schema); schema = null; // RamaSQL: vedi catalogFor
			
			message.setText( I18n.getFormattedString("querybuilder.message.loading.relations","check {0}'s relations ", new Object[]{"" + table.getIdentifier()}));
			
			// RamaSQL: se la facciata fornisce suggerimenti (FK reali + relazioni logiche del modello ER) si usano quelli,
			// altrimenti le chiavi esterne lette dai metadati JDBC
			java.util.List<JoinHint> hints = builder.getHost().joinHints(name);
			if(hints!=null && !hints.isEmpty()){
				join(hints,source);
			}else{
				join(dbmetadata.getImportedKeys(catalog, schema, name) , source, false);
				join(dbmetadata.getExportedKeys(catalog, schema, name) , source, true);
			}
			
		}
	}
	
	private void join(ResultSet rs, DiagramEntity source, boolean ispk)
		throws SQLException
	{
		while(rs.next())
		{
			String pkschema = rs.getString(2);
			String pktable	= rs.getString(3).trim();
			String pkcolumn = rs.getString(4).trim();
			String fkschema = rs.getString(6);
			String fktable	= rs.getString(7).trim();
			String fkcolumn = rs.getString(8).trim();
			String fkname	= rs.getString(12);
			
			if(builder.getQueryModel().getSchema()!=null)
				pkschema = fkschema = null;
			
			if(pkschema!=null) pkschema = pkschema.trim();
			if(fkschema!=null) fkschema = fkschema.trim();
			
			DiagramEntity itemP = ispk ? source : builder.diagram.getEntity(pkschema, pktable);
			DiagramEntity itemF = ispk ? builder.diagram.getEntity(fkschema, fktable) : source;
			
			if(itemP!=null && itemF!=null && !itemP.getQueryToken().toString().equalsIgnoreCase(itemF.getQueryToken().toString()))
			{
				DiagramField fP = itemP.getField(pkcolumn,true);
				DiagramField fF = itemF.getField(fkcolumn,true);
					
				builder.diagram.join(itemP,fP,itemF,fF);
				builder.diagram.getRelations()[builder.diagram.getRelationCount()-1].setName(fkname);
			}
		}
		rs.close();
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
