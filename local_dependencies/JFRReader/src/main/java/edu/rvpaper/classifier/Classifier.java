package edu.rvpaper.classifier;

import edu.rvpaper.CSVGenerator;
import edu.rvpaper.MethodSamplePair;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Classifier {

    protected List<MethodSamplePair> methodsList;
    protected final List<List<String>> mainEvents;
    protected final List<String> projectPackages;
    protected final Set<String> nativeMethods;

    public Classifier(List<List<String>> allEvents, List<List<String>> mainEvents, List<String> projectPackages, Set<String> nativeMethods) {
        this.mainEvents = mainEvents;
        this.projectPackages = projectPackages;
        this.nativeMethods = nativeMethods;
    }

    public void classify() {
        for (MethodSamplePair pair : methodsList) {
            pair.category = classifyMethod(pair.method);
        }
    }

    public Category classifyMethod(String method) {
        if (method.startsWith("java.") || method.startsWith("jdk.") || method.startsWith("com.sun.") || method.startsWith("sun.")) {
            return Category.STANDARD_LIB;
        } else if (method.startsWith("org.aspectj.")) {
            return Category.ASPECTJ;
        } else if (method.startsWith("mop.")) {
            return Category.JAVAMOP;
        } else if (method.startsWith("com.runtimeverification.rvmonitor.")) {
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

    public void output() {
        try {
            new CSVGenerator().output("output.csv", methodsList, Category.getStandard(), null);
        } catch (Exception ignored) {}
    }
}
