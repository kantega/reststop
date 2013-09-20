package org.kantega.reststop.development;

import org.apache.commons.io.IOUtils;
import org.junit.runner.notification.Failure;

import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

/**
 *
 */
public class ErrorReporter {
    private final File basedir;
    private List<JavaCompilationException> compilationExceptions = new ArrayList<>();
    private List<TestFailureException> testFailureExceptions = new ArrayList<>();

    public ErrorReporter(File basedir) {

        this.basedir = basedir;
    }

    public ErrorReporter addCompilationException(JavaCompilationException e) {
        compilationExceptions.add(e);
        return this;
    }


    public void render(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        OutputStream outputStream = resp.getOutputStream();
        String contextPath = req.getContextPath();
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        resp.setContentType("text/html");

        Map<String, String> map = new HashMap<>();
        map.put("contextPath", contextPath);
        map.put("compilationExceptions", formatCompilationExceptions());
        map.put("testFailureExceptions", formatTestFailureExceptions());
        byte[] bytes = render(map);
        resp.setContentLength(bytes.length);
        outputStream.write(bytes);

    }

    private String formatTestFailureExceptions() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (TestFailureException e : testFailureExceptions) {
            if(sb.length() != 1) {
                sb.append(",\n");
            }
            for(int f = 0; f < e.getFailures().size(); f++) {
                Failure failure = e.getFailures().get(f);
                if(f > 0) {
                    sb.append(",\n");
                }
                sb.append("[");
                {
                    sb.append("{");
                    {
                        sb.append("description:").append("\"").append(escape(failure.getDescription().toString())).append("\",");
                        sb.append("exceptionClass:").append("\"").append(escape(failure.getException().getClass().getName())).append("\",");
                        String message = failure.getMessage();

                        for (StackTraceElement element : failure.getException().getStackTrace()) {
                            if(element.getClassName().equals(failure.getDescription().getTestClass().getName())) {
                                sb.append("sourceFile:").append("\"").append(escape(element.getFileName())).append("\",");
                                sb.append("sourceMethod:").append("\"").append(escape(element.getMethodName())).append("\",");
                                sb.append("sourceLine:").append(Integer.toString(element.getLineNumber())).append(",");
                            }
                        }

                        File sourceFile = new File(new File(basedir, "src/test/java"), failure.getDescription().getTestClass().getName().replace('.','/') +".java");
                        sb.append("sourceLines:").append(readSourceLines(sourceFile)).append("\n,");

                        sb.append("message:").append("\"").append(escape(message)).append("\"");

                    }
                    sb.append("}");
                }
                sb.append("]");
            }
        }

        sb.append("]");
        return sb.toString();
    }

    private String formatCompilationExceptions() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (JavaCompilationException exception : compilationExceptions) {
            if(sb.length() != 1) {
                sb.append(",");
            }
            formatException(sb, exception);
        }
        sb.append("]");
        return sb.toString();
    }

    private void formatException(StringBuilder sb, JavaCompilationException exception) {
        sb.append("[");
        boolean first = true;
        for (Diagnostic<? extends JavaFileObject> diagnostic : exception.getDiagnostics()) {
            if(!first) {
                sb.append(",");
            }
            first = false;
            sb.append("{");
            sb.append("kind:").append("\"").append(diagnostic.getKind())
                    .append("\"\n,");

            String filename = diagnostic.getSource().getName();

            sb.append("sourceLines:").append(readSourceLines(new File(filename))).append("\n,");


            filename = basedir.toPath().relativize(new File(filename).toPath()).toString();

            sb.append("source:").append("\"").append(filename)
                    .append("\"\n,");
            sb.append("lineNumber:").append("\"").append(diagnostic.getLineNumber())
                    .append("\"\n,");
            sb.append("message:").append("\"").append(escape(diagnostic.getMessage(Locale.getDefault())))
                    .append("\"\n");

            sb.append("}");
        }
        sb.append("]");
    }

    private String readSourceLines(File file) {
        StringBuilder sb = new StringBuilder();

        try {
            sb.append("[");
            for(String line : Files.readAllLines(file.toPath(), Charset.forName("utf-8"))) {
                if(sb.length() != 1) {
                    sb.append(" ,\n");
                }

                sb.append("\"").append(escape(line)).append("\"");
            }
            sb.append("]");
            return sb.toString();
        } catch (IOException e) {
            throw  new RuntimeException(e);
        }
    }

    private String escape(String message) {
        String replaced = message.replace("\\", "\\\\").replace("\n", "\\n").replace("\"","\\\"");
        return replaced;
    }

    byte[] render(Map<String, String> values) throws IOException {
        InputStream stream = getClass().getResourceAsStream("template.html");
        String template = IOUtils.toString(stream, "utf-8");

        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("${" + entry.getKey() + "}", entry.getValue());
        }


        return rendered.getBytes("utf-8");
    }

    public ErrorReporter addTestFailulreException(TestFailureException e) {
        this.testFailureExceptions.add(e);
        return this;
    }
}
