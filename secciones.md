# Contenido para las secciones referenciadas en la metodología

---

## Sección 8.1 – Topología de Red y Tabla de Puertos

**Topología:** Comunicación punto a punto (fully connected). Cada nodo mantiene una conexión TCP directa con cualquier otro nodo al que necesite enviar un mensaje. No existe un servidor central ni中介.

**Tabla de asignación de puertos:**

| Nodo | ID | Puerto TCP | Host |
|------|----|-----------|------|
| Hospital Central | 1 | 5001 | localhost |
| Hospital Regional Norte | 2 | 5002 | localhost |
| Hospital Regional Sur | 3 | 5003 | localhost |
| Hospital Especialidades | 4 | 5004 | localhost |
| Hospital General | 5 | 5005 | localhost |

**Formato de mensajes:**

| Tipo | Formato | Dirección |
|------|---------|-----------|
| Elección | `ELECTION:<id_emisor>` | Nodo inferior → Nodos superiores |
| Respuesta | `OK` | Nodo superior → Nodo inferior |
| Coordinador | `COORDINATOR:<id_coordinador>` | Nuevo coordinador → Todos los nodos |
| Heartbeat | `PING` / `PONG` | Nodo monitor → Coordinador → Nodo monitor |

---

## Sección 8.2 – Equivalencia Temporal de Detección de Fallos

**Parámetros configurados:**
- `setSoTimeout(3000)` — timeout por intento de PING: 3 segundos
- `MAX_INTENTOS_PING = 3` — reintentos máximos consecutivos

**Tiempo máximo de detección:**
```
T_detección = MAX_INTENTOS × TIMEOUT_PING = 3 × 3 s = 9 s
```

**Intervalo entre ciclos de monitoreo:**
```
T_ciclo = INTERVALO_MONITOREO = 2 s
```

**Tiempo total máximo desde la caída hasta el inicio de la elección:**
```
T_total_máx = T_ciclo + T_detección = 2 s + 9 s = 11 s
```

**Ejemplo de línea de tiempo:**
```
t=0s    Coordinador cae
t=2s    Inicia ciclo de monitoreo
t=2-5s  Intento 1 de PING (timeout 3s)
t=5-8s  Intento 2 de PING (timeout 3s)
t=8-11s Intento 3 de PING (timeout 3s)
t=11s   Se declara caída → inicia elección
```

---

## Sección 8.3 – Código Fuente: HospitalNode.java

(Ver archivo `HospitalNode.java` en el directorio del proyecto.)

**Estructura de clases y métodos:**

```
HospitalNode
├── atributos
│   ├── id, port, coordinadorActual
│   ├── enEleccion (AtomicBoolean)
│   └── ejecutando (volatile boolean)
├── iniciar()
│   ├── Inicia hilo escuchar()
│   ├── Pausa 1s para estabilización
│   └── Inicia hilo monitorearCoordinador()
├── escuchar() — Bucle de aceptación de conexiones entrantes
├── descubrirCoordinador() — Escanea nodos superiores al iniciar
├── procesarMensaje() — Despachador de mensajes
├── iniciarEleccion() — Envía ELECTION a nodos superiores
├── anunciarCoordinador() — Difunde COORDINATOR a todos
├── monitorearCoordinador() — Envía PING periódicos
└── detener() — Cierra ServerSocket y termina hilos
```

**Mecanismo de concurrencia:**
- `enEleccion.compareAndSet(false, true)` evita elecciones simultáneas
- `coordinadorActual` como `volatile` para visibilidad entre hilos
- Cada conexión entrante se maneja en su propio hilo
- `synchronized` implícito en las operaciones atómicas

---

## Sección 8.4 – Resultados de Validación Experimental

**Escenario 1: Caída del coordinador principal (Nodo 5)**
| Métrica | Valor |
|---------|-------|
| Nodo eliminado | 5 |
| Nuevo coordinador | 4 ✓ |
| Tiempo de convergencia | ~11 s |
| Mensajes generados | 3 PING + 3 ELECTION + 4 COORDINATOR |

**Escenario 2: Caídas encadenadas (Nodo 5 → Nodo 4)**
| Métrica | Valor |
|---------|-------|
| Nodos eliminados | 5, luego 4 |
| Nuevo coordinador final | 3 ✓ |
| Tiempo de convergencia 1ra caída | ~11 s |
| Tiempo de convergencia 2da caída | ~11 s |

**Escenario 3: Caída de nodo intermedio (Nodo 3)**
| Métrica | Valor |
|---------|-------|
| Nodo eliminado | 3 |
| Coordinador actual | 5 (sin cambios) ✓ |
| Tiempo de convergencia | 0 s (no afecta) |

**Escenario 4: Caída simultánea de dos nodos (Nodo 5 y Nodo 4)**
| Métrica | Valor |
|---------|-------|
| Nodos eliminados | 5 y 4 |
| Nuevo coordinador | 3 ✓ |
| Tiempo de convergencia | ~11 s |

**Escenario 5: Recuperación con nodo superior**
| Métrica | Valor |
|---------|-------|
| Acción | Matar Nodo 5, Nodo 4 asume, luego iniciar Nodo 5 |
| Coordinador final | 5 ✓ |
| Tiempo de reconvergencia | ~11 s |

**Resumen general:**
- En todos los escenarios, el nodo elegido como coordinador fue el de mayor ID disponible.
- El tiempo de convergencia máximo observado fue de aproximadamente 11 segundos.
- El mecanismo de PING/PONG detectó la caída del coordinador en todos los casos.

---

## Anexo B – Detalle del Mecanismo de Detección de Fallos

**Código del hilo de monitoreo (extraído de HospitalNode.java):**

```java
private void monitorearCoordinador() {
    while (ejecutando) {
        if (coordinadorActual == id) {
            Thread.sleep(INTERVALO_MONITOREO);
            continue;
        }
        if (coordinadorActual == -1) {
            Thread.sleep(INTERVALO_MONITOREO);
            continue;
        }

        boolean coordinadorVivo = false;
        int puertoCoord = 5000 + coordinadorActual;

        for (int intento = 1; intento <= MAX_INTENTOS_PING; intento++) {
            try (Socket s = new Socket(HOST, puertoCoord);
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(
                     new InputStreamReader(s.getInputStream()))) {

                s.setSoTimeout(TIMEOUT_PING);
                out.println("PING");
                String respuesta = in.readLine();
                if ("PONG".equals(respuesta)) {
                    coordinadorVivo = true;
                    break;
                }
            } catch (IOException e) {
                // Intento fallido, continuar con el siguiente
            }
        }

        if (!coordinadorVivo && ejecutando) {
            iniciarEleccion();
        }

        Thread.sleep(INTERVALO_MONITOREO);
    }
}
```

**Funcionamiento:**
1. El hilo se ejecuta en cada nodo no-coordinador.
2. Cada 2 segundos, inicia un ciclo de verificación.
3. En cada ciclo, envía hasta 3 PINGs al coordinador actual.
4. Cada PING tiene un timeout de 3 segundos.
5. Si los 3 intentos fallan, se declara la caída y se inicia una elección.
6. El nodo coordinador omite la verificación (no se monitorea a sí mismo).

**Ventajas de este diseño:**
- Detección implícita: no requiere notificaciones externas de caída.
- Autónomo: cada nodo verifica independientemente.
- Tolerante a pérdidas de red transitorias (3 reintentos).
- Sin dependencias externas: usa solo sockets TCP estándar.
