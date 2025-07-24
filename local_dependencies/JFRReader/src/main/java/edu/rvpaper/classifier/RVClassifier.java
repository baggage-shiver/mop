package edu.rvpaper.classifier;

import edu.rvpaper.CSVGenerator;
import edu.rvpaper.MethodSamplePair;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

public class RVClassifier extends Classifier {

    private TreeMap<String, Integer> methodFrequency = new TreeMap<>();
    int valid_events = 0;
    public RVClassifier(List<List<String>> allEvents, List<List<String>> mainEvents, List<String> projectPackages, Set<String> nativeMethods) {
        super(allEvents, mainEvents, projectPackages, nativeMethods);
        processEvents();
    }

    protected void processEvents() {
        // Consider only main events, and the following categories:
        // Project, AspectJ, JavaMOP, and other
        Set<Category> categories = new HashSet<>(Arrays.asList(Category.PROJECT, Category.ASPECTJ_INSTRUMENTATION, Category.ASPECTJ_RUNTIME, Category.ASPECTJ_OTHER, Category.VIOLATION, Category.RVMONITOR, Category.MONITORING, Category.SPECIFICATION, Category.LOCK, Category.MOP_BASE_ASPECT));
        Set<Category> aspectCategories = new HashSet<>(Arrays.asList(Category.ASPECTJ_INSTRUMENTATION, Category.ASPECTJ_RUNTIME, Category.ASPECTJ_OTHER));
        Set<Category> mopCategories = new HashSet<>(Arrays.asList(Category.MONITORING, Category.SPECIFICATION, Category.RVMONITOR));

        HashMap<Category, Integer> counter = new HashMap<>();  // Map method to frequency

        // Move down from the stack until we see a method from the above categories
        for (List<String> event : mainEvents) {
            if (!event.isEmpty()) {
                valid_events += 1;

                boolean found = false;
                Category foundCategory = Category.UNKNOWN;
                for (String methodName : event) {
                    Category methodCategory = classifyMethod(methodName);
                    if (categories.contains(methodCategory)) {
                        if (!found || methodCategory != Category.PROJECT) {
                            // First time finding a method in the above categories
                            // Or the parent method category is not project
                            if (foundCategory == Category.LOCK) {
                                if (methodCategory == Category.RVMONITOR) {
                                    // RVMONITOR -> LOCK
                                    foundCategory = Category.RVMONITOR_LOCK;
                                    break;
                                } else if (methodCategory == Category.MONITORING) {
                                    // MONITORING -> LOCK
                                    foundCategory = Category.MONITORING_LOCK;
                                    break;
                                }
                            }

                            if (mopCategories.contains(methodCategory)) {
                                if (aspectCategories.contains(foundCategory)) {
                                    // MONITORING/SPECIFICATION/RVMONITOR -> AJC
                                    foundCategory = Category.MOP_AND_AJC;
                                } else {
                                    // MOP categories, don't need to check parent
                                    foundCategory = methodCategory;
                                }

                                found = true;
                                break;
                            }

                            if (methodCategory == Category.VIOLATION) {
                                // Violation printing
                                foundCategory = methodCategory;
                                found = true;
                                break;
                            }
                            foundCategory = methodCategory;
                        }
                        methodFrequency.put(methodName, methodFrequency.getOrDefault(methodName, 0) + 1);
                        found = true;
                    }
                }

                if (!found) {
                    // No method from the stack is from the above categories
                    counter.put(Category.OTHER, counter.getOrDefault(Category.OTHER, 0) + 1);
                } else {
                    counter.put(foundCategory, counter.getOrDefault(foundCategory, 0) + 1);
                }
            }
        }

        methodsList = new ArrayList<>();
        for (Map.Entry<Category, Integer> set : counter.entrySet()) {
            methodsList.add(new MethodSamplePair(set.getKey(), set.getValue()));
        }

        methodsList.sort((a, b) -> b.sample - a.sample);
    }

    @Override
    public Category classifyMethod(String method) {
        if (method.startsWith("java.util.concurrent.locks")) {
            return Category.LOCK;
        } else if (method.startsWith("mop.BaseAspect")) {
            return Category.MOP_BASE_ASPECT;
        } else if (method.startsWith("java.") || method.startsWith("jdk.") || method.startsWith("com.sun.") || method.startsWith("sun.")) {
            return Category.STANDARD_LIB;
        } else if (method.startsWith("org.aspectj.")) {
            if (method.startsWith("org.aspectj.weaver")) {
                return Category.ASPECTJ_INSTRUMENTATION;
            } else if (method.startsWith("org.aspectj.runtime")) {
                return Category.ASPECTJ_RUNTIME;
            }
            return Category.ASPECTJ_OTHER;
        } else if (method.startsWith("mop.")) {
            if (method.startsWith("mop.MultiSpec")) {
                return Category.MONITORING;
            }
            return Category.SPECIFICATION;
        } else if (method.startsWith("com.runtimeverification.rvmonitor.")) {
            if (method.startsWith("com.runtimeverification.rvmonitor.java.rt.ViolationRecorder")) {
                return Category.VIOLATION;
            }
            return Category.RVMONITOR;
        } else if (method.startsWith("aj.org.objectweb.asm")) {
            return Category.ASM;
        }

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
            new CSVGenerator().output("output.csv", methodsList, Category.getRV(), new String[]{valid_events + ""});
        } catch (Exception ignored) {}
        int counter = 10;
        try (PrintWriter writer = new PrintWriter("frequent.txt")) {
            Map<String, Integer> toPrint = methodFrequency.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
            for (String method : toPrint.keySet()) {
                if (counter-- > 0) {
                    writer.println(method + "," + toPrint.get(method));
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
