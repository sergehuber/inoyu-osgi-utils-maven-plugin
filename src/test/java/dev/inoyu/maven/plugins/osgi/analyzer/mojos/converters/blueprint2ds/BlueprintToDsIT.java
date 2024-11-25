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
package dev.inoyu.maven.plugins.osgi.analyzer.mojos.converters.blueprint2ds;

import org.apache.maven.it.Verifier;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;

import java.io.File;

public class BlueprintToDsIT extends AbstractMojoTestCase {

    public void testBlueprintToDsConversion() throws Exception {
        File testProjectDir = new File(getBasedir(), "target/it/projects/blueprint-to-ds-test");
        File pom = new File(testProjectDir, "pom.xml");

        assertNotNull(pom);
        assertTrue(pom.exists());

        Verifier verifier = new Verifier(testProjectDir.getAbsolutePath());
        verifier.setAutoclean(false);

        verifier.executeGoal("dev.inoyu:osgi-analyzer-maven-plugin:convert-blueprint-to-ds");

        verifier.verifyErrorFreeLog();

        // Validate changes
        File myServiceJavaFile = new File(testProjectDir, "src/main/java/com/example/test/MyService.java");
        assertTrue(myServiceJavaFile.exists());
        assertTrue(fileContains(myServiceJavaFile, "@Component"));
        assertTrue(fileContains(myServiceJavaFile, "import org.osgi.service.component.annotations.Component;"));

        File anotherServiceJavaFile = new File(testProjectDir, "src/main/java/com/example/test/AnotherService.java");
        assertTrue(anotherServiceJavaFile.exists());
        assertTrue(fileContains(anotherServiceJavaFile, "@Component"));
    }

    private boolean fileContains(File file, String content) throws Exception {
        String fileContent = new String(java.nio.file.Files.readAllBytes(file.toPath()));
        return fileContent.contains(content);
    }
}
