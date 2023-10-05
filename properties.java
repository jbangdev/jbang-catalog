//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5
//DEPS de.vandermeer:asciitable:0.3.2
//DEPS org.jline:jline:3.16.0

import de.vandermeer.asciitable.*;
import de.vandermeer.asciithemes.a7.A7_Grids;
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

@Command(name = "properties", mixinStandardHelpOptions = true, version = "properties 0.1",
        description = "Dump java properties made with jbang")
class properties implements Callable<Integer> {

    @Parameters(description = "Pattern used to filter System properties", defaultValue = ".*")
    private List<Pattern> pattern;

    public static void main(String... args) {
        int exitCode = new CommandLine(new properties()).execute(args);
        exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...
        int[] maxkey = { 0 };

        AsciiTable at = new AsciiTable();
        at.getContext().setGrid(A7_Grids.minusBarPlusEquals());

        at.addRule();
        at.addRow("Key", "Value");
        at.addRule();
        getProperties().keySet().stream().sorted()
                .filter(x -> pattern.stream().anyMatch(y -> y.matcher((String) x).find()))
                .forEach(x -> {
                    maxkey[0] = Math.max(maxkey[0], ((String) x).length());
                    at.addRow(x, getProperty((String) x));
                });
        at.addRule();

        int colwidth = org.jline.terminal.TerminalBuilder.terminal().getWidth();
        if(colwidth==0) colwidth = 80;

        at.getRenderer().setCWC(new CWC_LongestWordMax(colwidth - maxkey[0] - 3));
        out.println(at.render(colwidth));

        return 0;
    }
}
