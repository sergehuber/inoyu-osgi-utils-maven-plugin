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
package dev.inoyu.maven.plugins.osgi.utils.mojos.converters.blueprint2ds;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class JavaFileUpdater {

    public void updateJavaFile(File javaFile, Map<String, Object> updates) throws IOException {
        List<String> lines = Files.readAllLines(javaFile.toPath());
        List<String> updatedLines = new ArrayList<>();
        boolean addedImports = false;

        // Add annotations and imports
        for (String line : lines) {
            if (!addedImports && line.startsWith("package")) {
                updatedLines.add(line);
                addImports(updatedLines, updates);
                addedImports = true;
                continue;
            }

            if (line.contains("class ")) {
                addAnnotations(updatedLines, updates);
            }

            updatedLines.add(line);
        }

        Files.write(javaFile.toPath(), updatedLines);
    }

    private void addImports(List<String> updatedLines, Map<String, Object> updates) {
        List<String> imports = (List<String>) updates.get("imports");
        if (imports != null) {
            for (String imp : imports) {
                updatedLines.add(imp);
            }
        }
    }

    private void addAnnotations(List<String> updatedLines, Map<String, Object> updates) {
        List<String> annotations = (List<String>) updates.get("annotations");
        if (annotations != null) {
            for (String annotation : annotations) {
                updatedLines.add(annotation);
            }
        }
    }
}
