package uk.co.solong.rest2java;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

import org.apache.maven.plugin.logging.Log;

import freemarker.core.Environment;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

public class TestModel implements TemplateDirectiveModel {

    private final File outputDirectory;
    private final boolean writeToStdOut;
    private final Log log;

    public TestModel(File outputDirectory, boolean writeToStdOut, Log log) {
        this.outputDirectory = outputDirectory;
        this.writeToStdOut = writeToStdOut;
        this.log = log;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, @SuppressWarnings("rawtypes") Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {

        log.info("Writing class " + extractFqn(params));
        Writer out = null;
        if (writeToStdOut) {
            log.info("Writing class " + extractFileNameFromOutputDirective(params));
            out = new StringWriter();
            body.render(out);
            out.flush();
            log.debug("Class content:\n" + out.toString());
            out.close();
        } else {
            log.info("Writing class " + extractFileNameFromOutputDirective(params));
            String outputFile = extractFileNameFromOutputDirective(params);
            File f = new File(outputFile).getParentFile();
            log.debug("Creating folders");
            f.mkdirs();
            out = new PrintWriter(outputFile, "UTF-8");
            body.render(out);
            out.flush();
            out.close();
        }

        log.info("Done!");
    }

    private String extractFileNameFromOutputDirective(Map<String, SimpleScalar> params) {
        String fqn = extractFqn(params);
        String ffqn = fqn.replace(".", File.separator);
        return outputDirectory.getAbsolutePath() + File.separator + ffqn+".java";
    }

    private String extractFqn(Map<String, SimpleScalar> params) {
        return params.get("file").getAsString();
    }

}
