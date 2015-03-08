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

    @Parameter
    private File schemaFile;

    @Parameter(defaultValue = "false")
    private boolean writeToStdOut;
    
    @Parameter()
    private File outputDirectory;

   
    @Parameter(defaultValue="${project}", readonly=true, required=true)
    private MavenProject project;
    
    
    public void execute() throws MojoExecutionException {
        getLog().info("Loading schema from file2: " + schemaFile);
        getLog().info("Will write output to disk: " + writeToStdOut);

        try {
            APISpec apiSpec = getApiSpec();
            validate(apiSpec);
            JSources rootSources = JDeparser.createSources(getFiler(), new FormatPreferences(new Properties()));
            String _package = apiSpec.getOrg() + "." + apiSpec.getApiName();
            JSourceFile apiFile = rootSources.createSourceFile(_package, apiSpec.getServiceName());
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
                JSources currentBuilderSources = JDeparser.createSources(getFiler(), new FormatPreferences(new Properties()));
                JSourceFile currentBuilderFile = currentBuilderSources.createSourceFile(_package, builderClassName);
                JClassDef currentBuilderClass = currentBuilderFile._class(JMod.PUBLIC | JMod.FINAL, builderClassName);
                JType returnType = currentBuilderClass.erasedType();

                //import org.springFramework.web.client.RestTemplate;
                currentBuilderFile._import(RestTemplate.class);
                //private RestTemplate restTemplate
                JVarDeclaration templateField = currentBuilderClass.field(JMod.PRIVATE, RestTemplate.class, "restTemplate");
                //public BootLinodeBuilder() {
                JMethodDef builderConstructorDef = currentBuilderClass.constructor(JMod.PUBLIC);
                //restTemplate = new RestTemplate();
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
                //currentBuilderFile._import(returnType);
                //block.call(JExprs.$(resultDeclaration), "setTemplate").arg(JExprs.$(templateDeclaration));
                // block.assign(JExprs.$(d), JExprs._new(returnType)) ;
                JStatement t = block._return(JExprs.$(resultDeclaration));
                currentBuilderSources.writeSources();
            }
            rootSources.writeSources();
            
            try {
                getLog().info("Adding compiled source:"+project.getBuild().getDirectory());
            project.addCompileSourceRoot(project.getBuild().getDirectory());
            } catch (Throwable e){
                e.printStackTrace();
          
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
        Validate.notBlank(apiSpec.getApiName());
        Validate.notBlank(apiSpec.getOrg());
        Validate.notBlank(apiSpec.getServiceName());
        for (Method m : apiSpec.getMethods()) {
            Validate.notBlank(m.getMethodName());
            for (MandatoryParameter mp : m.getMandatoryParameters()) {
                Validate.notBlank(mp.getJavaName());
                Validate.notBlank(mp.getType());
            }
        }
    }

    private final ConcurrentMap<Key, FileOutputStream> sourceFiles = new ConcurrentHashMap<>();

    private final JFiler filer = new JFiler() {
        public OutputStream openStream(final String packageName, final String fileName) throws IOException {
            getLog().info("Writing for "+fileName);
            final Key key = new Key(packageName, fileName + ".java");
            if (!sourceFiles.containsKey(key)) {
                
                File f = new File(outputDirectory+key.toDirectory());
                if (!f.exists()){
                    getLog().info("creating: "+f.getCanonicalPath());
                    f.mkdirs();
                }
                
                String targetFile = outputDirectory+key.toFileName();
                getLog().info("Writing"+targetFile);
                
                final FileOutputStream stream = new FileOutputStream(targetFile );
                if (sourceFiles.putIfAbsent(key, stream) == null) {
                    if (writeToStdOut) {
                        return System.out;
                    } else {
                        return stream;
                    }
                }
            }
            throw new IOException("Already exists");
        }
    };

    public JFiler getFiler() {
        return filer;
    }

   /* public ByteArrayInputStream openFile(String packageName, String fileName) throws FileNotFoundException {
        final FileOutputStream out = sourceFiles.get(new Key(packageName, fileName));
        if (out == null)
            throw new FileNotFoundException("No file found for package " + packageName + " file " + fileName);
        return new ByteArrayInputStream(out.toByteArray());
    }*/

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
        
        public String toFileName(){
            return new StringBuilder().append("/").append(packageName.replaceAll("\\.", "/")).append("/").append(fileName).toString();
        }
        public String toDirectory(){
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

}
