CREATE TABLE companies (
                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                           company_code VARCHAR(255) NOT NULL UNIQUE,
                           company_name VARCHAR(255) NOT NULL,
                           api_hash_code VARCHAR(255) UNIQUE,
                           company_url VARCHAR(255),
                           company_status VARCHAR(50) NOT NULL,
                           contact_email VARCHAR(255) NOT NULL UNIQUE,
                           created_at DATETIME
);


CREATE TABLE admins (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        email VARCHAR(255) NOT NULL UNIQUE,
                        password_hash VARCHAR(255) NOT NULL,
                        role VARCHAR(50) NOT NULL,
                        must_change_password BOOLEAN NOT NULL,
                        credential_expires_at DATETIME,
                        created_at DATETIME
);

CREATE TABLE company_users (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               company_id BIGINT NOT NULL,
                               email VARCHAR(255) NOT NULL UNIQUE,
                               password_hash VARCHAR(255) NOT NULL,
                               role VARCHAR(50) NOT NULL,
                               must_change_password BOOLEAN NOT NULL,
                               credential_expires_at DATETIME,
                               created_at DATETIME,

                               CONSTRAINT fk_company_users_company
                                   FOREIGN KEY (company_id)
                                       REFERENCES companies(id)
);

CREATE TABLE companies_decisions (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     company_id BIGINT NOT NULL UNIQUE,
                                     admin_id BIGINT NOT NULL,
                                     decision VARCHAR(50) NOT NULL,
                                     reason VARCHAR(255),
                                     decided_at DATETIME NOT NULL,

                                     CONSTRAINT fk_company_decisions_company
                                         FOREIGN KEY (company_id)
                                             REFERENCES companies(id),

                                     CONSTRAINT fk_company_decisions_admin
                                         FOREIGN KEY (admin_id)
                                             REFERENCES admins(id)
);

CREATE TABLE admin_password_reset_tokens (
                                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             admin_id BIGINT NOT NULL,
                                             token_hash VARCHAR(255) NOT NULL UNIQUE,
                                             used BOOLEAN NOT NULL,
                                             expires_at DATETIME NOT NULL,
                                             created_at DATETIME NOT NULL,

                                             CONSTRAINT fk_admin_password_reset_tokens_admin
                                                 FOREIGN KEY (admin_id)
                                                     REFERENCES admins(id)
);

CREATE TABLE company_password_reset_tokens (
                                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                               company_user_id BIGINT NOT NULL,
                                               token_hash VARCHAR(255) NOT NULL UNIQUE,
                                               used BOOLEAN NOT NULL,
                                               expires_at DATETIME NOT NULL,
                                               created_at DATETIME NOT NULL,

                                               CONSTRAINT fk_company_password_reset_tokens_user
                                                   FOREIGN KEY (company_user_id)
                                                       REFERENCES company_users(id)
);

CREATE TABLE uploaded_files (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                company_id BIGINT NOT NULL,
                                file_processing_id VARCHAR(255) NOT NULL UNIQUE,
                                file_hash VARCHAR(64) NOT NULL,
                                total_chunks INT,
                                completed_chunks INT NOT NULL,
                                uploaded_at DATETIME NOT NULL,
                                completion_event_published BOOLEAN NOT NULL DEFAULT FALSE,

                                UNIQUE (company_id, file_hash),

                                CONSTRAINT fk_uploaded_files_company
                                    FOREIGN KEY (company_id)
                                        REFERENCES companies(id)
);


CREATE TABLE transactions (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,

                              transaction_id VARCHAR(255) NOT NULL UNIQUE,

                              sender_account VARCHAR(255) NOT NULL,
                              receiver_account VARCHAR(255) NOT NULL,

                              amount DECIMAL(15,2) NOT NULL,

                              transaction_time DATETIME NOT NULL,

                              file_name VARCHAR(255),

                              file_processing_id VARCHAR(255) NOT NULL,

                              company_id BIGINT NOT NULL,

                              sender_account_type VARCHAR(50) NOT NULL,
                              receiver_account_type VARCHAR(50) NOT NULL,

                              INDEX idx_transactions_file_processing_id (file_processing_id),

                              INDEX idx_transactions_sender_history
                                  (company_id, sender_account, transaction_time),

                              INDEX idx_transactions_receiver_history
                                  (company_id, receiver_account, transaction_time),

                              CONSTRAINT fk_transactions_company
                                  FOREIGN KEY (company_id)
                                      REFERENCES companies(id)
);

CREATE TABLE rejected_transactions (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                       company_id BIGINT NOT NULL,
                                       file_processing_id VARCHAR(255) NOT NULL,

                                       file_name VARCHAR(255) NOT NULL,
                                       raw_line VARCHAR(1000) NOT NULL,
                                       reason VARCHAR(255) NOT NULL,

                                       transaction_id VARCHAR(255),

                                       status VARCHAR(50) NOT NULL,

                                       rejected_at DATETIME NOT NULL,
                                       resolved_at DATETIME,

                                       INDEX idx_rejected_transactions_company_status_file
                                           (company_id, status, file_processing_id),

                                       CONSTRAINT fk_rejected_transactions_company
                                           FOREIGN KEY (company_id)
                                               REFERENCES companies(id)
);

CREATE TABLE notifications (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,

                               company_id BIGINT NOT NULL,

                               message VARCHAR(500) NOT NULL,

                               reference_id VARCHAR(255) NOT NULL ,

                               viewed BOOLEAN NOT NULL,

                               created_at DATETIME NOT NULL,

                               INDEX idx_notifications_company_created
                                   (company_id, created_at),

                               CONSTRAINT fk_notifications_company
                                   FOREIGN KEY (company_id)
                                       REFERENCES companies(id)
);


CREATE TABLE fraud_findings (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                company_id BIGINT NOT NULL,
                                file_processing_id VARCHAR(255) NOT NULL,
                                account_number VARCHAR(255) NOT NULL,

                                risk_level VARCHAR(50) NOT NULL,
                                triggered_rule_codes VARCHAR(500) NOT NULL,
                                reason VARCHAR(2000) NOT NULL,

                                status VARCHAR(50) NOT NULL,

                                resolved_at DATETIME,
                                created_at DATETIME,

                                INDEX idx_fraud_findings_company_status_created
                                    (company_id, status, created_at),

                                INDEX idx_fraud_findings_company_file_status_created
                                    (company_id, file_processing_id, status, created_at),

                                CONSTRAINT fk_fraud_findings_company
                                    FOREIGN KEY (company_id)
                                        REFERENCES companies(id)
);
