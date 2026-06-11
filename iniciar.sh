#!/bin/bash
# Script para iniciar los 5 nodos hospitalarios
# Se inician en orden descendente para que el Nodo 5 sea el primer coordinador

echo "=== Iniciando Red Hospitalaria (Algoritmo Bully) ==="
echo ""

# Compilar primero
echo "Compilando..."
javac *.java 2>/dev/null
if [ $? -ne 0 ]; then
    echo "Error de compilacion. Verifique los archivos."
    exit 1
fi
echo "Compilacion exitosa."
echo ""

# Iniciar nodos del 5 al 1
for i in 5 4 3 2 1; do
    echo "Iniciando Nodo $i en puerto $((5000 + i))..."
    java HospitalNode $i &
    sleep 0.8
done

echo ""
echo "=== Todos los nodos iniciados ==="
echo "Para detener un nodo: java KillNode <id>"
echo "Para detener todo: Ctrl+C"
echo ""

# Esperar a que los procesos terminen
wait
