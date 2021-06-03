
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class httpd {

    public static void main(String[] args) throws IOException {
        int port = 8080;
        String location = System.getProperty("user.dir");
        HttpServer server = HttpServer.create(new InetSocketAddress(8080),0);
        StaticFileHandler.create(server, "/", System.getProperty("user.dir"), "index.html");
        server.start();

        System.out.println("Serving HTTP on :port " + port + " (http://[::]:" + port + "/) from " + location + " ..." );
    }
}
// Based on https://stackoverflow.com/a/42404471/71208
class StaticFileHandler implements HttpHandler {
    private static final Map<String,String> MIME_MAP = new HashMap<>();
    static {
        MIME_MAP.put("appcache", "text/cache-manifest");
        MIME_MAP.put("css", "text/css");
        MIME_MAP.put("gif", "image/gif");
        MIME_MAP.put("html", "text/html");
        MIME_MAP.put("js", "application/javascript");
        MIME_MAP.put("json", "application/json");
        MIME_MAP.put("jpg", "image/jpeg");
        MIME_MAP.put("jpeg", "image/jpeg");
        MIME_MAP.put("mp4", "video/mp4");
        MIME_MAP.put("pdf", "application/pdf");
        MIME_MAP.put("png", "image/png");
        MIME_MAP.put("svg", "image/svg+xml");
        MIME_MAP.put("xlsm", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        MIME_MAP.put("xml", "application/xml");
        MIME_MAP.put("zip", "application/zip");
        MIME_MAP.put("md", "text/plain");
        MIME_MAP.put("txt", "text/plain");
        MIME_MAP.put("php", "text/plain");
        MIME_MAP.put("java", "text/plain");
        MIME_MAP.put("jsh", "text/plain");
        MIME_MAP.put("adoc", "text/plain");
        MIME_MAP.put("gradle", "text/plain");
        MIME_MAP.put("", "text/html");

    };

    private String filesystemRoot;
    private String urlPrefix;
    private String directoryIndex;

    /**
     * @param urlPrefix The prefix of all URLs.
     *                   This is the first argument to createContext. Must start and end in a slash.
     * @param filesystemRoot The root directory in the filesystem.
     *                       Only files under this directory will be served to the client.
     *                       For instance "./staticfiles".
     * @param directoryIndex File to show when a directory is requested, e.g. "index.html".
     */
    public StaticFileHandler(String urlPrefix, String filesystemRoot, String directoryIndex) {
        if (!urlPrefix.startsWith("/")) {
            throw new RuntimeException("pathPrefix does not start with a slash");
        }
        if (!urlPrefix.endsWith("/")) {
            throw new RuntimeException("pathPrefix does not end with a slash");
        }
        this.urlPrefix = urlPrefix;

        assert filesystemRoot.endsWith("/");
        try {
            this.filesystemRoot = new File(filesystemRoot).getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.directoryIndex = directoryIndex;
    }

    /**
     * Create and register a new static file handler.
     * @param hs The HTTP server where the file handler will be registered.
     * @param path The path in the URL prefixed to all requests, such as "/static/"
     * @param filesystemRoot The filesystem location.
     *                       For instance "/var/www/mystaticfiles/".
     *                       A request to "/static/x/y.html" will be served from the filesystem file "/var/www/mystaticfiles/x/y.html"
     * @param directoryIndex File to show when a directory is requested, e.g. "index.html".
     */
    public static void create(HttpServer hs, String path, String filesystemRoot, String directoryIndex) {
        StaticFileHandler sfh = new StaticFileHandler(path, filesystemRoot, directoryIndex);
        hs.createContext(path, sfh);
    }

    public void handle(HttpExchange he) throws IOException {
        System.out.println(he.getRequestMethod() + " " + he.getRequestURI());
        String method = he.getRequestMethod(); 
        if (! ("HEAD".equals(method) || "GET".equals(method))) {
            sendError(he, 501, "Unsupported HTTP method");
            return;
        }

        String wholeUrlPath = he.getRequestURI().getPath();

        if (! wholeUrlPath.startsWith(urlPrefix)) {
            throw new RuntimeException("Path is not in prefix - incorrect routing?");
        }
        String urlPath = wholeUrlPath.substring(urlPrefix.length());

        File f = new File(filesystemRoot, urlPath);
        File canonicalFile;
        try {
            canonicalFile = f.getCanonicalFile();
        } catch (IOException e) {
            // This may be more benign (i.e. not an attack, just a 403),
            // but we don't want the attacker to be able to discern the difference.
            reportPathTraversal(he);
            return;
        }

        String canonicalPath = canonicalFile.getPath();
        if (! canonicalPath.startsWith(filesystemRoot)) {
            reportPathTraversal(he);
            return;
        }

        InputStream fis;
        long length = 0;
        try {
            if(canonicalFile.isDirectory()) {
                StringBuffer buf = new StringBuffer();
                buf.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">\n" +
                        "<html>\n" +
                        "<head>\n" +
                        "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n" +
                        "<title>Directory listing for /</title>\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "<h1>Directory listing for /</h1>\n" +
                        "<hr>\n" +
                        "<ul>");

                Files.list(canonicalFile.toPath())
                        .forEach(p -> buf.append("<li><a href=\"" + canonicalFile.toPath().relativize(p) + "\">" + p.getFileName() + "</a></li>\n"));

                buf.append("</ul>\n" +
                        "<hr>\n" +
                        "</body>\n" +
                        "</html>");
                fis = new ByteArrayInputStream(buf.toString().getBytes());
                length = buf.toString().getBytes().length;


            } else {
                fis = new FileInputStream(canonicalFile);
                length = canonicalFile.length();
            }
        } catch (FileNotFoundException e) {
            // The file may also be forbidden to us instead of missing, but we're leaking less information this way 
            sendError(he, 404, "File not found"); 
            return;
        }

        String mimeType = lookupMime(urlPath);
        he.getResponseHeaders().set("Content-Type", mimeType);
        if ("GET".equals(method)) {
            he.sendResponseHeaders(200, length);
            OutputStream os = he.getResponseBody();
            copyStream(fis, os);
            os.close();
        } else {
            assert("HEAD".equals(method));
            he.sendResponseHeaders(200, -1);
        }
        fis.close();
    }

    private void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) >= 0) {
            os.write(buf, 0, n);
        }
    }

    private void sendError(HttpExchange he, int rCode, String description) throws IOException {
        String message = "HTTP error " + rCode + ": " + description;
        byte[] messageBytes = message.getBytes("UTF-8");

        he.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        he.sendResponseHeaders(rCode, messageBytes.length);
        OutputStream os = he.getResponseBody();
        os.write(messageBytes);
        os.close();
    }

    // This is one function to avoid giving away where we failed 
    private void reportPathTraversal(HttpExchange he) throws IOException {
        sendError(he, 400, "Path traversal attempt detected");
    }

    private static String getExt(String path) {
        int slashIndex = path.lastIndexOf('/');
        String basename = (slashIndex < 0) ? path : path.substring(slashIndex + 1);

        int dotIndex = basename.lastIndexOf('.');
        if (dotIndex >= 0) {
            return basename.substring(dotIndex + 1);
        } else {
            return "";
        }
    }

    private static String lookupMime(String path) {
        String ext = getExt(path).toLowerCase();
        return MIME_MAP.getOrDefault(ext, "application/octet-stream");
    }
}