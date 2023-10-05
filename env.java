//usr/bin/env echo '
/**** BOOTSTRAP jbang ****'>/dev/null
command -v jbang >/dev/null 2>&1 || curl -Ls https://sh.jbang.dev | bash -s app setup
exec jbang "$0" "$@" ; exit $?
\*** IMPORTANT: Any code including imports and annotations must come after this line ***/

//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.2.0
//DEPS de.vandermeer:asciitable:0.3.2
//DEPS org.jline:jline:3.23.0

import de.vandermeer.asciitable.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.System.*;
import static java.lang.System.getProperties;
import static java.lang.System.out;

@Command(name = "properties", mixinStandardHelpOptions = true, version = "env 0.1",
        description = "Dump java properties made with jbang")
class env implements Callable<Integer> {

    @Parameters(description = "Pattern used to filter env", defaultValue = ".*")
    private List<Pattern> pattern;

    public static void main(String... args) {
        int exitCode = new CommandLine(new env()).execute(args);
        exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...
        int[] maxkey = { 0 };

        AsciiTable at = new AsciiTable();

        at.addRule();
        at.addRow("Key", "Value");
        at.addRule();
        getenv().keySet().stream().sorted()
                .filter(x -> pattern.stream().anyMatch(y -> y.matcher((String) x).find()))
                .forEach(x -> {
                    maxkey[0] = Math.max(maxkey[0], ((String) x).length());
                    at.addRow(x, getenv((String) x));
                });
        at.addRule();

        int colwidth = org.jline.terminal.TerminalBuilder.terminal().getWidth();
        if(colwidth==0) colwidth = 80;

        at.getRenderer().setCWC(new CWC_LongestWordMax(colwidth - maxkey[0] - 3));
        out.println(at.render(colwidth));

        return 0;
    }
}
