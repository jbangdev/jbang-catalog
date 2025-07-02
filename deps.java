/// usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.jbang:jash:0.0.3
//DEPS com.fasterxml.jackson.core:jackson-databind:2.19.1
//DEPS eu.maveniverse.maven.plugins:toolbox:0.11.2

import static dev.jbang.jash.Jash.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedList;
import java.util.List;

public class deps {
    public static void main(String... args) throws Exception {
        String toolboxJar = "https://repo.maven.apache.org/maven2/eu/maveniverse/maven/plugins/toolbox/0.11.2/toolbox-0.11.2-cli.jar ";
        if (args.length > 0) {
            String script = args[0];
            String jbang_launch_cmd = System.getenv("JBANG_LAUNCH_CMD");
            String dependencies = $(jbang_launch_cmd + " info tools --select=dependencies " + script).get();
            JsonNode deps = new ObjectMapper().readTree(dependencies);
            List<String> gavList = new LinkedList<>();
            deps.forEach(
                    dep -> {
                        gavList.add(dep.asText());
                    });
            String result = $(jbang_launch_cmd + " trust add " + toolboxJar).get();
            $(jbang_launch_cmd + " run " + toolboxJar + " versions " + String.join(",", gavList)).stream()
                    .forEach(System.out::println);
        }
    }
}
