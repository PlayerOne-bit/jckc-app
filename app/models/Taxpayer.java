package app.models;

import java.util.ArrayList;
import java.util.List;

public class Taxpayer {
	private static Taxpayer taxpayer;
    private int id;

    private String tinNum;
    private String tradeName;
    private String bussAddress;
    private String bussKind;
    private String psic;
    private String formTypes;
    private String vat;
    private String appPassword;

    private PersonalInfo personalInfo;
    private Account account;
    private Name name;
    private List<Supplier> suppliers=new ArrayList<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTinNum() {
        return tinNum;
    }

    public void setTinNum(String tinNum) {
        this.tinNum = tinNum;
    }

    public String getTradeName() {
        return tradeName;
    }

    public void setTradeName(String tradeName) {
        this.tradeName = tradeName;
    }

    public String getBussAddress() {
        return bussAddress;
    }

    public void setBussAddress(String bussAddress) {
        this.bussAddress = bussAddress;
    }

    public String getBussKind() {
        return bussKind;
    }

    public void setBussKind(String bussKind) {
        this.bussKind = bussKind;
    }

    public String getPsic() {
        return psic;
    }

    public void setPsic(String psic) {
        this.psic = psic;
    }

    public String getFormTypes() {
        return formTypes;
    }

    public void setFormTypes(String formTypes) {
        this.formTypes = formTypes;
    }

    public String getVat() {
        return vat;
    }

    public void setVat(String vat) {
        this.vat = vat;
    }
    public String getAppPassword() {
        return appPassword;
    }

    public void setAppPassword(String appPassword) {
        this.appPassword = appPassword;
    }

    public PersonalInfo getPersonalInfo() {
        return personalInfo;
    }

    public void setPersonalInfo(PersonalInfo personalInfo) {
        this.personalInfo = personalInfo;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public Name getName() {
        return name;
    }

    public void setName(Name name) {
        this.name = name;
    }

	public List<Supplier> getSuppliers() {
		return suppliers;
	}

	public void setSuppliers(List<Supplier> suppliers) {
		this.suppliers = suppliers;
	}

	public static Taxpayer getTaxpayer() {
		return taxpayer;
	}

	public static void setTaxpayer(Taxpayer taxpayer) {
		Taxpayer.taxpayer = taxpayer;
	}
}