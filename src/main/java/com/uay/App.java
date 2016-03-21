package com.uay;

import com.uay.model.Post;
import com.uay.scraper.Scraper;

import java.util.List;

public class App {

    public static void main(String[] args) {
        Scraper scraper = new Scraper();
        List<Post> posts = scraper.parse();
        System.out.println("posts = " + posts.size());
    }
}
