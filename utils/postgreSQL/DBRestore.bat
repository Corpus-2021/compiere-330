@Echo	Compiere Database Restore 	$Revision: 1.2 $

@Rem $Id: DBRestore.bat,v 1.2 2005/01/22 21:59:15 jjanke Exp $

@Echo	Restoring Compiere DB from %COMPIERE_HOME%\data\ExpDat.dmp

@if (%COMPIERE_HOME%) == () goto environment
@if (%COMPIERE_DB_NAME%) == () goto environment
@Rem Must have parameter: systemAccount compiereID CompierePwd
@if (%1) == () goto usage
@if (%2) == () goto usage
@if (%3) == () goto usage

@echo -------------------------------------
@echo Re-Create DB user
@echo -------------------------------------
@sqlplus %1@%COMPIERE_DB_NAME% @%COMPIERE_HOME%\utils\CreateUser.sql %2 %3

@echo -------------------------------------
@echo Import ExpDat
@echo -------------------------------------
@imp %1@%COMPIERE_DB_NAME% FILE=%COMPIERE_HOME%\data\ExpDat.dmp FROMUSER=(%2) TOUSER=%2 

@echo -------------------------------------
@echo Check System
@echo Import may show some warnings. This is OK as long as the following does not show errors
@echo -------------------------------------
@sqlplus %2/%3@%COMPIERE_DB_NAME% @%COMPIERE_HOME%\utils\AfterImport.sql

@goto end

:environment
@Echo Please make sure that the enviroment variables are set correctly:
@Echo		COMPIERE_HOME	e.g. D:\Compiere2
@Echo		COMPIERE_DB_NAME e.g. dev1.compiere.org

:usage
@echo Usage:		%0% <systemAccount> <CompiereID> <CompierePwd>
@echo Example:	%0% system/manager compiere compiere

:end
