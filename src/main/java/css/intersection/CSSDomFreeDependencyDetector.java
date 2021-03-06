
package css.intersection;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;

import ca.concordia.cssanalyser.app.FileLogger;
import ca.concordia.cssanalyser.cssmodel.LocationInfo;
import ca.concordia.cssanalyser.cssmodel.StyleSheet;
import ca.concordia.cssanalyser.cssmodel.declaration.Declaration;
import ca.concordia.cssanalyser.cssmodel.declaration.value.DeclarationValue;
import ca.concordia.cssanalyser.cssmodel.selectors.BaseSelector;
import ca.concordia.cssanalyser.cssmodel.selectors.GroupingSelector;
import ca.concordia.cssanalyser.cssmodel.selectors.Selector;
import ca.concordia.cssanalyser.refactoring.dependencies.CSSInterSelectorValueOverridingDependency;
import ca.concordia.cssanalyser.refactoring.dependencies.CSSInterSelectorValueOverridingDependency.InterSelectorDependencyReason;
import ca.concordia.cssanalyser.refactoring.dependencies.CSSValueOverridingDependencyList;

public class CSSDomFreeDependencyDetector {

    private static final String PYTHON_COMMAND = "./intersection-tool.sh";

    private static Logger LOGGER
        = FileLogger.getLogger(CSSDomFreeDependencyDetector.class);

    private static class SelDec {
        public BaseSelector selector;
        public Declaration declaration;

        public SelDec(BaseSelector selector,
                      Declaration declaration) {
            this.selector = selector;
            this.declaration = declaration;
        }

        /**
         * @param sd another SelDec
         * @return true iff this declaration preceeds sd in the styleSheet
         */
        public boolean preceeds(SelDec sd) {
            LocationInfo li1 = selector.getSelectorNameLocationInfo();
            LocationInfo li2 = sd.selector.getSelectorNameLocationInfo();

            int line1 = li1.getLineNumber();
            int line2 = li2.getLineNumber();

            return ((line1 < line2) ||
                    ((line1 == line2) &&
                     (li1.getColumnNumber() < li2.getColumnNumber())));
        }

        public boolean equals(Object o) {
            if (o instanceof SelDec) {
                SelDec sd = (SelDec)o;
                return // selector equals kind of makes sense here because
                       // location is important for dependency
                       this.selector.equals(sd.selector) &&
                       this.declaration.equals(sd.declaration);
            }
            return false;
        }
    }

    private static class PropSpec {
        public String property;
        public int specificity;

        public PropSpec(String property, int specificity) {
            this.property = property;
            this.specificity = specificity;
        }

        public boolean equals(Object o) {
            if (o instanceof PropSpec) {
                PropSpec ps = (PropSpec)o;
                return this.specificity == ps.specificity &&
                       this.property.equals(ps.property);
            }
            return false;
        }

        public int hashCode() {
            return property.hashCode() + specificity;
        }
    }

    /**
     * Tasks are created then sent to Python which writes the result back later.
     * A poison task marks the end of the created tasks
     */
    private static class DependencyTask {
        public static final DependencyTask POISON = new DependencyTask();

        public SelDec sd1 = null;
        public SelDec sd2;
        public String property;

        // poison
        public DependencyTask() { }

        public DependencyTask(SelDec sd1,
                              SelDec sd2,
                              String property) {
            this.sd1 = sd1;
            this.sd2 = sd2;
            this.property = property;
        }

        public boolean isPoison() {
            return this.sd1 == null;
        }
    }


    /**
     * Commutative pair of selector strings for hash key
     */
    private static class SelPair {
        public BaseSelector sel1;
        public BaseSelector sel2;

        public SelPair() { }

        public SelPair(BaseSelector sel1, BaseSelector sel2) {
            this.sel1 = sel1;
            this.sel2 = sel2;
        }

        public boolean equals(Object o) {
            if (o instanceof SelPair) {
                SelPair s = (SelPair)o;
                // use selectorEquals since it ignores location and we don't
                // care about those for overlapping
                return ((sel1.selectorEquals(s.sel1, false) &&
                         sel2.selectorEquals(s.sel2, false)) ||
                        (sel1.selectorEquals(s.sel2, false) &&
                         sel2.selectorEquals(s.sel1, false)));
            }
            return false;
        }

        public int hashCode() {
            return sel1.selectorHashCode(false) +
                   sel2.selectorHashCode(false);
        }
    }

    // Memoize calls to overlap checker for all instances
    private static Map<SelPair, Boolean> overlapMemo
        = new HashMap<SelPair, Boolean>();
    // Temp SelPair to avoid creating objects just to check membership
    private static SelPair tempSelPair = new SelPair();


    // (p, spec) -> { ... (s, d) ... }
    // collects rules with the same property and specificity
    private Map<PropSpec, Set<SelDec>> overlapMap
        = new HashMap<PropSpec, Set<SelDec>>();
    private StyleSheet styleSheet;

    private static Process emptinessChecker = null;
    private static OutputStreamWriter empOut = null;
    private static InputStreamReader empIn = null;
    private static long totalTime = 0;
    private static ExecutorService executor = Executors.newSingleThreadExecutor();

    public CSSDomFreeDependencyDetector(StyleSheet styleSheet) {
        this.styleSheet = styleSheet;
    }


    /**
     * @return the orderings within the file that must be respected.  I.e. there
     * exists a DOM where reordering the dependencies would cause a different
     * rendering
     */
    public CSSValueOverridingDependencyList findOverridingDependencies() {
        try {
            buildOverlapMap();
            return buildDependencyList();
        } catch (IOException e) {
            LOGGER.error("IOException calculating dependencies, aborting.\n" + e);
            System.exit(-1);
            return null;
        }
    }

    /**
     * Needs to be called for program to terminate!
     */
    public static void killExecutor() {
        executor.shutdown();
    }

    private void startPython() throws IOException {
        File pythonCommand = new File(PYTHON_COMMAND);
        if (!pythonCommand.exists())
            throw new IOException("Please create " +
                                  PYTHON_COMMAND +
                                  " script to start emptiness checker tool (you probably want a script that runs \"python <path to our main.py> -e\"");

        if (emptinessChecker == null) {
            emptinessChecker =
                new ProcessBuilder().command(PYTHON_COMMAND).start();

            OutputStream out = emptinessChecker.getOutputStream();
            empOut = new OutputStreamWriter(out);

            InputStream in = emptinessChecker.getInputStream();
            empIn = new InputStreamReader(in);
        }
    }

    /**
     * After building overlapMap (buildOverlapMap()) call this function to build
     * the dependency list
     *
     * @return the dependency list
     */
    private CSSValueOverridingDependencyList buildDependencyList()
            throws IOException {
        startPython();

        LOGGER.info("Starting to find dependencies...");

        long startTime = System.currentTimeMillis();

		CSSValueOverridingDependencyList dependencies = new CSSValueOverridingDependencyList();

        // for creating tasks, finish with a poison task
        BlockingQueue<DependencyTask> tasks = new LinkedBlockingQueue<DependencyTask>();

        // first post all comparisons to python in an executor
        executor.submit(() -> {
            try {
                for (Map.Entry<PropSpec, Set<SelDec>> e : overlapMap.entrySet()) {
                    String property = e.getKey().property;
                    Set<SelDec> sds = e.getValue();

                    SelDec[] sdArray = sds.toArray(new SelDec[sds.size()]);
                    int len = sdArray.length;

                    for (int i = 0; i < len; ++i) {
                        for (int j = i + 1; j < len; ++j) {
                            SelDec sd1 = sdArray[i];
                            SelDec sd2 = sdArray[j];
                            if (!sd1.equals(sd2) &&
                                !declareSameValues(sd1.declaration, sd2.declaration)) {
                                Boolean memoRes = getMemoResult(sd1.selector,
                                                                sd2.selector);
                                if (memoRes == null) {
                                    empOut.write(sd1.selector + "\n");
                                    empOut.write(sd2.selector + "\n");
                                    tasks.add(new DependencyTask(sd1, sd2, property));
                                } else if (memoRes.equals(Boolean.TRUE)) {
                                    CSSInterSelectorValueOverridingDependency dep
                                        = makeDependency(sd1, sd2, property);
                                    synchronized (dependencies) {
                                        dependencies.add(dep);
                                    }
                                }
                            }
                        }
                    }
                }

                // force python to flush
                empOut.write(".\n");
                empOut.flush();
            } catch (IOException ex) {
                LOGGER.error("Exception generating dependency tasks!" + ex);
                System.exit(-1);
            } finally {
                tasks.add(DependencyTask.POISON);
            }
        });

        // get results while the executor above is generating them
        try {
            while (true) {
                DependencyTask t = tasks.take();
                if (t.isPoison())
                    break;

                int result = empIn.read();

                if ((char)result == 'N') {
                    CSSInterSelectorValueOverridingDependency dep
                        = makeDependency(t.sd1, t.sd2, t.property);
                    synchronized (dependencies) {
                        dependencies.add(dep);
                    }
                    setMemoResult(t.sd1.selector, t.sd2.selector, true);
                } else {
                    setMemoResult(t.sd1.selector, t.sd2.selector, false);
                }
            }
        } catch (InterruptedException ex) {
            LOGGER.error("Interrupted during task consumption: " + ex);
            System.exit(-1);
        }

        long endTime = System.currentTimeMillis();

        long thisTime = endTime - startTime;
        totalTime += thisTime;

        LOGGER.info("Calculating dependencies took " +
                    thisTime +
                    "ms (total: " +
                    totalTime +
                    "ms).  Found " +
                    dependencies.size() +
                    ".");

        return dependencies;
    }


    /**
     * NOTE: what am i not getting about css-analyser that means i have to
     * implement this (and so badly)...
     *
     * @param d1
     * @param d2
     * @return true if the two delcaration agree on their values
     */
    private boolean declareSameValues(Declaration d1, Declaration d2) {
        Set<DeclarationValue> s1 = new HashSet<DeclarationValue>();
        Set<DeclarationValue> s2 = new HashSet<DeclarationValue>();

        for (DeclarationValue v : d1.getDeclarationValues())
            s1.add(v);
        for (DeclarationValue v : d2.getDeclarationValues())
            s2.add(v);

        return s1.equals(s2);
    }


    /**
     * @param sel1
     * @param sel2
     * @return null if overlap test of pair not memoed, boolean for true if
     * there is an overlap, boolean of false if not
     */
    private Boolean getMemoResult(BaseSelector sel1,
                                  BaseSelector sel2) {
        // because buildOverlapMap is multithreaded
        synchronized (overlapMemo) {
            tempSelPair.sel1 = sel1;
            tempSelPair.sel2 = sel2;
            return overlapMemo.get(tempSelPair);
        }
    }

    /**
     * Memoizes overlap result
     *
     * @param sel1
     * @param sel2
     * @param overlap true if sel1 and sel2 can overlap
     */
    private void setMemoResult(BaseSelector sel1,
                               BaseSelector sel2,
                               boolean overlap) {
        // because buildOverlapMap is multithreaded
        synchronized (overlapMemo) {
            SelPair sp = new SelPair(sel1, sel2);
            overlapMemo.put(sp, Boolean.valueOf(overlap));
        }
    }

    /**
     * @param sd1
     * @param sd2
     * @param property the property that sd1 and sd2 pertain to
     * @return a new dependency object calculate by order of sd1 and sd2
     */
    private CSSInterSelectorValueOverridingDependency makeDependency(SelDec sd1,
                                                                     SelDec sd2,
                                                                     String property) {
        CSSInterSelectorValueOverridingDependency dep;
        if (sd1.preceeds(sd2)) {
            dep = new CSSInterSelectorValueOverridingDependency(
                            sd1.selector,
                            sd1.declaration,
                            sd2.selector,
                            sd2.declaration,
                            property,
                            InterSelectorDependencyReason.DUE_TO_CASCADING);
        } else {
            dep = new CSSInterSelectorValueOverridingDependency(
                            sd2.selector,
                            sd2.declaration,
                            sd1.selector,
                            sd1.declaration,
                            property,
                            InterSelectorDependencyReason.DUE_TO_CASCADING);
        }
        return dep;
    }


    /**
     * populates overlapMap with data from this.styleSheet
     */
    private void buildOverlapMap() {
        overlapMap.clear();

        for (Selector s : styleSheet.getAllSelectors()) {
            for (Declaration d : s.getDeclarations()) {
                if (s instanceof BaseSelector) {
                    addNewRule((BaseSelector)s, d);
                } else if (s instanceof GroupingSelector) {
                    GroupingSelector g = (GroupingSelector)s;
                    for (BaseSelector bs : g) {
                        addNewRule(bs, d);
                    }
                }
            }
        }
    }

    /**
     * Adds the rule (s, d) to the overlapMap.
     *
     * @param s a selector
     * @param d a declaration
     * @param selector_number the number of the selector containing s
     * @param declaration_number the number of the declaration
     */
    private void addNewRule(BaseSelector s,
                            Declaration d) {
        int specificity = s.getSpecificity();
        PropSpec ps = new PropSpec(d.getProperty(), specificity);
        Set<SelDec> sds = overlapMap.get(ps);
        if (sds == null) {
            sds = new HashSet<SelDec>();
            overlapMap.put(ps, sds);
        }
        sds.add(new SelDec(s, d));
    }

}
