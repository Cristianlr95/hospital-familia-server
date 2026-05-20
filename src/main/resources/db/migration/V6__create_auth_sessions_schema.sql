create table auth_sessions (
    id bigserial primary key,
    session_id uuid not null unique,
    user_id bigint not null references app_users(id) on delete cascade,
    token_type varchar(20) not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_auth_sessions_user_id on auth_sessions(user_id);
create index idx_auth_sessions_session_id on auth_sessions(session_id);
