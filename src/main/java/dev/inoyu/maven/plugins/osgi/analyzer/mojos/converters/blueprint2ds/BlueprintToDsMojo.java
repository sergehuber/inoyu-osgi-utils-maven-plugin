package dev.inoyu.maven.plugins.osgi.analyzer.mojos.converters.blueprint2ds;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.w3c.dom.Element;

import java.io.File;
import java.util.*;

@Mojo(name = "convert-blueprint-to-ds", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class BlueprintToDsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    private File baseDir;

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("Starting Blueprint to DS conversion...");

        try {
            BlueprintProcessor processor = new BlueprintProcessor();
            JavaFileUpdater updater = new JavaFileUpdater();

            File osgiInfDir = new File(baseDir, "src/main/resources/OSGI-INF");
            if (!osgiInfDir.exists()) {
                getLog().warn("OSGI-INF directory not found. Skipping...");
                return;
            }

            for (File blueprintFile : Objects.requireNonNull(osgiInfDir.listFiles((dir, name) -> name.endsWith(".xml")))) {
                Map<String, Object> blueprintData = processor.parseBlueprintFile(blueprintFile);

                List<Element> beans = (List<Element>) blueprintData.get("beans");
                for (Element bean : beans) {
                    String className = bean.getAttribute("class");
                    File javaFile = findJavaFile(className);

                    if (javaFile != null) {
                        Map<String, Object> updates = generateAnnotations(bean);
                        updater.updateJavaFile(javaFile, updates);
                    } else {
                        getLog().warn("Java file not found for class: " + className);
                    }
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error during Blueprint to DS conversion", e);
        }

        getLog().info("Blueprint to DS conversion completed.");
    }

    private File findJavaFile(String className) {
        String relativePath = className.replace(".", "/") + ".java";
        File javaDir = new File(baseDir, "src/main/java");
        File javaFile = new File(javaDir, relativePath);
        return javaFile.exists() ? javaFile : null;
    }

    private Map<String, Object> generateAnnotations(Element bean) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("annotations", Arrays.asList("@Component"));
        updates.put("imports", Arrays.asList("import org.osgi.service.component.annotations.Component;"));
        return updates;
    }
}
