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
 * Modificato per RamaSQL Client (2026-09-21): dimensioni scalate chieste alla facciata (QbRuntime.scaledDimension) invece che a Preferences.
 * Modificato per RamaSQL Client (2026-09-27): la maschera cambia solo l'operatore; il tipo di join (le due caselle «tutte le
 * righe di…», che insieme davano un FULL OUTER JOIN sconosciuto a MySQL/MariaDB) si sceglie dal menu del nodo del join.
 * Modificato per RamaSQL Client (2026-09-28, T12.3/T12.9): suggerimenti dei campi.
 */

package com.sqleo.querybuilder;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import com.sqleo.common.util.I18n;
import it.ramasql.qb.QbRuntime;
import com.sqleo.querybuilder.syntax.QueryTokens;


public class MaskJoin extends BaseMask
{
	private JCheckBox allLeft;
	private JCheckBox allRight;
	private JComboBox operator;
	
	private DiagramRelation relation;
	
	public MaskJoin(DiagramRelation relation,QueryBuilder builder)
	{
		super("join.edit",builder);
		this.relation = relation;
		
		JLabel primary = new JLabel(relation.primaryField.querytoken.getIdentifier(), JLabel.CENTER);
		JLabel foreign = new JLabel(relation.foreignField.querytoken.getIdentifier(), JLabel.CENTER);
		
		Border border = new CompoundBorder(new LineBorder(it.ramasql.qb.QbRuntime.host().color(it.ramasql.qb.QbColor.BORDER)), new EmptyBorder(3,4,3,4)); // RamaSQL (2026-09-27): bordo del token, non nero
		primary.setBorder(border);
		primary.setOpaque(true);
		primary.setBackground(ViewDiagram.BGCOLOR_START_JOIN);
		foreign.setBorder(border);
		foreign.setOpaque(true);
		foreign.setBackground(ViewDiagram.BGCOLOR_JOINED);
		
		operator = new JComboBox(new String[]{"=","<",">","<=",">=","<>","!="});
		operator.setSelectedItem(relation.querytoken.getCondition().getOperator());
		operator.setName("qb.join.operator");
		it.ramasql.qb.QbTips.explainOperators(operator);
		operator.setToolTipText(I18n.getString("querybuilder.join.operator.tooltip", ""));
		
		GridBagLayout gbl = new GridBagLayout();
		JPanel pane = new JPanel(gbl);
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets	= new Insets(0,0,3,0);
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		
		gbc.weightx	= 1.0;
		gbc.fill = GridBagConstraints.BOTH;
		gbl.setConstraints(primary, gbc);
		pane.add(primary);
		
		gbc.weightx	= 0.0;
		gbc.fill = GridBagConstraints.NONE;
		gbl.setConstraints(operator, gbc);
		pane.add(operator);
		
		gbc.weightx	= 1.0;
		gbc.fill = GridBagConstraints.BOTH;
		gbl.setConstraints(foreign, gbc);
		pane.add(foreign);
		
		gbc.insets	= new Insets(8,0,0,0);
		allLeft = new JCheckBox(I18n.getFormattedString("querybuilder.checkbox.allRowsFrom","all rows from {0}", new Object[]{ ""+relation.primaryEntity.getHeaderMenu().getText()}) );
		allLeft.setName("qb.join.allLeft");
		allLeft.setToolTipText(I18n.getString("querybuilder.join.allRowsFrom.tooltip", ""));
		allLeft.setSelected(relation.querytoken.getType() == QueryTokens.Join.LEFT_OUTER || relation.querytoken.getType() == QueryTokens.Join.FULL_OUTER);
		pane.add(allLeft);
		gbl.setConstraints(allLeft, gbc);
		
		gbc.insets	= new Insets(0,0,0,0);
		allRight = new JCheckBox(I18n.getFormattedString("querybuilder.checkbox.allRowsFrom","all rows from {0}", new Object[]{ "" + relation.foreignEntity.getHeaderMenu().getText()}) );
		allRight.setName("qb.join.allRight");
		allRight.setToolTipText(I18n.getString("querybuilder.join.allRowsFrom.tooltip", ""));
		allRight.setSelected(relation.querytoken.getType() == QueryTokens.Join.RIGHT_OUTER || relation.querytoken.getType() == QueryTokens.Join.FULL_OUTER);
		pane.add(allRight);
		gbl.setConstraints(allRight, gbc);

		allLeft.setVisible(false);   // RamaSQL (2026-09-27): vedi onConfirm
		allRight.setVisible(false);
		add(pane);
	}
	
	public Dimension getPreferredSize()
	{
		return QbRuntime.scaledDimension(350,170);		
	}	
	
	protected void onShow(){}
	protected boolean onConfirm()
	{
		// RamaSQL (2026-09-27): il tipo di join si sceglie dal menu del nodo; qui resta quello attuale (niente FULL OUTER JOIN,
		// che MySQL e MariaDB non conoscono: in origine si otteneva spuntando le due caselle)
		relation.setValues(relation.querytoken.getType(),operator.getSelectedItem().toString());
		
		return true;
	}
}
