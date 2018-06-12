package main;

import sql.Query;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class Main {

    private static final String
            ANNOTATIONS_BASE_STATEMENT =
                    "SELECT DISTINCT ConceptName, Image " +
                    "FROM Annotations " +
                    "WHERE Image IS NOT NULL AND (" +
                            "ConceptName = '' OR " +
                            "ConceptName LIKE ' %') " +
                    "ORDER BY ConceptName ASC",
            QUALITYIMAGEANNOTATIONS_BASE_STATEMENT =
                    "SELECT DISTINCT ConceptName, ImageReference " +
                    "FROM QualityImageAnnotations " +
                    "WHERE ImageReference IS NOT NULL AND (" +
                            "ConceptName = '' OR " +
                            "ConceptName LIKE ' %') " +
                    "ORDER BY ConceptName ASC";

    private static final int NUM_QUERIES = 2;

    public static void main(String[] args) {

        // Initialize Microsoft JDBC MSSQL driver
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            System.out.println("FATAL: JDBC MSSQL driver not found.");
            return;
        }

        // Parse command-line arguments
        File conceptList = parseArgs(args);

        if (conceptList == null) {
            System.out.println("\n--- Process failed. ---");
            return;
        }

        // Read list of concepts
        ArrayList<String> concepts =
                readList(conceptList);
                // getAllAtLevel("genus");

        // Run SQL queries on VARS database
        assert concepts != null;
        ArrayList<ResultSet> results = runQueries(concepts);

        // Calculate, display, and write results
        displayResults(results, concepts, new File(conceptList.getParent() + "\\counts.csv"));

        System.out.println("\n--- Process completed successfully. ---");

    }

    private static void displayResults(ArrayList<ResultSet> results, ArrayList<String> concepts, File write) {

        BufferedWriter writer;
        try {
            writer = new BufferedWriter(new FileWriter(write));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        System.out.println();

        for (int i = 0; i < results.size(); i += NUM_QUERIES) {

            // Grab ResultSets
            ResultSet annotationsResult = results.get(i);
            ResultSet qualityAnnotationsResult = results.get(i + 1);

            String concept = concepts.get(i / NUM_QUERIES);

            System.out.println("Counting images for concept " + concept + ":");

            // Count distinct
            int qualityImageCount = 0, totalImageCount = 0;
            try {
                while (annotationsResult.next()) totalImageCount++;
                while (qualityAnnotationsResult.next()) qualityImageCount++;
            } catch (SQLException e) {
                e.printStackTrace();
            }

            System.out.println(
                    String.format(
                            "%1$-10s%2$10d",
                            "Quality:",
                            qualityImageCount
                    )
            );
            System.out.println(
                    String.format(
                            "%1$-10s%2$10d",
                            "Total",
                            totalImageCount
                    )
            );

            System.out.println();

            // Write results to CSV
            try {
                writer.write(concept + "," + qualityImageCount + "," + totalImageCount);
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

    private static File parseArgs(String[] args) {

        String path;

        // Verify usage
        try {
            path = args[0];
        } catch (IndexOutOfBoundsException e) {
            System.out.println("Incorrect usage. Follow:\n\tjava -jar vars-summary-1.0.jar path/to/concept_list.txt");
            return null;
        }

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

            ArrayList<String> concepts = new ArrayList<String>();

            String concept;
            while ((concept = reader.readLine()) != null) concepts.add(concept);

            return concepts;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    private static ArrayList<ResultSet> runQueries(ArrayList<String> concepts) {

        ArrayList<ResultSet> resultSets = new ArrayList<ResultSet>();

        Query mainQuery = new Query();

        for (String concept : concepts) {

            System.out.println("Querying " + concept);

            // Add concept names to query
            String annotationsStatement = insertAt(ANNOTATIONS_BASE_STATEMENT, concept, ANNOTATIONS_BASE_STATEMENT.indexOf('\'') + 1);
            annotationsStatement = insertAt(annotationsStatement, concept, annotationsStatement.indexOf('%') - 1);
            String qualityImageAnnotationsStatement = insertAt(QUALITYIMAGEANNOTATIONS_BASE_STATEMENT, concept, QUALITYIMAGEANNOTATIONS_BASE_STATEMENT.indexOf('\'') + 1);
            qualityImageAnnotationsStatement = insertAt(qualityImageAnnotationsStatement, concept, qualityImageAnnotationsStatement.indexOf('%') - 1);

            String[] statements = {
                    annotationsStatement,
                    qualityImageAnnotationsStatement
            };

            for (String statement : statements) {
                mainQuery.setStatement(statement);
                resultSets.add(mainQuery.executeStatement());
            }

        }

        return resultSets;
    }

    private static String insertAt(String str, String insert, int index) {
        return (new StringBuilder(str).insert(index, insert)).toString();
    }

    private static ArrayList<String> getAllAtLevel(String level) {

        Query getAll = new Query("SELECT ConceptName FROM Annotations WHERE Image IS NOT NULL GROUP BY ConceptName ORDER BY ConceptName ASC");
        ResultSet result = getAll.executeStatement();

        ArrayList<String> conceptList = new ArrayList<String>();

        // Build concept list
        try {
            while (result.next()) {
                String concept = result.getString("ConceptName");
                conceptList.add(concept);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        ArrayList<String> levelList = new ArrayList<String>();

        // Build level list
        for (String concept : conceptList) {

            String link = "http://m3.shore.mbari.org/kb/v1/phylogeny/down/" + concept;

            // Fix links with spaces
            while (link.contains(" ")) link = link.replace(" ", "%20");

            boolean isAtLevel = false;

            try {

                // Establish connection
                URL conceptPhylogenyURL = new URL(link);
                URLConnection connection = conceptPhylogenyURL.openConnection();

                // Establish input stream
                InputStream inputStream = connection.getInputStream();

                // Read lines to ArrayList
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String json = "";
                String line;
                while ((line = reader.readLine()) != null) json += line;

                // Check if at level
                int rankIndex = json.indexOf("rank") + 8;
                if (json.substring(rankIndex, rankIndex + level.length()).equals(level)) isAtLevel = true;

            } catch (IOException e) {
                System.out.println("Error: Could not retrieve " + link + ", removing concept \"" + concept + "\" from results.");
                isAtLevel = false;
            }

            // Add if at desired level
            if (isAtLevel) {
                levelList.add(concept);
            }
        }

        return levelList;

    }

}
