//usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS com.fasterxml.jackson.core:jackson-databind:2.15.2
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2
//DEPS info.picocli:picocli:4.7.5

import java.util.jar.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "mf", mixinStandardHelpOptions = true, version = "1.0",
         description = "Extract and display JAR manifest information")
public class mf implements Runnable {
    
    @Parameters(index = "0", arity = "1", description = "JAR file to analyze")
    private File jarFile;
    
    @Option(names = {"--json"}, description = "Output in JSON format")
    private boolean jsonOutput;
    
    @Option(names = {"--yaml"}, description = "Output in YAML format")
    private boolean yamlOutput;
    
    @Option(names = {"--structured"}, description = "Parse known attributes into structured format")
    private boolean structuredOutput;
    
    @Override
    public void run() {
        try {
            try (JarFile jar = new JarFile(jarFile)) {
                Manifest manifest = jar.getManifest();
                if (manifest == null) {
                    System.err.println("No manifest found in " + jarFile);
                    System.exit(1);
                }
                
                Map<String, Object> attributes = new LinkedHashMap<>();
                manifest.getMainAttributes().forEach((k, v) -> {
                    String key = k.toString();
                    String value = v.toString();
                    
                    if (structuredOutput) {
                        attributes.put(key, parseStructuredAttribute(key, value));
                    } else {
                        attributes.put(key, value);
                    }
                });
                
                if (jsonOutput) {
                    outputJson(attributes);
                } else if (yamlOutput) {
                    outputYaml(attributes);
                } else {
                    outputText(attributes);
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static Object parseStructuredAttribute(String key, String value) {
        switch (key) {
            case "Export-Package":
            case "Import-Package":
                return parsePackageList(value);
            case "Include-Resource":
                return parseResourceList(value);
            case "Require-Capability":
                return parseCapabilityList(value);
            case "Bundle-Description":
                return value.replace("\\\n  ", " ").replace("\\\n ", " ").trim();
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
                Map<String, String> packageInfo = new LinkedHashMap<>();
                
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
                        
                        packageInfo.put(key, val);
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
                        
                        packageInfo.put(key, val);
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
    

    
    private static void outputText(Map<String, Object> attributes) {
        attributes.forEach((k, v) -> {
            if (v instanceof Map || v instanceof List) {
                System.out.println(k + ":");
                printStructuredValue(v, "  ");
            } else {
                System.out.println(k + ": " + v);
            }
        });
    }
    
    private static void printStructuredValue(Object value, String indent) {
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            map.forEach((k, v) -> {
                if (v instanceof Map || v instanceof List) {
                    System.out.println(indent + k + ":");
                    printStructuredValue(v, indent + "  ");
                } else {
                    System.out.println(indent + k + ": " + v);
                }
            });
        } else if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            list.forEach(item -> System.out.println(indent + "- " + item));
        }
    }
    
    private static void outputJson(Map<String, Object> attributes) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(attributes);
        System.out.println(json);
    }
    
    private static void outputYaml(Map<String, Object> attributes) throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        String yaml = mapper.writeValueAsString(attributes);
        System.out.println(yaml);
    }
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new mf()).execute(args);
        System.exit(exitCode);
    }
}
