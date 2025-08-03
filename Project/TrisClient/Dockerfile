FROM maven:3.9.4-eclipse-temurin-21

WORKDIR /app
COPY . .

RUN mvn clean package

CMD ["sh", "-c", "sleep 2 && mvn javafx:run"]
