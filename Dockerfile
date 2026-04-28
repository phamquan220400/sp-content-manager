FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Install Maven with specific version
RUN curl -fsSL https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.tar.gz | tar xzf - -C /opt/ \
    && ln -s /opt/apache-maven-3.9.6 /opt/maven \
    && ln -s /opt/maven/bin/mvn /usr/local/bin

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Build application
COPY src ./src
RUN mvn clean package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy jar file
COPY --from=build /app/target/*.jar app.jar

# Expose ports
EXPOSE 8080 5005

# JVM configuration for containers
ENV JAVA_OPTS="\
# Memory Management \
-XX:+UseContainerSupport \
-XX:InitialRAMPercentage=70.0 \
-XX:MaxRAMPercentage=80.0 \
-XX:MinRAMPercentage=50.0 \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/tmp/heapdump.hprof \
# Garbage Collection \
-XX:+UseG1GC \
-XX:G1HeapRegionSize=4m \
-XX:+ParallelRefProcEnabled \
-XX:+UseStringDeduplication \
-XX:MaxGCPauseMillis=200 \
-XX:+UseCompressedOops \
# Performance Tuning \
-XX:+OptimizeStringConcat \
-XX:+UseCompressedClassPointers \
-XX:+PerfDisableSharedMem \
-XX:+DisableExplicitGC \
# System Properties \
-Dfile.encoding=UTF-8 \
-Djava.security.egd=file:/dev/./urandom \
-Djava.awt.headless=true \
-Duser.timezone=UTC \
# Monitoring & Debugging \
-XX:+FlightRecorder \
-XX:StartFlightRecording=disk=true,maxsize=1024m,maxage=1d,path=/tmp/recording.jfr \
-XX:+UnlockDiagnosticVMOptions \
-XX:+DebugNonSafepoints"

# Debug configuration with async profiling support
ENV JAVA_TOOL_OPTIONS="\
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
-XX:+PreserveFramePointer"

# Create volume for logs and heap dumps
VOLUME ["/tmp", "/app/logs"]

# Add healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Start application with proper signal handling and memory calculation
CMD ["sh", "-c", "\
    # Calculate memory settings based on container limits\
    CONTAINER_MEMORY_IN_MB=$(free -m | grep Mem | awk '{print $2}') && \
    echo 'Container Memory: '$CONTAINER_MEMORY_IN_MB'MB' && \
    exec java $JAVA_OPTS -jar app.jar"]
