# Core de Adquirencia — PagueloFacil

Panel central del área de adquirencia: consolida el estado global del procesamiento
(PowerTranz, Evertec) y sirve como punto de entrada a las herramientas y datos del
área — proyectos, credenciales/DBAs, puntos de pago y documentación.

Implementa el alcance de la propuesta de proyecto del equipo de adquirencia
(Richard Mosqueda, Ahiezer Dominguez) con la identidad visual del manual de marca
de PagueloFacil.

## Qué incluye

**Dashboard global** (vista principal):

- Volumen de procesamiento con comparativo vs. periodo anterior.
- Cantidad de transacciones: aprobadas, rechazadas y tasa de aprobación.
- Alertas de procesamiento: caída de canal, pico de rechazos, códigos DR recurrentes, fallas 3DS, errores internos.
- Índice de reembolsos (% y monto sobre el total procesado).
- Procesamiento por canal de adquirente (PowerTranz / Evertec) con tasa de aprobación por canal.
- Uso por tipo de servicio: Auth/Capture, recurrencia, link de pago, checkout, API.
- 3DS vs. sin 3DS con tasa de aprobación de cada grupo.
- Códigos DR más frecuentes del periodo.

**Filtros del resumen**: por comercio individual, por fecha (hoy, 7/30/90 días,
mes actual o rango personalizado) y por servicio. Incluye comparador entre
comercios (hasta 4).

**Configuración de alertas**: reglas estándar (globales, aplican por defecto) y
reglas por comercio, con condiciones de tasa de rechazo, cantidad de rechazadas,
reembolsos, fallas 3DS, código DR recurrente, caída de canal y errores internos.
Las alertas activas se evalúan sobre los datos del día y escalan su severidad
según cuánto exceden el umbral.

**Tabs laterales** (navegación a la derecha, según la propuesta):

| Tab | Contenido |
|---|---|
| Dashboard | Vista global del procesamiento |
| Proyectos | Iniciativas del área, estado y responsables |
| Credenciales | Inventario de credenciales/DBAs por procesador y comercio, con reenvío |
| Puntos de pago | POS y dispositivos por comercio y banco (Towerbank, BAC) |
| Documentos | Códigos de rechazo, guías por procesador, procedimientos |
| Alertas | Configuración de reglas globales y por comercio |

## Stack

- **Backend**: Java 21 + Spring Boot 3 (API REST, sin base de datos: almacén en
  memoria con datos de demostración deterministas).
- **Frontend**: HTML/CSS/JS sin dependencias, servido por Spring Boot desde
  `src/main/resources/static`. Gráficas SVG propias con tooltips.
- **Marca**: paleta y tipografía (Encode Sans) del manual de marca. Los colores
  de las gráficas derivan de la marca pero están ajustados y validados para
  contraste (≥3:1) y visión de color (separación CVD).

## Ejecutar

```bash
mvn spring-boot:run
# o
mvn -DskipTests package && java -jar target/adquirencia-core-0.1.0.jar
```

Abrir <http://localhost:8080>.

## API

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/dashboard/summary?merchantId=&from=&to=&service=` | Resumen global o filtrado |
| GET | `/api/dashboard/compare?merchantIds=a,b&from=&to=` | Comparativo entre comercios |
| GET | `/api/alerts` · POST `/api/alerts/{id}/ack` | Alertas activas / reconocer |
| GET/POST/PUT/DELETE | `/api/alert-rules` | CRUD de reglas de alerta |
| GET | `/api/merchants`, `/api/projects`, `/api/credentials`, `/api/payment-points`, `/api/documents`, `/api/dr-codes` | Catálogos de los módulos |
| POST | `/api/credentials/{id}/resend` | Reenviar una credencial |

## Datos de demostración

`DataStore` genera ~90 días de transacciones con perfiles por comercio y dos
escenarios que disparan alertas reales: un pico de rechazos hoy en un comercio
(Tienda ModaPlus) y una degradación del canal Evertec (caída de volumen + código
91). Es el único punto a reemplazar para conectar el Core a las fuentes reales:
reportes/APIs de los procesadores y la base interna de transacciones.

## Próximos pasos sugeridos

1. Conectar `DataStore` a las fuentes reales (batch o near-real-time).
2. Persistencia (PostgreSQL) para reglas de alerta, ack y módulos.
3. Autenticación y permisos por rol dentro del área.
4. Notificaciones de alertas (correo / Slack / Teams).
5. Exportables (CSV/Excel) y módulo de certificaciones y casos.
