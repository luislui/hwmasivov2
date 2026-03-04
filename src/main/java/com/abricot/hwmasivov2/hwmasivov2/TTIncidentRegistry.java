package com.abricot.hwmasivov2.hwmasivov2;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hp.uca.expert.alarm.Alarm;
import com.hp.uca.expert.scenario.Scenario;

/**
 * Registro de incidentes TT para persistencia (Oracle/SQLite en el futuro).
 * Por ahora solo simula la escritura en BD registrando en log las filas que se insertarían.
 */
public class TTIncidentRegistry {

    private static final String ESTADO_ABIERTO = "abierto";

    private TTIncidentRegistry() {
    }

    /**
     * Registra el incidente TT asociado a la alarma: si es PA, una fila por cada neName único
     * del additionalText; si es hija, una fila con su neName e idSitio.
     * Por ahora solo simula (log); cuando se implemente la BD real se sustituirá por INSERT.
     *
     * @param scenario   escenario actual (para log)
     * @param alarm      alarma (PA o hija)
     * @param incidentId número del TT (identificador SM)
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
            // asumir habilitado para simulación
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

        // PA: additionalText contiene "PB=ProblemAlarm" -> una fila por neName único; id_sitio es el valor entre corchetes
        if (addText.contains("PB=ProblemAlarm")) {
            Map<String, String> uniqueByNeName = extractUniqueLinesByNeName(addText);
            for (Map.Entry<String, String> e : uniqueByNeName.entrySet()) {
                simulateInsert(scenario, incidentId.trim(), e.getKey(), e.getValue(), ESTADO_ABIERTO);
            }
            return;
        }

        // Hija: neName e idSitio en custom fields
        String neName = alarm.getCustomFieldValue("neName");
        String idSitio = alarm.getCustomFieldValue("idSitio");
        if (neName == null) {
            neName = "";
        } else {
            neName = neName.trim();
        }
        if (idSitio == null) {
            idSitio = "";
        } else {
            idSitio = idSitio.trim();
        }
        simulateInsert(scenario, incidentId.trim(), neName, idSitio, ESTADO_ABIERTO);
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
            if (trimmed.isEmpty()) {
                continue;
            }
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
     * Simula el INSERT registrando en log la fila que se persistiría.
     * Cuando se implemente la BD real, aquí irá la lógica JDBC.
     */
    private static void simulateInsert(Scenario scenario, String incidentId, String neName, String idSitio, String estado) {
        if (scenario == null || scenario.getLogger() == null) {
            return;
        }
        Instant now = Instant.now();
        scenario.getLogger().info(
                "TTIncidentRegistry [simulación] would insert: incident_id={}, ne_name={}, id_sitio={}, estado={}, fecha_creacion={}, fecha_actualizacion={}",
                incidentId, neName, idSitio, estado, now, now);
    }
}
