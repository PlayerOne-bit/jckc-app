package app.models;

public class Supplier {
	private int id, tinNum;
	private String tradeName, bussAddress;
	public Supplier(int id,int tinNum, String tradeName, String bussAddress) {
		this.id=id;
		this.tinNum=tinNum;
		this.tradeName=tradeName;
		this.bussAddress=bussAddress;
	}
	public int getId() {return id;}
	public int getTinNum() {return tinNum;}
	public String getTradeName() {return tradeName;}
	public String getBussAddress() {return bussAddress;}
}
