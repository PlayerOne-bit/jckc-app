package app.viewmodels;

import java.util.Comparator;
import java.util.List;
import app.database.Repository;
import app.models.Account;
import app.models.PersonalInfo;
import app.models.Taxpayer;

public class TaxpayerViewModel {
    
    private final Repository repo;
    private List<Taxpayer> taxpayers;

    public TaxpayerViewModel() throws Exception {
        this.repo = new Repository();
        refreshTaxpayers(); 
    }
    public void refreshTaxpayers() throws Exception {
        this.taxpayers = repo.getAllTaxpayers();
        for(Taxpayer taxpayer:this.taxpayers) {
        	PersonalInfo person = taxpayer.getPersonalInfo();
        	Account acc = taxpayer.getAccount();
        	taxpayer.setBussKind(safeText(taxpayer.getBussKind()));
        	taxpayer.setBussLine(safeText(taxpayer.getBussLine()));
        	taxpayer.setTaxNformTypes(safeText(taxpayer.getTaxNformTypes())+(taxpayer.getVat()!=null?" ("+taxpayer.getVat()+")":""));
        	taxpayer.setPsic(safeText(taxpayer.getPsic()));
        	person.setBirthdate(safeText(person.getBirthdate()));
        	person.setBirthplace(safeText(person.getBirthplace()));
        	person.setCivilStatus(safeText(person.getCivilStatus()));
        	person.setResidence(safeText(person.getResidence()));
        	person.setCpNum(safeText(person.getCpNum()));
        	person.setFatherName(safeText(person.getFatherName()));
        	person.setMotherMaidenName(safeText(person.getMotherMaidenName()));
        	person.setResidence(safeText(person.getResidence()));
        	person.setSpouseTin(safeText(person.getSpouseTin()));
        	person.setSpouseName(safeText(person.getSpouseName()));
        	acc.setGmailEmail(safeText(acc.getGmailEmail()));
        	acc.setGmailPass(safeText(acc.getGmailPass()));
        	acc.setYahooEmail(safeText(acc.getYahooEmail()));
        	acc.setYahooPass(safeText(acc.getYahooPass()));
        	acc.setOrusName(safeText(acc.getOrusName()));
        	acc.setOrusPass(safeText(acc.getOrusPass()));
        	acc.setAfsName(safeText(acc.getAfsName()));
        	acc.setAfsPass(safeText(acc.getAfsPass()));
        	acc.setFbName(safeText(acc.getFbName()));
        	acc.setRecoveryEmail(safeText(acc.getRecoveryEmail()));
        	taxpayer.setAccount(acc);
        	taxpayer.setPersonalInfo(person);
        }
        taxpayers.sort(Comparator.comparing(Taxpayer::getTaxName)
                .thenComparing(Taxpayer::getTinNum));
    }
    private String safeText(String text) {
    	if(text==null||text.trim().isEmpty()||text.equalsIgnoreCase("null"))
    		return "";
    	return text;
    }
    public List<Taxpayer> loadTaxpayers() {
    	
        return taxpayers;
    }

    public void createTaxpayer(Taxpayer taxpayer) throws Exception {
        repo.addTaxpayer(taxpayer);
        refreshTaxpayers();
    }

    public void updateTaxpayer(Taxpayer taxpayer) throws Exception {
        repo.updateTaxpayer(taxpayer);
        refreshTaxpayers();
    }

    public void deleteTaxpayer(int id) throws Exception {
        repo.deleteTaxpayer(id);
        refreshTaxpayers();
    }

    public Taxpayer getTaxpayer(int id) throws Exception {
        return repo.getTaxpayerById(id);
    }
}
