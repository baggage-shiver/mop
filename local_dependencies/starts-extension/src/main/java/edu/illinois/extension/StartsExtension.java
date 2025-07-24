package edu.illinois.extension;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;

@Component( role = AbstractMavenLifecycleParticipant.class, hint = "starts")
public class StartsExtension extends AbstractMavenLifecycleParticipant {

    @Override
    public void afterSessionStart(MavenSession session) throws MavenExecutionException {

    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        System.out.println("Modifying surefire to add STARTS.");
        for (MavenProject project : session.getProjects()) {
            // Do not add starts if it already has
            for (Plugin plugin : project.getBuildPlugins()) {
                if (plugin.getArtifactId().equals("starts-maven-plugin")) {
                    return;
                }
            }
            Build build = project.getBuild();
            Plugin starts = new Plugin();
            starts.setGroupId("edu.illinois");
            starts.setArtifactId("starts-maven-plugin");
            starts.setVersion("1.4");
            build.addPlugin(starts);
        }
    }

}
