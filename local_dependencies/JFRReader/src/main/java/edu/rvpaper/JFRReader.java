package edu.rvpaper;

import java.io.*;
import java.nio.file.*;

import jdk.jfr.*;
import jdk.jfr.consumer.*;

import java.util.*;

public class JFRReader {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Missing argument: path-to-jfr, path-to-packages");
            System.exit(1);
        }

        if (!new File(args[0]).isFile() || !new File(args[1]).isFile()) {
            System.err.println("ERROR: Cannot find path-to-jfr or path-to-packages, File path: " + args[0]);
            System.exit(1);
        }

        String classifier = "naive";
        if (args.length >= 3)  {
            if (!args[2].equalsIgnoreCase("naive") && !args[2].equalsIgnoreCase("parent") &&
                !args[2].equalsIgnoreCase("hot") && !args[2].equalsIgnoreCase("violation") &&
                !args[2].equalsIgnoreCase("rv") && !args[2].equalsIgnoreCase("overall") &&
                !args[2].equalsIgnoreCase("gc")
            ) {
                System.err.println("classifier: naive, parent, hot, violation, rv, overall, or gc");
                System.exit(1);
            }

            if (args[2].equalsIgnoreCase("overall")) {
                if (args.length < 6) {
                    System.err.println("Missing argument: path-to-test-classes, path-to-test-methods, path-to-test-fixtures");
                    System.exit(1);
                }

                read(args[0], args[1], "overall", args[3], args[4], args[5]);
                return;
            }

            classifier = args[2];
        }

        read(args[0], args[1], classifier, "", "", "");
    }

    public static void read(String pathToJFR, String pathToPackages, String classifier, String pathToTestClasses,
                            String pathToTestMethods, String pathToTestFixtures) {
        Path path = Paths.get(pathToJFR);

        try {
            List<List<String>> allEvents = new ArrayList<>();
            List<List<String>> mainEvents = new ArrayList<>();
            Set<String> nativeMethods = new HashSet<>();

            RecordingFile f = new RecordingFile(path);
            while (f.hasMoreEvents()) {
                RecordedEvent event = f.readEvent();
                RecordedStackTrace stack = event.getStackTrace();
                if (stack != null) {
                    List<String> methods = new ArrayList<>();
                    boolean isMainThread = false;

                    if (event.hasField("sampledThread")) {
                        if (event.getThread("sampledThread").getOSName().equals("main")) {
                            isMainThread = true;
                        }
                    }

                    if (!isMainThread)
                        continue;

                    for (RecordedFrame method : stack.getFrames()) {
                        String methodName = method.getMethod().getType().getName().isEmpty() ? method.getMethod().getName() : (method.getMethod().getType().getName().replace("$", ".") + "." + method.getMethod().getName());
                        if (method.getType().equals("Native")) {
                            nativeMethods.add(methodName);
                        }

                        methods.add(methodName);
                    }

//                    allEvents.add(methods);
//                    if (isMainThread) {
                    mainEvents.add(methods);
//                    }
                }
            }

            JFRProcessor.process(classifier, allEvents, mainEvents, readFile(pathToPackages), nativeMethods,
                    readFile(pathToTestClasses), readFile(pathToTestMethods), readFile(pathToTestFixtures));
        } catch(Exception ex) {
            System.err.println("ERROR: " + ex + ", File path: " + path);
            System.exit(1);
        }
    }

    public static List<String> readFile(String fileName) {
        if (fileName.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(fileName));
        } catch(Exception ignored) {
            lines = new ArrayList<>();
        }

        return lines;
    }
}
