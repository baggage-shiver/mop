package edu.rvpaper.classifier;

import edu.rvpaper.CSVGenerator;
import edu.rvpaper.MethodSamplePair;

import java.util.*;

public class ViolationClassifier extends Classifier {

    public ViolationClassifier(List<List<String>> allEvents, List<List<String>> mainEvents, List<String> projectPackages, Set<String> nativeMethods) {
        super(allEvents, mainEvents, projectPackages, nativeMethods);
        processEvents();
    }

    private void processEvents() {
        // Map stack to RVMONITOR, ASPECTJ, JAVAMOP, VIOLATION, or PROJECT
        Set<Category> categories = new HashSet<>(Arrays.asList(Category.RVMONITOR, Category.ASPECTJ, Category.JAVAMOP));
        HashMap<String, Integer> counter = new HashMap<>();  // Map method to frequency

        for (List<String> event : mainEvents) {
            if (!event.isEmpty()) {
                boolean found = false;
                String mopMethodName = "";
                for (String methodName : event) {
                    if (methodName.equals("com.runtimeverification.rvmonitor.java.rt.ViolationRecorder.getLineOfCode")) {
                        // Violation printing related
                        counter.put("violation", counter.getOrDefault("violation", 0) + 1);
                        mopMethodName = "";
                        found = true;
                        break;
                    } else {
                        Category category = classifyMethod(methodName);
                        if (!found && categories.contains(category)) {
                            // is MOP or project related
                            mopMethodName = methodName;
                            found = true;
                        } else if (category == Category.PROJECT) {
                            if (mopMethodName.isEmpty()) {
                                counter.put(methodName, counter.getOrDefault(methodName, 0) + 1);
                            } else {
                                counter.put(mopMethodName, counter.getOrDefault(mopMethodName, 0) + 1);
                                mopMethodName = "";
                            }
                            found = true;
                            break;
                        }
                    }
                }

                if (!found) {
                    // Empty method name represents not found
                    counter.put("", counter.getOrDefault("", 0) + 1);
                } else {
                    if (!mopMethodName.isEmpty()) {
                        counter.put(mopMethodName, counter.getOrDefault(mopMethodName, 0) + 1);
                    }
                }
            }
        }

        methodsList = new ArrayList<>();
        for (Map.Entry<String, Integer> set : counter.entrySet()) {
            methodsList.add(new MethodSamplePair(set.getKey(), set.getValue()));
        }

        methodsList.sort((a, b) -> b.sample - a.sample);
    }

    @Override
    public void classify() {
        for (MethodSamplePair pair : methodsList) {
            if (pair.method.isEmpty()) {
                pair.category = Category.OTHER;
            } else if (pair.method.equals("violation")){
                pair.category = Category.VIOLATION;
            } else {
                pair.category = classifyMethod(pair.method);
            }
        }
    }

    @Override
    public void output() {
        try {
            // All categories
            new CSVGenerator().output("output.csv", methodsList, Category.getViolation(), null);
        } catch (Exception ignored) {}
    }
}
