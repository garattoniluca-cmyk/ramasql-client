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
 * Modificato per RamaSQL Client (2026-09-21): rimossa l'azione save to definition file (definizione manuale dei metadati); l'opzione archi/linee si chiede alla facciata (QbOption.RELATION_ARCS); linee dei join in grigio medio (in origine grigio chiaro, poco leggibile su bianco) e ancore scalate; la posizione dei campi si calcola con SwingUtilities.convertPoint invece di getLocationOnScreen, che falliva (eccezione ignorata, join non disegnato) quando il diagramma non era ancora a schermo.
 * Modificato per RamaSQL Client (2026-09-27): il cambio del tipo di join avvisa il QueryBuilder (fireQueryChanged,
 * BUG-006); colori e spessore delle linee dalla facciata (QbHost.color, scala) invece che fissi (BUG-004).
 * Modificato per RamaSQL Client (2026-09-28): pathBoxes(), i rettangoli che contengono i tratti della linea, per
 * cercare un posto a una tabella nuova senza join che passino sotto altre tabelle (BUG-023).
 * Modificato per RamaSQL Client (2026-09-28, T12.3/T12.9): suggerimenti delle voci del menu del join.
 */

package com.sqleo.querybuilder;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;

import com.sqleo.common.util.I18n;
import it.ramasql.qb.QbOption;
import it.ramasql.qb.QbRuntime;
import com.sqleo.querybuilder.syntax.QueryTokens;
import com.sqleo.querybuilder.syntax.QueryTokens.Column;


public class DiagramRelation extends JPanel
{
	// RamaSQL (2026-09-27, BUG-004): colori e tratti dalla facciata e scalati (in origine: nero, grigio chiaro, verde, 2 px fissi)
	private static Color color(it.ramasql.qb.QbColor c)
	{
		return QbRuntime.host().color(c);
	}

	/** Colore di mezza linea: il lato di cui si tengono tutte le righe (join esterno) ha il colore dei join esterni. */
	private Color sideColor(boolean allRows)
	{
		if(allRows) return color(it.ramasql.qb.QbColor.JOIN_ALL_ROWS);
		return color(isHighlight() ? it.ramasql.qb.QbColor.LINE_HIGHLIGHT : it.ramasql.qb.QbColor.LINE);
	}

	private static Stroke highlightStroke = new BasicStroke((float) (2f));
	private static Stroke normalStroke = new BasicStroke((float) (2f));

	private Stroke stroke()
	{
		float scale = QbRuntime.scale(100) / 100f;
		return new BasicStroke((isHighlight() ? 2.25f : 1.5f) * scale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	}

	QueryTokens.Join querytoken;

	DiagramAbstractEntity primaryEntity;
	DiagramField primaryField;
	DiagramAbstractEntity foreignEntity;
	DiagramField foreignField;

	Anchor anchor;
	ViewDiagram owner;

	private boolean highlight = false;

	DiagramRelation(ViewDiagram owner)
	{
		this.owner = owner;
		setLayout(null);
		setOpaque(false);

		anchor = new Anchor();
	}

	public void setName(String name)
	{
		super.setName(name);
		onPropertyChanged();
	}

	public boolean isHighlight()
	{
		return highlight;
	}
	
	public void setHighlight(boolean b)
	{
		// A black border is better visible.
		// A selected relation is not black drawn with a 2 points stroke
		// setForeground(b ? Color.lightGray : Color.black);
		// anchor.setBorder(new LineBorder(getForeground(),1));

		this.highlight = b;
		this.doResize();
		this.repaint();
	}

	QueryTokens.Join getQueryToken()
	{
		return querytoken;
	}
	
	void setQueryToken(QueryTokens.Join token)
	{
		querytoken = token;
		onPropertyChanged();
	}

	void setValues(int jointype, String operator)
	{
		querytoken.setType(jointype);
		querytoken.getCondition().setOperator(operator);
		onPropertyChanged();
		if(owner!=null && owner.getBuilder()!=null) owner.getBuilder().fireQueryChanged(); // RamaSQL (2026-09-27)
	}

	private void onPropertyChanged()
	{
		String tip = querytoken.getCondition().toString();
		if (this.getName() != null)
			tip = "[ " + this.getName() + " ] " + tip;

		anchor.setToolTipText(tip);

		// RamaSQL (2026-09-27, BUG-004): nodo d'accento per il join interno, ambra per quelli esterni (in origine rosso/giallo/verde)
		switch (querytoken.getType())
		{
		case QueryTokens.Join.LEFT_OUTER:
		case QueryTokens.Join.RIGHT_OUTER:
		case QueryTokens.Join.FULL_OUTER:
			anchor.setBackground(color(it.ramasql.qb.QbColor.JOIN_OUTER));
			break;
		default:
			anchor.setBackground(color(it.ramasql.qb.QbColor.JOIN_INNER));
		}
		anchor.setToolTipText(joinDescription() + " — " + tip);
		
		this.doResize();
		this.repaint();
	}

	void onCreate(QueryBuilder builder,QueryTokens.Join join)
	{
		//setQueryToken(new QueryTokens.Join(primaryField.querytoken, "=", foreignField.querytoken));
		setQueryToken(join);
		builder.browser.addFromClause(querytoken);
	}

	void onDestroy(QueryBuilder builder)
	{
		primaryField.unjoined();
		foreignField.unjoined();

		primaryEntity.doFlush();
		foreignEntity.doFlush();

		builder.browser.removeFromClause(querytoken);
	}
	
	
	void doResize()
	{
		if(isArcRendering()){
			doResizeArc();
		}else {
			doResizeLinear();
		}
	}
	
	private boolean isArcRendering(){
		return QbRuntime.host().option(QbOption.RELATION_ARCS);
	}
	
	/**
	 * array of points to draw the connection line. It is updated by the method
	 * doResize()
	 * 
	 */
	private Point[] serie = isArcRendering() ? 
			new Point[] { new Point(0, 0), new Point(0, 0), new Point(0, 0) } : 
			new Point[4]	;
	private Color plusColor;
	private Color minusColor;

	/**
	 * Updates the serie array accordely to the fields positions.
	 * 
	 */
	private void doResizeArc()
	{
		try
		{
			int yFieldP = javax.swing.SwingUtilities.convertPoint(primaryField, 0, 0, primaryEntity).y + primaryEntity.getLocation().y;
			int yFieldF = javax.swing.SwingUtilities.convertPoint(foreignField, 0, 0, foreignEntity).y + foreignEntity.getLocation().y;

			int py = yFieldP + (primaryField.getSize().height / 2);
			int fy = yFieldF + (foreignField.getSize().height / 2);
	
			// 1. Check for space between the two fields...
			//        ____ ____
			//        | P |__ ____ ____ __| P |
			//        |____| \__ | F | | F |__/ |____|
			//             |____| |____|
			// px1 px2 fx1 fx2 fx1 fx2 px1 px2
	
			int px1 = primaryEntity.getLocation().x;
			int px2 = px1 + primaryEntity.getSize().width;
	
			int fx1 = foreignEntity.getLocation().x;
			int fx2 = fx1 + foreignEntity.getSize().width;
	
			int xMin = 0, yMin = 0, xMax = 0, yMax = 0;
	
			if (px2 < fx1)
			{
				plusColor = sideColor(allRowsOfPrimaryEntity());
				minusColor = sideColor(allRowsOfForeignEntity());

				serie[0].x = px2;
				serie[0].y = py;
				serie[2].x = fx1;
				serie[2].y = fy;
				serie[1].x = (px2 + fx1) / 2;
				serie[1].y = (py + fy) / 2;
	
				xMin = px1;
				xMax = fx2;
			}
			else if (px1 > fx2)
			{
				plusColor = sideColor(allRowsOfForeignEntity());
				minusColor = sideColor(allRowsOfPrimaryEntity());

				serie[0].x = fx2;
				serie[0].y = fy;
				serie[2].x = px1;
				serie[2].y = py;
				serie[1].x = (fx2 + px1) / 2;
				serie[1].y = (py + fy) / 2;
	
				xMin = fx1;
				xMax = px2;
			}
			else
			{
				plusColor = sideColor(allRowsOfPrimaryEntity());
				minusColor = sideColor(allRowsOfForeignEntity());

				serie[0].x = px2;
				serie[0].y = py;
				serie[2].x = fx2;
				serie[2].y = fy;
				serie[1].x = Math.max(px2, fx2) + 30;
				serie[1].y = (py + fy) / 2;
	
				xMin = Math.min(px2, fx2);
				xMax = serie[1].x;
			}
	
			yMin = Math.min(py, fy);
			yMax = Math.max(py, fy);
	
			Rectangle area = new Rectangle(xMin, yMin, xMax - xMin + 1, yMax - yMin + 1);
	
			for (int i = 0; i < serie.length; ++i)
			{
				serie[i].x -= area.x;
				serie[i].y -= area.y;
			}
			setBounds(area);
			anchor.setLocation(serie[1].x + area.x - (anchor.getSize().width / 2), serie[1].y + area.y - (anchor.getSize().height / 2));
        }
        catch(Exception e)
        {
        	// BUG: 1929659
        }
	}
	
	private void doResizeLinear(){
	try{	
 		int yFieldP = javax.swing.SwingUtilities.convertPoint(primaryField, 0, 0, primaryEntity).y + primaryEntity.getLocation().y;
 		int yFieldF = javax.swing.SwingUtilities.convertPoint(foreignField, 0, 0, foreignEntity).y + foreignEntity.getLocation().y;
 
		int yStart = yFieldP + (primaryField.getSize().height/2);
		int yEnd = yFieldF + (foreignField.getSize().height/2);

		int xMin = primaryEntity.getLocation().x;
		int xEnd = foreignEntity.getLocation().x;
		int xMax = foreignEntity.getLocation().x + foreignEntity.getSize().width;
		int xStart = primaryEntity.getLocation().x + primaryEntity.getSize().width;

		if(xStart < xEnd){
			plusColor = sideColor(allRowsOfPrimaryEntity());
			minusColor = sideColor(allRowsOfForeignEntity());
		}else if (xStart > xMax){
			plusColor = sideColor(allRowsOfForeignEntity());
			minusColor = sideColor(allRowsOfPrimaryEntity());
		}else {
			plusColor = sideColor(allRowsOfPrimaryEntity());
			minusColor = sideColor(allRowsOfForeignEntity());
		}

		if(xEnd < xMin)
		{
			int x = xEnd;
			xEnd = xMin;
			xMin = x;
			
			yStart = yFieldF + (foreignField.getSize().height/2);
			yEnd = yFieldP + (primaryField.getSize().height/2);
		}
	
		if(xStart > xMax)
		{
			int x = xStart;
			xStart = xMax;
			xMax = x;
		}

		int yMin = primaryEntity.getLocation().y;
		int yMinF = foreignEntity.getLocation().y;
		if(yMinF < yMin) { 
			yMin = yMinF;
		}
		
		int yMax = primaryEntity.getLocation().y + primaryEntity.getSize().height;
		int yMaxF = foreignEntity.getLocation().y + foreignEntity.getSize().height;
		if(yMaxF > yMax) {
			yMax = yMaxF;
		}
		
		Rectangle area = new Rectangle(xMin, yMin, xMax-xMin, yMax-yMin);

		yStart-= area.y;
		yEnd-= area.y;
		
		int y = yStart > yEnd ? yEnd + ((yStart-yEnd)/2) : yStart + ((yEnd-yStart)/2);
		int x = xEnd - xStart;
		if( x > (anchor.getSize().width*2))
			x = xStart - area.x + (x/2);
		else
			x = (area.width=area.width+30)- 15;

		serie[0] = new Point(xStart-area.x,yStart);
		serie[1] = new Point(x,yStart);
		serie[2] = new Point(x,yEnd);
		serie[3] = new Point(xEnd-area.x,yEnd);
		
 		setBounds(area);
 		anchor.setLocation(x + area.x - (anchor.getSize().width/2), y + area.y - (anchor.getSize().height/2));
 		
	   }catch(Exception e)
	   { 
	    // BUG: 1929659
	   }

	}
	
	/**
	 * RamaSQL (2026-09-28, BUG-023): rettangoli, nelle coordinate del diagramma, che contengono i tratti della linea
	 * disegnata da paintArc o paintLinear (un quarto d'ellisse sta nel rettangolo dei suoi due estremi, un segmento
	 * anche), allargati di mezzo tratto. Approssimazione per eccesso: se un'entita' non tocca nessun rettangolo, la
	 * linea non le passa sotto.
	 */
	java.util.List<Rectangle> pathBoxes()
	{
		java.util.List<Rectangle> boxes = new java.util.ArrayList<Rectangle>();
		int pad = Math.max(1, QbRuntime.scale(2));
		for(int i=0; i+1<serie.length; i++)
		{
			Point a = serie[i];
			Point b = serie[i+1];
			if(a==null || b==null) continue;
			Rectangle r = new Rectangle(Math.min(a.x,b.x) + getX(), Math.min(a.y,b.y) + getY(),
					Math.abs(a.x-b.x), Math.abs(a.y-b.y));
			r.grow(pad, pad);
			boxes.add(r);
		}
		return boxes;
	}

	/** RamaSQL (2026-09-28, BUG-023): il riquadro del nodo del join, nelle coordinate del diagramma. */
	Rectangle anchorBounds()
	{
		return anchor.getBounds();
	}

	protected void paintChildren(Graphics g)
	{
		if(isArcRendering()){
			paintArc(g);
		}else {
			paintLinear(g);
		}

	}
	
	protected void paintArc(Graphics g)
	{
		((Graphics2D) g).setStroke(isHighlight() ? highlightStroke : normalStroke);

		int arc_w = serie[2].x - serie[0].x;
		int arc_h = serie[2].y - serie[0].y;

		((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// -\_ and _/-
		if (serie[0].x > serie[1].x || serie[2].x > serie[1].x)
		{
			if (arc_h == 0)
			{
				int midx = (serie[0].x+serie[2].x)/2;
				int midy = (serie[0].y+serie[2].y)/2;
				g.setColor(plusColor);
				g.drawLine(serie[0].x, serie[0].y, midx, midy) ;
				
				g.setColor(minusColor);
				g.drawLine(midx, midy, serie[2].x, serie[2].y);
			}
			else if (arc_h < 0) // __/--
			{
				g.setColor(plusColor);
				g.drawArc(serie[0].x - arc_w / 2, serie[0].y + arc_h, arc_w, -arc_h, 270, 90);
				
				g.setColor(minusColor);
				g.drawArc(serie[0].x + arc_w / 2, serie[0].y + arc_h, arc_w, -arc_h, 180, -90);
			}
			else if (arc_h > 0) // --\_
			{
				g.setColor(plusColor);
				g.drawArc(serie[0].x - arc_w / 2, serie[0].y, arc_w, arc_h, 0, 90);
				
				g.setColor(minusColor);
				g.drawArc(serie[0].x + arc_w / 2, serie[0].y, arc_w, arc_h, 180, 90);
			}
		}
		else
		{

			if (arc_h == 0)
			{
				int midx = (serie[0].x+serie[2].x)/2;
				int midy = (serie[0].y+serie[2].y)/2;
				g.setColor(plusColor);
				g.drawLine(serie[0].x, serie[0].y, midx, midy) ;
				
				g.setColor(minusColor);
				g.drawLine(midx, midy, serie[2].x, serie[2].y);
				
			}
			else if (arc_h < 0) // ]
			{
				arc_w = serie[1].x - serie[0].x;
				arc_h = serie[0].y - serie[1].y;

				g.setColor(plusColor);
				g.drawArc(serie[0].x - arc_w, serie[1].y - arc_h, arc_w * 2, arc_h * 2, 270, 90);

				arc_w = serie[1].x - serie[2].x;
				arc_h = serie[1].y - serie[2].y;

				g.setColor(minusColor);
				g.drawArc(serie[2].x - arc_w, serie[2].y, arc_w * 2, arc_h * 2, 90, -90);

			}
			else if (arc_h > 0) // ]
			{
				arc_w = serie[1].x - serie[0].x;
				arc_h = serie[1].y - serie[0].y;
				
				g.setColor(plusColor);
				g.drawArc(serie[0].x - arc_w, serie[0].y, arc_w * 2, arc_h * 2, 90, -90);

				arc_w = serie[1].x - serie[2].x;
				arc_h = serie[2].y - serie[1].y;

				g.setColor(minusColor);
				g.drawArc(serie[2].x - arc_w, serie[1].y - arc_h, arc_w * 2, arc_h * 2, 270, 90);

			}
		}

//		 g.drawLine(serie[0].x,serie[0].y,serie[1].x,serie[1].y);
//		 g.drawLine(serie[1].x,serie[1].y,serie[2].x,serie[2].y);
//		 g.drawLine(serie[2].x,serie[2].y,serie[3].x,serie[3].y);

		super.paintChildren(g);
	}
	
	protected void paintLinear(Graphics g)
	{
		((Graphics2D) g).setStroke(stroke());
		((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		 
		 g.setColor(plusColor);
		 g.drawLine(serie[0].x,serie[0].y,serie[1].x,serie[1].y);
		 g.drawLine(serie[1].x,serie[1].y,(serie[1].x+serie[2].x)/2,(serie[1].y+serie[2].y)/2);

		 g.setColor(minusColor);
		 g.drawLine((serie[1].x+serie[2].x)/2,(serie[1].y+serie[2].y)/2,serie[2].x,serie[2].y);
		 g.drawLine(serie[2].x,serie[2].y,serie[3].x,serie[3].y);

		super.paintChildren(g);
	}

	/** RamaSQL (2026-09-27): che righe tiene il join, in parole semplici (suggerimento del nodo e menu). */
	String joinDescription()
	{
		if(isLeft())
			return I18n.getFormattedString("querybuilder.join.allRowsOf","Tutte le righe di {0}", new Object[]{primaryEntityName()});
		if(isRight())
			return I18n.getFormattedString("querybuilder.join.allRowsOf","Tutte le righe di {0}", new Object[]{foreignEntityName()});
		return I18n.getString("querybuilder.join.matchingOnly","Solo le righe che corrispondono");
	}

	/*
	 * RamaSQL (2026-09-27): i nomi e il verso si leggono dal TOKEN del join, cioe' da cio' che finisce nell'SQL. Scrivendo il
	 * FROM, il formatter puo' girare il token (primaria <-> esterna, LEFT <-> RIGHT) se la sua tabella primaria non e' ancora
	 * dichiarata; l'entita' «primaria» del diagramma invece resta quella di quando il join e' nato. Leggere il verso dalle
	 * entita' faceva dire al menu «tutte le righe di X» mentre l'SQL teneva tutte le righe dell'altra tabella.
	 */
	String primaryEntityName()
	{
		return displayName(querytoken == null ? null : querytoken.getPrimary().getTable());
	}

	String foreignEntityName()
	{
		return displayName(querytoken == null ? null : querytoken.getForeign().getTable());
	}

	private static String displayName(QueryTokens.Table t)
	{
		if(t == null) return "";
		String n = t.getAlias() != null ? t.getAlias() : t.getName();
		return com.sqleo.querybuilder.syntax.SQLFormatter.stripQuote(n);
	}

	/** Il token ha ancora come primaria l'entita' primaria del diagramma (non e' stato girato dal formatter). */
	private boolean tokenFollowsDiagram()
	{
		if(querytoken == null || primaryEntity == null) return true;
		return displayName(querytoken.getPrimary().getTable()).equalsIgnoreCase(displayName(primaryEntity.getQueryToken()));
	}

	/** Si tengono tutte le righe dell'entita' primaria del diagramma (per il colore della mezza linea dal suo lato). */
	private boolean allRowsOfPrimaryEntity()
	{
		return isFull() || (tokenFollowsDiagram() ? isLeft() : isRight());
	}

	private boolean allRowsOfForeignEntity()
	{
		return isFull() || (tokenFollowsDiagram() ? isRight() : isLeft());
	}

	private boolean isLeft(){
		return QueryTokens.Join.LEFT_OUTER == querytoken.getType();
	}
	
	private boolean isRight(){
		return QueryTokens.Join.RIGHT_OUTER == querytoken.getType();
	}
	
	private boolean isFull(){
		return QueryTokens.Join.FULL_OUTER == querytoken.getType();
	}	
	
	

	private class Anchor extends JPanel implements MouseListener
	{
		Anchor()
		{
			addMouseListener(this);
			// RamaSQL (2026-09-27, BUG-004): pallino pieno con anello bianco, disegnato (in origine quadratino con bordo nero)
			setOpaque(false);
			setName("qb.join.anchor");
			setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			setSize(it.ramasql.qb.QbRuntime.scale(14), it.ramasql.qb.QbRuntime.scale(14)); // RamaSQL: in origine 10x10 px fissi
		}

		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				float ring = QbRuntime.scale(100) / 100f * 2f;
				g2.setColor(color(it.ramasql.qb.QbColor.CANVAS));
				g2.fill(new java.awt.geom.Ellipse2D.Float(0, 0, getWidth(), getHeight()));
				g2.setColor(getBackground());
				g2.fill(new java.awt.geom.Ellipse2D.Float(ring, ring, getWidth() - 2 * ring, getHeight() - 2 * ring));
			}
			finally
			{
				g2.dispose();
			}
		}

		public void mouseClicked(MouseEvent me)
		{
			// RamaSQL (2026-09-27): un clic apre il menu del join (in origine: tasto destro, o doppio clic per la maschera)
			DiagramRelation.this.owner.setHighlight(DiagramRelation.this);
			joinMenu().show(this, me.getX(), me.getY());
		}

		public void mouseEntered(MouseEvent e)
		{
		}

		public void mouseExited(MouseEvent e)
		{
		}

		public void mousePressed(MouseEvent e)
		{
		}

		public void mouseReleased(MouseEvent e)
		{
		}
	}
	/**
	 * RamaSQL (2026-09-27): il menu del join, in parole semplici — quali righe si tengono (le voci dicono i nomi delle
	 * tabelle), la condizione, «Togli il join». Niente FULL OUTER JOIN: MySQL e MariaDB non lo conoscono.
	 */
	JPopupMenu joinMenu()
	{
		JPopupMenu popup = new JPopupMenu();
		javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
		int[] types = {QueryTokens.Join.INNER, QueryTokens.Join.LEFT_OUTER, QueryTokens.Join.RIGHT_OUTER};
		String[] labels = {
				I18n.getString("querybuilder.join.matchingOnly","Solo le righe che corrispondono"),
				I18n.getFormattedString("querybuilder.join.allRowsOf","Tutte le righe di {0}", new Object[]{primaryEntityName()}),
				I18n.getFormattedString("querybuilder.join.allRowsOf","Tutte le righe di {0}", new Object[]{foreignEntityName()})};
		String[] tips = {"querybuilder.join.matchingOnly.tooltip", "querybuilder.join.allRowsOf.tooltip",
				"querybuilder.join.allRowsOf.tooltip"};
		for(int i=0; i<types.length; i++)
		{
			final int type = types[i];
			javax.swing.JRadioButtonMenuItem item = new javax.swing.JRadioButtonMenuItem(labels[i], querytoken.getType()==type);
			item.setName("qb.join.kind." + i);
			item.setToolTipText(I18n.getString(tips[i], ""));
			item.addActionListener(e -> setValues(type, querytoken.getCondition().getOperator()));
			group.add(item);
			popup.add(item);
		}
		popup.addSeparator();
		popup.add(new ActionEdit());
		popup.add(new ActionRemove());
		it.ramasql.qb.QbTips.explain(popup);   // RamaSQL (2026-09-28, T12.9)
		return popup;
	}

	private class ActionEdit extends AbstractAction
	{
		ActionEdit()
		{
			super(I18n.getString("querybuilder.join.condition", "edit...")); // RamaSQL (2026-09-27): «Condizione…»
		}

		public void actionPerformed(ActionEvent e)
		{
			new MaskJoin(DiagramRelation.this,DiagramRelation.this.owner.getBuilder()).showDialog();
		}
	}

	private class ActionRemove extends AbstractAction
	{
		ActionRemove()
		{
			super(I18n.getString("querybuilder.menu.remove", "remove"));
		}

		public void actionPerformed(ActionEvent e)
		{
			DiagramRelation.this.owner.removeRelation(DiagramRelation.this);
		}
	}
}
