ARG image="amazoncorretto:8-alpine-jre"
FROM ${image}
ENV initialRamPercentage="-XX:InitialRAMPercentage=50.0"
ENV maxRamPercentage="-XX:MaxRAMPercentage=95.0"
ENV otherParameter="-Djava.security.egd=file:/dev/./urandom -Duser.timezone=Europe/Helsinki -XX:-OmitStackTraceInFastThrow"
# -XX:ActiveProcessorCount=xx"
ENV containerCPU=""
ENV javaParameter="${initialRam} ${maxRamPercentage} ${containerCPU} ${otherParameter}"
ENV batchMode="false"
RUN apk --no-cache add curl
RUN apk update && \
    apk del xz openssl zsh git wget
RUN mkdir /src
RUN mkdir /src/main
RUN mkdir /src/main/webapp
RUN mkdir /src/main/webapp/node_modules
COPY dist/ /src/main/webapp
COPY UI/ /src/main/webapp
COPY src/main/webapp/ /src/main/webapp
COPY node_modules/ /src/main/webapp/node_modules
COPY target/scala-2.11/digiroad2-assembly-0.1.0-SNAPSHOT.jar /digiroad2.jar
COPY aws/fargate/startup.sh /startup.sh
RUN sed -i 's/\r$//' /startup.sh  && chmod +x /startup.sh
EXPOSE 8080/tcp
ENTRYPOINT ["./startup.sh"]
