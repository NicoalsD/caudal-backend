# Imagen de producción de la API de CAUDAL (Render la construye desde render.yaml).
# Las imágenes base van fijadas por digest (eclipse-temurin 25 jdk y jre): para actualizarlas
# se cambia el digest a propósito, con una revisión de la nueva versión del JDK.
# Etapas: build (Maven con caché) -> extracción por capas de Spring Boot -> runtime sin herramientas.

FROM docker.io/library/eclipse-temurin:25-jdk@sha256:8c0a84ea11c8f6ed52600fc19f1040121f2a162998e9f50a5faebbbad9172dcc AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
# Las pruebas corren en ci.yml; aquí solo se empaqueta.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/caudal-backend-*.jar extract --layers --launcher --destination /workspace/extracted

FROM docker.io/library/eclipse-temurin:25-jre@sha256:fcd7fd7b387f94bb2ac461478a7436ad8e349924c374ea8313919624dceae636 AS runtime
WORKDIR /app
# Usuario sin privilegios con UID/GID fijos (Render y Kubernetes pueden verificar el no-root).
RUN groupadd --system --gid 10001 caudal \
 && useradd --system --uid 10001 --gid caudal --no-create-home --shell /usr/sbin/nologin caudal
# Capas de menor a mayor cambio: el caché sobrevive cuando solo cambia el código de la aplicación.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER 10001:10001
ENV PORT=10000
EXPOSE 10000
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
# Render usa healthCheckPath (render.yaml) y no lee esta instrucción; sirve para docker run o compose.
# La imagen JRE no trae curl ni wget: se usa bash y /dev/tcp para pedir /actuator/health.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${PORT} && printf 'GET /actuator/health HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && head -n1 <&3 | grep -q ' 200 '"]
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
