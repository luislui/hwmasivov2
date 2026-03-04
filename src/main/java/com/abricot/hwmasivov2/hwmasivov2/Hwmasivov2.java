package com.abricot.hwmasivov2.hwmasivov2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hp.uca.expert.alarm.Alarm;
import com.hp.uca.expert.alarm.TimeStampedAttributeChange;
import com.hp.uca.expert.scenario.Scenario;
import com.hp.uca.expert.scenario.ScenarioThreadLocal;
import com.hp.uca.expert.x733alarm.AttributeChange;

/**
 * Utility class that logs alarm insertions, attribute value changes and state changes
 * 
 */
public class Hwmasivov2 {

    private static final String ALARM_RECEIVED = "Alarm received: \n{}";

    // Patrones para parseo de campos específicos en el additionalText
    private static final Pattern PATTERN_ALARM_NAME = Pattern.compile("alarmName:\\s*([^|]+)\\|");
    // Acepta "neName=GT1709" o "neName: GT1709" y corta en coma, espacio, salto de línea o '|'
    // Ej: "neName=GT1709, neIP=..." -> GT1709
    private static final Pattern PATTERN_NE_NAME = Pattern.compile("neName\\s*[=:]\\s*([^|,\\s\\n]+)");
    // SHW_id: WG1709_:EHW_id  -> valor bruto WG1709
    private static final Pattern PATTERN_SHW_ID = Pattern.compile("SHW_id:\\s*([^_:\\s]+)_:EHW_id");
    // SPADRE:GNCYGTZA_:SPADRE -> GNCYGTZA
    private static final Pattern PATTERN_SPADRE = Pattern.compile("SPADRE:([^_:\\s]+)_:SPADRE");

    /**
     * Hides the empty constructor
     */
    private Hwmasivov2() {
        // Do nothing
    }

    // ==========================
    //  Métodos auxiliares parseo
    // ==========================

    private static String extractValue(Matcher matcher) {
        if (matcher != null && matcher.find()) {
            String value = matcher.group(1);
            return value != null ? value.trim() : "";
        }
        return "";
    }

    private static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    // ==========================
    //  Ticket: handledBy / problemInformation (centralizado, como CorteFibraV10)
    // ==========================

    /** Patrón para extraer ID de ticket desde problemInformation o handledBy.
     *  Acepta "Element F4688399" o "Element 'F4705963'" (formato TeMIP en custom field handledBy). */
    private static final Pattern PATTERN_TICKET_ELEMENT = Pattern.compile("Element\\s+['\"]?([A-Za-z0-9]+)['\"]?", Pattern.CASE_INSENSITIVE);

    /**
     * Extrae el ID de ticket de una cadena (problemInformation o handledBy).
     * Formato típico: "Element F4688399" o similar.
     *
     * @param source cadena que puede contener el ticket (problemInformation, handledBy, etc.)
     * @return ID del ticket o null si no se encuentra
     */
    public static String extractTicketId(String source) {
        if (source == null || source.trim().isEmpty()) {
            return null;
        }
        Matcher m = PATTERN_TICKET_ELEMENT.matcher(source.trim());
        if (m.find()) {
            String id = m.group(1);
            return id != null && !id.trim().isEmpty() ? id.trim() : null;
        }
        return null;
    }

    /**
     * Devuelve el ID de ticket de la alarma, buscando en este orden:
     * 1) custom field handledByTicketId,
     * 2) handledBy (custom field o atributo),
     * 3) problemInformation.
     * Así se cubre el caso en que Service Manager actualiza problemInformation pero no handledBy.
     *
     * @param alarm alarma
     * @return ID del ticket o null si no tiene
     */
    public static String getTicketId(Alarm alarm) {
        if (alarm == null) {
            return null;
        }
        Object cf = alarm.getCustomFieldValue("handledByTicketId");
        if (cf != null) {
            String s = cf.toString().trim();
            if (!s.isEmpty()) {
                return s;
            }
        }
        Object handledBy = alarm.getCustomFieldValue("handledBy");
        if (handledBy == null) {
            try {
                if (alarm.getStringField("handledBy") != null) {
                    handledBy = alarm.getStringField("handledBy");
                }
            } catch (Exception e) {
                // handledBy puede no existir como atributo
            }
        }
        String fromHandledBy = extractTicketId(handledBy != null ? handledBy.toString() : null);
        if (fromHandledBy != null) {
            return fromHandledBy;
        }
        try {
            String pi = alarm.getProblemInformation();
            return extractTicketId(pi);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Indica si la alarma tiene ticket (por handledByTicketId, handledBy o problemInformation).
     *
     * @param alarm alarma
     * @return true si tiene ticket
     */
    public static boolean alarmHasTicket(Alarm alarm) {
        return getTicketId(alarm) != null;
    }

    /**
     * Devuelve la lista de identificadores de los padres de la alarma.
     * Soporta parents como List de String (identificadores) o List de Alarm.
     *
     * @param alarm alarma (hija)
     * @return lista de identificadores de padres, nunca null
     */
    public static List<String> getParentIdentifiers(Alarm alarm) {
        if (alarm == null) {
            return Collections.emptyList();
        }
        try {
            Object parents = alarm.getParents();
            if (parents == null) {
                return Collections.emptyList();
            }
            if (parents instanceof List) {
                List<?> list = (List<?>) parents;
                List<String> ids = new ArrayList<>(list.size());
                for (Object o : list) {
                    if (o instanceof Alarm) {
                        String id = ((Alarm) o).getIdentifier();
                        if (id != null && !id.trim().isEmpty()) {
                            ids.add(id.trim());
                        }
                    } else if (o != null) {
                        String s = o.toString().trim();
                        if (!s.isEmpty()) {
                            ids.add(s);
                        }
                    }
                }
                return ids;
            }
        } catch (Exception e) {
            // ignorar
        }
        return Collections.emptyList();
    }

    /**
     * Devuelve la lista de identificadores de las hijas de la alarma.
     * Soporta children como List de String (identificadores) o List de Alarm.
     *
     * @param alarm alarma (padre)
     * @return lista de identificadores de hijas, nunca null
     */
    public static List<String> getChildIdentifiers(Alarm alarm) {
        if (alarm == null) {
            return Collections.emptyList();
        }
        try {
            Object children = alarm.getChildren();
            if (children == null) {
                return Collections.emptyList();
            }
            if (children instanceof List) {
                List<?> list = (List<?>) children;
                List<String> ids = new ArrayList<>(list.size());
                for (Object o : list) {
                    if (o instanceof Alarm) {
                        String id = ((Alarm) o).getIdentifier();
                        if (id != null && !id.trim().isEmpty()) {
                            ids.add(id.trim());
                        }
                    } else if (o != null) {
                        String s = o.toString().trim();
                        if (!s.isEmpty()) {
                            ids.add(s);
                        }
                    }
                }
                return ids;
            }
        } catch (Exception e) {
            // ignorar
        }
        return Collections.emptyList();
    }

    private static void formatField(StringBuilder sb, String fieldName, String fieldValue) {
        String value = isEmpty(fieldValue) ? "(no encontrado)" : fieldValue;
        String indicator = isEmpty(fieldValue) ? "[--]" : "[OK]";
        sb.append(String.format("  %s %-20s : %s%n", indicator, fieldName + ":", value));
    }

    private static String formatParsedData(String alarmName, String shwIdRaw, String idSitio,
                                           String neName, String spadre, String alarmId) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("  DATOS PARSEADOS DE LA ALARMA (HW MASIVO V2)\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");

        sb.append(String.format("  Alarm ID                 : %s%n",
                alarmId != null ? alarmId : "(vacío)"));
        sb.append("  ───────────────────────────────────────────────────────────\n");
        sb.append("  VALORES EXTRAÍDOS:\n");
        sb.append("  ───────────────────────────────────────────────────────────\n");

        formatField(sb, "alarmName", alarmName);
        formatField(sb, "SHW_id (bruto)", shwIdRaw);
        formatField(sb, "idSitio", idSitio);
        formatField(sb, "neName", neName);
        formatField(sb, "SPADRE", spadre);

        sb.append("═══════════════════════════════════════════════════════════════\n");
        return sb.toString();
    }

    /**
     * Formato de log para los campos que trae una PA al entrar (como en alarma normal).
     */
    private static String formatPAParsedData(Alarm alarm, String paSemillaId, String additionalText) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("  DATOS PARSEADOS DE LA PA (PROBLEM ALARM - HW MASIVO V2)\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");

        formatField(sb, "Alarm ID", alarm != null ? alarm.getIdentifier() : null);
        formatField(sb, "paSemillaId (idSitio)", paSemillaId);
        try {
            formatField(sb, "OriginatingManagedEntity",
                    alarm != null ? alarm.getOriginatingManagedEntity() : null);
            formatField(sb, "PerceivedSeverity",
                    alarm != null && alarm.getPerceivedSeverity() != null
                            ? alarm.getPerceivedSeverity().value() : null);
            formatField(sb, "AlarmType",
                    alarm != null && alarm.getAlarmType() != null
                            ? alarm.getAlarmType().value() : null);
            formatField(sb, "ProbableCause", alarm != null ? alarm.getProbableCause() : null);
            String handledBy = null;
            if (alarm != null) {
                Object cf = alarm.getCustomFieldValue("handledBy");
                if (cf != null) {
                    handledBy = cf.toString();
                }
                if (handledBy == null || handledBy.trim().isEmpty()) {
                    try {
                        if (alarm.getStringField("handledBy") != null) {
                            handledBy = alarm.getStringField("handledBy");
                        }
                    } catch (Exception ignored) {
                        // handledBy puede no existir como atributo
                    }
                }
            }
            formatField(sb, "handledBy", handledBy);
        } catch (Exception e) {
            // ignorar si algún getter no existe
        }
        sb.append("  ───────────────────────────────────────────────────────────\n");
        sb.append("  Additional Text (preview):\n");
        if (additionalText != null && !additionalText.isEmpty()) {
            String preview = additionalText.length() > 500
                    ? additionalText.substring(0, 500) + "..." : additionalText;
            sb.append("  ").append(preview.replace("\n", "\n  ")).append("\n");
        } else {
            sb.append("  (vacío)\n");
        }
        sb.append("═══════════════════════════════════════════════════════════════\n");
        return sb.toString();
    }

    /**
     * Parsea la alarma extrayendo alarmName, SHW_id, neName y SPADRE del additionalText
     * y los guarda en custom fields.
     *
     * - alarmName: texto entre "alarmName:" y el siguiente '|'
     * - SHW_id: de "SHW_id: WG1709_:EHW_id" se extrae "WG1709" y se guardan:
     *      * shwIdRaw  = "WG1709"
     *      * shwId     = "1709" (últimos 4 dígitos)
     * - neName: acepta "neName=GT1709" o "neName: GT1709"
     * - spadre: de "SPADRE:GNCYGTZA_:SPADRE" se extrae "GNCYGTZA"
     */
    public static void parseAlarm(Alarm alarm) {
        Scenario theScenario = ScenarioThreadLocal.getScenario();

        if (!theScenario.getLogger().isInfoEnabled()) {
            alarm.setJustInserted(false);
            return;
        }

        try {
            String addText = alarm.getStringField("additionalText");
            if (addText == null) {
                addText = "";
            }

            // Si es una PA nuestra (PB=ProblemAlarm: idSemilla), extraer paSemillaId y mostrar en log los campos que trae (como alarma normal)
            if (addText.contains("PB=ProblemAlarm: ")) {
                int start = addText.indexOf("PB=ProblemAlarm: ") + "PB=ProblemAlarm: ".length();
                int end = addText.indexOf('\n', start);
                if (end < 0) {
                    end = addText.length();
                }
                String paSemillaId = addText.substring(start, end).trim();
                if (!paSemillaId.isEmpty()) {
                    alarm.setCustomFieldValue("paSemillaId", paSemillaId);
                }
                // stage=1 para que la regla "Crear TT de PA" pueda hacer match; se pasa a stage=2 tras crear el TT
                alarm.setCustomFieldValue("stage", "1");

                theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");
                theScenario.getLogger().info("PA INSERTADA - Iniciando parseo (HW MASIVO V2)");
                theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");
                theScenario.getLogger().info("Alarma completa   :\n{}", alarm.toFormattedString());
                theScenario.getLogger().info("─────────────────────────────────────────────────────────────");
                theScenario.getLogger().info("{}", formatPAParsedData(alarm, paSemillaId, addText));
                theScenario.getLogger().info("[OK] Parseo PA completado - paSemillaId: {}, stage: 1", paSemillaId);
                theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");

                alarm.setJustInserted(false);
                return;
            }

            theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");
            theScenario.getLogger().info("NUEVA ALARMA INSERTADA - Iniciando parseo (HW MASIVO V2)");
            theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");
            theScenario.getLogger().info("Alarma completa   :\n{}", alarm.toFormattedString());
            theScenario.getLogger().info("─────────────────────────────────────────────────────────────");

            // Extraer valores
            String alarmName = extractValue(PATTERN_ALARM_NAME.matcher(addText));
            String shwIdRaw = extractValue(PATTERN_SHW_ID.matcher(addText));
            String neName = extractValue(PATTERN_NE_NAME.matcher(addText));
            String spadre = extractValue(PATTERN_SPADRE.matcher(addText));

            // Últimos 4 dígitos del SHW_id (si aplica) -> idSitio
            String idSitio = "";
            if (!isEmpty(shwIdRaw)) {
                if (shwIdRaw.length() > 4) {
                    idSitio = shwIdRaw.substring(shwIdRaw.length() - 4);
                } else {
                    idSitio = shwIdRaw;
                }
            }

            // Log con formato similar a hwmasivo v1
            String parsedDataLog = formatParsedData(
                    alarmName,
                    shwIdRaw,
                    idSitio,
                    neName,
                    spadre,
                    alarm.getIdentifier()
            );
            theScenario.getLogger().info(parsedDataLog);

            // Guardar en custom fields (marcando como parseada con stage=1)
            alarm.setCustomFieldValue("alarmName", alarmName);
            alarm.setCustomFieldValue("shwIdRaw", shwIdRaw);
            alarm.setCustomFieldValue("idSitio", idSitio); // Solo los últimos 4 dígitos, ej. "1709"
            alarm.setCustomFieldValue("neName", neName);
            alarm.setCustomFieldValue("spadre", spadre);
            alarm.setCustomFieldValue("stage", "1");

            theScenario.getLogger().info("─────────────────────────────────────────────────────────────");
            theScenario.getLogger().info("[OK] Parseo completado - Valores establecidos en la alarma");
            theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");
        } catch (Exception e) {
            theScenario.getLogger().error("═══════════════════════════════════════════════════════════════");
            theScenario.getLogger().error("[ERROR] Error procesando inserción de alarma", e);
            theScenario.getLogger().error("═══════════════════════════════════════════════════════════════");
        }

        // Evitar que la regla de parseo vuelva a disparar
        alarm.setJustInserted(false);
    }

    /**
     * Cuenta cuántos idSitio distintos hay en la lista de alarmas.
     *
     * @param alarmsList lista de Alarm (puede ser ArrayList de Drools)
     * @return número de idSitio distintos
     */
    public static int countDistinctIdSitio(List<?> alarmsList) {
        if (alarmsList == null || alarmsList.isEmpty()) {
            return 0;
        }
        Set<String> ids = new HashSet<>();
        for (Object item : alarmsList) {
            if (item instanceof Alarm) {
                String idSitio = ((Alarm) item).getCustomFieldValue("idSitio");
                if (idSitio != null && !idSitio.trim().isEmpty()) {
                    ids.add(idSitio.trim());
                }
            }
        }
        return ids.size();
    }

    /**
     * Method corresponding to the rule [alarm attribute value change]
     * 
     * @param alarm
     *            the alarm that has received an AttributeValueChange message
     */
    public static void alarmAttributeChange(Alarm alarm) {

        // Get the Scenario associated to the current thread
        Scenario theScenario = ScenarioThreadLocal.getScenario();

        // Get the logger used for the current scenario and check if enabled ie defined in log4j.xml
        if (!theScenario.getLogger().isInfoEnabled()) {
            return;
        }

        // Display simplest text description of the alarm
        theScenario.getLogger().info(ALARM_RECEIVED, alarm);
        theScenario.getLogger().info("Rule has fired correctly, and AVC has been proceeded");

        // Retrieve the list of attribute value updates, clears the list and reset the HasAVCChanged flag to false
        List<TimeStampedAttributeChange> attributeChanges = alarm.getAttributeValueChanges().getChangesAndClear();

        // Check if retrieved list exists and contains any modifications of alarm attribute
        if (attributeChanges == null || attributeChanges.isEmpty()) {
            return;
        }

        boolean syncHandledByTicketId = false;
        for (TimeStampedAttributeChange timeStampedAttributeChange : attributeChanges) {
            // Retrieve the list of attribute change stored
            List<AttributeChange> attChanges = timeStampedAttributeChange.getAttributeChange();

            if (attChanges != null && !attChanges.isEmpty()) {
                for (AttributeChange attributeChange : attChanges) {
                    // Display the name of attribute with its old and new value
                    theScenario.getLogger().info("Field update [name:{}][newValue:{}][oldValue:{}]",
                            attributeChange.getName(), attributeChange.getNewValue(), attributeChange.getOldValue());
                    // Si Service Manager actualizó problemInformation con el ticket, sincronizar handledByTicketId
                    if ("problemInformation".equalsIgnoreCase(attributeChange.getName())) {
                        Object newVal = attributeChange.getNewValue();
                        if (newVal != null) {
                            String ticketId = extractTicketId(newVal.toString());
                            if (ticketId != null) {
                                Object current = alarm.getCustomFieldValue("handledByTicketId");
                                if (current == null || current.toString().trim().isEmpty()) {
                                    alarm.setCustomFieldValue("handledByTicketId", ticketId);
                                    syncHandledByTicketId = true;
                                    theScenario.getLogger().info("Sincronizado handledByTicketId desde problemInformation: {}", ticketId);
                                }
                            }
                        }
                    }
                }
            }
        }
        if (syncHandledByTicketId) {
            theScenario.getSession().update(alarm);
        }
    }

    /**
     * Method corresponding to the rule [alarm state change]
     * 
     * @param alarm
     *            the alarm that has received an StateChange message
     */
    public static void alarmStateChange(Alarm alarm) {

        // Get the Scenario associated to the current thread
        Scenario theScenario = ScenarioThreadLocal.getScenario();

        // Get the logger used for the current scenario and check if enabled ie defined in log4j.xml
        if (!theScenario.getLogger().isInfoEnabled()) {
            return;
        }

        // Display simplest text description of the alarm
        theScenario.getLogger().info(ALARM_RECEIVED, alarm);
        theScenario.getLogger().info("Rule has fired correctly, and State Change has been proceeded");

        // Retrieve the list of State updates, clears the list and reset the HasStateChanged flag to false
        List<TimeStampedAttributeChange> stateChanges = alarm.getStateChanges().getChangesAndClear();

        // Check if retrieved list exists and contains any state change
        if (stateChanges == null || stateChanges.isEmpty()) {
            return;
        }

        for (TimeStampedAttributeChange timeStampedAttributeChange : stateChanges) {
            // Retrieve the list of state change stored
            List<AttributeChange> stChanges = timeStampedAttributeChange.getAttributeChange();

            // If exists and not empty, display the list of state changed with their old and new value
            if (stChanges != null && !stChanges.isEmpty()) {
                for (AttributeChange attributeChange : stChanges) {
                    theScenario.getLogger().info("State update [name:{}][newValue:{}][oldValue:{}]",
                            attributeChange.getName(), attributeChange.getNewValue(), attributeChange.getOldValue());

                }
            }
        }
    }
}
