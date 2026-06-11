Simulacion del Algoritmo Bully - Red Hospitalaria
===================================================

Arquitectura: 5 nodos (IDs 1-5, puertos 5001-5005)
Protocolo: ELECTION:ID, OK, COORDINATOR:ID, PING, PONG

Como usar
---------
1. Iniciar todos los nodos:
   $ ./iniciar.sh

2. En otra terminal, matar un nodo:
   $ java KillNode <id>

3. Para compilar manualmente:
   $ javac HospitalNode.java KillNode.java

Escenarios de prueba (Fase 4)
-----------------------------

Escenario 1: Caida del coordinador principal
  - Matar Nodo 5: java KillNode 5
  - Resultado esperado: Nodo 4 debe asumir como coordinador

Escenario 2: Caidas encadenadas de los dos nodos de mayor ID
  - Matar Nodo 5: java KillNode 5
  - Esperar a que Nodo 4 asuma
  - Matar Nodo 4: java KillNode 4
  - Resultado esperado: Nodo 3 debe asumir como coordinador

Escenario 3: Caida de nodo intermedio sin efecto en coordinacion
  - Matar Nodo 3: java KillNode 3
  - Resultado esperado: Nodo 5 sigue siendo coordinador

Escenario 4: Caida simultanea de dos nodos
  - Matar Nodo 5 y Nodo 4 rapidamente: java KillNode 5 && java KillNode 4
  - Resultado esperado: Nodo 3 debe asumir como coordinador

Escenario 5: Recuperacion con nodo superior
  - Matar Nodo 5, dejar que Nodo 4 asuma
  - Reiniciar Nodo 5: java HospitalNode 5
  - Resultado esperado: Nodo 5 debe desplazar a Nodo 4 como coordinador

Diagrama metodologico (Figura 1)
---------------------------------
[Fase 1: Modelado] --> [Fase 2: Deteccion Fallos] --> [Fase 3: Implementacion] --> [Fase 4: Validacion]
