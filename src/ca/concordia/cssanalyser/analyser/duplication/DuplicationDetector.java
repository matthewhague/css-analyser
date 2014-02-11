package ca.concordia.cssanalyser.analyser.duplication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import ca.concordia.cssanalyser.analyser.duplication.TypeFourDuplicationInstance.TypeIVBDuplication;
import ca.concordia.cssanalyser.analyser.duplication.apriori.Apriori;
import ca.concordia.cssanalyser.analyser.duplication.fpgrowth.FPGrowth;
import ca.concordia.cssanalyser.analyser.duplication.items.Item;
import ca.concordia.cssanalyser.analyser.duplication.items.ItemSet;
import ca.concordia.cssanalyser.analyser.duplication.items.ItemSetList;
import ca.concordia.cssanalyser.cssmodel.StyleSheet;
import ca.concordia.cssanalyser.cssmodel.declaration.Declaration;
import ca.concordia.cssanalyser.cssmodel.declaration.ShorthandDeclaration;
import ca.concordia.cssanalyser.cssmodel.declaration.value.DeclarationValue;
import ca.concordia.cssanalyser.cssmodel.selectors.BaseSelector;
import ca.concordia.cssanalyser.cssmodel.selectors.PseudoClass;
import ca.concordia.cssanalyser.cssmodel.selectors.Selector;
import ca.concordia.cssanalyser.cssmodel.selectors.SimpleSelector;
import ca.concordia.cssanalyser.dom.DOMHelper;


/**
 * This class is responsible for finding various types of duplications in a
 * style sheet. The stylesheet is kept in a <{@link StyleSheet} object in the memory.
 * 
 * @author Davood Mazinanian
 */
public class DuplicationDetector {

	private StyleSheet stylesheet;
	
	/*
	 *  This map contains all selectors for a given declaration
	 *  In apriori algorithm, this map is used in order to accelerate finding supports 
	 */
	private Map<Declaration, Item> declarationItemMap = new HashMap<>();
	
	public DuplicationDetector(StyleSheet stylesheet) {
		this.stylesheet = stylesheet;
	}
	
	private DuplicationIncstanceList typeOneDuplicationsList;
	private DuplicationIncstanceList typeTwoDuplicationsList;
	private DuplicationIncstanceList typeThreeDuplicationsList;
	private DuplicationIncstanceList typeFourADuplicationsList;
	private DuplicationIncstanceList typeFourBDuplicationsList;
	
	public DuplicationIncstanceList getTypeIDuplications() {
		return typeOneDuplicationsList;
	}
	
	public DuplicationIncstanceList getTypeIIDuplications() {
		return typeTwoDuplicationsList;
	}
	
	public DuplicationIncstanceList getTypeIIIDuplications() {
		return typeThreeDuplicationsList;
	}
	
	public DuplicationIncstanceList getTypeIVADuplications() {
		return typeFourADuplicationsList;
	}
	
	public DuplicationIncstanceList getTypeIVBDuplications() {
		return typeFourBDuplicationsList;
	}
	
	// For apriori
	private ItemSetList C1;
	
	/**
	 * Performs typeI through typeIVA duplication finding in the
	 * current stylesheet which has been given through constructor.
	 */
	public void findDuplications() {
		findTypeOneAndTwoDuplications();
		findTypeThreeDuplication();
		findTypeFourADuplication();
	}

	/**
	 * This method finds the cases in which the property and value
	 * (i.e. the declarations) are the same across different selectors. (Type I)
	 * @return An object of {@link DuplicationIncstanceList}
	 */
	private void findTypeOneAndTwoDuplications() {
		
		typeOneDuplicationsList = new DuplicationIncstanceList();
		typeTwoDuplicationsList = new DuplicationIncstanceList();
		
		C1 = new ItemSetList();
		
		// Lets get all the declarations
		List<Declaration> allDeclarations = new ArrayList<>(stylesheet.getAllDeclarations());
		
		// We don't want to repeat, being identical is a symmetric relation
		Set<Integer> visitedIdenticalDeclarations = new HashSet<>();
		Set<Integer> visitedEquivalentDeclarations = new HashSet<>();
		
		TypeOneDuplicationInstance typeOneDuplication = new TypeOneDuplicationInstance();
		TypeTwoDuplicationInstance typeTwoDuplication = new TypeTwoDuplicationInstance();
		
		int currentDeclarationIndex = -1;		
		while (++currentDeclarationIndex < allDeclarations.size()) {
			
			Declaration currentDeclaration = allDeclarations.get(currentDeclarationIndex);
		
			int checkingDecIndex = currentDeclarationIndex;
		
			/*
			 * We want to keep all current identical declarations together, and then 
			 * add them to the duplications list when we found all identical declarations 
			 * of current declaration.
			 */
			List<Declaration> currentTypeIDuplicatedDeclarations = new ArrayList<Declaration>();
			
			/*
			 * So we add current declaration to this list and add all identical
			 * declarations to this list 
			 */
			currentTypeIDuplicatedDeclarations.add(currentDeclaration);
			
			/* 
			 * Only when add the current duplication to the duplications list
			 * that we have really found a duplication
			 */
			boolean mustAddCurrentTypeIDuplication = false;
			
			
			/*
			 * As we are going to find the duplications of type II, we repeat three previous expressions
			 * for type II duplications
			 */
			List<Declaration> currentTypeIIDuplicatedDeclarations = new ArrayList<Declaration>();
			currentTypeIIDuplicatedDeclarations.add(currentDeclaration);
			boolean mustAddCurrentTypeTwoDuplication = false;
			
			// for Apriori
			Item newItem = declarationItemMap.get(currentDeclaration);
			if (newItem == null) {
				newItem = new Item(currentDeclaration);
				declarationItemMap.put(currentDeclaration, newItem);
				ItemSet itemSet = new ItemSet();
				itemSet.add(newItem);
				C1.add(itemSet);
			}
			
			while (++checkingDecIndex < allDeclarations.size()) {

				Declaration checkingDeclaration = allDeclarations.get(checkingDecIndex);
				
				boolean equals = currentDeclaration.declarationEquals(checkingDeclaration);

				if (equals && !visitedIdenticalDeclarations.contains(currentDeclarationIndex) && 
						!visitedIdenticalDeclarations.contains(checkingDecIndex) ) {
					
					// We have found type I duplication
					// We add the checkingDeclaration, it will add the Selector itself.
					currentTypeIDuplicatedDeclarations.add(checkingDeclaration);
					visitedIdenticalDeclarations.add(checkingDecIndex);
					mustAddCurrentTypeIDuplication = true;
					
					// This only used in apriori and fpgrowth
					newItem.add(checkingDeclaration);
					declarationItemMap.put(checkingDeclaration, newItem);
				}
				if (!equals && !visitedEquivalentDeclarations.contains(checkingDecIndex) && 
						(currentDeclaration.declarationIsEquivalent(checkingDeclaration))) {// || (equals && currentTypeIIDuplicatedDeclarations.size() > 1)) {
					/*
					 * We have found type II duplication
					 */
					currentTypeIIDuplicatedDeclarations.add(checkingDeclaration);
					visitedEquivalentDeclarations.add(checkingDecIndex);
					mustAddCurrentTypeTwoDuplication = true;
					
					newItem.add(checkingDeclaration);
					declarationItemMap.put(checkingDeclaration, newItem);
				}
				
			}
			
			// Only if we have at least one declaration in the list (at list one duplication)
			if (mustAddCurrentTypeIDuplication) {
				if (typeOneDuplication.hasAllSelectorsForADuplication(currentTypeIDuplicatedDeclarations)) {
					typeOneDuplication.addAllDeclarations(currentTypeIDuplicatedDeclarations);
				} else {
					typeOneDuplication = new TypeOneDuplicationInstance();
					typeOneDuplication.addAllDeclarations(currentTypeIDuplicatedDeclarations);
					typeOneDuplicationsList.addDuplication(typeOneDuplication);
				}
			}
			
			if (mustAddCurrentTypeTwoDuplication) {
				if (typeTwoDuplication.hasAllSelectorsForADuplication(currentTypeIIDuplicatedDeclarations)) {
					typeTwoDuplication.addAllDeclarations(currentTypeIIDuplicatedDeclarations);
				} else {
					typeTwoDuplication = new TypeTwoDuplicationInstance();
					typeTwoDuplication.addAllDeclarations(currentTypeIIDuplicatedDeclarations);
					typeTwoDuplicationsList.addDuplication(typeTwoDuplication);
				}
			}
			
		}

	}
	
	/**
	 * Finds typeIII duplications 
	 */
	public void findTypeThreeDuplication() {
		
		typeThreeDuplicationsList = new DuplicationIncstanceList();
		
		Set<Selector> selectors = stylesheet.getAllSelectors();
		
		for (Selector selector : selectors) {
			
			/*
			 * For each selector, we loop over the declarations to see
			 * whether a declaration could become the individual property for 
			 * one shorthand property (like margin-left which is an
			 * individual declaration of margin). We keep all individual 
			 * properties of a same type (like margin-top, -left, -right and -bottom)
			 * which are in the same selector in a set (namely currentIndividuals)
			 * and add this set to a map (named shorthandedDeclarations) which maps the name of corresponding
			 * shorthand property (margin, in our example) to the mentioned set.  
			 */
			Map<String, Set<Declaration>> shorthandedDeclarations = new HashMap<>();
			
			for (Declaration declaration : selector.getDeclarations()) {
				String property = declaration.getProperty();
				/*
				 * If a property is the individual property for one or more shorthand
				 * properties, ShorthandDeclaration#getShorthandPropertyNames() method
				 * returns a set containing the name of those shorthand properties 
				 * (for example, providing margin-left would result to margin)
				 */
				Set<String> shorthands = ShorthandDeclaration.getShorthandPropertyNames(property);
				/*
				 * We add the entry (or update the existing entry) in the HashMap, which
				 * maps the mentioned shorthand properties to the individual properties 
				 */
				for (String shorthand : shorthands) {
					Set<Declaration> currentIndividuals = shorthandedDeclarations.get(shorthand);
					if (currentIndividuals == null)
						currentIndividuals = new HashSet<>();
					currentIndividuals.add(declaration);
					shorthandedDeclarations.put(shorthand, currentIndividuals);	
				}
			}
			
			/*
			 * Then we search all the Declarations of current stylesheet
			 * to see whether one shorthand property has the equal (or equivalent)
			 */
			for (Entry<String, Set<Declaration>> entry : shorthandedDeclarations.entrySet()) {
				
				// Create a shorthand and compare it with a real shorthand
				ShorthandDeclaration virtualShorthand = new ShorthandDeclaration(entry.getKey(), new ArrayList<DeclarationValue>(), selector, -1, -1, false);
				virtualShorthand.isVirtual(true);
				for (Declaration dec : entry.getValue()) {
					virtualShorthand.addIndividualDeclaration(dec);
				}
				
				// For each real shorthand:
				for (Declaration checkingDeclaration : stylesheet.getAllDeclarations()) {
					if (checkingDeclaration instanceof ShorthandDeclaration && 
						virtualShorthand.individualDeclarationsEquivalent((ShorthandDeclaration)checkingDeclaration)) {
						
						TypeThreeDuplicationInstance duplication = new TypeThreeDuplicationInstance((ShorthandDeclaration)checkingDeclaration, entry.getValue());
						typeThreeDuplicationsList.addDuplication(duplication);
						
						// For apriori
						Item item = declarationItemMap.get(checkingDeclaration);
						
						item.add(virtualShorthand, true);
						
					}
				}
			}
		}
		
	}
	
	/**
	 * Finds type IV_A duplications
	 * @see DuplicationInstanceType
	 */
	public void findTypeFourADuplication() {
		
		typeFourADuplicationsList = new DuplicationIncstanceList();
		int currentSelectorIndex = -1;
		List<Selector> allSelectors = new ArrayList<>(stylesheet.getAllSelectors());
		Set<Integer> visitedSelectors = new HashSet<>();
		while (++currentSelectorIndex < allSelectors.size()) {
			if (visitedSelectors.contains(currentSelectorIndex))
				continue;
			Selector currentSelector = allSelectors.get(currentSelectorIndex);
			TypeFourDuplicationInstance.TypeIVADuplication newTypeIVADup = new TypeFourDuplicationInstance.TypeIVADuplication();
			newTypeIVADup.addSelector(currentSelector);
			int checkingSelectorIndex = currentSelectorIndex;
			while (++checkingSelectorIndex < allSelectors.size()) {
				if (visitedSelectors.contains(checkingSelectorIndex))
					continue;
				Selector checkingSelector = allSelectors.get(checkingSelectorIndex);
				if (currentSelector.selectorEquals(checkingSelector)) {
					visitedSelectors.add(checkingSelectorIndex);
					newTypeIVADup.addSelector(checkingSelector);
				}
			}
			if (newTypeIVADup.getSelectors().size() > 1) {
				typeFourADuplicationsList.addDuplication(newTypeIVADup);
			}
		}
		
	}
	
	/**
	 * Finds type IV_B duplications.
	 * For finding type IV_V duplication, we need a given DOM.
	 * @param dom
	 * @see DuplicationInstanceType
	 */
	public void findTypeFourBDuplication(Document dom) {
		
		typeFourBDuplicationsList = new DuplicationIncstanceList();
		
		Map<BaseSelector, NodeList> selectorNodeListMap = DOMHelper.mapStylesheetOnDocument(dom, stylesheet);
		
		List<BaseSelector> allSelectors = new ArrayList<>(stylesheet.getAllBaseSelectors());
		
		int currentSelectorIndex = -1;
		
		Set<Integer> visitedSelectors = new HashSet<>(); 
		
 		while (++currentSelectorIndex < allSelectors.size()) {
			
 			if (visitedSelectors.contains(currentSelectorIndex))
				continue;
			
			Selector currentSelector = allSelectors.get(currentSelectorIndex);

 			NodeList currentNodeList = selectorNodeListMap.get(currentSelector);
			if (currentNodeList == null)
				continue;
			TypeIVBDuplication typeIVBDuplication = new TypeIVBDuplication();
			typeIVBDuplication.addSelector(currentSelector);
			int checkingSelectorIndex = 0;
			while (++ checkingSelectorIndex < allSelectors.size()) {
				
				if (visitedSelectors.contains(checkingSelectorIndex))
					continue;
				
				Selector checkingSelector = allSelectors.get(checkingSelectorIndex);
				
				if (currentSelector.selectorEquals(checkingSelector))
					continue;
				
				NodeList checkingNodeList = selectorNodeListMap.get(checkingSelector);
				if (checkingNodeList == null)
					continue;
				
				// In case of :hover, etc:
				if (checkingSelector instanceof SimpleSelector) {
					boolean mustBreak = false;
					List<PseudoClass> pseudoClasses = ((SimpleSelector) checkingSelector).getPseudoClasses();
					for (PseudoClass ps : pseudoClasses)
						if (PseudoClass.isPseudoclassWithNoXpathEquivalence(ps.getName())) {
							if (currentSelector instanceof SimpleSelector) {
								if (!((SimpleSelector) currentSelector).getPseudoClasses().contains(ps))
									mustBreak = true;
							} else {
								mustBreak = true;
								break;
							}
						}
					if (mustBreak)
						continue;
				}

				if (currentNodeList.getLength() != 0 &&
						currentNodeList.getLength() == checkingNodeList.getLength()) {
					boolean equal = true;
					for (int i = 0; i < currentNodeList.getLength(); i++) {
						if (!currentNodeList.item(i).equals(checkingNodeList.item(i))) {
							equal = false;
							break;
						}

					}
					if (equal) {
						visitedSelectors.add(checkingSelectorIndex);
						typeIVBDuplication.addSelector(checkingSelector);
					}
				}
			}
			if (typeIVBDuplication.getSelectors().size() > 1) {
				typeFourBDuplicationsList.addDuplication(typeIVBDuplication);
			}
 		}
	}

	public List<ItemSetList> apriori(int minsup) {
		Apriori apriori = new Apriori(C1);
		return apriori.apriori(minsup);
	}

	public List<ItemSetList> fpGrowth(int minSupport) {
		List<TreeSet<Item>> itemSets = new ArrayList<>(stylesheet.getAllSelectors().size());
		
		for (Selector s : stylesheet.getAllSelectors()) {
			TreeSet<Item> currentItems = new TreeSet<>();
			for (Declaration d : s.getDeclarations()) {
				Item item = declarationItemMap.get(d);
				if (item.getSupport().size() >= minSupport) {
						currentItems.add(item);	
				}		
			}
			if (currentItems.size() > 0)
				itemSets.add(currentItems);
		}
		
		FPGrowth fpGrowth = new FPGrowth();
		return fpGrowth.mine(itemSets, minSupport);
	}

		

}