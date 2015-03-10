package uk.co.solong.rest2java;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jboss.jdeparser.FormatPreferences;
import org.jboss.jdeparser.JBlock;
import org.jboss.jdeparser.JClassDef;
import org.jboss.jdeparser.JDeparser;
import org.jboss.jdeparser.JExprs;
import org.jboss.jdeparser.JFiler;
import org.jboss.jdeparser.JMethodDef;
import org.jboss.jdeparser.JMod;
import org.jboss.jdeparser.JParamDeclaration;
import org.jboss.jdeparser.JSourceFile;
import org.jboss.jdeparser.JSources;
import org.jboss.jdeparser.JStatement;
import org.jboss.jdeparser.JType;
import org.jboss.jdeparser.JTypes;
import org.jboss.jdeparser.JVarDeclaration;
import org.springframework.web.client.RestTemplate;

import uk.co.solong.rest2java.spec.APISpec;
import uk.co.solong.rest2java.spec.MandatoryParameter;
import uk.co.solong.rest2java.spec.MandatoryPermaParam;
import uk.co.solong.rest2java.spec.Method;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        getLog().info("Loading schema from file2: " + schemaFile);
        if (!writeToStdOut) {
            getLog().info("Will write output to disk: " + outputDirectory);
        } else {
            getLog().info("Will write to STDOUT");
        }
        if (!schemaFile.exists()) {
            throw new MojoExecutionException("No schema file provided");
        }

        try {
            APISpec apiSpec = getApiSpec();
            validate(apiSpec);
            JFiler filer = getFiler();
            JSources rootSources = JDeparser.createSources(filer, new FormatPreferences(new Properties()));
            // String _package = apiSpec.getOrg() + "." + apiSpec.getApiName();
            JSourceFile apiFile = rootSources.createSourceFile(targetPackage, apiSpec.getServiceName());
            // apiFile._import()
            JClassDef apiClass = apiFile._class(JMod.PUBLIC | JMod.FINAL, apiSpec.getServiceName());

            if (apiSpec.getMandatoryPermaParams().size() > 0) {
                JMethodDef constructorDef = apiClass.constructor(JMod.PUBLIC);
                for (MandatoryPermaParam mpp : apiSpec.getMandatoryPermaParams()) {
                    JParamDeclaration param = constructorDef.param(JMod.FINAL, mpp.getType(), mpp.getJavaName());
                    JVarDeclaration field = apiClass.field(JMod.PRIVATE | JMod.FINAL, mpp.getType(), "_" + mpp.getJavaName());
                    constructorDef.body().assign(JExprs.$(field), JExprs.$(param));
                }
            }
            // create the method bodies
            for (Method methodSpec : apiSpec.getMethods()) {
                // for each method, generate a builderclass
                String methodName = methodSpec.getMethodName();
                String builderClassName = StringUtils.capitalize(methodName) + "Builder";
                // = JTypes._(builderClassName);
                JSources currentBuilderSources = JDeparser.createSources(filer, new FormatPreferences(new Properties()));
                JSourceFile currentBuilderFile = currentBuilderSources.createSourceFile(targetPackage, builderClassName);
                JClassDef currentBuilderClass = currentBuilderFile._class(JMod.PUBLIC | JMod.FINAL, builderClassName);
                JType returnType = currentBuilderClass.erasedType();

                // import org.springFramework.web.client.RestTemplate;
                currentBuilderFile._import(RestTemplate.class);
                // private RestTemplate restTemplate
                JVarDeclaration templateField = currentBuilderClass.field(JMod.PRIVATE, RestTemplate.class, "restTemplate");
                // public BootLinodeBuilder() {
                JMethodDef builderConstructorDef = currentBuilderClass.constructor(JMod.PUBLIC);
                // restTemplate = new RestTemplate();
                builderConstructorDef.body().assign(JExprs.$(templateField), JTypes._(RestTemplate.class)._new());

                // method name
                JMethodDef currentMethod = apiClass.method(JMod.PUBLIC | JMod.FINAL, returnType, methodName);
                // method parameters
                for (MandatoryParameter mp : methodSpec.getMandatoryParameters()) {
                    JParamDeclaration vap = currentMethod.param(JMod.FINAL, mp.getType(), mp.getJavaName());
                }
                JBlock block = currentMethod.body();

                // JExprs.
                // final SubmitNameBuilder result = new SubmitNameBuilder();
                JVarDeclaration resultDeclaration = block.var(JMod.FINAL, returnType, "result", returnType._new());
                // currentBuilderFile._import(returnType);
                // block.call(JExprs.$(resultDeclaration),
                // "setTemplate").arg(JExprs.$(templateDeclaration));
                // block.assign(JExprs.$(d), JExprs._new(returnType)) ;
                JStatement t = block._return(JExprs.$(resultDeclaration));
                currentBuilderSources.writeSources();
            }
            rootSources.writeSources();
            

            if (!writeToStdOut) {
                getLog().info("Adding compiled source:" + outputDirectory.getPath());
                project.addCompileSourceRoot(outputDirectory.getPath());
            } else {
                getLog().info("STDOUT is enabled. Not adding compiled source to maven classpath");
            }

        } catch (IOException e) {
            throw new MojoExecutionException("Schema format is invalid:", e);
        }

    }

    private APISpec getApiSpec() throws IOException, JsonParseException, JsonMappingException {
        ObjectMapper objectMapper = new ObjectMapper();

        APISpec apiSpec = objectMapper.readValue(schemaFile, APISpec.class);
        return apiSpec;
    }

    private void validate(APISpec apiSpec) {
        // TODO Auto-generated method stub
        // Validate.notBlank(apiSpec.getApiName());
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

    private final ConcurrentMap<Key, OutputStream> sourceFiles = new ConcurrentHashMap<>();

    private final JFiler filer = new JFiler() {
        public OutputStream openStream(final String packageName, final String fileName) throws IOException {
            getLog().info("Writing for " + fileName);
            final Key key = new Key(packageName, fileName + ".java");
            if (!sourceFiles.containsKey(key)) {
                OutputStream stream = null;
                if (writeToStdOut) {
                    stream = System.out;
                } else {
                    File f = new File(outputDirectory + key.toDirectory());

                    if (!f.exists()) {
                        getLog().info("Output directory does not exist. Creating: " + f.getCanonicalPath());
                        f.mkdirs();
                    } else {
                        getLog().info("Output directory exists " + f.getCanonicalPath());
                    }
                    String targetFile = outputDirectory + key.toFileName();
                    getLog().info("Writing" + targetFile);
                    stream = new FileOutputStream(targetFile);
                }
                if (sourceFiles.putIfAbsent(key, stream) == null) {
                    return stream;
                }
            }
            throw new IOException("Already exists");
        }
    };

    public JFiler getFiler() {
        return filer;
    }

    /*
     * public ByteArrayInputStream openFile(String packageName, String fileName)
     * throws FileNotFoundException { final FileOutputStream out =
     * sourceFiles.get(new Key(packageName, fileName)); if (out == null) throw
     * new FileNotFoundException("No file found for package " + packageName +
     * " file " + fileName); return new ByteArrayInputStream(out.toByteArray());
     * }
     */

    static final class Key {
        private final String packageName;
        private final String fileName;

        Key(final String packageName, final String fileName) {
            this.packageName = packageName;
            this.fileName = fileName;
        }

        public boolean equals(final Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            final Key key = (Key) o;

            return fileName.equals(key.fileName) && packageName.equals(key.packageName);
        }

        public int hashCode() {
            int result = packageName.hashCode();
            result = 31 * result + fileName.hashCode();
            return result;
        }

        public String toFileName() {
            return new StringBuilder().append("/").append(packageName.replaceAll("\\.", "/")).append("/").append(fileName).toString();
        }

        public String toDirectory() {
            return new StringBuilder().append("/").append(packageName.replaceAll("\\.", "/")).append("/").toString();
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
