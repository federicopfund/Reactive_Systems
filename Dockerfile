# Multi-stage Dockerfile for Play Framework application
# Stage 1: Builder - Build the application using sbt
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Copy sbt configuration files first for better caching
COPY project/build.properties project/
COPY project/plugins.sbt project/

# Install sbt
# Note: --no-check-certificate is used for compatibility with corporate proxies/SSL inspection
# In production builds on Render.com or other cloud platforms, this works securely
# Alternative: Use the sbt package from Debian repos if available in your environment
RUN apt-get update && \
    apt-get install -y wget && \
    wget --no-check-certificate https://github.com/sbt/sbt/releases/download/v1.9.7/sbt-1.9.7.tgz -O /tmp/sbt.tgz && \
    tar -xzf /tmp/sbt.tgz -C /usr/local && \
    ln -s /usr/local/sbt/bin/sbt /usr/bin/sbt && \
    rm /tmp/sbt.tgz && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Copy build definition
COPY build.sbt .

# Copy application source
COPY app app
COPY conf conf
COPY public public

# Build the application using sbt: clean, compile assets, and stage
# IMPORTANT: 'assets' task compiles SASS/LESS files before staging
RUN sbt clean compile assets stage

# Stage 2: Runtime - Minimal image for running the application
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the staged application from builder
COPY --from=builder /app/target/universal/stage /app

# Set environment variables for production
ENV APPLICATION_MODE="prod"

# Set conservative JVM options for limited memory (Render free plan)
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dplay.server.pidfile.path=/dev/null"

# Expose port - will be overridden by PORT env var at runtime
EXPOSE 9000

# Run the application in production mode
# The PORT environment variable will be used by Play Framework
# APPLICATION_SECRET must be provided via environment variable
CMD ["sh", "-c", "/app/bin/web -Dhttp.port=${PORT:-9000} -Dplay.http.secret.key=${APPLICATION_SECRET}"]