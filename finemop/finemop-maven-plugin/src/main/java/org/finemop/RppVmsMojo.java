package org.finemop;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.finemop.finemop.util.Util;
import org.finemop.finemop.util.Violation;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.eclipse.jgit.diff.DiffEntry;

@Mojo(name = "rpp-vms", requiresDirectInvocation = true, requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.TEST, lifecycle = "rpp-vms")
public class RppVmsMojo extends RppMojo {
    protected Path gitDir;

    /**
     * Agent configuration file from which to read monitored specs and excluded classes. The given path is
     * resolved relative to the artifacts directory. This property can be left null to indicate that all specs and
     * classes were monitored.
     */
    @Parameter(property = "monitorFile", required = false)
    protected String monitorFile;

    @Parameter(defaultValue = "${session.executionRootDirectory}", required = true, readonly = true)
    private String executionRootDirectory;

    // copied from VMS - is there a better way to do this?
    /**
     * Specific SHA to use as the "old" version of code when making comparisons.
     * The SHA should correspond to the previous run of VMS.
     */
    @Parameter(property = "lastSha", required = false)
    private String lastSha;

    /**
     * Specific SHA to use as the "new" version of code when making comparisons.
     */
    @Parameter(property = "newSha", required = false)
    private String newSha;

    /**
     * Whether to treat all found violations as new, regardless of previous runs.
     */
    @Parameter(property = "firstRun", required = false, defaultValue = "false")
    private boolean firstRun;

    /**
     * Whether to show all violations in <code>violation-counts</code> or only the new violations.
     */
    @Parameter(property = "showAllInFile", defaultValue = "false")
    private boolean showAllInFile;

    /**
     * Whether to always save <code>violation-counts</code> regardless of the current git status.
     */
    @Parameter(property = "forceSave", defaultValue = "false")
    private boolean forceSave;

    /**
     * Filename to read the "old" violations from. The filename is resolved
     * against the artifacts directory.
     */
    @Parameter(property = "lastViolationsFile", defaultValue = "violation-counts-old")
    private String lastViolationsFile;

    private Path oldViolationCountsPath;
    private Path newViolationCountsPath;


    public void execute() throws MojoExecutionException {
        super.execute();
        doVMSPart();
    }

    protected String getLastShaHelper(Path lastShaPath) throws MojoExecutionException {
        if (lastSha != null && !lastSha.isEmpty()) {
            return lastSha;
        }
        if (!Files.exists(lastShaPath)) {
            return null;
        }
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(lastShaPath.toFile()))) {
            String sha = bufferedReader.readLine();
            return sha;
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void doVMSPart() throws MojoExecutionException {
        getLog().info("[eMOP] VMS start time: " + Util.timeFormatter.format(new Date()));
        gitDir = Paths.get(executionRootDirectory, ".git");

        // Currently, RPP-VMS emulates regular VMS by aggregating between the critical and background runs
        // to get a singular violation-counts file, and comparing that against the previously created version.
        // TODO: Implement a finer-grained RPP-VMS that distinguishes critical and background phase violations.
        // For instance, "foo is a new violation that came from a background specification."

        oldViolationCountsPath = Paths.get(getArtifactsDir(), lastViolationsFile);
        newViolationCountsPath = basedir.toPath().resolve("violation-counts");
        Set<Violation> oldViolations = Violation.parseViolations(oldViolationCountsPath);
        // need to get path from both critical and background phases
        Set<Violation> newViolations = Violation.parseViolations(criticalViolationsPath);
        try {
            getLog().info("Copying critical violations from " + criticalViolationsPath
                    + " to " + newViolationCountsPath);
            Files.copy(criticalViolationsPath, newViolationCountsPath, StandardCopyOption.REPLACE_EXISTING);
            if (backgroundViolationsPath != null && !backgroundViolationsPath.toString().isEmpty()) {
                newViolations.addAll(Violation.parseViolations(backgroundViolationsPath));
                getLog().info("Copying background violations from " + backgroundViolationsPath
                        + " to " + newViolationCountsPath);
                Files.write(newViolationCountsPath, Files.readAllBytes(backgroundViolationsPath),
                        StandardOpenOption.APPEND);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        getLog().info("Number of total violations found: " + newViolations.size());

        Path lastShaPath = Paths.get(getArtifactsDir(), "last-SHA");
        lastSha = getLastShaHelper(lastShaPath);
        if (lastSha == null || lastSha.isEmpty()) {
            firstRun = true;
        }
        // Does not apply to first run because there are no commit differences to be obtained.
        if (!firstRun) {
            List<DiffEntry> diffEntryList = VmsMojo.getCommitDiffs(gitDir, lastSha, newSha);
            Map<String, String> renames = new HashMap<>();
            Map<String, Map<Integer, Integer>> offsets = new HashMap<>();
            Map<String, Set<Integer>> modifiedLines = new HashMap<>();
            VmsMojo.findLineChangesAndRenamesHelper(diffEntryList, renames, offsets, modifiedLines);
            VmsMojo.filterOutOldViolations(oldViolations, newViolations, renames, offsets, modifiedLines);
        }
        getLog().info("Number of \"new\" violations found: " + newViolations.size());

        Path monitorFilePath = monitorFile != null ? Paths.get(getArtifactsDir(), monitorFile) : null;
        VmsMojo.saveViolationCounts(forceSave, firstRun, monitorFilePath, gitDir, lastShaPath, newViolationCountsPath,
                oldViolationCountsPath);
        if (!showAllInFile) {
            VmsMojo.rewriteViolationCounts(newViolationCountsPath, firstRun, newViolations);
        }
        getLog().info("[eMOP] VMS end time: " + Util.timeFormatter.format(new Date()));
    }
}
