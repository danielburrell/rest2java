package uk.co.solong.rest2java;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.MojoRule;
import org.junit.Rule;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

public class Test2  {

 
    
    @Test
    public void test()  throws JsonProcessingException, MojoExecutionException {
        Rest2Java rest2Java= new Rest2Java();
        rest2Java.setSchemaFile(new File("D:/workspace/rest2java/src/main/resources/schema.json"));
        
        rest2Java.execute();

    }
}
