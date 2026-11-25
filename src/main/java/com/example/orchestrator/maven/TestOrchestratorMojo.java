package com.example.orchestrator.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Maven goal: orchestrator:test
 * 
 * Invokes the unit test orchestration framework.
 * 
 * Usage:
 *   mvn orchestrator:test -Dclass=com.example.MyClass
 *   mvn orchestrator:test -Dclass=com.example.MyClass -Daction=approve
 */
@Mojo(name = "test", requiresProject = true)
public class TestOrchestratorMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "orchestrator.class", required = true,
               description = "Target class for unit test orchestration (e.g., com.example.MyClass)")
    private String className;

    @Parameter(property = "orchestrator.action", defaultValue = "start",
               description = "Action: start (default), approve, resume, status")
    private String action;

    @Parameter(property = "orchestrator.framework.dir", defaultValue = "orchestrator-framework",
               description = "Path to orchestrator framework directory")
    private String frameworkDir;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            getLog().info("╔══════════════════════════════════════╗");
            getLog().info("║  Orchestrator Framework (Maven)      ║");
            getLog().info("╚══════════════════════════════════════╝");
            getLog().info("");
            getLog().info("Class:     " + className);
            getLog().info("Action:    " + action);
            getLog().info("Project:   " + project.getBasedir());
            getLog().info("");

            File frameworkPath = new File(project.getBasedir(), frameworkDir);
            if (!frameworkPath.exists()) {
                throw new MojoExecutionException(
                    "Framework not found at: " + frameworkPath.getAbsolutePath() + "\n" +
                    "Please add orchestrator-framework as Git submodule: " +
                    "git submodule add https://github.com/YOUR-ORG/orchestrator-framework ./orchestrator-framework"
                );
            }

            String supervisorScript = new File(frameworkPath, "supervisor/run.sh").getAbsolutePath();
            if (!new File(supervisorScript).exists()) {
                throw new MojoExecutionException(
                    "Supervisor script not found at: " + supervisorScript
                );
            }

            // Invoke supervisor with class name and action
            ProcessBuilder pb = new ProcessBuilder("bash", supervisorScript, className, action);
            pb.directory(project.getBasedir());
            pb.inheritIO(); // Stream output to console
            
            Process process = pb.start();
            int exitCode = process.waitFor();

            getLog().info("");
            if (exitCode == 0) {
                getLog().info("✅ Orchestration complete!");
            } else {
                throw new MojoExecutionException(
                    "Orchestration failed with exit code: " + exitCode
                );
            }

        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to execute orchestrator", e);
        }
    }

}
