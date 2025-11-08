-- Create and use the course database
CREATE DATABASE IF NOT EXISTS cs4297
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE cs4297;

-- Articles table = single source of truth from the crawler
CREATE TABLE IF NOT EXISTS articles (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        title VARCHAR(1024),
    content MEDIUMTEXT,
    tags VARCHAR(512),
    source_url VARCHAR(2048),
    crawled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB;

-- Helpful index for title searches
CREATE INDEX IF NOT EXISTS idx_articles_title ON articles(title);
