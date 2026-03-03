package com.abricot.hwmasivov2.hwmasivov2;

import java.util.List;
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
    // Acepta "neName=GT1709" o "neName: GT1709"
    private static final Pattern PATTERN_NE_NAME = Pattern.compile("neName\\s*[=:]\\s*([^|\\s\\n]+)");
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

            // Guardar en custom fields
            alarm.setCustomFieldValue("alarmName", alarmName);
            alarm.setCustomFieldValue("shwIdRaw", shwIdRaw);
            alarm.setCustomFieldValue("idSitio", idSitio); // Solo los últimos 4 dígitos, ej. "1709"
            alarm.setCustomFieldValue("neName", neName);
            alarm.setCustomFieldValue("spadre", spadre);

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

        for (TimeStampedAttributeChange timeStampedAttributeChange : attributeChanges) {
            // Retrieve the list of attribute change stored
            List<AttributeChange> attChanges = timeStampedAttributeChange.getAttributeChange();

            if (attChanges != null && !attChanges.isEmpty()) {
                for (AttributeChange attributeChange : attChanges) {
                    // Display the name of attribute with its old and new value
                    theScenario.getLogger().info("Field update [name:{}][newValue:{}][oldValue:{}]",
                            attributeChange.getName(), attributeChange.getNewValue(), attributeChange.getOldValue());
                }
            }
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
