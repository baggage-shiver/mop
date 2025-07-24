package edu.rvpaper;

import edu.rvpaper.classifier.*;

import java.util.*;

public class JFRProcessor {
    public static void process(String classifier, List<List<String>> allEvents, List<List<String>> mainEvents, List<String> projectPackages, Set<String> nativeMethods, List<String> testClasses, List<String> testMethods, List<String> testFixtures) {
        Classifier c;

        switch (classifier) {
            case "parent":
                c = new ParentClassifier(allEvents, mainEvents, projectPackages, nativeMethods);
                break;
            case "hot":
                c = new HotClassifier(allEvents, mainEvents, projectPackages, nativeMethods);
                break;
            case "violation":
                c = new ViolationClassifier(allEvents, mainEvents, projectPackages, nativeMethods);
                break;
            case "rv":
                c = new RVClassifier(allEvents, mainEvents, projectPackages, nativeMethods);
                break;
            case "overall":
                c = new OverallClassifier(allEvents, mainEvents, projectPackages, nativeMethods, testClasses, testMethods, testFixtures);
                break;
            case "gc":
                c = new GCClassifier(allEvents, mainEvents, projectPackages, nativeMethods);
                break;
            default:
                c = new NaiveClassifier(allEvents, mainEvents, projectPackages, nativeMethods);
        }

        c.classify();
        c.output();
    }
}
