package org.kantega.reststop.development;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.runner.notification.Failure;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

/**
 *
 */
public class ErrorReporter {
    private final VelocityEngine velocityEngine;
    private final File basedir;
    private List<JavaCompilationException> compilationExceptions = new ArrayList<>();
    private List<TestFailureException> testFailureExceptions = new ArrayList<>();

    public ErrorReporter(VelocityEngine velocityEngine, File basedir) {
        this.velocityEngine = velocityEngine;

        this.basedir = basedir;
    }

    public ErrorReporter addCompilationException(JavaCompilationException e) {
        compilationExceptions.add(e);
        return this;
    }


    public void render(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        resp.setContentType("text/html");

        VelocityContext map = new VelocityContext();

        map.put("contextPath", req.getContextPath());
        map.put("compilationExceptions", formatCompilationExceptions());
        map.put("testFailureExceptions", formatTestFailureExceptions());
        velocityEngine.getTemplate("templates/template.vm").merge(map, resp.getWriter());
        resp.getWriter().flush();

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
                        sb.append("description:").append("\"").append(escapeJavascript(failure.getDescription().toString())).append("\",");
                        sb.append("exceptionClass:").append("\"").append(escapeJavascript(failure.getException().getClass().getName())).append("\",");

                        for (StackTraceElement element : failure.getException().getStackTrace()) {
                            if(element.getClassName().equals(failure.getDescription().getTestClass().getName())) {
                                sb.append("sourceFile:").append("\"").append(escapeJavascript(element.getFileName())).append("\",");
                                sb.append("sourceMethod:").append("\"").append(escapeJavascript(element.getMethodName())).append("\",");
                                sb.append("sourceLine:").append(Integer.toString(element.getLineNumber())).append(",");
                            }
                        }


                        File sourceFile = new File(new File(basedir, "src/test/java"), failure.getDescription().getTestClass().getName().replace('.','/') +".java");
                        sb.append("sourceLines:").append(readSourceLines(sourceFile)).append("\n,");

                        if(failure.getException() != null) {
                            StringWriter sw = new StringWriter();
                            failure.getException().printStackTrace(new PrintWriter(sw));
                            sb.append("stackTrace:").append("\"").append(escapeJavascript(sw.toString())).append("\"\n,");
                        }

                        String message = failure.getMessage();
                        if(message != null) {
                            sb.append("message:").append("\"").append(escapeJavascript(message)).append("\"");
                        }

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
            sb.append("message:").append("\"").append(escapeJavascript(diagnostic.getMessage(Locale.getDefault())))
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

                sb.append("\"").append(escapeHTML(line)).append("\"");
            }
            sb.append("]");
            return sb.toString();
        } catch (IOException e) {
            throw  new RuntimeException(e);
        }
    }

    private String escapeJavascript(String message) {
        String replaced = message.replace("\\", "\\\\").replace("\n", "\\n").replace("\"","\\\"");
        return replaced;
    }

    private String escapeHTML(String message) {
        String replaced = message.replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        return replaced;
    }

    public ErrorReporter addTestFailulreException(TestFailureException e) {
        this.testFailureExceptions.add(e);
        return this;
    }
}
