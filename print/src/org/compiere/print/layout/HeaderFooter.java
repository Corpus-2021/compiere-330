/******************************************************************************
 * Product: Compiere ERP & CRM Smart Business Solution                        *
 * Copyright (C) 1999-2007 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY, without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program, if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 3600 Bridge Parkway #102, Redwood City, CA 94065, USA      *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.compiere.print.layout;

import java.awt.*;
import java.util.*;

import org.compiere.framework.*;
import org.compiere.util.*;

/**
 *	Header Footer
 *
 * 	@author 	Jorg Janke
 * 	@version 	$Id: HeaderFooter.java,v 1.2 2006/07/30 00:53:02 jjanke Exp $
 */
public class HeaderFooter
{
	/**
	 *	Standard Constructor
	 *  @param ctx context
	 */
	public HeaderFooter (Ctx ctx)
	{
		m_ctx = ctx;
	}	//	HeaderFooter

	/**	Context						*/
	private Ctx		 		m_ctx;

	/**	Header/Footer content			*/
	private ArrayList<PrintElement>	m_elements = new ArrayList<PrintElement>();
	/** Header/Footer content as Array	*/
	private PrintElement[] 	m_pe = null;

	/**
	 * 	Add Print Element to Page
	 * 	@param element print element
	 */
	public void addElement (PrintElement element)
	{
		if (element != null)
			m_elements.add(element);
		m_pe = null;
	}	//	addElement

	/**
	 * 	Get Elements
	 *	@return array of elements
	 */
	public PrintElement[] getElements()
	{
		if (m_pe == null)
		{
			m_pe = new PrintElement[m_elements.size()];
			m_elements.toArray(m_pe);
		}
		return m_pe;
	}	//	getElements

	/**
	 * 	Paint Page Header/Footer on Graphics in Bounds
	 *
	 * 	@param g2D graphics
	 * 	@param bounds page bounds
	 *  @param isView true if online view (IDs are links)
	 */
	public void paint (Graphics2D g2D, Rectangle bounds, boolean isView)
	{
		Point pageStart = new Point(bounds.getLocation());
		getElements();
		for (PrintElement element : m_pe)
			element.paint(g2D, 0, pageStart, m_ctx, isView);
	}	//	paint

	/**
	 * 	Get DrillDown value
	 * 	@param relativePoint relative Point
	 * 	@return if found NamePait or null
	 */
	public Query getDrillDown (Point relativePoint)
	{
		Query retValue = null;
		for (int i = 0; i < m_elements.size() && retValue == null; i++)
		{
			PrintElement element = m_elements.get(i);
			retValue = element.getDrillDown (relativePoint, 1);
		}
		return retValue;
	}	//	getDrillDown

}	//	HeaderFooter
