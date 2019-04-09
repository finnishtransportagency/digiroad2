FROM alpine

ARG JENKINS_UID=1000
RUN adduser -D -S -u ${JENKINS_UID} jenkins

#Install capistrano
RUN apk update && apk upgrade && \
    apk add openssh && \
    apk add ruby-dev && \
    apk add ruby-rdoc && \
    apk add g++ && \
    apk add make && \
    chown -R jenkins /home/jenkins/ && \
    chown -R jenkins /usr/

COPY config/Capfile /home/jenkins/Capfile
COPY config/Gemfile /home/jenkins/Gemfile
COPY config/deploy /home/jenkins/config/deploy
COPY config/deploy.rb /home/jenkins/config/deploy.rb

USER jenkins

RUN cd /home/jenkins/ && \
    gem install bundler && \
    bundle install