package app.models;

public class Name {
	private int id, taxId;
	private String lastName,firstName, middleName, suffix;
	
	public String getFullName(int index) {
		switch(index) {
			case 0: return "%s %s. %s %s".formatted(firstName, middleName.charAt(0), lastName, suffix);
			case 1: return "%s %s %s %s".formatted(firstName, middleName, lastName, suffix);
			case 2: return "%s%s, %s%s".formatted(lastName," "+suffix, firstName, " y "+middleName);
			case 3: return "%s%s, %s%s".formatted(lastName," "+suffix,firstName," "+middleName.charAt(0)+".");
			default: return null;
		}
	}

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

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getMiddleName() {
		return middleName;
	}

	public void setMiddleName(String middleName) {
		this.middleName = middleName;
	}

	public String getSuffix() {
		return suffix;
	}

	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}
}
