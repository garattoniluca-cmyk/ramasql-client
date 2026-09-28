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
 * Modificato per RamaSQL Client (2026-09-21): icona e dimensioni scalate chieste alla facciata (QbRuntime) invece che ad Application e Preferences; rimossa la trasformazione a tabella incrociata (pseudo-funzione eseguibile solo da SQLeo); la concatenazione di gruppo usa GROUP_CONCAT nativo di MySQL/MariaDB. Modificato per RamaSQL Client (2026-09-22): margine a destra del nome del campo (4 px scalati): il nome piu' lungo di un'entita' toccava il bordo del riquadro (controllo del margine di 2 px nello spike S5). Modificato per RamaSQL Client (2026-09-22): le icone del campo in WHERE si chiedono alla facciata (QbIcon.QB_WHERE, QbIcon.QB_KEYANDWHERE) invece che ai campi statici del renderer dell'albero, riscritto da zero.
 * Modificato per RamaSQL Client (2026-09-27): con l'icona del filtro l'entita' si allarga, se serve, invece di troncare il
 * nome del campo (BUG-011).
 * Modificato per RamaSQL Client (2026-09-28, T12.3/T12.9): voce SELECT dal file dei testi e suggerimenti delle voci del menu.
 */

package com.sqleo.querybuilder;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import com.sqleo.common.gui.BorderLayoutPanel;
import com.sqleo.common.util.I18n;
import com.sqleo.common.util.SQLHelper;
import it.ramasql.qb.QbIcon;
import it.ramasql.qb.QbRuntime;
import com.sqleo.querybuilder.dnd.DragMouseAdapter;
import com.sqleo.querybuilder.dnd.RelationDropTargetListener;
import com.sqleo.querybuilder.dnd.RelationTransferHandler;
import com.sqleo.querybuilder.syntax.QueryTokens;
import com.sqleo.querybuilder.syntax._ReservedWords;


public class DiagramField extends JPanel implements ItemListener, MouseListener, PopupMenuListener
{
	private static long expr_counter = 0;
	
	int position;
	static Icon keyIcon = null;
	private boolean primaryKey = false;
	private boolean inWhereClause = false;

	QueryTokens.Column querytoken;
	private DiagramAbstractEntity owner;

	private JCheckBox checkboxComponent = null;
	private JLabel labelComponent = null;

	private MouseListener listener;

	DiagramField(DiagramAbstractEntity entity, String label)
	{
		this(entity,label,false);
	}
	
	DiagramField(DiagramAbstractEntity entity, String label, boolean iskey)
	{
		super();
		setOwner(entity);
		setName(label);
		
		if (keyIcon == null)
			keyIcon = QbRuntime.host().icon(QbIcon.DIAG_FIELD);

		this.setLayout(new BorderLayout());
		checkboxComponent = new JCheckBox();
		// RamaSQL (2026-09-28, T12.9): la casella dice che cosa fa
		checkboxComponent.setName("qb.field.select");
		checkboxComponent.setToolTipText(I18n.getFormattedString("querybuilder.field.select.tooltip",
				"{0}", new Object[]{label}));

		labelComponent = new JLabel(label);
		labelComponent.setHorizontalTextPosition(JLabel.LEFT);
		// RamaSQL Client: margine tra il testo (o l'icona della chiave) e il bordo destro dell'entita'
		labelComponent.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, QbRuntime.scale(4)));
		
		Font f = labelComponent.getFont();
		if (!iskey)
		{
			f = new Font(f.getName(), Font.PLAIN, f.getSize());
		}
		else
		{
			primaryKey = true;
			f = new Font(f.getName(), Font.BOLD, f.getSize());
			labelComponent.setIcon(keyIcon);
		}
		labelComponent.setFont(f);

		this.add(getCheckboxComponent(), BorderLayout.WEST);
		this.add(labelComponent, BorderLayout.CENTER);

		getLabelComponent().addMouseListener(this);
		getLabelComponent().addMouseListener(listener = new DragMouseAdapter());
		getLabelComponent().setTransferHandler(new RelationTransferHandler());
		getLabelComponent().setDropTarget(new DropTarget(this, new RelationDropTargetListener(getOwner().builder.diagram)));

		getCheckboxComponent().addItemListener(this);
		getCheckboxComponent().addMouseListener(this);
		// RamaSQL (2026-09-27): larghezza scalata, altezza quella dell'icona della casella (in origine 8 px scalati: piu'
		// bassa dell'icona, cosi' a 150% le caselle si toccavano e sfioravano il nome)
		getCheckboxComponent().setPreferredSize(new java.awt.Dimension(QbRuntime.scale(22),
				getCheckboxComponent().getPreferredSize().height));
		getCheckboxComponent().setBorderPainted(false);
		getCheckboxComponent().setFocusPainted(false);
		getCheckboxComponent().setOpaque(false);
		
		setOpaque(true);
		setBackground(ViewDiagram.BGCOLOR_DEFAULT);
		setBorder(new LineBorder(UIManager.getColor("List.background")));
	}
	
	public String getLabel()
	{
		return this.getName();
	}
	
	public void setToolTipText(String text)
	{
		getLabelComponent().setToolTipText(text);
	}
	
	public void setFontColor(final Color color){
		getLabelComponent().setForeground(color);
	}
	
	QueryTokens.Column getQueryToken()
	{
		return querytoken;
	}

	void setQueryToken(QueryTokens.Column token)
	{
		querytoken = token;
	}

	public void itemStateChanged(ItemEvent ie)
	{
		getOwner().onSelectionChanged(this);
	}

	// RamaSQL (2026-09-28, T12.9): il menu del campo, con i suggerimenti delle voci (anche per le prove)
	JPopupMenu fieldMenu()
	{
		JPopupMenu popup = new JPopupMenu(this.getName());
		popup.addPopupMenuListener(this);
		popup.add(new MenuItemSelect());
		popup.addSeparator();
		popup.add(new ActionAddWhere());
		popup.add(new ActionAddHaving());
		popup.addSeparator();
		popup.add(new ActionAddExpression());
		it.ramasql.qb.QbTips.explain(popup);
		return popup;
	}

	public void mouseReleased(MouseEvent me)
	{
		if (SwingUtilities.isRightMouseButton(me))
		{
			fieldMenu().show(this, me.getX(), me.getY());
		}
		else if (!this.getOwner().builder.isDragAndDropEnabled())
		{
			getOwner().builder.diagram.join(this.getOwner(),this,"=");
		}
	}

	public void mouseExited(MouseEvent me)
	{
	}
	public void mouseClicked(MouseEvent me)
	{
	}
	public void mouseEntered(MouseEvent me)
	{
	}
	public void mousePressed(MouseEvent me)
	{
	}

	public void popupMenuCanceled(PopupMenuEvent pme)
	{
	}

	public void popupMenuWillBecomeInvisible(PopupMenuEvent pme)
	{
		setBorder(new LineBorder(UIManager.getColor("List.background")));
	}

	public void popupMenuWillBecomeVisible(PopupMenuEvent pme)
	{
		setBorder(new LineBorder(UIManager.getColor("List.selectionBackground")));
	}

	//	/////////////////////////////////////////////////////////////////////////////
	//	Join Manager
	//	/////////////////////////////////////////////////////////////////////////////
	private int joins;
	boolean isJoined()
	{
		return joins > 0;
	}

	void joined()
	{
		joins++;
	}

	void unjoined()
	{
		joins--;
	}

	void setDragAndDropEnabled(boolean b)
	{
		if(b)
			getLabelComponent().addMouseListener(listener);
		else
			getLabelComponent().removeMouseListener(listener);
	}

	//	/////////////////////////////////////////////////////////////////////////////
	//	Popup Actions
	//	/////////////////////////////////////////////////////////////////////////////
	private class MenuItemSelect extends JCheckBoxMenuItem implements ActionListener
	{
		private MenuItemSelect()
		{
			super(I18n.getString("querybuilder.menu.select", "select"));
			addActionListener(this);
			setState(DiagramField.this.isSelected());
		}

		public void actionPerformed(ActionEvent ae)
		{
			DiagramField.this.setSelected(this.getState());
		}
	}

	private class ActionAddExpression extends AbstractAction
	{
		private ActionAddExpression()
		{
			super(I18n.getString("querybuilder.menu.addExpression", "add expression..."));
		}

		public void actionPerformed(ActionEvent e)
		{
			// RamaSQL: GROUP_CONCAT nativo di MySQL/MariaDB (in origine: pseudo-funzione SQLeoGroupConcat, tradotta dall'esecutore di SQLeo)
			final String sqleoGroupConcat = "group_concat";
			final List<String> functions = new ArrayList<String>(Arrays.asList(SQLHelper.SQL_AGGREGATES));
			functions.add(sqleoGroupConcat);
			final JComboBox combo = new JComboBox(functions.toArray());
			final BorderLayoutPanel panel = new BorderLayoutPanel();
			panel.setComponentNorth(combo);
			panel.setPreferredSize(QbRuntime.scaledDimension(200, 40));
			int value = JOptionPane.showOptionDialog(DiagramField.this.getOwner().builder,
					panel,
					I18n.getString("querybuilder.message.chooseFunction", "choose function:"),
                    JOptionPane.OK_CANCEL_OPTION, 
                    JOptionPane.PLAIN_MESSAGE,
                    null, null, null);
			if(JOptionPane.OK_OPTION != value)
				return;
			Object choose = combo.getSelectedItem();
			if (choose != null)
			{
				String expr = null;
				if(choose.toString().equals(sqleoGroupConcat)){
					Object sqleoGroupConcatSeparator =
						JOptionPane.showInputDialog(
							DiagramField.this.getOwner().builder,
							I18n.getString("querybuilder.message.sqleoGroupConcat.chooseSeparator", "choose separator:"),
							I18n.getString("querybuilder.menu.add", "add..."),
							JOptionPane.PLAIN_MESSAGE,
							null,
							null,
							null);
					if(null == sqleoGroupConcatSeparator){
						return;
					}
					if(!sqleoGroupConcatSeparator.toString().startsWith("'")){
						sqleoGroupConcatSeparator = "'"+sqleoGroupConcatSeparator+"'";
					}
					expr = sqleoGroupConcat + "(" + DiagramField.this.querytoken.getIdentifier()
							+ " SEPARATOR " + sqleoGroupConcatSeparator + ")";
				}else{
					expr = choose.toString() + "(" + DiagramField.this.querytoken.getIdentifier() + ")";
				}
				QueryTokens.DefaultExpression token = new QueryTokens.DefaultExpression(expr);
				
				BrowserItems.AbstractQueryTreeItem qti = getOwner().builder.browser.getQueryItem();
				if(qti instanceof BrowserItems.DiagramQueryTreeItem)
				{
					token.setAlias("EXPR_" + (++expr_counter));
					
					DiagramQuery entityUp = ((BrowserItems.DiagramQueryTreeItem)qti).getDiagramObject();
					if(entityUp!=null){
						entityUp.addField(token.getAlias());
						entityUp.pack();
					}
				}
				
				getOwner().builder.browser.addSelectList(token);
			}
		}
	}

	private abstract class ActionAddCondition extends AbstractAction
	{
		abstract void add(QueryTokens.Condition token);
		abstract boolean isFirst();

		public void actionPerformed(ActionEvent e)
		{
			QueryTokens.Condition token = new QueryTokens.Condition();
			token.setLeft(new QueryTokens.DefaultExpression(DiagramField.this.querytoken.getIdentifier()));

			if (!isFirst())
				token.setAppend(_ReservedWords.AND);
			if (new MaskCondition(token, DiagramField.this.owner.builder).showDialog())
				add(token);
		}
	}

	private class ActionAddWhere extends ActionAddCondition
	{
		private ActionAddWhere()
		{
			putValue(NAME, I18n.getString("querybuilder.menu.addWhereCondition", "add where condition..."));
		}

		void add(QueryTokens.Condition token)
		{
			getOwner().builder.browser.addWhereClause(token);
			setWhereIcon();

		}

		boolean isFirst()
		{
			return getOwner().builder.browser.getQuerySpecification().getWhereClause().length == 0;
		}
	}
	
	public void setWhereIcon(){
		inWhereClause = true;
		labelComponent.setIcon(QbRuntime.host().icon(primaryKey ? QbIcon.QB_KEYANDWHERE : QbIcon.QB_WHERE));
		repack(); // RamaSQL (2026-09-27, BUG-011)
	}
	public void resetWhereIcon(){
		inWhereClause = false;
		labelComponent.setIcon(primaryKey ? keyIcon : null);
		repack(); // RamaSQL (2026-09-27, BUG-011)
	}

	// RamaSQL (2026-09-27, BUG-011): l'icona del filtro occupa spazio; se l'entita' non si allarga il nome del campo
	// viene troncato («data_restit…»). Si allarga solo se serve: la larghezza scelta dall'utente non si stringe.
	private void repack(){
		DiagramAbstractEntity entity = getOwner();
		if(entity == null) return;
		java.awt.Dimension preferred = entity.getPreferredSize();
		if(entity.getWidth() > 0 && preferred.width > entity.getWidth()){
			entity.setSize(preferred.width, Math.max(entity.getHeight(), preferred.height));
			entity.validate();
			if(entity.builder != null && entity.builder.diagram != null) entity.builder.diagram.doResize();
		}
	}
	public boolean isInWhereClause(){
		return inWhereClause;
	}

	private class ActionAddHaving extends ActionAddCondition
	{
		private ActionAddHaving()
		{
			putValue(NAME, I18n.getString("querybuilder.menu.addHavingCondition", "add having condition..."));
		}

		void add(QueryTokens.Condition token)
		{
			getOwner().builder.browser.addHavingClause(token);
		}

		boolean isFirst()
		{
			return getOwner().builder.browser.getQuerySpecification().getHavingClause().length == 0;
		}
	}

	public JCheckBox getCheckboxComponent()
	{
		return checkboxComponent;
	}

	public void setCheckboxComponent(JCheckBox checkboxComponent)
	{
		this.checkboxComponent = checkboxComponent;
	}

	public JLabel getLabelComponent()
	{
		return labelComponent;
	}

	public void setLabelComponent(JLabel labelComponent)
	{
		this.labelComponent = labelComponent;
	}

	public boolean isSelected()
	{
		return getCheckboxComponent().isSelected();
	}

	public void setSelected(boolean b)
	{
		getCheckboxComponent().setSelected(b);
	}

	public DiagramAbstractEntity getOwner()
	{
		return owner;
	}

	public void setOwner(DiagramAbstractEntity owner)
	{
		this.owner = owner;
	}
}
