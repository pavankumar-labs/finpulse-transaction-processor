create table if not exists transactions(
    id bigint auto_increment primary key,
    transaction_id varchar(50) not null unique,
    sender_account varchar(50) not null,
    receiver_account varchar(50) not null,
    amount decimal(15,2) not null,
    transaction_type varchar(20) not null,
    transaction_time datetime not null,
    status varchar(20) not null,
    file_name varchar(256) not null,
    created_at timestamp default current_timestamp

);


create index idx_transactoon_time_amount on transactions(transaction_time,amount);