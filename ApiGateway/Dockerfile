FROM openjdk:21-jre-slim

# Información del mantenedor
LABEL maintainer="Equipo Arquitectura <arquitectura@empresa.com>"
LABEL description="TCP Servers API Gateway - Agregación de datos en tiempo real"
LABEL version="1.0.0"

# Variables de entorno
ENV SPRING_PROFILES_ACTIVE=docker
ENV SERVER_PORT=8090
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Crear usuario no privilegiado
RUN groupadd -r gateway && useradd -r -g gateway gateway

# Crear directorios necesarios
RUN mkdir -p /app/logs /app/config && \
    chown -R gateway:gateway /app

# Cambiar a usuario no privilegiado
USER gateway

# Directorio de trabajo
WORKDIR /app

# Copiar el JAR compilado
COPY target/ApiGateway-*.jar app.jar

# Exponer puerto
EXPOSE 8091

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8091/gateway/actuator/health || exit 1
# Comando de inicio
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]