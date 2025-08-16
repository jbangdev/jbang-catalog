//usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS com.fasterxml.jackson.core:jackson-databind:2.15.2
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2
//DEPS info.picocli:picocli:4.7.5

//DESCRIPTION # mf.java - JAR Manifest Reader CLI
//DESCRIPTION 
//DESCRIPTION `mf` is a command-line tool for extracting and displaying JAR manifest information.
//DESCRIPTION 
//DESCRIPTION ## Usage
//DESCRIPTION 
//DESCRIPTION ```sh
//DESCRIPTION mf <jar-file> [regex-filter] [--json] [--yaml] [--structured|-s]
//DESCRIPTION ```
//DESCRIPTION 
//DESCRIPTION - **`<jar-file>`**: Path to the JAR file to analyze (required). Use '-' for stdin.
//DESCRIPTION - **`[regex-filter]`**: Optional case-insensitive regex pattern to filter manifest keys.
//DESCRIPTION - **`--json`**: Output manifest attributes as JSON.
//DESCRIPTION - **`--yaml`**: Output manifest attributes as YAML.
//DESCRIPTION - **`--structured`, `-s`**: Parse known attributes (e.g., Import-Package, Export-Package) into structured data (lists/maps).
//DESCRIPTION 
//DESCRIPTION ## Features
//DESCRIPTION 
//DESCRIPTION - Reads the `META-INF/MANIFEST.MF` from the specified JAR file.
//DESCRIPTION - By default, prints manifest attributes as manifest.mf.
//DESCRIPTION - Supports machine-readable output with `--json` or `--yaml`.
//DESCRIPTION - Structured parsing of known attributes with `--structured`.
//DESCRIPTION - Case-insensitive regex filtering of manifest keys.
//DESCRIPTION - Returns a nonzero exit code if the manifest is missing or the JAR cannot be read.
//DESCRIPTION 
//DESCRIPTION ## Examples
//DESCRIPTION 
//DESCRIPTION ```sh
//DESCRIPTION mf mylib.jar
//DESCRIPTION mf mylib.jar --json
//DESCRIPTION mf mylib.jar ".*package.*" --yaml --structured
//DESCRIPTION mf `jbang info jar org.junit.jupiter:junit-jupiter:5.10.0` --yaml --structured
//DESCRIPTION mf mylib.jar "^bundle.*" --json
//DESCRIPTION ```


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "mf", mixinStandardHelpOptions = true, version = "1.0",
         description = "Extract and display JAR manifest information")
public class mf implements Callable<Integer> {
    
    @Parameters(index = "0", arity = "1", description = "JAR file to analyze (use '-' for stdin)")
    private String jarFile;
    
    @Parameters(index = "1", arity = "0..1", description = "Regex pattern to filter keys (case-insensitive)")
    private String keyFilter;
    
    @Option(names = {"--json"}, description = "Output in JSON format")
    private boolean jsonOutput;
    
    @Option(names = {"--yaml"}, description = "Output in YAML format")
    private boolean yamlOutput;
    
    @Option(names = {"--structured", "-s"}, description = "Parse known attributes into structured format")
    private boolean structuredOutput;
    
    @Override
    public Integer call() {
        try {
            JarFile jar;
            Path tempFile = null;
            
            if ("-".equals(jarFile)) {
                // Read from standard input and create temporary file
                tempFile = Files.createTempFile("mf-", ".jar");
                try (InputStream in = System.in;
                     FileOutputStream out = new FileOutputStream(tempFile.toFile())) {
                    // Use a buffer to copy data instead of transferTo
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.flush();
                }
                jar = new JarFile(tempFile.toFile());
            } else {
                // Read from file
                jar = new JarFile(new File(jarFile));
            }
            
            try (jar) {
                Manifest manifest = jar.getManifest();
                if (manifest == null) {
                    System.err.println("No manifest found in " + jarFile);
                    return ExitCode.USAGE;
                }
                
                Map<String, Object> attributes = new LinkedHashMap<>();
                Pattern filterPattern = keyFilter != null ? Pattern.compile(keyFilter, Pattern.CASE_INSENSITIVE) : null;
                
                manifest.getMainAttributes().forEach((k, v) -> {
                    String key = k.toString();
                    String value = v.toString();
                    
                    // Apply filter if specified
                    if (filterPattern != null && !filterPattern.matcher(key).find()) {
                        return; // Skip this key
                    }
                    
                    if (structuredOutput) {
                        attributes.put(key, parseStructuredAttribute(key, value));
                    } else {
                        attributes.put(key, value);
                    }
                });
                
                if (jsonOutput) {
                    System.out.println(outputJson(attributes));
                } else if (yamlOutput) {
                    System.out.println(outputYaml(attributes));
                } else {
                    System.out.println(outputText(attributes));
                }
            } finally {
                // Clean up temporary file if created
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return ExitCode.SOFTWARE;
        }
        return ExitCode.OK;
    }
    
    private static Object parseStructuredAttribute(String key, String value) {
        switch (key.toLowerCase()) {
            case "export-package":
            case "import-package":
                return parsePackageList(value);
            case "include-resource":
                return parseResourceList(value);
            case "require-capability":
                return parseCapabilityList(value);
            case "bundle-description":
                return value.replace("\\\n  ", " ").replace("\\\n ", " ").trim();
            case "add-opens":
                return parseAddOpens(value);
            default:
                return value;
        }
    }
    
    private static Map<String, Object> parsePackageList(String value) {
        Map<String, Object> packages = new LinkedHashMap<>();
        
        // First, handle line continuations and remove outer quotes
        String cleanedValue = value.trim();
        if (cleanedValue.startsWith("\"") && cleanedValue.endsWith("\"")) {
            cleanedValue = cleanedValue.substring(1, cleanedValue.length() - 1);
        }
        // Remove line continuations
        cleanedValue = cleanedValue.replace("\\\n", "").replace("\\\r\n", "").replace("\\\r", "");
        
        // Split by comma, but be careful not to split inside quoted strings
        List<String> packageEntries = new ArrayList<>();
        StringBuilder currentEntry = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;
        
        for (int i = 0; i < cleanedValue.length(); i++) {
            char c = cleanedValue.charAt(i);
            
            if (escaped) {
                currentEntry.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
                currentEntry.append(c);
            } else if (c == '"') {
                inQuotes = !inQuotes;
                currentEntry.append(c);
            } else if (c == ',' && !inQuotes) {
                // End of package entry
                String entry = currentEntry.toString().trim();
                if (!entry.isEmpty()) {
                    packageEntries.add(entry);
                }
                currentEntry = new StringBuilder();
            } else {
                currentEntry.append(c);
            }
        }
        
        // Add the last entry
        String lastEntry = currentEntry.toString().trim();
        if (!lastEntry.isEmpty()) {
            packageEntries.add(lastEntry);
        }
        
        // Now parse each package entry
        for (String entry : packageEntries) {
            if (entry.isEmpty()) continue;
            
            // Find the package name (everything before the first semicolon)
            int firstSemicolon = entry.indexOf(';');
            String packageName;
            String attributes = null;
            
            if (firstSemicolon > 0) {
                packageName = entry.substring(0, firstSemicolon).trim();
                attributes = entry.substring(firstSemicolon + 1).trim();
            } else {
                packageName = entry.trim();
            }
            
            if (attributes != null && !attributes.isEmpty()) {
                Map<String, Object> packageInfo = new LinkedHashMap<>();
                
                // Parse attributes using semicolon as separator
                List<String> attrList = new ArrayList<>();
                StringBuilder currentAttr = new StringBuilder();
                inQuotes = false;
                escaped = false;
                
                for (int i = 0; i < attributes.length(); i++) {
                    char c = attributes.charAt(i);
                    
                    if (escaped) {
                        currentAttr.append(c);
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                        currentAttr.append(c);
                    } else if (c == '"') {
                        inQuotes = !inQuotes;
                        currentAttr.append(c);
                    } else if (c == ';' && !inQuotes) {
                        // End of attribute
                        String attr = currentAttr.toString().trim();
                        if (!attr.isEmpty()) {
                            attrList.add(attr);
                        }
                        currentAttr = new StringBuilder();
                    } else {
                        currentAttr.append(c);
                    }
                }
                
                // Add the last attribute
                String lastAttr = currentAttr.toString().trim();
                if (!lastAttr.isEmpty()) {
                    attrList.add(lastAttr);
                }
                
                // Parse each attribute
                for (String attr : attrList) {
                    if (attr.isEmpty()) continue;
                    
                    // Handle key=value and key:=value formats
                    int equalsIndex = attr.indexOf('=');
                    int colonEqualsIndex = attr.indexOf(":=");
                    
                    if (colonEqualsIndex > 0) {
                        // key:=value format
                        String key = attr.substring(0, colonEqualsIndex).trim();
                        String val = attr.substring(colonEqualsIndex + 2).trim();
                        
                        // Remove quotes if present and unescape
                        if (val.startsWith("\"") && val.endsWith("\"")) {
                            val = val.substring(1, val.length() - 1);
                            // Unescape any escaped quotes
                            val = val.replace("\\\"", "\"");
                        }
                        
                        packageInfo.put(key, parseSpecialAttribute(key, val));
                    } else if (equalsIndex > 0) {
                        // key=value format
                        String key = attr.substring(0, equalsIndex).trim();
                        String val = attr.substring(equalsIndex + 1).trim();
                        
                        // Remove quotes if present and unescape
                        if (val.startsWith("\"") && val.endsWith("\"")) {
                            val = val.substring(1, val.length() - 1);
                            // Unescape any escaped quotes
                            val = val.replace("\\\"", "\"");
                        }
                        
                        packageInfo.put(key, parseSpecialAttribute(key, val));
                    }
                }
                
                if (!packageInfo.isEmpty()) {
                    packages.put(packageName, packageInfo);
                } else {
                    packages.put(packageName, true);
                }
            } else {
                packages.put(packageName, true);
            }
        }
        
        return packages;
    }
    
    private static Map<String, String> parseResourceList(String value) {
        Map<String, String> resources = new LinkedHashMap<>();
        String[] resourceEntries = value.split(",");
        
        for (String entry : resourceEntries) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            
            int equalsIndex = entry.indexOf('=');
            if (equalsIndex > 0) {
                String key = entry.substring(0, equalsIndex).trim();
                String resourcePath = entry.substring(equalsIndex + 1).trim();
                resources.put(key, resourcePath);
            } else {
                resources.put(entry, "");
            }
        }
        
        return resources;
    }
    
    private static List<String> parseCapabilityList(String value) {
        List<String> capabilities = new ArrayList<>();
        // Split by comma, but be careful with nested parentheses
        String[] parts = value.split(",(?![^(]*\\))");
        
        for (String part : parts) {
            part = part.trim();
            if (!part.isEmpty()) {
                capabilities.add(part);
            }
        }
        
        return capabilities;
    }
    
    private static List<String> parseAddOpens(String value) {
        List<String> opens = new ArrayList<>();
        // Add-Opens typically uses space as separator
        String[] parts = value.split("\\s+");
        
        for (String part : parts) {
            part = part.trim();
            if (!part.isEmpty()) {
                opens.add(part);
            }
        }
        
        return opens;
    }
    
    private static Object parseSpecialAttribute(String key, String value) {
        // Attributes that should be converted from comma-separated strings to lists
        Set<String> listAttributes = Set.of("x-friends", "uses", "include", "exclude", "mandatory");
        
        if (listAttributes.contains(key)) {
            return parseCommaSeparatedList(value);
        }
        
        return value;
    }
    
    private static List<String> parseCommaSeparatedList(String value) {
        List<String> items = new ArrayList<>();
        String[] array = value.split(",");
        for (String item : array) {
            item = item.trim();
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }
    

    
    private static String outputText(Map<String, Object> attributes) {
        StringBuilder sb = new StringBuilder();
        attributes.forEach((k, v) -> {
            if (v instanceof Map || v instanceof List) {
                sb.append(k).append(":\n");
                appendStructuredValue(sb, v, "  ");
            } else {
                sb.append(k).append(": ").append(v).append("\n");
            }
        });
        return sb.toString();
    }
    
    private static void appendStructuredValue(StringBuilder sb, Object value, String indent) {
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            map.forEach((k, v) -> {
                if (v instanceof Map || v instanceof List) {
                    sb.append(indent).append(k).append(":\n");
                    appendStructuredValue(sb, v, indent + "  ");
                } else {
                    sb.append(indent).append(k).append(": ").append(v).append("\n");
                }
            });
        } else if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            list.forEach(item -> sb.append(indent).append("- ").append(item).append("\n"));
        }
    }
    
    private static String outputJson(Map<String, Object> attributes) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(attributes);
    }
    
    private static String outputYaml(Map<String, Object> attributes) throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.writeValueAsString(attributes);
    }
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new mf()).execute(args);
        System.exit(exitCode);
    }
}
