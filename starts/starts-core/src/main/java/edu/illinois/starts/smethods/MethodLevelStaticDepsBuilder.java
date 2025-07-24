package edu.illinois.starts.smethods;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import edu.illinois.starts.enums.TransitiveClosureOptions;
import edu.illinois.starts.helpers.ZLCHelperMethods;
import edu.illinois.starts.util.ChecksumUtil;
import edu.illinois.starts.util.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

public class MethodLevelStaticDepsBuilder {

    /** Map from every class to the methods it contains. */
    public static Map<String, Set<String>> classToMethods = new HashMap<>();

    /**
     * Map from method to the methods it directly invokes (i.e. forward dependency graph).
     * {@code (dependant => dependee)}
     */
    public static Map<String, Set<String>> callerToCalled = new HashMap<>();

    /**
     * Contains backward method level dependency graph.
     * {@code (dependee => dependant)}
     */
    public static Map<String, Set<String>> calledToCaller = new HashMap<>();

    /** Contains (backward) class to class dependency graph. */
    public static Map<String, Set<String>> backwardClassesDependencyGraph = new HashMap<>();

    /** Contains (forward) class to class dependency graph. */
    public static Map<String, Set<String>> forwardClassesDependencyGraph = new HashMap<>();

    /** Map from every class to its parents, including base class and implemented interfaces. */
    public static Map<String, Set<String>> classToSuperclass = new HashMap<>();

    /** Map from every class to its subclasses. */
    public static Map<String, Set<String>> classToSubclasses = new HashMap<>();

    public static Map<String, Set<String>> testClassToMethods = new HashMap<>();

    public static Map<String, Set<String>> testClassesToClasses = new HashMap<>();

    public static Map<String, Set<String>> classesToTestClasses = new HashMap<>();

    /** Map from method to test classes. */
    public static Map<String, Set<String>> methodToTestClasses = new HashMap<>();

    /** Map from method to test methods. */
    public static Map<String, Set<String>> methodToTestMethods = new HashMap<>();

    /** Map from every class to its checksum, where list is a tuple of (checksum for header, checksum for file). */
    public static Map<String, List<String>> classesChecksums = new HashMap<>();

    /** Map from method to its checksum. */
    private static Map<String, String> methodsCheckSum = new HashMap<>();

    private static final Logger LOGGER = Logger.getGlobal();

    /**
     * This function returns methodsCheckSum map.
     *
     * @return methodsCheckSum method to checksum mapping
     */
    public static Map<String, String> getMethodsCheckSum() {
        return methodsCheckSum;
    }

    /**
     * Check whether a class is a test class based on its name.
     * The specific way to check is up to the convention in use.
     * (e.g.: "Test" at the end, or contains "Test")
     *
     * @param className The class name to check for
     * @return Whether the class name represents a test class
     */
    private static boolean isTestClass(String className) {
        // TODO: Examine whether this implementation is appropriate, by convention, "Test" only appears at the end.
        return className.contains("Test");
    }

    /**
     * This function builds the method dependency graph for all the methods in the project.
     *
     * @param includeVars Specifies whether the method dependency graph include variables.
     */
    public static void buildMethodsGraph(boolean includeVars, boolean useThirdParty) {
        // find all .class files in project
        HashSet<String> bytecodePaths = null;
        try {
            // TODO: Search is done under current directory, are there more efficient ways to search?
            if (useThirdParty) {
                bytecodePaths = Files.walk(Paths.get("."))
                        .filter(Files::isRegularFile)
                        .filter(f -> (f.toString().endsWith(".class") &&  (f.toString().contains(".starts"
                                + File.separator + "lib-jars")
                                || f.toString().contains("target")) ))
                        .map(f -> f.normalize().toAbsolutePath().toString())
                        .collect(Collectors.toCollection(HashSet::new));
            } else {
                bytecodePaths = Files.walk(Paths.get("."))
                        .filter(Files::isRegularFile)
                        .filter(f -> (f.toString().endsWith(".class") && f.toString().contains("target")))
                        .map(f -> f.normalize().toAbsolutePath().toString())
                        .collect(Collectors.toCollection(HashSet::new));
            }
        } catch (IOException ex) {
            LOGGER.log(Level.INFO, "[ERROR] Files.walk(Paths.get(\".\")) errored out.");
            ex.printStackTrace();
        }
        // Find classToMethods, callerToCalled, classToSuperclass, classToSubclasses
        findMethodsInvoked(bytecodePaths);
        // Assumptions: 1) Test classes have "Test" in their class name. 2) Test classes are in src/test.
        Set<String> testClasses = new HashSet<>();
        for (String method : callerToCalled.keySet()) {
            String className = method.split("#|\\$")[0];
            if (isTestClass(className)) {
                testClasses.add(className);
            }
        }
        // Find Test Classes to methods
        testClassToMethods = getDepsSingleThread(testClasses);
        addReflexiveClosure(callerToCalled);
        // Inverting callerToCalled to have the dependency graph for each method
        calledToCaller = invertMap(callerToCalled);
        if (includeVars) {
            addVariableDepsToDependencyGraph();
        } else {
            // Remove any variables from keys or values i.e. pure method-level deps
            filterVariables();
        }
    }

    /**
     * Compute the following maps: classToMethods, callerToCalled, callerToCalled,
     * classToSuperclass, classToSubclasses.
     *
     * @param bytecodePaths The classpath in which to compute maps under.
     */
    public static void findMethodsInvoked(Set<String> bytecodePaths) {
        // Find classToMethods, classToSuperclass, classToSubclasses.
        for (String bytecodePath : bytecodePaths) {
            try (InputStream fis = Files.newInputStream(Paths.get(bytecodePath))) {
                ClassReader classReader = new ClassReader(fis);
                ClassToMethodsCollectorCV classToMethodsVisitor = new ClassToMethodsCollectorCV(
                        classToMethods, classToSuperclass, classToSubclasses);
                classReader.accept(classToMethodsVisitor, ClassReader.SKIP_DEBUG);
            } catch (IOException e) {
                LOGGER.log(Level.INFO, "[ERROR] cannot parse file: " + bytecodePath);
            }
        }

        // Find callerToCalled map.
        for (String bytecodePath : bytecodePaths) {
            try (InputStream fis = Files.newInputStream(new File(bytecodePath).toPath())) {
                ClassReader classReader = new ClassReader(fis);
                MethodCallCollectorCV methodClassVisitor = new MethodCallCollectorCV(callerToCalled,
                        classToSuperclass, classToSubclasses, classToMethods);
                classReader.accept(methodClassVisitor, ClassReader.SKIP_DEBUG);
            } catch (IOException e) {
                LOGGER.log(Level.INFO, "[ERROR] cannot parse file: " + bytecodePath);
            }
        }

        // Deal with test class in a special way, all the @test method in hierarchy should be considered.
        for (String superClass : classToSubclasses.keySet()) {
            if (superClass.contains("Test")) {
                for (String subClass : classToSubclasses.getOrDefault(superClass, new HashSet<>())) {
                    for (String methodSig : classToMethods.getOrDefault(superClass, new HashSet<>())) {
                        String subClassKey = subClass + "#" + methodSig;
                        String superClassKey = superClass + "#" + methodSig;
                        callerToCalled.computeIfAbsent(subClassKey, k -> new TreeSet<>()).add(superClassKey);
                    }
                }
            }
        }
    }

    /**
     * This function Computes and returns the methodToTestClasses map.
     *
     * @return methodToTestClasses method to test classes mapping
     */
    public static Map<String, Set<String>> computeMethodToTestClasses() {
        methodToTestClasses = invertMap(testClassToMethods);
        return methodToTestClasses;
    }

    /**
     * This function computes methods checksums for the given classes and returns a
     * map containing them.
     *
     * @param classes The classes in which to compute method checksums for
     * @param loader Java class loader
     * @return methodsCheckSum method to checksum mapping
     */
    public static Map<String, String> getMethodsChecksumsForClasses(Set<String> classes, ClassLoader loader) {
        // Loop over all the classes, and compute checksums for each method in each class
        Map<String, String> computedMethodsChecksums = new HashMap<>();

        for (String className : classes) {
            // Parse class file
            String klas = ChecksumUtil.toClassOrJavaName(className, false);
            URL url = loader.getResource(klas);

            String path = url.getPath();
            if (path.contains("jar!")) {
                // Don't checksum library classes
                continue;
            }

            ClassNode node = new ClassNode(Opcodes.ASM5);
            ClassReader reader;
            try {
                reader = new ClassReader(new FileInputStream(path));
            } catch (IOException exception) {
                LOGGER.log(Level.INFO, "[ERROR] reading class: " + path);
                continue;
            }

            String methodChecksum;
            reader.accept(node, ClassReader.SKIP_DEBUG);
            List<MethodNode> methods = node.methods;
            // Looping over all the methods in the class, and computing the checksum for
            // each method
            for (MethodNode method : methods) {
                String methodContent = ZLCHelperMethods.printMethodContent(method);
                try {
                    methodChecksum = ChecksumUtil.computeStringChecksum(methodContent);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }

                computedMethodsChecksums.put(
                        className + "#" + method.name + method.desc.substring(0, method.desc.indexOf(")") + 1),
                        methodChecksum);
            }
        }
        return computedMethodsChecksums;
    }

    /**
     * This function computes all classes in a project.
     *
     * @return classes set of classes in the project
     */
    public static Set<String> getClasses() {
        return new HashSet<>(classToMethods.keySet());
    }

    /**
     * This function computes checksums for all classes in a project.
     *
     * @param loader TODO
     * @param cleanBytes TODO
     * @return classesChecksums mapping of classes to their checksums
     */
    public static Map<String, List<String>> computeClassesChecksums(ClassLoader loader, boolean cleanBytes) {
        // Looping over all the classes, and computing the checksum for each method in each class
        for (String className : classToMethods.keySet()) {
            // Computing the checksum for the class file
            List<String> classPartsChecksums = new ArrayList<>();
            String klas = ChecksumUtil.toClassOrJavaName(className, false);
            URL url = loader.getResource(klas);
            String path = url.getPath();
            if (path.contains("jar!")) {
                // Don't checksum library classes
                continue;
            }

            ChecksumUtil checksumUtil = new ChecksumUtil(cleanBytes);
            String classCheckSum = checksumUtil.computeSingleCheckSum(url);
            classPartsChecksums.add(classCheckSum);

            // Computing the checksum for the class headers
            ClassNode node = new ClassNode(Opcodes.ASM5);
            ClassReader reader;
            try (FileInputStream fis = new FileInputStream(path)) {
                try {
                    reader = new ClassReader(fis);
                } catch (IOException exception) {
                    LOGGER.log(Level.INFO, "[ERROR] reading class file: " + path, exception);
                    continue;
                }
                reader.accept(node, ClassReader.SKIP_DEBUG);
            } catch (IOException exception) {
                LOGGER.log(Level.INFO, "[ERROR] reading class file: " + path, exception);
            }

            String classHeaders = getClassHeaders(node);
            String headersCheckSum;
            try {
                headersCheckSum = ChecksumUtil.computeStringChecksum(classHeaders);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
            classPartsChecksums.add(headersCheckSum);
            classesChecksums.put(className, classPartsChecksums);
        }
        return classesChecksums;
    }

    // print class header info (e.g., access flags, inner classes, etc)
    public static String getClassHeaders(ClassNode node) {
        Printer printer = new Textifier(Opcodes.ASM5) {
            @Override
            public Textifier visitField(int access, String name, String desc,
                    String signature, Object value) {
                return new Textifier();
            }

            @Override
            public Textifier visitMethod(int access, String name, String desc,
                    String signature, String[] exceptions) {
                return new Textifier();
            }
        };
        StringWriter sw = new StringWriter();
        TraceClassVisitor classPrinter = new TraceClassVisitor(null, printer,
                new PrintWriter(sw));
        node.accept(classPrinter);

        return sw.toString();
    }

    /*
     * This function computes the classes dependency graph.
     * It is a map from class to a set of classes that it depends on through
     * inheritance and uses
     */
    public static Map<String, Set<String>> constructClassesDependencyGraph() {
        for (Map.Entry<String, Set<String>> entry : callerToCalled.entrySet()) {
            String fromClass = entry.getKey().split("#")[0];
            Set<String> toClasses = new HashSet<>();

            for (String toMethod : entry.getValue()) {
                String toClass = toMethod.split("#")[0];
                toClasses.add(toClass);
            }

            if (forwardClassesDependencyGraph.containsKey(fromClass)) {
                forwardClassesDependencyGraph.get(fromClass).addAll(toClasses);
            } else {
                forwardClassesDependencyGraph.put(fromClass, toClasses);
            }
        }

        for (String className : classToSuperclass.keySet()) {
            Set<String> parents = classToSuperclass.get(className);
            forwardClassesDependencyGraph.getOrDefault(className, new HashSet<>()).addAll(parents);
        }
        addReflexiveClosure(classToMethods);
        backwardClassesDependencyGraph = invertMap(forwardClassesDependencyGraph);

        return backwardClassesDependencyGraph;
    }

    /*
     * This function computes the testClassesToClasses graph.
     */
    public static Map<String, Set<String>> constuctTestClassesToClassesGraph() {
        for (String testClass : testClassToMethods.keySet()) {
            Set<String> classes = new HashSet<>();
            for (String method : testClassToMethods.get(testClass)) {
                String className = method.split("#")[0];
                classes.add(className);
            }
            testClassesToClasses.put(testClass, classes);
        }
        return testClassesToClasses;
    }

    /*
     * This function computes the classesToTestClasses graph.
     */
    public static Map<String, Set<String>> constructClassesToTestClassesGraph() {
        classesToTestClasses = invertMap(testClassesToClasses);
        return classesToTestClasses;
    }

    /**
     * This function computes checksums for all methods in a project.
     *
     * @param loader TODO
     * @return TODO
     */
    public static Map<String, String> computeMethodsChecksum(ClassLoader loader) {
        // Loop over all classes and compute checksum for each method.
        for (String className : classToMethods.keySet()) {
            String klas = ChecksumUtil.toClassOrJavaName(className, false);
            URL url = loader.getResource(klas);
            // TODO: This is a way to mitigate the url == null bug,
            //  possibly due to having multiple class loaders, or PATH is wrong.
            if (url == null) {
                LOGGER.log(Level.WARNING, "[WARNING] Class loader does not recognize: " + klas);
                continue;
            }
            String path = url.getPath();
            if (path.contains("jar!")) {
                // Don't checksum library classes
                continue;
            }

            ClassNode node = new ClassNode(Opcodes.ASM5);
            ClassReader reader = null;

            try (FileInputStream fis = new FileInputStream(path)) {
                try {
                    reader = new ClassReader(fis);
                } catch (IOException exception) {
                    LOGGER.log(Level.INFO, "[ERROR] reading class file: " + path, exception);
                    continue;
                }
                reader.accept(node, ClassReader.SKIP_DEBUG);
            } catch (IOException exception) {
                LOGGER.log(Level.INFO, "[ERROR] reading class file: " + path, exception);
            }

            String methodChecksum;
            reader.accept(node, ClassReader.SKIP_DEBUG);
            List<MethodNode> methods = node.methods;
            // Loop over all methods in a class and compute checksum for each method.
            for (MethodNode method : methods) {
                String methodContent = ZLCHelperMethods.printMethodContent(method);
                try {
                    methodChecksum = ChecksumUtil.computeStringChecksum(methodContent);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
                methodsCheckSum.put(
                        className + "#" + method.name + method.desc.substring(0, method.desc.indexOf(")") + 1),
                        methodChecksum);
            }
        }
        return methodsCheckSum;
    }

    /**
     * TODO: Unused, consider whether to keep or discard.
     * This function computes the methodToTestMethods map.
     * For each method it computes all test methods that cover it.
     *
     * @return TODO
     */
//    public static Map<String, Set<String>> computeMethodToTestMethods() {
//        // Looping over all the methods, and computing the test methods that cover each
//        // method.
//        for (String method : calledToCaller.keySet()) {
//            if (!method.contains("Test")) {
//                // TODO: Placeholder to get rid of compilation issues.
//                Set<String> deps = getMethodDeps(method, TransitiveClosureOptions.PS3);
//                Set<String> toRemove = new HashSet<>();
//
//                for (String dep : deps) {
//                    if (!dep.contains("Test")) {
//                        toRemove.add(dep);
//                    }
//                }
//                deps.removeAll(toRemove);
//                methodToTestMethods.put(method, deps);
//            }
//        }
//        return methodToTestMethods;
//    }

    /**
     * This function computes all test methods in a project.
     *
     * @return testMethods set of methods in test classes
     */
    public static Set<String> getTestMethods() {
        Set<String> testMethods = new HashSet<>();
        for (String testMethod : methodsCheckSum.keySet()) {
            if (testMethod.contains("Test")) {
                testMethods.add(testMethod);
            }
        }
        return testMethods;
    }

    /**
     * This function add variable dependencies to the dependency graph.
     * Below is an example demonstrating this functionality:
     * Before operation:
     * (A, B, C) are classes
     * (a) is a variable
     * A -> A, B, C
     * a -> A
     * After operation:
     * A -> A, B, C, a
     * a -> A
     */
    private static void addVariableDepsToDependencyGraph() {
        for (String key : calledToCaller.keySet()) {
            if (key.endsWith(")")) { // Because methods end with ")"
                continue;
            }

            Set<String> deps = calledToCaller.get(key);
            for (String dep : deps) {
                calledToCaller.get(dep).add(key);
            }
        }
    }

    // simple DFS
    public static void getDepsDFS(String methodName, Set<String> visitedMethods) {
        if (callerToCalled.containsKey(methodName)) {
            for (String method : callerToCalled.get(methodName)) {
                if (!visitedMethods.contains(method)) {
                    visitedMethods.add(method);
                    getDepsDFS(method, visitedMethods);
                }
            }
        }
    }

    public static Set<String> computeReachability(Set<String> sources, Map<String, Set<String>> graph) {
        Set<String> toReturn = new HashSet<>();
        for (String source : sources) {
            if (toReturn.contains(source)) {
                // toReturn contains source means that source is in a subtree that was already explored.
                // The correctness is guaranteed by the recursive nature of the algorithm.
                // This serves as an optimization.
                continue;
            }
            toReturn.addAll(computeReachabilityHelper(source, graph));
        }
        return toReturn;
    }

    private static Set<String> computeReachabilityHelper(String source, Map<String, Set<String>> graph) {
        // Initialization:
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(source);
        visited.add(source);
        // Recursion:
        while (!queue.isEmpty()) {
            String current = queue.pollFirst();
            for (String node : graph.getOrDefault(current, new HashSet<>())) {
                if (!visited.contains(node)) {
                    queue.add(node);
                    visited.add(node);
                }
            }
        }

        return visited;
    }

    public static Set<String> computeTransitiveClosure(String component,
                                                       TransitiveClosureOptions closureOptions,
                                                       Map<String, Set<String>> forwardDependencyGraph,
                                                       Map<String, Set<String>> backwardDependencyGraph) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        // Initialization:
        queue.add(component);
        visited.add(component);
        // Recursion:
        while (!queue.isEmpty()) {
            String current = queue.pollFirst();
            for (String dependentComponent : backwardDependencyGraph.getOrDefault(current, new HashSet<>())) {
                if (!visited.contains(dependentComponent)) {
                    queue.add(dependentComponent);
                    visited.add(dependentComponent);
                }
            }
        }
        Set<String> toReturn = new HashSet<>(visited);
        if (closureOptions == TransitiveClosureOptions.PS2) {
            // Initialization:
            queue.add(component);
            visited = new HashSet<>();
            visited.add(component);
            // Recursion:
            while (!queue.isEmpty()) {
                String current = queue.pollFirst();
                for (String dependentComponent : forwardDependencyGraph.getOrDefault(current, new HashSet<>())) {
                    if (!visited.contains(dependentComponent)) {
                        queue.add(dependentComponent);
                        visited.add(dependentComponent);
                    }
                }
            }
        } else if (closureOptions == TransitiveClosureOptions.PS1) {
            // TODO: Implement
        }
        return toReturn;
    }

    public static Set<String> getDeps(String testClass) {
        Set<String> visited = new HashSet<>();
        for (String method : callerToCalled.keySet()) {
            if (method.startsWith(testClass + "#")) {
                visited.add(method);
                getDepsDFS(method, visited);
            }
        }
        return visited;
    }

    public static Map<String, Set<String>> getDepsSingleThread(Set<String> testClasses) {
        Map<String, Set<String>> testToMethods = new HashMap<>();
        for (String testClass : testClasses) {
            testToMethods.put(testClass, getDeps(testClass));
        }
        return testToMethods;
    }

    public static Map<String, Set<String>> getDepsMultiThread(Set<String> testClasses) {
        Map<String, Set<String>> testToMethods = new ConcurrentSkipListMap<>();
        ExecutorService service = null;
        try {
            service = Executors.newFixedThreadPool(16);
            for (final String testClass : testClasses) {
                service.submit(() -> {
                    Set<String> invokedMethods = getDeps(testClass);
                    testToMethods.put(testClass, invokedMethods);
                    // numMethodDepNodes.addAll(invokedMethods);
                });
            }
            service.shutdown();
            service.awaitTermination(5, TimeUnit.MINUTES);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
        return testToMethods;
    }

    public static Set<String> getMethodsFromHierarchies(String currentMethod, Map<String, Set<String>> hierarchies) {
        Set<String> res = new HashSet<>();
        // consider the superclass/subclass, do not have to consider the constructors
        String currentMethodSig = currentMethod.split("#")[1];
        if (!currentMethodSig.startsWith("<init>") && !currentMethodSig.startsWith("<clinit>")) {
            String currentClass = currentMethod.split("#")[0];
            for (String hyClass : hierarchies.getOrDefault(currentClass, new HashSet<>())) {
                String hyMethod = hyClass + "#" + currentMethodSig;
                res.addAll(getMethodsFromHierarchies(hyMethod, hierarchies));
                res.add(hyMethod);
            }
        }
        return res;
    }

    /**
     * Inverts a given map.
     *
     * @param mapToInvert the map to invert
     * @return the inverted map
     */
    public static Map<String, Set<String>> invertMap(Map<String, Set<String>> mapToInvert) {
        // Think of a map as a graph represented as an adjacency list.
        // This method inverts the graph by inverting all edges.
        Map<String, Set<String>> invertedMap = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : mapToInvert.entrySet()) {
            String key = entry.getKey();
            Set<String> values = entry.getValue();
            for (String value : values) {
                if (!invertedMap.containsKey(value)) {
                    invertedMap.put(value, new HashSet<>());
                }
                invertedMap.get(value).add(key);
            }
        }
        return invertedMap;
    }

    /**
     * This function adds reflexive closure to the given map.
     * Here is a simple demonstration of the effect:
     * Before operation: {@code A -> B}
     * After operation: {@code A -> A, B}
     *
     * @param mapToAddReflexiveClosure the passed mapping
     */
    public static void addReflexiveClosure(Map<String, Set<String>> mapToAddReflexiveClosure) {
        for (String method : mapToAddReflexiveClosure.keySet()) {
            mapToAddReflexiveClosure.get(method).add(method);
        }
    }

    /**
     * This function computes and returns the testClasses.
     *
     * @return testClasses
     */
    public static Set<String> computeTestClasses() {
        Set<String> testClasses = new HashSet<>();
        for (String testClass : testClassToMethods.keySet()) {
            testClasses.add(testClass);
        }
        return testClasses;
    }

    /**
     * This function computes and returns the methods.
     *
     * @return methods
     */
    public static Set<String> computeMethods() {
        Set<String> methodSigs = new HashSet<>();
        for (String keyString : methodsCheckSum.keySet()) {
            methodSigs.add(keyString);
        }
        return methodSigs;
    }

    /** Removes any variables from dependency graphs. */
    public static void filterVariables() {
        // Filter out keys and values that are variables.
        calledToCaller.keySet().removeIf(method -> !method.matches(".*\\(.*\\)"));
        calledToCaller.values().forEach(methods -> methods.removeIf(method -> !method.matches(".*\\(.*\\)")));
        // Filter from test2methods
        testClassToMethods.values().forEach(methods -> methods.removeIf(method -> !method.matches(".*\\(.*\\)")));
    }

    /**
     * TODO: Think about the relationship between this and computeTransitiveClosure.
     * Experimental method to see if we can have a transitive closure of methods here.
     * Currently, this method is not used anywhere.
     *
     * @param changedMethod the method path that changed
     * @return methods
     */
    public static Set<String> findTransitiveClosure(String changedMethod) throws Exception {
        Set<String> impactedMethods = new HashSet<>();
        Stack<String> stack = new Stack<>();
        stack.push(changedMethod);

        while (!stack.isEmpty()) {
            String method = stack.pop();
            if (calledToCaller.containsKey(method)) {
                Set<String> methodDeps = calledToCaller.getOrDefault(method, new HashSet<>());
                for (String invokedMethod : methodDeps) {
                    impactedMethods.add(invokedMethod);
                    stack.push(invokedMethod);
                }
            } else {
                throw new Exception("Method not found in the dependency graph");
            }
        }

        return impactedMethods;
    }
}
