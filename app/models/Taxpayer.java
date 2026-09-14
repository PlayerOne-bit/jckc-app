package app.models;

public class Taxpayer {
	private int id;
	private String tinNum, taxName, tradeName, bussAddress, bussKind, psic, bussLine, taxNformTypes;
	private boolean vat;
	public Taxpayer(String tinNum,String taxName, String tradeName, String bussAddress, boolean vat) {
		this.tinNum=tinNum;
		this.taxName=taxName;
		this.tradeName=tradeName;
		this.bussAddress=bussAddress;
		this.vat=vat;
	}
	public void setId(int id) {this.id=id;}
	public void setBussKind(String bussKind) {this.bussKind=bussKind;}
	public void setPsic(String psic) {this.psic=psic;}
	public void setBussLine(String bussLine) {this.bussLine=bussLine;}
	public void setTaxNformTypes(String taxNformTypes) {this.taxNformTypes=taxNformTypes;}
	public int getId() {return id;}
	public String getTinNum() {return tinNum;}
	public String getTaxName() {return taxName;}
	public String getTradeName() {return tradeName;}
	public String getBussAddress() {return bussAddress;}
	public String getBussKind() {return bussKind;}
	public String getPsic() {return psic;}
	public String getBussLine() {return bussLine;}
	public String getTaxNformTypes() {return taxNformTypes;}
	public boolean isVat() {return vat;}
}
