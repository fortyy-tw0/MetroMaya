# Build stage: compile the Java sources and package the static files next to them.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY src ./src
RUN mkdir out \
 && javac -d out $(find src/main/java -name '*.java') \
 && cp -r src/main/resources/. out/

# Run stage: just a small JRE and the compiled output.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/out ./out

# Vercel's Container runtime sends traffic to port 80 unless the PORT env var says otherwise.
ENV PORT=80
EXPOSE 80
CMD ["java", "-cp", "out", "appointment.Main"]
