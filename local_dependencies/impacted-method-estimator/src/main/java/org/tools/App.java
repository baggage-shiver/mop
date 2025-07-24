package org.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;


public class App {
    private static boolean satisfiesConstraints(String pathStr, Set<String> constraints) {
        for (String constraint : constraints) {
            if (pathStr.contains(constraint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Update constraints by parsing the impacted classes file.
     * @param constraints constraints regarding file name.
     */
    private static void parseImpactedClassesFile(String pathStr, Set<String> constraints) {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(pathStr));
            String line = reader.readLine();
            while (line != null) {
                if (!line.trim().isEmpty()) {
                    constraints.add(line.trim().split("\\$")[0].replace('.', File.separatorChar) + ".java");
                }
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        /*
         * args[0]: Path to project/module
         * args[1]: Impacted classes file
         * args[2]: Impacted methods
         * Actually, do we really need impacted methods? No...?
         */
        // Handle parameter
        Set<String> constraints = new HashSet<>();
        if (args.length > 1 && Files.isRegularFile(Paths.get(args[1]))) {
            parseImpactedClassesFile(args[1], constraints);
        } else {
            constraints.add(".java");
        }
        // Obtain the set of files.
        HashSet<String> sourceFilePathStrs = null;
        try {
            sourceFilePathStrs = Files.walk(Paths.get(args[0]))
                    .filter(Files::isRegularFile)
                    .filter(f -> (f.toString().endsWith(".java")))
                    .map(f -> f.normalize().toAbsolutePath().toString())
                    .filter(f -> satisfiesConstraints(f, constraints))
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        // Count number of methods for each file.
        int cumulativeCounter = 0;
        for (String sourceFilePathStr : sourceFilePathStrs) {
//            System.out.println(sourceFilePathStr);
            String content = new String(Files.readAllBytes(Paths.get(sourceFilePathStr)), StandardCharsets.UTF_8);
            CompilationUnit cu = StaticJavaParser.parse(content);
            List<String> methods = new ArrayList<>();
            cu.accept(new VoidVisitorAdapter<Object>() {
                @Override
                public void visit(MethodDeclaration method, Object arg) {
                    super.visit(method, arg);
                    List<String> methods = (List<String>) arg;
                    methods.add(method.getName().toString());
//                    System.out.println(method.getName());
                }
            }, methods);
            cumulativeCounter += methods.size();
//            System.out.println(methods.size());
        }
        System.out.println(cumulativeCounter);
    }
}
