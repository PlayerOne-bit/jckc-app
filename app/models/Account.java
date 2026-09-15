package app.models;

public class Account {
	private int id, taxId;
	private String gmailEmail, gmailPass, yahooEmail, yahooPass, orusName, orusPass, afsName, afsPass, fbName, recoveryEmail;
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public int getTaxId() {
		return taxId;
	}
	public void setTaxId(int taxId) {
		this.taxId = taxId;
	}
	public String getOrusName() {
		return orusName;
	}
	public void setOrusName(String orusName) {
		this.orusName = orusName;
	}
	public String getOrusPass() {
		return orusPass;
	}
	public void setOrusPass(String orusPass) {
		this.orusPass = orusPass;
	}
	public String getAfsName() {
		return afsName;
	}
	public void setAfsName(String afsName) {
		this.afsName = afsName;
	}
	public String getAfsPass() {
		return afsPass;
	}
	public void setAfsPass(String afsPass) {
		this.afsPass = afsPass;
	}
	public String getFbName() {
		return fbName;
	}
	public void setFbName(String fbName) {
		this.fbName = fbName;
	}
	public String getRecoveryEmail() {
		return recoveryEmail;
	}
	public void setRecoveryEmail(String recoveryEmail) {
		this.recoveryEmail = recoveryEmail;
	}
	public String getYahooEmail() {
		return yahooEmail;
	}
	public void setYahooEmail(String yahooEmail) {
		this.yahooEmail = yahooEmail;
	}
	public String getYahooPass() {
		return yahooPass;
	}
	public void setYahooPass(String yahooPass) {
		this.yahooPass = yahooPass;
	}
	public String getGmailEmail() {
		return gmailEmail;
	}
	public void setGmailEmail(String gmailEmail) {
		this.gmailEmail = gmailEmail;
	}
	public String getGmailPass() {
		return gmailPass;
	}
	public void setGmailPass(String gmailPass) {
		this.gmailPass = gmailPass;
	}	
}
