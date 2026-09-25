package com.astropaper.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "posts")
public class PostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180, unique = true)
    private String slug;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "content_markdown", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String contentMarkdown;

    @Column(name = "cover_image_url", length = 2048)
    private String coverImageUrl;

    @Column(nullable = false, length = 20)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private UserEntity author;

    @Column(name = "author_name", length = 100)
    private String authorName;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "modified_at")
    private Instant modifiedAt;

    @Column(length = 64)
    private String timezone;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "canonical_url", length = 2048)
    private String canonicalUrl;

    @Column(name = "og_image_url", length = 2048)
    private String ogImageUrl;

    @Column(name = "hide_edit_post", nullable = false)
    private boolean hideEditPost;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "post_tags",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<TagEntity> tags = new HashSet<>();

    protected PostEntity() {
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getContentMarkdown() { return contentMarkdown; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public String getStatus() { return status; }
    public UserEntity getAuthor() { return author; }
    public String getAuthorName() { return authorName; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getModifiedAt() { return modifiedAt; }
    public String getTimezone() { return timezone; }
    public boolean isFeatured() { return featured; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public String getOgImageUrl() { return ogImageUrl; }
    public boolean isHideEditPost() { return hideEditPost; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<TagEntity> getTags() { return tags; }
}
