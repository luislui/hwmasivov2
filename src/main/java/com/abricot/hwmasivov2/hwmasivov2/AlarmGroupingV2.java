package com.abricot.hwmasivov2.hwmasivov2;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.hp.uca.expert.alarm.Alarm;
import com.hp.uca.expert.scenario.Scenario;
import com.hp.uca.expert.scenario.ScenarioThreadLocal;
import com.hp.uca.expert.x733alarm.PerceivedSeverity;

/**
 * Agrupa alarmas \"NE Is Disconnected\" para crear una Problem Alarm (PA).
 * Criterio: al menos N idSitio distintos; todas las alarmas candidatas se
 * asocian como hijas.
 */
public class AlarmGroupingV2 {

    private static final int DEFAULT_NUM_ALARMS_NE_DISCONNECTED = 10;
    private static final int DEFAULT_SECONDS_TO_CONSIDER_NE_DISCONNECTED = 60;

    private AlarmGroupingV2() {
    }

    private static int getNumAlarmsNeDisconnected(Alarm mainAlarm) {
        try {
            if (mainAlarm.getPassingFiltersParams() != null
                    && mainAlarm.getPassingFiltersParams().containsKey("tags")) {
                Object tagsParams = mainAlarm.getPassingFiltersParams().get("tags");
                if (tagsParams instanceof java.util.Map) {
                    Object val = ((java.util.Map<?, ?>) tagsParams).get("NumAlarmsNeDisconnected");
                    if (val != null) {
                        return Integer.parseInt(val.toString());
                    }
                }
            }
        } catch (Exception e) {
            // usar default
        }
        return DEFAULT_NUM_ALARMS_NE_DISCONNECTED;
    }

    public static int getSecondsToConsiderNeDisconnected(Alarm mainAlarm) {
        try {
            if (mainAlarm.getPassingFiltersParams() != null
                    && mainAlarm.getPassingFiltersParams().containsKey("tags")) {
                Object tagsParams = mainAlarm.getPassingFiltersParams().get("tags");
                if (tagsParams instanceof java.util.Map) {
                    Object val = ((java.util.Map<?, ?>) tagsParams).get("SecondsToConsiderNeDisconnected");
                    if (val != null) {
                        return Integer.parseInt(val.toString());
                    }
                }
            }
        } catch (Exception e) {
            // usar default
        }
        return DEFAULT_SECONDS_TO_CONSIDER_NE_DISCONNECTED;
    }

    /**
     * Crea una Problem Alarm a partir de una lista de alarmas \"NE Is Disconnected\"
     * y las asocia como hijas.
     *
     * @param mainAlarm  alarma de referencia
     * @param alarmsList lista de alarmas candidatas (NE Is Disconnected)
     * @return ID de la Problem Alarm creada, o null si no se crea
     */
    public static String createNeDisconnectedPA(Alarm mainAlarm, Object alarmsList) {
        Scenario theScenario = ScenarioThreadLocal.getScenario();
        if (theScenario == null || !theScenario.getLogger().isInfoEnabled()) {
            return null;
        }

        List<Alarm> allChildren = new ArrayList<>();
        if (alarmsList instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> rawList = (List<Object>) alarmsList;
            for (Object item : rawList) {
                if (item instanceof Alarm) {
                    allChildren.add((Alarm) item);
                }
            }
        }

        if (allChildren.isEmpty()) {
            return null;
        }

        int numAlarmsRequired = getNumAlarmsNeDisconnected(mainAlarm);

        Set<String> sitioIdsDistintos = new LinkedHashSet<>();
        for (Alarm a : allChildren) {
            String idSitio = a.getCustomFieldValue("idSitio");
            if (idSitio != null && !idSitio.trim().isEmpty()) {
                sitioIdsDistintos.add(idSitio.trim());
            }
        }

        if (sitioIdsDistintos.size() < numAlarmsRequired) {
            theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");
            theScenario.getLogger().info(
                    "Agrupar NE Is Disconnected: se requieren al menos {} idSitio distintos. Encontrados {}",
                    numAlarmsRequired, sitioIdsDistintos.size());
            theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");
            return null;
        }

        theScenario.getLogger().info("─────────────────────────────────────────────────────────────");
        theScenario.getLogger().info(
                "AGRUPANDO ALARMAS NE IS DISCONNECTED - {} alarmas ({} idSitio distintos)",
                allChildren.size(), sitioIdsDistintos.size());
        theScenario.getLogger().info("IdSitio distintos  : {}", sitioIdsDistintos);
        theScenario.getLogger().info("─────────────────────────────────────────────────────────────");

        // idSitio de la alarma semilla para el Managed Object
        String idSitioSemilla = mainAlarm.getCustomFieldValue("idSitio");
        if (idSitioSemilla == null || idSitioSemilla.trim().isEmpty()) {
            idSitioSemilla = "0";
        } else {
            idSitioSemilla = idSitioSemilla.trim();
        }

        // MO: RADIOBASE_HUAWEI <idSitio>, ej. RADIOBASE_HUAWEI 1029
        String mo = "RADIOBASE_HUAWEI " + idSitioSemilla;

        // Additional Text: Evento masivo radio base Huawei + PB=ProblemAlarm: idSitio (mismo que el MO) + una línea por alarma "neName [idSitio]"
        StringBuilder atext = new StringBuilder();
        atext.append("Evento masivo radio base Huawei\n");
        atext.append("PB=ProblemAlarm: ").append(idSitioSemilla).append("\n");
        for (Alarm al : allChildren) {
            String neName = al.getCustomFieldValue("neName");
            String idSitio = al.getCustomFieldValue("idSitio");
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
            atext.append(neName).append(" [").append(idSitio).append("]\n");
        }

        String atextStr = atext.toString();

        // Log Managed Object y Additional Text
        theScenario.getLogger().info("CREANDO PROBLEM ALARM NE IS DISCONNECTED - Managed Object y Additional Text:");
        theScenario.getLogger().info("  Managed Object    : {}", mo);
        theScenario.getLogger().info("  Additional Text   :");
        theScenario.getLogger().info("{}", atextStr);
        theScenario.getLogger().info("─────────────────────────────────────────────────────────────");

        String oc = mainAlarm.getIdentifier();
        if (oc != null && oc.contains("alarm_object")) {
            int idx = oc.indexOf("alarm_object");
            oc = oc.substring(0, idx).trim();
        }
        if (oc == null || oc.trim().isEmpty()) {
            oc = "Operation_Context uca_network ALARM 0";
        }

        String sev = mainAlarm.getPerceivedSeverity() != null
                ? mainAlarm.getPerceivedSeverity().value()
                : PerceivedSeverity.MAJOR.value();
        String atype = mainAlarm.getAlarmType() != null ? mainAlarm.getAlarmType().value() : "";
        String pcause = mainAlarm.getProbableCause();
        String nid = mainAlarm.getNotificationIdentifier();

        String problemAlarmId = TemipDirectives.createAlarm(theScenario, oc, mo, sev, atype, pcause, nid, atextStr);

        // Si se creó la PA, asociar en ambos sentidos igual que en CorteFibraV10
        if (problemAlarmId != null && !problemAlarmId.trim().isEmpty()) {
            List<String> childIdentifiers = new ArrayList<>();
            for (Alarm alItem : allChildren) {
                childIdentifiers.add(alItem.getIdentifier());
            }

            theScenario.getLogger().info("─────────────────────────────────────────────────────────────");
            theScenario.getLogger().info(
                    "ASOCIANDO EN AMBOS SENTIDOS: {} ALARMAS HIJAS A LA PROBLEM ALARM NE IS DISCONNECTED",
                    childIdentifiers.size());
            theScenario.getLogger().info("  Problem Alarm ID     : {}", problemAlarmId);

            // 1. Hijas -> padre (GROUPALARMS)
            theScenario.getLogger().info("  → Asociando hijas al padre (GROUPALARMS)...");
            TemipDirectives.associateChildrenToParent(theScenario, problemAlarmId, childIdentifiers, mainAlarm);

            // 2. Padre -> cada hija (ADDPARENT)
            theScenario.getLogger().info("  → Asociando padre a cada hija (ADDPARENT)...");
            for (Alarm alItem : allChildren) {
                String childId = alItem.getIdentifier();
                theScenario.getLogger().info("    Asociando padre a hija: {}", childId);
                TemipDirectives.associateParentToChild(theScenario, childId, problemAlarmId, mainAlarm);
            }
        }

        return problemAlarmId;
    }
}

