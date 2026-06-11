# Hospital Distributed System - Trabajo Final

Este proyecto implementa una simulación de un sistema distribuido para una red de 5 hospitales. El sistema está estructurado bajo una arquitectura robusta de **Backend (Java Spring Boot)** y **Frontend (React)**.

## Objetivos Alcanzados

Se han implementado los siguientes algoritmos distribuidos requeridos en la especificación del trabajo final:

1. **OE1 - Algoritmo Bully:** Implementado para la elección automática del nodo coordinador (Líder) cuando este falla. Los nodos se monitorean periódicamente (Ping) y, ante la ausencia de respuesta, el nodo que lo detecta inicia una elección enviando un mensaje a los nodos de ID superior.
2. **OE2 - Algoritmo Berkeley:** Implementado para la sincronización periódica de relojes. El coordinador solicita el tiempo lógico de cada nodo activo, calcula el promedio, y envía los offsets (ajustes) necesarios a cada nodo para mantener la equivalencia temporal.
3. **OE3 - Vector Clock (Relojes Vectoriales):** Implementado para registrar y ordenar causalmente las operaciones de registro de donantes. Cada nodo mantiene un vector de estado `[0,0,0,0,0]` que se actualiza al crear o recibir un donante. La lista de donantes se ordena automáticamente respetando la precedencia causal de los eventos.

## Arquitectura Tecnológica

- **Backend:** Desarrollado en Java usando `Spring Boot` y `WebSockets`. El backend encapsula la lógica concurrente de los 5 nodos de manera eficiente mediante el uso de hilos (Threads), simulando los eventos de red, latencias y fallos sin necesidad de gestionar manualmente puertos bloqueados a nivel de OS.
- **Frontend:** Desarrollado en React y Vite. Interfaz moderna, con temática oscura (Dark Mode), Glassmorphism y diseño responsivo, usando CSS puro sin dependencias externas de diseño. Permite visualizar el estado en tiempo real, simular caídas y observar los logs del sistema.

## Instrucciones de Instalación y Uso

### 1. Requisitos Previos
- [Java Development Kit (JDK)](https://adoptium.net/) (Versión 17 o superior) y Maven instalado.
- [Node.js](https://nodejs.org/es/) (Versión 18 o superior) para el frontend.

### 2. Levantar el Backend (Lógica Distribuida en Java)
En una terminal, ubícate en la carpeta raíz del proyecto y ejecuta:

```bash
cd backend
mvn spring-boot:run
```
El servidor backend correrá en `http://localhost:8080` y el puerto de WebSockets en `3001` y comenzará a generar los procesos concurrentes.

### 3. Levantar el Frontend (Interfaz de Monitoreo)
En una nueva terminal, ubícate en la carpeta raíz del proyecto y ejecuta:

```bash
cd frontend
npm install
npm run dev
```
Accede a la URL indicada (generalmente `http://localhost:5173`) desde el navegador.

## Uso del Panel

- **Network Nodes:** Puedes visualizar el Coordinador actual, el reloj individual (sincronizado por Berkeley) y el Vector Clock de cada nodo. Usa los botones "Fail Node" y "Recover Node" para simular fallos y visualizar cómo el **Algoritmo Bully** escoge un nuevo líder.
- **Add Donor Operation:** Agrega un donante simulando que la solicitud entró por un nodo específico. Verás cómo el **Vector Clock** se incrementa y la información se propaga ordenadamente.
- **System Logs:** Registro en tiempo real de todos los eventos del sistema.
