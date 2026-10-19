package com.connectify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    private LocalDateTime eventDate;

    private String location;

    private String city;

    private String organizerName;

    private String organizerEmail;

    private BigDecimal price;

    private Integer capacity;

    @Column(name = "tickets_sold")
    private Integer sold;

    private String imageUrl;

    private boolean featured;

    @Column(unique = true)
    private String gateAccessCode;

    private String gatePassword;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 40)
    private EventDesignTemplate designTemplate = EventDesignTemplate.CLASSIC;

    private boolean designEnabled = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    private EventStatus status = EventStatus.PUBLISHED;

    public Event() {
    }

    public Event(String title, String description, Category category, LocalDateTime eventDate,
                 String location, String city, BigDecimal price, Integer capacity,
                 Integer sold, String imageUrl, boolean featured, EventStatus status) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.eventDate = eventDate;
        this.location = location;
        this.city = city;
        this.price = price;
        this.capacity = capacity;
        this.sold = sold;
        this.imageUrl = imageUrl;
        this.featured = featured;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        touch();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        touch();
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
        touch();
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDateTime eventDate) {
        this.eventDate = eventDate;
        touch();
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
        touch();
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
        touch();
    }

    public String getOrganizerName() {
        return organizerName;
    }

    public void setOrganizerName(String organizerName) {
        this.organizerName = organizerName;
        touch();
    }

    public String getOrganizerEmail() {
        return organizerEmail;
    }

    public void setOrganizerEmail(String organizerEmail) {
        this.organizerEmail = organizerEmail;
        touch();
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
        touch();
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
        touch();
    }

    public Integer getSold() {
        return sold;
    }

    public void setSold(Integer sold) {
        this.sold = sold;
        touch();
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        touch();
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
        touch();
    }

    public String getGateAccessCode() {
        return gateAccessCode;
    }

    public void setGateAccessCode(String gateAccessCode) {
        this.gateAccessCode = gateAccessCode;
        touch();
    }

    public String getGatePassword() {
        return gatePassword;
    }

    public void setGatePassword(String gatePassword) {
        this.gatePassword = gatePassword;
        touch();
    }

    public EventDesignTemplate getDesignTemplate() {
        return designTemplate;
    }

    public void setDesignTemplate(EventDesignTemplate designTemplate) {
        this.designTemplate = designTemplate;
        touch();
    }

    public boolean isDesignEnabled() {
        return designEnabled;
    }

    public void setDesignEnabled(boolean designEnabled) {
        this.designEnabled = designEnabled;
        touch();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
        touch();
    }

    public Integer getAvailableTickets() {
        if (capacity == null || sold == null) {
            return 0;
        }
        return Math.max(capacity - sold, 0);
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
