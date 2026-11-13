package com.connectify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_presentation_settings")
public class EventPresentationSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private Event event;

    @Column(length = 20)
    private String primaryColor = "#4f46e5";

    @Column(length = 20)
    private String accentColor = "#8b5cf6";

    @Column(length = 500)
    private String highlightText;

    @Column(columnDefinition = "TEXT")
    private String coverImageUrl;

    @Column(columnDefinition = "TEXT")
    private String thumbnailImageUrl;

    @Column(columnDefinition = "TEXT")
    private String promotionalImageUrl;

    @Column(columnDefinition = "TEXT")
    private String backgroundImageUrl;

    @Column(columnDefinition = "TEXT")
    private String stageImageUrl;

    @Column(length = 180)
    private String organizerBrandName;

    @Column(columnDefinition = "TEXT")
    private String organizerLogoUrl;

    @Column(columnDefinition = "TEXT")
    private String sponsorLogosText;

    @Column(length = 20)
    private String coverFocus = "CENTER";

    @Column(length = 20)
    private String contentFocus = "BALANCED";

    @Column(length = 20)
    private String infoPosition = "STANDARD";

    private boolean showGallery = true;

    private boolean showOrganizer = true;

    private boolean showAgenda = false;

    @Column(columnDefinition = "TEXT")
    private String agendaText;

    private boolean showBenefits = false;

    @Column(columnDefinition = "TEXT")
    private String benefitsText;

    private boolean showSponsors = false;

    @Column(columnDefinition = "TEXT")
    private String sponsorsText;

    private boolean showMedia = false;

    @Column(columnDefinition = "TEXT")
    private String youtubeUrl;

    @Column(columnDefinition = "TEXT")
    private String spotifyUrl;

    private boolean showStageMap = false;

    @Column(columnDefinition = "TEXT")
    private String stageText;

    private boolean showSessions = false;

    @Column(columnDefinition = "TEXT")
    private String sessionText;

    @Column(length = 120)
    private String tourEventIds;

    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; touch(); }
    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; touch(); }
    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; touch(); }
    public String getHighlightText() { return highlightText; }
    public void setHighlightText(String highlightText) { this.highlightText = highlightText; touch(); }
    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; touch(); }
    public String getThumbnailImageUrl() { return thumbnailImageUrl; }
    public void setThumbnailImageUrl(String thumbnailImageUrl) { this.thumbnailImageUrl = thumbnailImageUrl; touch(); }
    public String getPromotionalImageUrl() { return promotionalImageUrl; }
    public void setPromotionalImageUrl(String promotionalImageUrl) { this.promotionalImageUrl = promotionalImageUrl; touch(); }
    public String getBackgroundImageUrl() { return backgroundImageUrl; }
    public void setBackgroundImageUrl(String backgroundImageUrl) { this.backgroundImageUrl = backgroundImageUrl; touch(); }
    public String getStageImageUrl() { return stageImageUrl; }
    public void setStageImageUrl(String stageImageUrl) { this.stageImageUrl = stageImageUrl; touch(); }
    public String getOrganizerBrandName() { return organizerBrandName; }
    public void setOrganizerBrandName(String organizerBrandName) { this.organizerBrandName = organizerBrandName; touch(); }
    public String getOrganizerLogoUrl() { return organizerLogoUrl; }
    public void setOrganizerLogoUrl(String organizerLogoUrl) { this.organizerLogoUrl = organizerLogoUrl; touch(); }
    public String getSponsorLogosText() { return sponsorLogosText; }
    public void setSponsorLogosText(String sponsorLogosText) { this.sponsorLogosText = sponsorLogosText; touch(); }
    public String getCoverFocus() { return coverFocus; }
    public void setCoverFocus(String coverFocus) { this.coverFocus = coverFocus; touch(); }
    public String getContentFocus() { return contentFocus; }
    public void setContentFocus(String contentFocus) { this.contentFocus = contentFocus; touch(); }
    public String getInfoPosition() { return infoPosition; }
    public void setInfoPosition(String infoPosition) { this.infoPosition = infoPosition; touch(); }
    public boolean isShowGallery() { return showGallery; }
    public void setShowGallery(boolean showGallery) { this.showGallery = showGallery; touch(); }
    public boolean isShowOrganizer() { return showOrganizer; }
    public void setShowOrganizer(boolean showOrganizer) { this.showOrganizer = showOrganizer; touch(); }
    public boolean isShowAgenda() { return showAgenda; }
    public void setShowAgenda(boolean showAgenda) { this.showAgenda = showAgenda; touch(); }
    public String getAgendaText() { return agendaText; }
    public void setAgendaText(String agendaText) { this.agendaText = agendaText; touch(); }
    public boolean isShowBenefits() { return showBenefits; }
    public void setShowBenefits(boolean showBenefits) { this.showBenefits = showBenefits; touch(); }
    public String getBenefitsText() { return benefitsText; }
    public void setBenefitsText(String benefitsText) { this.benefitsText = benefitsText; touch(); }
    public boolean isShowSponsors() { return showSponsors; }
    public void setShowSponsors(boolean showSponsors) { this.showSponsors = showSponsors; touch(); }
    public String getSponsorsText() { return sponsorsText; }
    public void setSponsorsText(String sponsorsText) { this.sponsorsText = sponsorsText; touch(); }
    public boolean isShowMedia() { return showMedia; }
    public void setShowMedia(boolean showMedia) { this.showMedia = showMedia; touch(); }
    public String getYoutubeUrl() { return youtubeUrl; }
    public void setYoutubeUrl(String youtubeUrl) { this.youtubeUrl = youtubeUrl; touch(); }
    public String getSpotifyUrl() { return spotifyUrl; }
    public void setSpotifyUrl(String spotifyUrl) { this.spotifyUrl = spotifyUrl; touch(); }
    public boolean isShowStageMap() { return showStageMap; }
    public void setShowStageMap(boolean showStageMap) { this.showStageMap = showStageMap; touch(); }
    public String getStageText() { return stageText; }
    public void setStageText(String stageText) { this.stageText = stageText; touch(); }
    public boolean isShowSessions() { return showSessions; }
    public void setShowSessions(boolean showSessions) { this.showSessions = showSessions; touch(); }
    public String getSessionText() { return sessionText; }
    public void setSessionText(String sessionText) { this.sessionText = sessionText; touch(); }
    public String getTourEventIds() { return tourEventIds; }
    public void setTourEventIds(String tourEventIds) { this.tourEventIds = tourEventIds; touch(); }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    private void touch() { this.updatedAt = LocalDateTime.now(); }
}
