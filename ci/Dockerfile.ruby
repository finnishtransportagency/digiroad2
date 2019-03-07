FROM alpine

ARG JENKINS_UID=1000
RUN adduser -D -S -u ${JENKINS_UID} jenkins

#Install capistrano
RUN apk update && apk upgrade && \
    apk add ruby && \
    apk add ruby-rdoc && \
    apk add openssh-client && \
    gem install bundler && \
    chown -R jenkins /home/jenkins

COPY config/Capfile /home/jenkins/Capfile
COPY config/Gemfile /home/jenkins/Gemfile
COPY config/deploy /home/jenkins/config/deploy
COPY config/deploy.rb /home/jenkins/config/deploy.rb
#USER jenkins
RUN cd /home/jenkins && \
    bundle install