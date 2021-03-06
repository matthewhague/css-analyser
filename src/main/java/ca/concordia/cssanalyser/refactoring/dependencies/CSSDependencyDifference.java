package ca.concordia.cssanalyser.refactoring.dependencies;


public class CSSDependencyDifference<E> {
	
	public enum CSSDependencyDifferenceType {
		MISSING,
		REVERSED,
		ADDED
	}
	
	private final CSSDependencyDifferenceType type;
	private final CSSDependency<E> dependency;
	
	public CSSDependencyDifference(CSSDependencyDifferenceType type, CSSDependency<E> dependency) {
		this.type = type;
		this.dependency = dependency;
	}
	
	@Override
	public String toString() {
		String toReturn = "";
		switch (type) {
			case ADDED:
				toReturn = "Added dependency";
				break;
			case MISSING:
				toReturn = "Missing dependency";
				break;
			case REVERSED:
				toReturn = "Reversed dependency";
				break;
		}
		return toReturn + ": " + dependency;
	}

	public CSSDependencyDifferenceType getType() {
		return type;
	}
	
}
