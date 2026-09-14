package app.viewmodels;
import org.mindrot.jbcrypt.*; 
public final class AuthAdmin {
	private AuthAdmin() {}
	private static final String password = "$2a$10$oQ0ot01jjAn2dOcIBHORv.WivVjM.aOOOi37SyezSBFVGV.5A2oeW";
	public static boolean isPasswordCorrect(String inputPassword) {
		return BCrypt.checkpw(inputPassword, password);
	}
	
	
}
