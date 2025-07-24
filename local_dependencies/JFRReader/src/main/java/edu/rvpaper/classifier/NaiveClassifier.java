package edu.rvpaper.classifier;

import edu.rvpaper.CSVGenerator;
import edu.rvpaper.MethodSamplePair;

import java.util.*;

public class NaiveClassifier extends Classifier {

    public NaiveClassifier(List<List<String>> allEvents, List<List<String>> mainEvents, List<String> projectPackages, Set<String> nativeMethods) {
        super(allEvents, mainEvents, projectPackages, nativeMethods);
        processEvents();
    }

    private void processEvents() {
        // Consider only main events, and only the top of the stack
        HashMap<String, Integer> counter = new HashMap<>();  // Map method to frequency

        for (List<String> event : mainEvents) {
            if (!event.isEmpty()) {
                String methodName = event.get(0);
                counter.put(methodName, counter.getOrDefault(methodName, 0) + 1);
            }
        }

        methodsList = new ArrayList<>();
        for (Map.Entry<String, Integer> set : counter.entrySet()) {
            methodsList.add(new MethodSamplePair(set.getKey(), set.getValue()));
        }

        methodsList.sort((a, b) -> b.sample - a.sample);
    }
}
