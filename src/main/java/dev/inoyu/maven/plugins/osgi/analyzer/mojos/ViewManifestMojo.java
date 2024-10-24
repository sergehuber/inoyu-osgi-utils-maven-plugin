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
package dev.inoyu.maven.plugins.osgi.analyzer.mojos;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import org.fusesource.jansi.AnsiConsole;

import java.io.IOException;
import java.util.*;
import java.util.jar.JarFile;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * A Maven goal to view and analyze OSGi bundle manifests.
 * This mojo can be used both within a Maven project context and independently.
 *
 * When used within a project, it will analyze the project's main artifact.
 * When used independently, it can analyze specified JAR files.
 */
@Mojo(name = "view-manifest", requiresProject = false)
public class ViewManifestMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(property = "jars")
    private List<String> jars;

    public void setJars(List<String> jars) {
        this.jars = jars;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        AnsiConsole.systemInstall();
        printCoolHeader();

        List<String> jarPaths = new ArrayList<>();

        if (jars != null && !jars.isEmpty()) {
            jarPaths.addAll(jars);
        } else if (project != null && project.getArtifact() != null) {
            String extension = project.getPackaging();
            if ("bundle".equals(extension) || "maven-plugin".equals(extension)) {
                extension = "jar";
            }
            if ("pom".equals(extension)) {
                getLog().info(ansi().fgBrightRed().a("POM files are not supported for this goal.").reset().toString());
                return;
            }
            jarPaths.add(project.getBuild().getDirectory() + "/" + project.getBuild().getFinalName() + "." + extension);
        }

        if (jarPaths.isEmpty()) {
            throw new MojoExecutionException("No JAR files specified and not running in a project context.");
        }

        for (String jarPath : jarPaths) {
            try {
                String manifest = readManifest(jarPath);
                Map<String, String> headers = parseManifest(manifest);

                getLog().info(ansi().fgBrightYellow().a("Analyzing manifest of: ").fgBrightCyan().a(jarPath).reset()
                        .toString());
                getLog().info(ansi().fgBrightMagenta().a("=".repeat(80)).reset().toString());

                headers.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            String key = entry.getKey();
                            String value = entry.getValue();
                            if (Arrays.asList("Bundle-ClassPath", "Embedded-Artifacts", "Export-Package",
                                    "Import-Package", "Import-Service").contains(key)) {
                                getLog().info(formatHeader(key, value));
                            } else {
                                getLog().info(ansi().fgBrightGreen().a(key + ": ").fgBrightDefault().a(value).reset()
                                        .toString());
                            }
                            getLog().info(ansi().fgBrightBlue().a("─".repeat(78)).reset().toString());
                        });

                getLog().info(ansi().fgBrightMagenta().a("=".repeat(80)).reset().toString());
                getLog().info("");
            } catch (IOException e) {
                throw new MojoExecutionException("Error reading MANIFEST.MF from " + jarPath, e);
            }
        }

        AnsiConsole.systemUninstall();
    }

    private String readManifest(String jarPath) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath)) {
            return new String(jarFile.getInputStream(jarFile.getEntry("META-INF/MANIFEST.MF")).readAllBytes());
        }
    }

    private Map<String, String> parseManifest(String manifestContent) {
        Map<String, String> headers = new LinkedHashMap<>();
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();

        for (String line : manifestContent.split("\n")) {
            if (line.startsWith(" ")) {
                currentValue.append(line.substring(1));
            } else {
                if (currentKey != null) {
                    headers.put(currentKey, currentValue.toString());
                }
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    currentKey = parts[0];
                    currentValue = new StringBuilder(parts[1].trim());
                }
            }
        }

        if (currentKey != null) {
            headers.put(currentKey, currentValue.toString());
        }

        return headers;
    }

    private List<String> parseHeader(String headerValue) {
        List<String> clauses = new ArrayList<>();
        StringBuilder currentClause = new StringBuilder();
        boolean inQuotes = false;
        boolean escape = false;

        for (char c : headerValue.toCharArray()) {
            if (escape) {
                currentClause.append(c);
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"' && !escape) {
                inQuotes = !inQuotes;
                currentClause.append(c);
            } else if (c == ',' && !inQuotes) {
                clauses.add(currentClause.toString().trim());
                currentClause = new StringBuilder();
            } else {
                currentClause.append(c);
            }
        }

        if (currentClause.length() > 0) {
            clauses.add(currentClause.toString().trim());
        }

        return clauses;
    }

    private Map.Entry<String, List<Map.Entry<String, String>>> parseClause(String clause) {
        String[] parts = clause.split(";(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        String path = parts[0].trim();
        List<Map.Entry<String, String>> params = new ArrayList<>();

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains(":=")) {
                String[] keyValue = part.split(":=", 2);
                params.add(new AbstractMap.SimpleEntry<>(keyValue[0].trim(), keyValue[1].trim() + ":directive"));
            } else if (part.contains("=")) {
                String[] keyValue = part.split("=", 2);
                params.add(new AbstractMap.SimpleEntry<>(keyValue[0].trim(), keyValue[1].trim() + ":attribute"));
            } else {
                path += ";" + part.trim();
            }
        }

        return new AbstractMap.SimpleEntry<>(path, params);
    }

    private String formatHeader(String key, String value) {
        List<String> clauses = parseHeader(value);
        StringBuilder formatted = new StringBuilder(ansi().fgBrightYellow().a(key + ":").reset().toString() + "\n");

        for (String clause : clauses) {
            Map.Entry<String, List<Map.Entry<String, String>>> parsedClause = parseClause(clause);
            formatted.append(ansi().fgBrightGreen().a("  " + parsedClause.getKey()).reset().toString() + "\n");
            for (Map.Entry<String, String> param : parsedClause.getValue()) {
                String[] parts = param.getValue().split(":");
                String separator = parts[1].equals("directive") ? ":=" : "=";
                formatted.append(
                        ansi().fgBrightCyan().a("    " + param.getKey() + separator + parts[0]).reset().toString()
                                + "\n");
            }
        }

        return formatted.toString().trim();
    }

    private void printCoolHeader() {
        String[] header = {
                "  _____                         __  __             _  __          _  __      ___                       ",
                " |_   _|                       |  \\/  |           (_)/ _|        | | \\ \\    / (_)                      ",
                "   | |  _ __   ___  _   _ _   _| \\  / | __ _ _ __  _| |_ ___  ___| |_ \\ \\  / / _  _____      _____ _ __ ",
                "   | | | '_ \\ / _ \\| | | | | | | |\\/| |/ _` | '_ \\| |  _/ _ \\/ __| __| \\ \\/ / | |/ _ \\ \\ /\\ / / _ \\ '__|",
                "  _| |_| | | | (_) | |_| | |_| | |  | | (_| | | | | | ||  __/\\__ \\ |_   \\  /  | |  __/\\ V  V /  __/ |   ",
                " |_____|_| |_|\\___/ \\__, |\\__,_|_|  |_|\\__,_|_| |_|_|_| \\___||___/\\__|   \\/   |_|\\___| \\_/\\_/ \\___|_|   ",
                "                     __/ |                                                                                 ",
                "                    |___/                                                                                  "
        };

        for (String line : header) {
            getLog().info(ansi().fgBrightCyan().a(line).reset().toString());
        }
        getLog().info("");
    }
}
