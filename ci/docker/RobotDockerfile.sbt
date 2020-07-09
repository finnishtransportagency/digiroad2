FROM ppodgorsek/robot-framework:latest

ARG JENKINS_UID=1000
RUN adduser -D -S -u ${JENKINS_UID} jenkins



RUN pip3 install --no-cache-dir jproperties


USER jenkins