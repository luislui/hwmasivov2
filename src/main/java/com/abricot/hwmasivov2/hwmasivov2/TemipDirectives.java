package com.abricot.hwmasivov2.hwmasivov2;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.hp.uca.expert.alarm.Alarm;
import com.hp.uca.expert.scenario.Scenario;
import com.hp.uca.mediation.action.client.Action;
import com.hp.uca.mediation.action.jaxws.ActionResponseItem;
import com.hp.uca.temip.mvp.aodirective.mapper.AODirectiveKey;

/**
 * Clase utilitaria para crear alarmas y enviar directivas TeMIP
 * (CREATE, GROUPALARMS, ADDPARENT).
 */
public class TemipDirectives {

    // Patrón para extraer operation context y alarm object ID del identificador UCA
    // Formato: "operation_context {namespace}:.rg.cx.xxx_oc alarm_object 1"
    private static final Pattern PATTERN_UCA_IDENTIFIER = Pattern.compile(
            "operation_context\\s+(.+?)\\s+alarm_object\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    private TemipDirectives() {
    }

    private static String normalizeOperationContextForTemip(String operationContext) {
        if (operationContext == null || operationContext.trim().isEmpty()) {
            return operationContext;
        }
        String oc = operationContext.trim();
        oc = oc.replaceFirst("(?i)^operation_context\\s+", "");
        return oc.trim().toLowerCase();
    }

    private static String getTeMIPNamespace(Alarm alarm) {
        if (alarm == null) {
            return null;
        }
        try {
            Object tags = alarm.getPassingFiltersParams() != null ? alarm.getPassingFiltersParams().get("tags") : null;
            Object value = tags != null ? ((java.util.Map<?, ?>) tags).get("TeMIPNamespace") : null;
            if (value != null) {
                String ns = value.toString().trim();
                return ns.isEmpty() ? null : ns;
            }
        } catch (Exception e) {
            // ignorar
        }
        return null;
    }

    private static String convertUcaIdentifierToTemipFormat(String ucaIdentifier, String temipNamespace) {
        if (ucaIdentifier == null || ucaIdentifier.trim().isEmpty()) {
            return ucaIdentifier;
        }

        Matcher matcher = PATTERN_UCA_IDENTIFIER.matcher(ucaIdentifier.trim());
        if (matcher.find()) {
            String normalized = normalizeOperationContextForTemip(matcher.group(1));
            String path = (normalized != null && normalized.contains(":"))
                    ? normalized.substring(normalized.indexOf(':') + 1)
                    : (normalized != null ? normalized : "");
            String alarmObjectId = matcher.group(2).trim();
            String contextPart = (temipNamespace != null && !temipNamespace.trim().isEmpty())
                    ? (temipNamespace.trim() + ":" + path)
                    : path;
            return String.format("C:TeMIP:%s#%s", contextPart, alarmObjectId);
        }

        return ucaIdentifier;
    }

    private static String convertUcaIdentifierToTemipMasterFormat(String ucaIdentifier, String temipNamespace) {
        if (ucaIdentifier == null || ucaIdentifier.trim().isEmpty()) {
            return ucaIdentifier;
        }

        Matcher matcher = PATTERN_UCA_IDENTIFIER.matcher(ucaIdentifier.trim());
        if (matcher.find()) {
            String normalized = normalizeOperationContextForTemip(matcher.group(1));
            String path = (normalized != null && normalized.contains(":"))
                    ? normalized.substring(normalized.indexOf(':') + 1)
                    : (normalized != null ? normalized : "");
            String alarmObjectId = matcher.group(2).trim();
            String contextPart = (temipNamespace != null && !temipNamespace.trim().isEmpty())
                    ? (temipNamespace.trim() + ":" + path)
                    : path;
            return String.format("MASTER:TeMIP:%s#%s", contextPart, alarmObjectId);
        }

        return ucaIdentifier;
    }

    /**
     * Crea una alarma en TeMIP usando la acción TeMIP_AOAction (directiva
     * CREATE).
     *
     * @param theScenario el escenario actual
     * @param oc          operation context
     * @param mo          managed object
     * @param sev         perceived severity
     * @param atype       alarm type
     * @param pcause      probable cause
     * @param nid         notification identifier
     * @param atext       additional text
     * @return identificador de la alarma creada o null
     */
    public static String createAlarm(Scenario theScenario, String oc, String mo, String sev, String atype,
            String pcause, String nid, String atext) {
        if (theScenario == null) {
            return null;
        }
        if (oc == null || oc.trim().isEmpty()) {
            theScenario.getLogger().error("No se puede crear alarma: Operation Context (OC) es requerido");
            return null;
        }
        if (mo == null || mo.trim().isEmpty()) {
            theScenario.getLogger().error("No se puede crear alarma: Managed Object (MO) es requerido");
            return null;
        }

        String entityName = oc.trim() + " ALARM 0";

        theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");
        theScenario.getLogger().info("INICIANDO CREACIÓN DE ALARMA");
        theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");
        theScenario.getLogger().info("  Managed Object    : {}", mo);
        theScenario.getLogger().info("  Additional Text   :");
        theScenario.getLogger().info("{}", atext != null ? atext : "(vacío)");
        theScenario.getLogger().info("─────────────────────────────────────────────────────────────");

        try {
            Action action = new Action("TeMIP_AOAction");
            action.addCommand("directiveName", "CREATE");
            action.addCommand("entityName", entityName);
            action.addCommand("Managed_Object", mo);
            if (sev != null && !sev.trim().isEmpty()) {
                action.addCommand("Perceived_Severity", sev);
            }
            if (atype != null && !atype.trim().isEmpty()) {
                action.addCommand("Alarm_Type", atype);
            }
            if (pcause != null && !pcause.trim().isEmpty()) {
                action.addCommand("Probable_Cause", pcause);
            }
            if (nid != null && !nid.trim().isEmpty()) {
                action.addCommand("Notification_Identifier", nid);
            }
            if (atext != null && !atext.trim().isEmpty()) {
                action.addCommand("Additional_Text", atext);
            }

            theScenario.addAction(action);
            action.executeSync();

            String createdAlarmId = null;
            if (action.getListActionResponseItem() != null && !action.getListActionResponseItem().isEmpty()) {
                for (ActionResponseItem item : action.getListActionResponseItem()) {
                    if (item != null && item.getOutput() != null && item.getOutput().getEntry() != null) {
                        for (ActionResponseItem.Output.Entry entry : item.getOutput().getEntry()) {
                            if (entry != null) {
                                String key = entry.getKey();
                                String value = entry.getValue();
                                if (key != null && value != null
                                        && (key.equalsIgnoreCase("entitySpec") || key.equalsIgnoreCase("Natural")
                                                || key.equalsIgnoreCase("entityName"))) {
                                    createdAlarmId = value;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            if (createdAlarmId == null || createdAlarmId.trim().isEmpty()) {
                createdAlarmId = String.format("operation_context %s alarm_object 0", oc.trim());
                theScenario.getLogger().warn("No se pudo obtener el ID de la alarma creada. Usando placeholder.");
            }
            theScenario.getLogger().info("  Alarma creada     : {}", createdAlarmId);
            theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");
            return createdAlarmId;
        } catch (Exception e) {
            theScenario.getLogger().error("═══════════════════════════════════════════════════════════════");
            theScenario.getLogger().error("ERROR al crear alarma: {}", e.getMessage(), e);
            theScenario.getLogger().error("═══════════════════════════════════════════════════════════════");
            return null;
        }
    }

    /**
     * Asocia una lista de hijas a un padre usando GROUPALARMS.
     */
    public static void associateChildrenToParent(Scenario theScenario, String parentIdentifier,
            List<String> childrenIdentifiers, Alarm alarmForConfig) {
        String temipNamespace = getTeMIPNamespace(alarmForConfig);
        associateChildrenToParentImpl(theScenario, parentIdentifier, childrenIdentifiers, temipNamespace);
    }

    private static void associateChildrenToParentImpl(Scenario theScenario, String parentIdentifier,
            List<String> childrenIdentifiers, String temipNamespace) {
        if (theScenario == null) {
            return;
        }
        if (parentIdentifier == null || parentIdentifier.trim().isEmpty()) {
            theScenario.getLogger()
                    .error("No se puede asociar alarmas hijas: el identificador de la alarma padre es requerido");
            return;
        }
        if (childrenIdentifiers == null || childrenIdentifiers.isEmpty()) {
            theScenario.getLogger().error(
                    "No se puede asociar alarmas hijas: la lista de identificadores de hijas está vacía");
            return;
        }

        List<String> validChildren = new ArrayList<>();
        for (String childId : childrenIdentifiers) {
            if (childId != null && !childId.trim().isEmpty()) {
                validChildren.add(childId.trim());
            }
        }
        if (validChildren.isEmpty()) {
            theScenario.getLogger()
                    .error("No se puede asociar alarmas hijas: no hay identificadores válidos de hijas");
            return;
        }

        theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");
        theScenario.getLogger().info("INICIANDO ASOCIACIÓN DE ALARMAS HIJAS (GROUPALARMS)");
        theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");
        theScenario.getLogger().info("  Parent Alarm ID       : {}", parentIdentifier);
        theScenario.getLogger().info("  Children Count        : {}", validChildren.size());

        try {
            Action action = new Action("TeMIP_AOAction");
            action.addCommand(AODirectiveKey.DIRECTIVE_NAME, "GROUPALARMS");
            action.addCommand(AODirectiveKey.ENTITY_NAME, parentIdentifier.trim());

            for (String childId : validChildren) {
                String temipChildId = convertUcaIdentifierToTemipFormat(childId, temipNamespace);
                theScenario.getLogger().info("    Convirtiendo Child: {} -> {}", childId, temipChildId);
                action.addCommand("Child", temipChildId);
            }

            theScenario.addAction(action);
            action.executeAsync(AODirectiveKey.ENTITY_NAME);

            theScenario.getLogger().info("─────────────────────────────────────────────────────────────");
            theScenario.getLogger().info("ACCIÓN DE ASOCIACIÓN ENVIADA:");
            theScenario.getLogger().info("  Action ID             : {}", action.getActionId());
            theScenario.getLogger().info("  Parent Alarm ID       : {}", parentIdentifier);
            theScenario.getLogger().info("  Children Count        : {}", validChildren.size());
            theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");
        } catch (Exception e) {
            theScenario.getLogger().error("ERROR al asociar alarmas hijas: {}", e.getMessage(), e);
            theScenario.getLogger().error("Detalles: Parent ID={}, Children Count={}", parentIdentifier,
                    validChildren.size());
        }
    }

    /**
     * Asocia una única hija a un padre (GROUPALARM simplificado).
     */
    public static void associateChildToParent(Scenario theScenario, String parentIdentifier, String childIdentifier,
            Alarm alarmForConfig) {
        List<String> children = new ArrayList<>();
        children.add(childIdentifier);
        associateChildrenToParent(theScenario, parentIdentifier, children, alarmForConfig);
    }

    /**
     * Asocia un padre a una hija usando ADDPARENT.
     */
    public static void associateParentToChild(Scenario theScenario, String childIdentifier, String parentIdentifier,
            Alarm alarmForConfig) {
        String temipNamespace = getTeMIPNamespace(alarmForConfig);

        if (theScenario == null) {
            return;
        }
        if (childIdentifier == null || childIdentifier.trim().isEmpty()) {
            theScenario.getLogger()
                    .error("No se puede asociar padre a hijo: el identificador del hijo es requerido");
            return;
        }
        if (parentIdentifier == null || parentIdentifier.trim().isEmpty()) {
            theScenario.getLogger()
                    .error("No se puede asociar padre a hijo: el identificador del padre es requerido");
            return;
        }

        theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");
        theScenario.getLogger().info("INICIANDO ASOCIACIÓN DE PADRE A HIJO (ADDPARENT)");
        theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");
        theScenario.getLogger().info("  Child Alarm ID (UCA)  : {}", childIdentifier);
        theScenario.getLogger().info("  Parent Alarm ID (UCA) : {}", parentIdentifier);

        try {
            Action action = new Action("TeMIP_AOAction");
            action.addCommand(AODirectiveKey.DIRECTIVE_NAME, "ADDPARENT");
            action.addCommand(AODirectiveKey.ENTITY_NAME, childIdentifier.trim());

            String temipParentId = convertUcaIdentifierToTemipMasterFormat(parentIdentifier, temipNamespace);
            theScenario.getLogger().info("  Parent Alarm ID (TeMIP): {}", temipParentId);
            action.addCommand("PARENT", temipParentId);

            theScenario.addAction(action);
            action.executeAsync(AODirectiveKey.ENTITY_NAME);

            theScenario.getLogger().info("─────────────────────────────────────────────────────────────");
            theScenario.getLogger().info("ACCIÓN DE ASOCIACIÓN DE PADRE ENVIADA:");
            theScenario.getLogger().info("  Action ID             : {}", action.getActionId());
            theScenario.getLogger().info("  Child Alarm ID        : {}", childIdentifier);
            theScenario.getLogger().info("  Parent Alarm ID       : {}", temipParentId);
            theScenario.getLogger().info("═══════════════════════════════════════════════════════════════");
        } catch (Exception e) {
            theScenario.getLogger().error("ERROR al asociar padre a hijo: {}", e.getMessage(), e);
            theScenario.getLogger().error("Detalles: Child ID={}, Parent ID={}", childIdentifier, parentIdentifier);
        }
    }
}
