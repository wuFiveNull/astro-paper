ALTER TABLE posts
    ADD COLUMN author_name VARCHAR(100) NULL AFTER author_id,
    ADD COLUMN modified_at TIMESTAMP(6) NULL AFTER published_at,
    ADD COLUMN timezone VARCHAR(64) NULL AFTER modified_at,
    ADD COLUMN featured BOOLEAN NOT NULL DEFAULT FALSE AFTER timezone,
    ADD COLUMN canonical_url VARCHAR(2048) NULL AFTER featured,
    ADD COLUMN og_image_url VARCHAR(2048) NULL AFTER canonical_url,
    ADD COLUMN hide_edit_post BOOLEAN NOT NULL DEFAULT FALSE AFTER og_image_url;
