import java.util.Map;

public class ListEnvironmentVariables {
    public static void main(String[] args) {
        // Get all environment variables as a Map
        Map<String, String> env = System.getenv();

        // Iterate over the map and print each key-value pair
        System.out.println("--- Environment Variables ---");
        for (Map.Entry<String, String> entry : env.entrySet()) {
            System.out.println(entry.getKey() + " = " + entry.getValue());
        }
        System.out.println("---------------------------");

        // You can also get a specific environment variable
        String path = System.getenv("PATH");
        if (path != null) {
            System.out.println("\nPATH environment variable: " + path);
        } else {
            System.out.println("\nPATH environment variable not found.");
        }
    }
}