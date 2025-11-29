# Dockerfile para el servidor de chat  
FROM ubuntu:22.04 as builder

# Instalar OpenJDK 21 y Maven
RUN apt-get update && apt-get install -y openjdk-21-jdk maven && rm -rf /var/lib/apt/lists/*

# Crear directorio de trabajo
WORKDIR /app

# Copiar archivos de configuración de Maven primero (para cache de capas)
COPY pom.xml .

# Copiar los POM de módulos
COPY Datos/ConfigDB/pom.xml ./Datos/ConfigDB/
COPY DTO/pom.xml ./DTO/
COPY Dominio/Entidades/pom.xml ./Dominio/Entidades/
COPY Persistencia/Repositorios/pom.xml ./Persistencia/Repositorios/
COPY Aplicacion/Servicios/pom.xml ./Aplicacion/Servicios/
COPY Aplicacion/Bootstrap/pom.xml ./Aplicacion/Bootstrap/
COPY Aplicacion/Controladores/pom.xml ./Aplicacion/Controladores/
COPY Aplicacion/RestAPI/pom.xml ./Aplicacion/RestAPI/
COPY Presentacion/Vistas/pom.xml ./Presentacion/Vistas/

# Copiar código fuente
COPY . .

# Compilar el proyecto (sin try de optimización de cache por las dependencias internas)
RUN mvn clean package -DskipTests -B

# Etapa de ejecución
FROM ubuntu:22.04

# Instalar OpenJDK 21 JRE
RUN apt-get update && apt-get install -y openjdk-21-jre-headless && rm -rf /var/lib/apt/lists/*

# Crear usuario no-root para seguridad
RUN groupadd -r chatapp && useradd -r -g chatapp chatapp

# Crear directorios necesarios
RUN mkdir -p /var/chat/audio && \
    mkdir -p /app/config && \
    chown -R chatapp:chatapp /var/chat && \
    chown -R chatapp:chatapp /app

WORKDIR /app

# Copiar JARs compilados desde la etapa builder
COPY --from=builder /app/Aplicacion/Bootstrap/target/bootstrap-fat.jar app-bootstrap.jar
COPY --from=builder /app/Aplicacion/RestAPI/target/RestAPI-1.0-SNAPSHOT.jar app-restapi.jar

# Copiar archivos de configuración
COPY --from=builder /app/Aplicacion/Bootstrap/src/main/resources/properties/ ./config/
COPY --from=builder /app/docker-application.properties ./config/application.properties
COPY --from=builder /app/models/ ./models/

# Cambiar propietario de los archivos
RUN chown -R chatapp:chatapp /app

# Cambiar a usuario no-root
USER chatapp

# Exponer puertos
EXPOSE 5000 5100 6000 8089

# Variables de entorno
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV MYSQL_HOST=host.docker.internal
ENV MYSQL_PORT=3306
ENV MYSQL_DATABASE=databasemensajeria
ENV MYSQL_USERNAME=root
ENV MYSQL_PASSWORD=root

# Instalar netcat para health checks
USER root
RUN apt-get update && apt-get install -y netcat-openbsd && rm -rf /var/lib/apt/lists/*

# Script de inicio
COPY docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh
USER chatapp

ENTRYPOINT ["/app/docker-entrypoint.sh"]