# Builds the production image: one JAR with the backend and the pre-built React frontend.
#   docker build -t chatdocs .
#   docker run -p 8080:8080 -e DB_URL=... -e DB_USERNAME=... -e DB_PASSWORD=... -e GEMINI_API_KEY=... chatdocs

# ---- Build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Download Maven dependencies in their own layer, so code-only changes rebuild faster.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

# The Vaadin plugin downloads Node.js and builds the frontend in production mode.
COPY package.json package-lock.json tsconfig.json types.d.ts vite.config.ts ./
COPY src src
RUN ./mvnw -B -q -DskipTests package

# ---- Run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /workspace/target/chatdocs-*.jar app.jar
USER app

# prod: database from DB_URL/DB_USERNAME/DB_PASSWORD, no demo users.
# The JVM options keep memory use small enough for a 512 MB instance.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=192m"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
