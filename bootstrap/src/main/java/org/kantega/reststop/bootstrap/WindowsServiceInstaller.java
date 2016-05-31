package org.kantega.reststop.bootstrap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides install/uninstall of bootstrap jar as a Windows service. Supports only 64-bit Windows.
 * <p>
 * During install (or uninstall) two directories are created, in the jar's location:
 * <ul>
 * <li>bin: Contains two executable files:
 * <ul>
 * <li>the Windows service (wrapper), named <i>[serviceName].exe</i>.</li>
 * <li>the the GUI manager application used to monitor and configure the Windows service, named <i>[serviceName]w
 * .exe</i>.</li>
 * </ul>
 * </li>
 * <li>logs: Contains log files for the Windows service.
 * </ul>
 * </p>
 */
public class WindowsServiceInstaller {

    private static final String INSTALL_PARAM = "--installWinSrv";
    private static final String UNINSTALL_PARAM = "--unInstallWinSrv";
    private static final String SPACE = " ";
    private static final String JAVA_HOME = "java.home";

    static boolean shouldInstallOrUninstall(String[] args) {
        Settings installSettings = parseCli(args);
        return isWindows() && !installSettings.serviceName.isEmpty();
    }

    static void installOrUninstallAndExit(String[] args, Main.Settings settings) {

        if (!isWindows()) {
            System.out.println("Installation as Windows service will only be done on ... Windows!");
            System.exit(1);
        }

        Settings installSettings = parseCli(args);

        if (installSettings.serviceName.isEmpty()) {
            System.out.println("Settings for installation as Windows service are missing!");
            System.exit(2);
        }

        String operation = installSettings.install ? "[INSTALL]" : "[UNINSTALL]";

        try {

            File warLocation = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath());
            if (warLocation.getAbsolutePath().contains("%20")) {
                exitWithUsage(operation + " can't be done from a path with space(es)");
            }

            File binLocation = new File(warLocation.getParentFile(), "bin");
            binLocation.mkdirs();
            File logsLocation = new File(warLocation.getParentFile(), "logs");
            logsLocation.mkdirs();

            File prunsrv = unpackProcrun(binLocation, installSettings.serviceName);
            String[] cmd = createInstallOrUninstallCmd(prunsrv, warLocation, logsLocation, settings, installSettings);

            System.out.println(operation + " Windows service '" + installSettings.serviceName + "' ...");
            Process installProcess = Runtime.getRuntime().exec(cmd);
            int exitValue = installProcess.waitFor();
            if (exitValue != 0) {
                System.out.println(operation + " process returned " + exitValue + ", this is the expected exit value!");
            }
            System.out.println("Finished " + operation + " of service '" + installSettings.serviceName + "'.");
            if (installSettings.install) {
                System.out.println("The service will use the JRE installed in " + System.getProperty(JAVA_HOME));
            }

            System.exit(0);

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to " + operation + " Windows service", e);
        }
    }

    static String getOptions() {
        return isWindows() ?
                "\t" + INSTALL_PARAM + " <service name> (The service will use the JRE installed in " +
                        System.getProperty(JAVA_HOME) + ")\n" +
                        "\t" + UNINSTALL_PARAM + " <service name> " :
                "";
    }

    static boolean isOption(String arg) {
        return isWindows() && INSTALL_PARAM.equals(arg) || UNINSTALL_PARAM.equals(arg);
    }

    private static File unpackProcrun(File location, String serviceName) throws IOException {

        Path unpackedPrunmgrPath = Paths.get(location.getAbsolutePath(), serviceName + "w.exe");
        URL packedPrunmgrUrl = WindowsServiceInstaller.class.getResource("prunmgr.exe");
        try (InputStream in = packedPrunmgrUrl.openStream()) {
            Files.copy(in, unpackedPrunmgrPath, StandardCopyOption.REPLACE_EXISTING);
        }

        Path unpackedPrunsrvPath = Paths.get(location.getAbsolutePath(), serviceName + ".exe");
        URL packedPrunsrvUrl = WindowsServiceInstaller.class.getResource("prunsrv.exe");
        try (InputStream in = packedPrunsrvUrl.openStream()) {
            Files.copy(in, unpackedPrunsrvPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return unpackedPrunsrvPath.toFile();
    }

    private static String[] createInstallOrUninstallCmd(File prunsrv, File warLocation, File logsLocation, Main.Settings
            settings, Settings installSettings) {

        List<String> installCmd = new ArrayList<>();

        installCmd.add(prunsrv.getAbsolutePath());
        installCmd.add((installSettings.install ? "//IS//" : "//DS//") + installSettings.serviceName);

        if (installSettings.install) {

            StringBuilder startParams = new StringBuilder();
            for (String setting : settings.getAsList()) {
                String[] keyAndValue = setting.split(SPACE);
                startParams.append(keyAndValue[0]).append(";").append(keyAndValue[1]).append(";");
            }

            File stdoutLog = new File(logsLocation, installSettings.serviceName + "-stdout.log");
            File stderrLog = new File(logsLocation, installSettings.serviceName + "-stderr.log");

            installCmd.add("--StartMode=jvm");
            installCmd.add("--Jvm=" + installSettings.jvmDllPath);
            installCmd.add("--Classpath=" + warLocation.getAbsolutePath());
            installCmd.add("--StartPath=" + warLocation.getParentFile().getAbsolutePath());

            installCmd.add("--StartClass=" + Main.class.getCanonicalName());
            installCmd.add("--StartMethod=main");
            installCmd.add("--StartParams=" + startParams);

            installCmd.add("--StopMode=jvm");
            installCmd.add("--StopPath=" + warLocation.getParentFile().getAbsolutePath());
            installCmd.add("--StopClass=" + Main.class.getCanonicalName());
            installCmd.add("--StopMethod=shutdown");
            installCmd.add("--StopTimeout=5");

            installCmd.add("--LogPath=" + logsLocation.getAbsolutePath());
            installCmd.add("--LogLevel=Debug");
            installCmd.add("--LogPrefix=" + installSettings.serviceName);
            installCmd.add("--StdOutput=" + stdoutLog.getAbsolutePath());
            installCmd.add("--StdError=" + stderrLog.getAbsolutePath());
        }

        return installCmd.toArray(new String[installCmd.size()]);
    }

    private static void exitWithUsage(String message) {
        System.out.println("ERROR: " + message);
        System.out.println("Options:" + getOptions());
        System.exit(3);
    }

    private static Settings parseCli(String[] args) {

        boolean installWinSrv = false;

        String serviceName = "";
        String javaHome = "";

        for (int i = 0; i < args.length; i++) {
            if (INSTALL_PARAM.equals(args[i])) {
                installWinSrv = true;
                if (i == args.length - 1) {
                    exitWithUsage(INSTALL_PARAM + " option requires a service name");
                }
                serviceName = args[i + 1];
                i++;
            }
            if (UNINSTALL_PARAM.equals(args[i])) {
                if (i == args.length - 1) {
                    exitWithUsage(UNINSTALL_PARAM + " option requires a service name");
                }
                serviceName = args[i + 1];
                i++;
            }
        }

        String jvmDllPath = "";
        if (installWinSrv) {

            javaHome = System.getProperty(JAVA_HOME, "");
            if (javaHome.isEmpty()) {
                exitWithUsage("Failed to find Java installation, '" + JAVA_HOME + "' system property is empty.");
            }
            if (javaHome.contains(SPACE)) {
                exitWithUsage("Java must be installed in a path without space(es). " +
                        "Use 'dir /X' or 'for %I in (.) do echo %~sI' to find path with short name notation.");
            }
            jvmDllPath = javaHome + "\\bin\\server\\jvm.dll";
            File jvmDll = new File(jvmDllPath);
            if (!jvmDll.exists()) {
                exitWithUsage("Failed to find path to an existing jvm.dll file, it should be here: " + jvmDllPath);
            }
        }

        return new Settings(installWinSrv, serviceName, jvmDllPath);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static class Settings {

        private final boolean install;
        private final String serviceName;
        private final String jvmDllPath;

        private Settings(boolean install, String serviceName, String jvmDllPath) {
            this.install = install;
            this.serviceName = serviceName;
            this.jvmDllPath = jvmDllPath;
        }
    }
}
