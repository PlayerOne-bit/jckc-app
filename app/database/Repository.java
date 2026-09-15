package app.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import app.models.Taxpayer;

public class Repository {
	Connection sql;
	public Repository() throws SQLException {
		sql = DatabaseConfig.getConnection();
	}
	private void addData(Taxpayer taxpayer) throws SQLException {
		//TODO
	}
	public List<Taxpayer> getAllTaxpayers() throws SQLException{
		List<Taxpayer> taxpayers = new ArrayList<>();
		ResultSet rs = sql.prepareStatement(DATABASE.SELECT_TABLE_TAXPAYERS).executeQuery();
		
		//TODO
		return taxpayers;
	}
	
	
	private abstract class DATABASE{
		static final String 
			TABLE_TAXPAYER = "Taxpayer",
			TABLE_PERSONAL_INFO = "PersonalInfo",
			TABLE_ACCOUNT = "Account",
			TABLE_ACCOUNTS = "Accounts",
			TABLE_SUPPLIER = "Supplier",
			COLUMN_T_ID="id",
			COLUMN_T_TIN_NUM="tinNum",
			COLUMN_T_TAX_NAME="taxName",
			COLUMN_T_TRADE_NAME="tradeName",
			COLUMN_T_BUSS_ADD="busAddress",
			COLUMN_T_BUSS_KIND="bussKind",
			COLUMN_T_TAX_AND_FORM_TYPES="taxNformTypes",
			COLUMN_T_VAT="vat",
			COLUMN_P_ID="id",
			COLUMN_P_TAX_ID="taxId",
			COLUMN_P_BDAY="birthdate",
			COLUMN_P_BPLACE="birthplace",
			COLUMN_P_RESIDENCE="residence",
			COLUMN_P_CIVIL_STATUS="civilStatus",
			COLUMN_P_SPOUSE_NAME="spouseName",
			COLUMN_P_SPOUSE_TIN="spouseTin",
			COLUMN_P_MOTHER_NAME="motherMaidenName",
			COLUMN_P_FATHER_NAME="fatherName",
			COLUMN_P_CP_NUM="cpNum",
			COLUMN_A_ID="id",
			COLUMN_A_TAX_ID="taxId",
			COLUMN_A_ORUS_NAME="orusName",
			COLUMN_A_ORUS_PASS="orusPass",
			COLUMN_A_AFS_NAME="afsName",
			COLUMN_A_AFS_PASS="afsPass",
			COLUMN_A_FB_NAME="fbName",
			COLUMN_A_RECOVERY_EMAIL="recoveryEmail",
			COLUMN_AS_ID="id",
			COLUMN_AS_TAX_ID="taxId",
			COLUMN_AS_EMAIL="email",
			COLUMN_AS_PASS="pass",
			COLUMN_S_ID="id",
			COLUMN_S_TIN_NUM="tinNum",
			COLUMN_S_TRADE_NAME="tradeName",
			COLUMN_S_BUSS_ADD="bussAddress";
		
		static final String
			SELECT_TABLE_TAXPAYERS="SELECT * FROM "+TABLE_TAXPAYER,
			SELECT_TABLE_PERSONAL_INFO="SELECT * FROM "+TABLE_PERSONAL_INFO,
			SELECT_TABLE_ACCOUNT="SELECT * FROM "+TABLE_ACCOUNT,
			SELECT_TABLE_ACCOUNTS="SELECT * FROM "+TABLE_ACCOUNTS,
			SELECT_TABLE_SUPPLIER="SELECT * FROM "+TABLE_SUPPLIER,
			INSERT_TABLE_TAXPAYER = "INSERT INTO " + TABLE_TAXPAYER + " (" 
		            + COLUMN_T_TIN_NUM + ", " + COLUMN_T_TAX_NAME + ", " + COLUMN_T_TRADE_NAME + ", " 
		            + COLUMN_T_BUSS_ADD + ", " + COLUMN_T_BUSS_KIND + ", " + COLUMN_T_TAX_AND_FORM_TYPES + ", " 
		            + COLUMN_T_VAT + ") VALUES (?, ?, ?, ?, ?, ?, ?)",

		    INSERT_TABLE_PERSONAL_INFO = "INSERT INTO " + TABLE_PERSONAL_INFO + " (" 
		            + COLUMN_P_TAX_ID + ", " + COLUMN_P_BDAY + ", " + COLUMN_P_BPLACE + ", " 
		            + COLUMN_P_RESIDENCE + ", " + COLUMN_P_CIVIL_STATUS + ", " + COLUMN_P_SPOUSE_NAME + ", " 
		            + COLUMN_P_SPOUSE_TIN + ", " + COLUMN_P_MOTHER_NAME + ", " + COLUMN_P_FATHER_NAME + ", " 
		            + COLUMN_P_CP_NUM + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",

		    INSERT_TABLE_ACCOUNT = "INSERT INTO " + TABLE_ACCOUNT + " (" 
		            + COLUMN_A_TAX_ID + ", " + COLUMN_A_ORUS_NAME + ", " + COLUMN_A_ORUS_PASS + ", " 
		            + COLUMN_A_AFS_NAME + ", " + COLUMN_A_AFS_PASS + ", " + COLUMN_A_FB_NAME +", " 
		            + COLUMN_A_RECOVERY_EMAIL+") VALUES (?, ?, ?, ?, ?, ?,?)",

		    INSERT_TABLE_ACCOUNTS = "INSERT INTO " + TABLE_ACCOUNTS + " (" 
		            + COLUMN_AS_TAX_ID + ", " + COLUMN_AS_EMAIL + ", " + COLUMN_AS_PASS 
		            + ") VALUES (?, ?, ?)",

		    INSERT_TABLE_SUPPLIER = "INSERT INTO " + TABLE_SUPPLIER + " (" 
		            + COLUMN_S_TIN_NUM + ", " + COLUMN_S_TRADE_NAME + ", " + COLUMN_S_BUSS_ADD 
		            + ") VALUES (?, ?, ?)",
		    DELETE_TABLE_TAXPAYER = "DELETE FROM " + TABLE_TAXPAYER + " WHERE " + COLUMN_T_ID + " = ?",
		    DELETE_TABLE_PERSONAL_INFO = "DELETE FROM " + TABLE_PERSONAL_INFO + " WHERE " + COLUMN_P_ID + " = ?",
		    DELETE_TABLE_ACCOUNT = "DELETE FROM " + TABLE_ACCOUNT + " WHERE " + COLUMN_A_ID + " = ?",
		    DELETE_TABLE_ACCOUNTS = "DELETE FROM " + TABLE_ACCOUNTS + " WHERE " + COLUMN_AS_ID + " = ?",
		    DELETE_TABLE_SUPPLIER = "DELETE FROM " + TABLE_SUPPLIER + " WHERE " + COLUMN_S_ID + " = ?";
		
	}

}
