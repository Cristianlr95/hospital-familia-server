create table notifications (
    id bigserial primary key,
    recipient_user_id bigint not null references app_users(id) on delete cascade,
    patient_id bigint references patients(id) on delete cascade,
    type varchar(40) not null,
    title varchar(140) not null,
    message varchar(500) not null,
    read_at timestamp with time zone,
    created_at timestamp with time zone not null
);

create index idx_notifications_recipient_created on notifications(recipient_user_id, created_at desc);
create index idx_notifications_patient_id on notifications(patient_id);
