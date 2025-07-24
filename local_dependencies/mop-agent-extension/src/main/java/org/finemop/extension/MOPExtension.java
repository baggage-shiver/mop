package org.finemop.extension;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.annotations.Component;
import org.apache.maven.project.MavenProject;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.apache.maven.model.PluginExecution;

@Component( role = AbstractMavenLifecycleParticipant.class, hint = "mop")
public class MOPExtension extends AbstractMavenLifecycleParticipant
{

    final String MOP_AGENT_STRING="/javamop-agent/javamop-agent/1.0/javamop-agent-1.0.jar";
    String SUREFIRE_VERSION="3.2.5";

    @Override
    public void afterProjectsRead( MavenSession session )
        throws MavenExecutionException
    {
        String PROFILER_OPTION = "";
        if (System.getenv("PROFILER_OPTION") != null) {
            PROFILER_OPTION = System.getenv("PROFILER_OPTION");
        }
    
        System.out.println("Modifying surefire to add JavaMOP...");
        boolean found = false;
        for (MavenProject project : session.getProjects()) {
            for (Plugin plugin : project.getBuildPlugins()) {
                if (plugin.getArtifactId().equals("maven-surefire-plugin") && plugin.getGroupId().equals("org.apache.maven.plugins")) {
                    found = true;
                    System.out.println("=====Old Version:: " + plugin.getVersion());
                    if (System.getenv("SUREFIRE_VERSION") != null) {
                        SUREFIRE_VERSION = System.getenv("SUREFIRE_VERSION");
                    }
                    plugin.setVersion(SUREFIRE_VERSION);
                    System.out.println("=====New Version:: " + plugin.getVersion());
                    Xpp3Dom config = (Xpp3Dom)plugin.getConfiguration();
                    if (config != null) {
                        Xpp3Dom argLine = config.getChild("argLine");
                        if (argLine != null) {
                            String currentArgLine = argLine.getValue();
                            System.out.println("=====Current ArgLine:: " + currentArgLine);
                            argLine.setValue("-javaagent:" + session.getLocalRepository().getBasedir() + MOP_AGENT_STRING + " " + PROFILER_OPTION + " " + argLine.getValue());
                        } else {
                            config.addChild(getNewArgLine(session));
                        }
                    } else {
                        config = new Xpp3Dom("configuration");
                        config.addChild(getNewArgLine(session));
                    }
                    for (PluginExecution execution : plugin.getExecutions()) {
                        System.out.println("=====Version:: " + plugin.getExecutions());
                        execution.setConfiguration(config);
                    }
                }
            }
        }
    }

    public Xpp3Dom getNewArgLine(MavenSession session) {
        String PROFILER_OPTION = "";
        if (System.getenv("PROFILER_OPTION") != null) {
            PROFILER_OPTION = System.getenv("PROFILER_OPTION");
        }
        
        Xpp3Dom argLine = new Xpp3Dom("argLine");
        argLine.setValue("-javaagent:" + session.getLocalRepository().getBasedir() + MOP_AGENT_STRING + " " + PROFILER_OPTION);
        return argLine;
    }
}
