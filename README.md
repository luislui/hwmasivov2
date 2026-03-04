# hwmasivov2 — Reglas Drools

Este proyecto define un conjunto de reglas (Drools) para el procesamiento de alarmas **NE Is Disconnected** en TeMIP: parseo, agrupación en Problem Alarm (PA), asociación padre-hijo, creación de Trouble Tickets (TT) y validación de existencia de padres/hijas. Los incidentes TT se persisten en **SQLite** (JDBC) para que este y otros proyectos puedan consultar si existe un neName con idSitio en estado abierto.

---

## Reglas

### 1. Rule [Parseo de nueva alarma]

**Archivo:** `hwmasivov2.drl`  
**Cuándo:** Cualquier alarma recién insertada en Working Memory (`justInserted == true`).

**Qué hace:** Ejecuta el parseo de la alarma llamando a `Hwmasivov2.parseAlarm(a)`. Extrae del `additionalText` campos como `alarmName`, `neName`, `idSitio`, `SHW_id`, etc., y los guarda en custom fields. Luego actualiza la alarma en la sesión.

---

### 2. Rule [Agrupar NE Is Disconnected]

**Archivo:** `hwmasivov2.drl`  
**Cuándo:** Se dispara con un `TickFlag` cuando existe una alarma “semilla” **NE Is Disconnected** (parseada, sin padre, no PA) que cumple:

- Han pasado al menos `SecondsToConsiderNeDisconnected` segundos desde la semilla (para dar tiempo a que lleguen más alarmas).
- La semilla no es más vieja que `SecondsMaxAgeForNeAlarm` segundos.
- Se pueden recolectar al menos `NumAlarmsNeDisconnected` alarmas NE Is Disconnected en esa ventana de tiempo (sin contar PAs).
- Hay al menos `NumAlarmsNeDisconnected` **idSitio distintos** entre las alarmas recolectadas.
- No existe ya un `Flag` con id `NE_DISC_PA` (evita crear varias PAs en paralelo).

**Qué hace:** Inserta un `Flag` con id `NE_DISC_PA` y descripción `creating_<idSitio>`, llama a `AlarmGroupingV2.createNeDisconnectedPA(a, alarms)` para crear la Problem Alarm padre y marcar las alarmas como hijas, y pone `stage = 2` en todas las alarmas agrupadas.

---

### 3. Rule [Alarma NE Is Disconnected debe asociarse como hija]

**Prioridad:** `salience 20` (mayor que la regla de agrupación).

**Cuándo:** Hay una alarma **NE Is Disconnected** sin padre (`parents == null`), en `stage == 1`, con `idSitio`, y existe una PA cuyo `additionalText` contiene `PB=ProblemAlarm` y una línea con `[idSitio]` de esa alarma. La hija no debe ser demasiado vieja respecto a la PA (según `SecondsMaxAgeForNeAlarm`).

**Qué hace:** Asocia la alarma como hija de la PA existente: llama a `TemipDirectives.associateChildToParent` (GROUPALARMS) y `TemipDirectives.associateParentToChild` (ADDPARENT), y marca la hija con `stage = 2` para que no entre en una nueva agrupación.

---

### 4. Rule [Eliminar Flag cuando PA llega a WM]

**Cuándo:** Llega una alarma PA (`additionalText` contiene `PB=ProblemAlarm`) con `paSemillaId` y existe un `Flag` con id `NE_DISC_PA` y descripción `creating_` + ese `paSemillaId`.

**Qué hace:** Retira ese `Flag` de la sesión. Así se desbloquea la lógica para futuras agrupaciones cuando la PA que se creó (con ese idSitio en el MO) ya está en Working Memory.

---

### 5. Rule [Crear TT de PA]

**Cuándo:** Hay un `TickFlag`, una PA en `stage == 1` con `paSemillaId`, sin ticket (ni `handledByTicketId` ni `problemInformation` según `Hwmasivov2.alarmHasTicket`), y han pasado al menos `SecondsToleranceForTT` segundos desde el `eventTime` de la PA.

**Qué hace:** Llama a `ServiceManager.createTroubleTicket(theScenario, a)` para crear el TT; si se obtiene un ID, lo guarda en `handledByTicketId`, pone la PA en `stage = 2` y llama a `TTIncidentRegistry.registerTTIncident`: escribe en SQLite una fila por cada neName único del additionalText (id_sitio = valor entre corchetes), estado `abierto`. La regla no termina hasta que todos los INSERT han finalizado.

---

### 6. Rule [Asociar ticket a hijas]

**Cuándo:** Existe una PA con `PB=ProblemAlarm` que ya tiene ticket (`Hwmasivov2.alarmHasTicket(pa)`), y una alarma que es hija de esa PA (según `Hwmasivov2.getParentIdentifiers`) y que aún no tiene ticket.

**Qué hace:** Copia el `handledByTicketId` de la PA a la hija, actualiza la alarma en la sesión, envía la directiva HANDLE en TeMIP con `TemipDirectives.handleAlarm` y llama a `TTIncidentRegistry.registerTTIncident`: escribe en SQLite una fila con ne_name e id_sitio de la hija, estado `abierto`.

---

### 7. Rule [Validar existencia de padre]

**Cuándo:** Una alarma tiene `parents` no vacío (`Hwmasivov2.getParentIdentifiers(child).size() > 0`) y no se ha procesado aún (`parentValidatedProcessed` es null), y al menos uno de los padres **no está** en Working Memory.

**Qué hace:** Log de advertencia; si la hija tiene ticket (`Hwmasivov2.getTicketId`), envía `TemipDirectives.closeAlarm` para cerrar la alarma hija en TeMIP con ese ticket. Marca la hija con `parentValidatedProcessed = "true"`.

---

### 8. Rule [Validar existencia de hijas]

**Cuándo:** Una PA tiene hijas declaradas (`Hwmasivov2.getChildIdentifiers(pa).size() > 0`) y no se ha procesado aún (`childrenValidatedProcessed` es null), y **no existe** ninguna alarma en WM que sea hija de esa PA.

**Qué hace:** Log de advertencia y llamada a `TemipDirectives.terminateAlarm` para terminar la alarma padre en TeMIP. Marca la PA con `childrenValidatedProcessed = "true"`.

---

### 9. Rule [Alarm Attribute Value Change]

**Cuándo:** Una alarma ya existente en WM (no recién insertada, no a punto de ser retirada) tiene `hasAVCChanged == true`.

**Qué hace:** Llama a `Hwmasivov2.alarmAttributeChange(a)` para procesar el cambio de atributos.

---

### 10. Rule [Alarm State Change]

**Cuándo:** Una alarma ya existente en WM tiene `hasStateChanged == true` y no está a punto de ser retirada.

**Qué hace:** Llama a `Hwmasivov2.alarmStateChange(a)` para procesar el cambio de estado.

---

### 11. Rule [Alarm no more eligible]

**Cuándo:** Una alarma tiene `aboutToBeRetracted == true` (ya no cumple la política de elegibilidad, p. ej. `NetworkState == "CLEARED"`).

**Qué hace:** Registra en log que la alarma ya no es elegible. La retirada de WM la realiza el motor según la política.

---

## Parámetros (tags)

Configuración típica en filtros/tags:

| Tag | Uso |
|-----|-----|
| `SecondsToConsiderNeDisconnected` | Ventana en segundos para recolectar alarmas NE Is Disconnected antes de crear la PA. |
| `SecondsMaxAgeForNeAlarm` | Edad máxima en segundos de una alarma NE Is Disconnected para ser considerada (agrupación o asociación como hija). |
| `NumAlarmsNeDisconnected` | Número mínimo de alarmas (y de idSitio distintos) para crear la PA. |
| `SecondsToleranceForTT` | Segundos de espera desde el eventTime de la PA antes de crear el TT. |
| `CreateTTEnabled` | 1 = habilitar creación de TT para la PA; 0 = deshabilitado. |
| `TTRegistryEnabled` | 1 = persistir incidentes TT en SQLite; 0 = no escribir en BD. |
| `TTRegistryJdbcUrl` | URL JDBC de la base SQLite (ej. `jdbc:sqlite:tt_incident.db`). Se define en `filters-file.xml` y `filtersTags.xml`. |
| `TTRegistryTableName` | Nombre de la tabla (por defecto `UCA_TT_INCIDENT`). |

Los tags se configuran en **filtersTags.xml** (definición y valores por defecto) y en **filters-file.xml** (valores aplicados por el filtro `tags` a las alarmas que pasan).

---

## Registro TT en SQLite

Los incidentes TT se persisten en una base **SQLite** vía JDBC. La clase `TTIncidentRegistry` escribe en la tabla `UCA_TT_INCIDENT`:

- **PA**: una fila por cada **neName único** del `additionalText` (líneas `neName [idSitio]`); `id_sitio` es el valor entre corchetes.
- **Hija**: una fila con `ne_name` e `id_sitio` de los custom fields de la alarma.

**Tabla** (script en `src/main/resources/valuepack/hwmasivov2/sql/create_tt_registry.sql`):

| Columna | Tipo | Descripción |
|--------|------|-------------|
| `id` | INTEGER | PK, AUTOINCREMENT |
| `incident_id` | TEXT | Número del TT |
| `ne_name` | TEXT | Nombre del NE |
| `id_sitio` | TEXT | Id. del sitio |
| `estado` | TEXT | `abierto` o `cerrado` |
| `fecha_creacion` | TEXT | Hora local, rellenada por la BD |
| `fecha_actualizacion` | TEXT | Hora local, actualizada por trigger |

- **UNIQUE(incident_id, ne_name, id_sitio)** para evitar duplicados.
- **Índices**: `incident_id`; y `(ne_name, id_sitio, estado)` para consultas del tipo “¿existe incidente abierto para este NE y sitio?”.

Crear la BD una vez, por ejemplo: `sqlite3 tt_incident.db < src/main/resources/valuepack/hwmasivov2/sql/create_tt_registry.sql`. La ruta del fichero SQLite se define en el tag `TTRegistryJdbcUrl`.

---

## Flujo resumido

1. **Llega alarma** → Parseo (Rule 1).
2. **Alarmas NE Is Disconnected** → O se asocian como hijas de una PA existente (Rule 3) o, si se cumple número y ventana, se agrupan en una nueva PA (Rule 2).
3. **Cuando la PA creada llega a WM** → Se elimina el Flag (Rule 4).
4. **PA sin ticket y con antigüedad suficiente** → Se crea TT y se registra en TTIncidentRegistry (Rule 5).
5. **Hijas de una PA con ticket** → Se les asigna el mismo ticket y se registran (Rule 6).
6. **Padre ausente en WM** → Se cierra la hija con su ticket (Rule 7). **Sin hijas activas en WM** → Se termina la PA (Rule 8).
7. **Cambios de atributos/estado y retirada** → Rules 9, 10 y 11.
