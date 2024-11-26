/*
 * Copyright 2024 Serge Huber
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.inoyu.maven.plugins.osgi.utils.mojos;

import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Descriptors;
import aQute.bnd.osgi.Clazz;
import dev.inoyu.maven.plugins.osgi.utils.themes.ThemeManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;

import org.fusesource.jansi.AnsiConsole;

import java.io.File;
import java.util.*;

import static dev.inoyu.maven.plugins.osgi.utils.themes.ThemeManager.builder;
import static dev.inoyu.maven.plugins.osgi.utils.themes.ThemeManager.Role.*;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * A Maven goal to find all usages of a package inside an OSGi project and its
 * transitive dependencies using BND.
 * This helps understand why BND generates an Import-Package statement and from
 * which specific piece of code it originates.
 * It provides a clear trail of how we got to each dependency, including
 * information about which dependencies are optional.
 */
@Mojo(name = "find-package-usages", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class FindPackageUsagesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "package", required = true)
    private String packageName;

    @Component
    private DependencyGraphBuilder dependencyGraphBuilder;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        printCoolHeader();
        getLog().info(builder()
                .add(CONTEXT, "Searching for usages of package: ")
                .add(DETAIL, packageName)
                .build());

        try {
            File classesDir = new File(project.getBuild().getOutputDirectory());
            analyzeWithBnd(classesDir, "Project classes", Collections.emptyList());

            ProjectBuildingRequest projectBuildingRequest = session.getProjectBuildingRequest();
            projectBuildingRequest.setProject(project);

            DependencyNode rootNode = dependencyGraphBuilder.buildDependencyGraph(projectBuildingRequest, null);
            analyzeDependencyNode(rootNode, new ArrayList<>());
        } catch (Exception e) {
            throw new MojoExecutionException("Error while searching for package usages", e);
        } finally {
            AnsiConsole.systemUninstall();
        }
    }

    private void analyzeDependencyNode(DependencyNode node, List<String> dependencyTrail) throws Exception {
        Artifact artifact = node.getArtifact();
        String artifactKey = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();

        List<String> currentTrail = new ArrayList<>(dependencyTrail);
        currentTrail.add(artifactKey + (artifact.isOptional() ? " (optional)" : ""));

        File file = artifact.getFile();
        if (file == null) {
            file = resolveArtifactFile(artifact);
        }

        if (file != null && file.isFile()) {
            analyzeWithBnd(file, "Dependency: " + artifactKey, currentTrail);
        }

        for (DependencyNode child : node.getChildren()) {
            analyzeDependencyNode(child, currentTrail);
        }
    }

    private File resolveArtifactFile(Artifact artifact) throws Exception {
        // First, check the local repository
        File localFile = new File(repoSession.getLocalRepository().getBasedir(),
                repoSession.getLocalRepositoryManager().getPathForLocalArtifact(new DefaultArtifact(
                        artifact.getGroupId(),
                        artifact.getArtifactId(),
                        artifact.getClassifier(),
                        getArtifactExtension(artifact),
                        artifact.getVersion())));

        if (localFile.exists()) {
            return localFile;
        }

        // If not found locally, resolve from remote repositories
        DefaultArtifact aetherArtifact = new DefaultArtifact(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getClassifier(),
                getArtifactExtension(artifact),
                artifact.getVersion());

        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(aetherArtifact);
        request.setRepositories(project.getRemoteProjectRepositories());

        ArtifactResult result = repoSystem.resolveArtifact(repoSession, request);
        return result.getArtifact().getFile();
    }

    private void analyzeWithBnd(File file, String context, List<String> dependencyTrail) throws Exception {
        try (Jar jar = new Jar(file)) {
            Analyzer analyzer = new Analyzer();
            analyzer.setJar(jar);
            analyzer.analyze();

            for (Clazz clazz : analyzer.getClassspace().values()) {
                String className = clazz.getClassName().getFQN();
                for (Descriptors.PackageRef ref : clazz.getReferred()) {
                    if (ref.getFQN().startsWith(packageName)) {
                        getLog().info(builder()
                                .add(CONTEXT, "📦 Usage found in ")
                                .add(DETAIL, context)
                                .add(CONTEXT, ": ")
                                .add(CLAUSE, className)
                                .add(CONTEXT, " uses ")
                                .add(DIRECTIVE, ref.getFQN())
                                .build());
                        printDependencyTrail(dependencyTrail);
                        getLog().info(""); // Empty line for readability
                    }
                }
            }

            analyzer.close();
        }
    }

    private void printDependencyTrail(List<String> dependencyTrail) {
        if (!dependencyTrail.isEmpty()) {
            getLog().info(builder().add(CONTEXT, "Dependency trail:").build());
            for (int i = 0; i < dependencyTrail.size(); i++) {
                String dep = dependencyTrail.get(i);
                ThemeManager.ColorBuilder builder = builder();
                builder.add(CONTEXT, "  ".repeat(i) + "├─ ");
                if (dep.contains("(optional)")) {
                    builder.add(DEPENDENCY, dep.replace(" (optional)", "")).add(ATTRIBUTE, " (optional)");
                } else {
                    builder.add(DEPENDENCY, dep);
                }
                getLog().info(builder.build());
            }
        }
    }

    private void printCoolHeader() {
        String[] header = {
                " _____                         _____           _                      ",
                "|_   _|                       |  __ \\         | |                     ",
                "  | |  _ __   ___  _   _ _   _| |__) |_ _  ___| | ____ _  __ _  ___   ",
                "  | | | '_ \\ / _ \\| | | | | | |  ___/ _` |/ __| |/ / _` |/ _` |/ _ \\  ",
                " _| |_| | | | (_) | |_| | |_| | |  | (_| | (__|   < (_| | (_| |  __/  ",
                "|_____|_| |_|\\___/ \\__, |\\__,_|_|   \\__,_|\\___|_|\\_\\__,_|\\__, |\\___|  ",
                "                     __/ |                                __/ |        ",
                "                    |___/                                |___/         ",
                "  _    _                       ______ _           _                   ",
                " | |  | |                     |  ____(_)         | |                  ",
                " | |  | |___  __ _  __ _  ___ | |__   _ _ __   __| | ___ _ __         ",
                " | |  | / __|/ _` |/ _` |/ _ \\|  __| | | '_ \\ / _` |/ _ \\ '__|        ",
                " | |__| \\__ \\ (_| | (_| |  __/| |    | | | | | (_| |  __/ |           ",
                "  \\____/|___/\\__,_|\\__, |\\___||_|    |_|_| |_|\\__,_|\\___|_|           ",
                "                    __/ |                                             ",
                "                   |___/                                              "
        };

        for (String line : header) {
            getLog().info(builder().add(HEADER, line).build());
        }
        getLog().info("");
    }

    private String getArtifactExtension(Artifact artifact) {
        ArtifactHandler handler = artifact.getArtifactHandler();
        return handler.getExtension();
    }

}
