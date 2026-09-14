package app.models;

public class Account {
	private int id, taxId;
	private String orusName, orusPass, afsName, afsPass, fbName;
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
}
