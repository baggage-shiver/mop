package edu.rvpaper.classifier;

import edu.rvpaper.CSVGenerator;
import edu.rvpaper.MethodSamplePair;

import java.util.*;

public class HotClassifier extends Classifier {
    public HotClassifier(List<List<String>> allEvents, List<List<String>> mainEvents, List<String> projectPackages, Set<String> nativeMethods) {
        super(allEvents, mainEvents, projectPackages, nativeMethods);
        processEvents();
    }

    protected void processEvents() {
        Set<Category> categories = new HashSet<>(Arrays.asList(Category.RVMONITOR, Category.ASPECTJ, Category.JAVAMOP));
        HashMap<String, Integer> counter = new HashMap<>();  // Map method to frequency

        // The first time we see project or lib method, we add 1 to this method if we have a mop related method before.
        for (List<String> event : mainEvents) {
            if (!event.isEmpty()) {
                boolean found = false;
                for (String methodName : event) {
                    Category methodCategory = classifyMethod(methodName);

                    if (categories.contains(methodCategory)) {
                        found = true;
                    }
                    if (methodCategory == Category.PROJECT || methodCategory == Category.THIRD_PARTY_LIB) {
                        // First time finding a method in project or lib
                        if (found) {
                            // Found MOP related methods before
                            counter.put(methodName, counter.getOrDefault(methodName, 0) + 1);
                        }
                        break;
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
    public void output() {
        try {
            new CSVGenerator().outputHotMethods("hot-methods.txt", methodsList);
        } catch (Exception ignored) {}
    }
}
