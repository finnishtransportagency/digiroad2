FROM hseeberger/scala-sbt
ARG JENKINS_UID=1000
RUN adduser -u ${JENKINS_UID} jenkins
  RUN mkdir /home/jenkins/.m2 && \
mkdir /home/jenkins/.ivy2 && \
mkdir /home/jenkins/.sbt && \
mkdir /home/jenkins/package && \
  mkdir /home/jenkins/package/oth-UI
COPY config/repositories /home/jenkins/.sbt/repositories
RUN wget http://mirrors.up.pt/pub/apache/maven/maven-3/3.6.0/binaries/apache-maven-3.6.0-bin.tar.gz && \
tar xzf apache-maven-3.6.0-bin.tar.gz -C /home/jenkins
RUN chown -R jenkins /home/jenkins
  ENV M2_HOME="/home/jenkins/apache-maven-3.6.0"
ENV PATH "/home/jenkins/apache-maven-3.6.0/bin:$PATH"