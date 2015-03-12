package uk.co.solong.rest2java;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.jboss.jdeparser.JCall;
import org.jboss.jdeparser.JClassDef;
import org.jboss.jdeparser.JDeparser;
import org.jboss.jdeparser.JExpr;
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
import org.jsonschema2pojo.DefaultGenerationConfig;
import org.jsonschema2pojo.GenerationConfig;
import org.jsonschema2pojo.Jackson2Annotator;
import org.jsonschema2pojo.SchemaGenerator;
import org.jsonschema2pojo.SchemaMapper;
import org.jsonschema2pojo.SchemaStore;
import org.jsonschema2pojo.rules.RuleFactory;
import org.springframework.web.client.RestTemplate;

import uk.co.solong.rest2java.spec.APISpec;
import uk.co.solong.rest2java.spec.MandatoryParameter;
import uk.co.solong.rest2java.spec.MandatoryPermaParam;
import uk.co.solong.rest2java.spec.Method;
import uk.co.solong.rest2java.spec.OptionalParameter;
import uk.co.solong.rest2java.spec.ReturnType;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.codemodel.JCodeModel;

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

    private SourcePrinter sourcePrinter;

    public Rest2Java() {
        sourcePrinter = new SourcePrinter();
    }

    public void execute() throws MojoExecutionException {
        sourcePrinter.setLog(getLog());
        sourcePrinter.setOutputDirectory(outputDirectory);
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

        try {
            APISpec apiSpec = getApiSpec();
            validate(apiSpec);
            JFiler filer = sourcePrinter.getFiler();
            JSources rootSources = JDeparser.createSources(filer, new FormatPreferences(new Properties()));
            // String _package = apiSpec.getOrg() + "." + apiSpec.getApiName();
            JSourceFile apiFile = rootSources.createSourceFile(targetPackage, apiSpec.getServiceName());
            
            JClassDef apiClass = apiFile._class(JMod.PUBLIC | JMod.FINAL, apiSpec.getServiceName());


            //declare the mandatory parameters as fields first
            Map<MandatoryPermaParam, JVarDeclaration> mandatoryParamToFieldMap = new HashMap<>();
            for (MandatoryPermaParam mpp : apiSpec.getMandatoryPermaParams()) {
                JVarDeclaration field = apiClass.field(JMod.PRIVATE | JMod.FINAL, mpp.getType(), "_" + mpp.getJavaName());
                mandatoryParamToFieldMap.put(mpp, field);
            }
            
            //take api parameters etc
            if (apiSpec.getMandatoryPermaParams().size() > 0) {
                JMethodDef constructorDef = apiClass.constructor(JMod.PUBLIC);
                for (MandatoryPermaParam mpp : apiSpec.getMandatoryPermaParams()) {
                    JParamDeclaration param = constructorDef.param(JMod.FINAL, mpp.getType(), mpp.getJavaName());
                    constructorDef.body().assign(JExprs.$(mandatoryParamToFieldMap.get(mpp)), JExprs.$(param));
                }
            }
            
            // create the method bodies
            for (Method methodSpec : apiSpec.getMethods()) {
                String methodName = methodSpec.getMethodName();
                
                //generate the return type classes
                JCodeModel codeModel = new JCodeModel();
                ReturnType retTypeJsonSchema = methodSpec.getReturnType();
                ObjectMapper mapper = new ObjectMapper();
                String retTypeJsonSchemAsString = mapper.writeValueAsString(retTypeJsonSchema);
                String returnTypeClassName = StringUtils.capitalize(methodName)+"Result";
                String returnTypePackageString = targetPackage+"."+StringUtils.lowerCase(methodSpec.getMethodName());
                String qualifiedReturnTypeClassName = returnTypePackageString+"."+returnTypeClassName;
                DefaultGenerationConfig config = new DefaultGenerationConfig(){
                    @Override
                    public boolean isUseCommonsLang3() {
                        // TODO Auto-generated method stub
                        return true;
                    }
                };
                
                RuleFactory ruleFactory = new RuleFactory(config , new Jackson2Annotator(), new SchemaStore());
         
                new SchemaMapper(ruleFactory , new SchemaGenerator()).generate(codeModel, returnTypeClassName , returnTypePackageString, retTypeJsonSchemAsString);
                if (!outputDirectory.exists()){
                    outputDirectory.mkdirs();
                }
                codeModel.build(outputDirectory);
                
                
                // generate a builderclass file for this method
                String builderClassName = StringUtils.capitalize(methodName) + "Builder";
                JSources currentBuilderSources = JDeparser.createSources(filer, new FormatPreferences(new Properties()));
                JSourceFile currentBuilderFile = currentBuilderSources.createSourceFile(targetPackage, builderClassName);
                JClassDef currentBuilderClass = currentBuilderFile._class(JMod.PUBLIC | JMod.FINAL, builderClassName);
                JType returnType = currentBuilderClass.erasedType();

                // import org.springFramework.web.client.RestTemplate;
                currentBuilderFile._import(RestTemplate.class);
                currentBuilderFile._import(JsonNode.class);
                currentBuilderFile._import(java.util.Map.class);
                currentBuilderFile._import(java.util.HashMap.class);
                currentBuilderFile._import(qualifiedReturnTypeClassName);
                
                // private fields in the builder class
                JVarDeclaration templateField = currentBuilderClass.field(JMod.PRIVATE, RestTemplate.class, "restTemplate");
                JVarDeclaration parameterMapField = currentBuilderClass.field(JMod.PRIVATE, "Map<String,Object>", "parameters");
                
                //builder constructor
                JMethodDef builderConstructorDef = currentBuilderClass.constructor(JMod.PUBLIC);
                //add constructor params from the api's mandatory set
                for (MandatoryPermaParam mpp : apiSpec.getMandatoryPermaParams()) {
                    JParamDeclaration param = builderConstructorDef.param(JMod.FINAL, mpp.getType(), mpp.getJavaName());
                }
                //add constructor params from the builders mandatory set
                for (MandatoryParameter mpp : methodSpec.getMandatoryParameters()) {
                    JParamDeclaration param = builderConstructorDef.param(JMod.FINAL, mpp.getType(), mpp.getJavaName());
                }
                
                //add constructor params from the method's mandatory set
                builderConstructorDef.body().assign(JExprs.$(templateField), JTypes._(RestTemplate.class)._new());
                builderConstructorDef.body().assign(JExprs.$(parameterMapField), JTypes._("HashMap<String,Object>")._new());
                
                //assign the parameters in the builder constructor into a hashmap
                for (MandatoryPermaParam mpp : apiSpec.getMandatoryPermaParams()) {
                    builderConstructorDef.body().add(JExprs.$(parameterMapField).call("put").arg(JExprs.$("\""+mpp.getJsonName()+"\"")).arg(JExprs.$(mpp.getJavaName())));
                }
                
              //assign the parameters in the builder constructor into a hashmap
                for (MandatoryParameter mpp : methodSpec.getMandatoryParameters()) {
                    builderConstructorDef.body().add(JExprs.$(parameterMapField).call("put").arg(JExprs.$("\""+mpp.getJsonName()+"\"")).arg(JExprs.$(mpp.getJavaName())));
                }

                //go method
                JMethodDef goMethodDef = currentBuilderClass.method(JMod.PUBLIC, qualifiedReturnTypeClassName, "go");               
                String methodUrl = apiSpec.getDefaultBaseUrl()+methodSpec.getUrl();
                JVarDeclaration goMethodReturnDeclaration = goMethodDef.body().var(JMod.FINAL, qualifiedReturnTypeClassName, "result",JExprs.$(templateField).call("getForObject").arg(JExprs.$("\""+methodUrl+"\"")).arg(JExprs.$(qualifiedReturnTypeClassName+".class")).arg(JExprs.$(parameterMapField)));
                goMethodDef.body()._return(JExprs.$(goMethodReturnDeclaration));
                
                //create the with() methods
                for (OptionalParameter i : methodSpec.getOptionalParameters()){
                    String optionalMethodName = "with"+StringUtils.capitalize(i.getJavaName());
                    JMethodDef methodDef = currentBuilderClass.method(JMod.PUBLIC, returnType, optionalMethodName);
                    methodDef.param(JMod.FINAL, i.getType(), i.getJavaName());
                    methodDef.body().add(JExprs.$(parameterMapField).call("put").arg(JExprs.$("\""+i.getJsonName()+"\"")).arg(JExprs.$(i.getJavaName())));
                    methodDef.body()._return(JExprs.name("this"));
                    
                }
                
                // method name
                JMethodDef currentMethod = apiClass.method(JMod.PUBLIC | JMod.FINAL, returnType, methodName);
                List<JParamDeclaration> methodParamdeclarationList = new ArrayList<JParamDeclaration>();
                // method parameters
                for (MandatoryParameter mp : methodSpec.getMandatoryParameters()) {
                    JParamDeclaration vap = currentMethod.param(JMod.FINAL, mp.getType(), mp.getJavaName());
                    methodParamdeclarationList.add(vap);
                }
                JBlock block = currentMethod.body();

                // JExprs.
                // final SubmitNameBuilder result = new SubmitNameBuilder();
                JCall myNew = returnType._new();
                //pass each of the fixed parameters
                for (MandatoryPermaParam mp : apiSpec.getMandatoryPermaParams()) {
                    myNew.arg(JExprs.$("_"+mp.getJavaName()));
                }
                
                //pass eacah of the mandatory method parameters    
                for (JParamDeclaration param:methodParamdeclarationList){
                    myNew.arg(JExprs.$(param));
                }
                JVarDeclaration resultDeclaration = block.var(JMod.FINAL, returnType, "result", myNew);
                // currentBuilderFile._import(returnType);
                // block.call(JExprs.$(resultDeclaration),
                // "setTemplate").arg(JExprs.$(templateDeclaration));
                // block.assign(JExprs.$(d), JExprs._new(returnType)) ;
                JStatement sometType = block._return(JExprs.$(resultDeclaration));
                currentBuilderSources.writeSources();
            }
            rootSources.writeSources();

            if (!writeToStdOut) {
                sourcePrinter.writeToFile();
                getLog().info("Adding compiled source:" + outputDirectory.getPath());
                project.addCompileSourceRoot(outputDirectory.getPath());
            } else {
                getLog().info("STDOUT is enabled. Not adding compiled source to maven classpath");
                sourcePrinter.printToStdOut();
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
