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

import java.lang.reflect.*;
import java.math.*;
import java.sql.*;
import java.util.*;
import java.util.logging.*;

import org.compiere.api.*;
import org.compiere.framework.*;
import org.compiere.model.*;
import org.compiere.report.*;
import org.compiere.util.*;
import org.compiere.vos.*;

/**
 *  Posting Document Root.
 *  @author Jorg Janke
 *  @version  $Id: Doc.java,v 1.6 2006/07/30 00:53:33 jjanke Exp $
 */
public abstract class Doc implements AccountingInterface
{
	//  Posting Status - AD_Reference_ID=234     //
	/**	Document Status         */
	public static final String 	STATUS_NotPosted        = "N";
	/**	Document Status         */
	public static final String 	STATUS_NotBalanced      = "b";
	/**	Document Status         */
	public static final String 	STATUS_NotConvertible   = "c";
	/**	Document Status         */
	public static final String 	STATUS_PeriodClosed     = "p";
	/**	Document Status         */
	public static final String 	STATUS_InvalidAccount   = "i";
	/**	Document Status         */
	public static final String 	STATUS_PostPrepared     = "y";
	/**	Document Status         */
	public static final String 	STATUS_Posted           = "Y";
	/**	Document Status         */
	public static final String 	STATUS_Error            = "E";
	/**	Document Status         */
	public static final String 	STATUS_DocumentError    = "e";


	/**
	 *  Create Posting document
	 *	@param ass accounting schema
	 *  @param AD_Table_ID Table ID of Documents
	 *  @param Record_ID record ID to load
	 *  @param trx transaction name
	 *  @return Document or null
	 */
	public static Doc get (MAcctSchema[] ass, int AD_Table_ID, int Record_ID, Trx trx)
	{
		Ctx ctx = ass[0].getCtx();
		MDocBaseType dbt = MDocBaseType.getForTable(ctx, AD_Table_ID);
		if (dbt == null)
		{
			s_log.log(Level.SEVERE, "No DocType for AD_Table_ID=" + AD_Table_ID);
			return null;
		}
		String TableName = dbt.getTableName();
		if (TableName == null)
		{
			s_log.severe("No TableName for " + dbt);
			return null;
		}
		//
		Doc doc = null;
		StringBuffer sql = new StringBuffer("SELECT * FROM ")
			.append(TableName)
			.append(" WHERE ").append(TableName).append("_ID=? AND Processed='Y'");
		PreparedStatement pstmt = null;
		try
		{
			pstmt = DB.prepareStatement (sql.toString(), trx);
			pstmt.setInt (1, Record_ID);
			ResultSet rs = pstmt.executeQuery ();
			if (rs.next ())
			{
				doc = get (ass, AD_Table_ID, rs, trx);
			}
			else
				s_log.severe("Not Found: " + TableName + "_ID=" + Record_ID);
			rs.close ();
			pstmt.close ();
			pstmt = null;
		}
		catch (Exception e)
		{
			s_log.log (Level.SEVERE, sql.toString(), e);
		}
		try
		{
			if (pstmt != null)
				pstmt.close ();
			pstmt = null;
		}
		catch (Exception e)
		{
			pstmt = null;
		}
		return doc;
	}	//	get

	/**
	 *  Create Posting document
	 *	@param ass accounting schema
	 *  @param AD_Table_ID Table ID of Documents
	 *  @param rs ResultSet
	 *  @param trx transaction name
	 *  @return Document
	 */
	public static Doc get (MAcctSchema[] ass, int AD_Table_ID, ResultSet rs, Trx trx)
	{
		Ctx ctx = ass[0].getCtx();
		MDocBaseType dbt = MDocBaseType.getForTable(ctx, AD_Table_ID);
		if (dbt == null)
		{
			s_log.log(Level.SEVERE, "No DocType for AD_Table_ID=" + AD_Table_ID);
			return null;
		}
		try
		{
			Doc doc = (Doc)dbt.getAccountingInstance(ass, rs, trx);
			return doc;
		}
		catch (Exception e)
		{
			s_log.log(Level.SEVERE, "Error creating Document for " + dbt);
		}
		return null;
	}   //  get

	/**
	 *  Post Document
	 * 	@param ass accounting schemata
	 * 	@param 	AD_Table_ID		Transaction table
	 *  @param  Record_ID       Record ID of this document
	 *  @param  force           force posting
	 *  @param trx			transaction
	 *  @return null if the document was posted or error message
	 */
	public static String postImmediate (MAcctSchema[] ass,
		int AD_Table_ID, int Record_ID, boolean force, Trx trx)
	{
		Doc doc = get (ass, AD_Table_ID, Record_ID, trx);
		if (doc != null)
			return doc.post (force, true);	//	repost
		return "NoDoc";
	}   //  post

	/**	Static Log						*/
	protected static CLogger	s_log = CLogger.getCLogger(Doc.class);
	/**	Log	per Document				*/
	protected CLogger			log = CLogger.getCLogger(getClass());


	/**************************************************************************
	 *  Constructor
	 * 	@param ass accounting schemata
	 * 	@param clazz Document Class
	 * 	@param rs result set
	 * 	@param defaultDocumentType default document type or null
	 * 	@param trx p_trx
	 */
	public Doc (MAcctSchema[] ass, Class<?> clazz, ResultSet rs, String defaultDocumentType, Trx trx)
	{
		p_Status = STATUS_Error;
		m_ass = ass;
		m_ctx = new Ctx(m_ass[0].getCtx());
		m_ctx.setAD_Client_ID(m_ass[0].getAD_Client_ID());

		String className = clazz.getName();
		className = className.substring(className.lastIndexOf('.')+1);
		try
		{
			Constructor<?> constructor = clazz.getConstructor(new Class[]{Ctx.class, ResultSet.class, Trx.class});
			p_po = (PO)constructor.newInstance(new Object[]{m_ctx, rs, trx});
		}
		catch (Exception e)
		{
			String msg = className + ": " + e.getLocalizedMessage();
			log.severe(msg);
			throw new IllegalArgumentException(msg);
		}

		//	DocStatus
		int index = p_po.get_ColumnIndex("DocStatus");
		if (index != -1)
			m_DocStatus = (String)p_po.get_Value(index);

		//	Document Type
		setDocumentType (defaultDocumentType);
		m_trxName = trx;
		if (m_trxName == null)
			m_trxName = Trx.get("Post" + m_DocumentType + p_po.get_ID());
		p_po.set_Trx(m_trxName);

		//	Amounts
		m_Amounts[0] = Env.ZERO;
		m_Amounts[1] = Env.ZERO;
		m_Amounts[2] = Env.ZERO;
		m_Amounts[3] = Env.ZERO;
	}   //  Doc

	/** Accounting Schema Array     */
	private MAcctSchema[]    	m_ass = null;
	/** Context						*/
	private Ctx					m_ctx = null;
	/** Transaction Name			*/
	private Trx                 m_trxName = null;
	/** The Document				*/
	protected PO				p_po = null;
	/** Document Type      			*/
	private String				m_DocumentType = null;
	/** Document Status      			*/
	private String				m_DocStatus = null;
	/** Document No      			*/
	private String				m_DocumentNo = null;
	/** Description      			*/
	private String				m_Description = null;
	/** GL Category      			*/
	private int					m_GL_Category_ID = 0;
	/** GL Period					*/
	private MPeriod 			m_period = null;
	/** Period ID					*/
	private int					m_C_Period_ID = 0;
	/** Location From				*/
	private int					m_C_LocFrom_ID = 0;
	/** Location To					*/
	private int					m_C_LocTo_ID = 0;
	/** Accounting Date				*/
	private Timestamp			m_DateAcct = null;
	/** Document Date				*/
	private Timestamp			m_DateDoc = null;
	/** Tax Included				*/
	private boolean				m_TaxIncluded = false;
	/** Is (Source) Multi-Currency Document - i.e. the document has different currencies
	 *  (if true, the document will not be source balanced)     */
	private boolean				m_MultiCurrency = false;
	/** BP Sales Region    			*/
	private int					m_BP_C_SalesRegion_ID = -1;
	/** B Partner	    			*/
	private int					m_C_BPartner_ID = -1;

	/** Bank Account				*/
	private int 				m_C_BankAccount_ID = -1;
	/** Cash Book					*/
	private int 				m_C_CashBook_ID = -1;
	/** Currency					*/
	private int					m_C_Currency_ID = -1;
	/** Work Order Class            */
	private int                 m_workorder_id = -1;


	/**	Contained Doc Lines			*/
	protected DocLine[]			p_lines;

	/** Facts                       */
	private ArrayList<Fact>    	m_fact = null;

	/** No Currency in Document Indicator (-1)	*/
	protected static final int  NO_CURRENCY = -2;

	/**	Actual Document Status  */
	protected String			p_Status = null;
	/** Error Message			*/
	protected String			p_Error = null;


	/**
	 * 	Get Context
	 *	@return context
	 */
	protected Ctx getCtx()
	{
		return m_ctx;
	}	//	getCtx

	/**
	 * 	Get Table Name
	 *	@return table name
	 */
	public String get_TableName()
	{
		return p_po.get_TableName();
	}	//	get_TableName

	/**
	 * 	Get Table ID
	 *	@return table id
	 */
	public int get_Table_ID()
	{
		return p_po.get_Table_ID();
	}	//	get_Table_ID

	/**
	 * 	Get Record_ID
	 *	@return record id
	 */
	public int get_ID()
	{
		return p_po.get_ID();
	}	//	get_ID

	/**
	 * 	Get Persistent Object
	 *	@return po
	 */
	protected PO getPO()
	{
		return p_po;
	}	//	getPO

	/**
	 *  Post Document.
	 *  <pre>
	 *  - try to lock document (Processed='Y' (AND Processing='N' AND Posted='N'))
	 * 		- if not ok - return false
	 *          - postlogic (for all Accounting Schema)
	 *              - create Fact lines
	 *          - postCommit
	 *              - commits Fact lines and Document & sets Processing = 'N'
	 *              - if error - create Note
	 *  </pre>
	 *  @param force if true ignore that locked
	 *  @param repost if true ignore that already posted
	 *  @return null if posted error otherwise
	 */
	public final String post (boolean force, boolean repost)
	{
		if (m_DocStatus == null)
			;	//	return "No DocStatus for DocumentNo=" + getDocumentNo();
		else if (m_DocStatus.equals(DocActionConstants.STATUS_Completed)
			|| m_DocStatus.equals(DocActionConstants.STATUS_Closed)
			|| m_DocStatus.equals(DocActionConstants.STATUS_Voided)
			|| m_DocStatus.equals(DocActionConstants.STATUS_Reversed))
			;
		else
			return "Invalid DocStatus='" + m_DocStatus + "' for DocumentNo=" + getDocumentNo();
		//
		if (p_po.getAD_Client_ID() != m_ass[0].getAD_Client_ID())
		{
			String error = "AD_Client_ID Conflict - Document=" + p_po.getAD_Client_ID()
				+ ", AcctSchema=" + m_ass[0].getAD_Client_ID();
			log.severe(error);
			return error;
		}

		//  Lock Record ----
		StringBuffer sql = new StringBuffer ("UPDATE ");
		sql.append(get_TableName()).append( " SET Processing='Y' WHERE ")
			.append(get_TableName()).append("_ID=").append(get_ID())
			.append(" AND Processed='Y'"); // AND IsActive='Y'");
		if (!force)
			sql.append(" AND (Processing='N' OR Processing IS NULL)");
		if (!repost)
			sql.append(" AND Posted='N'");
		if (DB.executeUpdate(sql.toString(), (Trx) null) == 1)	//	outside p_trx
			log.info("Locked: " + get_TableName() + "_ID=" + get_ID());
		else
		{
			log.log(Level.WARNING, "Resubmit - Cannot lock " + get_TableName() + "_ID="
				+ get_ID() + ", Force=" + force + ",RePost=" + repost);
			if (force)
				return "Cannot Lock - ReSubmit";
			return "Cannot Lock - ReSubmit or RePost with Force";
		}

		p_Error = loadDocumentDetails();
		if (p_Error != null)	//	LoadError
		{
			p_Status = STATUS_DocumentError;
			save(null);
			return p_Error;
		}

		//  Delete existing Accounting
		if (repost)
		{
			if (isPosted() && !isPeriodOpen())	//	already posted - don't delete if period closed
			{
				log.log(Level.WARNING, toString() + " - Period Closed for already posed document");
				unlock();
				return "PeriodClosed";
			}
			//	delete it
			deleteAcct();
		}
		else if (isPosted())
		{
			log.log(Level.WARNING, toString() + " - Document already posted");
			unlock();
			return "AlreadyPosted";
		}

		p_Status = STATUS_NotPosted;

		//  Create Fact per AcctSchema
		m_fact = new ArrayList<Fact>();

		//  for all Accounting Schema
		boolean OK = true;
		try
		{
			for (int i = 0; OK && (i < m_ass.length); i++)
			{
				//	if acct schema has "only" org, skip
				boolean skip = false;
				if (m_ass[i].getAD_OrgOnly_ID() != 0)
				{
					if (m_ass[i].getOnlyOrgs() == null)
						m_ass[i].setOnlyOrgs(MReportTree.getChildIDs(getCtx(),
							0, X_C_AcctSchema_Element.ELEMENTTYPE_Organization,
							m_ass[i].getAD_OrgOnly_ID()));

					//	Header Level Org
					skip = m_ass[i].isSkipOrg(getAD_Org_ID());
					//	Line Level Org
					if (p_lines != null)
					{
						for (int line = 0; skip && (line < p_lines.length); line++)
						{
							skip = m_ass[i].isSkipOrg(p_lines[line].getAD_Org_ID());
							if (!skip)
								break;
						}
					}
				}
				if (skip)
					continue;
				//	post
				log.info("(" + i + ") " + p_po);
				p_Status = postLogic (i);
				if (!p_Status.equals(STATUS_Posted))
					OK = false;
			}
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, "", e);
			p_Status = STATUS_Error;
			p_Error = e.toString();
			OK = false;
		}

		//  commitFact
		p_Status = postCommit (p_Status);

		//  Create Note
		if (!p_Status.equals(STATUS_Posted))
		{
			//  Insert Note
			String AD_MessageValue = "PostingError-" + p_Status;
			int AD_User_ID = p_po.getUpdatedBy();
			MNote note = new MNote (getCtx(), AD_MessageValue, AD_User_ID,
				getAD_Client_ID(), getAD_Org_ID(), null);
			note.setRecord(p_po.get_Table_ID(), p_po.get_ID());
			//  Reference
			note.setReference(toString());	//	Document
			//	Text
			StringBuffer Text = new StringBuffer (Msg.getMsg(Env.getCtx(), AD_MessageValue));
			if (p_Error != null)
				Text.append(" (").append(p_Error).append(")");
			String cn = getClass().getName();
			Text.append(" - ").append(cn.substring(cn.lastIndexOf('.')))
				.append(" (").append(getDocumentType())
				.append(" - DocumentNo=").append(getDocumentNo())
				.append(", DateAcct=").append(getDateAcct().toString().substring(0,10))
				.append(", Amount=").append(getAmount())
				.append(", Sta=").append(p_Status)
				.append(" - PeriodOpen=").append(isPeriodOpen())
				.append(", Balanced=").append(isBalanced());
			note.setTextMsg(Text.toString());
			note.save();
		}

		//  dispose facts
		for (int i = 0; i < m_fact.size(); i++)
		{
			Fact fact = m_fact.get(i);
			if (fact != null)
				fact.dispose();
		}
		p_lines = null;

		if (p_Status.equals(STATUS_Posted))
			return null;
		return p_Error;
	}   //  post

	/**
	 * 	Delete Accounting
	 *	@return number of records
	 */
	private int deleteAcct()
	{
		StringBuffer sql = new StringBuffer ("DELETE FROM Fact_Acct WHERE AD_Table_ID=")
			.append(get_Table_ID())
			.append(" AND Record_ID=").append(p_po.get_ID());
		int no = DB.executeUpdate(sql.toString(), getTrxName());
		if (no != 0)
			log.info("#=" + no);
		return no;
	}	//	deleteAcct

	/**
	 *  Posting logic for Accounting Schema index
	 *  @param  index   Accounting Schema index
	 *  @return posting status/error code
	 */
	private final String postLogic (int index)
	{
		log.info("(" + index + ") " + p_po);

		//  rejectUnbalanced
		if (!m_ass[index].isSuspenseBalancing() && !isBalanced())
			return STATUS_NotBalanced;

		//  rejectUnconvertible
		if (!isConvertible(m_ass[index]))
			return STATUS_NotConvertible;

		//  rejectPeriodClosed
		if (!isPeriodOpen())
			return STATUS_PeriodClosed;

		//  createFacts
		ArrayList<Fact> facts = createFacts (m_ass[index]);
		if (facts == null)
			return STATUS_Error;
		for (int f = 0; f < facts.size(); f++)
		{
			Fact fact = facts.get(f);
			if (fact == null)
				return STATUS_Error;
			m_fact.add(fact);
			//
			p_Status = STATUS_PostPrepared;

			//	check accounts
			if (!fact.checkAccounts())
				return STATUS_InvalidAccount;

			//	distribute
			if (!fact.distribute())
				return STATUS_Error;

			//  balanceSource
			if (!fact.isSourceBalanced())
			{
				fact.balanceSource();
				if (!fact.isSourceBalanced())
					return STATUS_NotBalanced;
			}

			//  balanceSegments
			if (!fact.isSegmentBalanced())
			{
				fact.balanceSegments();
				if (!fact.isSegmentBalanced())
					return STATUS_NotBalanced;
			}

			//  balanceAccounting
			if (!fact.isAcctBalanced())
			{
				fact.balanceAccounting();
				if (!fact.isAcctBalanced())
					return STATUS_NotBalanced;
			}

		}	//	for all facts

		return STATUS_Posted;
	}   //  postLogic

	/**
	 *  Post Commit.
	 *  Save Facts & Document
	 *  @param status status
	 *  @return Posting Status
	 */
	private final String postCommit (String status)
	{
		log.info("Sta=" + status + " DT=" + getDocumentType()
			+ " ID=" +  p_po.get_ID());
		p_Status = status;

		Trx p_trx = getTrxName();
		try
		{
		//  *** Transaction Start       ***
			//  Commit Facts
			if (status.equals(STATUS_Posted))
			{
				for (int i = 0; i < m_fact.size(); i++)
				{
					Fact fact = m_fact.get(i);
					if (fact == null)
						;
					else if (fact.save(getTrxName()))
						;
					else
					{
						log.log(Level.SEVERE, "(fact not saved) ... rolling back");
						p_trx.rollback();
						p_trx.close();
						unlock();
						return STATUS_Error;
					}
				}
			}
			//  Commit Doc
			if (!save(getTrxName()))     //  contains unlock & document status update
			{
				log.log(Level.SEVERE, "(doc not saved) ... rolling back");
				p_trx.rollback();
				p_trx.close();
				unlock();
				return STATUS_Error;
			}
			//	Success
			p_trx.commit();
			p_trx.close();
			p_trx = null;
		//  *** Transaction End         ***
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, "... rolling back", e);
			status = STATUS_Error;
			try {
				if (p_trx != null)
					p_trx.rollback();
			} catch (Exception e2) {}
			try {
				if (p_trx != null)
					p_trx.close();
				p_trx = null;
			} catch (Exception e3) {}
			unlock();
		}
		p_Status = status;
		return status;
	}   //  postCommit

	/**
	 * 	Get Trx Name and create Transaction
	 *	@return Trx Name
	 */
	protected Trx getTrxName()
	{
		return m_trxName;
	}	//	getTrxName

	/**
	 *  Unlock Document
	 */
	private void unlock()
	{
		StringBuffer sql = new StringBuffer ("UPDATE ");
		sql.append(get_TableName()).append( " SET Processing='N' WHERE ")
			.append(get_TableName()).append("_ID=").append(p_po.get_ID());
		DB.executeUpdate(sql.toString(), (Trx) null);		//	outside p_trx
	}   //  unlock


	/**************************************************************************
	 *  Load Document Type and GL Info.
	 * 	Set p_DocumentType and p_GL_Category_ID
	 * 	@return document type
	 */
	protected String getDocumentType()
	{
		if (m_DocumentType == null)
			setDocumentType(null);
		return m_DocumentType;
	}   //  getDocumentType

	/**
	 *  Load Document Type and GL Info.
	 * 	Set p_DocumentType and p_GL_Category_ID
	 *	@param DocumentType
	 */
	protected void setDocumentType (String DocumentType)
	{
		if (DocumentType != null)
			m_DocumentType = DocumentType;
		//  Set Document Type & GL_Category	// TODO - Cache DocTypes
		if (getC_DocType_ID() != 0)		//	MatchInv,.. does not have C_DocType_ID
		{
			String sql = "SELECT DocBaseType, GL_Category_ID FROM C_DocType WHERE C_DocType_ID=?";
			try
			{
				PreparedStatement pstmt = DB.prepareStatement(sql, (Trx) null);
				pstmt.setInt(1, getC_DocType_ID());
				ResultSet rsDT = pstmt.executeQuery();
				if (rsDT.next())
				{
					m_DocumentType = rsDT.getString(1);
					m_GL_Category_ID = rsDT.getInt(2);
				}
				rsDT.close();
				pstmt.close();
			}
			catch (SQLException e)
			{
				log.log(Level.SEVERE, sql, e);
			}
		}
		if (m_DocumentType == null)
		{
			log.log(Level.SEVERE, "No DocBaseType for C_DocType_ID="
				+ getC_DocType_ID() + ", DocumentNo=" + getDocumentNo());
		}

		//  We have a document Type, but no GL info - search for DocType
		if (m_GL_Category_ID == 0)
		{
			String sql = "SELECT GL_Category_ID FROM C_DocType "
				+ "WHERE AD_Client_ID=? AND DocBaseType=?";
			try
			{
				PreparedStatement pstmt = DB.prepareStatement(sql, (Trx) null);
				pstmt.setInt(1, getAD_Client_ID());
				pstmt.setString(2, m_DocumentType);
				ResultSet rsDT = pstmt.executeQuery();
				if (rsDT.next())
					m_GL_Category_ID = rsDT.getInt(1);
				rsDT.close();
				pstmt.close();
			}
			catch (SQLException e)
			{
				log.log(Level.SEVERE, sql, e);
			}
		}

		//  Still no GL_Category - get Default GL Category
		if (m_GL_Category_ID == 0)
		{
			String sql = "SELECT GL_Category_ID FROM GL_Category "
				+ "WHERE AD_Client_ID=? "
				+ "ORDER BY ASCII(IsDefault) DESC";
			try
			{
				PreparedStatement pstmt = DB.prepareStatement(sql, (Trx) null);
				pstmt.setInt(1, getAD_Client_ID());
				ResultSet rsDT = pstmt.executeQuery();
				if (rsDT.next())
					m_GL_Category_ID = rsDT.getInt(1);
				rsDT.close();
				pstmt.close();
			}
			catch (SQLException e)
			{
				log.log(Level.SEVERE, sql, e);
			}
		}
		//
		if (m_GL_Category_ID == 0)
			log.log(Level.WARNING, "No default GL_Category - " + toString());

		if (m_DocumentType == null)
			throw new IllegalStateException("Document Type not found");
	}	//	setDocumentType


	/**************************************************************************
	 *  Is the Source Document Balanced
	 *  @return true if (source) baanced
	 */
	public boolean isBalanced()
	{
		//  Multi-Currency documents are source balanced by definition
		if (isMultiCurrency())
			return true;
		//
		boolean retValue = getBalance().signum() == 0;
		if (retValue)
			log.fine("Yes " + toString());
		else
			log.warning("NO - " + toString());
		return retValue;
	}	//	isBalanced

	/**
	 *  Is Document convertible to currency and Conversion Type
	 *  @param acctSchema accounting schema
	 *  @return true, if vonvertable to accounting currency
	 */
	public boolean isConvertible (MAcctSchema acctSchema)
	{
		//  No Currency in document
		if (getC_Currency_ID() == NO_CURRENCY)
		{
			log.fine("(none) - " + toString());
			return true;
		}
		//  Get All Currencies
		HashSet<Integer> set = new HashSet<Integer>();
		set.add(new Integer(getC_Currency_ID()));
		for (int i = 0; (p_lines != null) && (i < p_lines.length); i++)
		{
			int C_Currency_ID = p_lines[i].getC_Currency_ID();
			if (C_Currency_ID != NO_CURRENCY)
				set.add(new Integer(C_Currency_ID));
		}

		//  just one and the same
		if ((set.size() == 1) && (acctSchema.getC_Currency_ID() == getC_Currency_ID()))
		{
			log.fine("(same) Cur=" + getC_Currency_ID() + " - " + toString());
			return true;
		}

		boolean convertible = true;
		Iterator<Integer> it = set.iterator();
		while (it.hasNext() && convertible)
		{
			int C_Currency_ID = it.next().intValue();
			if (C_Currency_ID != acctSchema.getC_Currency_ID())
			{
				BigDecimal amt = MConversionRate.getRate (C_Currency_ID, acctSchema.getC_Currency_ID(),
					getDateAcct(), getC_ConversionType_ID(), getAD_Client_ID(), getAD_Org_ID());
				if (amt == null)
				{
					convertible = false;
					log.warning ("NOT from C_Currency_ID=" + C_Currency_ID
						+ " to " + acctSchema.getC_Currency_ID()
						+ " - " + toString());
				}
				else
					log.fine("From C_Currency_ID=" + C_Currency_ID);
			}
		}

		log.fine("Convertible=" + convertible + ", AcctSchema C_Currency_ID=" + acctSchema.getC_Currency_ID() + " - " + toString());
		return convertible;
	}	//	isConvertible

	/**
	 *  Calculate Period from DateAcct.
	 *  m_C_Period_ID is set to -1 of not open to 0 if not found
	 */
	public void setPeriod()
	{
		if (m_period != null)
			return;

		//	Period defined in GL Journal (e.g. adjustment period)
		int index = p_po.get_ColumnIndex("C_Period_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				m_period = MPeriod.get(getCtx(), ii.intValue());
		}
		if (m_period == null)
			m_period = MPeriod.getOfOrg(getCtx(), getAD_Org_ID(), getDateAcct());
		//	Is Period Open?
		if ((m_period != null)
			&& (m_period.isOpen(getDocumentType(), getDateAcct())== null))
			m_C_Period_ID = m_period.getC_Period_ID();
		else
			m_C_Period_ID = -1;
		//
		log.fine(	// + AD_Client_ID + " - "
			getDateAcct() + " - " + getDocumentType() + " => " + m_C_Period_ID);
	}   //  setC_Period_ID

	/**
	 * 	Get C_Period_ID
	 *	@return period
	 */
	public int getC_Period_ID()
	{
		if (m_period == null)
			setPeriod();
		return m_C_Period_ID;
	}	//	getC_Period_ID

	/**
	 *	Is Period Open
	 *  @return true if period is open
	 */
	public boolean isPeriodOpen()
	{
		setPeriod();
		boolean open = m_C_Period_ID > 0;
		if (open)
			log.fine("Yes - " + toString());
		else
			log.warning("NO - " + toString());
		return open;
	}	//	isPeriodOpen

	/*************************************************************************/

	/**	Amount Type - Invoice - Gross   */
	public static final int 	AMTTYPE_Gross   = 0;
	/**	Amount Type - Invoice - Net   */
	public static final int 	AMTTYPE_Net     = 1;
	/**	Amount Type - Invoice - Charge   */
	public static final int 	AMTTYPE_Charge  = 2;

	/** Source Amounts (may not all be used)	*/
	private final BigDecimal[]		m_Amounts = new BigDecimal[4];
	/** Quantity								*/
	private BigDecimal			m_qty = null;

	/**
	 *	Get the Amount
	 *  (loaded in loadDocumentDetails)
	 *
	 *  @param AmtType see AMTTYPE_*
	 *  @return Amount
	 */
	public BigDecimal getAmount(int AmtType)
	{
		if ((AmtType < 0) || (AmtType >= m_Amounts.length))
			return null;
		return m_Amounts[AmtType];
	}	//	getAmount

	/**
	 *	Set the Amount
	 *  @param AmtType see AMTTYPE_*
	 *  @param amt Amount
	 */
	protected void setAmount(int AmtType, BigDecimal amt)
	{
		if ((AmtType < 0) || (AmtType >= m_Amounts.length))
			return;
		if (amt == null)
			m_Amounts[AmtType] = Env.ZERO;
		else
			m_Amounts[AmtType] = amt;
	}	//	setAmount

	/**
	 *  Get Amount with index 0
	 *  @return Amount (primary document amount)
	 */
	public BigDecimal getAmount()
	{
		return m_Amounts[0];
	}   //  getAmount

	/**
	 *  Set Quantity
	 *  @param qty Quantity
	 */
	protected void setQty (BigDecimal qty)
	{
		m_qty = qty;
	}   //  setQty

	/**
	 *  Get Quantity
	 *  @return Quantity
	 */
	public BigDecimal getQty()
	{
		if (m_qty == null)
		{
			int index = p_po.get_ColumnIndex("Qty");
			if (index != -1)
				m_qty = (BigDecimal)p_po.get_Value(index);
			else
				m_qty = Env.ZERO;
		}
		return m_qty;
	}   //  getQty

	/*************************************************************************/

	/**	Account Type - Invoice - Charge  */
	public static final int 	ACCTTYPE_Charge         = 0;
	/**	Account Type - Invoice - AR  */
	public static final int 	ACCTTYPE_C_Receivable   = 1;
	/**	Account Type - Invoice - AP  */
	public static final int 	ACCTTYPE_V_Liability    = 2;
	/**	Account Type - Invoice - AP Service  */
	public static final int 	ACCTTYPE_V_Liability_Services    = 3;
	/**	Account Type - Invoice - AR Service  */
	public static final int 	ACCTTYPE_C_Receivable_Services   = 4;

	/** Account Type - Payment - Unallocated */
	public static final int     ACCTTYPE_UnallocatedCash = 10;
	/** Account Type - Payment - Transfer */
	public static final int 	ACCTTYPE_BankInTransit  = 11;
	/** Account Type - Payment - Selection */
	public static final int     ACCTTYPE_PaymentSelect  = 12;
	/** Account Type - Payment - Prepayment */
	public static final int 	ACCTTYPE_C_Prepayment  = 13;
	/** Account Type - Payment - Prepayment */
	public static final int     ACCTTYPE_V_Prepayment  = 14;

	/** Account Type - Cash     - Asset */
	public static final int     ACCTTYPE_CashAsset = 20;
	/** Account Type - Cash     - Transfer */
	public static final int     ACCTTYPE_CashTransfer = 21;
	/** Account Type - Cash     - Expense */
	public static final int     ACCTTYPE_CashExpense = 22;
	/** Account Type - Cash     - Receipt */
	public static final int     ACCTTYPE_CashReceipt = 23;
	/** Account Type - Cash     - Difference */
	public static final int     ACCTTYPE_CashDifference = 24;

	/** Account Type - Allocation - Discount Expense (AR) */
	public static final int 	ACCTTYPE_DiscountExp = 30;
	/** Account Type - Allocation - Discount Revenue (AP) */
	public static final int 	ACCTTYPE_DiscountRev = 31;
	/** Account Type - Allocation  - Write Off */
	public static final int 	ACCTTYPE_WriteOff = 32;

	/** Account Type - Bank Statement - Asset  */
	public static final int     ACCTTYPE_BankAsset = 40;
	/** Account Type - Bank Statement - Interest Revenue */
	public static final int     ACCTTYPE_InterestRev = 41;
	/** Account Type - Bank Statement - Interest Exp  */
	public static final int     ACCTTYPE_InterestExp = 42;

	/** Inventory Accounts  - Differnces	*/
	public static final int     ACCTTYPE_InvDifferences = 50;
	/** Inventory Accounts - NIR		*/
	public static final int     ACCTTYPE_NotInvoicedReceipts = 51;

	/** Project Accounts - Assets      	*/
	public static final int     ACCTTYPE_ProjectAsset = 61;
	/** Project Accounts - WIP         	*/
	public static final int     ACCTTYPE_ProjectWIP = 62;

	/** GL Accounts - PPV Offset		*/
	public static final int     ACCTTYPE_PPVOffset = 101;
	/** GL Accounts - Commitment Offset	*/
	public static final int     ACCTTYPE_CommitmentOffset = 111;

	/** Work Order Close Expense Account */
	public static final int ACCTTYPE_WOCloseExpenseAcct = 767;
	/** Work Order Material Account */
	public static final int ACCTTYPE_WOMaterial = 177;


	/**
	 *	Get the Valid Combination id for Accounting Schema
	 *  @param AcctType see ACCTTYPE_*
	 *  @param as accounting schema
	 *  @return C_ValidCombination_ID
	 */
	public int getValidCombination_ID (int AcctType, MAcctSchema as)
	{
		int para_1 = 0;     //  first parameter (second is always AcctSchema)
		String sql = null;

		/**	Account Type - Invoice */
		if (AcctType == ACCTTYPE_Charge)	//	see getChargeAccount in DocLine
		{
			int cmp = getAmount(AMTTYPE_Charge).compareTo(Env.ZERO);
			if (cmp == 0)
				return 0;
			else if (cmp < 0)
				sql = "SELECT CH_Expense_Acct FROM C_Charge_Acct WHERE C_Charge_ID=? AND C_AcctSchema_ID=?";
			else
				sql = "SELECT CH_Revenue_Acct FROM C_Charge_Acct WHERE C_Charge_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_Charge_ID();
		}
		else if (AcctType == ACCTTYPE_V_Liability)
		{
			sql = "SELECT V_Liability_Acct FROM C_BP_Vendor_Acct WHERE C_BPartner_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}
		else if (AcctType == ACCTTYPE_V_Liability_Services)
		{
			sql = "SELECT V_Liability_Services_Acct FROM C_BP_Vendor_Acct WHERE C_BPartner_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}
		else if (AcctType == ACCTTYPE_C_Receivable)
		{
			sql = "SELECT C_Receivable_Acct FROM C_BP_Customer_Acct WHERE C_BPartner_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}
		else if (AcctType == ACCTTYPE_C_Receivable_Services)
		{
			sql = "SELECT C_Receivable_Services_Acct FROM C_BP_Customer_Acct WHERE C_BPartner_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}
		else if (AcctType == ACCTTYPE_V_Prepayment)
		{
			sql = "SELECT V_Prepayment_Acct FROM C_BP_Vendor_Acct WHERE C_BPartner_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}
		else if (AcctType == ACCTTYPE_C_Prepayment)
		{
			sql = "SELECT C_Prepayment_Acct FROM C_BP_Customer_Acct WHERE C_BPartner_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}

		/** Account Type - Payment  */
		else if (AcctType == ACCTTYPE_UnallocatedCash)
		{
			sql = "SELECT B_UnallocatedCash_Acct FROM C_BankAccount_Acct WHERE C_BankAccount_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BankAccount_ID();
		}
		else if (AcctType == ACCTTYPE_BankInTransit)
		{
			sql = "SELECT B_InTransit_Acct FROM C_BankAccount_Acct WHERE C_BankAccount_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BankAccount_ID();
		}
		else if (AcctType == ACCTTYPE_PaymentSelect)
		{
			sql = "SELECT B_PaymentSelect_Acct FROM C_BankAccount_Acct WHERE C_BankAccount_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BankAccount_ID();
		}

		/** Account Type - Allocation   */
		else if (AcctType == ACCTTYPE_DiscountExp)
		{
			sql = "SELECT a.PayDiscount_Exp_Acct FROM C_BP_Group_Acct a, C_BPartner bp "
				+ "WHERE a.C_BP_Group_ID=bp.C_BP_Group_ID AND bp.C_BPartner_ID=? AND a.C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}
		else if (AcctType == ACCTTYPE_DiscountRev)
		{
			sql = "SELECT PayDiscount_Rev_Acct FROM C_BP_Group_Acct a, C_BPartner bp "
				+ "WHERE a.C_BP_Group_ID=bp.C_BP_Group_ID AND bp.C_BPartner_ID=? AND a.C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}
		else if (AcctType == ACCTTYPE_WriteOff)
		{
			sql = "SELECT WriteOff_Acct FROM C_BP_Group_Acct a, C_BPartner bp "
				+ "WHERE a.C_BP_Group_ID=bp.C_BP_Group_ID AND bp.C_BPartner_ID=? AND a.C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}

		/** Account Type - Bank Statement   */
		else if (AcctType == ACCTTYPE_BankAsset)
		{
			sql = "SELECT B_Asset_Acct FROM C_BankAccount_Acct WHERE C_BankAccount_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BankAccount_ID();
		}
		else if (AcctType == ACCTTYPE_InterestRev)
		{
			sql = "SELECT B_InterestRev_Acct FROM C_BankAccount_Acct WHERE C_BankAccount_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BankAccount_ID();
		}
		else if (AcctType == ACCTTYPE_InterestExp)
		{
			sql = "SELECT B_InterestExp_Acct FROM C_BankAccount_Acct WHERE C_BankAccount_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BankAccount_ID();
		}

		/** Account Type - Cash     */
		else if (AcctType == ACCTTYPE_CashAsset)
		{
			sql = "SELECT CB_Asset_Acct FROM C_CashBook_Acct WHERE C_CashBook_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_CashBook_ID();
		}
		else if (AcctType == ACCTTYPE_CashTransfer)
		{
			sql = "SELECT CB_CashTransfer_Acct FROM C_CashBook_Acct WHERE C_CashBook_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_CashBook_ID();
		}
		else if (AcctType == ACCTTYPE_CashExpense)
		{
			sql = "SELECT CB_Expense_Acct FROM C_CashBook_Acct WHERE C_CashBook_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_CashBook_ID();
		}
		else if (AcctType == ACCTTYPE_CashReceipt)
		{
			sql = "SELECT CB_Receipt_Acct FROM C_CashBook_Acct WHERE C_CashBook_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_CashBook_ID();
		}
		else if (AcctType == ACCTTYPE_CashDifference)
		{
			sql = "SELECT CB_Differences_Acct FROM C_CashBook_Acct WHERE C_CashBook_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_CashBook_ID();
		}

		/** Inventory Accounts          */
		else if (AcctType == ACCTTYPE_InvDifferences)
		{
			sql = "SELECT W_Differences_Acct FROM M_Warehouse_Acct WHERE M_Warehouse_ID=? AND C_AcctSchema_ID=?";
			//  "SELECT W_Inventory_Acct, W_Revaluation_Acct, W_InvActualAdjust_Acct FROM M_Warehouse_Acct WHERE M_Warehouse_ID=? AND C_AcctSchema_ID=?";
			para_1 = getM_Warehouse_ID();
		}
		else if (AcctType == ACCTTYPE_NotInvoicedReceipts)
		{
			sql = "SELECT NotInvoicedReceipts_Acct FROM C_BP_Group_Acct a, C_BPartner bp "
				+ "WHERE a.C_BP_Group_ID=bp.C_BP_Group_ID AND bp.C_BPartner_ID=? AND a.C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}

		/** Project Accounts          	*/
		else if (AcctType == ACCTTYPE_ProjectAsset)
		{
			sql = "SELECT PJ_Asset_Acct FROM C_Project_Acct WHERE C_Project_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_Project_ID();
		}
		else if (AcctType == ACCTTYPE_ProjectWIP)
		{
			sql = "SELECT PJ_WIP_Acct FROM C_Project_Acct WHERE C_Project_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_Project_ID();
		}

		/** GL Accounts                 */
		else if (AcctType == ACCTTYPE_PPVOffset)
		{
			sql = "SELECT PPVOffset_Acct FROM C_AcctSchema_GL WHERE C_AcctSchema_ID=?";
			para_1 = -1;
		}
		else if (AcctType == ACCTTYPE_CommitmentOffset)
		{
			sql = "SELECT CommitmentOffset_Acct FROM C_AcctSchema_GL WHERE C_AcctSchema_ID=?";
			para_1 = -1;
		}

		/** Work Order Accounts  */
		else if (AcctType == ACCTTYPE_WOCloseExpenseAcct)
		{
			sql = "SELECT WO_CloseExpense_Acct FROM M_WorkOrderClass_Acct woclass, M_WorkOrder wo "
					+ "WHERE wo.M_WorkOrder_ID = ? AND woclass.M_WorkOrderClass_ID = wo.M_WorkOrderClass_ID AND woclass.C_AcctSchema_ID=?";
			para_1 = getM_WorkOrder_ID();
		}
		else if (AcctType == ACCTTYPE_WOMaterial)
		{
			sql = "SELECT WO_Material_Acct FROM M_WorkOrderClass_Acct woclass, M_WorkOrder wo "
					+ "WHERE wo.M_WorkOrder_ID = ? AND woclass.M_WorkOrderClass_ID = wo.M_WorkOrderClass_ID AND woclass.C_AcctSchema_ID=?";
			para_1 = getM_WorkOrder_ID();
		}

		else
		{
			log.warning ("Not found AcctType=" + AcctType);
			return 0;
		}
		//  Do we have sql & Parameter
		if ((sql == null) || (para_1 == 0))
		{
			log.warning ("No Parameter for AcctType=" + AcctType + " - SQL=" + sql);
			return 0;
		}

		//  Get Acct
		int Account_ID = 0;
		try
		{
			PreparedStatement pstmt = DB.prepareStatement(sql, (Trx) null);
			if (para_1 == -1)   //  GL Accounts
				pstmt.setInt (1, as.getC_AcctSchema_ID());
			else
			{
				pstmt.setInt (1, para_1);
				pstmt.setInt (2, as.getC_AcctSchema_ID());
			}
			ResultSet rs = pstmt.executeQuery();
			if (rs.next())
				Account_ID = rs.getInt(1);
			rs.close();
			pstmt.close();
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, "AcctType=" + AcctType + " - SQL=" + sql, e);
			return 0;
		}
		//	No account
		if (Account_ID == 0)
		{
			log.warning ("Account NOT found Type="
				+ AcctType + ", Record=" + p_po.get_ID());
			return 0;
		}
		return Account_ID;
	}	//	getValidCombination_ID

	/**
	 *	Get the account for Accounting Schema
	 *  @param AcctType see ACCTTYPE_*
	 *  @param as accounting schema
	 *  @return Account
	 */
	public final MAccount getAccount (int AcctType, MAcctSchema as)
	{
		int C_ValidCombination_ID = getValidCombination_ID(AcctType, as);
		if (C_ValidCombination_ID == 0)
			return null;
		//	Return Account
		MAccount acct = MAccount.get (as.getCtx(), C_ValidCombination_ID);
		return acct;
	}	//	getAccount


	/**************************************************************************
	 *  Save to Disk - set posted flag
	 *  @param trx transaction
	 *  @return true if saved
	 */
	private final boolean save(Trx trx)
	{
		log.fine(toString() + "->" + p_Status);
		StringBuffer sql = new StringBuffer("UPDATE ");
		sql.append(get_TableName()).append(" SET Posted='").append(p_Status)
			.append("',Processing='N' ")
			.append("WHERE ")
			.append(get_TableName()).append("_ID=").append(p_po.get_ID());
		int no = DB.executeUpdate(sql.toString(), trx);
		return no == 1;
	}   //  save
	/**
	 *  Get DocLine with ID
	 *  @param Record_ID Record ID
	 *  @return DocLine
	 */
	public DocLine getDocLine (int Record_ID)
	{
		if ((p_lines == null) || (p_lines.length == 0) || (Record_ID == 0))
			return null;

		for (int i = 0; i < p_lines.length; i++)
		{
			if (p_lines[i].get_ID() == Record_ID)
				return p_lines[i];
		}
		return null;
	}   //  getDocLine

	/**
	 *  String Representation
	 *  @return String
	 */
	@Override
	public String toString()
	{
		return p_po.toString();
	}   //  toString


	/**
	 * 	Get AD_Client_ID
	 *	@return client
	 */
	public int getAD_Client_ID()
	{
		return p_po.getAD_Client_ID();
	}	//	getAD_Client_ID

	/**
	 * 	Get AD_Org_ID
	 *	@return org
	 */
	public int getAD_Org_ID()
	{
		return p_po.getAD_Org_ID();
	}	//	getAD_Org_ID

	/**
	 * 	Get Document No
	 *	@return document No
	 */
	public String getDocumentNo()
	{
		if (m_DocumentNo != null)
			return m_DocumentNo;
		int index = p_po.get_ColumnIndex("DocumentNo");
		if (index == -1)
			index = p_po.get_ColumnIndex("Name");
		if (index == -1)
			throw new UnsupportedOperationException("No DocumentNo");
		m_DocumentNo = (String)p_po.get_Value(index);
		return m_DocumentNo;
	}	//	getDocumentNo

	/**
	 * 	Get Description
	 *	@return Description
	 */
	public String getDescription()
	{
		if (m_Description == null)
		{
			int index = p_po.get_ColumnIndex("Description");
			if (index != -1)
				m_Description = (String)p_po.get_Value(index);
			else
				m_Description = "";
		}
		return m_Description;
	}	//	getDescription

	/**
	 * 	Get C_Currency_ID
	 *	@return currency
	 */
	public int getC_Currency_ID()
	{
		if (m_C_Currency_ID == -1)
		{
			int index = p_po.get_ColumnIndex("C_Currency_ID");
			if (index != -1)
			{
				Integer ii = (Integer)p_po.get_Value(index);
				if (ii != null)
					m_C_Currency_ID = ii.intValue();
			}
			if (m_C_Currency_ID == -1)
				m_C_Currency_ID = NO_CURRENCY;
		}
		return m_C_Currency_ID;
	}	//	getC_Currency_ID

	/**
	 * 	Set C_Currency_ID
	 *	@param C_Currency_ID id
	 */
	public void setC_Currency_ID (int C_Currency_ID)
	{
		m_C_Currency_ID = C_Currency_ID;
	}	//	setC_Currency_ID

	/**
	 * 	Is Multi Currency
	 *	@return mc
	 */
	public boolean isMultiCurrency()
	{
		return m_MultiCurrency;
	}	//	isMultiCurrency

	/**
	 * 	Set Multi Currency
	 *	@param mc multi currency
	 */
	protected void setIsMultiCurrency (boolean mc)
	{
		m_MultiCurrency = mc;
	}	//	setIsMultiCurrency

	/**
	 * 	Is Tax Included
	 *	@return tax incl
	 */
	public boolean isTaxIncluded()
	{
		return m_TaxIncluded;
	}	//	isTaxIncluded

	/**
	 * 	Set Tax Includedy
	 *	@param ti Tax Included
	 */
	protected void setIsTaxIncluded (boolean ti)
	{
		m_TaxIncluded = ti;
	}	//	setIsTaxIncluded

	/**
	 * 	Get C_ConversionType_ID
	 *	@return ConversionType
	 */
	public int getC_ConversionType_ID()
	{
		int index = p_po.get_ColumnIndex("C_ConversionType_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_ConversionType_ID

	/**
	 * 	Get GL_Category_ID
	 *	@return categoory
	 */
	public int getGL_Category_ID()
	{
		return m_GL_Category_ID;
	}	//	getGL_Category_ID

	/**
	 * 	Get GL_Category_ID
	 *	@return categoory
	 */
	public int getGL_Budget_ID()
	{
		int index = p_po.get_ColumnIndex("GL_Budget_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getGL_Budget_ID

	/**
	 * 	Get Accounting Date
	 *	@return currency
	 */
	public Timestamp getDateAcct()
	{
		if (m_DateAcct != null)
			return m_DateAcct;
		int index = p_po.get_ColumnIndex("DateAcct");
		if (index != -1)
		{
			m_DateAcct = (Timestamp)p_po.get_Value(index);
			if (m_DateAcct != null)
				return m_DateAcct;
		}
		throw new IllegalStateException("No DateAcct");
	}	//	getDateAcct

	/**
	 * 	Set Date Acct
	 *	@param da accounting date
	 */
	protected void setDateAcct (Timestamp da)
	{
		m_DateAcct = da;
	}	//	setDateAcct

	/**
	 * 	Get Document Date
	 *	@return currency
	 */
	public Timestamp getDateDoc()
	{
		if (m_DateDoc != null)
			return m_DateDoc;
		int index = p_po.get_ColumnIndex("DateDoc");
		if (index == -1)
			index = p_po.get_ColumnIndex("MovementDate");
		if (index != -1)
		{
			m_DateDoc = (Timestamp)p_po.get_Value(index);
			if (m_DateDoc != null)
				return m_DateDoc;
		}
		throw new IllegalStateException("No DateDoc");
	}	//	getDateDoc

	/**
	 * 	Set Date Doc
	 *	@param dd document date
	 */
	protected void setDateDoc (Timestamp dd)
	{
		m_DateDoc = dd;
	}	//	setDateDoc

	/**
	 * 	Is Document Posted
	 *	@return true if posted
	 */
	public boolean isPosted()
	{
		int index = p_po.get_ColumnIndex("Posted");
		if (index != -1)
		{
			Object posted = p_po.get_Value(index);
			if (posted instanceof Boolean)
				return ((Boolean)posted).booleanValue();
			if (posted instanceof String)
				return "Y".equals(posted);
		}
		throw new IllegalStateException("No Posted");
	}	//	isPosted

	/**
	 * 	Is Sales Trx
	 *	@return true if posted
	 */
	public boolean isSOTrx()
	{
		int index = p_po.get_ColumnIndex("IsSOTrx");
		if (index == -1)
			index = p_po.get_ColumnIndex("IsReceipt");
		if (index != -1)
		{
			Object posted = p_po.get_Value(index);
			if (posted instanceof Boolean)
				return ((Boolean)posted).booleanValue();
			if (posted instanceof String)
				return "Y".equals(posted);
		}
		return false;
	}	//	isSOTrx

	/**
	 * 	Is Return Trx
	 *	@return true if this is a return transaction
	 */
	public boolean isReturnTrx()
	{
		int index = p_po.get_ColumnIndex("IsReturnTrx");
		if (index != -1)
		{
			Object isReturnTrx = p_po.get_Value(index);
			if (isReturnTrx instanceof Boolean)
				return ((Boolean)isReturnTrx).booleanValue();
			if (isReturnTrx instanceof String)
				return "Y".equals(isReturnTrx);
		}
		return false;
	}	//	isReturnTrx

	/**
	 * 	Get C_DocType_ID
	 *	@return DocType
	 */
	public int getC_DocType_ID()
	{
		int index = p_po.get_ColumnIndex("C_DocType_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			//	DocType does not exist - get DocTypeTarget
			if ((ii != null) && (ii.intValue() == 0))
			{
				index = p_po.get_ColumnIndex("C_DocTypeTarget_ID");
				if (index != -1)
					ii = (Integer)p_po.get_Value(index);
			}
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_DocType_ID


	/**
	 * 	Get header level C_Charge_ID
	 *	@return Charge
	 */
	public int getC_Charge_ID()
	{
		int index = p_po.get_ColumnIndex("C_Charge_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_Charge_ID

	/**
	 * 	Get SalesRep_ID
	 *	@return SalesRep
	 */
	public int getSalesRep_ID()
	{
		int index = p_po.get_ColumnIndex("SalesRep_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getSalesRep_ID

	/**
	 * 	Get C_BankAccount_ID
	 *	@return BankAccount
	 */
	public int getC_BankAccount_ID()
	{
		if (m_C_BankAccount_ID == -1)
		{
			int index = p_po.get_ColumnIndex("C_BankAccount_ID");
			if (index != -1)
			{
				Integer ii = (Integer)p_po.get_Value(index);
				if (ii != null)
					m_C_BankAccount_ID = ii.intValue();
			}
			if (m_C_BankAccount_ID == -1)
				m_C_BankAccount_ID = 0;
		}
		return m_C_BankAccount_ID;
	}	//	getC_BankAccount_ID

	/**
	 * 	Set C_BankAccount_ID
	 *	@param C_BankAccount_ID bank acct
	 */
	protected void setC_BankAccount_ID (int C_BankAccount_ID)
	{
		m_C_BankAccount_ID = C_BankAccount_ID;
	}	//	setC_BankAccount_ID

	/**
	 * 	Get C_CashBook_ID
	 *	@return CashBook
	 */
	public int getC_CashBook_ID()
	{
		if (m_C_CashBook_ID == -1)
		{
			int index = p_po.get_ColumnIndex("C_CashBook_ID");
			if (index != -1)
			{
				Integer ii = (Integer)p_po.get_Value(index);
				if (ii != null)
					m_C_CashBook_ID = ii.intValue();
			}
			if (m_C_CashBook_ID == -1)
				m_C_CashBook_ID = 0;
		}
		return m_C_CashBook_ID;
	}	//	getC_CashBook_ID

	/**
	 * 	Get M_WorkOrderClass_ID
	 *	@return WorkOrderClass
	 */
	public int getM_WorkOrder_ID()
	{
		if (m_workorder_id == -1)
		{
			int index = p_po.get_ColumnIndex("M_WorkOrder_ID");
			if (index != -1)
			{
				Integer ii = (Integer)p_po.get_Value(index);
				if (ii != null)
					m_workorder_id = ii.intValue();
			}
			if (m_workorder_id == -1)
				m_workorder_id = 0;
		}
		return m_workorder_id;
	}	//	getC_CashBook_ID

	/**
	 * 	Set C_CashBook_ID
	 *	@param C_CashBook_ID cash book
	 */
	protected void setC_CashBook_ID (int C_CashBook_ID)
	{
		m_C_CashBook_ID = C_CashBook_ID;
	}	//	setC_CashBook_ID

	/**
	 * 	Get M_Warehouse_ID
	 *	@return Warehouse
	 */
	public int getM_Warehouse_ID()
	{
		int index = p_po.get_ColumnIndex("M_Warehouse_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getM_Warehouse_ID


	/**
	 * 	Get C_BPartner_ID
	 *	@return BPartner
	 */
	public int getC_BPartner_ID()
	{
		if (m_C_BPartner_ID == -1)
		{
			int index = p_po.get_ColumnIndex("C_BPartner_ID");
			if (index != -1)
			{
				Integer ii = (Integer)p_po.get_Value(index);
				if (ii != null)
					m_C_BPartner_ID = ii.intValue();
			}
			if (m_C_BPartner_ID == -1)
				m_C_BPartner_ID = 0;
		}
		return m_C_BPartner_ID;
	}	//	getC_BPartner_ID

	/**
	 * 	Set C_BPartner_ID
	 *	@param C_BPartner_ID bp
	 */
	protected void setC_BPartner_ID (int C_BPartner_ID)
	{
		m_C_BPartner_ID = C_BPartner_ID;
	}	//	setC_BPartner_ID

	/**
	 * 	Get C_BPartner_Location_ID
	 *	@return BPartner Location
	 */
	public int getC_BPartner_Location_ID()
	{
		int index = p_po.get_ColumnIndex("C_BPartner_Location_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_BPartner_Location_ID

	/**
	 * 	Get C_Project_ID
	 *	@return Project
	 */
	public int getC_Project_ID()
	{
		int index = p_po.get_ColumnIndex("C_Project_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_Project_ID

	/**
	 * 	Get C_SalesRegion_ID
	 *	@return Sales Region
	 */
	public int getC_SalesRegion_ID()
	{
		int index = p_po.get_ColumnIndex("C_SalesRegion_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_SalesRegion_ID

	/**
	 * 	Get C_SalesRegion_ID
	 *	@return Sales Region
	 */
	public int getBP_C_SalesRegion_ID()
	{
		if (m_BP_C_SalesRegion_ID == -1)
		{
			int index = p_po.get_ColumnIndex("C_SalesRegion_ID");
			if (index != -1)
			{
				Integer ii = (Integer)p_po.get_Value(index);
				if (ii != null)
					m_BP_C_SalesRegion_ID = ii.intValue();
			}
			if (m_BP_C_SalesRegion_ID == -1)
				m_BP_C_SalesRegion_ID = 0;
		}
		return m_BP_C_SalesRegion_ID;
	}	//	getBP_C_SalesRegion_ID

	/**
	 * 	Set C_SalesRegion_ID
	 *	@param C_SalesRegion_ID id
	 */
	protected void setBP_C_SalesRegion_ID (int C_SalesRegion_ID)
	{
		m_BP_C_SalesRegion_ID = C_SalesRegion_ID;
	}	//	setBP_C_SalesRegion_ID

	/**
	 * 	Get C_Activity_ID
	 *	@return Activity
	 */
	public int getC_Activity_ID()
	{
		int index = p_po.get_ColumnIndex("C_Activity_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_Activity_ID

	/**
	 * 	Get C_Campaign_ID
	 *	@return Campaign
	 */
	public int getC_Campaign_ID()
	{
		int index = p_po.get_ColumnIndex("C_Campaign_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_Campaign_ID

	/**
	 * 	Get M_Product_ID
	 *	@return Product
	 */
	public int getM_Product_ID()
	{
		int index = p_po.get_ColumnIndex("M_Product_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getM_Product_ID

	/**
	 * 	Get AD_OrgTrx_ID
	 *	@return Trx Org
	 */
	public int getAD_OrgTrx_ID()
	{
		int index = p_po.get_ColumnIndex("AD_OrgTrx_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getAD_OrgTrx_ID

	/**
	 * 	Get C_LocFrom_ID
	 *	@return loc from
	 */
	public int getC_LocFrom_ID()
	{
		return m_C_LocFrom_ID;
	}	//	getC_LocFrom_ID

	/**
	 * 	Set C_LocFrom_ID
	 *	@param C_LocFrom_ID loc from
	 */
	protected void setC_LocFrom_ID(int C_LocFrom_ID)
	{
		m_C_LocFrom_ID = C_LocFrom_ID;
	}	//	setC_LocFrom_ID

	/**
	 * 	Get C_LocTo_ID
	 *	@return loc to
	 */
	public int getC_LocTo_ID()
	{
		return m_C_LocTo_ID;
	}	//	getC_LocTo_ID

	/**
	 * 	Set C_LocTo_ID
	 *	@param C_LocTo_ID loc to
	 */
	protected void setC_LocTo_ID(int C_LocTo_ID)
	{
		m_C_LocTo_ID = C_LocTo_ID;
	}	//	setC_LocTo_ID

	/**
	 * 	Get User1_ID
	 *	@return User 1
	 */
	public int getUser1_ID()
	{
		int index = p_po.get_ColumnIndex("User1_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getUser1_ID

	/**
	 * 	Get User2_ID
	 *	@return User 2
	 */
	public int getUser2_ID()
	{
		int index = p_po.get_ColumnIndex("User2_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getUser2_ID

	/**
	 * 	Get User Defined value
	 * 	@param ColumnName column
	 *	@return User defined
	 */
	public int getValue (String ColumnName)
	{
		int index = p_po.get_ColumnIndex(ColumnName);
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getValue


	/*************************************************************************/
	//  To be overwritten by Subclasses

	/**
	 *  Load Document Details
	 *  @return error message or null
	 */
	public abstract String loadDocumentDetails();

	/**
	 *  Get Source Currency Balance - subtracts line (and tax) amounts from total - no rounding
	 *  @return positive amount, if total header is bigger than lines
	 */
	public abstract BigDecimal getBalance();

	/**
	 *  Create Facts (the accounting logic)
	 *  @param as accounting schema
	 *  @return Facts
	 */
	public abstract ArrayList<Fact> createFacts (MAcctSchema as);

}   //  Doc
