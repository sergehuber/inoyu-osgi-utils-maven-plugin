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

import org.apache.maven.it.Verifier;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;

import java.io.File;

public class FindPackageUsagesIT extends AbstractMojoTestCase {

    public void testFindPackageUsages() throws Exception {
        File testProjectDir = new File(getBasedir(), "target/it/projects/find-package-usages-test");

        Verifier verifier = new Verifier(testProjectDir.getAbsolutePath());
        verifier.setAutoclean(false);

        verifier.setSystemProperty("package", "org.osgi.framework");
        verifier.executeGoal("dev.inoyu:osgi-utils-maven-plugin:find-package-usages");

        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Usage found in Dependency: org.osgi:org.osgi.core");

        verifier.resetStreams();
    }
}
