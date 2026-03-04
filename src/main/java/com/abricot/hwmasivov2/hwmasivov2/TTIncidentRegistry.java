package com.abricot.hwmasivov2.hwmasivov2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hp.uca.expert.alarm.Alarm;
import com.hp.uca.expert.scenario.Scenario;

/**
 * Registro de incidentes TT persistido en SQLite vía JDBC.
 * Una fila por (incident_id, ne_name, id_sitio); constraint único evita duplicados.
 */
public class TTIncidentRegistry {

    private static final String ESTADO_ABIERTO = "abierto";
    private static final String DEFAULT_TABLE = "UCA_TT_INCIDENT";

    private TTIncidentRegistry() {
    }

    /**
     * Registra el incidente TT: si es PA, una fila por cada neName único del additionalText;
     * si es hija, una fila con su neName e idSitio. Persiste en SQLite (tags: TTRegistryJdbcUrl, TTRegistryTableName).
     */
    public static void registerTTIncident(Scenario scenario, Alarm alarm, String incidentId) {
        if (scenario == null || alarm == null || incidentId == null || incidentId.trim().isEmpty()) {
            return;
        }
        try {
            int enabled = 1;
            if (alarm.getPassingFiltersParams() != null && alarm.getPassingFiltersParams().get("tags") != null) {
                Object v = ((java.util.Map<?, ?>) alarm.getPassingFiltersParams().get("tags")).get("TTRegistryEnabled");
                if (v != null) {
                    enabled = Integer.parseInt(v.toString().trim());
                }
            }
            if (enabled != 1) {
                return;
            }
        } catch (Exception e) {
            // asumir habilitado
        }

        String addText = null;
        try {
            addText = alarm.getStringField("additionalText");
        } catch (Exception e) {
            Object cf = alarm.getCustomFieldValue("additionalText");
            addText = cf != null ? cf.toString() : null;
        }
        if (addText == null) {
            addText = "";
        }

        String jdbcUrl = getTag(alarm, "TTRegistryJdbcUrl");
        String tableName = getTag(alarm, "TTRegistryTableName");
        if (tableName == null || tableName.trim().isEmpty()) {
            tableName = DEFAULT_TABLE;
        }

        if (addText.contains("PB=ProblemAlarm")) {
            Map<String, String> uniqueByNeName = extractUniqueLinesByNeName(addText);
            for (Map.Entry<String, String> e : uniqueByNeName.entrySet()) {
                insertRow(scenario, alarm, jdbcUrl, tableName, incidentId.trim(), e.getKey(), e.getValue(), ESTADO_ABIERTO);
            }
            return;
        }

        String neName = alarm.getCustomFieldValue("neName");
        String idSitio = alarm.getCustomFieldValue("idSitio");
        if (neName == null) neName = "";
        else neName = neName.trim();
        if (idSitio == null) idSitio = "";
        else idSitio = idSitio.trim();
        insertRow(scenario, alarm, jdbcUrl, tableName, incidentId.trim(), neName, idSitio, ESTADO_ABIERTO);
    }

    /**
     * Consulta si existe un incidente abierto para el (neName, idSitio) y devuelve su incident_id.
     * Usado por la regla de evaluación antes de agrupar; una consulta por alarma.
     *
     * @return incident_id de la fila con estado 'abierto', o null si no existe o hay error/URL no configurada
     */
    public static String getOpenIncidentIdForNeAndSitio(Scenario scenario, Alarm alarm, String neName, String idSitio) {
        if (alarm == null) {
            return null;
        }
        String n = (neName == null) ? "" : neName.trim();
        String s = (idSitio == null) ? "" : idSitio.trim();
        String jdbcUrl = getTag(alarm, "TTRegistryJdbcUrl");
        String tableName = getTag(alarm, "TTRegistryTableName");
        if (tableName == null || tableName.trim().isEmpty()) {
            tableName = DEFAULT_TABLE;
        }
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            return null;
        }
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DriverManager.getConnection(jdbcUrl);
            String sql = "SELECT incident_id FROM " + sanitizeTableName(tableName) + " WHERE ne_name = ? AND id_sitio = ? AND estado = ? LIMIT 1";
            ps = conn.prepareStatement(sql);
            ps.setString(1, n);
            ps.setString(2, s);
            ps.setString(3, ESTADO_ABIERTO);
            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString(1);
            }
            return null;
        } catch (SQLException e) {
            if (scenario != null && scenario.getLogger() != null) {
                scenario.getLogger().warn("TTIncidentRegistry: error al consultar incidente abierto para ne_name={}, id_sitio={} - {}", n, s, e.getMessage());
            }
            return null;
        } finally {
            if (rs != null) try { rs.close(); } catch (SQLException ignored) { }
            if (ps != null) try { ps.close(); } catch (SQLException ignored) { }
            if (conn != null) try { conn.close(); } catch (SQLException ignored) { }
        }
    }

    private static String getTag(Alarm alarm, String key) {
        try {
            if (alarm.getPassingFiltersParams() == null || alarm.getPassingFiltersParams().get("tags") == null) {
                return null;
            }
            Object v = ((java.util.Map<?, ?>) alarm.getPassingFiltersParams().get("tags")).get(key);
            return v != null ? v.toString().trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extrae una entrada por neName único: líneas con formato "neName [idSitio]".
     * id_sitio es el valor entre corchetes. Si el mismo neName aparece varias veces, se conserva la primera.
     */
    private static Map<String, String> extractUniqueLinesByNeName(String additionalText) {
        Map<String, String> uniqueByNeName = new LinkedHashMap<>();
        if (additionalText == null || additionalText.isEmpty()) {
            return uniqueByNeName;
        }
        String[] lines = additionalText.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            int open = trimmed.indexOf(" [");
            int close = trimmed.lastIndexOf(']');
            if (open > 0 && close > open) {
                String neName = trimmed.substring(0, open).trim();
                String idSitio = trimmed.substring(open + 2, close).trim();
                if (!neName.isEmpty() && !uniqueByNeName.containsKey(neName)) {
                    uniqueByNeName.put(neName, idSitio);
                }
            }
        }
        return uniqueByNeName;
    }

    /**
     * Inserta una fila en SQLite. Si la URL JDBC está vacía, solo registra en log.
     * Duplicados (UNIQUE constraint): se registra en log y no se relanza.
     */
    private static void insertRow(Scenario scenario, Alarm alarm, String jdbcUrl, String tableName,
                                  String incidentId, String neName, String idSitio, String estado) {
        if (scenario != null && scenario.getLogger() != null) {
            scenario.getLogger().info(
                    "TTIncidentRegistry insert: incident_id={}, ne_name={}, id_sitio={}, estado={} (fechas en BD)",
                    incidentId, neName, idSitio, estado);
        }

        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            return;
        }

        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = DriverManager.getConnection(jdbcUrl);
            String sql = "INSERT INTO " + sanitizeTableName(tableName) + " (incident_id, ne_name, id_sitio, estado) VALUES (?, ?, ?, ?)";
            ps = conn.prepareStatement(sql);
            ps.setString(1, incidentId);
            ps.setString(2, neName);
            ps.setString(3, idSitio);
            ps.setString(4, estado);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (isUniqueConstraintViolation(e)) {
                if (scenario != null && scenario.getLogger() != null) {
                    scenario.getLogger().info("TTIncidentRegistry: fila ya existe (duplicado ignorado): incident_id={}, ne_name={}, id_sitio={}", incidentId, neName, idSitio);
                }
            } else {
                if (scenario != null && scenario.getLogger() != null) {
                    scenario.getLogger().warn("TTIncidentRegistry: error al insertar: incident_id={}, ne_name={}, id_sitio={} - {}", incidentId, neName, idSitio, e.getMessage());
                }
            }
        } finally {
            if (ps != null) try { ps.close(); } catch (SQLException ignored) { }
            if (conn != null) try { conn.close(); } catch (SQLException ignored) { }
        }
    }

    private static boolean isUniqueConstraintViolation(SQLException e) {
        // SQLite: error code 19 = SQLITE_CONSTRAINT (UNIQUE, NOT NULL, etc.)
        if (e.getErrorCode() == 19) {
            return true;
        }
        String msg = e.getMessage();
        return msg != null && (msg.contains("UNIQUE constraint") || msg.contains("UNIQUE constraint failed"));
    }

    /** Solo permite caracteres alfanuméricos y guión bajo en nombre de tabla (evitar SQL injection). */
    private static String sanitizeTableName(String tableName) {
        if (tableName == null) return DEFAULT_TABLE;
        return tableName.trim().replaceAll("[^a-zA-Z0-9_]", "");
    }
}
