-- V4__add_event_publication_table.sql
-- PM-013: schema required by spring-modulith-starter-jdbc's EventPublicationRegistry so
-- @ApplicationModuleListener dispatch (book/enrollment cascade listeners) is durably tracked and
-- retryable, rather than fire-and-forget. Column/table names match
-- spring-modulith-events-jdbc-1.1.12.jar!/schema-mysql.sql byte-for-byte, unlike this project's
-- usual lower_snake_case convention: JdbcEventPublicationRepository's SQL hardcodes unquoted
-- uppercase identifiers, and MySQL on Linux (this project's Testcontainers/prod target) treats
-- table names as case-sensitive, so a lower-cased table here would 42S02 ("table doesn't exist")
-- against every query Modulith issues.
-- Flyway owns this table, not Modulith's own schema auto-initializer (left disabled/default).

CREATE TABLE EVENT_PUBLICATION (
    ID                VARCHAR(36)  NOT NULL,
    LISTENER_ID       VARCHAR(512) NOT NULL,
    EVENT_TYPE        VARCHAR(512) NOT NULL,
    SERIALIZED_EVENT  VARCHAR(4000) NOT NULL,
    PUBLICATION_DATE  TIMESTAMP(6) NOT NULL,
    COMPLETION_DATE   TIMESTAMP(6) NULL,
    PRIMARY KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
