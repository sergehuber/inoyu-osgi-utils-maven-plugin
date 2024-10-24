package dev.inoyu.maven.plugins.osgi.analyzer;

import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;
import org.junit.Test;

import java.io.File;

public class FindPackageUsagesIT {

    @Test
    public void testFindPackageUsages() throws Exception {
        File testDir = ResourceExtractor.simpleExtractResources(getClass(), "/projects/find-package-usages-test");

        Verifier verifier = new Verifier(testDir.getAbsolutePath());

        verifier.setSystemProperty("package", "org.osgi.framework");
        verifier.executeGoal("dev.inoyu:osgi-analyzer-maven-plugin:find-package-usages");

        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Usage found in Dependency: org.osgi:org.osgi.core");

        verifier.resetStreams();
    }
}