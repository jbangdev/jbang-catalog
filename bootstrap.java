///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+ 
//DESCRIPTION Bootstrap a jbang script to make it self-contained.

import static java.lang.System.*;
import static java.nio.file.Files.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class bootstrap {

    public static void main(String... args) {
        if(args.length != 1) {
            err.println("Usage: jbang bootstrap.java <jbang script>");
            exit(1);
        }

        var path = Path.of(args[0]);

        if(notExists(path)) {
            err.println("File " + path + " does not exist");
            exit(1);
        }

        List<String> lines = null;

        try {
            lines = Files.readAllLines(path);
        } catch(IOException e) {
            err.println("Could not read from " + path);
            exit(1);
        }

        String header = """
            //usr/bin/env echo '
            /**** BOOTSTRAP jbang ****\'>/dev/null
            command -v jbang >/dev/null 2>&1 || curl -Ls https://sh.jbang.dev | bash -s app setup
            exec jbang "$0" "$@" ; exit $?
            \\*** IMPORTANT: Any code including imports and annotations must come after this line ***/
            """;

        if (lines.size() > 0 && lines.get(0).startsWith("///usr/bin/env jbang \"$0\" \"$@\" ; exit $?")) {
            lines.set(0, header);
        } else {
            lines.add(0, header);
        }

        try { 
            write(path, lines, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch(IOException e) {
            err.println("Could not write to " + path);
            exit(1);
        }

    }
}
