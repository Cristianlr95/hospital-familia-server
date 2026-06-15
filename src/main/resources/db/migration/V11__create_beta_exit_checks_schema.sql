create table beta_exit_checks (
    id bigserial primary key,
    check_key varchar(80) not null unique,
    label varchar(160) not null,
    description varchar(500) not null,
    sort_order integer not null,
    completed boolean not null default false,
    notes varchar(500),
    completed_by_user_id bigint references app_users(id) on delete set null,
    completed_at timestamp with time zone,
    updated_at timestamp with time zone not null
);

create index idx_beta_exit_checks_sort_order on beta_exit_checks(sort_order);

insert into beta_exit_checks (check_key, label, description, sort_order, completed, updated_at) values
('LOGIN_TUTOR_DESKTOP', 'Login tutor desktop', 'Validar login tutor, dashboard y lectura inicial en navegador desktop.', 10, false, current_timestamp),
('LOGIN_STAFF_DESKTOP', 'Login staff desktop', 'Validar login staff, panel operativo y permisos staff en navegador desktop.', 20, false, current_timestamp),
('TUTOR_MOBILE_LAYOUT', 'Vista tutor mobile', 'Validar que dashboard tutor, notificaciones y contacto staff sean legibles en ancho mobile.', 30, false, current_timestamp),
('STAFF_MOBILE_LAYOUT', 'Vista staff mobile', 'Validar que panel staff, pacientes, solicitudes y eventos sean operables en ancho mobile.', 40, false, current_timestamp),
('LINKING_FLOW', 'Flujo vinculacion', 'Validar solicitud tutor, aprobacion staff y visibilidad de paciente aprobado.', 50, false, current_timestamp),
('STATUS_EVENTS_FLOW', 'Estado y calendario', 'Validar actualizacion staff de estado/eventos y lectura tutor autorizada.', 60, false, current_timestamp),
('NOTIFICATIONS_FLOW', 'Notificaciones in-app', 'Validar bandeja tutor, marcado de lectura y preferencias principales.', 70, false, current_timestamp),
('CONTACT_STAFF_FLOW', 'Contacto tutor-staff', 'Validar solicitud de contacto tutor, listado staff, resolucion y notificacion al tutor.', 80, false, current_timestamp),
('PASSWORD_RESET_FLOW', 'Recuperacion contrasena', 'Validar solicitud y confirmacion de recuperacion de contrasena en entorno beta.', 90, false, current_timestamp),
('DEV_ENVIRONMENT_HEALTH', 'Entorno dev saludable', 'Validar backend dev, base hospital_familia_dev y frontend conectados para revision.', 100, false, current_timestamp);
