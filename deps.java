/// usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.jbang:jash:0.0.3
//DEPS com.fasterxml.jackson.core:jackson-databind:2.19.1
//DEPS info.picocli:picocli:4.7.7

import static dev.jbang.jash.Jash.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedList;
import java.util.List;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Command(name = "deps", mixinStandardHelpOptions = true, version = "deps 0.1",
        description = "Analyze JBang script dependencies (//DEPS)")

public class deps implements Callable<Integer> {

    @Parameters(index = "0", description = "JBang script to analyze", defaultValue = "")
    private String script;

    public static void main(String... args) throws Exception {
        int exitCode = new CommandLine(new deps()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (script.equals("")) {
            return 1;
        }
        String toolboxJar = "https://repo.maven.apache.org/maven2/eu/maveniverse/maven/plugins/toolbox/0.11.2/toolbox-0.11.2-cli.jar ";
        String jbang_launch_cmd = System.getenv("JBANG_LAUNCH_CMD");
        String dependencies = $(jbang_launch_cmd + " info tools --select=dependencies " + script).get();
        JsonNode deps = new ObjectMapper().readTree(dependencies);
        List<String> gavList = new LinkedList<>();
        deps.forEach(
                dep -> {
                    gavList.add(dep.asText());
                });
        String result = $(jbang_launch_cmd + " trust add " + toolboxJar).get();
        String command = jbang_launch_cmd + " run " + toolboxJar + " versions " + String.join(",", gavList);
        $(command).stream().forEach(System.out::println);
        return 0;
    }

}
