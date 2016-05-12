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

    static boolean shouldInstallOrUninstall(String[] args) {
        Settings installSettings = parseCli(args);
        return isWindows() && !installSettings.isEmpty();
    }

    static void installOrUninstallAndExit(String[] args, Main.Settings settings) {

        if (!isWindows()) {
            System.out.println("Installation as Windows service will only be done on ... Windows!");
            System.exit(1);
        }

        Settings installSettings = parseCli(args);

        if (installSettings.isEmpty()) {
            System.out.println("Settings for installation as Windows service are missing!");
            System.exit(2);
        }

        String operation = installSettings.install ? "[INSTALL]" : "[UNINSTALL]";

        try {

            File warLocation = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath());

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
            System.out.println("Finished " + operation + " of service '" + installSettings.serviceName + "'");

            System.exit(0);

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to " + operation + " Windows service", e);
        }
    }

    static String getOptions() {
        return isWindows() ?
                "\t--installwinsrv ---serviceName <name> ---jvmDllPath <path to jvm.dll>\n" +
                        "\t--uninstallwinsrv ---serviceName <name> " :
                "";
    }

    static boolean isOption(String arg) {
        return isWindows() && "--installwinsrv".equals(arg) ||
                "--uninstallwinsrv".equals(arg) ||
                "---serviceName".equals(arg) ||
                "---jvmDllPath".equals(arg);
    }

    static int getOptionParameterCount(String arg) {

        if (!isWindows()) {
            throw new IllegalStateException("'" + arg + "' is not an Windows service option");
        } else if ("--installwinsrv".equals(arg) || "--uninstallwinsrv".equals(arg)) {
            return 0;
        } else if ("---serviceName".equals(arg) || "---jvmDllPath".equals(arg)) {
            return 1;
        }

        exitWithUsage("'" + arg + "' is not an option");

        throw new IllegalStateException("'" + arg + "' is not an option");
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
            installCmd.add("--StartMode=jvm");
            installCmd.add("--Jvm=" + installSettings.jvmDllPath);
            installCmd.add("--Classpath=" + warLocation.getAbsolutePath());
            installCmd.add("--StartPath=" + warLocation.getParentFile().getAbsolutePath());

            installCmd.add("--StartClass=" + Main.class.getCanonicalName());
            installCmd.add("--StartMethod=main");
            StringBuilder startParams = new StringBuilder();
            for (String setting : settings.getAsList()) {
                String[] keyAndValue = setting.split(" ");
                startParams.append(keyAndValue[0]).append(";").append(keyAndValue[1]).append(";");
            }
            installCmd.add("--StartParams=" + startParams);

            installCmd.add("--StopMode=jvm");
            installCmd.add("--StopPath=" + warLocation.getParentFile().getAbsolutePath());
            installCmd.add("--StopClass=" + Main.class.getCanonicalName());
            installCmd.add("--StopMethod=shutdown");
            installCmd.add("--StopTimeout=5");

            installCmd.add("--LogPath=" + logsLocation.getAbsolutePath());
            installCmd.add("--LogLevel=Debug");
            installCmd.add("--LogPrefix=" + installSettings.serviceName);
            File stdoutLog = new File(logsLocation, installSettings.serviceName + "-stdout.log");
            installCmd.add("--StdOutput=" + stdoutLog.getAbsolutePath());
            File stderrLog = new File(logsLocation, installSettings.serviceName + "-stderr.log");
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

        boolean installwinsrv = false;
        boolean uninstallwinsrv = false;

        for (int i = 0; i < args.length; i++) {
            if ("--installwinsrv".equals(args[i])) {
                installwinsrv = true;
                if (i == args.length - 1) {
                    exitWithUsage("--installwinsrv option requires more options:");
                }
            }
            if ("--uninstallwinsrv".equals(args[i])) {
                uninstallwinsrv = true;
                if (i == args.length - 1) {
                    exitWithUsage("--uninstallwinsrv option requires more options:");
                }
            }
        }

        String serviceName = "";
        String javaHome = "";

        if (installwinsrv || uninstallwinsrv) {

            for (int i = 0; i < args.length; i++) {
                if ("---serviceName".equals(args[i])) {
                    if (i == args.length - 1) {
                        exitWithUsage("---serviceName option requires a name");
                    }
                    serviceName = args[i + 1];
                    i++;
                }
            }
        }

        if (installwinsrv) {

            for (int i = 0; i < args.length; i++) {
                if ("---jvmDllPath".equals(args[i])) {
                    if (i == args.length - 1) {
                        exitWithUsage("---jvmDllPath option requires a path");
                    }
                    javaHome = args[i + 1];
                }
            }
            File jvmDll = new File(javaHome);
            if (!jvmDll.exists()) {
                exitWithUsage("---jvmDllPath option requires a path to an existing jvm.dll file");
            }
        }

        return new Settings(installwinsrv, serviceName, javaHome);
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

        private boolean isEmpty() {
            return install && (serviceName.isEmpty() || jvmDllPath.isEmpty()) ||
                    !install && serviceName.isEmpty();
        }
    }
}
