import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateRedirectedXrefPaths {

    public static final String fileExtension = ".adoc";
    public static final String extraPath = "/midpoint/reference";

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
            // This may not be a dealbreaker if the required arguments are supplied in the right format;
            // in such a case, we just ignore the invalid arguments.
            else {
                System.err.println("Ignoring invalid argument: " + args[i] + "\t\tCLI arguments must be key-value pairs: `--option1 value1 --option2 value2`");
            }
            i++;
        }

        // Assign CLI arguments to control variables
        File rootPath;
        if (params.containsKey("root")) {
            rootPath = new File(params.get("root"));
        }
        else {
            System.err.println("Path to root missing. Use `--root /some/path`.");
            return;
        }

        File auxRootPath = null;
        boolean auxRootSpecified = false;
        if (params.containsKey("auxroot")) {
            auxRootPath = new File(params.get("auxroot"));
            auxRootSpecified = true;
        }

        String throwAwayRootPrefix;
        if (params.containsKey("root-prefix")) {
            throwAwayRootPrefix = removeTrailingSlash(params.get("root-prefix"));
        }
        else {
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

        String extraPathSegment;
        if (isReference) {
            extraPathSegment = extraPath;
        }
        else {
            extraPathSegment = "";
        }

        // Get list of all files in both root and auxiliary root paths
        try {
            ArrayList<File> adocFiles = new ArrayList<>(listFiles(rootPath));
            ArrayList<File> auxAdocFiles = new ArrayList<>();
            if (auxRootSpecified) {
                auxAdocFiles.addAll(listFiles(auxRootPath));
            }

            // Extract redirects (:page-moved-from:) from every listed file
            // For every such redirect in a file, rewrite all xrefs that use the redirected path to the path of the current file. Do this in all listed files.
            for (File adocPage : adocFiles) {
                ArrayList<String> redirectsInPage = getRedirects(adocPage);
                String adjustedPhysicalFilePath = adocPage.getPath().replace("/index.adoc", "").replace(fileExtension, "").replace(throwAwayRootPrefix, "");
                if (!redirectsInPage.isEmpty()) {
                    for (String redirect : redirectsInPage) {
                        fixObsoleteXrefs(adocFiles, redirect, adjustedPhysicalFilePath, extraPathSegment);
                        fixObsoleteXrefs(auxAdocFiles, redirect, adjustedPhysicalFilePath, extraPathSegment);
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
                    if (node.getName().endsWith(fileExtension)) {
                        foundAdocFiles.add(node);
                    }
                }
                else if (node.isDirectory()) {
                    foundAdocFiles.addAll(listFiles(node));
                }
            }
        }
        catch (Exception e) {
            System.err.println("Exception: " + e.getMessage() + " \n(Likely because nodesInDirectory for " + directory + " contains no files. Check the path.)");
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
                    String newlineEOL = "\n";
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
                    updateFile(article, fixedFile);
                }
            }
            catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void updateFile(File fileToUpdate, StringBuilder updateContent) {
        try {
            FileWriter fileToUpdateWriter = new FileWriter(fileToUpdate);
            fileToUpdateWriter.write(String.valueOf(updateContent));
            fileToUpdateWriter.close();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void printHelp() {
        String help = "This is Dokuro-chan, a tool that mercilessly bludgeons obsolete redirected paths from xrefs and replaces them with up-to-date paths." +
                "This is an abridged usage guide. For full documentation, refer to the home of the project: https://github.com/edison23/UpdateRedirectedXrefPaths\n" +
                "Dokuro depends on redirects being specified using `:page-moved-from: /some/old/path` in .adoc files.\n" +
                "The new xref paths are constructed from the location of the file containing the redirect." +
                "Use these parameters:\n" +
                "\t--root /home/dokuro/projects/docs/guides\t\tSpecify where are the files to fix. This part of the path to files is also omitted from the new xref paths.\n" +
                "\t--auxroot /home/dokuro/projects/other-docs/\t\tSpecify if your portal is split into two repositories, for instance, which must be both checked because they are interlinked." +
                    "Files in this auxiliary root path are scanned for xrefs to be fixed, not for redirects." +
                    "`-root` and `--auxroot` are not commutative. You need to run Dokuro-chan from the perspective of each path, i.e., twice, to fix the redirected paths in both." +
                "\t--root-prefix /home/dokuro/projects/docs/\t\tSpecify if you run Dokuro only for portion of the whole tree, " +
                    "meaning the root to which `xref` paths must be relative is higher in the file system than what you have in `--root`.\n" +
                "\t--reference true\t\t\tSet if you need to prefix the physical paths to files with an arbitrary string." +
                    "No need to use `--reference false`; just leave out the argument altogether.\n" +
                "\t--help\t\t\t\t\tPrint this help.";
        System.out.println(help);
    }
}
