package ca.concordia.cssanalyser.analyser.duplication.items;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import ca.concordia.cssanalyser.cssmodel.declaration.Declaration;
import ca.concordia.cssanalyser.cssmodel.declaration.ShorthandDeclaration;
import ca.concordia.cssanalyser.cssmodel.selectors.Selector;


/**
 * This class keeps the data of a itemset, in addition to its support 
 * In our definition, every itemset is a set of declarations and
 * support means the number of selectors that have all these declarations.
 * In fact, instead of keeping the support as a pure percentage or number of supports,
 * we keep the selectors for further uses.
 * 
 * Every ItemSet is in fact a clone set.
 * Every <i>frequent</i> itemset (output of the FP-Growth or Apriori algorithms) 
 * will be one grouping refactoring opportunities.
 * 
 * @author Davood Mazinanian
 *
 */
public class ItemSet implements Set<Item>, Cloneable {
	
	private final Set<Item> itemset;
	private final Set<Selector> support;
	private ItemSetList parentItemSetList;
	
	public ItemSet() {
		itemset = new HashSet<>();
		support = new HashSet<>();
	} 
	
	public ItemSet(Set<Item> declarations, Set<Selector> support) {
		itemset = declarations;
		this.support = support;
	}
	
	public Set<Selector> getSupport() {
		return support;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this) 
			return true;
		
		if (obj == null)
			return false;
		
		if (getClass() != obj.getClass())
			return false;

		ItemSet otherObj = (ItemSet)obj;
		
//		if (itemset.equals(otherObj.itemset) &&
//				!otherObj.itemset.equals(itemset)) {
//			//itemset.equals(otherObj.itemset);
//			//otherObj.itemset.equals(itemset);
//			for (Item i : otherObj.itemset) {
//				if (!itemset.contains(i));
//					System.out.println();
//			}
//			for (Item i : itemset) {
//				if (!otherObj.itemset.contains(i));
//					System.out.println();
//			}
//		}
//		else if (itemset.equals(otherObj.itemset) &&
//				!otherObj.itemset.equals(itemset)) {
//			itemset.equals(otherObj.itemset);
//			otherObj.itemset.equals(itemset);
//		}
		
		
		
		return itemset.equals(otherObj.itemset);// || otherObj.itemset.equals(itemset);
	}

	@Override
	public int hashCode() {
		return itemset.hashCode();
	}
	
	@Override
	public ItemSet clone() {
		return new ItemSet(new HashSet<Item>(itemset), new HashSet<Selector>(support));
	}
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder("(");
		for (Item d : itemset)
			s.append(d.toString() + " - ");
		if (s.length() > 3)
			s.delete(s.length() - 3, s.length());
		s.append(") : {") ;
		if (support != null) {
			for (Selector sel : support)
				s.append(sel + ", ");
			s.delete(s.length() - 2, s.length());
		}
		s.append("}");
		return s.toString();
	}

	@Override
	public boolean add(Item e) {
		boolean itemsChanged = itemset.add(e);
		boolean supportChanged = false;
		if (itemsChanged) {
			if (itemset.size() == 1) {
				supportChanged = support.addAll(e.getSupport());
			}
			else
				supportChanged = support.retainAll(e.getSupport());
		}
		e.setParentItemSet(this);
		if (parentItemSetList != null && supportChanged)
			parentItemSetList.calculateMaxSupport();
		return itemsChanged;
		
	}

	@Override
	public boolean addAll(Collection<? extends Item> c) {
		boolean changed = false;
		for (Item i : c) {
			if (add(i))
				changed = true;
		}
		return changed;
	}

	@Override
	public void clear() {
		itemset.clear();
		support.clear();
	}

	@Override
	public boolean contains(Object o) {
		return itemset.contains(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return itemset.containsAll(c);
	}

	@Override
	public boolean isEmpty() {
		return itemset.isEmpty();
	}

	@Override
	public Iterator<Item> iterator() {
		return itemset.iterator();
	}

	@Override
	public boolean remove(Object o) {
		boolean changed = itemset.remove(o);
		if (changed) {
			rebuildSupport();
		}
		return changed;
	}

	/**
	 * Finds the intersection between the supports of
	 * all containing items.
	 */
	public void rebuildSupport() {
		support.clear();
		boolean mustUnion = true;
		for (Item i : itemset) {
			if (mustUnion) {
				support.addAll(i.getSupport());
				mustUnion = false;
			} else {
				support.retainAll(i.getSupport());
			}
		}
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		boolean changed = itemset.removeAll(c);
		if (changed) {
			rebuildSupport();
		}
		return changed;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		boolean changed = itemset.retainAll(c);
		if (changed) {
			rebuildSupport();
		}
		return changed;
	}

	@Override
	public int size() {
		return itemset.size();
	}

	@Override
	public Object[] toArray() {
		return itemset.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return itemset.toArray(a);
	}

	/**
	 * Gets the ItemSetList which contains this ItemSet
	 * @return
	 */
	public ItemSetList getParentItemSetList() {
		return parentItemSetList;
	}

	/**
	 * Gets the ItemSetList which contains this ItemSet
	 * @param parentItemSetList
	 */
	public void setParentItemSetList(ItemSetList parentItemSetList) {
		this.parentItemSetList = parentItemSetList;
	}
	
	/**
	 * This method returns the value that could be used to rank the 
	 * grouping refactoring opportunities.
	 * This value is the number of characters we save by doing this 
	 * grouping.
	 * @return
	 */
	public int getRefactoringImpact() {
		int result = 0;
		
		int newSelectorCharsLength = 0;
		// Adding length of involving selectors
		// TODO Make sure that the replacing of " " will not have an effect?
		for (Selector s : this.support)
			newSelectorCharsLength += s.toString().length();
		newSelectorCharsLength += 2 * (this.support.size() - 1); // adding grouping commas and spaces 
		newSelectorCharsLength += 3; // curly brackets
		
		int groupedDeclarationsLength = 0;
		for (Item item : this.itemset) {
			// Get the declaration with minimum chars in every item
			groupedDeclarationsLength += item.getDeclarationWithMinimumChars().toString().length() + 1; // +1 is for semicolon 
		}
		
		int realDeclarationsLength = 0;
		for (Item item : this.itemset) {
			for (Declaration declaration : item){
				// The declaration must be in the involving selectors in this refactorin opportunity
				if (this.getSupport().contains(declaration.getSelector()))
				{		
					if (declaration instanceof ShorthandDeclaration) {
						ShorthandDeclaration shorthand = (ShorthandDeclaration)declaration;
						if (shorthand.isVirtual()) {
							for (Declaration individual : shorthand.getIndividualDeclarations())
								realDeclarationsLength += individual.toString().length() + 1; // +1 is for semicolon 
						} else {
							realDeclarationsLength += declaration.toString().length() + 1;
						}
					} else {
						realDeclarationsLength += declaration.toString().length() + 1;
					}
				}
			}
		}
		
		result = realDeclarationsLength - groupedDeclarationsLength - newSelectorCharsLength;
		return result;
	}
}