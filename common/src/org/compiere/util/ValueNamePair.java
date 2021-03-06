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
package org.compiere.util;


/**
 *	(String) Value Name Pair
 *
 *  @author     Jorg Janke
 *  @version    $Id: ValueNamePair.java,v 1.2 2006/07/30 00:52:23 jjanke Exp $
 */
public final class ValueNamePair extends NamePair
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * 	Use for GWT serialization
	 */
	public ValueNamePair()
	{
	}

	/**
	 *	Construct KeyValue Pair
	 *  @param value value
	 *  @param name string representation
	 */
	public ValueNamePair(String value, String name)
	{
		super(name);
		m_value = value;
		if (m_value == null)
			m_value = "";
	}   //  ValueNamePair

	/** The Value       */
	private String m_value = null;

	/**
	 *	Get Value
	 *  @return Value
	 */
	public String getValue()
	{
		return m_value;
	}	//	getValue

	/**
	 *	Get ID
	 *  @return Value
	 */
	@Override
	public String getID()
	{
		return m_value;
	}	//	getID

	/**
	 *	Equals
	 *  @param obj Object
	 *  @return true, if equal
	 */
	@Override
	public boolean equals(Object obj)
	{
		if (obj instanceof ValueNamePair)
		{
			ValueNamePair pp = (ValueNamePair)obj;
			if (pp.getName() != null && pp.getValue() != null &&
				pp.getName().equals(getName()) && pp.getValue().equals(m_value))
				return true;
			return false;
		}
		return false;
	}	//	equals

	/**
	 *  Return Hashcode of value
	 *  @return hascode
	 */
	@Override
	public int hashCode()
	{
		return m_value.hashCode();
	}   //  hashCode

}	//	KeyValuePair

