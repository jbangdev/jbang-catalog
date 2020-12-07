//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 11+
//DEPS info.picocli:picocli:4.2.0
//DEPS org.jboss.resteasy:resteasy-client:4.5.6.Final
//DEPS org.jboss.resteasy:resteasy-jackson2-provider:4.5.6.Final
//DEPS de.codeshelf.consoleui:consoleui:0.0.13

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
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.System.out;
import static org.fusesource.jansi.Ansi.ansi;

@Command(name = "gavsearch", mixinStandardHelpOptions = true, version = "gavsearch 0.1",
        description = "mvnsearch made with jbang")
public class gavsearch implements Callable<Integer> {

    @Parameters(index = "0", description = "Query string to use when searching search.maven.org")
    private String query;

    @Parameters(index = "1..n", description = "Strings that should be part of either Group, Artifact or Version")
    private List<String> filters = new ArrayList<>();

    @CommandLine.Option(names={ "--formats", "-f" }, split =",", defaultValue="maven,gradle,jbang")
    private List<String> formats;

    @CommandLine.Option(names={ "-q", "--quiet"},
                        defaultValue = "false",
                        description = "Quiet mode - no questions asked, just print in the format specified.")
    private boolean quiet;

    public static void main(String... args) {
        int exitCode = new CommandLine(new gavsearch()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...
        AnsiConsole.systemInstall();

        Map<String, Function<Doc, String>> formatMap = new LinkedHashMap<>();
        formatMap.put("gradle", Doc::gradle);
        formatMap.put("maven", Doc::maven);
        formatMap.put("jbang", Doc::jbang);


        System.out.println("Searching for `" + query + "` on search.maven.org...");
        var client = ClientBuilder.newClient();
        try(var thing = client.target("https://search.maven.org/solrsearch/select?rows=100&q=" + URLEncoder.encode(query, "UTF-8")).request().get()) {
            ConsolePrompt prompt = new ConsolePrompt();
            var result = thing.readEntity(MvnSearchResult.class);

            if(result.response.docs.isEmpty()) {
                System.err.println("no results");
            } else if (quiet) {
                    result.response.docs.stream().filter(x -> {
                        return filterMatch(x);
                    }).forEach(x -> {
                        formatMap.forEach((k,v) -> {
                            Consumer<Doc> printer = gav -> out.println(v.apply(gav));
                            printer.accept(x);
                        }
                        );
                        out.println();
                    });
            } else {
                var builder = prompt.getPromptBuilder();

                var list = builder.createListPrompt()
                        .name("gav")
                        .message("coordinates");
                final Map<String, Doc> gavmap = new HashMap<>();
                result.response.docs.stream().filter(this::filterMatch).forEach(gav -> {
                    gavmap.put(gav.id, gav);
                    list.newItem(gav.id).text(gav.gradle()).add();
                });

                if(gavmap.isEmpty()) {
                    System.err.println(result.response.docs.size() + " found on search.maven.org but noone matched the specified filters.");
                    System.exit(1);
                }
                list.addPrompt();

                Map<String, ? extends PromtResultItemIF> response = prompt.prompt(builder.build());

                String selid = ((ListResult)response.get("gav")).getSelectedId();
                Doc selected = gavmap.get(selid);

                builder = new ConsolePrompt().getPromptBuilder();
                var listprompt = builder.createListPrompt()
                        .name("choice")
                        .message("Action");

                Map<String, String> actions = new HashMap<>();

                formats.forEach(s -> {
                    var f = formatMap.get(s);
                    if(f!=null) {
                        String txt  = f.apply(selected);
                        out.println(ansi().render("@|bold " + s + ":|@\n") +
                                txt);
                        listprompt.newItem(s).text("Copy " + s + " to Clipboard").add();
                        actions.put(s, txt);
                    } else {
                        System.err.println("Unknown format: " + s);
                    }
                });

                listprompt.newItem("versions").text("Search older versions").add()
                .newItem("quit").text("Quit").add()
                .addPrompt();
                response = prompt.prompt(builder.build());

                String choice = ((ListResult)response.get("choice")).getSelectedId();
                String res = actions.get(choice);
                if(res!=null) {
                    copyClipboard(res);
                } else if ("versions".equals(choice)) {
                    try(var versions = client.target("https://search.maven.org/solrsearch/select?rows=100&q=g:"
                            + selected.g + "+AND+a:" + selected.a + "&core=gav").request().get()) {

                        var vs = versions.readEntity(MvnSearchResult.class);
                        if(vs.response.docs.isEmpty()) {
                            System.err.println("no results");
                        } else {
                            vs.response.docs.forEach(x -> {
                                System.out.println(x.v);
                            });
                        }
                    }

                }


            }
        }

        return 0;
    }

    public boolean filterMatch(gavsearch.Doc x) {
        if(filters.isEmpty()) return true;

        for (String string : filters) {
            if (x.g.contains(string)) return true;
            if (x.a!=null && x.a.contains(string)) return true;
            if (x.v!=null && x.v.contains(string)) return true;
        }
        
        return false;
    }

    void copyClipboard(String str) {
        StringSelection stringSelection = new StringSelection(str);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
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
        public String v;
        public String latestVersion;
        public String p;
        public Date timestamp;
        public int versionCount;
        public List<String> text;
        public List<String> ec;

        public String gradle() {
            return g + ":" + a + ":" + latestVersion;
        }

        public String jbang() {
            return "//DEPS " + gradle();
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
