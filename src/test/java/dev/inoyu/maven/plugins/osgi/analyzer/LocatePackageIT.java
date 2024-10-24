package dev.inoyu.maven.plugins.osgi.analyzer;

import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;
import org.junit.Test;

import java.io.File;

public class LocatePackageIT {

    @Test
    public void testLocatePackage() throws Exception {
        File testDir = ResourceExtractor.simpleExtractResources(getClass(), "/projects/locate-package-test");

        Verifier verifier = new Verifier(testDir.getAbsolutePath());

        verifier.setSystemProperty("package", "org.osgi.framework");
        verifier.executeGoal("dev.inoyu:osgi-analyzer-maven-plugin:locate-package");

        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Package found in Dependency: org.osgi:org.osgi.core");

        verifier.resetStreams();
    }
}