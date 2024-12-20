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

import dev.inoyu.maven.plugins.osgi.utils.themes.ThemeManager;
import org.apache.maven.artifact.handler.ArtifactHandler;
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
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.fusesource.jansi.AnsiConsole;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;

import static dev.inoyu.maven.plugins.osgi.utils.themes.ThemeManager.Role.*;
import static dev.inoyu.maven.plugins.osgi.utils.themes.ThemeManager.builder;

/**
 * A Maven goal to find where a package is located within a project and all its
 * transitive dependencies.
 * It provides a clear trail of how we got to each location, including
 * information about which dependencies are optional.
 */
@Mojo(name = "locate-package", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class LocatePackageMojo extends AbstractMojo {

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

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Package name: " + packageName);
        printCoolHeader();
        getLog().info(builder().add(CONTEXT, "Searching for package location: ").add(DETAIL, packageName).build());

        try {
            if (project == null) {
                throw new MojoExecutionException("MavenProject is null");
            }
            if (project.getBuild() == null) {
                throw new MojoExecutionException("Project build is null");
            }

            boolean packageFound = false;

            File classesDir = new File(project.getBuild().getOutputDirectory());
            packageFound |= locatePackageInDirectory(classesDir, "Project classes", Collections.emptyList());

            ProjectBuildingRequest projectBuildingRequest = session.getProjectBuildingRequest();
            projectBuildingRequest.setProject(project);

            DependencyNode rootNode = dependencyGraphBuilder.buildDependencyGraph(projectBuildingRequest, null);
            if (rootNode == null) {
                throw new MojoExecutionException("Failed to build dependency graph: rootNode is null");
            }

            packageFound |= locatePackageInDependencyNode(rootNode, new ArrayList<>());

            if (!packageFound) {
                printPackageNotFound();
            }
        } catch (Exception e) {
            getLog().error("Error while searching for package location", e);
            throw new MojoExecutionException("Error while searching for package location", e);
        } finally {
            AnsiConsole.systemUninstall();
        }
    }

    private boolean locatePackageInDependencyNode(DependencyNode node, List<String> dependencyTrail)
            throws Exception {
        Artifact artifact = node.getArtifact();
        getLog().debug("Locating package in dependency node: " + artifact.getGroupId() + ":" + artifact.getArtifactId() + " file=" + artifact.getFile() + " downloadUrl=" + artifact.getDownloadUrl());
        String artifactKey = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();

        List<String> currentTrail = new ArrayList<>(dependencyTrail);
        currentTrail.add(artifactKey + (artifact.isOptional() ? " (optional)" : ""));

        File file = artifact.getFile();
        if (file == null) {
            file = resolveArtifactFile(artifact);
        }

        boolean result = false;

        if (file != null && file.isFile()) {
            result |= locatePackageInJar(file, "Dependency: " + artifactKey, currentTrail);
        }

        for (DependencyNode child : node.getChildren()) {
            result |= locatePackageInDependencyNode(child, currentTrail);
        }
        return result;
    }

    private boolean locatePackageInDirectory(File directory, String context, List<String> dependencyTrail) {
        String packagePath = packageName.replace('.', File.separatorChar);
        File packageDir = new File(directory, packagePath);

        if (packageDir.exists() && packageDir.isDirectory()) {
            printLocationFound(context);
            printDependencyTrail(dependencyTrail);
            listPackageContents(packageDir, "  ");
            return true;
        }
        return false;
    }

    private boolean locatePackageInJar(File jarFile, String context, List<String> dependencyTrail) throws IOException {
        String packagePath = packageName.replace('.', '/');
        boolean found = false;

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                getLog().debug("Locating package in entry : " + entry.getName());
                if (entry.getName().startsWith(packagePath + "/") && !entry.isDirectory()) {
                    if (!found) {
                        printLocationFound(context);
                        printDependencyTrail(dependencyTrail);
                        found = true;
                    }
                    getLog().debug(builder().add(DEPENDENCY, "  " + entry.getName()).build());
                }
            }
        }
        return found;
    }

    private void printDependencyTrail(List<String> dependencyTrail) {
        if (!dependencyTrail.isEmpty()) {
            getLog().info(builder().add(CONTEXT, "Dependency trail:").build());
            for (int i = 0; i < dependencyTrail.size(); i++) {
                String dep = dependencyTrail.get(i);
                ThemeManager.ColorBuilder builder = builder();
                builder.add(DETAIL, "  ".repeat(i) + "├─ ");
                if (dep.contains("(optional)")) {
                    builder.add(DEPENDENCY, dep.replace(" (optional)", "")).add(ATTRIBUTE, " (optional)");
                } else {
                    builder.add(DEPENDENCY, dep);
                }
                getLog().info(builder.build());
            }
        }
        getLog().info(""); // Empty line for readability
    }

    private void listPackageContents(File directory, String indent) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                getLog().info(builder().add(DEPENDENCY, indent + file.getName()).build());
                if (file.isDirectory()) {
                    listPackageContents(file, indent + "  ");
                }
            }
        }
    }

    private void printLocationFound(String context) {
        getLog().info(builder().add(CONTEXT, "📦 Package found in ").add(DETAIL, context).build());
    }

    private void printCoolHeader() {
        String[] header = {
                "  _____                         _____           _                    _                     _             ",
                " |_   _|                       |  __ \\         | |                  | |                   | |            ",
                "   | |  _ __   ___  _   _ _   _| |__) |_ _  ___| | ____ _  __ _  ___| |     ___   ___ __ _| |_ ___  _ __ ",
                "   | | | '_ \\ / _ \\| | | | | | |  ___/ _` |/ __| |/ / _` |/ _` |/ _ \\ |    / _ \\ / __/ _` | __/ _ \\| '__|",
                "  _| |_| | | | (_) | |_| | |_| | |  | (_| | (__|   < (_| | (_| |  __/ |___| (_) | (_| (_| | || (_) | |   ",
                " |_____|_| |_|\\___/ \\__, |\\__,_|_|   \\__,_|\\___|_|\\_\\__,_|\\__, |\\___|______\\___/ \\___\\__,_|\\__\\___/|_|   ",
                "                     __/ |                                 __/ |                                         ",
                "                    |___/                                 |___/                                          "
        };

        for (String line : header) {
            getLog().info(builder().add(HEADER, line).build());
        }
        getLog().info("");
    }

    private void printPackageNotFound() {
        String[] notFoundArt = {
                "  _____           _                    _   _       _     ______                     _ ",
                " |  __ \\         | |                  | \\ | |     | |   |  ____|                   | |",
                " | |__) |_ _  ___| | ____ _  __ _  ___|  \\| | ___ | |_  | |__ ___  _   _ _ __   __| |",
                " |  ___/ _` |/ __| |/ / _` |/ _` |/ _ \\ . ` |/ _ \\| __| |  __/ _ \\| | | | '_ \\ / _` |",
                " | |  | (_| | (__|   < (_| | (_| |  __/ |\\  | (_) | |_  | | | (_) | |_| | | | | (_| |",
                " |_|   \\__,_|\\___|_|\\_\\__,_|\\__, |\\___|_| \\_|\\___/ \\__| |_|  \\___/ \\__,_|_| |_|\\__,_|",
                "                             __/ |                                                    ",
                "                            |___/                                                     "
        };

        getLog().info("");
        for (String line : notFoundArt) {
            getLog().info(builder().add(ERROR, line).build());
        }
        getLog().info("");
        getLog().info(builder().add(CONTEXT, "The package ").add(DETAIL, packageName)
                .add(CONTEXT, " was not found in the project or its dependencies.").build());
        getLog().info("");
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

    private String getArtifactExtension(Artifact artifact) {
        ArtifactHandler handler = artifact.getArtifactHandler();
        return handler.getExtension();
    }

}
