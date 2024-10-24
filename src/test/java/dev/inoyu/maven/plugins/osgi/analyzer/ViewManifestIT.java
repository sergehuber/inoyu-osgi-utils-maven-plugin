package dev.inoyu.maven.plugins.osgi.analyzer;

import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;
import org.junit.Test;

import java.io.File;

public class ViewManifestIT {

    @Test
    public void testViewManifest() throws Exception {
        File testDir = ResourceExtractor.simpleExtractResources(getClass(), "/projects/view-manifest-test");

        Verifier verifier = new Verifier(testDir.getAbsolutePath());

        verifier.executeGoal("dev.inoyu:osgi-analyzer-maven-plugin:view-manifest");

        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Bundle-SymbolicName:");
        verifier.verifyTextInLog("Bundle-Version:");

        verifier.resetStreams();
    }
}