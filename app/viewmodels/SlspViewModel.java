package app.viewmodels;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import app.database.SlspRepository;
import app.models.SLSP;

public class SlspViewModel {
	private SlspRepository repo;
	private List<SLSP> SLSPs= new ArrayList<>();
	private static final Comparator<String> NULL_SAFE = Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER);
	private static final Comparator<SLSP> SLSP_ORDER = Comparator.comparing(
			SLSP::getPeriod,NULL_SAFE
			);
	
	public SlspViewModel() throws Exception{
		repo=new SlspRepository();
		refreshSLSPs();
	}
	public void refreshSLSPs() throws Exception{
		SLSPs=repo.getAllSLSPs();
	}
	public List<SLSP> loadSLSPs() {
		List<SLSP> sorted = SLSPs!=null?new ArrayList<>(SLSPs):null;
		sorted.sort(SLSP_ORDER);
		return sorted;
	}
	public boolean createSlsp(SLSP slsp) throws Exception{
		List<SLSP> list = SLSPs;
		int left = 0;
        int right = list.size() - 1;
        while (left <= right) {
            int mid = (left + right) / 2;
            SLSP midSLSP = list.get(mid);
            int comparison = midSLSP.getPeriod().compareTo(slsp.getPeriod());
            if (comparison == 0) {
                return false; 
            } else if (comparison < 0) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        repo.addSLSP(slsp);
        refreshSLSPs();
        return true;
	}
	public void deleteSlsp(int id) throws Exception{
		repo.deleteSLSP(id);
		refreshSLSPs();
	}
	public void updateSlsp(SLSP slsp)throws Exception{
		repo.updateSLSP(slsp);
		refreshSLSPs();
	}
	public SLSP getSlspById(int id) throws Exception{
		return repo.getSLSPById(id);
	}
}
