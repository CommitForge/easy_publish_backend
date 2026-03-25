FROM ubuntu:22.04

ARG DEBIAN_FRONTEND=noninteractive
ARG APP_JAR=target/easypublish-0.0.1-SNAPSHOT.jar

# ---- Base OS + Java ----
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    gnupg \
    openjdk-25-jdk \
    fontconfig \
    fonts-dejavu \
    libfreetype6 \
    libxrender1 \
    libxext6 && \
    rm -rf /var/lib/apt/lists/*

# ---- Node.js runtime for scripts ----
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && \
    apt-get update && \
    apt-get install -y --no-install-recommends nodejs && \
    rm -rf /var/lib/apt/lists/*

# ---- Node scripts ----
WORKDIR /app/node
COPY node/package*.json ./
RUN npm ci --omit=dev || npm install --omit=dev
COPY node/*.js ./

# ---- Spring Boot ----
WORKDIR /app
COPY ${APP_JAR} /app/app.jar
COPY src/main/resources/application.properties /app/application.properties

# Fail image build early if a non-executable/plain jar is copied
RUN jar xf /app/app.jar META-INF/MANIFEST.MF && \
    grep -q "Main-Class: org.springframework.boot.loader.launch.JarLauncher" META-INF/MANIFEST.MF && \
    grep -q "Start-Class: com.easypublish.EasyPublishApplication" META-INF/MANIFEST.MF && \
    rm -rf META-INF

# ---- Environment ----
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true"

# ---- Port ----
EXPOSE 8081

# ---- Start Spring Boot ----
ENTRYPOINT ["java","-jar","/app/app.jar","--server.port=8081","--spring.config.location=file:/app/application.properties"]
