package com.uay.scraper;

import com.uay.model.Post;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Scraper {

    private static final Logger logger = LoggerFactory.getLogger(Scraper.class);
    public static final String BASE_URL = "http://myblog";
    public static final String URL_TO_PARSE = BASE_URL + "/posts";
    public static final int START_PAGE = 1;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.2; WOW64; rv:44.0) Gecko/20100101 Firefox/44.0";
    private long length;
    private long words;
    private final Lock lock = new ReentrantLock();

    public List<Post> parse() {
        long start = System.currentTimeMillis();
        initializeData();
        logger.info("Start parsing of all blog posts at " + URL_TO_PARSE);
        List<Post> posts = IntStream.rangeClosed(START_PAGE, getLastPageNumber(URL_TO_PARSE))
                .parallel()
                .mapToObj(page -> parseArticles(URL_TO_PARSE + "/page/" + page))
                .flatMap(List::stream)
                .collect(Collectors.toList());
        logger.info("Parsing finished[length = " + length + "; words = " + words +
                "; time = " + (System.currentTimeMillis() - start) + "]");
        return posts;
    }

    private void initializeData() {
        length = 0;
        words = 0;
    }

    private int getLastPageNumber(String url) {
        try {
            Document doc = Jsoup.connect(url).get();
            return Integer.parseInt(doc.select("div.paging > a").last().text());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Post> parseArticles(String url) {
        try {
            url = getAbsoluteUrl(url);
            logger.debug("Parsing article at " + url);
            Document doc = getJsoupDocument(url);
            return getPageArticles(doc)
                    .stream()
                    .map(article -> parseArticle(article.attr("href")))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Document getJsoupDocument(String url) throws IOException {
        return Jsoup.connect(url).userAgent(USER_AGENT).timeout(2000)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .maxBodySize(1024 * 1024 * 3).get();
    }

    private Elements getPageArticles(Document doc) {
        return doc.select("div.posts a");
    }

    private Post parseArticle(String url) {
        url = getAbsoluteUrl(url);
        try {
            Document doc = getJsoupDocument(url);
            Post post = new Post();
            post.setDate(doc.select("div.post-info .date").text());
            post.setAuthor(doc.select("div.post-info div.author a").text());
            post.setTitle(doc.title());
            post.setBody(doc.select("article.typo").text());

            logger.debug(post.toString());
            updateStatistics(post.getBody());
            return post;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateStatistics(String body) {
        lock.lock();
        try {
            long bodyWords = body.trim().split("\\s+").length;
            logger.debug("Number of words in the body: " + bodyWords);
            words += bodyWords;
            length += body.length();
        } finally {
            lock.unlock();
        }
    }

    private String getAbsoluteUrl(String url) {
        if (url.startsWith("/")) {
            url = BASE_URL + url;
        }
        return url;
    }
}
