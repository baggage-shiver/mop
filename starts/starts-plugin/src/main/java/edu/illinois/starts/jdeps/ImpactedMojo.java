/*
 * Copyright (c) 2015 - Present. The STARTS Team. All Rights Reserved.
 */

package edu.illinois.starts.jdeps;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import edu.illinois.starts.constants.StartsConstants;
import edu.illinois.starts.enums.Granularity;
import edu.illinois.starts.enums.TransitiveClosureOptions;
import edu.illinois.starts.helpers.FileUtil;
import edu.illinois.starts.helpers.RTSUtil;
import edu.illinois.starts.helpers.Writer;
import edu.illinois.starts.helpers.ZLCHelper;
import edu.illinois.starts.helpers.ZLCHelperMethods;
import edu.illinois.starts.smethods.MethodLevelStaticDepsBuilder;
import edu.illinois.starts.util.ChecksumUtil;
import edu.illinois.starts.util.Logger;
import edu.illinois.starts.util.Pair;
import edu.illinois.yasgl.DirectedGraph;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.surefire.booter.Classpath;

/**
 * Find all types that are impacted by a change.
 */
@Mojo(name = "impacted", requiresDirectInvocation = true, requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.TEST_COMPILE)
public class ImpactedMojo extends DiffMojo implements StartsConstants {

    /** Set this to "true" to print debug statements. */
    @Parameter(property = "debug", defaultValue = FALSE)
    protected boolean debug;

    // Class Section
    /**
     * Set this to "true" to update test dependencies on disk. The default value of "false"
     * is useful for "dry runs" where one may want to see the diff without updating
     * the test dependencies.
     */
    @Parameter(property = "updateImpactedChecksums", defaultValue = FALSE)
    private boolean updateImpactedChecksums;

    /**
     * Set this to "true" to write the surefire classpath to disk.
     * Note that the surefire classpath will also be written to disk
     * at or below log Level.FINER
     */
    @Parameter(property = "writePath", defaultValue = "false")
    private boolean writePath;

    /** Set to "true" to print newly-added classes: classes in the program that were not in the previous version. */
    @Parameter(property = "trackNewClasses", defaultValue = FALSE)
    private boolean trackNewClasses;

    /** Set to "true" to print non-impacted classes: classes in the program that were not impacted by changes. */
    @Parameter(property = "trackNonImpacted", defaultValue = FALSE)
    private boolean trackNonImpacted;

    /**
     * Parameter to determine how to compute impacted classes.
     * Possible values are PS1, PS2, PS3.
     */
    @Parameter(property = "closureOption", defaultValue = "PS3")
    private TransitiveClosureOptions closureOption;

    // Method and Hybrid section
    /**
     * Set this to "true" to compute impacted methods as well. False indicates only
     * changed methods will be computed, can be useful for debugging purposes.
     */
    @Parameter(property = "computeImpactedMethods", defaultValue = TRUE)
    private boolean computeImpactedMethods;

    /** Set this to "true" to save the new checksums of changed methods in the zlc file. */
    @Parameter(property = "updateMethodsChecksums", defaultValue = TRUE)
    private boolean updateMethodsChecksums;

    /** Determines whether to include variables in the method-level static dependencies. */
    @Parameter(property = "includeVariables", defaultValue = FALSE)
    private boolean includeVariables;

    /** Set this to "true" to compute affected test classes as well. */
    @Parameter(property = "computeAffectedTests", defaultValue = FALSE)
    private boolean computeAffectedTests;

    /** Choose which level of granularity to perform impact-change analysis. */
    @Parameter(property = "granularity", defaultValue = "CLASS")
    private Granularity granularity;

    private Logger logger;

    // Class-level data
    private Set<String> impacted;
    private Set<String> nonAffected;
    private Set<String> changed;

    // Shared data
    private Set<String> newClasses;
    private Set<String> oldClasses;

    // Method-level and Hybrid-level data
    private Set<String> changedMethods;
    private Set<String> newMethods;
    private Set<String> impactedMethods;
    private Set<String> changedClasses;
    private Set<String> affectedTestClasses;
    private Set<String> nonAffectedMethods; // This may not be needed at all
    private Map<String, String> methodsCheckSum;
    private Map<String, Set<String>> methodToTestClasses;
    private ClassLoader loader;

    // Hybrid-level
    private Map<String, List<String>> classesChecksum;
    private Map<String, Set<String>> backwardClassDependencyGraph;
    private Map<String, Set<String>> classToTestClassGraph;
    private Set<String> deletedClasses;
    private Set<String> changedClassesWithChangedHeaders;
    private Set<String> changedClassesWithoutChangedHeaders;
    private Set<String> impactedClasses;

    public void setGranularity(Granularity granularity) {
        this.granularity = granularity;
    }

    public Granularity getGranularity() {
        return granularity;
    }

    // Class-level
    public Set<String> getImpacted() {
        return Collections.unmodifiableSet(impacted);
    }

    public Set<String> getNonAffected() {
        return Collections.unmodifiableSet(nonAffected);
    }

    public Set<String> getChanged() {
        return Collections.unmodifiableSet(changed);
    }

    public Set<String> getNewClasses() {
        return Collections.unmodifiableSet(newClasses);
    }

    public Set<String> getOldClasses() {
        return Collections.unmodifiableSet(oldClasses);
    }

    public boolean getComputeImpactedMethods() {
        return computeImpactedMethods;
    }

    public void setUpdateImpactedChecksums(boolean updateImpactedChecksums) {
        this.updateImpactedChecksums = updateImpactedChecksums;
    }

    public void setTrackNewClasses(boolean trackNewClasses) {
        this.trackNewClasses = trackNewClasses;
    }

    public void setTransitiveClosureOption(TransitiveClosureOptions closureOption) {
        this.closureOption = closureOption;
    }

    // Method and Hybrid level
    public Set<String> getNonAffectedClasses() {
        Set<String> nonAffectedClasses = new HashSet<>(getAllClasses());
        Set<String> affectedClasses = getImpactedMethods().stream()
                .map(method -> method.split("#")[0].replace('/', '.'))
                .collect(Collectors.toSet());
        nonAffectedClasses.removeAll(affectedClasses);
        if (granularity == Granularity.HYBRID) {
            affectedClasses = getAffectedClasses().stream()
                    .map(className -> className.replace('/', '.'))
                    .collect(Collectors.toSet());
            nonAffectedClasses.removeAll(affectedClasses);
        }
        return Collections.unmodifiableSet(nonAffectedClasses);
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setUpdateMethodsChecksums(boolean updateChecksums) {
        this.updateMethodsChecksums = updateChecksums;
    }

    public void setComputeImpactedMethods(boolean computeImpactedMethods) {
        this.computeImpactedMethods = computeImpactedMethods;
    }

    public void setIncludeVariables(boolean includeVariables) {
        this.includeVariables = includeVariables;
    }

    public void setComputeAffectedTests(boolean computeAffectedTests) {
        this.computeAffectedTests = computeAffectedTests;
    }

    public Set<String> getAffectedMethods() {
        // TODO: Is this a bug? Where is the graph traversal?
        //  Actually it's a terminology issue, should be changed.
        Set<String> affectedMethods = new HashSet<>();
        affectedMethods.addAll(changedMethods);
        affectedMethods.addAll(newMethods);
        return Collections.unmodifiableSet(affectedMethods);
    }

    public Set<String> getImpactedMethods() {
        return Collections.unmodifiableSet(impactedMethods);
    }

    // Hybrid level
    public Set<String> getAffectedClasses() {
        Set<String> affectedClasses = new HashSet<>();
        affectedClasses.addAll(changedClassesWithChangedHeaders);
        affectedClasses.addAll(newClasses);
        return Collections.unmodifiableSet(affectedClasses);
    }

    public Set<String> getImpactedClasses() {
        return Collections.unmodifiableSet(impactedClasses);
    }

    public Set<String> getChangedClassesWithChangedHeaders() throws MojoExecutionException {
        Set<String> changedC = new HashSet<>();
        for (String c : changedClassesWithChangedHeaders) {
            URL url = loader.getResource(ChecksumUtil.toClassOrJavaName(c, false));
            String extForm = url.toExternalForm();
            changedC.add(extForm);
        }
        return Collections.unmodifiableSet(changedC);
    }

    // TODO: Wait what? There are no differences!
    public Set<String> getChangedClassesWithoutChangedHeaders() throws MojoExecutionException {
        Set<String> changedC = new HashSet<>();
        for (String c : changedClassesWithoutChangedHeaders) {
            URL url = loader.getResource(ChecksumUtil.toClassOrJavaName(c, false));
            String extForm = url.toExternalForm();
            changedC.add(extForm);
        }
        return Collections.unmodifiableSet(changedC);
    }

    public Set<String> getChangedClasses() throws MojoExecutionException {
        Set<String> changedC = new HashSet<>();
        if (granularity == Granularity.METHOD) {
            for (String c : changedClasses) {
                URL url = loader.getResource(ChecksumUtil.toClassOrJavaName(c, false));
                String extForm = url.toExternalForm();
                changedC.add(extForm);
            }
        } else if (granularity == Granularity.HYBRID) {
            for (String c : changedClassesWithoutChangedHeaders) {
                URL url = loader.getResource(ChecksumUtil.toClassOrJavaName(c, false));
                String extForm = url.toExternalForm();
                changedC.add(extForm);
            }
            for (String c : changedClassesWithChangedHeaders) {
                URL url = loader.getResource(ChecksumUtil.toClassOrJavaName(c, false));
                String extForm = url.toExternalForm();
                changedC.add(extForm);
            }
        }
        return Collections.unmodifiableSet(changedC);
    }

    public Set<String> getNonAffectedMethods() {
        return Collections.unmodifiableSet(nonAffectedMethods);
    }

    /**
     * This method first builds the method-level static dependencies by calling
     * MethodLevelStaticDepsBuilder.buildMethodsGraph().
     * Then, it computes and retrieves the methods'/classes' checksums and the mapping
     * between methods and test classes by calling
     * MethodLevelStaticDepsBuilder.computeMethodsChecksum(ClassLoader) and
     * MethodLevelStaticDepsBuilder.computeMethod2testClasses() respectively.
     * Finally, it computes the changed (and impacted) methods [through changed
     * classes] by calling runMethods(boolean).
     */
    public void execute() throws MojoExecutionException {
        Logger.getGlobal().setLoggingLevel(Level.parse(loggingLevel));
        logger = Logger.getGlobal();
        // Extract bytecode first if working with libraries
        if (useThirdParty) {
            List<String> libJars = new ArrayList<>();
            try {
                libJars = this.getProject().getCompileClasspathElements();
                libJars.addAll(this.getProject().getTestClasspathElements());
                libJars = libJars.stream().filter(lib -> lib.endsWith(".jar")).collect(Collectors.toList());
            } catch (DependencyResolutionRequiredException ex) {
                throw new MojoExecutionException("Failed to list jars.", ex);
            }
            try {
                File libJarsDir = new File(getArtifactsDir() + File.separator + "lib-jars");
                if (libJarsDir.exists()) {
                    FileUtil.recursiveDelete(libJarsDir);
                }
                libJarsDir.mkdirs();
                for (String libJar : libJars) {
                    FileUtil.extractJar(Paths.get(libJar), libJarsDir.toPath());
                }
            } catch (IOException ex) {
                throw new MojoExecutionException("Failed to extract lib-jars.", ex);
            }
        }

        if (granularity == Granularity.CLASS || granularity == Granularity.FINE) {
            Pair<Set<String>, Set<String>> data = computeChangeData(false);
            // 0. Find all classes in program
            List<String> allClasses = getAllClasses();
            if (allClasses.isEmpty()) {
                logger.log(Level.INFO, "There are no .class files in this module.");
                oldClasses = new HashSet<>();
                newClasses = new HashSet<>();
                impacted = new HashSet<>();
                return;
            }
            Writer.writeToFile(allClasses, "all-classes", getArtifactsDir());
            impacted = new HashSet<>(allClasses);
            // 1a. Find what changed and what is non-affected
            nonAffected = data == null ? new HashSet<String>() : data.getKey();
            changed = data == null ? new HashSet<String>() : data.getValue();

            // 1b. Remove nonAffected from all classes to get classes impacted by the change
            impacted.removeAll(nonAffected);

            logger.log(Level.FINEST, "CHANGED: " + changed.toString());
            logger.log(Level.FINEST, "IMPACTED: " + impacted.toString());
            // 2. Optionally find newly-added classes
            if (trackNewClasses) {
                newClasses = new HashSet<>(allClasses);
                oldClasses = ZLCHelper.getExistingClasses(getArtifactsDir(), useThirdParty);
                newClasses.removeAll(oldClasses);
                logger.log(Level.FINEST, "NEWLY-ADDED: " + newClasses.toString());
                Writer.writeToFile(newClasses, "new-classes", getArtifactsDir());
            }
            // 3. Optionally update ZLC file for next run, using all classes in the SUT
            if (updateImpactedChecksums) {
                updateForNextRun(allClasses);
            }
            // 4. Print impacted and/or write to file
            Writer.writeToFile(changed, CHANGED_CLASSES, getArtifactsDir());
            Writer.writeToFile(impacted, "impacted-classes", getArtifactsDir());
            if (trackNonImpacted) {
                Writer.writeToFile(nonAffected, "non-impacted-classes", getArtifactsDir());
            }
            logger.log(Level.INFO, "ChangedClasses: " + changed.size());
            logger.log(Level.INFO, "ImpactedClasses: " + impacted.size());
            if (granularity == Granularity.FINE) {
                MethodLevelStaticDepsBuilder.buildMethodsGraph(includeVariables, useThirdParty);
                newMethods = MethodLevelStaticDepsBuilder.computeMethods();
                changedMethods = ZLCHelper.getChangedMethods();
                computeImpactedMethods();
            }
        } else if (granularity == Granularity.METHOD) {
            loader = createClassLoader(getSureFireClassPath());
            // Build method level static dependencies
            MethodLevelStaticDepsBuilder.buildMethodsGraph(includeVariables, useThirdParty);
            methodToTestClasses = MethodLevelStaticDepsBuilder.computeMethodToTestClasses();
            methodsCheckSum = MethodLevelStaticDepsBuilder.computeMethodsChecksum(loader);
            runMethods(computeImpactedMethods);
        } else if (granularity == Granularity.HYBRID) {
            loader = createClassLoader(getSureFireClassPath());
            // Build method level static dependencies
            MethodLevelStaticDepsBuilder.buildMethodsGraph(includeVariables, useThirdParty);
            classesChecksum = MethodLevelStaticDepsBuilder.computeClassesChecksums(loader, cleanBytes);
            if (computeAffectedTests) {
                methodToTestClasses = MethodLevelStaticDepsBuilder.computeMethodToTestClasses();
            }
            runHybrid(computeImpactedMethods);
        }
    }

    /**
     * This method handles the main logic of the mojo for method-level analysis.
     * It checks if the file of dependencies exists and sets the changed
     * methods accordingly. (First run doesn't have the file of dependencies)
     * If the file does not exist, it sets the changed methods, new methods,
     * impacted test classes, old classes, changed classes, new classes and
     * non-affected methods.
     * If the file exists, it sets the changed methods and computes the impacted
     * methods and impacted test classes if impacted is true.
     * It also updates the methods checksums in the dependency file if
     * updateMethodsChecksums is true.
     *
     * @param impacted a boolean value indicating whether to compute impacted
     *                 methods and impacted test classes
     * @throws MojoExecutionException if an exception occurs while setting changed
     *                                methods
     */
    protected void runMethods(boolean impacted) throws MojoExecutionException {
        // Checking if the file of dependencies exists (first run or not)
        if (!Files.exists(Paths.get(getArtifactsDir() + METHODS_CHECKSUMS_SERIALIZED_FILE))) {
            changedMethods = new HashSet<>();
            newMethods = MethodLevelStaticDepsBuilder.computeMethods();
            oldClasses = new HashSet<>();
            changedClasses = new HashSet<>();
            newClasses = MethodLevelStaticDepsBuilder.getClasses();
            nonAffectedMethods = new HashSet<>();
            if (computeAffectedTests) {
                affectedTestClasses = MethodLevelStaticDepsBuilder.computeTestClasses();
            }
            if (impacted) {
                impactedMethods = newMethods;
            }
            // Always save the checksums in the first run
            try {
                ZLCHelperMethods.serializeMapping(methodsCheckSum, getArtifactsDir(),
                        METHODS_CHECKSUMS_SERIALIZED_FILE);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        } else {
            // First run has saved the old revision's checksums. Time to find changes.
            computeChangedMethods();
            if (impacted) {
                computeImpactedMethods();
            }
            if (computeAffectedTests) {
                computeAffectedTestClasses();
            }
            if (updateMethodsChecksums) {
                try {
                    ZLCHelperMethods.serializeMapping(methodsCheckSum, getArtifactsDir(),
                            METHODS_CHECKSUMS_SERIALIZED_FILE);
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
        }
        logInfoStatements(impacted);
    }

    /**
     * This method handles the main logic of the mojo for hybrid analysis.
     * It checks if the file of dependencies exists and sets the changed
     * methods accordingly. (First run doesn't have the file of dependencies)
     * If the file does not exist, it sets the changed methods, new methods,
     * impacted test classes, old classes, changed classes, new classes and
     * non-affected methods.
     * If the file exists, it sets the changed methods and computes the impacted
     * methods and impacted test classes if impacted is true.
     * It also updates the methods checksums in the dependency file if
     * updateMethodsChecksums is true.
     *
     * @param impacted a boolean value indicating whether to compute impacted
     *                 methods and impacted test classes
     * @throws MojoExecutionException if an exception occurs while setting changed
     *                                methods
     */
    protected void runHybrid(boolean impacted) throws MojoExecutionException {
        // Checking if the file of dependencies exists (first run)
        if (!Files.exists(Paths.get(getArtifactsDir() + METHODS_CHECKSUMS_SERIALIZED_FILE))
                && !Files.exists(Paths.get(getArtifactsDir() + CLASSES_CHECKSUM_SERIALIZED_FILE))) {
            // In the first run we compute all method checksums and save them.
            // In later runs we just compute new method checksums for changed classes
            MethodLevelStaticDepsBuilder.computeMethodsChecksum(loader);
            methodsCheckSum = MethodLevelStaticDepsBuilder.getMethodsCheckSum();
            changedMethods = new HashSet<>();
            newMethods = MethodLevelStaticDepsBuilder.computeMethods();
            newClasses = MethodLevelStaticDepsBuilder.getClasses();
            oldClasses = new HashSet<>();
            deletedClasses = new HashSet<>();
            changedClassesWithChangedHeaders = new HashSet<>();
            changedClassesWithoutChangedHeaders = new HashSet<>();
            nonAffectedMethods = new HashSet<>();

            if (computeAffectedTests) {
                affectedTestClasses = MethodLevelStaticDepsBuilder.computeTestClasses();
            }

            if (impacted) {
                impactedMethods = newMethods;
                impactedClasses = newClasses;
            }
        } else {
            backwardClassDependencyGraph = MethodLevelStaticDepsBuilder.constructClassesDependencyGraph();
            MethodLevelStaticDepsBuilder.constuctTestClassesToClassesGraph();
            if (computeAffectedTests) {
                classToTestClassGraph = MethodLevelStaticDepsBuilder.constructClassesToTestClassesGraph();
                methodToTestClasses = MethodLevelStaticDepsBuilder.computeMethodToTestClasses();
                affectedTestClasses = new HashSet<>();
            }

            setChangedAndNonAffectedMethods();

            if (impacted) {
                computeImpactedMethods();
                computeImpactedClasses();
            }
        }

        if (updateMethodsChecksums) {
            try {
                // Save class-to-checksums mapping
                ZLCHelperMethods.serializeMapping(classesChecksum, getArtifactsDir(),
                        CLASSES_CHECKSUM_SERIALIZED_FILE);
                // Save method-to-checksum mapping
                // The method-to-checksum mapping has been updated in
                // ZLCHelperMethods.getChangedDataHybrid()
                ZLCHelperMethods.serializeMapping(methodsCheckSum, getArtifactsDir(),
                        METHODS_CHECKSUMS_SERIALIZED_FILE);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }

        logInfoStatements(impacted);
    }

    /**
     * This method logs information statements about changed methods, new methods,
     * impacted test classes, new classes, old classes and changed classes.
     * If impacted is true, it also logs information about impacted methods.
     *
     * @param impacted a boolean value indicating whether to log information about
     *                 impacted methods
     */
    private void logInfoStatements(boolean impacted) {
        logger.log(Level.INFO, "ChangedMethods: " + changedMethods.size());
        logger.log(Level.INFO, "NewMethods: " + newMethods.size());
        if (impacted) {
            logger.log(Level.INFO, "ImpactedMethods: " + impactedMethods.size());
            // TODO: Currently this information is not accurate when reverting to Base RV.
            if (includeVariables) {
                logger.log(Level.INFO, "RealImpactedMethods: " + impactedMethods.stream().filter(str -> str.matches(".*\\(.*\\)")).collect(Collectors.toSet()).size());
                logger.log(Level.INFO, "VariablesInvolved: " + impactedMethods.stream().filter(str -> !str.matches(".*\\(.*\\)")).collect(Collectors.toSet()).size());
            }
            if (this.granularity == Granularity.METHOD) {
                // Number of impacted classes reasoned from impacted methods:
                logger.log(Level.INFO, "Total ImpactedClasses: " + impactedMethods.stream()
                        .map(s -> s.split("#")[0]).collect(Collectors.toSet()).size());
            } else if (this.granularity == Granularity.HYBRID) {
                // Number of impacted classes reasoned from impacted methods and impacted classes:
                Set<String> combined = new HashSet<>(impactedMethods);
                combined.addAll(impactedClasses);
                logger.log(Level.INFO, "Total ImpactedClasses: " + combined.stream()
                        .map(s -> s.split("#")[0]).collect(Collectors.toSet()).size());
                combined = new HashSet<>(impactedMethods);
                for (String clazz : impactedClasses) {
                    Set<String> methodsInClass = MethodLevelStaticDepsBuilder.classToMethods.get(clazz);
                    if (methodsInClass == null) {
                        continue;
                    }
                    combined.addAll(methodsInClass.stream().map(s -> clazz + "#" + s).collect(Collectors.toSet()));
                }
                // TODO: This count is not fully accurate, some classes do not have a mapping to its methods.
                logger.log(Level.INFO, "Total ImpactedMethods: " + combined.size());
            }
        }
        logger.log(Level.INFO, "NewClasses: " + newClasses.size());
        logger.log(Level.INFO, "OldClasses: " + oldClasses.size());
        if (computeAffectedTests) {
            logger.log(Level.INFO, "AffectedTestClasses: " + affectedTestClasses.size());
        }
        if (this.granularity == Granularity.METHOD) {
            logger.log(Level.INFO, "ChangedClasses: " + changedClasses.size());
        } else if (this.granularity == Granularity.HYBRID) {
            logger.log(Level.INFO, "DeletedClasses: " + deletedClasses.size());
            logger.log(Level.INFO, "ChangedClassesWithChangedHeaders: " + changedClassesWithChangedHeaders.size());
            logger.log(Level.INFO,
                    "ChangedClassesWithoutChangedHeaders: " + changedClassesWithoutChangedHeaders.size());
            if (impacted) {
                logger.log(Level.INFO, "ImpactedClasses: " + impactedClasses.size());
            }
        }
        if (debug) {
            if (this.granularity == Granularity.METHOD) {
                logger.log(Level.INFO, "ChangedMethods: " + changedMethods);
                logger.log(Level.INFO, "ImpactedMethods: " + impactedMethods);
                if (computeAffectedTests) {
                    logger.log(Level.INFO, "AffectedTestClasses: " + affectedTestClasses);
                }
            } else if (this.granularity == Granularity.HYBRID) {
                logger.log(Level.INFO, "ImpactedMethods: " + impactedMethods);
                logger.log(Level.INFO, "ImpactedClasses: " + impactedClasses);
                logger.log(Level.INFO, "BackwardClassDependencyGraph: " + backwardClassDependencyGraph);
                logger.log(Level.INFO, "ChangedClassesWithChangedHeaders: " + changedClassesWithChangedHeaders);
                logger.log(Level.INFO, "ChangedClassesWithoutChangedHeaders: " + changedClassesWithoutChangedHeaders);
                if (computeAffectedTests) {
                    logger.log(Level.INFO, "AffectedTestClasses: " + affectedTestClasses);
                    logger.log(Level.INFO, "ClassToTestClassGraph: " + classToTestClassGraph);
                }
            }
        }
    }

    /**
     * Sets the changed and non-affected methods by retrieving changed data using
     * the ZLCHelperMethods class and updating the relevant fields.
     * This method also updates the impacted test classes by adding test classes
     * associated with new methods.
     */
    protected void setChangedAndNonAffectedMethods() throws MojoExecutionException {
        List<Set<String>> classesData = ZLCHelperMethods.getChangedDataHybridClassLevel(classesChecksum,
                getArtifactsDir(), CLASSES_CHECKSUM_SERIALIZED_FILE);

        newClasses = classesData == null ? new HashSet<String>() : classesData.get(0);
        deletedClasses = classesData == null ? new HashSet<String>() : classesData.get(1);
        changedClassesWithChangedHeaders = classesData == null ? new HashSet<String>() : classesData.get(2);
        changedClassesWithoutChangedHeaders = classesData == null ? new HashSet<String>() : classesData.get(3);
        oldClasses = classesData == null ? new HashSet<String>() : classesData.get(4);

        List<Set<String>> methodsData = ZLCHelperMethods.getChangedDataHybridMethodLevel(newClasses, deletedClasses,
                changedClassesWithChangedHeaders,
                changedClassesWithoutChangedHeaders, MethodLevelStaticDepsBuilder.getMethodsCheckSum(), loader,
                getArtifactsDir(), METHODS_CHECKSUMS_SERIALIZED_FILE);

        methodsCheckSum = MethodLevelStaticDepsBuilder.getMethodsCheckSum();

        changedMethods = methodsData == null ? new HashSet<String>() : methodsData.get(0);
        newMethods = methodsData == null ? new HashSet<String>() : methodsData.get(1);

        if (computeAffectedTests) {
            for (String newMethod : newMethods) {
                affectedTestClasses.addAll(methodToTestClasses.getOrDefault(newMethod, new HashSet<>()));
            }

            for (String changedMethod : changedMethods) {
                affectedTestClasses.addAll(methodToTestClasses.getOrDefault(changedMethod, new HashSet<>()));
            }

            for (String addedClass : newClasses) {
                affectedTestClasses.addAll(classToTestClassGraph.getOrDefault(addedClass, new HashSet<>()));
            }

            for (String changedClassesWithChangedHeader : changedClassesWithChangedHeaders) {
                affectedTestClasses
                        .addAll(classToTestClassGraph.getOrDefault(changedClassesWithChangedHeader, new HashSet<>()));
            }
        }
    }

    /**
     * This method sets the changed methods by getting the list of sets for changed
     * methods, new methods, impacted test classes, old classes and changed classes
     * accordingly.
     */
    protected void computeChangedMethods() throws MojoExecutionException {

        List<Set<String>> dataList = ZLCHelperMethods.getChangedDataMethods(methodsCheckSum,
                methodToTestClasses, getArtifactsDir(), METHODS_CHECKSUMS_SERIALIZED_FILE);

        changedMethods = dataList == null ? new HashSet<>() : dataList.get(0);
        newMethods = dataList == null ? new HashSet<>() : dataList.get(1);

        affectedTestClasses = dataList == null ? new HashSet<>() : dataList.get(2);
        for (String newMethod : newMethods) {
            affectedTestClasses.addAll(methodToTestClasses.getOrDefault(newMethod, new HashSet<>()));
        }

        oldClasses = dataList == null ? new HashSet<>() : dataList.get(3);
        changedClasses = dataList == null ? new HashSet<>() : dataList.get(4);
        newClasses = MethodLevelStaticDepsBuilder.getClasses();
        newClasses.removeAll(oldClasses);
        // nonAffectedMethods = MethodLevelStaticDepsBuilder.computeMethods();
        // nonAffectedMethods.removeAll(changedMethods);
        // nonAffectedMethods.removeAll(newMethods);
    }

    /**
     * This method computes the impacted test classes by adding all test classes
     * associated with each impacted method to the set of impacted test classes.
     */
    private void computeAffectedTestClasses() {
        for (String impactedMethod : impactedMethods) {
            affectedTestClasses.addAll(methodToTestClasses.getOrDefault(impactedMethod, new HashSet<>()));
        }
    }

    /**
     * METHOD: This method computes the impacted methods by finding all impacted methods
     * associated with changed methods and new methods and adding them to the set of
     * impacted methods.
     * HYBRID: Computes the impacted methods by finding impacted methods for changed and new
     * methods (called affected methods), and updating the impacted test classes by
     * adding test classes found from impacted methods.
     */
    private void computeImpactedMethods() {
        impactedMethods = new HashSet<>();
        impactedMethods.addAll(findImpactedComponents(changedMethods, closureOption, Granularity.METHOD));
        impactedMethods.addAll(findImpactedComponents(newMethods, closureOption, Granularity.METHOD));
        if (granularity == Granularity.HYBRID) {
            if (computeAffectedTests) {
                for (String impactedMethod : impactedMethods) {
                    affectedTestClasses.addAll(methodToTestClasses.getOrDefault(impactedMethod, new HashSet<String>()));
                }
            }
        }
    }

    /**
     * Computes the impacted classes by finding impacted classes for new and
     * changedClassesWithChangedHeaders
     * methods (called affected methods), and updating the impacted test classes by
     * adding test classes found from impacted methods.
     */
    private void computeImpactedClasses() {
        impactedClasses = new HashSet<>();
        impactedClasses.addAll(findImpactedComponents(newClasses, closureOption, Granularity.CLASS));
        impactedClasses.addAll(findImpactedComponents(changedClassesWithChangedHeaders,
                closureOption, Granularity.CLASS));
        if (computeAffectedTests) {
            for (String impactedClass : impactedClasses) {
                affectedTestClasses.addAll(classToTestClassGraph.getOrDefault(impactedClass, new HashSet<String>()));
            }
        }
    }

    /**
     * This method finds all impacted components associated with a set of affected components (new components and
     * changed components), this can be either at class level or method level.
     * It adds all dependencies of each affected component to the set of impacted components.
     * This is the method that finds the transitive closure of the affected components.
     *
     * @param sources a set of source components.
     * @param closureOption determines how to calculate impacted components.
     * @param granularity determines which granularity the component is, can be either METHOD or CLASS.
     * @return a set of impacted components found from source components.
     */
    private Set<String> findImpactedComponents(Set<String> sources,
                                               TransitiveClosureOptions closureOption,
                                               Granularity granularity) {
        Map<String, Set<String>> forwardGraph = null;
        Map<String, Set<String>> backwardGraph = null;
        if (granularity == Granularity.METHOD) {
            forwardGraph = MethodLevelStaticDepsBuilder.callerToCalled;
            backwardGraph = MethodLevelStaticDepsBuilder.calledToCaller;
        } else if (granularity == Granularity.CLASS) {
            forwardGraph = MethodLevelStaticDepsBuilder.forwardClassesDependencyGraph;
            backwardGraph = MethodLevelStaticDepsBuilder.backwardClassesDependencyGraph;
        }
        Set<String> toReturn;
        switch (closureOption) {
            case PS1:
                // First backward then composite with forward
                toReturn = MethodLevelStaticDepsBuilder.computeReachability(sources, backwardGraph);
                toReturn = MethodLevelStaticDepsBuilder.computeReachability(toReturn, forwardGraph);
                break;
            case PS2:
                // Union of backward and forward
                toReturn = MethodLevelStaticDepsBuilder.computeReachability(sources, backwardGraph);
                toReturn.addAll(MethodLevelStaticDepsBuilder.computeReachability(sources, forwardGraph));
                break;
            case PS3:
            default:
                // Only backward
                toReturn = MethodLevelStaticDepsBuilder.computeReachability(sources, backwardGraph);
                break;
        }
        return toReturn;
    }

    private void updateForNextRun(List<String> allClasses) throws MojoExecutionException {
        long start = System.currentTimeMillis();
        Classpath sfClassPath = getSureFireClassPath();
        String sfPathString = Writer.pathToString(sfClassPath.getClassPath());
        ClassLoader loader = createClassLoader(sfClassPath);
        Result result = prepareForNextRun(sfPathString, sfClassPath, allClasses, new HashSet<String>(), false,
                closureOption);
        ZLCHelper zlcHelper = new ZLCHelper();
        zlcHelper.updateZLCFile(result.getTestDeps(), loader, getArtifactsDir(), new HashSet<String>(), useThirdParty,
                zlcFormat);
        long end = System.currentTimeMillis();
        if (writePath || logger.getLoggingLevel().intValue() <= Level.FINER.intValue()) {
            Writer.writeClassPath(sfPathString, getArtifactsDir());
        }
        if (logger.getLoggingLevel().intValue() <= Level.FINEST.intValue()) {
            save(getArtifactsDir(), result.getGraph());
        }
        Logger.getGlobal().log(Level.FINE, PROFILE_UPDATE_FOR_NEXT_RUN_TOTAL + Writer.millsToSeconds(end - start));
    }

    private void save(String artifactsDir, DirectedGraph<String> graph) {
        RTSUtil.saveForNextRun(artifactsDir, graph, printGraph, graphFile);
    }
}
