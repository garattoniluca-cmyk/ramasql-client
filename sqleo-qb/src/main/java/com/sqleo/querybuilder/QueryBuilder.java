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
 * Modificato per RamaSQL Client (2026-09-21): riferimenti ad Application, ConnectionAssistant, ConnectionHandler e ClientQueryBuilder sostituiti dalla facciata it.ramasql.qb.QbHost (costruttore QueryBuilder(QbHost), getHost()); identificatori tra backtick come valore predefinito; scheda syntax con QueryStyledDocument; ricarica del modello sull'EDT; tolta l'azione trova/sostituisci.
 * Modificato per RamaSQL Client (2026-09-22): caricando un modello con una tabella derivata, le sue colonne nella
 * SELECT esterna non ricevono piu' l'alias automatico del campo (t.col AS t_col): resta l'alias scritto (spike S2d).
 * Modificato per RamaSQL Client (2026-09-27): metadati chiesti alla facciata (metadata(), BUG-016); ascoltatori di
 * modifica della query (addQueryChangeListener/fireQueryChanged, BUG-006: il client aggiorna la sua vista SQL a ogni
 * gesto); scheda SQL interna nascondibile (hideSyntaxTab, BUG-006: la vista SQL e' quella del client).
 */

package com.sqleo.querybuilder;

import java.awt.Color;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.sqleo.common.gui.BorderLayoutPanel;
import com.sqleo.common.gui.TextView;
import com.sqleo.common.util.I18n;
import it.ramasql.qb.QbHost;
import it.ramasql.qb.QbRuntime;
import com.sqleo.querybuilder.BrowserItems.DefaultTreeItem;
import com.sqleo.querybuilder.syntax.DerivedTable;
import com.sqleo.querybuilder.syntax.QueryTokens;
import com.sqleo.querybuilder.syntax.QueryTokens.Column;
import com.sqleo.querybuilder.syntax.QueryTokens.Condition;
import com.sqleo.querybuilder.syntax.QueryTokens.DefaultExpression;
import com.sqleo.querybuilder.syntax.QueryTokens._Expression;
import com.sqleo.querybuilder.syntax.SQLFormatter;
import com.sqleo.querybuilder.syntax.SQLParser;
import com.sqleo.querybuilder.syntax.SubQuery;


public class QueryBuilder extends JTabbedPane implements ChangeListener
{
	private Connection connection;
	
	private boolean loading = false;
	
	public static boolean autoJoin = true;
	public static boolean autoAlias = true;
	// RamaSQL (2026-09-27): alias automatico delle colonne spuntate (t.col AS t_col) spento; le tabelle ricevono un
	// alias solo quando la stessa tabella compare due volte (vedi DiagramLoader.addTable)
	public static boolean autoAliasColumns = false;
	public static boolean useAlwaysQuote = true;

	/* querybuilder.objetctype.TABLE */
	public static boolean loadObjectsAtOnce = true;
	public static boolean selectAllColumns = true;
	
	// RamaSQL: solo MySQL/MariaDB, identificatori tra backtick (in origine: virgolette doppie)
	public static String identifierQuoteString 	= "`";
	public static int maxColumnNameLength = 0;

	private DiagramLayout layout;

	private TextView syntax;
	ViewBrowser browser;
	ViewDiagram diagram;
	ViewObjects objects;

	// RamaSQL: facciata verso l'applicazione (in origine: chiave del ConnectionHandler e finestra ClientQueryBuilder)
	private QbHost host;

	public QueryBuilder()
	{
		this(QbRuntime.host());
	}

	public QueryBuilder(QbHost host)
	{
		super(JTabbedPane.BOTTOM);

		this.host = host;

		QueryActions.init(this);

		this.initComponents();
		this.setConnection(host.connection());

		this.transferFocus();
	}

	public QbHost getHost()
	{
		return host;
	}

	// RamaSQL: metadati dalla facciata (in origine: DatabaseMetaData della connessione); null = nessun metadato
	public it.ramasql.qb.QbMetadata metadata()
	{
		return host.metadata();
	}

	// RamaSQL (2026-09-27, BUG-006): chi vuole sapere che la query e' cambiata (la vista SQL del client). La notifica
	// arriva sull'EDT, una sola volta per gesto, mai durante un caricamento.
	private final List<Runnable> queryChangeListeners = new java.util.concurrent.CopyOnWriteArrayList<Runnable>();
	private boolean changePending = false;
	private boolean syntaxHidden = false;

	public void addQueryChangeListener(Runnable listener)
	{
		queryChangeListeners.add(listener);
	}

	public void removeQueryChangeListener(Runnable listener)
	{
		queryChangeListeners.remove(listener);
	}

	public void fireQueryChanged()
	{
		fireQueryChanged(0);
	}

	private void fireQueryChanged(final int retries)
	{
		if(changePending || queryChangeListeners.isEmpty()) return;
		changePending = true;
		javax.swing.SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				changePending = false;
				// durante un caricamento si aspetta; se un caricamento fallito lasciasse «loading» acceso, dopo qualche
				// giro si notifica lo stesso (niente attesa infinita sull'EDT)
				if(loading && retries < 20) { fireQueryChanged(retries + 1); return; }
				for(Runnable r : queryChangeListeners) r.run();
			}
		});
	}

	// RamaSQL (2026-09-27, BUG-004): una colonna o tabella che sul server non c'e', con il colore della facciata e un
	// suggerimento in italiano (in origine: rosso puro e «!!! missing !!!»)
	static java.awt.Color missingColor()
	{
		return QbRuntime.host().color(it.ramasql.qb.QbColor.MISSING);
	}

	static String missingTip(String column)
	{
		return I18n.getFormattedString("querybuilder.missing.column", "{0}: column not found", new Object[]{column});
	}

	static String missingTableTip(String table)
	{
		return I18n.getFormattedString("querybuilder.missing.table", "{0}: table not found", new Object[]{table});
	}

	// RamaSQL (2026-09-27, BUG-006): toglie la scheda SQL interna, e con lei la linguetta: il client ha la sua vista SQL
	public void hideSyntaxTab()
	{
		if(syntaxHidden) return;
		syntaxHidden = true;
		setSelectedIndex(0);
		removeTabAt(1);
		putClientProperty("JTabbedPane.hideTabAreaWithOneTab", Boolean.TRUE);
	}

	private void initComponents()
	{
		browser = new ViewBrowser(this);
		diagram = new ViewDiagram(this);
		objects = new ViewObjects(this);
		
		// RamaSQL (2026-09-27): in alto l'elenco delle tabelle (cio' che si trascina, 70%), sotto l'albero della query
		// altezza ciascuno al primo layout (in origine: albero sopra, divisore a 250 px, e l'elenco finiva fuori vista)
		JSplitPane split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT)
		{
			private boolean placed = false;

			public void doLayout()
			{
				if(!placed && getHeight() > 0)
				{
					placed = true;
					setDividerLocation(getHeight() * 7 / 10);
				}
				super.doLayout();
			}
		};
		split2.setOneTouchExpandable(true);
		split2.setResizeWeight(0.7);
		split2.setTopComponent(objects);
		split2.setBottomComponent(browser);
		
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		split.setTopComponent(split2);
		split.setBottomComponent(diagram);
		split.setOneTouchExpandable(true);
		split.setDividerLocation(QbRuntime.scale(220)); // RamaSQL (2026-09-27): senza, albero ed elenco a sinistra restavano larghi pochi pixel
		split.setBorder(null);
		split2.setBorder(null);

		BorderLayoutPanel designer = new BorderLayoutPanel(2,2);
		designer.setComponentCenter(split);
		
		add(I18n.getString("querybuilder.designer","designer"),designer);
		add(I18n.getString("querybuilder.syntax","syntax"),syntax = new TextView(new QueryStyledDocument(), false));
		addChangeListener(this);		
	}
	
	public void setBackgroundColor(final Color color){
		diagram.setBackgroundColor(color);
		syntax.setBackgroundColor(color);
	}
	
	public boolean isDragAndDropEnabled()
	{
		return diagram.isDragAndDropEnabled();
	}
	
	public void setDragAndDropEnabled(boolean b)
	{
		diagram.setDragAndDropEnabled(b);
	}

	public final DiagramLayout getDiagramLayout()
	{
		layout.freeze();
		return layout;
	}
	
	public final void setDiagramLayout(DiagramLayout layout)
	{
		this.layout = layout;
		layout.setQueryBuilder(this);
		
		onModelChanged();
	}
	
	public final QueryModel getQueryModel()
	{
		if(layout == null)
		{
			layout = new DiagramLayout();
			layout.setQueryBuilder(this);
		}
		return layout.getQueryModel();
	}
	
	public final void setQueryModel(QueryModel qm)
	{
		layout = new DiagramLayout();
		layout.resetExtras(qm.getExtrasMap());
		layout.setQueryBuilder(this);
		layout.setQueryModel(qm);

		onModelChanged();
		fireQueryChanged(); // RamaSQL (2026-09-27)
	}
	
	private void onModelChanged()
	{
		loading = true;
		
		diagram.onModelChanged();
		browser.onModelChanged();
		objects.onModelChanged();
		
		loading = false;		
	}

	boolean isLoading()
	{
		return loading;
	}
	
	public Connection getConnection()
	{
        // ticket #84 check if Connection.isClosed() before getMetaData
		try {
			if(connection!=null && connection.isClosed()){
				// RamaSQL: la connessione si richiede alla facciata (in origine: ConnectionAssistant.getHandler)
				Connection fresh = host.connection();
				if(fresh!=null && fresh!=connection && !fresh.isClosed()){
					setConnection(fresh);
				}else{
					host.alert("No connection exists!");
				}
			}
		} catch (SQLException sqle) {
			System.out.println("[ QueryBuilder::getConnection ]\n" + sqle);
		}
		return connection;
	}
	
	public void setConnection(Connection connection)
	{
		try
		{
			this.connection = connection;
			
			if(connection!=null)
			{
				QueryBuilder.identifierQuoteString = connection.getMetaData().getIdentifierQuoteString();
				QueryBuilder.maxColumnNameLength = connection.getMetaData().getMaxColumnNameLength();
			}
			
			objects.onConnectionChanged();
		}
		catch(SQLException sqle)
		{
			System.out.println("[ QueryBuilder::setConnection ]\n" + sqle);
		}
	}
	
	void onLoad()
	{
		loading = true;
		
		load(browser.getQuerySpecification().getFromClause());
		load(browser.getQuerySpecification().getSelectList());
		load(browser.getQuerySpecification().getWhereClause());
		layout.resume();
		loading = false;
		
		convertJoins(browser.getQuerySpecification().getWhereClause());
		layout.freeze();
	}
	
	
	private void load(Condition[] whereClauses) {
		for(Condition whereClause : whereClauses){
			for(DiagramField field : getDiagramFieldsFromWhereClause(whereClause)){
				if(field!=null) field.setWhereIcon();
			}
		}
	}
	public List<DiagramField> getDiagramFieldsFromWhereClause(Condition whereClause){
		List<DiagramField> fields = new ArrayList<DiagramField>(2);
		DiagramField left = getDiagramFieldFromWhereToken(whereClause.getLeft());
		DiagramField right = getDiagramFieldFromWhereToken(whereClause.getRight());
		if(left!=null && right!=null){
			//don't set icons if both left and right clause belongs to diagram field
			return fields;
		}
		fields.add(left);
		fields.add(right);
		return fields;
	}
	private DiagramField getDiagramFieldFromWhereToken(QueryTokens._Expression whereToken){
		if(whereToken!=null){
			if(whereToken instanceof Column){
				QueryTokens.Column token = (QueryTokens.Column)whereToken;
				DiagramEntity entity = diagram.getEntity(token.getTable());
				if(entity!=null){
					DiagramField field =  entity.getField(token.getName());
					if(null == field){
						//add dummy column
						field = entity.addField(null, token.getName(), null);
						field.setFontColor(missingColor());
						field.setToolTipText(missingTip(token.getName()));
						entity.pack();
					}
					return field;
				}
			}else if (whereToken instanceof DefaultExpression){
				DiagramAbstractEntity[] entities = diagram.getEntities();
				for(int j=0; j<entities.length; j++){
					DiagramField field = null;
					if(entities[j] instanceof DiagramEntity) {
						DiagramEntity entity = (DiagramEntity)entities[j];
						String whereString = whereToken.toString();
						JPanel entityFields = entity.getFields();
						 
						for(int i = 0; i < entityFields.getComponentCount(); i++)
						{
						   DiagramField currentField = (DiagramField) entityFields.getComponent(i);
						   if (SQLFormatter.stripQuote(whereToken.toString()).indexOf(
						      SQLFormatter.stripQuote(currentField.getQueryToken().toString())) != -1)
						      whereString = currentField.getQueryToken().toString();   
						}
						field = entity.getField(whereString);
						if(null == field && whereToken.toString().lastIndexOf(SQLFormatter.DOT)!=-1){
							final String[] split = whereToken.toString().split("\\"+SQLFormatter.DOT);
							final String tableName = split[0];
							if (tableName.equals(entity.getQueryToken().getName()) 
								|| (entity.getQueryToken().getAlias()!=null && tableName.equals(entity.getQueryToken().getAlias())))
							{
								QueryTokens.DefaultExpression exp = (QueryTokens.DefaultExpression)whereToken;
								//add dummy column
								final String realColumn = getRealColumn(exp.getAlias(), exp.getValue());
								field = entity.addField(null,realColumn , null);
								field.setFontColor(missingColor());
								field.setToolTipText(missingTip(realColumn));
								entity.pack();
							}
						}
					}
					else if(entities[j] instanceof DiagramQuery){
						DiagramQuery entity = (DiagramQuery)entities[j];
						QueryTokens.DefaultExpression exp = (QueryTokens.DefaultExpression)whereToken;
						field = entity.getField(exp.getValue());
						if(null == field && exp.getValue().lastIndexOf(SQLFormatter.DOT)!=-1){
							final String[] split = exp.getValue().split("\\"+SQLFormatter.DOT);
							final String tableName = split[0];
							if(tableName.equals(entity.getQueryToken().getName())
								|| (entity.getQueryToken().getAlias()!=null && tableName.equals(entity.getQueryToken().getAlias())))
						   {
								//add dummy column
								final String realColumn = getRealColumn(exp.getAlias(), exp.getValue());
								field = entity.addField(realColumn);
								field.setFontColor(missingColor());
								field.setToolTipText(missingTip(realColumn));
								entity.pack();
						    }
						}
					}
					if(field!=null){
						return field;
					}
				}
			}
		}
		return null;
	}

	public static String getRealColumn(final String alias,final String column){
		final String realColumn;
		if(alias!=null){
			realColumn = alias;
		}else if (column!=null && column.contains(".")) {
			final String[] cols = column.split("\\.");
			realColumn = cols[cols.length-1];
		}else{
			realColumn = column;
		}
		return realColumn;
	}
	
	private void load(QueryTokens._TableReference[] tokens)
	{
		for(int i=0; i<tokens.length; i++)
		{
			if(tokens[i] instanceof QueryTokens.Table)
			{
				DiagramLoader.run(DiagramLoader.DEFAULT,this,(QueryTokens.Table)tokens[i],false);
			}
			else if(tokens[i] instanceof DerivedTable) // added for ticket #80
			{
				DerivedTable subQuery = (DerivedTable)tokens[i];
				DiagramQuery entity = new DiagramQuery(this,subQuery);
				// display derived table fields
				for(final _Expression exp : subQuery.getQuerySpecification().getSelectList()){
					if(exp instanceof DefaultExpression){
						final DefaultExpression column = (DefaultExpression) exp;
						entity.addField(getRealColumn(column.getAlias(),column.getValue()));
					}else if(exp instanceof QueryTokens.Column){
						QueryTokens.Column column = (QueryTokens.Column)exp;
						entity.addField(getRealColumn(column.getAlias(),column.getName()));
					}else if(exp instanceof SubQuery){
						SubQuery column = (SubQuery)exp;
						entity.addField(getRealColumn(column.getAlias(),null));
					}
				}
				entity.pack();
				this.diagram.addEntity(entity);
			}
			else
			{
				doJoin((QueryTokens.Join)tokens[i]);
			}
		}
	}
	
	private void load(QueryTokens._Expression[] tokens)
	{
		for(int i=0; i<tokens.length; i++)
		{
			if(tokens[i] instanceof QueryTokens.Column)
			{
				QueryTokens.Column token = (QueryTokens.Column)tokens[i];
				
				DiagramAbstractEntity entity = diagram.getEntity(token.getTable());
				if(entity == null){
					//search for derived table 
					entity = diagram.getDerivedEntity(token.getTable());
				}
				if(entity!=null)
				{
					DiagramField field = entity.getField(token.getName());
					if(field!=null)
					{
						field.setQueryToken(token);
						field.setSelected(true);
					}
				}				
			}
			else if(tokens[i] instanceof QueryTokens.DefaultExpression)
			{
				//F-1730329 : verifica se e una colonna senza nome tabella specificato
				DiagramAbstractEntity[] entities = diagram.getEntities();
				for(int j=0; j<entities.length; j++)
				{
					DiagramAbstractEntity entity = null;
					DiagramField field = null;
					if(entities[j] instanceof DiagramEntity) { 
					  entity = (DiagramEntity)entities[j];
					  field = entity.getField(tokens[i].toString());
					  if(field!=null)
						{
							QueryTokens.Column token = new QueryTokens.Column(entity.getQueryToken(),tokens[i].toString());
							field.setQueryToken(token);

							BrowserItems.DefaultTreeItem item = (BrowserItems.DefaultTreeItem)browser.getQueryItem().getChildAt(0);
							for(int k=0; k<item.getChildCount(); k++)
							{
								DefaultTreeItem child = (DefaultTreeItem)item.getChildAt(k);
								if(child.getUserObject().toString().equalsIgnoreCase(tokens[i].toString()))
								{
									browser.getQuerySpecification().setSelectList(k,token);
									child.setUserObject(token);
									browser.reload(child);
									field.setSelected(true);
								}
							}
						}
					}else if(entities[j] instanceof DiagramQuery){
						entity = (DiagramQuery)entities[j];
						QueryTokens.DefaultExpression exp = (QueryTokens.DefaultExpression)tokens[i];
						field = entity.getField(getRealColumn(exp.getAlias(), exp.getValue()));
						if(field!=null){
							// RamaSQL Client: la colonna della tabella derivata tiene l'alias scritto dall'utente (o nessuno),
							// non quello automatico del campo (t.col -> t_col), che cambiava i nomi delle colonne del risultato
							field.getQueryToken().setAlias(exp.isAliasSet() ? exp.getAlias() : null);
							BrowserItems.DefaultTreeItem item = (BrowserItems.DefaultTreeItem)browser.getQueryItem().getChildAt(0);
							for(int k=0; k<item.getChildCount(); k++)
							{
								DefaultTreeItem child = (DefaultTreeItem)item.getChildAt(k);
								if(child.getUserObject().toString().equalsIgnoreCase(tokens[i].toString()))
								{
									browser.getQuerySpecification().setSelectList(k,field.getQueryToken());
									child.setUserObject(field.getQueryToken());
									browser.reload(child);
									field.setSelected(true);
								}
							}
						}else{
							entity.setColumnSelections(true);
						}
					}
				}
			}
		}
	}
	
	private void convertJoins(QueryTokens.Condition[] tokens)
	{
		DiagramAbstractEntity[] entities = diagram.getEntities();
		if(entities.length < 2) return;

		Hashtable tables = new Hashtable();
		for(int i=0; i<entities.length; i++)
		{
			if(entities[i] instanceof DiagramEntity){
				DiagramEntity entity = (DiagramEntity)entities[i];
				QueryTokens._TableReference token = entity.getQueryToken();
				tables.put(SQLFormatter.stripQuote(((QueryTokens.Table)token).getReference()),token);
			}
			else if(entities[i] instanceof DiagramQuery){
				DiagramQuery entity = (DiagramQuery)entities[i];
				QueryTokens._TableReference token = entity.getQueryToken();
				tables.put(SQLFormatter.stripQuote(((QueryTokens.Table)token).getReference()),token);
			}
		}
		
		for(int i=0; i<tokens.length; i++)
		{
			if(tokens[i].getLeft() instanceof SubQuery || tokens[i].getRight() instanceof SubQuery) continue;

			try
			{
				QueryTokens.Column left = SQLParser.doConvertColumn(tables,tokens[i].getLeft());
				QueryTokens.Column right = SQLParser.doConvertColumn(tables,tokens[i].getRight());
				
				if(left == null || right == null) continue;
				
				//System.out.println("**** dovrebbe essere una join ****");
				QueryTokens.Join token = new QueryTokens.Join(left,tokens[i].getOperator(),right);
				DiagramRelation relation = doJoin(token);
				if(relation!=null)
				{
				   browser.removeWhereClause(tokens[i]);
					// #393 Designer: reversing query never ends 
					// onModelChanged();
				}
			}
			catch(IOException e)
			{
			}
		}
	}
	
	private DiagramRelation doJoin(QueryTokens.Join token)
	{
		DiagramRelation relation = null;
		
		DiagramAbstractEntity entityP = diagram.getEntity(token.getPrimary().getTable());
		if(entityP == null)
		{
			//search for derived table 
			entityP = diagram.getDerivedEntity(token.getPrimary().getTable());
			if(entityP == null){
			 DiagramLoader.run(DiagramLoader.DEFAULT,this,token.getPrimary().getTable(),false);
			 entityP = diagram.getEntity(token.getPrimary().getTable());
			}
		}
		
		DiagramAbstractEntity entityF = diagram.getEntity(token.getForeign().getTable());
		if(entityF == null)
		{
			//search for derived table 
			entityF = diagram.getDerivedEntity(token.getForeign().getTable());
			if(entityF == null){
				DiagramLoader.run(DiagramLoader.DEFAULT,this,token.getForeign().getTable(),false);
				entityF = diagram.getEntity(token.getForeign().getTable());
			}
		}
		
		DiagramField fieldP = entityP.getField(token.getPrimary().getName());
		DiagramField fieldF = entityF.getField(token.getForeign().getName());
		
		if (fieldP == null) {
			// ticket # 150 relation is lost
			// add missing field in red color
			// JOptionPane.showMessageDialog(this, "Field " + token.getPrimary().getName() +" Not found in table" + token.getPrimary().getTable(), "Join" , JOptionPane.WARNING_MESSAGE);			
			final String fieldLabel = token.getPrimary().getName();
			if(entityP instanceof DiagramEntity){
				DiagramEntity entityPReal = (DiagramEntity) entityP;
				fieldP = entityPReal.addField(null, fieldLabel, null);
			}else if (entityP instanceof DiagramQuery){
				DiagramQuery entityPReal = (DiagramQuery) entityP;
				fieldP = entityPReal.addField(fieldLabel);
			}
			if(fieldP!=null){
				fieldP.setFontColor(missingColor());
				fieldP.setToolTipText(missingTip(fieldLabel));
				entityP.pack();
			}
		} 

		if (fieldF == null) {
			// ticket # 150 relation is lost
			// add missing field in red color
			// JOptionPane.showMessageDialog(this, "Field " + token.getForeign().getName() + " Not found in table" + token.getForeign().getTable(), "Join" , JOptionPane.WARNING_MESSAGE);			
			final String fieldLabel = token.getForeign().getName();
			if(entityF instanceof DiagramEntity){
				DiagramEntity entityFReal = (DiagramEntity) entityF;
				fieldF = entityFReal.addField(null, fieldLabel, null);
			}else if (entityF instanceof DiagramQuery){
				DiagramQuery entityFReal = (DiagramQuery) entityF;
				fieldF = entityFReal.addField(fieldLabel);
			}
			if(fieldF!=null){
				fieldF.setFontColor(missingColor());
				fieldF.setToolTipText(missingTip(fieldLabel));
				entityF.pack();
			}


		}
		
		if(fieldP!=null && fieldF!=null)
		{
			diagram.join(entityP,fieldP, token.getCondition().getOperator());
			diagram.join(entityF,fieldF, token.getCondition().getOperator());
			
			relation = diagram.getRelation(token);
			if(relation!=null) relation.setQueryToken(token);

		} 

		
		return relation;
	}
	
	public boolean hasMultipleQueries(){
		final String query = getSyntax().getText().trim();
		final int scIndex = query.indexOf(';');
		if(scIndex!=-1 && 
				(query.indexOf("select", scIndex)!=-1 
				  || query.indexOf("SELECT", scIndex)!=-1)
		){
			return true;
		}
		return false;
	}
	
	public void stateChanged(ChangeEvent ce)
	{
		if(syntaxHidden) return; // RamaSQL (2026-09-27): senza la scheda SQL interna non c'e' nulla da sincronizzare qui
		this.getActionMap().get(QueryActions.DIAGRAM_SAVE_AS_IMAGE).setEnabled(this.getSelectedIndex() == 0);
		this.getActionMap().get(QueryActions.ENTITIES_ARRANGE_GRID).setEnabled(this.getSelectedIndex() == 0);
		this.getActionMap().get(QueryActions.ENTITIES_ARRANGE_SPRING).setEnabled(this.getSelectedIndex() == 0);
		this.getActionMap().get(QueryActions.ENTITIES_REMOVE).setEnabled(this.getSelectedIndex() == 0);
		this.getActionMap().get(QueryActions.ENTITIES_PACK).setEnabled(this.getSelectedIndex() == 0);

		if(this.getSelectedIndex() == 0)
		{
			String msql = layout.getQueryModel().toString(true);
			String tsql = syntax.getText();
			
			if(!tsql.equals(msql))
			{
				int choice = JOptionPane.showConfirmDialog(this,I18n.getString("querybuilder.syntaxChanged","syntax changed!\ndo you want to apply changes (designer need to reload)?"));
				if(choice == JOptionPane.YES_OPTION)
				{
					// RamaSQL: in coda sull'EDT (in origine: un thread in attesa attiva che ricaricava il modello fuori dall'EDT)
					javax.swing.SwingUtilities.invokeLater(new Runnable()
					{
						public void run()
						{
							try
							{
								QueryModel qm = SQLParser.toQueryModel(syntax.getText());
								qm.setSchema(QueryBuilder.this.layout.getQueryModel().getSchema());
								QueryBuilder.this.setQueryModel(qm);
							}
							catch(IOException e)
							{
							}
						}
					});
				}
			}
		}
		else{
			layout.freeze();
			this.getQueryModel().resetExtrasMap(layout.getExtras());
			syntax.setText(this.getQueryModel().toString(true));
		}
	}

	public TextView getSyntax() {
		return syntax;
	}

}
