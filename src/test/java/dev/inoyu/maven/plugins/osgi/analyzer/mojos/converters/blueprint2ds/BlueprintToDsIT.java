package dev.inoyu.maven.plugins.osgi.analyzer.mojos.converters.blueprint2ds;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;

import java.io.File;

public class BlueprintToDsIT extends AbstractMojoTestCase {

    public void testBlueprintToDsConversion() throws Exception {
        File testProjectDir = new File(getBasedir(), "src/test/resources/test-project");
        File pom = new File(testProjectDir, "pom.xml");

        assertNotNull(pom);
        assertTrue(pom.exists());

        BlueprintToDsMojo mojo = (BlueprintToDsMojo) lookupMojo("convert-blueprint-to-ds", pom);
        assertNotNull(mojo);

        mojo.execute();

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
