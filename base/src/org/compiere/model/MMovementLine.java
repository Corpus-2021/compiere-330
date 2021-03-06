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
package org.compiere.model;

import java.math.*;
import java.sql.*;

import org.compiere.api.*;
import org.compiere.common.constants.*;
import org.compiere.util.*;

/**
 *	Inventory Move Line Model
 *	
 *  @author Jorg Janke
 *  @version $Id: MMovementLine.java,v 1.3 2006/07/30 00:51:03 jjanke Exp $
 */
public class MMovementLine extends X_M_MovementLine
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * 	Standard Cosntructor
	 *	@param ctx context
	 *	@param M_MovementLine_ID id
	 *	@param trx transaction
	 */
	public MMovementLine (Ctx ctx, int M_MovementLine_ID, Trx trx)
	{
		super (ctx, M_MovementLine_ID, trx);
		if (M_MovementLine_ID == 0)
		{
		//	setM_LocatorTo_ID (0);	// @M_LocatorTo_ID@
		//	setM_Locator_ID (0);	// @M_Locator_ID@
		//	setM_MovementLine_ID (0);			
		//	setLine (0);	
		//	setM_Product_ID (0);
			setM_AttributeSetInstance_ID(0);	//	ID
			setMovementQty (Env.ZERO);	// 1
			setTargetQty (Env.ZERO);	// 0
			setScrappedQty(Env.ZERO);
			setConfirmedQty(Env.ZERO);
			setProcessed (false);
		}	
	}	//	MMovementLine

	/**
	 * 	Load Constructor
	 *	@param ctx context
	 *	@param rs result set
	 *	@param trx transaction
	 */
	public MMovementLine (Ctx ctx, ResultSet rs, Trx trx)
	{
		super(ctx, rs, trx);
	}	//	MMovementLine

	/**
	 * 	Parent constructor
	 *	@param parent parent
	 */
	public MMovementLine (MMovement parent)
	{
		this (parent.getCtx(), 0, parent.get_Trx());
		setClientOrg(parent);
		setM_Movement_ID(parent.getM_Movement_ID());
	}	//	MMovementLine
	
	/**
	 * 	Get AttributeSetInstance To
	 *	@return ASI
	 */
	@Override
	public int getM_AttributeSetInstanceTo_ID ()
	{
		int M_AttributeSetInstanceTo_ID = super.getM_AttributeSetInstanceTo_ID();
		if (M_AttributeSetInstanceTo_ID == 0)
			M_AttributeSetInstanceTo_ID = super.getM_AttributeSetInstance_ID();
		return M_AttributeSetInstanceTo_ID;
	}	//	getM_AttributeSetInstanceTo_ID
	
	/**
	 * 	Add to Description
	 *	@param description text
	 */
	public void addDescription (String description)
	{
		String desc = getDescription();
		if (desc == null)
			setDescription(description);
		else
			setDescription(desc + " | " + description);
	}	//	addDescription

	/**
	 * 	Get Product
	 *	@return product or null if not defined
	 */
	public MProduct getProduct()
	{
		if (getM_Product_ID() != 0)
			return MProduct.get(getCtx(), getM_Product_ID());
		return null;
	}	//	getProduct
	
	/**
	 * 	Set Product - Callout
	 *	@param oldM_Product_ID old value
	 *	@param newM_Product_ID new value
	 *	@param windowNo window
	 *	@throws Exception
	 */
	@UICallout public void setM_Product_ID (String oldM_Product_ID, 
			String newM_Product_ID, int windowNo) throws Exception
	{
		if (newM_Product_ID == null || newM_Product_ID.length() == 0)
			return;
		int M_Product_ID = Integer.parseInt(newM_Product_ID);
		if (M_Product_ID == 0)
			return;
		//
		super.setM_Product_ID(M_Product_ID);
		if (getCtx().getContextAsInt(EnvConstants.WINDOW_INFO, EnvConstants.TAB_INFO, "M_Product_ID") == M_Product_ID
			&& getCtx().getContextAsInt(EnvConstants.WINDOW_INFO, EnvConstants.TAB_INFO, "M_AttributeSetInstance_ID") != 0)
			setM_AttributeSetInstance_ID(getCtx().getContextAsInt(EnvConstants.WINDOW_INFO, EnvConstants.TAB_INFO, "M_AttributeSetInstance_ID"));
		else
			setM_AttributeSetInstance_ID(0);
	}	//	setM_Product_ID
	
	
	/**
	 * 	Set Movement Qty - enforce UOM 
	 *	@param MovementQty qty
	 */
	@Override
	public void setMovementQty (BigDecimal MovementQty)
	{
		if (MovementQty != null)
		{
			MProduct product = getProduct();
			if (product != null)
			{
				int precision = product.getUOMPrecision(); 
				MovementQty = MovementQty.setScale(precision, BigDecimal.ROUND_HALF_UP);
			}
		}
		super.setMovementQty(MovementQty);
	}	//	setMovementQty
	
	/** Parent							*/
	private MMovement m_parent = null;
	
	/**
	 * get Parent
	 * @return Parent Movement
	 */
	public MMovement getParent() 
	{
		if (m_parent == null)
			m_parent = new MMovement (getCtx(), getM_Movement_ID(), get_Trx());
		return m_parent;
	}	//	getParent

	
	/**
	 * 	Before Save
	 *	@param newRecord new
	 *	@return true
	 */
	@Override
	protected boolean beforeSave (boolean newRecord)
	{
		//	Set Line No
		if (getLine() == 0)
		{
			String sql = "SELECT COALESCE(MAX(Line),0)+10 AS DefaultValue FROM M_MovementLine WHERE M_Movement_ID=?";
			int ii = DB.getSQLValue (get_Trx(), sql, getM_Movement_ID());
			setLine (ii);
		}
		
		if (getM_Locator_ID() == getM_LocatorTo_ID())
		{
			log.saveError("Error", Msg.parseTranslation(getCtx(), "@M_Locator_ID@ == @M_LocatorTo_ID@"));
			return false;
		}

		if (getMovementQty().signum() == 0)
		{
			log.saveError("FillMandatory", Msg.getElement(getCtx(), "MovementQty"));
			return false;
		}

		//	Qty Precision
		if (newRecord || is_ValueChanged("QtyEntered"))
			setMovementQty(getMovementQty());

		//	Mandatory Instance
		if (getM_AttributeSetInstanceTo_ID() == 0)
		{
			if (getM_AttributeSetInstance_ID() != 0)	//	set to from
				setM_AttributeSetInstanceTo_ID(getM_AttributeSetInstance_ID());
			else
			{
				MProduct product = getProduct();
				if (product != null
					&& product.getM_AttributeSet_ID() != 0)
				{
					MAttributeSet mas = MAttributeSet.get(getCtx(), product.getM_AttributeSet_ID());
					if (mas.isInstanceAttribute() 
						&& (mas.isMandatory() || mas.isMandatoryAlways()))
					{
						log.saveError("FillMandatory", Msg.getElement(getCtx(), "M_AttributeSetInstanceTo_ID"));
						return false;
					}
				}
			}
		}	//	ASI
		
		return true;
	}	//	beforeSave

	
}	//	MMovementLine
