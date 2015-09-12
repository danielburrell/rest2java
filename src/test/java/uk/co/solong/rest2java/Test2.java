package uk.co.solong.rest2java;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import freemarker.template.TemplateException;

public class Test2  {

 
    @Test
    public void testApiKeyPassedOnTemplate() {
        
    }
    
    @Test 
    public void shouldPass()  throws MojoExecutionException, TemplateException, IOException {
        Rest2Java rest2Java= new Rest2Java();
        rest2Java.setSchemaFile(new File("D:/workspace/rest2java/src/main/resources/schema.json"));
        rest2Java.setOutputDirectory(new File("D:/workspace/rest2java/target/generated-sources/"));
        rest2Java.setWriteToStdOut(true);
        rest2Java.setTargetPackage("uk.co.solong.linode4j");
        rest2Java.execute();

    }
}
