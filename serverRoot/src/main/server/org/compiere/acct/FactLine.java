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
package org.compiere.acct;

import java.math.*;
import java.sql.*;
import java.util.logging.*;

import org.compiere.model.*;
import org.compiere.util.*;

/**
 *  Accounting Fact Entry.
 *
 *  @author 	Jorg Janke
 *  @version 	$Id: FactLine.java,v 1.3 2006/07/30 00:53:33 jjanke Exp $
 */
public final class FactLine extends X_Fact_Acct
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 *	Constructor
	 *	@param ctx context
	 *  @param AD_Table_ID  - Table of Document Source
	 *  @param Record_ID    - Record of document
	 *  @param Line_ID      - Optional line id
	 *  @param trx transaction
	 */
	public FactLine (Ctx ctx, int AD_Table_ID, int Record_ID, int Line_ID, Trx trx)
	{
		super(ctx, 0, trx);
		setAD_Client_ID(0);							//	do not derive
		setAD_Org_ID(0);							//	do not derive
		//
		setAmtAcctCr (Env.ZERO);
		setAmtAcctDr (Env.ZERO);
		setAmtSourceCr (Env.ZERO);
		setAmtSourceDr (Env.ZERO);
	//	Log.trace(this,Log.l1_User, "FactLine " + AD_Table_ID + ":" + Record_ID);
		setAD_Table_ID (AD_Table_ID);
		setRecord_ID (Record_ID);
		setLine_ID (Line_ID);
	}   //  FactLine

	/**	Account					*/
	private MAccount	m_acct = null;
	/** Accounting Schema		*/
	private MAcctSchema	m_acctSchema = null;
	/** Document Header			*/
	private Doc			m_doc = null;
	/** Document Line 			*/
	private DocLine 	m_docLine = null;
	
	/**
	 * 	Create Reversal (negate DR/CR) of the line
	 *	@param description new description
	 *	@return reversal line
	 */
	public FactLine reverse (String description)
	{
		FactLine reversal = new FactLine (getCtx(), getAD_Table_ID(), getRecord_ID(), getLine_ID(), get_Trx());
		reversal.setClientOrg(this);	//	needs to be set explicitly
		reversal.setDocumentInfo(m_doc, m_docLine);
		reversal.setAccount(m_acctSchema, m_acct);
		reversal.setPostingType(getPostingType());
		//
		reversal.setAmtSource(getC_Currency_ID(), getAmtSourceDr().negate(), getAmtSourceCr().negate());
		reversal.convert();
		reversal.setDescription(description);
		return reversal;
	}	//	reverse

	/**
	 * 	Create Accrual (flip CR/DR) of the line
	 *	@param description new description
	 *	@return accrual line
	 */
	public FactLine accrue (String description)
	{
		FactLine accrual = new FactLine (getCtx(), getAD_Table_ID(), getRecord_ID(), getLine_ID(), get_Trx());
		accrual.setClientOrg(this);	//	needs to be set explicitly
		accrual.setDocumentInfo(m_doc, m_docLine);
		accrual.setAccount(m_acctSchema, m_acct);
		accrual.setPostingType(getPostingType());
		//
		accrual.setAmtSource(getC_Currency_ID(), getAmtSourceCr(), getAmtSourceDr());
		accrual.convert();
		accrual.setDescription(description);
		return accrual;
	}	//	reverse

	/**
	 *  Set Account Info
	 *  @param acctSchema account schema
	 *  @param acct account
	 */
	public void setAccount (MAcctSchema acctSchema, MAccount acct)
	{
		m_acctSchema = acctSchema;
		setC_AcctSchema_ID (acctSchema.getC_AcctSchema_ID());
		//
		m_acct = acct;
		if (getAD_Client_ID() == 0)
			setAD_Client_ID(m_acct.getAD_Client_ID());
		if (acct.getAccount_ID() == 0)
			throw new IllegalArgumentException("Account_ID not defined: " + acct);
		setAccount_ID (m_acct.getAccount_ID());
		setC_SubAcct_ID(m_acct.getC_SubAcct_ID());

		//	User Defined References
		MAcctSchemaElement ud1 = m_acctSchema.getAcctSchemaElement(
				X_C_AcctSchema_Element.ELEMENTTYPE_UserElement1);
		if (ud1 != null)
		{
			String ColumnName1 = ud1.getDisplayColumnName();
			if (ColumnName1 != null)
			{
				int ID1 = 0;
				if (m_docLine != null)
					ID1 = m_docLine.getValue(ColumnName1);
				if (ID1 == 0)
				{
					if (m_doc == null)
						throw new IllegalArgumentException("Document not set yet");
					ID1 = m_doc.getValue(ColumnName1);
				}
				if (ID1 != 0)
					setUserElement1_ID(ID1);
			}
		}
		MAcctSchemaElement ud2 = m_acctSchema.getAcctSchemaElement(
				X_C_AcctSchema_Element.ELEMENTTYPE_UserElement2);
		if (ud2 != null)
		{
			String ColumnName2 = ud2.getDisplayColumnName();
			if (ColumnName2 != null)
			{
				int ID2 = 0;
				if (m_docLine != null)
					ID2 = m_docLine.getValue(ColumnName2);
				if (ID2 == 0)
				{
					if (m_doc == null)
						throw new IllegalArgumentException("Document not set yet");
					ID2 = m_doc.getValue(ColumnName2);
				}
				if (ID2 != 0)
					setUserElement2_ID(ID2);
			}
		}
	}   //  setAccount

	/**
	 *  Set Source Amounts
	 *  @param C_Currency_ID currency
	 *  @param AmtSourceDr source amount dr
	 *  @param AmtSourceCr source amount cr
	 *  @return true, if any if the amount is not zero
	 */
	public boolean setAmtSource (int C_Currency_ID, BigDecimal AmtSourceDr, BigDecimal AmtSourceCr)
	{
		setC_Currency_ID (C_Currency_ID);
		if (AmtSourceDr != null)
			setAmtSourceDr (AmtSourceDr);
		if (AmtSourceCr != null)
			setAmtSourceCr (AmtSourceCr);
		//  one needs to be non zero
		if (getAmtSourceDr().equals(Env.ZERO) && getAmtSourceCr().equals(Env.ZERO))
			return false;
		//	Currency Precision
		int precision = MCurrency.getStdPrecision(getCtx(), C_Currency_ID);
		if (AmtSourceDr != null && AmtSourceDr.scale() > precision)
		{
			BigDecimal AmtSourceDr1 = AmtSourceDr.setScale(precision, BigDecimal.ROUND_HALF_UP);
			log.warning("Source DR Precision " + AmtSourceDr + " -> " + AmtSourceDr1);
			setAmtSourceDr(AmtSourceDr1);
		}
		if (AmtSourceCr != null && AmtSourceCr.scale() > precision)
		{
			BigDecimal AmtSourceCr1 = AmtSourceCr.setScale(precision, BigDecimal.ROUND_HALF_UP);
			log.warning("Source CR Precision " + AmtSourceCr + " -> " + AmtSourceCr1);
			setAmtSourceCr(AmtSourceCr1);
		}
		return true;
	}   //  setAmtSource

	/**
	 *  Set Accounted Amounts (alternative: call convert)
	 *  @param AmtAcctDr acct amount dr
	 *  @param AmtAcctCr acct amount cr
	 */
	public void setAmtAcct(BigDecimal AmtAcctDr, BigDecimal AmtAcctCr)
	{
		setAmtAcctDr (AmtAcctDr);
		setAmtAcctCr (AmtAcctCr);
	}   //  setAmtAcct

	/**
	 *  Set Document Info
	 *  @param doc document
	 *  @param docLine doc line
	 */
	public void setDocumentInfo(Doc doc, DocLine docLine)
	{
		m_doc = doc;
		m_docLine = docLine;
		//	reset
		setAD_Org_ID(0);
		setC_SalesRegion_ID(0);
		//	Client
		if (getAD_Client_ID() == 0)
			setAD_Client_ID (m_doc.getAD_Client_ID());
		//	Date Trx
		setDateTrx (m_doc.getDateDoc());
		if (m_docLine != null && m_docLine.getDateDoc() != null)
			setDateTrx (m_docLine.getDateDoc());
		//	Date Acct
		setDateAcct (m_doc.getDateAcct());
		if (m_docLine != null && m_docLine.getDateAcct() != null)
			setDateAcct (m_docLine.getDateAcct());
		//	Period, Tax
		if (m_docLine != null &&  m_docLine.getC_Period_ID() != 0)
			setC_Period_ID(m_docLine.getC_Period_ID());
		else
			setC_Period_ID (m_doc.getC_Period_ID());
		if (m_docLine != null)
			setC_Tax_ID (m_docLine.getC_Tax_ID());
		//	Description
		StringBuffer description = new StringBuffer(m_doc.getDocumentNo());
		if (m_docLine != null)
		{
			description.append(" #").append(m_docLine.getLine());
			if (m_docLine.getDescription() != null)
				description.append(" (").append(m_docLine.getDescription()).append(")");
			else if (m_doc.getDescription() != null && m_doc.getDescription().length() > 0)
				description.append(" (").append(m_doc.getDescription()).append(")");		
		}
		else if (m_doc.getDescription() != null && m_doc.getDescription().length() > 0)
			description.append(" (").append(m_doc.getDescription()).append(")");
		setDescription(description.toString());
		//	Journal Info
		setGL_Budget_ID (m_doc.getGL_Budget_ID());
		setGL_Category_ID (m_doc.getGL_Category_ID());

		//	Product
		if (m_docLine != null)
			setM_Product_ID (m_docLine.getM_Product_ID());
		if (getM_Product_ID() == 0)
			setM_Product_ID (m_doc.getM_Product_ID());
		//	UOM
		if (m_docLine != null)
			setC_UOM_ID (m_docLine.getC_UOM_ID());
		//	Qty
		if (get_Value("Qty") == null)	// not previously set
		{
			setQty (m_doc.getQty());	//	neg = outgoing
			if (m_docLine != null)
				setQty (m_docLine.getQty());
		}
		
		//	Loc From (maybe set earlier)
		if (getC_LocFrom_ID() == 0 && m_docLine != null)
			setC_LocFrom_ID (m_docLine.getC_LocFrom_ID());
		if (getC_LocFrom_ID() == 0)
			setC_LocFrom_ID (m_doc.getC_LocFrom_ID());
		//	Loc To (maybe set earlier)
		if (getC_LocTo_ID() == 0 && m_docLine != null)
			setC_LocTo_ID (m_docLine.getC_LocTo_ID());
		if (getC_LocTo_ID() == 0)
			setC_LocTo_ID (m_doc.getC_LocTo_ID());
		//	BPartner
		if (m_docLine != null)
			setC_BPartner_ID (m_docLine.getC_BPartner_ID());
		if (getC_BPartner_ID() == 0)
			setC_BPartner_ID (m_doc.getC_BPartner_ID());
		//	Sales Region from BPLocation/Sales Rep
		//	Trx Org
		if (m_docLine != null)
			setAD_OrgTrx_ID (m_docLine.getAD_OrgTrx_ID());
		if (getAD_OrgTrx_ID() == 0)
			setAD_OrgTrx_ID (m_doc.getAD_OrgTrx_ID());
		//	Project
		if (m_docLine != null)
			setC_Project_ID (m_docLine.getC_Project_ID());
		if (getC_Project_ID() == 0)
			setC_Project_ID (m_doc.getC_Project_ID());
		//	Campaign
		if (m_docLine != null)
			setC_Campaign_ID (m_docLine.getC_Campaign_ID());
		if (getC_Campaign_ID() == 0)
			setC_Campaign_ID (m_doc.getC_Campaign_ID());
		//	Activity
		if (m_docLine != null)
			setC_Activity_ID (m_docLine.getC_Activity_ID());
		if (getC_Activity_ID() == 0)
			setC_Activity_ID (m_doc.getC_Activity_ID());
		//	User List 1
		if (m_docLine != null)
			setUser1_ID (m_docLine.getUser1_ID());
		if (getUser1_ID() == 0)
			setUser1_ID (m_doc.getUser1_ID());
		//	User List 2
		if (m_docLine != null)
			setUser2_ID (m_docLine.getUser2_ID());
		if (getUser2_ID() == 0)
			setUser2_ID (m_doc.getUser2_ID());
		//	References in setAccount
	}   //  setDocumentInfo

	/**
	 * 	Get Document Line
	 *	@return doc line
	 */
	protected DocLine getDocLine()
	{
		return m_docLine;
	}	//	getDocLine
	
	/**
	 * 	Set Description
	 *	@param description description
	 */
	public void addDescription (String description)
	{
		String original = getDescription();
		if (original == null || original.trim().length() == 0)
			super.setDescription(description);
		else
			super.setDescription(original + " - " + description);
	}	//	addDescription
	
	/**
	 *  Set Warehouse Locator.
	 *  - will overwrite Organization -
	 *  @param M_Locator_ID locator
	 */
	@Override
	public void setM_Locator_ID (int M_Locator_ID)
	{
		super.setM_Locator_ID (M_Locator_ID);
		setAD_Org_ID(0);	//	reset
	}   //  setM_Locator_ID

	
	/**************************************************************************
	 *  Set Location
	 *  @param C_Location_ID location
	 *  @param isFrom from
	 */
	public void setLocation (int C_Location_ID, boolean isFrom)
	{
		if (isFrom)
			setC_LocFrom_ID (C_Location_ID);
		else
			setC_LocTo_ID (C_Location_ID);
	}   //  setLocator

	/**
	 *  Set Location from Locator
	 *  @param M_Locator_ID locator
	 *  @param isFrom from
	 */
	public void setLocationFromLocator (int M_Locator_ID, boolean isFrom)
	{
		if (M_Locator_ID == 0)
			return;
		int C_Location_ID = 0;
		String sql = "SELECT w.C_Location_ID FROM M_Warehouse w, M_Locator l "
			+ "WHERE w.M_Warehouse_ID=l.M_Warehouse_ID AND l.M_Locator_ID=?";
		try
		{
			PreparedStatement pstmt = DB.prepareStatement(sql, get_Trx());
			pstmt.setInt(1, M_Locator_ID);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next())
				C_Location_ID = rs.getInt(1);
			rs.close();
			pstmt.close();
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql, e);
			return;
		}
		if (C_Location_ID != 0)
			setLocation (C_Location_ID, isFrom);
	}   //  setLocationFromLocator

	/**
	 *  Set Location from Busoness Partner Location
	 *  @param C_BPartner_Location_ID bp location
	 *  @param isFrom from
	 */
	public void setLocationFromBPartner (int C_BPartner_Location_ID, boolean isFrom)
	{
		if (C_BPartner_Location_ID == 0)
			return;
		int C_Location_ID = 0;
		String sql = "SELECT C_Location_ID FROM C_BPartner_Location WHERE C_BPartner_Location_ID=?";
		try
		{
			PreparedStatement pstmt = DB.prepareStatement(sql, get_Trx());
			pstmt.setInt(1, C_BPartner_Location_ID);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next())
				C_Location_ID = rs.getInt(1);
			rs.close();
			pstmt.close();
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql, e);
			return;
		}
		if (C_Location_ID != 0)
			setLocation (C_Location_ID, isFrom);
	}   //  setLocationFromBPartner

	/**
	 *  Set Location from Organization
	 *  @param AD_Org_ID org
	 *  @param isFrom from
	 */
	public void setLocationFromOrg (int AD_Org_ID, boolean isFrom)
	{
		if (AD_Org_ID == 0)
			return;
		int C_Location_ID = 0;
		String sql = "SELECT C_Location_ID FROM AD_OrgInfo WHERE AD_Org_ID=?";
		try
		{
			PreparedStatement pstmt = DB.prepareStatement(sql, get_Trx());
			pstmt.setInt(1, AD_Org_ID);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next())
				C_Location_ID = rs.getInt(1);
			rs.close();
			pstmt.close();
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql, e);
			return;
		}
		if (C_Location_ID != 0)
			setLocation (C_Location_ID, isFrom);
	}   //  setLocationFromOrg

	
	/**************************************************************************
	 *  Returns Source Balance of line
	 *  @return source balance
	 */
	public BigDecimal getSourceBalance()
	{
		if (getAmtSourceDr() == null)
			setAmtSourceDr (Env.ZERO);
		if (getAmtSourceCr() == null)
			setAmtSourceCr (Env.ZERO);
		//
		return getAmtSourceDr().subtract(getAmtSourceCr());
	}   //  getSourceBalance

	/**
	 *	Is Debit Source Balance
	 *  @return true if DR source balance
	 */
	public boolean isDrSourceBalance()
	{
		return getSourceBalance().signum() != -1;
	}   //  isDrSourceBalance

	/**
	 *  Get Accounted Balance
	 *  @return accounting balance
	 */
	public BigDecimal getAcctBalance()
	{
		if (getAmtAcctDr() == null)
			setAmtAcctDr (Env.ZERO);
		if (getAmtAcctCr() == null)
			setAmtAcctCr (Env.ZERO);
		return getAmtAcctDr().subtract(getAmtAcctCr());
	}   //  getAcctBalance

	/**
	 *	Is Account on Balance Sheet
	 *  @return true if account is a balance sheet account
	 */
	public boolean isBalanceSheet()
	{
		return m_acct.isBalanceSheet();
	}	//	isBalanceSheet

	/**
	 *	Currect Accounting Amount.
	 *  <pre>
	 *  Example:    1       -1      1       -1
	 *  Old         100/0   100/0   0/100   0/100
	 *  New         99/0    101/0   0/99    0/101
	 *  </pre>
	 *  @param deltaAmount delta amount
	 */
	public void currencyCorrect (BigDecimal deltaAmount)
	{
		boolean negative = deltaAmount.compareTo(Env.ZERO) < 0;
		boolean adjustDr = getAmtAcctDr().abs().compareTo(getAmtAcctCr().abs()) > 0;

		log.fine(deltaAmount.toString()
			+ "; Old-AcctDr=" + getAmtAcctDr() + ",AcctCr=" + getAmtAcctCr()
			+ "; Negative=" + negative + "; AdjustDr=" + adjustDr);

		if (adjustDr)
			if (negative)
				setAmtAcctDr (getAmtAcctDr().subtract(deltaAmount));
			else
				setAmtAcctDr (getAmtAcctDr().subtract(deltaAmount));
		else
			if (negative)
				setAmtAcctCr (getAmtAcctCr().add(deltaAmount));
			else
				setAmtAcctCr (getAmtAcctCr().add(deltaAmount));

		log.fine("New-AcctDr=" + getAmtAcctDr() + ",AcctCr=" + getAmtAcctCr());
	}	//	currencyCorrect

	/**
	 *  Convert to Accounted Currency
	 *  @return true if converted
	 */
	public boolean convert ()
	{
		//  Document has no currency
		if (getC_Currency_ID() == Doc.NO_CURRENCY)
			setC_Currency_ID (m_acctSchema.getC_Currency_ID());

		if (m_acctSchema.getC_Currency_ID() == getC_Currency_ID())
		{
			setAmtAcctDr (getAmtSourceDr());
			setAmtAcctCr (getAmtSourceCr());
			return true;
		}
		//	Get Conversion Type from Line or Header
		int C_ConversionType_ID = 0;
		int AD_Org_ID = 0;
		if (m_docLine != null)			//	get from line
		{
			C_ConversionType_ID = m_docLine.getC_ConversionType_ID();
			AD_Org_ID = m_docLine.getAD_Org_ID();
		}
		if (C_ConversionType_ID == 0)	//	get from header
		{
			if (m_doc == null)
			{
				log.severe ("No Document VO");
				return false;
			}
			C_ConversionType_ID = m_doc.getC_ConversionType_ID();
			if (AD_Org_ID == 0)
				AD_Org_ID = m_doc.getAD_Org_ID();
		}
		
		Timestamp convDate = getDateAcct();

		// For sourceforge bug 1718381: Use transaction date instead of
		// accounting date for currency conversion when the document is Bank
		// Statement. Ideally this should apply to all "reconciliation"
		// accounting entries, but doing just Bank Statement for now to avoid
		// breaking other things.
		if( m_doc instanceof Doc_Bank )
			convDate = getDateTrx();
		
		setAmtAcctDr (MConversionRate.convert (getCtx(),
			getAmtSourceDr(), getC_Currency_ID(), m_acctSchema.getC_Currency_ID(),
			convDate, C_ConversionType_ID, m_doc.getAD_Client_ID(), AD_Org_ID));
		if (getAmtAcctDr() == null)
			return false;
		setAmtAcctCr (MConversionRate.convert (getCtx(),
			getAmtSourceCr(), getC_Currency_ID(), m_acctSchema.getC_Currency_ID(),
			convDate, C_ConversionType_ID, m_doc.getAD_Client_ID(), AD_Org_ID));
		return true;
	}	//	convert

	/**
	 * 	Get Account
	 *	@return account
	 */
	public MAccount getAccount()
	{
		return m_acct;
	}	//	getAccount
	
	/**
	 *	To String
	 *  @return String
	 */
	@Override
	public String toString()
	{
		StringBuffer sb = new StringBuffer("FactLine=[");
		sb.append(getAD_Table_ID()).append(":").append(getRecord_ID())
			.append(",").append(m_acct)
			.append(",Cur=").append(getC_Currency_ID())
			.append(", DR=").append(getAmtSourceDr()).append("|").append(getAmtAcctDr())
			.append(", CR=").append(getAmtSourceCr()).append("|").append(getAmtAcctCr())
			.append("]");
		return sb.toString();
	}	//	toString

	
	/**
	 *  Get AD_Org_ID (balancing segment).
	 *  (if not set directly - from document line, document, account, locator)
	 *  <p>
	 *  Note that Locator needs to be set before - otherwise
	 *  segment balancing might produce the wrong results
	 *  @return AD_Org_ID
	 */
	@Override
	public int getAD_Org_ID()
	{
		if (super.getAD_Org_ID() != 0)      //  set earlier
			return super.getAD_Org_ID();
		//	Prio 1 - get from locator - if exist
		if (getM_Locator_ID() != 0)
		{
			String sql = "SELECT AD_Org_ID FROM M_Locator WHERE M_Locator_ID=? AND AD_Client_ID=?";
			try
			{
				PreparedStatement pstmt = DB.prepareStatement(sql, get_Trx());
				pstmt.setInt(1, getM_Locator_ID());
				pstmt.setInt(2, getAD_Client_ID());
				ResultSet rs = pstmt.executeQuery();
				if (rs.next())
				{
					setAD_Org_ID (rs.getInt(1));
					log.finer("AD_Org_ID=" + super.getAD_Org_ID() + " (1 from M_Locator_ID=" + getM_Locator_ID() + ")");
				}
				else
					log.log(Level.SEVERE, "AD_Org_ID - Did not find M_Locator_ID=" + getM_Locator_ID());
				rs.close();
				pstmt.close();
			}
			catch (SQLException e)
			{
				log.log(Level.SEVERE, sql, e);
			}
		}   //  M_Locator_ID != 0

		//	Prio 2 - get from doc line - if exists (document context overwrites)
		if (m_docLine != null && super.getAD_Org_ID() == 0)
		{
			setAD_Org_ID (m_docLine.getAD_Org_ID());
			log.finer("AD_Org_ID=" + super.getAD_Org_ID() + " (2 from DocumentLine)");
		}
		//	Prio 3 - get from doc - if not GL
		if (m_doc != null && super.getAD_Org_ID() == 0)
		{
			if (MDocBaseType.DOCBASETYPE_GLJournal.equals (m_doc.getDocumentType()))
			{
				setAD_Org_ID (m_acct.getAD_Org_ID()); //	inter-company GL
				log.finer("AD_Org_ID=" + super.getAD_Org_ID() + " (3 from Acct)");
			}
			else
			{
				setAD_Org_ID (m_doc.getAD_Org_ID());
				log.finer("AD_Org_ID=" + super.getAD_Org_ID() + " (3 from Document)");
			}
		}
		//	Prio 4 - get from account - if not GL
		if (m_doc != null && super.getAD_Org_ID() == 0)
		{
			if (MDocBaseType.DOCBASETYPE_GLJournal.equals (m_doc.getDocumentType()))
			{
				setAD_Org_ID (m_doc.getAD_Org_ID());
				log.finer("AD_Org_ID=" + super.getAD_Org_ID() + " (4 from Document)");
			}
			else
			{
				setAD_Org_ID (m_acct.getAD_Org_ID());
				log.finer("AD_Org_ID=" + super.getAD_Org_ID() + " (4 from Acct)");
			}
		}
		return super.getAD_Org_ID();
	}   //  setAD_Org_ID


	/**
	 *	Get/derive Sales Region
	 *	@return Sales Region
	 */
	@Override
	public int getC_SalesRegion_ID ()
	{
		if (super.getC_SalesRegion_ID() != 0)
			return super.getC_SalesRegion_ID();
		//
		if (m_docLine != null)
			setC_SalesRegion_ID (m_docLine.getC_SalesRegion_ID());
		if (m_doc != null)
		{
			if (super.getC_SalesRegion_ID() == 0)
				setC_SalesRegion_ID (m_doc.getC_SalesRegion_ID());
			if (super.getC_SalesRegion_ID() == 0 && m_doc.getBP_C_SalesRegion_ID() > 0)
				setC_SalesRegion_ID (m_doc.getBP_C_SalesRegion_ID());
			//	derive SalesRegion if AcctSegment
			if (super.getC_SalesRegion_ID() == 0
				&& m_doc.getC_BPartner_Location_ID() != 0
				&& m_doc.getBP_C_SalesRegion_ID() == -1)	//	never tried
			//	&& m_acctSchema.isAcctSchemaElement(MAcctSchemaElement.ELEMENTTYPE_SalesRegion))
			{
				String sql = "SELECT COALESCE(C_SalesRegion_ID,0) FROM C_BPartner_Location WHERE C_BPartner_Location_ID=?";
				setC_SalesRegion_ID (DB.getSQLValue(null,
					sql, m_doc.getC_BPartner_Location_ID()));
				if (super.getC_SalesRegion_ID() != 0)		//	save in VO
				{
					m_doc.setBP_C_SalesRegion_ID(super.getC_SalesRegion_ID());
					log.fine("C_SalesRegion_ID=" + super.getC_SalesRegion_ID() + " (from BPL)" );
				}
				else	//	From Sales Rep of Document -> Sales Region
				{
					sql = "SELECT COALESCE(MAX(C_SalesRegion_ID),0) FROM C_SalesRegion WHERE SalesRep_ID=?";
					setC_SalesRegion_ID (DB.getSQLValue(null,
						sql, m_doc.getSalesRep_ID()));
					if (super.getC_SalesRegion_ID() != 0)		//	save in VO
					{
						m_doc.setBP_C_SalesRegion_ID(super.getC_SalesRegion_ID());
						log.fine("C_SalesRegion_ID=" + super.getC_SalesRegion_ID() + " (from SR)" );
					}
					else
						m_doc.setBP_C_SalesRegion_ID(-2);	//	don't try again
				}
			}
			if (m_acct != null && super.getC_SalesRegion_ID() == 0)
				setC_SalesRegion_ID (m_acct.getC_SalesRegion_ID());
		}
		//
	//	log.fine("C_SalesRegion_ID=" + super.getC_SalesRegion_ID() 
	//		+ ", C_BPartner_Location_ID=" + m_docVO.C_BPartner_Location_ID
	//		+ ", BP_C_SalesRegion_ID=" + m_docVO.BP_C_SalesRegion_ID 
	//		+ ", SR=" + m_acctSchema.isAcctSchemaElement(MAcctSchemaElement.ELEMENTTYPE_SalesRegion));
		return super.getC_SalesRegion_ID();
	}	//	getC_SalesRegion_ID

	
	/**
	 * 	Before Save
	 *	@param newRecord new
	 *	@return true
	 */
	@Override
	protected boolean beforeSave (boolean newRecord)
	{
		if (newRecord)
		{
			log.fine(toString());
			//
			getAD_Org_ID();
			getC_SalesRegion_ID();
			//  Set Default Account Info
			if (getM_Product_ID() == 0)
				setM_Product_ID (m_acct.getM_Product_ID());
			if (getC_LocFrom_ID() == 0)
				setC_LocFrom_ID (m_acct.getC_LocFrom_ID());
			if (getC_LocTo_ID() == 0)
				setC_LocTo_ID (m_acct.getC_LocTo_ID());
			if (getC_BPartner_ID() == 0)
				setC_BPartner_ID (m_acct.getC_BPartner_ID());
			if (getAD_OrgTrx_ID() == 0)
				setAD_OrgTrx_ID (m_acct.getAD_OrgTrx_ID());
			if (getC_Project_ID() == 0)
				setC_Project_ID (m_acct.getC_Project_ID());
			if (getC_Campaign_ID() == 0)
				setC_Campaign_ID (m_acct.getC_Campaign_ID());
			if (getC_Activity_ID() == 0)
				setC_Activity_ID (m_acct.getC_Activity_ID());
			if (getUser1_ID() == 0)
				setUser1_ID (m_acct.getUser1_ID());
			if (getUser2_ID() == 0)
				setUser2_ID (m_acct.getUser2_ID());
		}
		return true;
	}	//	beforeSave
	
	/**************************************************************************
	 * 	Update Line with reversed Original Amount in Accounting Currency.
	 * 	Also copies original dimensions like Project, etc.
	 * 	Called from Doc_MatchInv
	 * 	@param AD_Table_ID table
	 * 	@param Record_ID record
	 * 	@param Line_ID line
	 * 	@param targetQty
	 *  @param documentQty
	 *  @param C_Currency_ID
	 * 	@return true if success
	 */
	public boolean updateReverseLine (int AD_Table_ID, int Record_ID, int Line_ID,
			BigDecimal targetQty, BigDecimal documentQty, int C_Currency_ID)
	{
		boolean success = false;

		String sql = "SELECT * "
			+ "FROM Fact_Acct "
			+ "WHERE C_AcctSchema_ID=? AND AD_Table_ID=? AND Record_ID=?"
			+ " AND Line_ID=? AND Account_ID=?";
		try
		{
			PreparedStatement pstmt = DB.prepareStatement(sql, get_Trx());
			pstmt.setInt(1, getC_AcctSchema_ID());
			pstmt.setInt(2, AD_Table_ID);
			pstmt.setInt(3, Record_ID);
			pstmt.setInt(4, Line_ID);
			pstmt.setInt(5, m_acct.getAccount_ID());
			ResultSet rs = pstmt.executeQuery();
			if (rs.next())
			{
				MFactAcct fact = new MFactAcct(getCtx(), rs, get_Trx());

				// Set the accounted amounts to currency precision
				int precision = MCurrency.getStdPrecision(getCtx(), C_Currency_ID);

				BigDecimal dr = fact.getAmtAcctDr().multiply(targetQty.abs())
								.divide(documentQty.abs(), precision, BigDecimal.ROUND_HALF_UP);
				BigDecimal cr = fact.getAmtAcctCr().multiply(targetQty.abs())
								.divide(documentQty.abs(), precision, BigDecimal.ROUND_HALF_UP);

				//  Accounted Amounts - reverse
				setAmtAcctDr (cr);
				setAmtAcctCr (dr);
				
				//  Source Amounts
				setAmtSourceDr (getAmtAcctDr());
				setAmtSourceCr (getAmtAcctCr());

				success = true;
				log.fine(new StringBuffer("(Table=").append(AD_Table_ID)
					.append(",Record_ID=").append(Record_ID)
					.append(",Line=").append(Record_ID)
					.append(", Account=").append(m_acct)
					.append(",dr=").append(dr).append(",cr=").append(cr)
					.append(") - DR=").append(getAmtSourceDr()).append("|").append(getAmtAcctDr())
					.append(", CR=").append(getAmtSourceCr()).append("|").append(getAmtAcctCr())
					.toString());
				//	Dimensions
				setAD_OrgTrx_ID(fact.getAD_OrgTrx_ID());
				setC_Project_ID (fact.getC_Project_ID());
				setC_Activity_ID(fact.getC_Activity_ID());
				setC_Campaign_ID(fact.getC_Campaign_ID());
				setC_SalesRegion_ID(fact.getC_SalesRegion_ID());
				setC_LocFrom_ID(fact.getC_LocFrom_ID());
				setC_LocTo_ID(fact.getC_LocTo_ID());
				setM_Product_ID(fact.getM_Product_ID());
				setM_Locator_ID(fact.getM_Locator_ID());
				setUser1_ID(fact.getUser1_ID());
				setUser2_ID(fact.getUser2_ID());
				setC_UOM_ID(fact.getC_UOM_ID());
				setC_Tax_ID(fact.getC_Tax_ID());
				//	Org for cross charge
				setAD_Org_ID (fact.getAD_Org_ID());
			}
			else
				log.warning(new StringBuffer("Not Found (try later) ")
					.append(",C_AcctSchema_ID=").append(getC_AcctSchema_ID())
					.append(", AD_Table_ID=").append(AD_Table_ID)
					.append(",Record_ID=").append(Record_ID)
					.append(",Line_ID=").append(Line_ID)
					.append(", Account_ID=").append(m_acct.getAccount_ID()).toString());
			rs.close();
			pstmt.close();
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql, e);
		}
		return success;
	}   //  updateReverseLine

}	//	FactLine
