package ca.concordia.cssanalyser.cssmodel.declaration;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ca.concordia.cssanalyser.cssmodel.declaration.value.DeclarationValue;
import ca.concordia.cssanalyser.cssmodel.selectors.Selector;

public class SingleValuedDeclaration extends Declaration {
	
	protected DeclarationValue declarationValue;

	public SingleValuedDeclaration(String propertyName, DeclarationValue declrationValue, Selector belongsTo, int offset, int length, boolean important) {
		super(propertyName, belongsTo, offset, length, important);
		this.declarationValue = declrationValue;
		// For single-valued declarations, the style property is set to the declaration property
		// See doc for DeclarationValue#setCorrespondingStyleProperty
		declarationValue.setCorrespondingStyleProperty(this.getProperty());
	}

	@Override
	protected boolean valuesEquivalent(Declaration otherDeclaration) {
		
		if (!(otherDeclaration instanceof SingleValuedDeclaration))
			throw new RuntimeException("This method cannot be called on a declaration rather than SingleValuedDeclaration.");
		
		SingleValuedDeclaration otherSingleValuedDeclaration = (SingleValuedDeclaration)otherDeclaration;
		
		return declarationValue.equivalent(otherSingleValuedDeclaration.getValue());

	}

	
	public DeclarationValue getValue() {
		return declarationValue;
	}

	@Override
	public Declaration clone() {
		return new SingleValuedDeclaration(property, declarationValue.clone(), parentSelector, offset, length, isImportant);
	}

	@Override
	protected boolean valuesEqual(Declaration otherDeclaration) {
		if (!(otherDeclaration instanceof SingleValuedDeclaration))
			throw new RuntimeException("This method cannot be called on a declaration rather than SingleValuedDeclaration.");
		
		SingleValuedDeclaration otherSingleValuedDeclaration = (SingleValuedDeclaration)otherDeclaration;
		
		return declarationValue.equals(otherSingleValuedDeclaration.getValue());

	}
	
	@Override
	public String toString() {	
		return String.format("%s: %s", property, declarationValue);
	}
	
	int hashCode = -1;
	@Override
	public int hashCode() {
		// Only calculate the hashCode once
		if (hashCode == -1) {
			final int prime = 31;
			int result = 1;
			result = prime * result + offset;
			result = prime * result +  prime * declarationValue.hashCode();
			result = prime * result + (isCommaSeparatedListOfValues ? 1231 : 1237);
			result = prime * result + (isImportant ? 1231 : 1237);
			result = prime * result + length;
			result = prime * result + 0;
			result = prime * result
					+ ((parentSelector == null) ? 0 : parentSelector.hashCode());
			result = prime * result
					+ ((property == null) ? 0 : property.hashCode());
			hashCode = result;
		}
		return hashCode;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SingleValuedDeclaration other = (SingleValuedDeclaration) obj;
		if (length != other.length)
			return false;
		if (offset != other.offset)
			return false;
		if (isCommaSeparatedListOfValues != other.isCommaSeparatedListOfValues)
			return false;
		if (isImportant != other.isImportant)
			return false;
		if (property == null) {
			if (other.property != null)
				return false;
		} else if (!property.equals(other.property))
			return false;
		if (parentSelector == null) {
			if (other.parentSelector != null)
				return false;
		} else if (!parentSelector.equals(other.parentSelector))
			return false;
		if (declarationValue == null) {
			if (other.declarationValue != null)
				return false;
		} else if (!declarationValue.equals(other.declarationValue))
			return false;
		return true;
	}

	@Override
	public Map<String, DeclarationValue> getPropertyToValuesMap() {
		Map<String, DeclarationValue> toReturn = new HashMap<>();
		toReturn.put(property, declarationValue);
		return toReturn;
	}
	
	@Override
	public Collection<String> getStyleProperties() {
		Set<String> toReturn = new HashSet<>();
		toReturn.add(property);
		return toReturn;
	}
	 
}
