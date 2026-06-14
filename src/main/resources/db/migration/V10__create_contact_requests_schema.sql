create table contact_requests (
    id bigserial primary key,
    tutor_user_id bigint not null references app_users(id) on delete cascade,
    patient_id bigint not null references patients(id) on delete cascade,
    message varchar(500) not null,
    status varchar(20) not null,
    resolution_note varchar(500),
    resolved_by_user_id bigint references app_users(id) on delete set null,
    resolved_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_contact_requests_status_created on contact_requests(status, created_at desc);
create index idx_contact_requests_tutor_created on contact_requests(tutor_user_id, created_at desc);
create index idx_contact_requests_patient_id on contact_requests(patient_id);
