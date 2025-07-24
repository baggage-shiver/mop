package org.finemop;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "rps-rpp-vms", requiresDirectInvocation = true, requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.TEST, lifecycle = "rps-rpp-vms")
public class RpsRppVmsMojo extends RppVmsMojo {
    public void execute() throws MojoExecutionException {
        super.monitorFile = MonitorMojo.AGENT_CONFIGURATION_FILE;
        super.execute();
    }
}
