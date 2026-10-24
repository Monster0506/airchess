# Build from the repository root: docker build -f server/Dockerfile -t airchess-server .
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY protocol protocol
COPY chess chess
COPY server server
# The Android app is not needed (and needs an SDK) to build the server.
RUN sed -i '/":app"/d' settings.gradle.kts \
    && sh ./gradlew :server:installDist --no-daemon --console=plain

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/server/build/install/server /app
ENV PORT=8080
EXPOSE 8080
CMD ["/app/bin/server"]
