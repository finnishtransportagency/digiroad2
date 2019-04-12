FROM alpine

ENV SCALA_VERSION 2.11.7
ENV SBT_VERSION 0.13.5

ARG JENKINS_UID=1000
RUN adduser -D -S -u ${JENKINS_UID} jenkins

#Create scala sbt folders
RUN mkdir /home/jenkins/.m2 && \
    mkdir /home/jenkins/.ivy && \
    mkdir /home/jenkins/.sbt  && \
    mkdir /home/jenkins/package && \
    mkdir /home/jenkins/package/UI

#Sbt repositories config
COPY config/repositories /home/jenkins/.sbt/repositories

#Updates and bash
RUN apk update && apk upgrade && \
    apk add bash && \
    apk add curl && \
    apk add tzdata && \
    cp /usr/share/zoneinfo/Europe/Helsinki /etc/localtime && \
    echo "Europe/Helsinki" > /etc/timezone

#Install java
RUN apk add openjdk8-jre

#Install scala
RUN curl -L https://downloads.typesafe.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.tgz --output scala-$SCALA_VERSION.tgz && \
    tar xzf "scala-$SCALA_VERSION.tgz" -C /home/jenkins/

#Install sbt
RUN curl -L https://dl.bintray.com/sbt/native-packages/sbt/$SBT_VERSION/sbt-$SBT_VERSION.tgz --output sbt-$SBT_VERSION.tgz && \
    tar xzf "sbt-$SBT_VERSION.tgz" -C /home/jenkins/

ENV PATH "/home/jenkins/sbt/bin:$PATH"
ENV PATH "/home/jenkins/scala-$SCALA_VERSION/bin:$PATH"

RUN chown -R jenkins /home/jenkins
USER jenkins