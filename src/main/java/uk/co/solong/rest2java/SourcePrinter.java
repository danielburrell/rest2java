package uk.co.solong.rest2java;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.maven.plugin.logging.Log;
import org.jboss.jdeparser.JFiler;

public class SourcePrinter {

    private File outputDirectory;
    private Log log;
    private final ConcurrentMap<Key, ByteArrayOutputStream> sourceFiles = new ConcurrentHashMap<>();

    private final JFiler filer = new JFiler() {
        public OutputStream openStream(final String packageName, final String fileName) throws IOException {
            final Key key = new Key(packageName, fileName + ".java");
            if (!sourceFiles.containsKey(key)) {
                final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                if (sourceFiles.putIfAbsent(key, stream) == null) {
                    return stream;
                }
            }
            throw new IOException("Already exists");
        }
    };

    public void printToStdOut() {
        for (Key key : sourceFiles.keySet()) {
            ByteArrayOutputStream s = sourceFiles.get(key);
            System.out.println(new String(s.toByteArray()));
        }
    }

    public void writeToFile() throws IOException {
        for (Key key : sourceFiles.keySet()) {
            ByteArrayOutputStream s = sourceFiles.get(key);
            File f = new File(outputDirectory + key.toDirectory());

            if (!f.exists()) {
                log.info("Output directory does not exist. Creating: " + f.getCanonicalPath());
                f.mkdirs();
            } else {
                log.debug("Output directory exists: " + f.getCanonicalPath());
            }
            String targetFile = outputDirectory + key.toFileName();
            log.info("Writing " + targetFile);
            OutputStream stream = new FileOutputStream(targetFile);
            s.writeTo(stream);
        }
    }

    public JFiler getFiler() {
        return filer;
    }

    static final class Key {
        private final String packageName;
        private final String fileName;
        private final String packagePath;

        Key(final String packageName, final String fileName) {
            this.packageName = packageName;
            this.fileName = fileName;
            this.packagePath = packageName.replace(".", slash);
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

        private static final String slash = File.separator;

        public String toFileName() {
            System.out.println(slash);
            return new StringBuilder().append(slash).append(packagePath).append(slash).append(fileName).toString();
        }

        public String toDirectory() {
            return new StringBuilder().append(slash).append(packagePath).append(slash).toString();
        }
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public void setLog(Log log) {
        this.log = log;
    }
}
