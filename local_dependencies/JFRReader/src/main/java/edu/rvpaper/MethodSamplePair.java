package edu.rvpaper;

import edu.rvpaper.classifier.Category;

public class MethodSamplePair {
    public String method;
    public Integer sample;
    public Category category;

    public MethodSamplePair(String method, Integer sample) {
        this.method = method;
        this.sample = sample;
    }

    public MethodSamplePair(Category category, Integer sample) {
        this.method = "";
        this.category = category;
        this.sample = sample;
    }

    public Category getCategory() {
        if (category == null) {
            return Category.UNKNOWN;
        }

        return category;
    }

    public String toString() {
        if (category == null) {
            return "(" + this.sample * 0.005 + "s)  " + this.method;
        } else {
            return "(" + this.sample * 0.005 + "s)  " + "(" + category + ")  " + this.method;
        }
    }
}
