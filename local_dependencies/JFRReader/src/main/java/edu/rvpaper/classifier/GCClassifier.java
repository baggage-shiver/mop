package edu.rvpaper.classifier;

import edu.rvpaper.CSVGenerator;
import edu.rvpaper.MethodSamplePair;

import java.util.*;

public class GCClassifier extends Classifier {

    int valid_events = 0;
    int gc_events = 0;
    public GCClassifier(List<List<String>> allEvents, List<List<String>> mainEvents, List<String> projectPackages, Set<String> nativeMethods) {
        super(allEvents, mainEvents, projectPackages, nativeMethods);
        processEvents();
    }

    protected void processEvents() {
        HashMap<Category, Integer> counter = new HashMap<>();  // Map method to frequency

        // Move down from the stack until we see a method from the above categories
        for (List<String> event : mainEvents) {
            if (!event.isEmpty()) {
                valid_events += 1;

                for (String methodName : event) {
                    if (methodName.equals("GC_active")) {
                        gc_events += 1;
                        break;
                    }
                }
            }
        }

        methodsList = new ArrayList<>();
    }

    @Override
    public void classify() {}

    @Override
    public void output() {
        try {
            // All categories
            new CSVGenerator().output("output.csv", methodsList, new Category[]{}, new String[]{gc_events + "," + valid_events});
        } catch (Exception ignored) {}
    }
}
