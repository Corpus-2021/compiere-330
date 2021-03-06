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
package org.compiere.grid.ed;

import java.awt.*;
import java.awt.event.*;
import java.beans.*;

import javax.swing.*;

import org.compiere.common.*;
import org.compiere.model.*;
import org.compiere.plaf.*;
import org.compiere.swing.*;
import org.compiere.util.*;

/**
 *	Data Binding:
 *		VEditors call fireVetoableChange(m_columnName, null, getText());
 *		GridController (for Single-Row) and VCellExitor (for Multi-Row)
 *      listen to Vetoable Change Listener (vetoableChange)
 *		then set the value for that column in the current row in the table
 *
 *  @author 	Jorg Janke
 *  @version 	$Id: VString.java,v 1.2 2006/07/30 00:51:27 jjanke Exp $
 */
public final class VString extends CTextField
	implements VEditor, ActionListener, FocusListener
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/** Max Display Length - 60 */
	public static final int MAXDISPLAY_LENGTH = org.compiere.model.GridField.MAXDISPLAY_LENGTH;

	/**
	 *	IDE Bean Constructor for 30 character updateable field
	 */
	public VString()
	{
		this("String", false, false, true, 30, 30, "", null);
	}	//	VString

	/**
	 *	Detail Constructor
	 *  @param columnName column name
	 *  @param mandatory mandatory
	 *  @param isReadOnly read only
	 *  @param isUpdateable updateable
	 *  @param displayLength display length
	 *  @param fieldLength field length
	 *  @param VFormat format
	 *  @param ObscureType obscure type
	 */
	public VString (String columnName, boolean mandatory, boolean isReadOnly, boolean isUpdateable,
		int displayLength, int fieldLength, String VFormat, String ObscureType)
	{
		super(displayLength>MAXDISPLAY_LENGTH ? MAXDISPLAY_LENGTH : displayLength);
		super.setName(columnName);
		m_columnName = columnName;
		if (VFormat == null)
			VFormat = "";
		m_VFormat = VFormat;
		m_fieldLength = fieldLength;
		if (m_VFormat.length() != 0 || m_fieldLength != 0)
			setDocument(new MDocString(m_VFormat, m_fieldLength, this));
		if (m_VFormat.length() != 0)
			setCaret(new VOvrCaret());
		//
		setMandatory(mandatory);
		if (ObscureType != null && ObscureType.length() > 0)
		{
			m_obscure = new Obscure ("", ObscureType);
			m_stdFont = getFont();
			m_obscureFont = new Font("SansSerif", Font.ITALIC, m_stdFont.getSize());
			addFocusListener(this);
		}

		//	Editable
		if (isReadOnly || !isUpdateable)
		{
			setEditable(false);
			setBackground(CompierePLAF.getFieldBackground_Inactive());
		}

		this.addKeyListener(this);
		this.addActionListener(this);
		//	Popup for Editor
		if (fieldLength > displayLength)
		{
            //  Popup
            addMouseListener(new MouseAdapter()
            {
                @Override
				public void mouseClicked(MouseEvent e)
                {
                    if (SwingUtilities.isRightMouseButton(e))
                        m_popupMenu.show((Component)e.getSource(), e.getX(), e.getY());
                }
            });
            
            String actionKey = getClass().getName() + "_popop";
            InputMap iMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_MASK);
            iMap.put(ks, actionKey);
            getActionMap().put(actionKey, new AbstractAction()
            {
                /**
				 * 
				 */
				private static final long serialVersionUID = 1L;

				public void actionPerformed(ActionEvent e)
                {
                    Component comp = (Component)e.getSource();
                    m_popupMenu.show(comp, 10, 10);
                }
            });
            
			mEditor = new CMenuItem (Msg.getMsg(Env.getCtx(), "Editor"), Env.getImageIcon("Editor16.gif"));
			mEditor.addActionListener(this);
			m_popupMenu.add(mEditor);
		}
		setForeground(CompierePLAF.getTextColor_Normal());
		setBackground(CompierePLAF.getFieldBackground_Normal());
	}	//	VString

	/**
	 *  Dispose
	 */
	public void dispose()
	{
		m_mField = null;
	}   //  dispose

	/**	Popup Menu				*/
	JPopupMenu 					m_popupMenu = new JPopupMenu();
	/** Editor Menu Item		*/
	private CMenuItem 			mEditor;
	/** Grid Field				*/
	private GridField      		m_mField = null;
	/** Column Name				*/
	private String				m_columnName;
	private String				m_oldText;
	private String				m_initialText;
	private String				m_VFormat;
	/** Field Length				*/
	private int					m_fieldLength;
	/**	Obcure Setting				*/
	private Obscure				m_obscure = null;
	private Font				m_stdFont = null;
	private Font				m_obscureFont = null;
	/**	Setting new value			*/
	private volatile boolean	m_setting = false;
	/**	Field in focus				*/
	private volatile boolean	m_infocus = false;

	/**	Logger	*/
	private static CLogger log = CLogger.getCLogger (VString.class);

	/**
	 *	Set Editor to value
	 *  @param value value
	 */
	@Override
	public void setValue(Object value)
	{
	//	log.config("" + value);
		if (value == null)
			m_oldText = "";
		else
			m_oldText = value.toString();
		//	only set when not updated here
		if (m_setting)
			return;
		setText (m_oldText);
		m_initialText = m_oldText;
		//	If R/O left justify 
		if (!isEditable() || !isEnabled())
			setCaretPosition(0);
	}	//	setValue

	/**
	 *  Property Change Listener
	 *  @param evt event
	 */
	public void propertyChange (PropertyChangeEvent evt)
	{
		if (evt.getPropertyName().equals(org.compiere.model.GridField.PROPERTY))
			setValue(evt.getNewValue());
	}   //  propertyChange

	/**
	 *	Return Editor value
	 *  @return value
	 */
	@Override
	public Object getValue()
	{
		return getText();
	}	//	getValue

	/**
	 *  Return Display Value
	 *  @return value
	 */
	@Override
	public String getDisplay()
	{
		return super.getText();		// optionally obscured
	}   //  getDisplay


	/**
	 *	Key Released.
	 *	if Escape Restore old Text
	 *  @param e event
	 */
	@Override
	public void keyReleased(KeyEvent e)
	{
		log.finest("Key=" + e.getKeyCode() + " - " + e.getKeyChar()
			+ " -> " + getText());
		//  ESC
		if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
			setText(m_initialText);
		m_setting = true;
		try
		{
			String clear = getText();
			if (clear.length() > m_fieldLength)
				clear = clear.substring(0, m_fieldLength);
			fireVetoableChange (m_columnName, m_oldText, clear);
		}
		catch (PropertyVetoException pve)	
		{
		}
		m_setting = false;
	}	//	keyReleased

	/**
	 *	Data Binding to MTable (via GridController)	-	Enter pressed
	 *  @param e event
	 */
	public void actionPerformed(ActionEvent e)
	{
		if (e.getActionCommand().equals(ValuePreference.NAME))
		{
			if (MRole.getDefault().isShowPreference())
				ValuePreference.start (m_mField, getValue());
			return;
		}

		//  Invoke Editor
		if (e.getSource() == mEditor)
		{
			String s = Editor.startEditor(this, Msg.translate(Env.getCtx(), m_columnName), 
				getText(), isEditable(), m_fieldLength, m_VFormat);
			setText(s);
		}
		//  Data Binding
		try
		{
			fireVetoableChange(m_columnName, m_oldText, getText());
		}
		catch (PropertyVetoException pve)	
		{
		}
	}	//	actionPerformed

	/**
	 *  Set Field/WindowNo for ValuePreference
	 *  @param mField field
	 */
	public void setField (GridField mField)
	{
		m_mField = mField;
		if (m_mField != null
			&& MRole.getDefault().isShowPreference())
		{
			String columnName = mField.getColumnName();
			if ("Value".equals(columnName) || "DocumentNo".equals(columnName))
				;
			else
				ValuePreference.addMenu (this, m_popupMenu);
		}
	}   //  setField

	/**
	 *  Get Field
	 *  @return gridField
	 */
	public GridField getField()
	{
		return m_mField;
	}   //  getField

	
	/**
	 * 	Set Text (optionally obscured)
	 *	@param text text
	 */
	@Override
	public void setText (String text)
	{
		if (m_obscure != null && !m_infocus)
		{
			super.setFont(m_obscureFont);
			super.setText (m_obscure.getObscuredValue(text));
			super.setForeground(Color.gray);
		}
		else
		{
			if (m_stdFont != null)
			{
				super.setFont(m_stdFont);
				super.setForeground(CompierePLAF.getTextColor_Normal());
			}
			super.setText (text);
		}
	}	//	setText

	
	/**
	 * 	Get Text (clear)
	 *	@return text
	 */
	@Override
	public String getText ()
	{
		String text = super.getText();
		if (m_obscure != null && text != null && text.length() > 0)
		{
			if (text.equals(m_obscure.getObscuredValue()))
				text = m_obscure.getClearValue();
		}
		return text;
	}	//	getText

	/**
	 * 	Focus Gained.
	 * 	Enabled with Obscure
	 *	@param e event
	 */
	public void focusGained (FocusEvent e)
	{
		m_infocus = true;
		setText(getText());		//	clear
	}	//	focusGained

	/**
	 * 	Focus Lost
	 * 	Enabled with Obscure
	 *	@param e event
	 */
	public void focusLost (FocusEvent e)
	{
		m_infocus = false;
		setText(getText());		//	obscure
	}	//	focus Lost
	
	/**
	 * 	Get Focus Component
	 *	@return component
	 */
	public Component getFocusableComponent()
	{
		return this;
	}	//	getFocusableComponent

}	//	VString
