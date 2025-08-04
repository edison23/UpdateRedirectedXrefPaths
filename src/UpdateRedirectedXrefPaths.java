import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateRedirectedXrefPaths {

    public static void main(String[] args) {


        // Print usage info and exit if no arguments supplied.
        if (args.length < 1) {
            printHelp();
            System.exit(1);
        }

        // Parse CLI arguments into the params map.
        final Map<String, String> params = new HashMap<>();
        int i = 0;
        while (args.length > i) {

            // User asked for help. Print it and exit.
            if (args[i].substring(2).equals("help")) {
                printHelp();
                return;
            }

            // User supplied valid arguments. Go thru the pairs and save them to the `params` map.
            // We don't check for their validity yet, only if they're in the right format (`--option value`)
            if ((args[i].startsWith("--")) && (args.length > i+1)) {
                params.put(args[i].substring(2), args[++i]);
            }

            // The supplied argument is not in the right --key value format, warn user about it.
            // This may not be a deal breaker if the required arguments are supplied in the right format;
            // in such a case, we just ignore the invalid arguments.
            else {
                System.err.println("Ignoring invalid argument: " + args[i] + "\t\tCLI arguments must be key-value pairs: `--option1 value1 --option2 value2`");
            }
            i++;
        }

        File rootPath;
        if (params.containsKey("root")) {
            rootPath = new File(params.get("root"));
        }
        else {
            System.err.println("Path to root missing. Use `--root /some/path`.");
            return;
        }

        String throwAwayRootPrefix;
        if (params.containsKey("root-prefix")) {
            throwAwayRootPrefix = removeTrailingSlash(params.get("root-prefix"));
        }
        else {
            System.out.println("WARNING: No root path prefix (throw-away prefix) provided. The `--root` argument value is used.");
            throwAwayRootPrefix = removeTrailingSlash(params.get("root"));
        }

        boolean isReference;
        try {
            isReference = Boolean.parseBoolean(params.get("reference"));
        }
        catch (Exception e){
            System.err.println("Invalid argument value. Reference can be 'true' or 'false'. Use `--reference true`.\n" + e.getMessage());
            return;
        }

        String referenceURL;
        if (isReference) {
            referenceURL = "/midpoint/reference";
        }
        else {
            referenceURL = "";
        }

        try {
            ArrayList<File> adocFiles = new ArrayList<>(listFiles(rootPath));

            for (File adocPage : adocFiles) {
                ArrayList<String> redirectsInPage = getRedirects(adocPage);
                if (!redirectsInPage.isEmpty()) {
                    for (String redirect : redirectsInPage) {
                        fixObsoleteXrefs(adocFiles, redirect, adocPage.getPath().replace(".adoc", "").replace(throwAwayRootPrefix, ""), referenceURL);
                    }
                }
            }
        }
        catch (NullPointerException e) {
            System.err.println("NullPointerException: " + e.getMessage());
        }
    }

    public static String removeTrailingSlash(String string) {
        if (!string.isEmpty()) {
            if (string.endsWith("/")) {
                return string.substring(0, string.length()-1);
            }
            else {
                return string;
            }
        }
        return string; // even if empty
    }

    public static ArrayList<File> listFiles(File directory) {
        ArrayList<File> foundAdocFiles = new ArrayList<>();
        try {
            File[] nodesInDirectory = directory.listFiles();
            for (File node : nodesInDirectory) {
                if (node.isFile()) {
                    if (node.getName().endsWith(".adoc")) {
                        foundAdocFiles.add(node);
                    }
                }
                else if (node.isDirectory()) {
                    foundAdocFiles.addAll(listFiles(node));
                }
            }
        }
        catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
        }
        return foundAdocFiles;
    }

    public static ArrayList<String> getRedirects(File page) {
        ArrayList<String> redirects = new ArrayList<>();
        try {
            Scanner pageReader = new Scanner(page);
            Pattern pattern = Pattern.compile(":page-moved-from: (.*)");
            while (pageReader.hasNextLine()) {
                String line = pageReader.nextLine();
                // If this evaluates to `true`, redirect has been found.
                // This assumes the `:page-moved-from:` is never indented.
                if (line.indexOf(":page-moved-from:") == 0) {
                    try {
                        Matcher matcher = pattern.matcher(line);
                        while (matcher.find()) {
                            redirects.add(matcher.group(1));
                        }
                    }
                    catch (Exception e) {
                        System.err.println(e.getMessage());
                    }
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return redirects;
    }

    public static void fixObsoleteXrefs(ArrayList<File> sources, String redirectedXref, String newTarget, String referencePrefix) {
        Pattern pattern = Pattern.compile("xref:" + redirectedXref + "/?");
        // New target path must have a trailing slash.
        // I create a new instance of the String under the same name so that I don't have to change the rest of the code...
        String newTargetSanitized;
        if (!newTarget.endsWith("/")) {
            newTargetSanitized = referencePrefix + newTarget + "/";
        }
        else {
            newTargetSanitized = referencePrefix + newTarget;
        }
        for (File article : sources) {
            boolean isFileTouched = false;
            try {
                Scanner articleReader = new Scanner(article);
                StringBuilder fixedFile = new StringBuilder();
                while (articleReader.hasNextLine()) {
                    StringBuilder line = new StringBuilder(articleReader.nextLine());
                    String newlineEOL;
                    if (articleReader.hasNextLine()) {
                        newlineEOL = "\n";
                    }
                    else {
                        newlineEOL = "";
                    }
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        isFileTouched = true;
                        String fixedLine = matcher.replaceAll("xref:" + newTargetSanitized);
                        fixedFile.append(fixedLine).append(newlineEOL);
                    }
                    else {
                        fixedFile.append(line).append(newlineEOL);
                    }
                }
                articleReader.close();
                if (isFileTouched) {
                    try {
                        FileWriter articleWriter = new FileWriter(article);
                        articleWriter.write(String.valueOf(fixedFile));
                        articleWriter.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void printHelp() {
        String help = "This is Dokuro-chan, a tool to mercilessly bludgeon obsolete redirected paths from xrefs and replace them with up-to-date paths.\n" +
                "Depends on redirects being specified in `:page-moved-from: /some/old/path` in .adoc files.\n" +
                "Use these parameters:\n" +
                "\t--root /home/dokuro/some/path\t\tSpecify where are the files to fix.\n" +
                "\t--root-prefix /home/dokuro\t\tSpecify if you run this script o a portion of the whole portal, meaning the root from which to process files is somewhere lower than root of the portal but the xref paths must be valid relative to the portal root.\n" +
                "\t--reference true\t\t\tSet if you're processing the reference section of the portal. Reference is special because physical paths don't match the files public URLs.\n" +
                "\t--help\t\t\t\t\tPrint this help.";
        System.out.println(help);
    }
}
