//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 11+
//DEPS info.picocli:picocli:4.2.0
//DEPS org.jboss.resteasy:resteasy-client:4.5.6.Final
//DEPS org.jboss.resteasy:resteasy-jackson2-provider:4.5.6.Final
//DEPS de.codeshelf.consoleui:consoleui:0.0.11

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.codeshelf.consoleui.prompt.ConsolePrompt;
import de.codeshelf.consoleui.prompt.ListResult;
import de.codeshelf.consoleui.prompt.PromtResultItemIF;
import jline.console.completer.StringsCompleter;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import javax.naming.directory.SearchResult;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.lang.System.out;
import static org.fusesource.jansi.Ansi.ansi;

@Command(name = "mvnsearch", mixinStandardHelpOptions = true, version = "mvnsearch 0.1",
        description = "mvnsearch made with jbang")
public class mvnsearch implements Callable<Integer> {

    @Parameters(index = "0", description = "Query string to use when searching search.maven.org", defaultValue = "picocli")
    private String query;

    public static void main(String... args) {
        int exitCode = new CommandLine(new mvnsearch()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...
        AnsiConsole.systemInstall();
        ConsolePrompt prompt = new ConsolePrompt();

        var client = ClientBuilder.newClient();
        try(var thing = client.target("https://search.maven.org/solrsearch/select?rows=100&q=" + URLEncoder.encode(query, "UTF-8")).request().get()) {
            var result = thing.readEntity(MvnSearchResult.class);
            if(result.response.docs.isEmpty()) {
                System.err.println("no results");
            } else {
                var builder = prompt.getPromptBuilder();

                var list = builder.createListPrompt()
                        .name("gav")
                        .message("coordinates");
                final Map<String, Doc> gavmap = new HashMap<>();
                result.response.docs.forEach(gav -> {
                    gavmap.put(gav.id, gav);
                    list.newItem(gav.id).text(gav.gradle()).add();
                });

                list.addPrompt();

                Map<String, ? extends PromtResultItemIF> response = prompt.prompt(builder.build());

                String selid = ((ListResult)response.get("gav")).getSelectedId();
                Doc selected = gavmap.get(selid);

                out.println(ansi().render("@|bold gradle:|@ ") + selected.gradle());
                out.println(ansi().render("@|bold maven:|@ \n") + selected.maven());
                out.println(ansi().render("@|italic ok?|@"));

                builder = new ConsolePrompt().getPromptBuilder();
                builder.createListPrompt()
                        .name("choice")
                        .message("Action")
                        .newItem().name("copygradle").text("Copy Gradle to Clipboard").add()
                        .newItem("copymaven").text("Copy Maven to Clipboard").add()
                        .newItem("versions").text("Search older versions").add()
                        .newItem("quit").text("Quit").add()
                        .addPrompt();

                response = prompt.prompt(builder.build());

                out.println(response);
            }
        }

        return 0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MvnSearchResult {

        public Header responseHeader;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public Response response;

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        public int status;

    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        public List<Doc> docs;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Doc {
        public String id;
        public String g;
        public String a;
        public String latestVersion;
        public String p;
        public Date timestamp;
        public int versionCount;
        public List<String> text;
        public List<String> ec;

        public String gradle() {
            return g + ":" + a + ":" + latestVersion;
        }

        public String maven() {
            return "<dependency>\n" +
                    "  <groupId>" + g + "</groupId>\n" +
                    "  <artifactId>" + a + "</artifactId>\n" +
                    "  <version>" + latestVersion + "</version>\n" +
                    "</dependency>";
        }
    }
}
