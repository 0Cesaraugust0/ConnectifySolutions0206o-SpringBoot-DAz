package com.connectify.controller;

import com.connectify.entity.Event;
import com.connectify.entity.EventStatus;
import com.connectify.repository.EventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
public class SeoController {

    private static final DateTimeFormatter SITEMAP_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final List<String> PUBLIC_PAGES = List.of(
            "/events", "/help", "/terms", "/privacy", "/cookies"
    );

    private final EventRepository eventRepository;
    private final String baseUrl;

    public SeoController(EventRepository eventRepository,
                         @Value("${seo.base-url:https://www.showyouplay.com}") String baseUrl) {
        this.eventRepository = eventRepository;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        String body = """
                User-agent: *
                Allow: /events
                Allow: /help
                Allow: /terms
                Allow: /privacy
                Allow: /cookies
                Disallow: /dashboard/
                Disallow: /admin/
                Disallow: /communications/
                Disallow: /cart/
                Disallow: /payments/
                Disallow: /tickets/
                Disallow: /setup
                Disallow: /prototype/

                Sitemap: %s/sitemap.xml
                """.formatted(baseUrl);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                """);

        PUBLIC_PAGES.forEach(path -> appendUrl(xml, baseUrl + path, null));
        eventRepository.findByStatusOrderByUpdatedAtDesc(EventStatus.PUBLISHED)
                .forEach(event -> appendUrl(xml, baseUrl + "/events/" + event.getId(), event));
        xml.append("</urlset>\n");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xml.toString());
    }

    private void appendUrl(StringBuilder xml, String location, Event event) {
        xml.append("  <url>\n")
                .append("    <loc>").append(location).append("</loc>\n");
        if (event != null && event.getUpdatedAt() != null) {
            xml.append("    <lastmod>")
                    .append(event.getUpdatedAt().format(SITEMAP_DATE))
                    .append("</lastmod>\n");
        }
        xml.append("  </url>\n");
    }
}
