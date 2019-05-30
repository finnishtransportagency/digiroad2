FROM python:3

ARG JENKINS_UID=1000
RUN adduser -D -S -u ${JENKINS_UID} jenkins

#Install Robotframework
RUN python3 -m pip install --no-cache-dir robotframework--selenium2library

USER jenkins
