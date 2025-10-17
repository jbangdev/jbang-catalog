///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.6.3
//DEPS org.eclipse.jgit:org.eclipse.jgit:6.5.0.202303070854-r
//DEPS org.slf4j:slf4j-nop:2.0.9

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Command(name = "trylink", mixinStandardHelpOptions = true, version = "trylink 0.1",
        description = "Generate jbang.dev/try links with optional code, repository, and redirect parameters")
class trylink implements Callable<Integer> {

    @Parameters(index = "0", description = "File path or repository URL", arity = "0..1")
    private Optional<String> input;

    @Option(names = "--repo", description = "Override repository URL")
    private String repo;

    @Option(names = "--branch", description = "Branch name (defaults to auto-detect or 'main')")
    private String branch;

    @Option(names = "--title", description = "Friendly title for the repository")
    private String title;

    @Option(names = "--filepath", description = "Path within repository to open in Jupyter")
    private String filepath;

    @Option(names = "--code", description = "Code to pre-fill in editor (reads from file if path given)")
    private String code;

    @Option(names = "--redirect", description = "Auto-redirect seconds (0=immediate, omit for no redirect)")
    private Integer redirect;

    @Option(names = "--base-url", description = "Base URL for try page", defaultValue = "https://jbang.dev")
    private String baseUrl;

    @Option(names = "--copy", description = "Copy to clipboard (requires pbcopy/xclip/clip)")
    private boolean copy;

    private static class GitInfo {
        String repoUrl;
        String relativePath;
        String branch;
        
        GitInfo(String repoUrl, String relativePath, String branch) {
            this.repoUrl = repoUrl;
            this.relativePath = relativePath;
            this.branch = branch;
        }
    }
    
    private GitInfo detectGitInfoFromPath(String filePath) throws Exception {
        Path path = Paths.get(filePath).toAbsolutePath();
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        
        // Find the .git directory by walking up the directory tree
        Path currentPath = path;
        while (currentPath != null) {
            File gitDir = currentPath.resolve(".git").toFile();
            if (gitDir.exists()) {
                Repository repository = builder.setGitDir(gitDir).readEnvironment().findGitDir().build();
                String remoteUrl = repository.getConfig().getString("remote", "origin", "url");
                String detectedBranch = repository.getBranch();
                repository.close();
                
                if (remoteUrl != null) {
                    // Convert SSH URL to HTTPS URL if needed
                    if (remoteUrl.startsWith("git@")) {
                        remoteUrl = remoteUrl.replace("git@", "https://")
                                           .replace(":", "/")
                                           .replace(".git", "");
                    } else if (remoteUrl.endsWith(".git")) {
                        remoteUrl = remoteUrl.substring(0, remoteUrl.length() - 4);
                    }
                    
                    // Calculate relative path from git repository root
                    Path gitRoot = currentPath;
                    Path relativePath = gitRoot.relativize(path);
                    
                    return new GitInfo(remoteUrl, relativePath.toString(), detectedBranch != null ? detectedBranch : "main");
                }
            }
            currentPath = currentPath.getParent();
        }
        throw new RuntimeException("No git repository found for path: " + filePath);
    }
    
    private boolean isGitHubUrl(String url) {
        return url.contains("github.com") || url.contains("gist.github.com");
    }
    
    private String extractRepoFromUrl(String url) {
        // Handle GitHub URLs like https://github.com/user/repo or https://github.com/user/repo/blob/branch/path/file
        if (url.contains("github.com/") && !url.contains("gist.github.com")) {
            String[] parts = url.split("github.com/");
            if (parts.length > 1) {
                String remainder = parts[1];
                // Extract user/repo
                String[] pathParts = remainder.split("/");
                if (pathParts.length >= 2) {
                    String userRepo = pathParts[0] + "/" + pathParts[1];
                    return "https://github.com/" + userRepo;
                }
            }
        }
        // Handle Gist URLs
        if (url.contains("gist.github.com")) {
            // Return the gist URL as-is
            return url.replaceAll("\\#.*", "").replaceAll("\\?.*", "");
        }
        return url;
    }
    
    private String extractBranchFromUrl(String url) {
        // Try to extract branch from URL like https://github.com/user/repo/blob/branch/...
        if (url.contains("/blob/") || url.contains("/tree/")) {
            String[] parts = url.split("/(?:blob|tree)/");
            if (parts.length > 1) {
                String afterBlob = parts[1];
                String[] pathParts = afterBlob.split("/");
                if (pathParts.length > 0) {
                    return pathParts[0];
                }
            }
        }
        return null;
    }
    
    private String extractFilepathFromUrl(String url) {
        // Extract filepath from URL like https://github.com/user/repo/blob/branch/path/to/file
        if (url.contains("/blob/") || url.contains("/tree/")) {
            String[] parts = url.split("/(?:blob|tree)/");
            if (parts.length > 1) {
                String afterBlob = parts[1];
                String[] pathParts = afterBlob.split("/", 2);
                if (pathParts.length > 1) {
                    return pathParts[1];
                }
            }
        }
        return null;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new trylink()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        String actualRepo = repo;
        String actualBranch = branch;
        String actualFilepath = filepath;
        String actualCode = code;
        
        // Process input parameter
        if (input.isPresent()) {
            String inputValue = input.get();
            
            if (isGitHubUrl(inputValue)) {
                // It's a URL
                if (actualRepo == null) {
                    actualRepo = extractRepoFromUrl(inputValue);
                }
                if (actualBranch == null) {
                    actualBranch = extractBranchFromUrl(inputValue);
                }
                if (actualFilepath == null) {
                    actualFilepath = extractFilepathFromUrl(inputValue);
                }
            } else {
                // It's a file path
                Path path = Paths.get(inputValue);
                
                if (Files.exists(path) && Files.isRegularFile(path)) {
                    
                    
                    // Try to detect git info if repo not specified
                    if (actualRepo == null) {
                        try {
                            GitInfo gitInfo = detectGitInfoFromPath(inputValue);
                            actualRepo = gitInfo.repoUrl;
                            actualBranch = gitInfo.branch;
                            if (actualFilepath == null) {
                                actualFilepath = gitInfo.relativePath;
                            }
                        } catch (Exception e) {
                            // Not in a git repo, that's OK - we can still use code
                            System.err.println("Note: Not in a git repository, code will be embedded in URL");
                        }
                    }
                } else {
                    System.err.println("Warning: File not found: " + inputValue);
                }
            }
        }
        
        // Build the try URL
        StringBuilder tryUrl = new StringBuilder(baseUrl);
        if (!baseUrl.endsWith("/")) {
            tryUrl.append("/");
        }
        tryUrl.append("try/");
        
        boolean hasParams = false;
        
        if (actualCode != null) {
            tryUrl.append("?code=").append(URLEncoder.encode(actualCode, StandardCharsets.UTF_8));
            hasParams = true;
        }
        
        if (actualRepo != null) {
            tryUrl.append(hasParams ? "&" : "?").append("repo=").append(URLEncoder.encode(actualRepo, StandardCharsets.UTF_8));
            hasParams = true;
        }
        
        if (title != null) {
            tryUrl.append(hasParams ? "&" : "?").append("title=").append(URLEncoder.encode(title, StandardCharsets.UTF_8));
            hasParams = true;
        }
        
        if (actualBranch != null) {
            tryUrl.append(hasParams ? "&" : "?").append("branch=").append(URLEncoder.encode(actualBranch, StandardCharsets.UTF_8));
            hasParams = true;
        }
        
        if (actualFilepath != null) {
            tryUrl.append(hasParams ? "&" : "?").append("filepath=").append(URLEncoder.encode(actualFilepath, StandardCharsets.UTF_8));
            hasParams = true;
        }
        
        if (redirect == null && actualCode == null) {
            redirect = 3; // if no code given then assume redirect to Jupyter after 3 seconds
        }

        if (redirect != null) {
            tryUrl.append(hasParams ? "&" : "?").append("redirect=").append(redirect);
            hasParams = true;
        }
        
        String finalUrl = tryUrl.toString();
        
        // Print information
        if (actualRepo != null) {
            System.out.println("Repository: " + actualRepo);
        }
        if (actualBranch != null) {
            System.out.println("Branch: " + actualBranch);
        }
        if (actualFilepath != null) {
            System.out.println("File path: " + actualFilepath);
        }
        if (actualCode != null) {
            System.out.println("Code: " + actualCode.length() + " characters");
        }
        if (title != null) {
            System.out.println("Title: " + title);
        }
        if (redirect != null) {
            System.out.println("Redirect: " + redirect + " seconds");
        }
        System.out.println();
        System.out.println(finalUrl);
        
        // Copy to clipboard if requested
        if (copy) {
            try {
                copyToClipboard(finalUrl);
                System.out.println("\n✅ Copied to clipboard!");
            } catch (Exception e) {
                System.err.println("\n❌ Failed to copy to clipboard: " + e.getMessage());
            }
        }
        
        return 0;
    }
    
    private void copyToClipboard(String text) throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;
        
        if (os.contains("mac")) {
            pb = new ProcessBuilder("pbcopy");
        } else if (os.contains("linux")) {
            pb = new ProcessBuilder("xclip", "-selection", "clipboard");
        } else if (os.contains("windows")) {
            pb = new ProcessBuilder("clip");
        } else {
            throw new UnsupportedOperationException("Clipboard copy not supported on this OS");
        }
        
        Process process = pb.start();
        process.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        process.waitFor();
    }
}

