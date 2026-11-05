FROM eclipse-temurin:21-jre
WORKDIR /app
COPY server/build/libs/server-all.jar app.jar
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
