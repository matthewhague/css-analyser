package ca.concordia.cssanalyser.refactoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.w3c.dom.Document;

import ca.concordia.cssanalyser.analyser.duplication.DuplicationDetector;
import ca.concordia.cssanalyser.analyser.duplication.items.Item;
import ca.concordia.cssanalyser.analyser.duplication.items.ItemSet;
import ca.concordia.cssanalyser.analyser.duplication.items.ItemSetList;
import ca.concordia.cssanalyser.app.FileLogger;
import ca.concordia.cssanalyser.cssmodel.StyleSheet;
import ca.concordia.cssanalyser.cssmodel.declaration.Declaration;
import ca.concordia.cssanalyser.cssmodel.declaration.ShorthandDeclaration;
import ca.concordia.cssanalyser.cssmodel.selectors.GroupingSelector;
import ca.concordia.cssanalyser.cssmodel.selectors.Selector;
import ca.concordia.cssanalyser.io.IOHelper;
import ca.concordia.cssanalyser.parser.CSSParser;
import ca.concordia.cssanalyser.parser.CSSParserFactory;
import ca.concordia.cssanalyser.parser.CSSParserFactory.CSSParserType;
import ca.concordia.cssanalyser.refactoring.dependencies.CSSDependencyDetector;
import ca.concordia.cssanalyser.refactoring.dependencies.CSSDependencyDifferenceList;
import ca.concordia.cssanalyser.refactoring.dependencies.CSSValueOverridingDependencyList;



public class RefactorDuplicationsToGroupingSelector {

	Logger LOGGER = FileLogger.getLogger(RefactorDuplicationsToGroupingSelector.class);

	private StyleSheet originalStyleSheet;
    private boolean domFreeDeps = false;

    private class RefactoringResult {
        StyleSheet newStyleSheet = null;
        boolean refactoringWasPossible = false;
    }

    /**
     * @param domFreeDeps whether to use the DOM free dependency detector
     */
    public RefactorDuplicationsToGroupingSelector(StyleSheet styleSheet,
                                                  boolean domFreeDeps) {
		originalStyleSheet = styleSheet;
        this.domFreeDeps = domFreeDeps;
	}

	public RefactorDuplicationsToGroupingSelector(StyleSheet styleSheet) {
		originalStyleSheet = styleSheet;
	}

	public StyleSheet getOriginalStyleSheet() {
		return this.originalStyleSheet;
	}

	/**
	 * Applies one grouping refactoring for the given ItemSet on the given style sheet.
	 * Doesn't touch the original given stylesheet
	 * @param styleSheetToBeRefactored
	 * @param itemset
	 * @return
	 */
	private StyleSheet groupingRefactoring(StyleSheet styleSheetToBeRefactored, ItemSet itemset) {

		GroupingSelector newGroupingSelector = itemset.getGroupingSelector();

		Set<Declaration> declarationsToBeRemoved = itemset.getDeclarationsToBeRemoved();

		// Create a new empty StyleSheet (the refactored one)
		StyleSheet refactoredStyleSheet = new StyleSheet();

		// Adding selectors to the refactored declarations
		for (Selector selectorToBeAdded : styleSheetToBeRefactored.getAllSelectors()) {
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

	/**
	 * Applies one grouping refactoring for the given ItemSet (through the constructor of this class).
	 * Doesn't touch the original given stylesheet
	 * @param itemset
	 * @return
	 */
	public StyleSheet groupingRefactoring(ItemSet itemset) {
		return groupingRefactoring(originalStyleSheet, itemset);
	}

	/**
	 * Applies a number of grouping refactorings on the given ItemSet (through the constructor of this class), until no new size reduction can be achieved.
	 * Doesn't touch the original given stylesheet.
	 * Ignores the DOM and does not check the dependencies
	 * @param MIN_SUPPORT
	 * @param folderName
	 * @param fpgrowthResults
     * @param domFreeDeps whether to calculate deps without dom if no dom
	 * @return
	 */
	public BatchGroupingRefactoringResult refactorGroupingOpportunities(final int MIN_SUPPORT, String folderName, List<ItemSetList> fpgrowthResults,  boolean writeIntermediateFiles, boolean domFreeDeps) {
		return refactorGroupingOpportunities(MIN_SUPPORT, folderName, fpgrowthResults, null, writeIntermediateFiles, domFreeDeps);
	}

	/**
	 * Applies a number of grouping refactorings on the given ItemSet (through the constructor of this class), until no new size reduction can be achieved.
	 * Doesn't touch the original given stylesheet
	 * @param MIN_SUPPORT
	 * @param folderName The folder to which the files should be written to
	 * @param fpgrowthResults
	 * @param dom
     * @param domFreeDeps whether to calculate dependencies without dom
	 * @return
	 */
	public BatchGroupingRefactoringResult refactorGroupingOpportunities(int MIN_SUPPORT, String folderName,	List<ItemSetList> fpgrowthResults, Document dom, boolean writeIntermediateFiles, boolean domFreeDeps) {

		StyleSheet stylesheetToBeRefactored = this.originalStyleSheet;

		CSSValueOverridingDependencyList originalDependencies = stylesheetToBeRefactored.getValueOverridingDependencies(dom, domFreeDeps);

		IOHelper.writeStringToFile(originalDependencies.toString(), folderName + "/orderDependencies.txt");

		TreeSet<ItemSet> itemSetsTreeSet = new TreeSet<>(new Comparator<ItemSet>() {
			@Override
			public int compare(ItemSet o1, ItemSet o2) {
				if (o1 == o2)
					return 0;

				int i = o1.getGroupingRefactoringImpact();
				int j = o2.getGroupingRefactoringImpact();

				if (i != j)
					return -Integer.compare(i, j);
				return 1;
			}

		});

		List<ItemSet> itemSetsSortedList = null;

		List<ItemSet> listOfInfeasibleRefactorings = new ArrayList<>();

		boolean firstRun = true;
		int numberOfPositiveRefactorings = 0;
		int refactoringRound = 0;
		boolean refactoringWasPossible = true;

		while (true) {

			if (refactoringWasPossible) {

                List<ItemSet> pairs = new ArrayList<>();

				refactoringRound++;
				itemSetsTreeSet.clear();
				for (ItemSetList isl : fpgrowthResults) {
					for (ItemSet is : isl) {
						if (is.getGroupingRefactoringImpact() > 0) {
							if (is.isApplicable()) {
								itemSetsTreeSet.add(is);
                            }
						}
                        // TODO: experiment with these
                        if (is.getGroupingRefactoringImpact() > 0 &&
                            is.isApplicable() &&
                            is.size() == 2) {
                            pairs.add(is);
                        }

					}
				}

                LOGGER.info("Testing " + pairs.size() + " pairs");

                // do all pairs then add infeasible to infeasible list
                int badCount = 0;
                int total = 0;
                for (ItemSet is : pairs) {
                    if (!containsItemSet(listOfInfeasibleRefactorings, is)) {
                        RefactoringResult result
                            = attemptRefactoring(stylesheetToBeRefactored,
                                                 is,
                                                 writeIntermediateFiles,
                                                 folderName,
                                                 refactoringRound,
                                                 dom,
                                                 domFreeDeps,
                                                 originalDependencies);
                        if (!result.refactoringWasPossible) {
                            listOfInfeasibleRefactorings.add(is);
                            badCount++;
                        }
                        total++;
                        LOGGER.info("Done " + total + " pairs");
                    }
                }
                LOGGER.info("Found " + badCount + " bad pairs");

                // The following is already taken care of below, but let's just
                // do it now for info
                int removedCount = 0;
                for (ItemSet is : itemSetsTreeSet) {
                    if (containsSubItemSet(listOfInfeasibleRefactorings, is)) {
                        removedCount++;
                    }
                }
                LOGGER.info("This will remove " + removedCount + " options, leaving " + (itemSetsTreeSet.size() - removedCount));

				itemSetsSortedList = new ArrayList<>(itemSetsTreeSet);

				if (firstRun) {
					firstRun = false;
					numberOfPositiveRefactorings = itemSetsSortedList.size();
				}
			}

			// Find a feasible refactoring opportunity with max impact
			ItemSet itemSetWithMaxImpact = null;
			do {
				if (itemSetsSortedList.size() == 0) {
					itemSetWithMaxImpact = null;
					break;
				}
				itemSetWithMaxImpact = itemSetsSortedList.get(0);
				itemSetsSortedList.remove(0);
			} while (containsItemSet(listOfInfeasibleRefactorings, itemSetWithMaxImpact));

			if (itemSetWithMaxImpact == null)
				// No more refactoring is possible to reduce the size
				break;

            LOGGER.info(String.format("Applying round %s of refactoring on %s to reduce %s characters.",
                    refactoringRound, stylesheetToBeRefactored.getFilePath(), itemSetWithMaxImpact.getGroupingRefactoringImpact()));

            RefactoringResult result = attemptRefactoring(stylesheetToBeRefactored,
                                                          itemSetWithMaxImpact,
                                                          writeIntermediateFiles,
                                                          folderName,
                                                          refactoringRound,
                                                          dom,
                                                          domFreeDeps,
                                                          originalDependencies);
            refactoringWasPossible = result.refactoringWasPossible;
            if(refactoringWasPossible) {
				stylesheetToBeRefactored = result.newStyleSheet;
				DuplicationDetector duplicationFinderRefacored = new DuplicationDetector(stylesheetToBeRefactored);
				duplicationFinderRefacored.findDuplications();
				fpgrowthResults = duplicationFinderRefacored.fpGrowth(MIN_SUPPORT, false);
				duplicationFinderRefacored = null;

            } else {
                listOfInfeasibleRefactorings.add(itemSetWithMaxImpact);
                LOGGER.info("Reordering was not feasible, applying the next refactoring opportunity at round " + refactoringRound);
            }
		}


		int numberOfAppliedRefactorings = refactoringRound - 1;
		return new BatchGroupingRefactoringResult(stylesheetToBeRefactored, numberOfAppliedRefactorings , numberOfPositiveRefactorings);

	}


    private RefactoringResult attemptRefactoring(StyleSheet stylesheetToBeRefactored,
                                                 ItemSet itemSet,
                                                 boolean writeIntermediateFiles,
                                                 String folderName,
                                                 int refactoringRound,
                                                 Document dom,
                                                 boolean domFreeDeps,
                                                 CSSValueOverridingDependencyList originalDependencies) {
        RefactoringResult result = new RefactoringResult();

        result.newStyleSheet = groupingRefactoring(stylesheetToBeRefactored, itemSet);

        if (writeIntermediateFiles)
            IOHelper.writeStringToFile(result.newStyleSheet.toString(),
                                       folderName + "/refactored" + refactoringRound + ".css");

        CSSParser parser = CSSParserFactory.getCSSParser(CSSParserType.LESS);
        try {
            result.newStyleSheet
                = parser.parseExternalCSS(folderName + "/refactored" + refactoringRound + ".css");
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        CSSValueOverridingDependencyList refactoredDependencies
            = result.newStyleSheet.getValueOverridingDependencies(dom, domFreeDeps);

        LOGGER.info("Checking differences in the dependencies " + refactoredDependencies);
        long startTime = System.currentTimeMillis();
        CSSDependencyDifferenceList differences = originalDependencies.getDifferencesWith(refactoredDependencies);
        long endTime = System.currentTimeMillis();
        LOGGER.info("Took " + (endTime - startTime) + "ms.");

        if (differences.size() > 0 && !differences.allMissing()) {

            LOGGER.info("Writing to file");
            startTime = System.currentTimeMillis();
            IOHelper.writeStringToFile(differences.toString(), folderName + "/dependency-differences" + refactoringRound + ".txt");
            endTime = System.currentTimeMillis();
            LOGGER.info("Took " + (endTime - startTime) + "ms.");

            LOGGER.info("Reordering needed at round " + refactoringRound);

            startTime = System.currentTimeMillis();
            RefactorToSatisfyDependencies r = new RefactorToSatisfyDependencies();
            StyleSheet refactoredAndOrdered = r.refactorToSatisfyOverridingDependencies(result.newStyleSheet, originalDependencies);
            endTime = System.currentTimeMillis();
            LOGGER.info("Reordering took " +
                        (endTime - startTime) +
                        "ms.");

            if (refactoredAndOrdered == null) { // It was not possible to satisfy constraints

                result.refactoringWasPossible = false;
            } else {

                result.refactoringWasPossible = true;

                CSSDependencyDetector dependencyDetector2 = new CSSDependencyDetector(refactoredAndOrdered, dom);

                if (writeIntermediateFiles)
                    IOHelper.writeStringToFile(refactoredAndOrdered.toString(), folderName + "/refactored-reordered" + refactoringRound + ".css");

                CSSValueOverridingDependencyList dependenciesReordered = dependencyDetector2.findOverridingDependancies(domFreeDeps);
                differences = originalDependencies.getDifferencesWith(dependenciesReordered);

                if (differences.size() > 0) {
                    LOGGER.warn("Differences in dependencies after reordering " + refactoringRound + "\n");
                    LOGGER.warn(differences.toString() + "\n");
                    IOHelper.writeStringToFile(differences.toString(), folderName + "/dependency-differences-after-reordering" + refactoringRound + ".txt");
                }

                try {
                    result.newStyleSheet = parser.parseExternalCSS(folderName + "/refactored-reordered" + refactoringRound + ".css");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } else { // If there were not difference between dependencies after grouping
            result.refactoringWasPossible = true;
        }

        return result;
    }

	private boolean containsItemSet(List<ItemSet> listOfItemSetsToCheck, ItemSet itemSet) {
		for (ItemSet is : listOfItemSetsToCheck) {
		    boolean itemSetFound = true;
			if (is.size() == itemSet.size() && is.getSupportSize() == itemSet.getSupportSize()) {
				for (Item i : is) {
					boolean itemFound = false;
					for (Item j : itemSet) {
						if (i.getFirstDeclaration().declarationEquals(j.getFirstDeclaration())) {
							itemFound = true;
							break;
						}
					}
					if (!itemFound) {
						itemSetFound = false;
						break;
					}
				}
				if (itemSetFound)
					return true;
			}
		}
		return false;
	}

	private boolean containsSubItemSet(List<ItemSet> listOfItemSetsToCheck,
                                       ItemSet itemSet) {
		for (ItemSet is : listOfItemSetsToCheck) {
		    boolean itemSetFound = true;
            for (Item i : is) {
                boolean itemFound = false;
                for (Item j : itemSet) {
                    if (i.getFirstDeclaration().declarationEquals(j.getFirstDeclaration())) {
                        itemFound = true;
                        break;
                    }
                }
                if (!itemFound) {
                    itemSetFound = false;
                    break;
                }
            }
            if (itemSetFound)
                return true;
		}
		return false;
	}

}
