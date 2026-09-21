package app.viewmodels;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import app.database.TaxpayerRepository;
import app.models.Account;
import app.models.PersonalInfo;
import app.models.Taxpayer;

public class TaxpayerViewModel {
    private final TaxpayerRepository repo;
    private List<Taxpayer> taxpayers;
    private static final Comparator<String> NULL_SAFE = Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER);

    private static final Comparator<Taxpayer> TAXPAYER_ORDER = Comparator
    		.comparing((Taxpayer t) -> t.getName() != null ? t.getName().getLastName() : null, NULL_SAFE)
    		.thenComparing((Taxpayer t) -> t.getName() != null ? t.getName().getFirstName() : null, NULL_SAFE)
    		.thenComparing((Taxpayer t) -> t.getName() != null ? t.getName().getMiddleName() : null, NULL_SAFE)
    		.thenComparing(Taxpayer::getTinNum, NULL_SAFE)
    		.thenComparing(Taxpayer::getTradeName, NULL_SAFE)
    		.thenComparing(Taxpayer::getBussAddress, NULL_SAFE);
    
    public TaxpayerViewModel() throws Exception {
        this.repo = new TaxpayerRepository();
        refreshTaxpayers(); 
    }
    public void refreshTaxpayers() throws Exception {
        this.taxpayers = repo.getAllTaxpayers();
        for(Taxpayer taxpayer:this.taxpayers) {
        	PersonalInfo person = taxpayer.getPersonalInfo();
        	Account acc = taxpayer.getAccount();
        	taxpayer.setBussKind(safeText(taxpayer.getBussKind()));
        	taxpayer.setFormTypes(safeText(taxpayer.getFormTypes()));
        	taxpayer.setVat(safeText(taxpayer.getVat()));;
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
    }
    private String safeText(String text) {
    	if(text==null||text.trim().isEmpty()||text.equalsIgnoreCase("null"))
    		return "";
    	return text;
    }
    public List<Taxpayer> loadTaxpayers() {
    	List<Taxpayer> sorted = taxpayers != null ? new ArrayList<>(taxpayers) : new ArrayList<>();
    	sorted.sort(TAXPAYER_ORDER);
    	return sorted;
    }
    public boolean createTaxpayer(Taxpayer taxpayer) throws Exception {
        List<Taxpayer> list = taxpayers; 
        int left = 0;
        int right = list.size() - 1;
        while (left <= right) {
            int mid = (left + right) / 2;
            Taxpayer midTaxpayer = list.get(mid);
            int comparison = midTaxpayer.getTinNum().compareTo(taxpayer.getTinNum());
            if (comparison == 0) {
                return false; 
            } else if (comparison < 0) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        repo.addTaxpayer(taxpayer);
        refreshTaxpayers();
        return true;
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
