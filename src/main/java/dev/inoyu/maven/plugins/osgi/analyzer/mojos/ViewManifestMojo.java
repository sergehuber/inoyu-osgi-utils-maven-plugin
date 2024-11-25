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

import org.apache.felix.utils.manifest.Attribute;
import org.apache.felix.utils.manifest.Clause;
import org.apache.felix.utils.manifest.Directive;
import org.apache.felix.utils.manifest.Parser;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import org.fusesource.jansi.AnsiConsole;

import java.io.IOException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

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
                Manifest manifest = readManifest(jarPath);

                getLog().info(ansi().fgBrightYellow().a("Analyzing manifest of: ").fgBrightCyan().a(jarPath).reset()
                        .toString());
                getLog().info(ansi().fgBrightMagenta().a("=".repeat(80)).reset().toString());

                processAttributes(manifest.getMainAttributes());
                for (Map.Entry<String, Attributes> namedAttributes : manifest.getEntries().entrySet()) {
                    processAttributes(namedAttributes.getValue());
                }

                getLog().info(ansi().fgBrightMagenta().a("=".repeat(80)).reset().toString());
                getLog().info("");
            } catch (IOException e) {
                throw new MojoExecutionException("Error reading MANIFEST.MF from " + jarPath, e);
            }
        }

        AnsiConsole.systemUninstall();
    }

    private Manifest readManifest(String jarPath) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath)) {
            return jarFile.getManifest();
        }
    }

    private void processAttributes(Attributes attributes) {

        for (Object attributeKey : attributes.keySet()) {
            String attributeName = attributeKey.toString();
            String attributeValue = attributes.getValue(attributeName);
            if (Arrays.asList("Bundle-ClassPath", "Embedded-Artifacts", "Export-Package",
                    "Import-Package", "Import-Service", "Require-Capability", "Provide-Capability",
                    "Fragment-Host", "DynamicImport-Package", "Bundle-NativeCode", "Service-Component",
                    "Bundle-RequiredExecutionEnvironment", "Component-Properties").contains(attributeName)) {
                getLog().info(formatHeader(attributeName, attributeValue));
            } else {
                getLog().info(ansi().fgBrightGreen().a(attributeName + ": ").fgBrightDefault().a(attributeValue).reset()
                        .toString());
            }
            getLog().info(ansi().fgBrightBlue().a("─".repeat(78)).reset().toString());
        }
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
        StringBuilder formatted = new StringBuilder(ansi().fgBrightYellow().a(key + ":").reset().toString() + "\n");

        // Parse the Import-Package header
        Clause[] clauses = Parser.parseHeader(value);

        for (int i = 0; i < clauses.length; i++) {
            Clause clause = clauses[i];
            formatted.append(ansi().fgBrightGreen().a("  " + clause.getName()).reset().toString());
            for (Directive directive : clause.getDirectives()) {
                String quotedValue = applyQuotingIfNeeded(directive.getValue());
                formatted.append(ansi().fgBrightCyan().a(";" + directive.getName() + ":=" + quotedValue).reset().toString());
            }
            for (Attribute attribute : clause.getAttributes()) {
                String quotedValue = applyQuotingIfNeeded(attribute.getValue());
                formatted.append(ansi().fgBrightBlue().a(";" + attribute.getName() + "=" + quotedValue).reset().toString());
            }
            // Add a comma if this is not the last clause
            if (i < clauses.length - 1) {
                formatted.append(",\n");
            }
        }

        return formatted.toString();
    }

    private String applyQuotingIfNeeded(String value) {
        // Regex pattern for detecting special characters or whitespace
        Pattern specialCharacters = Pattern.compile("[,;=]|\\s");

        // Quote the value if it contains special characters, whitespace, or is empty
        if (value.isEmpty() || specialCharacters.matcher(value).find()) {
            return "\"" + value.replace("\"", "\\\"") + "\""; // Escape inner quotes
        }

        // Return the unquoted value if quoting is unnecessary
        return value;
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
