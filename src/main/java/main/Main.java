package main;

import sql.Query;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class Main {

    private static final String[] DEFAULT_PARAMS = {
            "false", // Use kb
            System.getProperty("user.home") + "/genera.txt", // Input file path
            System.getProperty("user.home") + "/counts.csv" // Output file path
    };

    private static final int NUM_QUERIES = 3;

    public static void main(String[] args) {

        // Initialize Microsoft JDBC MSSQL driver
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            System.out.println("FATAL: JDBC MSSQL driver not found.");
            return;
        }

        // Parse command-line arguments
        String[] params = parseArgs(args);

        if (params == null) {
            System.out.println("\n--- Process failed. ---");
            return;
        }

        // Read list of concepts
        ArrayList<String> concepts;
        if (!Boolean.parseBoolean(params[0])) concepts = readList(getFile(params[1])); // Use path specified
        else concepts = getAllGenera(); // Use kb server

        // Run SQL queries on VARS database
        assert concepts != null;
        String[] statements = {
                Query.readFromStream(ClassLoader.getSystemClassLoader().getResourceAsStream("AnnotationsQuery.sql")),
                Query.readFromStream(ClassLoader.getSystemClassLoader().getResourceAsStream("QualityImageAnnotationsQuery.sql")),
                Query.readFromStream(ClassLoader.getSystemClassLoader().getResourceAsStream("TotalAnnotationsQuery.sql"))
        };
        ArrayList<ResultSet> results = runQueries(concepts, statements);

        // Calculate and display results
        String[] resultLines = getResults(results, concepts);

        // Write results
        writeResults(resultLines, params[2]);

        System.out.println("\n--- Process completed successfully. ---");

    }

    private static void writeResults(String[] results, String path) {

        File writeFile = new File(path);

        // Initialize writer
        BufferedWriter writer;
        try {
            writer = new BufferedWriter(new FileWriter(writeFile));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        for (String resultLine : results) {
            // Write results to CSV
            try {
                writer.write(resultLine);
                writer.newLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Close writer
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static String[] getResults(ArrayList<ResultSet> results, ArrayList<String> concepts) {

        String[] resultLines = new String[concepts.size()];

        System.out.println();

        for (int i = 0; i < results.size(); i += NUM_QUERIES) {

            // Grab ResultSets
            ResultSet annotationsResult = results.get(i);
            ResultSet qualityAnnotationsResult = results.get(i + 1);
            ResultSet observationsResult = results.get(i + 2);

            String concept = concepts.get(i / NUM_QUERIES);

            System.out.println("Counting for concept " + concept + ":");

            // Count
            int observationCount = 0, qualityImageCount = 0, totalImageCount = 0;
            try {
                while (annotationsResult.next()) totalImageCount++;
                while (qualityAnnotationsResult.next()) qualityImageCount++;
                while (observationsResult.next()) observationCount++;
            } catch (SQLException e) {
                e.printStackTrace();
            }

            System.out.println(
                    String.format(
                            "%1$-15s%2$10d",
                            "Observations:",
                            observationCount
                    )
            );
            System.out.println(
                    String.format(
                            "%1$-15s%2$10d",
                            "Quality:",
                            qualityImageCount
                    )
            );
            System.out.println(
                    String.format(
                            "%1$-15s%2$10d",
                            "Total:",
                            totalImageCount
                    )
            );

            System.out.println();

            resultLines[i / NUM_QUERIES] = concept + "," + observationCount + "," + qualityImageCount + "," + totalImageCount;

        }

        return resultLines;

    }

    private static String[] parseArgs(String[] args) {

        String[] params = DEFAULT_PARAMS;

        // Verify usage
        try {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-kb")) params[0] = "true";
                else if (args[i].equals("-o")) params[2] = args[i + 1];
            }
            params[1] = args[args.length - 1]; // Set input file path to last argument
            return params;
        } catch (Exception e) {
            System.out.println("Incorrect usage. Follow:\n\tjava -jar vars-summary-1.0-all.jar [OPTIONS] path/to/genera.txt");
            return null;
        }

    }

    private static File getFile(String path) {

        File conceptList = new File(path);

        // Verify file exists
        if (!conceptList.exists()) {
            System.out.println(conceptList.getAbsolutePath() + " does not exist. Check options.");
            return null;
        }

        return conceptList;

    }

    private static ArrayList<String> readList(File conceptList) {

        try {

            BufferedReader reader = new BufferedReader(new FileReader(conceptList));

            ArrayList<String> concepts = new ArrayList<>();

            String concept;
            while ((concept = reader.readLine()) != null) concepts.add(concept);

            return concepts;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    private static ArrayList<ResultSet> runQueries(ArrayList<String> concepts, String[] statements) {

        ArrayList<ResultSet> resultSets = new ArrayList<>();

        Query mainQuery = new Query();

        for (String concept : concepts) {

            System.out.println("Querying " + concept);

            for (String statement : statements) {
                mainQuery.setStatement(insertConcept(statement, concept));
                resultSets.add(mainQuery.executeStatement());
            }

        }

        return resultSets;
    }

    private static String insertAt(String str, String insert, int index) {
        return (new StringBuilder(str).insert(index, insert)).toString();
    }

    private static String insertConcept(String str, String concept) {
        String first = insertAt(
                str,
                concept,
                str.indexOf('\'') + 1
        );
        return insertAt(
                first,
                concept,
                first.indexOf('%') - 1
        );
    }

    private static ArrayList<String> getAllGenera() {

        System.out.println("Getting genera from MBARI kb server...");

        String rank = "genus";

        Query getAll = new Query("SELECT ConceptName FROM Annotations WHERE Image IS NOT NULL GROUP BY ConceptName ORDER BY ConceptName ASC");
        ResultSet result = getAll.executeStatement();

        ArrayList<String> conceptList = new ArrayList<>();

        // Build concept list
        try {
            while (result.next()) {
                String concept = result.getString("ConceptName");
                conceptList.add(concept);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        ArrayList<String> rankList = new ArrayList<>();

        // Build rank list
        for (String concept : conceptList) {

            String link = "http://m3.shore.mbari.org/kb/v1/phylogeny/down/" + concept;

            // Fix links with spaces
            while (link.contains(" ")) link = link.replace(" ", "%20");

            System.out.println("Checking if " + concept + " at rank '" + rank + "'");

            boolean isAtLevel = false;

            try {

                // Establish connection
                URL conceptPhylogenyURL = new URL(link);
                URLConnection connection = conceptPhylogenyURL.openConnection();

                // Establish input stream
                InputStream inputStream = connection.getInputStream();

                // Read lines to ArrayList
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) json.append(line);

                // Check if at rank
                int rankIndex = json.indexOf("rank") + 8;
                if (json.toString().substring(rankIndex, rankIndex + rank.length()).equals(rank)) isAtLevel = true;

            } catch (IOException e) {
                System.out.println("Error: Could not retrieve " + link + ", removing concept \"" + concept + "\" from results.");
                isAtLevel = false;
            }

            // Add if at desired rank
            if (isAtLevel) {
                rankList.add(concept);
            }
        }

        return rankList;

    }

}
