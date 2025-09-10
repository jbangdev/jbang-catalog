///usr/bin/env jbang "$0" "$@" ; exit $?
// Update the Quarkus version to what you want here or run jbang with
// `-Dquarkus.version=<version>` to override it.
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.5.0}@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS io.quarkus:quarkus-jackson
//DEPS io.quarkus:quarkus-qute

//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=WARN
//Q:CONFIG quarkus.qute.property-not-found-strategy=throw-exception

//FILES templates/index.txt=templates/index.txt.qute

import java.io.File;
import java.io.IOException;
import java.util.Map;

import jakarta.inject.Inject;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

import io.quarkus.qute.Template;
import picocli.CommandLine;

@CommandLine.Command
public class catalog2readme implements Runnable {

    @CommandLine.Parameters(index = "0", description = "The file to print", defaultValue = "jbang-catalog.json")
    File file;

    @CommandLine.Option(names = "-l", required = false)
    String location;

    @Inject
    CommandLine.IFactory factory;

    final ObjectMapper mapper;

    @Inject
    Template index;

    public catalog2readme(ObjectMapper mapper) {
        this.mapper = mapper;
        mapper// .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,true)
                .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE)
                .setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
    }

    @Override
    public void run() {
        try {
            Catalog catalog;
            catalog = mapper.readValue(file, Catalog.class);
            if (location != null) {
                if (location.endsWith("/jbang-catalog")) {
                    location = location.substring(0, location.length() - "/jbang-catalog".length());
                }
                if (!location.startsWith("@")) {
                    location = "@" + location;
                }
            }
            System.out.println(index.data("catalog", catalog).data("location", location).render());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static public class Catalog {
        public Map<String, Alias> aliases;
        public Map<String, CTemplate> templates;
        public String baseRef;
        public String description;
    }

    static public class Alias {
        public String scriptRef;
        public String description;
    }

    static public class CTemplate {
        public String description;
    }
}
