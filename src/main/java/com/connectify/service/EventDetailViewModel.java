package com.connectify.service;

import com.connectify.entity.Event;
import com.connectify.entity.EventPresentationSettings;
import com.connectify.repository.TicketTypeRepository;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

@Component
public class EventDetailViewModel {

    private final TicketTypeRepository ticketTypeRepository;

    public EventDetailViewModel(TicketTypeRepository ticketTypeRepository) {
        this.ticketTypeRepository = ticketTypeRepository;
    }

    public void addAttributes(Event event, EventPresentationSettings presentation, Model model) {
        model.addAttribute("event", event);
        model.addAttribute("ticketTypes", ticketTypeRepository.findByEventIdAndActiveTrueOrderByPriceAsc(event.getId()));
        model.addAttribute("presentation", presentation);
        model.addAttribute("coverImageUrl", firstText(presentation == null ? null : presentation.getCoverImageUrl(), event.getImageUrl()));
        model.addAttribute("thumbnailImageUrl", firstText(presentation == null ? null : presentation.getThumbnailImageUrl(), event.getImageUrl()));
        model.addAttribute("backgroundImageUrl", firstText(presentation == null ? null : presentation.getBackgroundImageUrl(), ""));
        model.addAttribute("stageImageUrl", firstText(presentation == null ? null : presentation.getStageImageUrl(), ""));
        model.addAttribute("organizerBrandName", firstText(presentation == null ? null : presentation.getOrganizerBrandName(), event.getOrganizerName()));
        model.addAttribute("organizerLogoUrl", firstText(presentation == null ? null : presentation.getOrganizerLogoUrl(), ""));
        model.addAttribute("youtubeEmbedUrl", embedYoutube(presentation == null ? null : presentation.getYoutubeUrl()));
        model.addAttribute("spotifyEmbedUrl", embedSpotify(presentation == null ? null : presentation.getSpotifyUrl()));
        model.addAttribute("presentationStyle", styleFor(presentation));
    }

    private String firstText(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        return fallback == null ? "" : fallback;
    }

    private String styleFor(EventPresentationSettings presentation) {
        if (presentation == null) return "";
        String background = safeBackground(presentation.getBackgroundImageUrl());
        return "--market-primary:" + safeColor(presentation.getPrimaryColor(), "#4338ca") + ";"
                + "--market-accent:" + safeColor(presentation.getAccentColor(), "#7c3aed") + ";"
                + "--market-cover-position:" + coverPosition(presentation.getCoverFocus()) + ";"
                + (background.isBlank() ? "" : "--market-background-image:url('" + background + "');");
    }

    private String safeColor(String color, String fallback) {
        return color != null && color.matches("#[0-9a-fA-F]{6}") ? color : fallback;
    }

    private String coverPosition(String value) {
        if ("TOP".equals(value)) return "50% 18%";
        if ("BOTTOM".equals(value)) return "50% 82%";
        return "50% 50%";
    }

    private String safeBackground(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim();
        return normalized.startsWith("https://") || normalized.startsWith("http://") || normalized.startsWith("/uploads/")
                || normalized.startsWith("/images/showcase/")
                ? normalized.replace("'", "%27") : "";
    }

    private String embedSpotify(String value) {
        if (value == null || value.isBlank()) return "";
        String url = value.trim();
        if (!url.startsWith("https://open.spotify.com/")) return "";
        String clean = url.split("\\?")[0].replace("https://open.spotify.com/", "https://open.spotify.com/embed/");
        return clean + "?utm_source=generator";
    }

    private String embedYoutube(String value) {
        if (value == null || value.isBlank()) return "";
        String url = value.trim();
        String id = "";
        int watchIndex = url.indexOf("watch?v=");
        if (watchIndex >= 0) {
            id = url.substring(watchIndex + 8).split("&")[0];
        } else if (url.startsWith("https://youtu.be/")) {
            id = url.substring("https://youtu.be/".length()).split("\\?")[0];
        } else if (url.startsWith("https://www.youtube.com/embed/")) {
            id = url.substring("https://www.youtube.com/embed/".length()).split("\\?")[0];
        }
        return id.isBlank() ? "" : "https://www.youtube.com/embed/" + id;
    }
}
