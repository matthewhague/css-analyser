package duplication;

import java.util.ArrayList;
import java.util.List;

import CSSModel.declaration.Declaration;
import CSSModel.selectors.Selector;

/**
 * This class represents the overridden properties (in cases 
 * that we have the same selectors and same properties but not the 
 * same values).
 * @author Davood Mazinanian
 *
 */
public class OverriddenProperties extends Duplication {

	private final Selector forSelector;
	private final List<List<Declaration>> forDeclarations;
	
	public OverriddenProperties(Selector selector) {
		super(DuplicationType.OVERRIDEN_PROPERTY);
		forSelector = selector;
		forDeclarations = new ArrayList<>();
	}
	
	public int getNumberOfDeclarations() {
		return forDeclarations.size();
	}
	
	public Selector getSelector() {
		return forSelector;
	}
	
	public boolean addDeclaration(Declaration declaration) {
		for (List<Declaration> declarations : forDeclarations) {
			if (declarations != null && declarations.get(0)!= null && 
					declarations.get(0).getProperty().equals(declaration.getProperty())) {
				return declarations.add(declaration);
			}
		}
		ArrayList<Declaration> newList = new ArrayList<>();
		newList.add(declaration);
		return forDeclarations.add(newList);
	}
	
	@Override
	public String toString() {
		String string = "For selector " + forSelector + " these properties are defined" +
				" more than once so they would be overridden: \n"; 
		for (List<Declaration> list : forDeclarations) {
			if (list.get(0) != null) {
				string += "\t" + "Property " + list.get(0).getProperty() + " in the following lines: \n";
				for (Declaration declaration : list) {
					string += "\t\t" + declaration.getLineNumber() + " : " + declaration.getColumnNumber() + "\n";
				}
			}
		}
		return string;
	}

	public void addAllDeclarations(List<Declaration> currentEqualDeclarations) {
		for (Declaration declaration : currentEqualDeclarations)
			addDeclaration(declaration);
			
	}

	// TODO: implement equals and hashCode
}
