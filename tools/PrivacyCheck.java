import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Contrôle de livraison autonome, jamais inclus dans le mod. */
class PrivacyCheck {
    private static final Pattern ACCOUNT_PATH = Pattern.compile(
            "(?i)(?:[a-z]:[\\\\/]+Users[\\\\/]+[^\\\\/\\s\"<>]+|/(?:home|Users)/[^/\\s\"<>]+)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+\\-]+@([A-Za-z0-9.\\-]+\\.[A-Za-z]{2,})");
    private static final Pattern SECRET = Pattern.compile(
            "(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[0-9A-Z]{16}"
                    + "|eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"
                    + "|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"
                    + "|(?i:(?:password|api[_-]?key|secret|access[_-]?token)\\s*[:=]\\s*[\"'][A-Za-z0-9_+/=-]{20,}[\"']))");
    private static final Pattern AUTHOR = Pattern.compile("\"authors\"\\s*:\\s*\\[\\s*\"By FastedCorsi\"\\s*]");
    private static final Set<String> EXCLUDED = Set.of("build", ".gradle", ".git", "run", "logs", "config",
            "configs", "saves", "screenshots", "crash-reports", "sessions", "backups");
    private static final int ENTRY_LIMIT = 64 * 1024 * 1024;
    private final List<String> privateTerms;
    private final List<String> issues = new ArrayList<>();
    private int inspected;

    private PrivacyCheck(List<String> privateTerms) { this.privateTerms = privateTerms; }

    private static boolean excluded(String location) {
        for (String part : location.replace('\\', '/').split("/")) {
            String lower = part.toLowerCase(Locale.ROOT);
            if (EXCLUDED.contains(lower) || lower.startsWith(".env") || lower.endsWith(".log")
                    || lower.endsWith(".pem") || lower.endsWith(".p12") || lower.endsWith(".pfx")
                    || lower.endsWith(".keystore")) return true;
        }
        return false;
    }

    private static List<String> findings(String text, List<String> terms) {
        List<String> result = new ArrayList<>();
        if (ACCOUNT_PATH.matcher(text).find()) result.add("personal-account-path");
        Matcher emails = EMAIL.matcher(text);
        while (emails.find()) {
            String domain = emails.group(1).toLowerCase(Locale.ROOT);
            if (!(domain.endsWith(".invalid") || domain.endsWith(".test")
                    || Set.of("example.com", "example.org", "example.net", "users.noreply.github.com").contains(domain))) {
                result.add("email-review-required");
                break;
            }
        }
        if (SECRET.matcher(text).find()) result.add("possible-secret-review-required");
        for (String term : terms) {
            if (Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(term)
                    + "(?![\\p{L}\\p{N}_])").matcher(text).find()) {
                result.add("private-identity-term");
                break;
            }
        }
        return result;
    }

    private void inspect(String location, byte[] bytes, int depth) throws IOException {
        inspected++;
        if (excluded(location)) issues.add(location + ": excluded-private-content");
        Set<String> categories = new LinkedHashSet<>();
        for (Charset charset : List.of(StandardCharsets.UTF_8, StandardCharsets.UTF_16LE, StandardCharsets.UTF_16BE))
            categories.addAll(findings(new String(bytes, charset), privateTerms));
        for (String category : categories) issues.add(redact(location) + ": " + category);
        if (bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K') {
            if (depth >= 8) { issues.add(location + ": archive-depth-review-required"); return; }
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    if (entry.getName().contains("../")) { issues.add(location + ": unsafe-archive-entry"); continue; }
                    byte[] nested = zip.readNBytes(ENTRY_LIMIT + 1);
                    if (nested.length > ENTRY_LIMIT) { issues.add(location + ": oversized-entry"); continue; }
                    inspect(location + "!/" + entry.getName(), nested, depth + 1);
                }
            }
        }
    }

    private void inspectSources(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (excluded(relative)) continue;
                if (Files.size(path) > ENTRY_LIMIT) { issues.add(relative + ": oversized-source"); continue; }
                inspect(relative, Files.readAllBytes(path), 0);
            }
        }
    }

    private String redact(String value) {
        String clean = value;
        for (String term : privateTerms) clean = clean.replaceAll("(?iu)" + Pattern.quote(term), "[redacted]");
        return clean;
    }

    private static List<String> localTerms() {
        Set<String> terms = new LinkedHashSet<>();
        String account = System.getProperty("user.name", "");
        if (account.length() >= 4 && !Set.of("root", "user", "admin", "runner", "developer")
                .contains(account.toLowerCase(Locale.ROOT))) terms.add(account);
        String supplied = System.getenv("TROPIMON_PRIVATE_TERMS");
        if (supplied != null) for (String term : supplied.split("\\R"))
            if (term.strip().length() >= 3) terms.add(term.strip());
        return List.copyOf(terms);
    }

    private static byte[] archive(String name, byte[] content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static void selfTest() throws IOException {
        String fictional = "FictionalMaintainer";
        require(findings("credits: " + fictional, List.of(fictional)).contains("private-identity-term"));
        require(findings("C:" + "\\Users\\" + fictional + "\\project", List.of()).contains("personal-account-path"));
        require(findings("contact" + "@sample-company.dev", List.of()).contains("email-review-required"));
        require(findings("ghp_" + "Z".repeat(36), List.of()).contains("possible-secret-review-required"));
        require(findings("fixture" + "@example.invalid By FastedCorsi", List.of()).isEmpty());
        PrivacyCheck nested = new PrivacyCheck(List.of(fictional));
        nested.inspect("fixture.jar", archive("nested.jar", archive("Fixture.class",
                ("constant:" + fictional).getBytes(StandardCharsets.UTF_8))), 0);
        require(!nested.issues.isEmpty());
        require(AUTHOR.matcher("{\"authors\":[\"By FastedCorsi\"]}").find());
        System.out.println("Privacy checker: synthetic tests passed.");
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("Privacy checker synthetic self-test failed.");
    }

    public static void main(String[] args) throws Exception {
        selfTest();
        if (args.length < 2) throw new IllegalArgumentException("Expected project directory and final archives.");
        PrivacyCheck check = new PrivacyCheck(localTerms());
        check.inspectSources(Path.of(args[0]));
        for (int index = 1; index < args.length; index++) {
            Path artifact = Path.of(args[index]);
            byte[] bytes = Files.readAllBytes(artifact);
            check.inspect(artifact.getFileName().toString(), bytes, 0);
            try (ZipFile zip = new ZipFile(artifact.toFile())) {
                ZipEntry metadata = zip.getEntry("fabric.mod.json");
                if (metadata == null) check.issues.add(artifact.getFileName() + ": missing-metadata");
                else try (InputStream input = zip.getInputStream(metadata)) {
                    String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    if (!text.contains("\"id\": \"tropimon_farm\"") || !AUTHOR.matcher(text).find())
                        check.issues.add(artifact.getFileName() + ": incorrect-public-metadata");
                }
            }
        }
        if (!check.issues.isEmpty()) {
            check.issues.forEach(System.err::println);
            throw new IllegalStateException("Privacy review required; matched values are never printed.");
        }
        System.out.println("Privacy check passed: " + check.inspected + " entries; credit: By FastedCorsi.");
    }
}
