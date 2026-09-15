package app.models;

public class Name {
	private int id, taxId;
	private String lastName,firstName, middleName, suffix;
	public Name(int id, int taxId,String lastName, String firstName, String middleName, String suffix) {
		this.id=id;
		this.taxId=taxId;
		this.lastName=lastName;
		this.firstName=firstName;
		this.middleName=middleName;
		this.suffix=suffix;
	}
	
	public String getFullName(int index) {
		switch(index) {
			case 1: return "%s %s %s %s".formatted(firstName, middleName, lastName, suffix);
			case 2: return "%s %s, %s y %s".formatted(lastName, suffix, firstName, middleName);
			case 3: return "%s %s, %s %c".formatted(lastName,suffix,firstName,middleName.charAt(0));
			default: return null;
		}
	}
	public static void Main(String[] args) {
		Name name = new Name(0,0,"Claudio", "Joven", "Kallos", "Jr.");
		System.out.println(name.getFullName(0));
	}
}
