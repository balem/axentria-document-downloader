# Paso 1: Compilación y construcción del proyecto (Cambiado a Java 22)
FROM maven:3.9.6-eclipse-temurin-22 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Paso 2: Entorno de ejecución con Java 22 y Chrome moderno
FROM eclipse-temurin:22-jre
WORKDIR /app

# Instalar dependencias esenciales
RUN apt-get update && apt-get install -y \
    wget \
    curl \
    gnupg \
    ca-certificates \
    --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*

# Descargar la clave GPG oficial de Google Chrome e instalar el repositorio
RUN curl -fsSL https://dl.google.com/linux/linux_signing_key.pub | gpg --dearmor -o /usr/share/keyrings/google-chrome.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.gpg] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list

# Instalar Google Chrome Estable
RUN apt-get update && apt-get install -y \
    google-chrome-stable \
    --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*

# Copiar el JAR generado desde la etapa de compilación
COPY --from=build /app/target/*.jar ./app.jar

# Crear la carpeta de descargas dentro del contenedor
RUN mkdir -p /mnt/Downloads

# Ejecutar la aplicación
CMD ["java", "-jar", "app.jar"]