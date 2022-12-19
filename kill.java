///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.6.3
//DEPS org.zeroturnaround:zt-exec:1.12
//DEPS org.slf4j:slf4j-simple:1.7.30

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static java.lang.System.out;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.zeroturnaround.exec.ProcessExecutor;

@Command(name = "kill", mixinStandardHelpOptions = true, version = "kill 0.1", description = "kill made with jbang")
class kill implements Callable<Integer> {

    @Option(names = { "--port", "-p" }, description = "Port to try kill", defaultValue = "8080")
    int port;

    @Option(names = { "--force", "-9", "-f" }, description = "Force kill")
    boolean force;

    public static void main(String... args) {
        int exitCode = new CommandLine(new kill()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...

        final List<String> kills = new ArrayList<>();

        System.out.println("Scanning for processes listening on port " + port + " ...");

        if (getOS() == OS.WINDOWS) {
            List<String> args = List.of("netstat", "-ano");
            String output = new ProcessExecutor().command(args)
                    .readOutput(true).execute().outputUTF8();

            output.lines().forEach(line -> {
                String[] parts = line.split("\\s+");
                if (parts.length >= 5) {
                    String listen = parts[2];
                    String pid = parts[parts.length-1];
                    try {
                        if(listen.endsWith(":" + port) && !"0".equals(pid)) {
                        kills.add(pid);
                        out.println("Kill " + pid);
                        List<String> cmd = new ArrayList<>();
                        cmd.add("taskkill");
                        if(force) {
                            cmd.add("/F");
                        }
                        cmd.add("/T");
                        cmd.add("/PID");
                        cmd.add(pid);
                        int res = new ProcessExecutor().command(cmd).readOutput(true).execute().getExitValue();
                        if(res!=0) {
                            System.err.println("Process " + pid + " not killed. Try again using --force");
                            kills.remove(pid);
                        }
                    }
                    } catch (Exception e) {
                        System.err.println("Error killing " + pid);
                        kills.remove(pid);
                    }
                }
            });

        } else {

            String output = new ProcessExecutor().command("lsof", "-i:" + port)
                    .readOutput(true).execute().outputUTF8();

            output.lines().skip(1).forEach(line -> {
                String[] parts = line.split("\\s+");
                String pid = parts[1];
                try {
                    out.println("Killing " + pid);
                    new ProcessExecutor().command("kill", force ? "-9" : "-15", pid).execute();
                } catch (Exception e) {
                    System.err.println("Error killing " + pid);
                    kills.remove(pid);
                }
            });

            
        }

        if (kills.isEmpty()) {
            out.println("No process killed or not found any proces on port " + port);
            out.println("If looking for a different port use --port <port>");
        }
        return 0;
    }

    // from
    // https://stackoverflow.com/questions/228477/how-do-i-programmatically-determine-operating-system-in-java
    static public enum OS {
        WINDOWS, LINUX, MAC, SOLARIS
    };// Operating systems.

    private static OS os = null;

    public static OS getOS() {
        if (os == null) {
            String operSys = System.getProperty("os.name").toLowerCase();
            if (operSys.contains("win")) {
                os = OS.WINDOWS;
            } else if (operSys.contains("nix") || operSys.contains("nux")
                    || operSys.contains("aix")) {
                os = OS.LINUX;
            } else if (operSys.contains("mac")) {
                os = OS.MAC;
            } else if (operSys.contains("sunos")) {
                os = OS.SOLARIS;
            }
        }
        return os;
    }
}
