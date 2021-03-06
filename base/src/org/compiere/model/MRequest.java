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

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.logging.*;

import org.compiere.api.*;
import org.compiere.framework.*;
import org.compiere.util.*;

/**
 * 	Request Model
 *
 *  @author Jorg Janke
 *  @version $Id: MRequest.java,v 1.2 2006/07/30 00:51:03 jjanke Exp $
 */
public class MRequest extends X_R_Request
{
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;


	/**
	 * 	Get Request ID from mail text
	 *	@param mailText mail text
	 *	@return ID if it contains request tag otherwise 0
	 */
	public static int getR_Request_ID (String mailText)
	{
		if (mailText == null)
			return 0;
		int indexStart = mailText.indexOf(TAG_START);
		if (indexStart == -1)
			return 0;
		int indexEnd = mailText.indexOf(TAG_END, indexStart);
		if (indexEnd == -1)
			return 0;
		//
		indexStart += 5;
		String idString = mailText.substring(indexStart, indexEnd);
		int R_Request_ID = 0;
		try
		{
			R_Request_ID = Integer.parseInt(idString);
		}
		catch (Exception e)
		{
			s_log.severe ("Cannot parse " + idString);
		}
		return R_Request_ID;
	}	//	getR_Request_ID

	/**	Static Logger					*/
	private static CLogger	s_log	= CLogger.getCLogger (MRequest.class);
	/** Request Tag Start				*/
	private static final String		TAG_START = "[Req#";
	/** Request Tag End					*/
	private static final String		TAG_END = "#ID]";

	/** Mail Trailer Server Info		*/
	private String	m_serverAddress = null;
	/** Mail Trailer Server Info		*/
	private int		m_W_Store_ID = 0;
	/** Mail Trailer Server Info		*/
	private int		m_AD_Menu_ID = 0;


	/**************************************************************************
	 * 	Constructor
	 * 	@param ctx context
	 * 	@param R_Request_ID request or 0 for new
	 *	@param trx transaction
	 */
	public MRequest(Ctx ctx, int R_Request_ID, Trx trx)
	{
		super (ctx, R_Request_ID, trx);
		if (R_Request_ID == 0)
		{
			setDueType (DUETYPE_Due);
		//  setSalesRep_ID (0);
		//	setDocumentNo (null);
			setConfidentialType (CONFIDENTIALTYPE_PublicInformation);	// A
			setConfidentialTypeEntry (CONFIDENTIALTYPEENTRY_PublicInformation);	// A
			setProcessed (false);
			setRequestAmt (Env.ZERO);
			setPriorityUser (PRIORITY_Low);
		//  setR_RequestType_ID (0);
		//  setSummary (null);
			setIsEscalated (false);
			setIsSelfService (false);
			setIsInvoiced (false);
		}
	}	//	MRequest

	/**
	 * 	SelfService Constructor
	 * 	@param ctx context
	 * 	@param SalesRep_ID SalesRep
	 * 	@param R_RequestType_ID request type
	 * 	@param Summary summary
	 * 	@param isSelfService self service
	 *	@param trx transaction
	 */
	public MRequest (Ctx ctx, int SalesRep_ID,
		int R_RequestType_ID, String Summary, boolean isSelfService, Trx trx)
	{
		this(ctx, 0, trx);
		set_Value ("SalesRep_ID", Integer.valueOf(SalesRep_ID));	//	could be 0
		set_Value ("R_RequestType_ID", Integer.valueOf(R_RequestType_ID));
		setSummary (Summary);
		setIsSelfService(isSelfService);
		getRequestType();
		if (m_requestType != null)
		{
			String ct = m_requestType.getConfidentialType();
			if (ct != null)
			{
				setConfidentialType (ct);
				setConfidentialTypeEntry (ct);
			}
		}
	}	//	MRequest

	/**
	 * 	Load Constructor
	 *	@param ctx context
	 *	@param rs result set
	 *	@param trx transaction
	 */
	public MRequest (Ctx ctx, ResultSet rs, Trx trx)
	{
		super(ctx, rs, trx);
	}	//	MRequest

	/**
	 * 	Import Constructor
	 *	@param imp import
	 */
	public MRequest (X_I_Request imp)
	{
		this (imp.getCtx(), 0, imp.get_Trx());
		PO.copyValues(imp, this, imp.getAD_Client_ID(), imp.getAD_Org_ID());
	}	//	MRequest

	/** Request Type				*/
	private MRequestType	m_requestType = null;
	/**	Changed						*/
	private boolean			m_changed = false;
	/**	BPartner					*/
	private MBPartner		m_partner = null;
	/** User/Contact				*/
	private MUser			m_user = null;
	/** List of EMail Notices		*/
	private StringBuffer	m_emailTo = new StringBuffer();

	/** Separator line				*/
	public static final String	SEPARATOR =
		"\n---------.----------.----------.----------.----------.----------\n";

	private int m_success = 0;
	private int m_failure = 0;
	private int m_notices = 0;


	/**************************************************************************
	 * 	Set Default Request Type.
	 */
	public void setR_RequestType_ID ()
	{
		m_requestType = MRequestType.getDefault(getCtx());
		if (m_requestType == null)
			log.warning("No default found");
		else
			super.setR_RequestType_ID(m_requestType.getR_RequestType_ID());
	}	//	setR_RequestType_ID

	/**
	 * 	Set Default Request Status.
	 */
	public void setR_Status_ID ()
	{
		MStatus status = MStatus.getDefault(getCtx(), getR_RequestType_ID());
		if (status == null)
		{
			log.warning("No default found");
			if (getR_Status_ID() != 0)
				setR_Status_ID(0);
		}
		else
			setR_Status_ID(status.getR_Status_ID());
	}	//	setR_Status_ID

	/**
	 * 	Add To Result
	 * 	@param Result
	 */
	public void addToResult (String Result)
	{
		String oldResult = getResult();
		if (Result == null || Result.length() == 0)
			;
		else if (oldResult == null || oldResult.length() == 0)
			setResult (Result);
		else
			setResult (oldResult + "\n-\n" + Result);
	}	//	addToResult

	/**
	 * 	Set DueType based on Date Next Action
	 */
	public void setDueType()
	{
		Timestamp due = getDateNextAction();
		if (due == null)
			return;
		//
		int dueDateTolerance = getRequestType().getDueDateTolerance();
		Timestamp overdue = TimeUtil.addDays(due, dueDateTolerance);
		Timestamp now = new Timestamp (System.currentTimeMillis());
		//
		String DueType = DUETYPE_Due;
		if (now.before(due))
			DueType = DUETYPE_Scheduled;
		else if (now.after(overdue))
			DueType = DUETYPE_Overdue;
		super.setDueType(DueType);
	}	//	setDueType


	/**************************************************************************
	 * 	Get Action History
	 *	@return array of actions
	 */
	public MRequestAction[] getActions()
	{
		String sql = "SELECT * FROM R_RequestAction "
			+ "WHERE R_Request_ID=? "
			+ "ORDER BY Created DESC";
		ArrayList<MRequestAction> list = new ArrayList<MRequestAction>();
		PreparedStatement pstmt = null;
		try
		{
			pstmt = DB.prepareStatement(sql, get_Trx());
			pstmt.setInt(1, getR_Request_ID());
			ResultSet rs = pstmt.executeQuery();
			while (rs.next())
				list.add(new MRequestAction(getCtx(), rs, get_Trx()));
			rs.close();
			pstmt.close();
			pstmt = null;
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, sql, e);
		}
		try
		{
			if (pstmt != null)
				pstmt.close();
			pstmt = null;
		}
		catch (Exception e)
		{
			pstmt = null;
		}
		//
		MRequestAction[] retValue = new MRequestAction[list.size()];
		list.toArray(retValue);
		return retValue;
	}	//	getActions

	/**
	 * 	Get Updates
	 * 	@param confidentialType maximum confidential type - null = all
	 *	@return updates
	 */
	public MRequestUpdate[] getUpdates(String confidentialType)
	{
		String sql = "SELECT * FROM R_RequestUpdate "
			+ "WHERE R_Request_ID=? "
			+ "ORDER BY Created DESC";
		ArrayList<MRequestUpdate> list = new ArrayList<MRequestUpdate>();
		PreparedStatement pstmt = null;
		try
		{
			pstmt = DB.prepareStatement(sql, get_Trx());
			pstmt.setInt(1, getR_Request_ID());
			ResultSet rs = pstmt.executeQuery();
			while (rs.next())
			{
				MRequestUpdate ru = new MRequestUpdate(getCtx(), rs, get_Trx());
				if (confidentialType != null)
				{
					//	Private only if private
					if (ru.getConfidentialTypeEntry().equals(CONFIDENTIALTYPEENTRY_PrivateInformation)
						&& !confidentialType.equals(CONFIDENTIALTYPEENTRY_PrivateInformation))
						continue;
					//	Internal not if Customer/Public
					if (ru.getConfidentialTypeEntry().equals(CONFIDENTIALTYPEENTRY_Internal)
						&& (confidentialType.equals(CONFIDENTIALTYPEENTRY_PartnerConfidential)
							|| confidentialType.equals(CONFIDENTIALTYPEENTRY_PublicInformation)))
						continue;
					//	No Customer if public
					if (ru.getConfidentialTypeEntry().equals(CONFIDENTIALTYPEENTRY_PartnerConfidential)
						&& confidentialType.equals(CONFIDENTIALTYPEENTRY_PublicInformation))
						continue;
				}
				list.add(ru);
			}
			rs.close();
			pstmt.close();
			pstmt = null;
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, sql, e);
		}
		try
		{
			if (pstmt != null)
				pstmt.close();
			pstmt = null;
		}
		catch (Exception e)
		{
			pstmt = null;
		}
		//
		MRequestUpdate[] retValue = new MRequestUpdate[list.size()];
		list.toArray(retValue);
		return retValue;
	}	//	getUpdates

	/**
	 * 	Get Public Updates
	 *	@return public updates
	 */
	public MRequestUpdate[] getUpdatesPublic()
	{
		return getUpdates(CONFIDENTIALTYPE_PublicInformation);
	}	//	getUpdatesPublic

	/**
	 * 	Get Customer Updates
	 *	@return customer updates
	 */
	public MRequestUpdate[] getUpdatesCustomer()
	{
		return getUpdates(CONFIDENTIALTYPE_PartnerConfidential);
	}	//	getUpdatesCustomer

	/**
	 * 	Get Internal Updates
	 *	@return internal updates
	 */
	public MRequestUpdate[] getUpdatesInternal()
	{
		return getUpdates(CONFIDENTIALTYPE_Internal);
	}	//	getUpdatesInternal

	/**
	 *	Get Request Type
	 *	@return Request Type
	 */
	public MRequestType getRequestType()
	{
		if (m_requestType == null)
		{
			int R_RequestType_ID = getR_RequestType_ID();
			if (R_RequestType_ID == 0)
			{
				setR_RequestType_ID();
				R_RequestType_ID = getR_RequestType_ID();
			}
			m_requestType = MRequestType.get (getCtx(), R_RequestType_ID);
		}
		return m_requestType;
	}	//	getRequestType


	/**
	 *	Get Request Type Text (for jsp)
	 *	@return Request Type Text
	 */
	public String getRequestTypeName()
	{
		if (m_requestType == null)
			getRequestType();
		if (m_requestType == null)
			return "??";
		return m_requestType.getName();
	}	//	getRequestTypeText

	/**
	 * 	Get Request Category
	 *	@return category
	 */
	public MRequestCategory getCategory()
	{
		if (getR_Category_ID() == 0)
			return null;
		return MRequestCategory.get(getCtx(), getR_Category_ID());
	}	//	getCategory

	/**
	 * 	Get Request Category Name
	 *	@return name
	 */
	public String getCategoryName()
	{
		MRequestCategory cat = getCategory();
		if (cat == null)
			return "";
		return cat.getName();
	}	//	getCategoryName

	/**
	 * 	Get Request Group
	 *	@return group
	 */
	public MGroup getGroup()
	{
		if (getR_Group_ID() == 0)
			return null;
		return MGroup.get(getCtx(), getR_Group_ID());
	}	//	getGroup

	/**
	 * 	Get Request Group Name
	 *	@return name
	 */
	public String getGroupName()
	{
		MGroup grp = getGroup();
		if (grp == null)
			return "";
		return grp.getName();
	}	//	getGroupName

	/**
	 * 	Get Status
	 *	@return status
	 */
	public MStatus getStatus()
	{
		if (getR_Status_ID() == 0)
			return null;
		return MStatus.get(getCtx(), getR_Status_ID());
	}	//	getStatus

	/**
	 * 	Get Request Status Name
	 *	@return name
	 */
	public String getStatusName()
	{
		MStatus sta = getStatus();
		if (sta == null)
			return "?";
		return sta.getName();
	}	//	getStatusName

	/**
	 * 	Get Request Resolution
	 *	@return resolution
	 */
	public MResolution getResolution()
	{
		if (getR_Resolution_ID() == 0)
			return null;
		return MResolution.get(getCtx(), getR_Resolution_ID());
	}	//	getResolution

	/**
	 * 	Get Request Resolution Name
	 *	@return name
	 */
	public String getResolutionName()
	{
		MResolution res = getResolution();
		if (res == null)
			return "";
		return res.getName();
	}	//	getResolutionName

	/**
	 * 	Is Overdue
	 *	@return true if overdue
	 */
	public boolean isOverdue()
	{
		return DUETYPE_Overdue.equals(getDueType());
	}	//	isOverdue

	/**
	 * 	Is due
	 *	@return true if due
	 */
	public boolean isDue()
	{
		return DUETYPE_Due.equals(getDueType());
	}	//	isDue

	/**
	 * 	Get DueType Text (for jsp)
	 *	@return text
	 */
	public String getDueTypeText()
	{
		return MRefList.getListName(getCtx(), DUETYPE_AD_Reference_ID, getDueType());
	}	//	getDueTypeText

	/**
	 * 	Get Priority Text (for jsp)
	 *	@return text
	 */
	public String getPriorityText()
	{
		return MRefList.getListName(getCtx(), PRIORITY_AD_Reference_ID, getPriority());
	}	//	getPriorityText

	/**
	 * 	Get Importance Text (for jsp)
	 *	@return text
	 */
	public String getPriorityUserText()
	{
		return MRefList.getListName(getCtx(), PRIORITYUSER_AD_Reference_ID, getPriorityUser());
	}	//	getPriorityUserText

	/**
	 * 	Get Confidential Text (for jsp)
	 *	@return text
	 */
	public String getConfidentialText()
	{
		return MRefList.getListName(getCtx(), CONFIDENTIALTYPE_AD_Reference_ID, getConfidentialType());
	}	//	getConfidentialText

	/**
	 * 	Get Confidential Entry Text (for jsp)
	 *	@return text
	 */
	public String getConfidentialEntryText()
	{
		return MRefList.getListName(getCtx(), CONFIDENTIALTYPEENTRY_AD_Reference_ID, getConfidentialTypeEntry());
	}	//	getConfidentialTextEntry

	/**
	 * 	Set Date Last Alert to today
	 */
	public void setDateLastAlert ()
	{
		super.setDateLastAlert (new Timestamp(System.currentTimeMillis()));
	}	//	setDateLastAlert

	/**
	 * 	Get Sales Rep
	 *	@return Sales Rep User
	 */
	public MUser getSalesRep()
	{
		if (getSalesRep_ID() == 0)
			return null;
		return MUser.get(getCtx(), getSalesRep_ID());
	}	//	getSalesRep

	/**
	 * 	Get Sales Rep Name
	 *	@return Sales Rep User
	 */
	public String getSalesRepName()
	{
		MUser sr = getSalesRep();
		if (sr == null)
			return "n/a";
		return sr.getName();
	}	//	getSalesRepName

	/**
	 * 	Get Name of creator
	 *	@return name
	 */
	public String getCreatedByName()
	{
		MUser user = MUser.get(getCtx(), getCreatedBy());
		return user.getName();
	}	//	getCreatedByName

	/**
	 * 	Get Contact (may be not defined)
	 *	@return Sales Rep User
	 */
	public MUser getUser()
	{
		if (getAD_User_ID() == 0)
			return null;
		if (m_user != null && m_user.getAD_User_ID() != getAD_User_ID())
			m_user = null;
		if (m_user == null)
			m_user = new MUser (getCtx(), getAD_User_ID(), get_Trx());
		return m_user;
	}	//	getUser

	/**
	 * 	Set Business Partner - Callout
	 *	@param oldAD_User_ID old value
	 *	@param newAD_User_ID new value
	 *	@param windowNo window
	 *	@throws Exception
	 */
	@UICallout public void setAD_User_ID (String oldAD_User_ID,
			String newAD_User_ID, int windowNo) throws Exception
	{
		if (newAD_User_ID == null || newAD_User_ID.length() == 0)
			return;
		int AD_User_ID = Integer.parseInt(newAD_User_ID);
		super.setAD_User_ID(AD_User_ID);
		if (AD_User_ID == 0)
			return;

		if(getC_BPartner_ID() == 0)
		{
			MUser user = new MUser (getCtx(), AD_User_ID, null);
			setC_BPartner_ID(user.getC_BPartner_ID());
		}

	}	//	setR_MailText_ID


	/**
	 * 	Get BPartner (may be not defined)
	 *	@return Sales Rep User
	 */
	public MBPartner getBPartner()
	{
		if (getC_BPartner_ID() == 0)
			return null;
		if (m_partner != null && m_partner.getC_BPartner_ID() != getC_BPartner_ID())
			m_partner = null;
		if (m_partner == null)
			m_partner = new MBPartner (getCtx(), getC_BPartner_ID(), get_Trx());
		return m_partner;
	}	//	getBPartner



	/**
	 * 	Web Can Update Request
	 *	@return true if Web can update
	 */
	public boolean isWebCanUpdate()
	{
		if (isProcessed())
			return false;
		if (getR_Status_ID() == 0)
			setR_Status_ID();
		if (getR_Status_ID() == 0)
			return false;
		MStatus status = MStatus.get(getCtx(), getR_Status_ID());
		if (status == null)
			return false;
		return status.isWebCanUpdate();
	}	//	isWebCanUpdate


	/**
	 * 	Set Priority
	 */
	private void setPriority()
	{
		if (getPriorityUser() == null)
			setPriorityUser(PRIORITYUSER_Low);
		//
		if (getBPartner() != null)
		{
			MBPGroup bpg = MBPGroup.get(getCtx(), getBPartner().getC_BP_Group_ID());
			String prioBase = bpg.getPriorityBase();
			if (prioBase != null && !prioBase.equals(X_C_BP_Group.PRIORITYBASE_Same))
			{
				char targetPrio = getPriorityUser().charAt(0);
				if (prioBase.equals(X_C_BP_Group.PRIORITYBASE_Lower))
					targetPrio += 2;
				else
					targetPrio -= 2;
				if (targetPrio < PRIORITY_High.charAt(0))	//	1
					targetPrio = PRIORITY_High.charAt(0);
				if (targetPrio > PRIORITY_Low.charAt(0))	//	9
					targetPrio = PRIORITY_Low.charAt(0);
				if (getPriority() == null)
					setPriority(String.valueOf(targetPrio));
				else	//	previous priority
				{
					if (targetPrio < getPriority().charAt(0))
						setPriority(String.valueOf(targetPrio));
				}
			}
		}
		//	Same if nothing else
		if (getPriority() == null)
			setPriority(getPriorityUser());
	}	//	setPriority

	/**
	 * 	Set Confidential Type Entry
	 *	@param ConfidentialTypeEntry confidentiality
	 */
	@Override
	public void setConfidentialTypeEntry (String ConfidentialTypeEntry)
	{
		if (ConfidentialTypeEntry == null)
			ConfidentialTypeEntry = getConfidentialType();
		//
		if (CONFIDENTIALTYPE_Internal.equals(getConfidentialType()))
			super.setConfidentialTypeEntry (CONFIDENTIALTYPE_Internal);
		else if (CONFIDENTIALTYPE_PrivateInformation.equals(getConfidentialType()))
		{
			if (CONFIDENTIALTYPE_Internal.equals(ConfidentialTypeEntry)
				|| CONFIDENTIALTYPE_PrivateInformation.equals(ConfidentialTypeEntry))
				super.setConfidentialTypeEntry (ConfidentialTypeEntry);
			else
				super.setConfidentialTypeEntry (CONFIDENTIALTYPE_PrivateInformation);
		}
		else if (CONFIDENTIALTYPE_PartnerConfidential.equals(getConfidentialType()))
		{
			if (CONFIDENTIALTYPE_Internal.equals(ConfidentialTypeEntry)
				|| CONFIDENTIALTYPE_PrivateInformation.equals(ConfidentialTypeEntry)
				|| CONFIDENTIALTYPE_PartnerConfidential.equals(ConfidentialTypeEntry))
				super.setConfidentialTypeEntry (ConfidentialTypeEntry);
			else
				super.setConfidentialTypeEntry (CONFIDENTIALTYPE_PartnerConfidential);
		}
		else if (CONFIDENTIALTYPE_PublicInformation.equals(getConfidentialType()))
			super.setConfidentialTypeEntry (ConfidentialTypeEntry);
	}	//	setConfidentialTypeEntry

	/**
	 * 	Web Update
	 *	@param result result
	 *	@return true if updated
	 */
	public boolean webUpdate (String result)
	{
		MStatus status = MStatus.get(getCtx(), getR_Status_ID());
		if (!status.isWebCanUpdate())
			return false;
		if (status.getUpdate_Status_ID() > 0)
			setR_Status_ID(status.getUpdate_Status_ID());
		setResult(result);
		return true;
	}	//	webUpdate

	/**
	 * 	String Representation
	 *	@return info
	 */
	@Override
	public String toString ()
	{
		StringBuffer sb = new StringBuffer ("MRequest[");
		sb.append (get_ID()).append ("-").append(getDocumentNo()).append ("]");
		return sb.toString ();
	}	//	toString

	/**
	 * 	Create PDF
	 *	@return pdf or null
	 */
	public File createPDF ()
	{
		try
		{
			File temp = File.createTempFile(get_TableName()+get_ID()+"_", ".pdf");
			return createPDF (temp);
		}
		catch (Exception e)
		{
			log.severe("Could not create PDF - " + e.getMessage());
		}
		return null;
	}	//	getPDF

	/**
	 * 	Create PDF file
	 *	@param file output file
	 *	@return file if success
	 */
	public File createPDF (File file)
	{
	//	ReportEngine re = ReportEngine.get (getCtx(), ReportEngine.INVOICE, getC_Invoice_ID());
	//	if (re == null)
			return null;
	//	return re.getPDF(file);
	}	//	createPDF

	/**************************************************************************
	 * 	Before Save
	 *	@param newRecord new
	 *	@return true
	 */
	@Override
	protected boolean beforeSave (boolean newRecord)
	{
		//	Request Type
		getRequestType();
		if (newRecord || is_ValueChanged("R_RequestType_ID"))
		{
			if (m_requestType != null)
			{
				if (isInvoiced() != m_requestType.isInvoiced())
					setIsInvoiced(m_requestType.isInvoiced());
				if (getDateNextAction() == null && m_requestType.getAutoDueDateDays() > 0)
					setDateNextAction(TimeUtil.addDays(new Timestamp(System.currentTimeMillis()),
						m_requestType.getAutoDueDateDays()));
			}
			//	Is Status Valid
			if (getR_Status_ID() != 0)
			{
				MStatus sta = MStatus.get(getCtx(), getR_Status_ID());
				MRequestType rt = MRequestType.get(getCtx(), getR_RequestType_ID());
				if (sta.getR_StatusCategory_ID() != rt.getR_StatusCategory_ID())
					setR_Status_ID();	//	set to default
			}
		}

		//	Request Status
		if (getR_Status_ID() == 0)
			setR_Status_ID();
		//	Validate/Update Due Type
		setDueType();
		MStatus status = MStatus.get(getCtx(), getR_Status_ID());
		//	Close/Open
		if (status != null)
		{
			if (status.isOpen())
			{
				if (getStartDate() == null)
					setStartDate (new Timestamp(System.currentTimeMillis()));
				if (getCloseDate() != null)
					setCloseDate(null);
			}
			if (status.isClosed()
				&& getCloseDate() == null)
				setCloseDate(new Timestamp(System.currentTimeMillis()));
			if (status.isFinalClose())
				setProcessed(true);
		}

		//	Confidential Info
		if (getConfidentialType() == null)
		{
			getRequestType();
			if (m_requestType != null)
			{
				String ct = m_requestType.getConfidentialType();
				if (ct != null)
					setConfidentialType (ct);
			}
			if (getConfidentialType() == null)
				setConfidentialType(CONFIDENTIALTYPEENTRY_PublicInformation);
		}
		if (getConfidentialTypeEntry() == null)
			setConfidentialTypeEntry(getConfidentialType());
		else
			setConfidentialTypeEntry(getConfidentialTypeEntry());

		//	Importance / Priority
		setPriority();

		//	New
		if (newRecord)
			return true;

		//	Change Log
		m_changed = false;
		ArrayList<String> sendInfo = new ArrayList<String>();
		MRequestAction ra = new MRequestAction(this, false);
		//
		if (checkChange(ra, "R_RequestType_ID"))
			sendInfo.add("R_RequestType_ID");
		if (checkChange(ra, "R_Group_ID"))
			sendInfo.add("R_Group_ID");
		if (checkChange(ra, "R_Category_ID"))
			sendInfo.add("R_Category_ID");
		if (checkChange(ra, "R_Status_ID"))
			sendInfo.add("R_Status_ID");
		if (checkChange(ra, "R_Resolution_ID"))
			sendInfo.add("R_Resolution_ID");
		//
		if (checkChange(ra, "SalesRep_ID"))
		{
			//	Sender
			int AD_User_ID = getCtx().getAD_User_ID();
			if (AD_User_ID == 0)
				AD_User_ID = getUpdatedBy();
			//	Old
			Object oo = get_ValueOld("SalesRep_ID");
			int oldSalesRep_ID = 0;
			if (oo instanceof Integer)
				oldSalesRep_ID = ((Integer)oo).intValue();
			if (oldSalesRep_ID != 0)
			{
				//  RequestActionTransfer - Request {0} was transfered by {1} from {2} to {3}
				Object[] args = new Object[] {getDocumentNo(),
					MUser.getNameOfUser(AD_User_ID),
					MUser.getNameOfUser(oldSalesRep_ID),
					MUser.getNameOfUser(getSalesRep_ID())
					};
				String msg = Msg.getMsg(getCtx(), "RequestActionTransfer", args);
				addToResult(msg);
				sendInfo.add("SalesRep_ID");
			}
		}
		checkChange(ra, "AD_Role_ID");
		//
		checkChange(ra, "Priority");
		if (checkChange(ra, "PriorityUser"))
			sendInfo.add("PriorityUser");
		if (checkChange(ra, "IsEscalated"))
			sendInfo.add("IsEscalated");
		//
		checkChange(ra, "ConfidentialType");
		checkChange(ra, "Summary");
		checkChange(ra, "IsSelfService");
		checkChange(ra, "C_BPartner_ID");
		checkChange(ra, "AD_User_ID");
		checkChange(ra, "C_Project_ID");
		checkChange(ra, "A_Asset_ID");
		checkChange(ra, "C_Order_ID");
		checkChange(ra, "C_Invoice_ID");
		checkChange(ra, "M_Product_ID");
		checkChange(ra, "C_Payment_ID");
		checkChange(ra, "M_InOut_ID");
	//	checkChange(ra, "C_Campaign_ID");
	//	checkChange(ra, "RequestAmt");
		checkChange(ra, "IsInvoiced");
		checkChange(ra, "C_Activity_ID");
		checkChange(ra, "DateNextAction");
		checkChange(ra, "M_ProductSpent_ID");
		checkChange(ra, "QtySpent");
		checkChange(ra, "QtyInvoiced");
		checkChange(ra, "StartDate");
		checkChange(ra, "CloseDate");
		checkChange(ra, "TaskStatus");
		checkChange(ra, "DateStartPlan");
		checkChange(ra, "DateCompletePlan");
		//
		if (m_changed)
			ra.save();

		//	Current Info
		MRequestUpdate update = new MRequestUpdate(this);
		if (update.isNewInfo())
			update.save();
		else
			update = null;
		//
		m_emailTo = new StringBuffer();
		if (update != null || sendInfo.size() > 0)
		{
			sendNotices(sendInfo);

			//	Update
			if(ra != null)
				setDateLastAction(ra.getCreated());
			setLastResult(getResult());
			setDueType();
			//	Reset
			setConfidentialTypeEntry (getConfidentialType());
			setStartDate(null);
			setEndTime(null);
			setR_StandardResponse_ID(0);
			setR_MailText_ID(0);
			setResult(null);
		//	setQtySpent(null);
		//	setQtyInvoiced(null);
		}
		return true;
	}	//	beforeSave

	/**
	 * 	Check for changes
	 *	@param ra request action
	 *	@param columnName column
	 *	@return true if changes
	 */
	private boolean checkChange (MRequestAction ra, String columnName)
	{
		if (is_ValueChanged(columnName))
		{
			Object value = get_ValueOld(columnName);
			if (value == null)
				ra.addNullColumn(columnName);
			else
				ra.set_ValueNoCheck(columnName, value);
			m_changed = true;
			return true;
		}
		return false;
	}	//	checkChange

	/**
	 * 	Set SalesRep_ID
	 *	@param SalesRep_ID id
	 */
	@Override
	public void setSalesRep_ID (int SalesRep_ID)
	{
		if (SalesRep_ID != 0)
			super.setSalesRep_ID (SalesRep_ID);
		else if (getSalesRep_ID() != 0)
			log.warning("Ignored - Tried to set SalesRep_ID to 0 from " + getSalesRep_ID());
	}	//	setSalesRep_ID


	/**
	 * 	Set MailText - Callout
	 *	@param oldR_MailText_ID old value
	 *	@param newR_MailText_ID new value
	 *	@param windowNo window
	 *	@throws Exception
	 */
	@UICallout public void setR_MailText_ID (String oldR_MailText_ID,
			String newR_MailText_ID, int windowNo) throws Exception
	{
		if (newR_MailText_ID == null || newR_MailText_ID.length() == 0)
			return;
		int R_MailText_ID = Integer.parseInt(newR_MailText_ID);
		super.setR_MailText_ID(R_MailText_ID);
		if (R_MailText_ID == 0)
			return;

		MMailText mt = new MMailText(getCtx(), R_MailText_ID, null);
		if (mt.get_ID() == R_MailText_ID)
		{
			String txt = mt.getMailText();
			txt = Env.parseContext(getCtx(), windowNo, txt, false, true);
			setResult(txt);
		}
	}	//	setR_MailText_ID

	/**
	 * 	Set Standard Response - Callout
	 *	@param oldR_StandardResponse_ID old value
	 *	@param newR_StandardResponse_ID new value
	 *	@param windowNo window
	 *	@throws Exception
	 */
	@UICallout public void setR_StandardResponse_ID (String oldR_StandardResponse_ID,
			String newR_StandardResponse_ID, int windowNo) throws Exception
	{
		if (newR_StandardResponse_ID == null || newR_StandardResponse_ID.length() == 0)
			return;
		int R_StandardResponse_ID = Integer.parseInt(newR_StandardResponse_ID);
		super.setR_StandardResponse_ID(R_StandardResponse_ID);
		if (R_StandardResponse_ID == 0)
			return;

		MStandardResponse sr = new MStandardResponse(getCtx(), R_StandardResponse_ID, null);
		if (sr.get_ID() == R_StandardResponse_ID)
		{
			String txt = sr.getResponseText();
			txt = Env.parseContext(getCtx(), windowNo, txt, false, true);
			setResult(txt);
		}
	}	//	setR_StandardResponse_ID

	/**
	 * 	Set Request Type - Callout
	 *	@param oldR_RequestType_ID old value
	 *	@param newR_RequestType_ID new value
	 *	@param windowNo window
	 *	@throws Exception
	 */
	@UICallout public void setR_RequestType_ID (String oldR_RequestType_ID,
			String newR_RequestType_ID, int windowNo) throws Exception
	{
		if (newR_RequestType_ID == null || newR_RequestType_ID.length() == 0)
			return;
		int R_RequestType_ID = Integer.parseInt(newR_RequestType_ID);
		super.setR_RequestType_ID(R_RequestType_ID);
		if (R_RequestType_ID == 0)
			return;

		MRequestType rt = MRequestType.get(getCtx(), R_RequestType_ID);
		int R_Status_ID = rt.getDefaultR_Status_ID();
		setR_Status_ID(R_Status_ID);
	}	//	setR_RequestType_ID


	/**
	 * 	After Save
	 *	@param newRecord new
	 *	@param success success
	 *	@return success
	 */
	@Override
	protected boolean afterSave (boolean newRecord, boolean success)
	{
		if (!success)
			return success;

		//	Create Update
		if (newRecord && getResult() != null)
		{
			MRequestUpdate update = new MRequestUpdate(this);
			update.save();
		}
		//	Initial Mail
		if (newRecord)
			sendNotices(new ArrayList<String>());

		//	ChangeRequest - created in Request Processor
		if (getM_ChangeRequest_ID() != 0
			&& is_ValueChanged("R_Group_ID"))	//	different ECN assignment?
		{
			int oldID = get_ValueOldAsInt("R_Group_ID");
			if (getR_Group_ID() == 0)
				setM_ChangeRequest_ID(0);	//	not effective as in afterSave
			else
			{
				MGroup oldG = MGroup.get(getCtx(), oldID);
				MGroup newG = MGroup.get(getCtx(), getR_Group_ID());
				if (oldG.getM_BOM_ID() != newG.getM_BOM_ID()
					|| oldG.getM_ChangeNotice_ID() != newG.getM_ChangeNotice_ID())
				{
					MChangeRequest ecr = new MChangeRequest(getCtx(), getM_ChangeRequest_ID(), get_Trx());
					if (!ecr.isProcessed()
						|| ecr.getM_FixChangeNotice_ID() == 0)
					{
						ecr.setM_BOM_ID(newG.getM_BOM_ID());
						ecr.setM_ChangeNotice_ID(newG.getM_ChangeNotice_ID());
						ecr.save();
					}
				}
			}
		}

		if (m_emailTo.length() > 0)
			log.saveInfo ("RequestActionEMailOK", m_emailTo.toString());

		return success;
	}	//	afterSave

	/**
	 * 	Send Update EMail/Notices
	 * 	@param list list of changes
	 */
	protected void sendNotices(ArrayList<String> list)
	{
		//	Subject
		String subject = Msg.translate(getCtx(), "R_Request_ID")
			+ " " + Msg.getMsg(getCtx(), "Updated") + ": " + getDocumentNo();
		//	Message
		StringBuffer message = new StringBuffer();
		//		UpdatedBy: Joe
		int UpdatedBy = getCtx().getAD_User_ID();
		MUser from = MUser.get(getCtx(), UpdatedBy);
		if (from != null)
			message.append(Msg.translate(getCtx(), "UpdatedBy")).append(": ")
				.append(from.getName());
		//		LastAction/Created: ...
		if (getDateLastAction() != null)
			message.append("\n").append(Msg.translate(getCtx(), "DateLastAction"))
				.append(": ").append(getDateLastAction());
		else
			message.append("\n").append(Msg.translate(getCtx(), "Created"))
				.append(": ").append(getCreated());
		//	Changes
		for (int i = 0; i < list.size(); i++)
		{
			String columnName = list.get(i);
			message.append("\n").append(Msg.getElement(getCtx(), columnName))
				.append(": ").append(get_DisplayValue(columnName, false))
				.append(" -> ").append(get_DisplayValue(columnName, true));
		}
		//	NextAction
		if (getDateNextAction() != null)
			message.append("\n").append(Msg.translate(getCtx(), "DateNextAction"))
				.append(": ").append(getDateNextAction());
		message.append(SEPARATOR)
			.append(getSummary());
		if (getResult() != null)
			message.append("\n----------\n").append(getResult());
		message.append(getMailTrailer());
		File pdf = createPDF();
		log.finer(message.toString());

		//	Prepare sending Notice/Mail
		MClient client = MClient.get(getCtx(), getAD_Client_ID());
		//	Reset from if external
		if (from.getEMailUser() == null || from.getEMailUserPW() == null)
			from = null;
		m_success = 0;
		m_failure = 0;
		m_notices = 0;

		/** List of users - aviod duplicates	*/
		ArrayList<Integer> userList = new ArrayList<Integer>();
		String sql = "SELECT u.AD_User_ID, u.NotificationType, u.EMail, u.Name, MAX(r.AD_Role_ID) "
			+ "FROM RV_RequestUpdates_Only ru"
			+ " INNER JOIN AD_User u ON (ru.AD_User_ID=u.AD_User_ID)"
			+ " LEFT OUTER JOIN AD_User_Roles r ON (u.AD_User_ID=r.AD_User_ID) "
			+ "WHERE ru.R_Request_ID=? "
			+ "GROUP BY u.AD_User_ID, u.NotificationType, u.EMail, u.Name";
		PreparedStatement pstmt = null;
		try
		{
			pstmt = DB.prepareStatement(sql, (Trx) null);
			pstmt.setInt (1, getR_Request_ID());
			ResultSet rs = pstmt.executeQuery ();
			while (rs.next ())
			{
				int AD_User_ID = rs.getInt(1);
				String NotificationType = rs.getString(2);
				if (NotificationType == null)
					NotificationType = X_AD_User.NOTIFICATIONTYPE_EMail;
				String email = rs.getString(3);
				String Name = rs.getString(4);
				//	Role
				int AD_Role_ID = rs.getInt(5);
				if (rs.wasNull())
					AD_Role_ID = -1;

				//	Don't send mail to oneself
		//		if (AD_User_ID == UpdatedBy)
		//			continue;

				//	No confidential to externals
				if (AD_Role_ID == -1
					&& (getConfidentialTypeEntry().equals(CONFIDENTIALTYPE_Internal)
						|| getConfidentialTypeEntry().equals(CONFIDENTIALTYPE_PrivateInformation)))
					continue;

				if (X_AD_User.NOTIFICATIONTYPE_None.equals(NotificationType))
				{
					log.config("Opt out: " + Name);
					continue;
				}
				if ((X_AD_User.NOTIFICATIONTYPE_EMail.equals(NotificationType)
					|| X_AD_User.NOTIFICATIONTYPE_EMailPlusNotice.equals(NotificationType))
					&& (email == null || email.length() == 0))
				{
					if (AD_Role_ID >= 0)
						NotificationType = X_AD_User.NOTIFICATIONTYPE_Notice;
					else
					{
						log.config("No EMail: " + Name);
						continue;
					}
				}
				if (X_AD_User.NOTIFICATIONTYPE_Notice.equals(NotificationType)
					&& AD_Role_ID >= 0)
				{
					log.config("No internal User: " + Name);
					continue;
				}

				//	Check duplicate receivers
				Integer ii = Integer.valueOf (AD_User_ID);
				if (userList.contains(ii))
					continue;
				userList.add(ii);
				//
				sendNoticeNow(AD_User_ID, NotificationType,
					client, from, subject, message.toString (), pdf);
			}
			rs.close ();
			pstmt.close ();
			pstmt = null;
		}
		catch (Exception e)
		{
			log.log (Level.SEVERE, sql, e);
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
		//	New Sales Rep (may happen if sent from beforeSave
		if (!userList.contains(getSalesRep_ID()))
			sendNoticeNow(getSalesRep_ID(), null,
				client, from, subject, message.toString (), pdf);

		log.info("EMail Success=" + m_success + ", Failure=" + m_failure
			+ " - Notices=" + m_notices);
	}	//	sendNotice

	/**
	 * 	Send Notice Now
	 *	@param AD_User_ID	recipient
	 *	@param NotificationType	optional notification type
	 *	@param client client
	 *	@param from sender
	 *	@param subject subject
	 *	@param message message
	 *	@param pdf optional attachment
	 */
	private void sendNoticeNow(int AD_User_ID, String NotificationType,
		MClient client, MUser from, String subject, String message, File pdf)
	{
		MUser to = MUser.get (getCtx(), AD_User_ID);
		if (NotificationType == null)
			NotificationType = to.getNotificationType();
		//	Send Mail
		if (X_AD_User.NOTIFICATIONTYPE_EMail.equals(NotificationType)
			|| X_AD_User.NOTIFICATIONTYPE_EMailPlusNotice.equals(NotificationType))
		{
			if (client.sendEMail(from, to, subject, message, pdf))
			{
				m_success++;
				if (m_emailTo.length() > 0)
					m_emailTo.append(", ");
				m_emailTo.append(to.getEMail());
			}
			else
			{
				log.warning("Failed: " + to);
				m_failure++;
				NotificationType = X_AD_User.NOTIFICATIONTYPE_Notice;
			}
		}
		//	Send Note
		if (X_AD_User.NOTIFICATIONTYPE_Notice.equals(NotificationType)
			|| X_AD_User.NOTIFICATIONTYPE_EMailPlusNotice.equals(NotificationType))
		{
			int AD_Message_ID = 834;
			MNote note = new MNote(getCtx(), AD_Message_ID, AD_User_ID,
				X_R_Request.Table_ID, getR_Request_ID(),
				subject, message.toString(), get_Trx());
			if (note.save())
				m_notices++;
		}
	}	//	sendNoticeNow


	/**************************************************************************
	 * 	Get MailID
	 * 	@param serverAddress server address
	 *	@return Mail Trailer
	 */
	public String getMailTrailer()
	{
		StringBuffer sb = new StringBuffer("\n").append(SEPARATOR)
			.append(Msg.translate(getCtx(), "R_Request_ID"))
			.append(": ").append(getDocumentNo())
			.append("  ").append(getMailTag())
			.append("\nSent by CompiereMail ");
		String serverAddress = getServerAddress();
		if (serverAddress != null)
		{
			if (serverAddress.startsWith("http://"))
				serverAddress = Util.replace(serverAddress, "http://", "https://");
			sb.append("\nReply via ").append(serverAddress)
				.append("/apps/Compiere.html?W_Store_ID=").append(m_W_Store_ID)
				.append("#Node?nodeID=").append(m_AD_Menu_ID)
				.append("&Record_ID=").append(getR_Request_ID())
				.append("&mode=single");
		}
		return sb.toString();
	}	//	getMailTrailer

	/**
	 * 	Get Server Address for CPRO Access
	 *	@return Server Address or null
	 */
	private String getServerAddress()
	{
		if (m_serverAddress != null)
		{
			if (m_serverAddress.length() == 0)
				return null;	//	not found
			return m_serverAddress;
		}
		//	Get Info
		MStore[] stores = MStore.getOfClient(MClient.get(getCtx(), getAD_Client_ID()));
		for (MStore element : stores)
		{
			if (element.getAD_Client_ID() == getAD_Client_ID())	//	first one
			{
				m_serverAddress = element.getURL();
				m_W_Store_ID = element.getW_Store_ID();
				break;
			}
		}
		//	Menu
		if (m_serverAddress != null)
		{
			log.info("Found: " + m_serverAddress + "; W_Store_ID=" + m_W_Store_ID);
			MMenu[] menues = MMenu.get(getCtx(), "Name LIKE 'Support Request (External)'");
			if (menues.length == 0)
			{
				m_serverAddress = null;
				log.warning("No external Menu found for Link");
			}
			else
				m_AD_Menu_ID = menues[0].getAD_Menu_ID();
		}
		else
			log.warning("No Web Store found for AD_Client_ID=" + getAD_Client_ID());
		if (m_serverAddress == null)
		{
			m_serverAddress = "";
			return null;
		}
		return m_serverAddress;
	}	//	getServerAddress


	/**
	 * 	Get Mail Tag
	 *	@return [Req@{id}@]
	 */
	public String getMailTag()
	{
		return TAG_START + get_ID() + TAG_END;
	}	//	getMailTag

	/**
	 * 	(Soft) Close request.
	 * 	Must be called after webUpdate
	 */
	public void doClose()
	{
		MStatus status = MStatus.get(getCtx(), getR_Status_ID());
		if (!status.isClosed())
		{
			MStatus[] closed = MStatus.getClosed(getCtx(), status.getR_StatusCategory_ID());
			MStatus newStatus = null;
			for (int i = 0; i < closed.length; i++)
			{
				if (!closed[i].isFinalClose())
				{
					newStatus = closed[i];
					break;
				}
			}
			if (newStatus == null && closed.length > 0)
				newStatus = closed[0];
			if (newStatus != null)
				setR_Status_ID(newStatus.getR_Status_ID());
		}
	}	//	doClose

	/**
	 * 	Escalate request
	 * 	@param user true if user escalated - otherwise system
	 */
	public void doEscalate(boolean user)
	{
		if (user)
		{
			String Importance = getPriorityUser();
			if (PRIORITYUSER_Urgent.equals(Importance))
				;	//	high as it goes
			else if (PRIORITYUSER_High.equals(Importance))
				setPriorityUser(PRIORITYUSER_Urgent);
			else if (PRIORITYUSER_Medium.equals(Importance))
				setPriorityUser(PRIORITYUSER_High);
			else if (PRIORITYUSER_Low.equals(Importance))
				setPriorityUser(PRIORITYUSER_Medium);
			else if (PRIORITYUSER_Minor.equals(Importance))
				setPriorityUser(PRIORITYUSER_Low);
		}
		else
		{
			String Importance = getPriority();
			if (PRIORITY_Urgent.equals(Importance))
				;	//	high as it goes
			else if (PRIORITY_High.equals(Importance))
				setPriority(PRIORITY_Urgent);
			else if (PRIORITY_Medium.equals(Importance))
				setPriority(PRIORITY_High);
			else if (PRIORITY_Low.equals(Importance))
				setPriority(PRIORITY_Medium);
			else if (PRIORITY_Minor.equals(Importance))
				setPriority(PRIORITY_Low);
		}
	}	//	doEscalate

}	//	MRequest
