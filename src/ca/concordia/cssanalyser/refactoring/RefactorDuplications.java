package ca.concordia.cssanalyser.refactoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import ca.concordia.cssanalyser.analyser.duplication.items.Item;
import ca.concordia.cssanalyser.analyser.duplication.items.ItemSet;
import ca.concordia.cssanalyser.cssmodel.StyleSheet;
import ca.concordia.cssanalyser.cssmodel.declaration.Declaration;
import ca.concordia.cssanalyser.cssmodel.declaration.ShorthandDeclaration;
import ca.concordia.cssanalyser.cssmodel.selectors.BaseSelector;
import ca.concordia.cssanalyser.cssmodel.selectors.GroupingSelector;
import ca.concordia.cssanalyser.cssmodel.selectors.Selector;



public class RefactorDuplications {
	
	/**
	 * Applies the grouping refactoring for the given ItemSet.
	 * Doesn't touch the original given stylesheet
	 * @param originalStyleSheet
	 * @param itemset
	 * @return
	 */
	public static StyleSheet groupingRefactoring(StyleSheet originalStyleSheet, ItemSet itemset) {
		
		// First create a new grouped selector for refactoring
		GroupingSelector newGroupingSelector = new GroupingSelector();
		
		// Sort selectors in this new grouping selector based on their appearance in their original file
		SortedSet<Selector> sortedSet = new TreeSet<>(new Comparator<Selector>() {

			@Override
			public int compare(Selector o1, Selector o2) {
				if (o1.getSelectorNumber() >= o2.getSelectorNumber())
					return 1;
				return -1;
			}
			
		});
		
		for (Selector s : itemset.getSupport())
				sortedSet.add(s);
		
		for (Selector selector : sortedSet) {
			if (selector instanceof GroupingSelector) {
				GroupingSelector grouping = (GroupingSelector)selector;
				for (BaseSelector baseSelector : grouping.getBaseSelectors()) {
					newGroupingSelector.add((BaseSelector)baseSelector.copyEmptySelector());
				}
			} else {
				newGroupingSelector.add((BaseSelector)selector.copyEmptySelector());
			}
		}
		// Add the media queries to the new grouping selector
		newGroupingSelector.addMediaQueryLists(itemset.getMediaQueryLists());
		
		// Add declarations to this new grouping selector
		// We want to add declarations based on their appearance in the original file
		
		SortedSet<Declaration> sortedDeclarations = new TreeSet<>(new Comparator<Declaration>() {
			@Override
			public int compare(Declaration o1, Declaration o2) {
				if (o1.getDeclarationNumber() >= o2.getDeclarationNumber())
					return 1;
				return -1;
			}
		});
		
		// Have a place to mark declarations to be removed from the original selector
		Set<Declaration> declarationsToBeRemoved = new HashSet<>();
		for (Item currentItem : itemset) {

			sortedDeclarations.add(currentItem.getDeclarationWithMinimumChars());
			
			//Mark declarations to be deleted from the original StyleSheet
			for (Declaration currentDeclaration : currentItem) {
				if (itemset.supportContains(currentDeclaration.getSelector())) {
					if (currentDeclaration instanceof ShorthandDeclaration && ((ShorthandDeclaration)currentDeclaration).isVirtual()) {
						for (Declaration individual : ((ShorthandDeclaration)currentDeclaration).getIndividualDeclarations())
							declarationsToBeRemoved.add(individual);
					} else {
						declarationsToBeRemoved.add(currentDeclaration);
					}
				}
			}
		}
		
		
		for (Declaration declaration : sortedDeclarations)
			newGroupingSelector.addDeclaration(declaration.clone());
		
		// Create a new empty StyleSheet (the refactored one)
		StyleSheet refactoredStyleSheet = new StyleSheet();
		
		// Adding selectors to the refactored declarations
		for (Selector selectorToBeAdded : originalStyleSheet.getAllSelectors()) {
			Selector newSelector = selectorToBeAdded.copyEmptySelector();
			// Only add declaration which are not marked to the refactored stylesheet
			for (Declaration d : selectorToBeAdded.getDeclarations()) {
				if (d instanceof ShorthandDeclaration) {
					if (((ShorthandDeclaration)d).isVirtual())
						continue;
				}
				if (!declarationsToBeRemoved.contains(d)) {
					newSelector.addDeclaration(d.clone()); 
				}
				
			}
			refactoredStyleSheet.addSelector(newSelector);
		}
		
		// Add the new grouping selector at the end of the refactored stylesheet
		refactoredStyleSheet.addSelector(newGroupingSelector);
		
		// Remove empty selectors from refactored stylesheet
		List<Selector> selectorsToBeRemoved = new ArrayList<>(); 
		for (Selector selector : refactoredStyleSheet.getAllSelectors()) {
			if (selector.getNumberOfDeclarations() == 0)
				selectorsToBeRemoved.add(selector);
		}
		refactoredStyleSheet.removeSelectors(selectorsToBeRemoved);
		
		return refactoredStyleSheet;

	}

}
