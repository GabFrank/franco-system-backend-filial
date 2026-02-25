#!/bin/bash

# Este script ejecuta la aplicación usando la versión correcta de Java y Maven
# Se asegura de cargar todas las dependencias y el classpath correctamente.

export JAVA_HOME=/home/ultron/opt/java/jdk-17.0.17+10
export PATH=$JAVA_HOME/bin:$PATH

echo "Iniciando Franco Systems Backend..."
echo "Java version en uso:"
$JAVA_HOME/bin/java -version

echo "Ejecutando con Maven..."
./mvnw spring-boot:run
