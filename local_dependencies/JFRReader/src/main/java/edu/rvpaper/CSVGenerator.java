package edu.rvpaper;

import edu.rvpaper.classifier.Category;

import java.io.*;
import java.util.HashMap;
import java.util.List;

public class CSVGenerator {
    public void output(String fileName, List<MethodSamplePair> pairs, Category[] categories, String[] messages) throws FileNotFoundException, UnsupportedEncodingException {
        HashMap<Category, Integer> counter = new HashMap<>();
        for (MethodSamplePair pair : pairs) {
            Category category = pair.getCategory();
            counter.put(category, counter.getOrDefault(category, 0) + pair.sample);
        }

        PrintWriter writer = new PrintWriter(fileName, "UTF-8");

        StringBuilder header = new StringBuilder();
        StringBuilder result = new StringBuilder();
        for (Category category : categories) {
            header.append(category).append(",");
            result.append(counter.getOrDefault(category, 0)).append(",");
        }

        writer.println(header);
        writer.println(result);

        if (messages != null)
            for (String message : messages)
                writer.println(message);

        writer.close();
    }

    public void outputHotMethods(String fileName, List<MethodSamplePair> pairs) throws FileNotFoundException, UnsupportedEncodingException {
        PrintWriter writer = new PrintWriter(fileName, "UTF-8");
        writer.println("method,category,time");

        for (MethodSamplePair pair : pairs) {
            writer.println(pair.method + "," + pair.getCategory() + "," + pair.sample);
        }

        writer.close();

    }
}
