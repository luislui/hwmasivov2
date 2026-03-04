-- Tabla de registro de incidentes TT (SQLite).
-- Una fila por (incident_id, ne_name, id_sitio); constraint único para evitar duplicados.
-- fecha_creacion y fecha_actualizacion en hora LOCAL del servidor (datetime('now','localtime')).
-- Ejecutar una vez para crear la BD (ej. sqlite3 tt_incident.db < create_tt_registry.sql).

CREATE TABLE IF NOT EXISTS UCA_TT_INCIDENT (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    incident_id         TEXT NOT NULL,
    ne_name             TEXT NOT NULL,
    id_sitio            TEXT NOT NULL,
    estado              TEXT NOT NULL,
    fecha_creacion      TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    fecha_actualizacion TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    UNIQUE(incident_id, ne_name, id_sitio)
);

-- Actualizar fecha_actualizacion automáticamente en cada UPDATE (hora local)
CREATE TRIGGER IF NOT EXISTS uca_tt_incident_updated
AFTER UPDATE ON UCA_TT_INCIDENT
FOR EACH ROW
BEGIN
    UPDATE UCA_TT_INCIDENT SET fecha_actualizacion = datetime('now', 'localtime') WHERE id = NEW.id;
END;

CREATE INDEX IF NOT EXISTS idx_tt_incident_id ON UCA_TT_INCIDENT(incident_id);

-- Búsqueda por ne_name + id_sitio + estado (ej. "¿existe incidente abierto para este NE y sitio?")
CREATE INDEX IF NOT EXISTS idx_tt_ne_sitio_estado ON UCA_TT_INCIDENT(ne_name, id_sitio, estado);
