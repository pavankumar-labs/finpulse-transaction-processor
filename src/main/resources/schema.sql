create table if not exists companies(
    id bigint auto_increment primary key,
    company_code varchar(50) not null unique,
    company_name varchar(255) not null,
    api_hash_code varchar(255) not null unique,
    company_url varchar(512),
    webhook_secret varchar(255),
    contact_email varchar(255) not null unique,
    company_status varchar(20) not null,
    created_at timestamp default current_timestamp
    );

create table if not exists transactions(
    id bigint auto_increment primary key,
    transaction_id varchar(50) not null unique,
    company_id bigint not null,
    sender_account varchar(50) not null,
    receiver_account varchar(50) not null,
    amount decimal(15,2) not null,
    transaction_type varchar(20) not null,
    transaction_time datetime not null,
    status varchar(20) not null,
    file_name varchar(256) not null,
    created_at timestamp default current_timestamp,
    foreign key (company_id) references companies(id)

);


create table if not exists company_decisions(
    id bigint auto_increment primary key,
    company_id bigint not null unique,
    admin_id bigint not null,
    decision varchar(20) not null,
    reason varchar(512),
    decided_at timestamp not null,
    foreign key (company_id) references companies(id)
    );

create table if not exists rejected_transactions(
    id bigint auto_increment primary key,
    company_id bigint not null,
    file_processing_id varchar(100) not null,
    file_name varchar(256) not null,
    raw_line varchar(1000) not null,
    reason varchar(50) not null,
    transaction_id varchar(50),
    status varchar(20) not null,
    rejected_at timestamp not null,
    resolved_at timestamp null,
    foreign key (company_id) references companies(id)
    );

create table if not exists uploaded_files(
    id bigint auto_increment primary key,
    company_id bigint not null,
    file_processing_id varchar(100) not null,
    file_hash varchar(64) not null,
    total_chunks int,
    completed_chunks int not null default 0,
    uploaded_at timestamp not null,
    foreign key (company_id) references companies(id)
    );

create table if not exists notifications(
    id bigint auto_increment primary key,
    company_id bigint not null,
    type varchar(50) not null,
    message varchar(500) not null,
    reference_id varchar(100) not null,
    viewed boolean not null default false,
    created_at timestamp not null,
    foreign key (company_id) references companies(id)
    );

create table if not exists company_users(
    id bigint auto_increment primary key,
    company_id bigint not null,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    role varchar(20) not null,
    must_change_password boolean not null default true,
    credential_expires_at timestamp null,
    created_at timestamp default current_timestamp,
    foreign key (company_id) references companies(id)
    );


create table if not exists company_password_reset_tokens(
    id bigint auto_increment primary key,
    company_user_id bigint not null,
    token_hash varchar(255) not null unique,
    used boolean not null default false,
    expires_at timestamp not null,
    created_at timestamp default current_timestamp,
    foreign key (company_user_id) references company_users(id)
    );
create table if not exists refresh_tokens(
    id bigint auto_increment primary key,
    subject_type varchar(20) not null,
    subject_id bigint not null,
    token_hash varchar(255) not null unique,
    expires_at timestamp not null,
    revoked boolean not null default false,
    created_at timestamp default current_timestamp
    );

create index if not exists idx_refreshtoken_tokenhash on refresh_tokens(token_hash);
create index if not exists idx_companyuser_company on company_users(company_id);
create index if not exists idx_resettoken_tokenhash on company_password_reset_tokens(token_hash);
create index if not exists idx_notification_company_viewed on notifications(company_id, viewed);

create index if not exists idx_uploaded_company_hash on uploaded_files(company_id, file_hash);

create index if not exists idx_rejected_company_status on rejected_transactions(company_id, status);
create index if not exists idx_rejected_company_txnid on rejected_transactions(company_id, transaction_id);

create index if not exists idx_transaction_time_amount on transactions(transaction_time,amount);
create index if not exists idx_transaction_company on transactions(company_id);