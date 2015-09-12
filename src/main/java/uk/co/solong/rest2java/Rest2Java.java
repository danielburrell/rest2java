package uk.co.solong.rest2java;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.apache.commons.lang3.Validate;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import uk.co.solong.rest2java.spec.APISpec;
import uk.co.solong.rest2java.spec.MandatoryParameter;
import uk.co.solong.rest2java.spec.Method;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateNotFoundException;

@Mojo(name = "rest2java", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class Rest2Java extends AbstractMojo {

    @Parameter(defaultValue = "${basedir}/src/main/resources/schema.json")
    private File schemaFile;

    @Parameter(defaultValue = "false")
    private boolean writeToStdOut;

    @Parameter(defaultValue = "mypackage")
    private String targetPackage;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources")
    private File outputDirectory;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    public void execute() throws MojoExecutionException {

        getLog().info("Loading schema from file: " + schemaFile);
        getLog().info("Package is: " + targetPackage);
        if (!writeToStdOut) {
            getLog().info("Will write output to disk: " + outputDirectory);
        } else {
            getLog().info("Will write to STDOUT");
        }
        if (!schemaFile.exists()) {
            throw new MojoExecutionException("No schema file provided");
        }

        Configuration cfg = new Configuration(Configuration.VERSION_2_3_22);
        cfg.setSharedVariable("output", new TestModel(outputDirectory, writeToStdOut, getLog()));
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("templates/apiTemplate.ftl").getFile());

        try {
            cfg.setDirectoryForTemplateLoading(file.getParentFile());
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to set template directory", e);
        }
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

        APISpec apiSpec = null;
        try {
            apiSpec = getApiSpec();
            apiSpec.setPackage(targetPackage);
            validate(apiSpec);
        } catch (IOException e1) {
            throw new MojoExecutionException("Unable to parse Rest2Java API spec. Please check your spec is valid JSON", e1);
        }

        try {
            generateApiClass(cfg, apiSpec);
            generateBuilderClasses(cfg, apiSpec);
        } catch (IOException | TemplateException e) {
            throw new MojoExecutionException("Unable to generate classes", e);
        }
    }

    private void generateBuilderClasses(Configuration cfg, APISpec apiSpec) throws TemplateNotFoundException, MalformedTemplateNameException, ParseException,
            IOException, TemplateException {

        /* Get the template (uses cache internally) */
        Template temp = cfg.getTemplate("builderTemplate.ftl");
        Writer out = new OutputStreamWriter(System.out);
        temp.process(apiSpec, out);
    }

    private void generateApiClass(Configuration cfg, APISpec apiSpec) throws TemplateNotFoundException, MalformedTemplateNameException, ParseException,
            IOException, TemplateException {

        Template temp = cfg.getTemplate("apiTemplate.ftl");
        Writer out = new OutputStreamWriter(System.out);
        temp.process(apiSpec, out);
    }

    private APISpec getApiSpec() throws IOException, JsonParseException, JsonMappingException {
        ObjectMapper objectMapper = new ObjectMapper();
        APISpec apiSpec = objectMapper.readValue(schemaFile, APISpec.class);
        return apiSpec;
    }

    private void validate(APISpec apiSpec) {
        Validate.notBlank(targetPackage, "Package must not be null");
        Validate.notBlank(apiSpec.getServiceName(), "ServiceName must not be null");
        for (Method m : apiSpec.getMethods()) {
            Validate.notBlank(m.getMethodName(), "Method name must not be blank");
            for (MandatoryParameter mp : m.getMandatoryParameters()) {
                Validate.notBlank(mp.getJavaName(), "Parameter name must be specified in method {}", m.getMethodName());
                Validate.notBlank(mp.getType(), "Parameter type must be specified for {} in method {}", mp.getJavaName(), m.getMethodName());
            }
        }
    }

    public File getSchemaFile() {
        return schemaFile;
    }

    public void setSchemaFile(File schemaFile) {
        this.schemaFile = schemaFile;
    }

    public boolean isWriteToStdOut() {
        return writeToStdOut;
    }

    public void setWriteToStdOut(boolean writeToStdOut) {
        this.writeToStdOut = writeToStdOut;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public String getTargetPackage() {
        return targetPackage;
    }

    public void setTargetPackage(String targetPackage) {
        this.targetPackage = targetPackage;
    }

}
