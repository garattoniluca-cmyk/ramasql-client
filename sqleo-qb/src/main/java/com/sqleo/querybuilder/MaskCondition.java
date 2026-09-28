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
 * Modificato per RamaSQL Client (2026-09-27): la casella SUBQUERY si attiva anche con gli operatori di confronto
 * (=, <, >, <=, >=, <>, !=), non solo con IN ed EXISTS; lo stato dei campi si ricalcola anche all'apertura.
 * Modificato per RamaSQL Client (2026-09-28, T12.3/T12.9): suggerimenti dei campi.
 */

package com.sqleo.querybuilder;

import com.sqleo.common.util.I18n;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.sqleo.querybuilder.syntax.QueryTokens;
import com.sqleo.querybuilder.syntax.SubQuery;
import com.sqleo.querybuilder.syntax._ReservedWords;


public class MaskCondition extends BaseMask implements ItemListener ,ChangeListener
{
	private JComboBox predicate;
	private JComboBox operator;
	private JCheckBox subquery;
	private JTextArea left;
	private JTextArea right;
	
	private QueryTokens.Condition querytoken;
	
	public MaskCondition(QueryTokens.Condition token,QueryBuilder builder)
	{
		super("condition.edit",builder);
		querytoken = token;

		predicate = new JComboBox(new String[]{_ReservedWords.AND,_ReservedWords.OR});
		operator = new JComboBox(new String[]{"=","<",">","<=",">=","<>","!=","LIKE","NOT LIKE","IS","IS NOT","IN","NOT IN","EXISTS","NOT EXISTS","BETWEEN","NOT BETWEEN"});
		subquery = new JCheckBox("<html><i>SUBQUERY");
		predicate.setName("qb.condition.predicate");
		predicate.setToolTipText(I18n.getString("querybuilder.condition.predicate.tooltip", ""));
		operator.setName("qb.condition.operator");
		it.ramasql.qb.QbTips.explainOperators(predicate);
		it.ramasql.qb.QbTips.explainOperators(operator);
		operator.setToolTipText(I18n.getString("querybuilder.condition.operator.tooltip", ""));
		subquery.setName("qb.condition.subquery");
		subquery.setToolTipText(I18n.getString("querybuilder.condition.subquery.tooltip", ""));
		subquery.setEnabled(false);
		subquery.setSelected(querytoken.getRight() instanceof SubQuery);
		
		operator.addItemListener(this);
		subquery.addChangeListener(this);

		JScrollPane scrollL = new JScrollPane(left = new JTextArea());
		JScrollPane scrollR = new JScrollPane(right = new JTextArea());
		left.setName("qb.condition.left");
		left.setToolTipText(I18n.getString("querybuilder.condition.left.tooltip", ""));
		right.setName("qb.condition.right");
		right.setToolTipText(I18n.getString("querybuilder.condition.right.tooltip", ""));
		
		left.setWrapStyleWord(true);
		right.setWrapStyleWord(true);
		
		left.setLineWrap(true);
		right.setLineWrap(true);

		left.setColumns(50);
		right.setColumns(50);
		
		left.setRows(5);
		right.setRows(5);
		
		GridBagLayout gbl = new GridBagLayout();
		JPanel pane = new JPanel(gbl);
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets	= new Insets(0,0,2,0);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		
		gbc.weightx	= 0.0;
		gbc.fill = GridBagConstraints.NONE;
		gbl.setConstraints(predicate, gbc);
		pane.add(predicate);
		
		gbc.weightx	= 1.0;
		gbc.fill = GridBagConstraints.BOTH;
		gbl.setConstraints(scrollL, gbc);
		pane.add(scrollL);
		
		gbc.weightx	= 0.0;
		gbc.fill = GridBagConstraints.NONE;
		gbc.gridwidth = GridBagConstraints.RELATIVE;
		gbl.setConstraints(operator, gbc);
		pane.add(operator);
		
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		gbl.setConstraints(subquery, gbc);
		pane.add(subquery);
		
		gbc.weightx	= 1.0;
		gbc.fill = GridBagConstraints.BOTH;
		gbl.setConstraints(scrollR, gbc);
		pane.add(scrollR);
		
		this.setComponentCenter(pane);
	}

	protected boolean onConfirm()
	{
		if(predicate.isEnabled())
			querytoken.setAppend(predicate.getSelectedItem().toString());
		
		querytoken.setOperator(operator.getSelectedItem().toString());
		
		if(isExistsSelected())
			querytoken.setLeft(null);
		else
			querytoken.setLeft(new QueryTokens.DefaultExpression(left.getText()));
		
		if(subquery.isSelected())
		{
			if(!(querytoken.getRight() instanceof SubQuery))
				querytoken.setRight(new SubQuery());
		}
		else
			querytoken.setRight(new QueryTokens.DefaultExpression(right.getText()));
		
		return true;
	}

	protected void onShow()
	{
		predicate.setEnabled(querytoken.getAppend()!=null);
		predicate.setSelectedItem(querytoken.getAppend());
		operator.setSelectedItem(querytoken.getOperator());
		
		if(querytoken.getLeft()!=null)
		{
			left.setText(querytoken.getLeft().toString());
			if(querytoken.getLeft() instanceof QueryTokens.DefaultExpression)
				if(((QueryTokens.DefaultExpression)querytoken.getLeft()).isEmpty())
					left.setText("");
		}
			
		if(querytoken.getRight()!=null)
		{
			right.setText(querytoken.getRight().toString());
			if(querytoken.getRight() instanceof QueryTokens.DefaultExpression)
				if(((QueryTokens.DefaultExpression)querytoken.getRight()).isEmpty())
					right.setText("");
		}
		// RamaSQL (2026-09-27): lo stato dei campi si ricalcola anche all'apertura; se l'operatore era gia' quello
		// selezionato («=», il primo) nessun evento lo faceva e la casella SUBQUERY restava spenta
		onChanged();
	}

	private boolean isExistsSelected()
	{
		return operator.getSelectedItem().toString().equals("EXISTS") || operator.getSelectedItem().toString().equals("NOT EXISTS");
	}

	private boolean isInSelected()
	{
		return operator.getSelectedItem().toString().equals("IN") || operator.getSelectedItem().toString().equals("NOT IN");
	}
	
	// RamaSQL (2026-09-27): la sottoquery vale anche per i confronti («prezzo > (SELECT AVG…)»), che il modello e il
	// parser rappresentano gia'; in origine la casella SUBQUERY si attivava solo con IN ed EXISTS
	/** RamaSQL (2026-09-27): per la facciata e i test */
	boolean isSubqueryEnabled()
	{
		return subquery.isEnabled();
	}

	private boolean isComparisonSelected()
	{
		String op = operator.getSelectedItem().toString();
		return op.equals("=") || op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=")
				|| op.equals("<>") || op.equals("!=");
	}

	private void onChanged()
	{
		subquery.setEnabled(isExistsSelected() || isInSelected() || isComparisonSelected());
		
		left.setEnabled(!isExistsSelected());
		right.setEnabled(!subquery.isSelected());
		
		left.setOpaque(left.isEnabled());
		right.setOpaque(right.isEnabled());
		
		if(!left.isEnabled())
			left.setText("");
		
		if(!right.isEnabled())
			right.setText("");
		
		if(!subquery.isEnabled())
			subquery.setSelected(false);
	}

	public void itemStateChanged(ItemEvent e)
	{
		onChanged();
	}
	
	public void stateChanged(ChangeEvent e)
	{
		onChanged();
	}
}