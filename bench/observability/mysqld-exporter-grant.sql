-- Read-only user for the mysqld-exporter container (docker-compose.yml) to connect as. Run once
-- against the running management-mysql container:
--
--   docker exec -i management-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
--       < bench/observability/mysqld-exporter-grant.sql
--
-- '%' rather than 'localhost': mysqld-exporter is a separate container reaching mysql over the
-- compose bridge network, not a genuine loopback connection. Password must match
-- bench/observability/mysqld_exporter.my.cnf (copy it from the .example next to it).
CREATE USER IF NOT EXISTS 'exporter'@'%' IDENTIFIED BY 'exporter';
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'%';
FLUSH PRIVILEGES;
