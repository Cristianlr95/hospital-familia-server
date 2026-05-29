create table password_reset_tokens (
    id bigserial primary key,
    token_hash varchar(128) not null unique,
    user_id bigint not null references app_users(id) on delete cascade,
    expires_at timestamp with time zone not null,
    used_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_password_reset_tokens_token_hash on password_reset_tokens(token_hash);
create index idx_password_reset_tokens_user_id on password_reset_tokens(user_id);
