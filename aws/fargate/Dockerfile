FROM openjdk:8u191-jre-alpine
ENV initialRam="-XX:InitialRAMPercentage=2048.0"
ENV minRamPercentage="-XX:MinRAMPercentage=20.0"
ENV maxRamPercentage="-XX:MaxRAMPercentage=80.0"
ENV otherParameter="-Djava.security.egd=file:/dev/./urandom -Duser.timezone=Europe/Helsinki -XX:-OmitStackTraceInFastThrow"
ENV javaParameter="${initialRam} ${minRamPercentage} ${maxRamPercentage} ${otherParameter}"
RUN apk --no-cache add curl
RUN mkdir /src
RUN mkdir /src/main
RUN mkdir /src/main/webapp
RUN mkdir /src/main/webapp/node_modules
COPY dist/ /src/main/webapp
COPY UI/ /src/main/webapp
COPY src/main/webapp/ /src/main/webapp
COPY node_modules/ /src/main/webapp/node_modules
COPY target/scala-2.11/digiroad2-assembly-0.1.0-SNAPSHOT.jar /digiroad2.jar
EXPOSE 8080/tcp
ENTRYPOINT java ${javaParameter} -jar /digiroad2.jar