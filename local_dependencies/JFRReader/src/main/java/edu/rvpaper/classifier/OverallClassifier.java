package edu.rvpaper.classifier;

import edu.rvpaper.CSVGenerator;
import edu.rvpaper.MethodSamplePair;

import java.util.*;

public class OverallClassifier extends Classifier {

    List<String> testClasses;
    List<String> testMethods;
    List<String> testFixtures;
    int valid_events = 0;
    int valid_mop_events = 0;

    public OverallClassifier(List<List<String>> allEvents, List<List<String>> mainEvents, List<String> projectPackages, Set<String> nativeMethods, List<String> testClasses, List<String> testMethods, List<String> testFixtures) {
        super(allEvents, mainEvents, projectPackages, nativeMethods);
        this.testClasses = testClasses;
        this.testMethods = testMethods;
        this.testFixtures = testFixtures;
        processEvents();
    }

    protected void processEvents() {
        Set<Category> categories = new HashSet<>(Arrays.asList(Category.PROJECT, Category.THIRD_PARTY_LIB,
                Category.TEST, Category.TEST_METHOD, Category.TEST_FRAMEWORK, Category.TEST_FIXTURE));
        Set<Category> mopCategories = new HashSet<>(Arrays.asList(Category.JAVAMOP, Category.RVMONITOR, Category.ASPECTJ));

        HashMap<Category, Integer> counter = new HashMap<>();  // Map category to frequency

        // Move down from the stack until we see a method from the above categories
        for (List<String> event : mainEvents) {
            if (!event.isEmpty()) {
                valid_events += 1;

                boolean hasMop = false;
                boolean isAJC = false;
                boolean found = false;
                int index = 0;
                for (String methodName : event) {
                    index += 1;
                    // if event doesn't contain RV method, then we ignore it
                    Category methodCategory = classifyMethod(methodName);
                    if (categories.contains(methodCategory)) {
                        // Is project, lib, test, fixture, or framework
                        if (hasMop) {
                            if (methodCategory != Category.TEST_FIXTURE && hasTestFixture(event, index)) {
                                // Continue searching for stack for test fixture
                                switch (methodCategory) {
                                    case PROJECT:
                                        methodCategory = Category.PROJECT_AND_FIXTURE;
                                        break;
                                    case THIRD_PARTY_LIB:
                                        methodCategory = Category.THIRD_PARTY_LIB_AND_FIXTURE;
                                        break;
                                    case TEST:
                                        methodCategory = Category.TEST_AND_FIXTURE;
                                        break;
                                    case TEST_METHOD:
                                        methodCategory = Category.TEST_METHOD_AND_FIXTURE;
                                        break;
                                    case TEST_FRAMEWORK:
                                        methodCategory = Category.TEST_FRAMEWORK_AND_FIXTURE;
                                        break;
                                }
                            }

                            if (isAJC) {
                                Category newCategory = convertToAJC(methodCategory);
                                counter.put(newCategory, counter.getOrDefault(newCategory, 0) + 1);
                            } else {
                                counter.put(methodCategory, counter.getOrDefault(methodCategory, 0) + 1);
                            }
                            found = true;
                            break;
                        }
                        // If hasMop is false, that means we see a method in project/lib/test/fixture/framework without
                        // seeing MOP first.
                        // Example: ChannelApe-shopify-jdk (mop calls lib)
                    }

                    if (mopCategories.contains(methodCategory)) {
                        if (!hasMop)
                            valid_mop_events += 1;

                        hasMop = true;

                        if (methodCategory == Category.ASPECTJ) {
                            isAJC = true;
                        }
                    }

                }

                if (hasMop && !found) {
                    if (event.get(event.size() - 1).startsWith("sun.launcher.LauncherHelper")) {
                        // Other if parent is sun.launcher.LauncherHelper (related to ajc)
                        if (isAJC)
                            counter.put(Category.AJC_OTHER, counter.getOrDefault(Category.AJC_OTHER, 0) + 1);
                        else
                            counter.put(Category.OTHER, counter.getOrDefault(Category.OTHER, 0) + 1);
                    } else {
                        // Unknown if we see mop without parent
                        if (isAJC)
                            counter.put(Category.AJC_UNKNOWN, counter.getOrDefault(Category.AJC_UNKNOWN, 0) + 1);
                        else
                            counter.put(Category.UNKNOWN, counter.getOrDefault(Category.UNKNOWN, 0) + 1);
                    }
                }
            }
        }

        methodsList = new ArrayList<>();
        for (Map.Entry<Category, Integer> set : counter.entrySet()) {
            methodsList.add(new MethodSamplePair(set.getKey(), set.getValue()));
        }

        methodsList.sort((a, b) -> b.sample - a.sample);
    }

    private boolean hasTestFixture(List<String> event, int startingIndex) {
        for (int i = startingIndex; i < event.size(); i++) {
            Category methodCategory = classifyMethod(event.get(i));
            if (methodCategory == Category.TEST_FIXTURE)
                return true;
        }

        return false;
    }

    private Category convertToAJC(Category category) {
        switch (category) {
            case PROJECT:
                return Category.AJC_PROJECT;
            case THIRD_PARTY_LIB:
                return Category.AJC_THIRD_PARTY_LIB;
            case TEST:
                return Category.AJC_TEST;
            case TEST_METHOD:
                return Category.AJC_TEST_METHOD;
            case TEST_FRAMEWORK:
                return Category.AJC_TEST_FRAMEWORK;
            case TEST_FIXTURE:
                return Category.AJC_TEST_FIXTURE;
            case PROJECT_AND_FIXTURE:
                return Category.AJC_PROJECT_AND_FIXTURE;
            case THIRD_PARTY_LIB_AND_FIXTURE:
                return Category.AJC_THIRD_PARTY_LIB_AND_FIXTURE;
            case TEST_AND_FIXTURE:
                return Category.AJC_TEST_AND_FIXTURE;
            case TEST_METHOD_AND_FIXTURE:
                return Category.AJC_TEST_METHOD_AND_FIXTURE;
            case TEST_FRAMEWORK_AND_FIXTURE:
                return Category.AJC_TEST_FRAMEWORK_AND_FIXTURE;
            default:
                return Category.ASPECTJ;
        }
    }

    @Override
    public Category classifyMethod(String method) {
        if (method.startsWith("java.") || method.startsWith("jdk.") || method.startsWith("com.sun.") || method.startsWith("sun.") || method.startsWith("org.xml.sax")) {
            return Category.STANDARD_LIB;
        } else if (method.startsWith("org.aspectj.")) {
            return Category.ASPECTJ;
        } else if (method.startsWith("mop.")) {
            return Category.JAVAMOP;
        } else if (method.startsWith("com.runtimeverification")) {
            return Category.RVMONITOR;
        } else if (method.startsWith("aj.org.objectweb.asm")) {
            return Category.ASM;
        } else if (method.startsWith("org.apache.maven") || method.startsWith("org.junit") || method.startsWith("junit") || method.startsWith("org.testng")) {
            return Category.TEST_FRAMEWORK;
        }

        for (String testClass : testClasses) {
            if (method.startsWith(testClass)) {
                // Is test class
                for (String testMethod : testMethods) {
                    if (method.startsWith(testMethod)) {
                        // Is test method
                        return Category.TEST_METHOD;
                    }
                }

                for (String testFixture : testFixtures) {
                    if (method.startsWith(testFixture)) {
                        // Is test fixture
                        return Category.TEST_FIXTURE;
                    }
                }
                return Category.TEST;
            }
        }

        // Check testClass before we check project package
        for (String projectPackage : projectPackages) {
            if (method.startsWith(projectPackage)) {
                return Category.PROJECT;
            }
        }

        if (nativeMethods.contains(method)) {
            return Category.NATIVE;
        } else if ((method.startsWith("lib") && method.contains(".so.")) || method.contains("::")) {
            // Not native method but matches lib.*\.so\. or ::
            return Category.OTHER;
        }

        return Category.THIRD_PARTY_LIB;
    }

    @Override
    public void classify() {}

    @Override
    public void output() {
        try {
            // All categories
            new CSVGenerator().output("output.csv", methodsList, Category.getOverall(),
                    new String[]{valid_mop_events + "," + valid_events});
        } catch (Exception ignored) {}
    }
}