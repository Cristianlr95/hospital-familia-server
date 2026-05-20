create table notification_preferences (
    id bigserial primary key,
    user_id bigint not null unique references app_users(id) on delete cascade,
    state_changes_enabled boolean not null,
    events_enabled boolean not null,
    linking_updates_enabled boolean not null,
    quiet_hours_enabled boolean not null,
    quiet_hours_start varchar(5),
    quiet_hours_end varchar(5),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_notification_preferences_user_id on notification_preferences(user_id);
