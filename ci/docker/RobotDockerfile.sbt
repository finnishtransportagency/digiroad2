FROM python:3

RUN python3 -m pip install robotframework

ARG JENKINS_UID=1000
RUN adduser -D -S -u ${JENKINS_UID} jenkins

RUN chown -R jenkins /home/jenkins
USER jenkins