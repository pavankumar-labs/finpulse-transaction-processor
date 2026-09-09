

CREATE TABLE refresh_tokens (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                subject_type VARCHAR(50) NOT NULL,
                                subject_id BIGINT NOT NULL,
                                token_hash VARCHAR(255) NOT NULL UNIQUE,
                                expires_at DATETIME NOT NULL,
                                revoked BOOLEAN NOT NULL,
                                created_at DATETIME
);
