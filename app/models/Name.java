package app.models;

public class Name {
	private int id, taxId;
	private String lastName,firstName, middleName, suffix;
	
	public String getFullName(int index) {
		String suffix = isBlank(this.suffix) ? "" : " " + this.suffix.trim();
		String middleName;
		switch(index) {
			case 0:
				middleName = isBlank(this.middleName) ? "" : " " + this.middleName.trim().charAt(0) + ".";
				return "%s%s %s%s".formatted(firstName, middleName, lastName, suffix);
			case 1:
				middleName = isBlank(this.middleName) ? "" : " " + this.middleName.trim();
				return "%s%s %s%s".formatted(firstName, middleName, lastName, suffix);
			case 2:
				middleName = isBlank(this.middleName) ? "" : " " + this.middleName.trim().charAt(0) + ".";
				return "%s%s, %s%s".formatted(lastName, suffix, firstName, middleName);
			case 3:
				middleName = isBlank(this.middleName) ? "" : " y " + this.middleName.trim();
				return "%s%s, %s%s".formatted(lastName, suffix, firstName, middleName);
			default: return null;
		}
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
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
