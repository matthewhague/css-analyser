package CSSModel.selectors;

/**
 * Represents CSS "selector1 selector2" selectors
 * selector1 and selector2 are of type {@link AtomicSelector}
 * so for example, for selector1 selector2 selector 3, we have two selectors
 * one of which is again a {@link DescendantSelector} and anoter is an {@link DescendantSelector}
 * @author Davood Mazinanian
 */
public class DescendantSelector extends AtomicSelector {
	
	protected final AtomicSelector parentSelector;
	protected final AtomicSelector childSelector; 
	
	public DescendantSelector(AtomicSelector parent, AtomicSelector child) {
		parentSelector = parent;
		childSelector = child;
	}
	
	/**
	 * Returns the parent selector (the selector on the left hand side
	 * of a descendant selector)
	 * @return
	 */
	public AtomicSelector getParentSelector() {
		return parentSelector;
	}
	
	/**
	 * Returns the child selector (the selector on the right hand side
	 * of a descendant selector)
	 * @return
	 */
	public AtomicSelector getChildSelector() {
		return childSelector;
	}
	
	@Override
	public String toString() {
		return parentSelector + " " + childSelector;
	}
	
	@Override
	public boolean selectorEquals(Selector otherSelector) {
		if (!checkGeneralEquality(otherSelector))
			return false;
		DescendantSelector otherDesendantSelector = (DescendantSelector)otherSelector;
		return parentSelector.selectorEquals(otherDesendantSelector.parentSelector) &&
				childSelector.selectorEquals(otherDesendantSelector.childSelector);
	}
	
	@Override
	public boolean equals(Object obj) {
		checkGeneralEquality(obj);
		DescendantSelector otherDesendantSelector = (DescendantSelector)obj;
		return (lineNumber == otherDesendantSelector.lineNumber &&
				columnNumber == otherDesendantSelector.columnNumber &&
				selectorEquals(otherDesendantSelector));
	}

	private boolean checkGeneralEquality(Object obj) {
		if (obj == null)
			return false;
		if (obj == this)
			return true;
		if (!(obj instanceof DescendantSelector))
			return false;
		return true;
	}
	
	@Override
	public int hashCode() {
		int result = 17;
		result = 31 * result + lineNumber;
		result = 31 * result + columnNumber;
		result = 31 * result + (parentSelector == null ? 0 : parentSelector.hashCode());
		result = 31 * result + (childSelector == null ? 0 : childSelector.hashCode());
		return result;
	}
	
	@Override
	public Selector clone() {
		return new DescendantSelector(this.parentSelector, this.childSelector);
	}
	
}