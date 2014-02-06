package ca.concordia.cssanalyser.cssmodel.selectors;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents CSS "selector1 selector2" selectors
 * selector1 and selector2 are of type {@link BaseSelector}
 * so for example, for selector1 selector2 selector 3, we have two selectors
 * one of which is again a {@link DescendantSelector} and anoter is an {@link DescendantSelector}
 * @author Davood Mazinanian
 */
public class DescendantSelector extends BaseSelector {
	
	protected final BaseSelector parentSelector;
	protected final BaseSelector childSelector; 
	
	public DescendantSelector(BaseSelector parent, BaseSelector child) {
		parentSelector = parent;
		childSelector = child;
	}
	
	/**
	 * Returns the parent selector (the selector on the left hand side
	 * of a descendant selector)
	 * @return
	 */
	public BaseSelector getParentSelector() {
		return parentSelector;
	}
	
	/**
	 * Returns the child selector (the selector on the right hand side
	 * of a descendant selector)
	 * @return
	 */
	public BaseSelector getChildSelector() {
		return childSelector;
	}
	
	@Override
	public String toString() {
		return parentSelector + " " + childSelector;
	}
	
	@Override
	public boolean selectorEquals(Selector otherSelector) {
		if (!generalEquals(otherSelector))
			return false;
		DescendantSelector otherDesendantSelector = (DescendantSelector)otherSelector;
		return parentSelector.selectorEquals(otherDesendantSelector.parentSelector) &&
				childSelector.selectorEquals(otherDesendantSelector.childSelector);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!generalEquals(obj))
			return false;;
		DescendantSelector otherDesendantSelector = (DescendantSelector)obj;
		return (lineNumber == otherDesendantSelector.lineNumber &&
				columnNumber == otherDesendantSelector.columnNumber &&
				selectorEquals(otherDesendantSelector));
	}

	private boolean generalEquals(Object obj) {
		if (obj == null)
			return false;
		if (obj == this)
			return true;
		if (!(obj instanceof DescendantSelector))
			return false;
		if (parentMedia != null) {
			DescendantSelector otherDescendantSelector = (DescendantSelector)obj;
			if (otherDescendantSelector.parentMedia == null)
				return false;
			if (!parentMedia.equals(otherDescendantSelector.parentMedia))
				return false;
		}
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
	public DescendantSelector clone() {
		return new DescendantSelector(this.parentSelector.clone(), this.childSelector.clone());
	}

	@Override
	protected String getXPathConditionsString(List<String> xpathConditions) throws UnsupportedSelectorToXPathException {
		 
		// if selector combinator is " " or ">"
		BaseSelector parent = this.getParentSelector();
		BaseSelector child = this.getChildSelector();
		String modifier = "descendant::"; // if selector is "s1 > s2"
		if (this instanceof ChildSelector) // if selector is "s1 s2"
			modifier = "";
		List<String> parentConditions = new ArrayList<>();
		String parentPrefix = parent.getXPathConditionsString(parentConditions);
		String parentXPath = generateXpath(parentPrefix, parentConditions);
		List<String> childConditions = new ArrayList<>();
		String childPrefix = child.getXPathConditionsString(childConditions);
		String childXPath = generateXpath(childPrefix, childConditions);
		
		return String.format("%s/%s%s", parentXPath, modifier, childXPath);
		
	
	}
	
}
