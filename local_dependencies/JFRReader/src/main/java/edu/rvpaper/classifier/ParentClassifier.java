package edu.rvpaper.classifier;

import edu.rvpaper.MethodSamplePair;

import java.util.*;

public class ParentClassifier extends Classifier {
    public ParentClassifier(List<List<String>> allEvents, List<List<String>> mainEvents, List<String> projectPackages, Set<String> nativeMethods) {
        super(allEvents, mainEvents, projectPackages, nativeMethods);
        processEvents();
    }

    protected void processEvents() {
        // Consider only main events, and the following categories:
        // Project, AspectJ, JavaMOP, and other
        Set<Category> categories = new HashSet<>(Arrays.asList(Category.PROJECT, Category.ASPECTJ, Category.JAVAMOP));

        HashMap<String, Integer> counter = new HashMap<>();  // Map method to frequency

        // Move down from the stack until we see a method from the above categories
        for (List<String> event : mainEvents) {
            if (!event.isEmpty()) {
                boolean found = false;
                String foundMethodName = "";
                for (String methodName : event) {
                    Category methodCategory = classifyMethod(methodName);
                    if (categories.contains(methodCategory)) {
                        if (!found || methodCategory != Category.PROJECT) {
                            // First time finding a method in the above categories
                            // Or the parent method category is AspectJ or JavaMOP
                            foundMethodName = methodName;
                        }

                        found = true;
                    }
                }

                if (!found) {
                    // No method from the stack is from the above categories
                    counter.put(event.get(0), counter.getOrDefault(event.get(0), 0) + 1);
                } else {
                    counter.put(foundMethodName, counter.getOrDefault(foundMethodName, 0) + 1);
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
        Set<Category> categories = new HashSet<>(Arrays.asList(Category.PROJECT, Category.ASPECTJ, Category.JAVAMOP));

        for (MethodSamplePair pair : methodsList) {
            Category category = classifyMethod(pair.method);
            pair.category = categories.contains(category) ? category : Category.OTHER;
        }
    }
}
