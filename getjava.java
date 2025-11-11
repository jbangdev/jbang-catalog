///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.7
//DEPS io.foojay.api:discoclient:21.0.1

import static java.lang.System.out;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import io.foojay.api.discoclient.DiscoClient;
import io.foojay.api.discoclient.pkg.Architecture;
import io.foojay.api.discoclient.pkg.ArchiveType;
import io.foojay.api.discoclient.pkg.Bitness;
import io.foojay.api.discoclient.pkg.Distribution;
import io.foojay.api.discoclient.pkg.Latest;
import io.foojay.api.discoclient.pkg.LibCType;
import io.foojay.api.discoclient.pkg.OperatingSystem;
import io.foojay.api.discoclient.pkg.PackageType;
import io.foojay.api.discoclient.pkg.Pkg;
import io.foojay.api.discoclient.pkg.ReleaseStatus;
import io.foojay.api.discoclient.pkg.Scope;
import io.foojay.api.discoclient.pkg.SemVer;
import io.foojay.api.discoclient.pkg.TermOfSupport;
import io.foojay.api.discoclient.pkg.VersionNumber;
import io.foojay.api.discoclient.util.PkgInfo;
import io.foojay.api.discoclient.util.SemVerParsingResult;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "getjava", version = "getjava 0.1", description = "List and Download Java available from api.foojay.io made with jbang")
class getjava implements Callable<Integer> {

    @Option(names = { "-v", "--version" }, description = "Java version to look for. Can be a major version (i.e. 17) or more specific distribution version (i.e 11.0.12)")
    private Optional<SemVer> version;

    @Option(names = { "-d",
            "--distro" }, defaultValue = "none", description = "default: ${DEFAULT-VALUE} Valid values: ${COMPLETION-CANDIDATES}")
    private Distribution distribution;

    @Option(names = { "-l",
            "--latest" }, defaultValue = "none", description = "default: ${DEFAULT-VALUE} Valid values: ${COMPLETION-CANDIDATES}")
    private Latest latest;

    @Option(names = { "--os" }, description = "default: ${DEFAULT-VALUE} Valid values: ${COMPLETION-CANDIDATES}")
    private Optional<OperatingSystem> os;

    @Option(names = {
            "--libc" }, defaultValue = "none", description = "default: ${DEFAULT-VALUE} Valid values: ${COMPLETION-CANDIDATES}")
    private LibCType libc;

    @Option(names = {
            "--arch" }, defaultValue = "none", description = "default: ${DEFAULT-VALUE} Valid values: ${COMPLETION-CANDIDATES}")
    private Architecture architecture;

    @Option(names = {
            "--bits" }, defaultValue = "none", description = "default: ${DEFAULT-VALUE} Valid values: ${COMPLETION-CANDIDATES}")
    private Bitness bits;

    @Option(names = {
            "--archive" }, defaultValue = "none", description = "default: ${DEFAULT-VALUE} Valid values: ${COMPLETION-CANDIDATES}")
    private ArchiveType archive;

    @Option(names = {
            "--type" }, defaultValue = "jdk", description = "default: ${DEFAULT-VALUE} Valid values: ${COMPLETION-CANDIDATES}")
    private PackageType type;

    @Option(names = { "--javafx" }, description = "default: ${DEFAULT-VALUE} Valid values: ${COMPLETION-CANDIDATES}")
    private Boolean javafx;

    @Option(names = { "--direct" }, description = "default: ${DEFAULT-VALUE} Valid values: ${COMPLETION-CANDIDATES}")
    private Boolean direct;

    @Option(names = {
            "--status" }, defaultValue = "none", description = "default: ${DEFAULT-VALUE} Valid values: ${COMPLETION-CANDIDATES}")
    private ReleaseStatus status;

    @Option(names = {
            "--support" }, defaultValue = "none", description = "default: ${DEFAULT-VALUE} Valid values: ${COMPLETION-CANDIDATES}")
    private TermOfSupport support;

    @Option(names = {
            "--scope" }, defaultValue = "none", description = "default: ${DEFAULT-VALUE} Valid values: ${COMPLETION-CANDIDATES}")
    private Scope scope;

    @Override
    public Integer call() throws Exception { // your business logic goes here...

        DiscoClient disco = new DiscoClient();

        VersionNumber versionNumber = version.map(v->v.getVersionNumber()).orElse(null);
        os = Optional.of(os.orElse(disco.getOperatingSystem())); 
        List<Pkg> packages = disco.getPkgs(distribution, versionNumber, latest,
                os.get(), libc, architecture, bits, archive, type, javafx, direct, status,
                support, scope);

        if (packages.isEmpty()) {
            System.err.println("No matching Java found using the following criteria:");
            if(distribution!=Distribution.NONE) out.println("Distro: " + distribution.name());
            if(versionNumber!=null) out.println("Version: " + versionNumber);
            if(latest!=Latest.NONE) out.println("Latest: " + latest.getUiString());
            if(os.get()!=OperatingSystem.NONE) out.println("Operating System: " + os.get().getUiString());
            if(libc!=LibCType.NONE) out.println("Libc: " + libc.toString());
            if(architecture!=Architecture.NONE) out.println("Architecture: " + architecture.name());
            if(bits!=Bitness.NONE) out.println("Bits:" + bits.name());
            if(archive!=ArchiveType.NONE) out.println("Archive: " + archive.name());
            if(type!=PackageType.NONE) out.println("Type: " + type.name());
            if(javafx!=null) out.println("JavaFX: " + javafx);
            if(direct!=null) out.println("Direct download: " + direct);
            if(status!=ReleaseStatus.NONE) out.println("Release status: " + status.name());
            if(support!=TermOfSupport.NONE) out.println("Support: " + support.name());
            if(scope!=Scope.NONE) out.println("Scope: " + scope.name());

        } else {
            packages.sort(Comparator.comparing(Pkg::getDistribution).thenComparing(Pkg::getJavaVersion).thenComparing(Pkg::getOperatingSystem).thenComparing(Pkg::getArchitecture).thenComparing(Pkg::getArchiveType).thenComparing(Pkg::getArchitecture));

            if(packages.size()==1) {
                        Pkg p = packages.get(0);
                        printInfo(p);
                        Path path = Paths.get(p.getFileName());
                        System.out.println("Found an exact match. Downloading to " + path);
                        //todo probably does not handle all cases with redirects etc.
                        String uri = disco.getPkgDirectDownloadUri(p.getEphemeralId(), p.getJavaVersion());
                        try(InputStream is = URI.create(uri).toURL().openStream()) {
                                Files.copy(is,path);
                        }                        

                } else {
            packages.forEach(p -> {
                printInfo(p);
            });
            System.err.println(packages.size() + " different Java's found. Please narrow result to 1 to get download");
            }
        }
        return 0;
    }
private void printInfo(Pkg p) {
        out.println(orDefault(p.getDistribution().getUiString(),
                orDefault(p.getDistributionName(), "<Unknown distro>")) + " " + p.getDistributionVersion() + ":"
                + p.getOperatingSystem().getUiString() + ":" + p.getReleaseStatus() + ":" + p.getPackageType()
                + ":" + p.getArchiveType() + ": " + p.getBitness() + ":" + p.getArchitecture() + ":"
                + p.getFileName());
}
    public static void main(String... args) {
        int exitCode = new CommandLine(new getjava()).setCaseInsensitiveEnumValuesAllowed(true)
                .registerConverter(OperatingSystem.class,
                        (s) -> "none".equals(s.toLowerCase()) ? OperatingSystem.NONE : OperatingSystem.fromText(s))
                .registerConverter(SemVer.class, (s) ->  { 
                    SemVerParsingResult v = SemVer.fromText(s);
                    if(v.getError1()==null) {
                        return v.getSemVer1();
                    }
                    throw new Exception(v.getError1().getMessage());
                 })
                .execute(args);
        System.exit(exitCode);
    }

    String orDefault(String val, String defaultValue) {
        if (val != null && !val.trim().isEmpty()) {
            return val;
        }
        return defaultValue;
}
    }

   