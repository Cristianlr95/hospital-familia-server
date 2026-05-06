# hospital-familia-server

Backend base de `Hospital - Familia`.

## Stack
- Spring Boot 3.5
- Java 21
- Maven Wrapper
- PostgreSQL
- Flyway

## Estado inicial
- scaffold backend creado,
- listo para definir modulos, seguridad y persistencia.

## Base de datos local

El perfil de desarrollo usa PostgreSQL local con estos valores por defecto:

```text
Host: localhost
Puerto: 5432
Base de datos: hospital_familia_dev
Usuario: hospital_familia_dev
Password: hospital_familia_dev
```

URL JDBC:

```text
jdbc:postgresql://localhost:5432/hospital_familia_dev
```

Para DBeaver, crea una conexion PostgreSQL con esos mismos valores. La base y el usuario se pueden recrear desde una conexion admin con:

```sql
CREATE USER hospital_familia_dev WITH PASSWORD 'hospital_familia_dev';
CREATE DATABASE hospital_familia_dev OWNER hospital_familia_dev;
GRANT ALL PRIVILEGES ON DATABASE hospital_familia_dev TO hospital_familia_dev;
```

## CORS local

El backend permite por defecto llamadas desde Ionic y Angular locales:

```text
http://localhost:8100
http://localhost:4200
```

Para ajustar origenes por ambiente, usa:

```text
CORS_ALLOWED_ORIGINS=https://app.hospitalfamilia.com,https://admin.hospitalfamilia.com
```

## Endpoints de estado visible del paciente

Los estados de paciente solo se exponen a tutores autenticados con vinculacion `APPROVED`.

```text
GET /api/patients/my-statuses
GET /api/patients/{patientPublicId}/status
```

La respuesta entrega solo datos operativos acotados para familia: nombre visible, estado general, servicio actual, ubicacion actual, resumen breve y fecha de actualizacion. No expone historia clinica ni codigo de vinculacion.

## Endpoints de calendario y eventos

Los tutores solo pueden leer eventos de pacientes con vinculacion `APPROVED`.

```text
GET /api/patients/{patientPublicId}/events
```

El personal `STAFF` o `ADMIN` puede gestionar eventos operativos visibles para familia:

```text
GET    /api/events/patient/{patientPublicId}
POST   /api/events
PUT    /api/events/{id}
PUT    /api/events/{id}/status
DELETE /api/events/{id}
```

Tipos soportados:

```text
SURGERY, EXAM, VISIT, STATE_CHANGE, DISCHARGE, OTHER
```

Estados soportados:

```text
SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
```
