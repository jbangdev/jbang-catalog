///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.7
//DEPS com.fasterxml.jackson.core:jackson-databind:2.15.3

import static java.lang.System.out;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "dalle", showDefaultValues=true, mixinStandardHelpOptions = true, version = "dalle 0.1",
        description = "Generate images using DALL-E with jbang. Requires a DALL-E account (https://labs.openai.com/).")
class dalle implements Callable<Integer> {

    ObjectMapper mapper = new ObjectMapper();

    static class SIZES extends ArrayList<String> {{ add("256x256"); add("512x512"); add("1024x1024");  }};

    @Option(names={"-s", "--size"}, description = "Size of generated image", defaultValue = "256x256", completionCandidates = SIZES.class )
    String size;

    @Option(names={"-n"}, description = "The number of images to generate. Must be between 1 and 10", defaultValue = "1")
    int n;

    @Option(names={"-p", "--prompt"}, description = "A text description of the desired image(s). The maximum length is 1000 characters.", defaultValue = "A Sunflower sunset")
    String prompt;

    @Option(names={"-t", "--token"}, description = "API key from https://beta.openai.com/account/api-keys. Will honor OPENAI_API_KEY environment variable.",defaultValue="${OPENAI_API_KEY}", required=true)
    String token;
    public static void main(String... args) {
        int exitCode = new CommandLine(new dalle()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { 
        HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .connectTimeout(Duration.ofSeconds(10))
        .build();
        
        Map<Object, Object> data = new HashMap<>();
        data.put("prompt", prompt);
        data.put("n", n);
        data.put("size", size);

        HttpRequest request = HttpRequest.newBuilder()
                .POST(ofFormData(data))
                .uri(URI.create("https://api.openai.com/v1/images/generations"))
                .setHeader("User-Agent", "JBang") // add request header
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .build();

        out.printf("Asking Dall-e for %s x %s image(s) of: '%s'\n", n, size, prompt);
        out.println("This may take a while...\n");

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if(response.statusCode() == 200) {
            var reply = mapper.readValue(response.body(), JsonNode.class);
            int i=1;
            for(JsonNode node : reply.get("data")) {
                out.printf("Image %s: %s\n\n", i++, node.get("url").asText());
            }
        } else {
            out.printf("Error(%s):\n%s\n", response.statusCode(), response.body());
        }

        return response.statusCode()==200?0:response.statusCode();
    }

    HttpRequest.BodyPublisher ofFormData(Map<Object, Object> data) throws JsonProcessingException {
        return HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(data));
    }
}
