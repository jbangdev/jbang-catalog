///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS dev.jbang:jash:0.0.3
//DEPS com.fasterxml.jackson.core:jackson-databind:2.19.1
//DEPS info.picocli:picocli:4.7.7

//DEPS eu.maveniverse.maven.plugins:toolbox:0.11.4
//DEPS org.apache.maven:maven-plugin-api:3.9.11
//DEPS org.apache.maven:maven-settings:3.9.10
//DEPS eu.maveniverse.maven.mima.runtime:standalone-static:2.4.30

//DEPS org.slf4j:slf4j-simple:2.0.17

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
        description = "Analyze JBang script dependencies")

public class deps implements Callable<Integer> {

    @Parameters(index = "0", description = "JBang script alias, filename or GAV to analyze", defaultValue = "")
    private String name;

    public static void main(String... args) throws Exception {
        int exitCode = new CommandLine(new deps()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (name.equals("")) {
            // Return failure if no JBang script file is supplied
            return 1;
        }

        // Get dependencies
        String jbang_launch_cmd = System.getenv("JBANG_LAUNCH_CMD");
        try {
            List<String> gavList = new LinkedList<>();

            if (!name.contains(":")) {
                // name is a script file or alias
                String dependencies = $(jbang_launch_cmd + " info tools --quiet --select=dependencies " + name).get();
                JsonNode deps = new ObjectMapper().readTree(dependencies);

                // Build the gavList
                deps.forEach(
                        dep -> {
                            gavList.add(dep.asText());
                        });
            } else {
                // name is a GAV
                gavList.add(name);
            }
            // Check for version updates
            String[] toolbox_args = {"versions", String.join(",", gavList)};
            eu.maveniverse.maven.toolbox.plugin.CLI.main(toolbox_args);
        } catch (dev.jbang.jash.ProcessException e) {
            // script file or alias do not contain any //DEPS
            return 2;
        }

        // Return success
        return 0;
    }

}
