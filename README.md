# Hospital Distributed System - Trabajo Final

Este proyecto implementa una simulación de un sistema distribuido para una red de 5 hospitales. El sistema está estructurado bajo una arquitectura robusta de **Backend (Java Spring Boot)** y **Frontend (React)**.

## Objetivos Alcanzados

Se han implementado los siguientes algoritmos distribuidos requeridos en la especificación del trabajo final:

1. **OE1 - Algoritmo Bully:** Implementado para la elección automática del nodo coordinador (Líder) cuando este falla. Los nodos se monitorean periódicamente (Ping) y, ante la ausencia de respuesta, el nodo que lo detecta inicia una elección enviando un mensaje a los nodos de ID superior.
2. **OE2 - Algoritmo Berkeley:** Implementado para la sincronización periódica de relojes. El coordinador solicita el tiempo lógico de cada nodo activo, calcula el promedio, y envía los offsets (ajustes) necesarios a cada nodo para mantener la equivalencia temporal.
3. **OE3 - Vector Clock (Relojes Vectoriales):** Implementado para registrar y ordenar causalmente las operaciones de registro de donantes. Cada nodo mantiene un vector de estado `[0,0,0,0,0]` que se actualiza al crear o recibir un donante. La lista de donantes se ordena automáticamente respetando la precedencia causal de los eventos.

## Arquitectura Tecnológica (Sistema Distribuido Físico)

- **Backend:** Desarrollado en Java usando `Spring Boot` y `RestTemplate`. A diferencia de arquitecturas centralizadas, este sistema opera de forma totalmente descentralizada (Peer-to-Peer). Cada máquina física ejecuta su propio servidor que actúa como un **Nodo Único**, comunicándose con los demás a través de peticiones HTTP en red local.
- **Frontend:** Desarrollado en React y Vite. Interfaz moderna (Dark Mode, Glassmorphism) que actúa como el panel de monitoreo individual de cada hospital/nodo.

## Instrucciones de Instalación y Uso

### 1. Requisitos Previos en TODAS las Máquinas
- [Java Development Kit (JDK)](https://adoptium.net/) (Versión 17 o superior) y Maven instalado.
- [Node.js](https://nodejs.org/es/) (Versión 18 o superior) para el frontend.
- Sistema Operativo **Linux** (obligatorio para el cambio automático de hora por hardware de Berkeley).

### 2. Configurar Direcciones IP (Static Peer Discovery)
Las 4 máquinas deben tener IPs asignadas en la misma subred.
Abre el archivo `backend/src/main/resources/application.properties` en TODAS las computadoras y edita la lista de IPs para que coincidan.
El orden de las IPs dictará el ID del Nodo:
```properties
hospital.nodes.ips=192.168.1.10,192.168.1.11,192.168.1.12,192.168.1.13
```

### 3. Levantar el Backend (En cada máquina)
Abre la terminal en la raíz del proyecto y ejecuta el servidor con **permisos de administrador** (crítico para que el comando `date -s` funcione en Linux):
```bash
cd backend
sudo mvn clean compile
sudo mvn spring-boot:run
```
*NOTA: El backend interno usará el puerto `8085` para la comunicación REST inter-nodos y el puerto `3001` para WebSockets con el Frontend.*

### 4. Levantar el Frontend (En cada máquina)
En una **nueva terminal**, sin permisos de admin, ejecuta:
```bash
cd frontend
npm run dev -- --host
```

### 5. Acceso al Panel
Abre el navegador en cada computadora e ingresa a `http://localhost:5173`. 
Cada máquina verá su propio hospital. La tabla de "Nodos de la Red" hará un rastreo en vivo de las demás computadoras físicas mediante su IP, y las acciones que realicen se propagarán a la "Lista de Donantes".

## Uso del Panel

- **Nodos de la Red:** Visualiza el Coordinador actual, el reloj individual (sincronizado por Berkeley) y el Reloj Vectorial de cada nodo. Usa los botones "Tumbar" y "Recuperar" para forzar fallos y visualizar cómo el **Algoritmo Bully** escoge un nuevo líder.
- **Agregar Donante:** Agrega un donante simulando el ingreso de un paciente por un hospital. El **Reloj Vectorial** se incrementará y la base de datos se actualizará para todos respetando el orden causal en la "Lista de Donantes".
- **Registros del Sistema:** Registro en tiempo real de todos los eventos del clúster (elecciones, sincronización y fallos).

## ⚠️ Solución de Problemas Frecuentes (Troubleshooting)

1. **Error `Address already in use` en puerto 3001 o 8085**
   Si el backend lanza error de puerto al iniciar, es porque hay un proceso de Java o Node.js "zombie" oculto en segundo plano. Bórralo con:
   ```bash
   sudo fuser -k 3001/tcp
   sudo killall java
   ```

2. **Aparecen 2 o más Coordinadores (Split-Brain / Cerebro Dividido)**
   Si ves múltiples "👑" en la tabla, significa que la red está fragmentada. Esto ocurre estrictamente cuando las computadoras **no tienen el mismo código** o la lista de IPs en `application.properties` no es idéntica en todas las PCs. Asegúrate de pasar el proyecto exacto por USB/GitHub a tu compañero, y que ambos estén conectados al mismo WiFi.
