package com.smartgrievance.testsupport;

import java.io.UnsupportedEncodingException;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.test.web.servlet.MvcResult;

public record Html(Document document) {
    public static Html of(MvcResult result) throws UnsupportedEncodingException {
        return of(result.getResponse().getContentAsString());
    }

    public static Html of(String markup) { return new Html(Jsoup.parse(markup)); }

    public String text(String selector) { return document.select(selector).text(); }
    public List<String> links() { return document.select("a[href]").eachAttr("href"); }
    public List<String> buttons() { return document.select("button").eachText(); }
    public List<String> ids() { return document.select("[id]").stream().map(Element::id).toList(); }
    public List<String> errorSummary() { return document.select(".error-summary a").eachText(); }
    public boolean hasNoForm() { return document.select("form").isEmpty(); }
}
