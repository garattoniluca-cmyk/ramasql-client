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
 * Modificato per RamaSQL Client (2026-09-21): icona chiesta alla facciata (QbRuntime); rimosse le azioni Show content e Show definition, che aprivano finestre interne di SQLeo (ClientContent, ClientDefinition): nel client quelle funzioni stanno fuori dal query builder.
 * Modificato per RamaSQL Client (2026-09-27): intestazione con il nome senza backtick e l'alias solo se diverso dal nome.
 */

package com.sqleo.querybuilder;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JOptionPane;

import com.sqleo.common.util.I18n;
import it.ramasql.qb.QbIcon;
import it.ramasql.qb.QbRuntime;
import com.sqleo.querybuilder.syntax.QuerySpecification;
import com.sqleo.querybuilder.syntax.QueryTokens;
import com.sqleo.querybuilder.syntax.QueryTokens.Condition;
import com.sqleo.querybuilder.syntax.QueryTokens.Group;
import com.sqleo.querybuilder.syntax.QueryTokens.Sort;

public class DiagramEntity extends DiagramAbstractEntity
{
	private QueryTokens.Table querytoken;
	private static Icon icon;

	DiagramEntity(QueryBuilder builder,QueryTokens.Table qtoken)
	{
		super(builder);
		
		if (icon == null) icon = QbRuntime.host().icon(QbIcon.DIAG_TABLE);
		getHeaderMenu().setIcon(icon);
		
		getHeaderMenu().addSeparator();
		getHeaderMenu().add(new ActionOpenAllForeignTables());
		getHeaderMenu().add(new ActionOpenAllPrimaryTables());
		getHeaderMenu().add(new ActionReferences());
		getHeaderMenu().addSeparator();
		
		setQueryToken(qtoken);
	}
	void onCreate()
	{
		builder.browser.addFromClause(querytoken);
		doFlush();
	}
	
	void onDestroy()
	{
		fireDeselectAll();
		builder.diagram.removeAllRelation(this);
		builder.browser.removeFromClause(querytoken);
		
// Ticket #379  Designer: removing a table should remove all related columns (By Dana P)
		QuerySpecification qs = builder.browser.getQuerySpecification();
      
		Condition[] conditions = qs.getWhereClause();
      
		for (int i = 0; i < conditions.length; i++)
			if (conditions[i].getLeft().toString().indexOf(querytoken.getName()) != -1 || (querytoken.getAlias()!=null && conditions[i].getLeft().toString().indexOf(querytoken.getAlias()) != -1))
				builder.browser.removeWhereClause(conditions[i]);
      
		conditions = qs.getHavingClause();
      
		for (int i = 0; i < conditions.length; i++)
			if (conditions[i].getLeft().toString().indexOf(querytoken.getName()) != -1 || (querytoken.getAlias()!=null && conditions[i].getLeft().toString().indexOf(querytoken.getAlias()) != -1))
				builder.browser.removeHavingClause(conditions[i]);
      
		Group[] groups = qs.getGroupByClause();
      
		for (int i = 0; i < groups.length; i++)
			if (groups[i].toString().indexOf(querytoken.getName()) != -1 || (querytoken.getAlias()!=null && groups[i].toString().indexOf(querytoken.getAlias()) != -1))
				builder.browser.removeGroupByClause(groups[i]);
      
		Sort[] orders = builder.getQueryModel().getOrderByClause();
      
		for (int i = 0; i < orders.length; i++)
			if (orders[i].getExpression().toString().indexOf(querytoken.getName()) != -1 || (querytoken.getAlias()!=null && orders[i].getExpression().toString().indexOf(querytoken.getAlias()) != -1))
				builder.browser.removeOrderByClause(orders[i]);
// end #379
	}
	
	DiagramField addField(Integer ordinalPosition, String label, Object key)
	{
		DiagramField df = new DiagramField(this,label,key!=null);
		if(ordinalPosition!=null){
			df.position = ordinalPosition;
		}
		
		QueryTokens.Column ctoken = new QueryTokens.Column(querytoken,label);
		df.setQueryToken(ctoken);
		
		addField(df);
		return df;
	}

	public QueryTokens.Table getQueryToken()
	{
		return querytoken;
	}

	public void setQueryToken(QueryTokens.Table querytoken)
	{
		this.querytoken = querytoken;
		
		// ticket #55 (display table AND alias in header)
		// getHeaderMenu().setText(querytoken.getReference());
		// getHeaderMenu().setToolTipText(querytoken.isAliasSet() ? querytoken.toString() : null);
		// RamaSQL (2026-09-27): nome senza backtick e alias solo se diverso dal nome (in origine: «`libri` libri»)
		String name = com.sqleo.querybuilder.syntax.SQLFormatter.stripQuote(querytoken.getName());
		String alias = querytoken.isAliasSet() ? com.sqleo.querybuilder.syntax.SQLFormatter.stripQuote(querytoken.getAlias()) : null;
		getHeaderMenu().setText(alias == null || alias.equalsIgnoreCase(name) ? name : name + "  " + alias);
		getHeaderMenu().setToolTipText(querytoken.toString());

		pack();
	}	
	
//	/////////////////////////////////////////////////////////////////////////////
//	Menu Actions
//	/////////////////////////////////////////////////////////////////////////////
	private class ActionOpenAllForeignTables extends AbstractAction
	{
		private ActionOpenAllForeignTables()
		{
			super(I18n.getString("querybuilder.menu.openAllForeignTables","open all foreign tables"));
		}
		
		public void actionPerformed(ActionEvent e)
		{
			DiagramLoader.run(DiagramLoader.ALL_FOREIGN_TABLES, DiagramEntity.this.builder, DiagramEntity.this.getQueryToken(), true);
		}
	}
	
	private class ActionOpenAllPrimaryTables extends AbstractAction
	{
		private ActionOpenAllPrimaryTables()
		{
			super(I18n.getString("querybuilder.menu.openAllPrimaryTables","open all primary tables"));
		}
		
		public void actionPerformed(ActionEvent e)
		{
			DiagramLoader.run(DiagramLoader.ALL_PRIMARY_TABLES, DiagramEntity.this.builder, DiagramEntity.this.getQueryToken(), true);
		}
	}
	
	private class ActionReferences extends AbstractAction
	{
		private ActionReferences()
		{
			super(I18n.getString("querybuilder.menu.references","references..."));
		}
		
		public void actionPerformed(ActionEvent e)
		{
			new MaskReferences(DiagramEntity.this,DiagramEntity.this.builder).showDialog();
		}
	}
}
