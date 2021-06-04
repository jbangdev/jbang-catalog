//DESCRIPTION `httpd` runs a webserver serving out the content of a directory.
//DESCRIPTION Example: `jbang httpd@jbangdev -d _site` will serve out the `_site` folder on localhost:8000.

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class httpd {

    private static final String USAGE  //
            = String.join("\n",
            "usage: httpd [-h] [-b bind address] [-p port] [-d directory] [-o none|info]",
            "",
            "Serves directory content over http.");

    static int port = 8000;
    static String bind = null;
    static String output = "info";
    static String directory = System.getProperty("user.dir");

    static Logger logger;

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%5$s %n");
        logger = Logger.getLogger("http");
    }

    public static void main(String[] argv) throws IOException {


        try {
            for (int i = 0; i < argv.length; i++) {
                String arg = argv[i];

                if (arg.startsWith("-")) {
                    switch (arg) {
                        case "-h":
                            usage();
                        case "-p":
                            port = Integer.parseInt(argv[++i]);
                            break;
                        case "-b":
                            bind = argv[++i];
                            break;
                        case "-d":
                            directory = Path.of(System.getProperty("user.dir")).resolve(argv[++i]).toFile().getCanonicalPath();
                            break;
                        case "-o":
                            output = argv[++i];
                            break;
                        default:
                            usage();
                    }
                } else {
                    usage();
                }
            }
        } catch (Exception e) {
            usage();
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(bind), port), 0);
        StaticFileHandler sfh = new StaticFileHandler("/", directory);
        server.createContext("/", sfh).getFilters().add(logging(logger));

        server.start();

        String bindlabel = bind == null ? "0.0.0.0" : bind;
        logger.info("Serving HTTP on " + bindlabel + " port " + port + " (http://" + bindlabel + ":" + port + "/) from " + directory + " ...");
    }

    private static Filter logging(Logger logger) {
        return new Filter() {

            final SimpleDateFormat df = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z");

            @Override
            public void doFilter(HttpExchange http, Chain chain) throws IOException {
                try {


                    chain.doFilter(http);
                } finally {
                    logger.info(String.format("%s %s %s [%s] \"%s %s\" %s %s",
                            http.getRemoteAddress().getAddress().getHostAddress(),
                            "-",
                            "-",
                            df.format(new Date()),
                            http.getRequestMethod(),
                            http.getRequestURI(),
                            http.getResponseCode(),
                            "-"));
                }
            }

            @Override
            public String description() {
                return "logging";
            }
        };
    }

    private static void usage() {
        System.out.println(USAGE);
        System.exit(0);
    }
}

// Based on https://stackoverflow.com/a/42404471/71208
class StaticFileHandler implements HttpHandler {
    private static final Map<String, String> MIME_MAP = new HashMap<>();

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
        MIME_MAP.put("qute", "text/plain");
        MIME_MAP.put("php", "text/plain");
        MIME_MAP.put("java", "text/plain");
        MIME_MAP.put("jsh", "text/plain");
        MIME_MAP.put("adoc", "text/plain");
        MIME_MAP.put("gradle", "text/plain");
        MIME_MAP.put("properties", "text/plain");
        MIME_MAP.put("yaml", "text/plain");
        MIME_MAP.put("toml", "text/plain");
        MIME_MAP.put("", "text/html");

    }

    private final String filesystemRoot;
    private final String urlPrefix;

    /**
     * @param urlPrefix      The prefix of all URLs.
     *                       This is the first argument to createContext. Must start and end in a slash.
     * @param filesystemRoot The root directory in the filesystem.
     *                       Only files under this directory will be served to the client.
     *                       For instance "./staticfiles".
     */
    public StaticFileHandler(String urlPrefix, String filesystemRoot) {

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
        String contenType = URLConnection.getFileNameMap().getContentTypeFor(path);
        if (contenType != null) return contenType;

        String ext = getExt(path).toLowerCase();
        return MIME_MAP.getOrDefault(ext, "application/octet-stream");
    }

    public void handle(HttpExchange he) throws IOException {
        String method = he.getRequestMethod();
        if (!("HEAD".equals(method) || "GET".equals(method))) {
            sendError(he, 501, "Unsupported HTTP method");
            return;
        }

        String wholeUrlPath = he.getRequestURI().getPath();

        if (!wholeUrlPath.startsWith(urlPrefix)) {
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
        if (!canonicalPath.startsWith(filesystemRoot)) {
            reportPathTraversal(he);
            return;
        }

        if (canonicalFile.isDirectory() && !wholeUrlPath.endsWith("/")) {
            he.getResponseHeaders().add("Location", wholeUrlPath + "/");
            he.sendResponseHeaders(301, 0);
            OutputStream os = he.getResponseBody();
            os.write("".getBytes());
            os.close();
            return;
        }

        InputStream fis;
        long length;
        try {
            if (canonicalFile.isDirectory()) {
                StringBuilder buf = new StringBuilder();

                Path index = canonicalFile.toPath().resolve("index.html");
                if (Files.exists(index)) {
                    buf.append(Files.readString(index));
                } else {
                    buf.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">\n" +
                            "<html>\n" +
                            "<head>\n" +
                            "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n" +
                            "<meta name=\"viewport\" content=\"width=device-width\">\n" +
                            "<title>Directory listing for " + urlPath + "</title>\n" +
                            "<style>/*! modern-normalize v1.0.0 | MIT License | https://github.com/sindresorhus/modern-normalize */*,::after,::before{box-sizing:border-box}:root{-moz-tab-size:4;tab-size:4}html{line-height:1.15;-webkit-text-size-adjust:100%}body{margin:0}body{font-family:system-ui,-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif,'Apple Color Emoji','Segoe UI Emoji'}hr{height:0;color:inherit}abbr[title]{text-decoration:underline dotted}b,strong{font-weight:bolder}code,kbd,pre,samp{font-family:ui-monospace,SFMono-Regular,Consolas,'Liberation Mono',Menlo,monospace;font-size:1em}small{font-size:80%}sub,sup{font-size:75%;line-height:0;position:relative;vertical-align:baseline}sub{bottom:-.25em}sup{top:-.5em}table{text-indent:0;border-color:inherit}button,input,optgroup,select,textarea{font-family:inherit;font-size:100%;line-height:1.15;margin:0}button,select{text-transform:none}[type=button],[type=reset],[type=submit],button{-webkit-appearance:button}::-moz-focus-inner{border-style:none;padding:0}:-moz-focusring{outline:1px dotted ButtonText}:-moz-ui-invalid{box-shadow:none}legend{padding:0}progress{vertical-align:baseline}::-webkit-inner-spin-button,::-webkit-outer-spin-button{height:auto}[type=search]{-webkit-appearance:textfield;outline-offset:-2px}::-webkit-search-decoration{-webkit-appearance:none}::-webkit-file-upload-button{-webkit-appearance:button;font:inherit}summary{display:list-item}</style>" +
                            //   "<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/modern-normalize/1.0.0/modern-normalize.min.css\">\n" +
                            "</head>\n" +
                            "<body>\n" +
                            "<h1>Directory listing for " + urlPath + "</h1>\n" +
                            "<hr>\n" +
                            "<ul>");

                    Files.list(canonicalFile.toPath())
                            .sorted().forEach(p -> buf.append("<li><a href=\"" + canonicalFile.toPath().relativize(p) + (p.toFile().isDirectory() ? "/" : "") + "\">" + p.getFileName() + (p.toFile().isDirectory() ? "/" : "") + "</a></li>\n"));

                    buf.append("</ul>\n" +
                            "<hr>\n" +
                            "<p style=\"text-align:right;\"><small>Served by <a href=\"https://jbang.dev/appstore\">httpd@jbangdev</a></small></p>\n" +
                            "</body>\n" +
                            "</html>");
                }
                byte[] bytes = buf.toString().getBytes();
                fis = new ByteArrayInputStream(bytes);
                length = bytes.length;


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
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

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
}
