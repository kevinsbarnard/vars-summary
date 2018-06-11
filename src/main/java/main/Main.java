package main;

import sql.Query;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class Main {

    private static final String
            ANNOTATIONS_BASE_STATEMENT =
            "SELECT ConceptName, count(*) AS Total " +
                    "FROM Annotations " +
                    "WHERE Image IS NOT NULL AND " +
                    "ConceptName LIKE '%' " +
                    "GROUP BY ConceptName " +
                    "ORDER BY len(ConceptName) ASC",
            QUALITYIMAGEANNOTATIONS_BASE_STATEMENT =
                    "SELECT ConceptName, count(*) AS Total " +
                            "FROM QualityImageAnnotations " +
                            "WHERE ImageReference IS NOT NULL AND " +
                            "ConceptName LIKE '%' " +
                            "GROUP BY ConceptName " +
                            "ORDER BY len(ConceptName) ASC";

    private static final int NUM_QUERIES = 2;

    public static void main(String[] args) {

        // Initialize Microsoft JDBC MSSQL driver
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            System.out.println("FATAL: JDBC MSSQL driver not found.");
            return;
        }

        File conceptList = parseArgs(args);

        if (conceptList == null) {
            System.out.println("\n--- Process failed. ---");
            return;
        }

        ArrayList<String> concepts = readList(conceptList);

        assert concepts != null;
        ArrayList<ResultSet> results = runQueries(concepts);

        displayResults(results, concepts);

        System.out.println("\n--- Process completed successfully. ---");

    }

    private static void displayResults(ArrayList<ResultSet> results, ArrayList<String> concepts) {

        System.out.println();

        for (int i = 0; i < results.size(); i += NUM_QUERIES) {

            ResultSet annotationsResult = results.get(i);
            ResultSet qualityAnnotationsResult = results.get(i + 1);

            String concept = concepts.get(i / NUM_QUERIES);

            System.out.println("Results for concept " + concept + ":");

            System.out.print("\t");
            System.out.println(
                    String.format("%1$40s%2$10s", "Quality", "All")
            );

            int sumTotalAnnotations = 0, sumTotalQualityAnnotations = 0;

            try {
                while (annotationsResult.next() && qualityAnnotationsResult.next()) {
                    System.out.print("\t");

                    int totalAnnotations = annotationsResult.getInt("Total");
                    int totalQualityAnnotations = qualityAnnotationsResult.getInt("Total");

                    System.out.println(
                            String.format(
                                    "%1$-30s%2$10d%3$10d",
                                    annotationsResult.getString("ConceptName"),
                                    totalQualityAnnotations,
                                    totalAnnotations
                            )
                    );

                    sumTotalAnnotations += totalAnnotations;
                    sumTotalQualityAnnotations += totalQualityAnnotations;
                }
            } catch (SQLException e) {
                System.out.println("Error in reading from result set " + i + ", concept " + concept);
            }

            System.out.print("\t");

            System.out.println(
                    String.format(
                            "%1$-30s%2$10d%3$10d",
                            "Total: ",
                            sumTotalAnnotations,
                            sumTotalQualityAnnotations
                    )
            );

            System.out.println();

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

            String[] statements = {
                    insertAt(ANNOTATIONS_BASE_STATEMENT, concept, ANNOTATIONS_BASE_STATEMENT.indexOf('%')),
                    insertAt(QUALITYIMAGEANNOTATIONS_BASE_STATEMENT, concept, QUALITYIMAGEANNOTATIONS_BASE_STATEMENT.indexOf('%'))
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

}
