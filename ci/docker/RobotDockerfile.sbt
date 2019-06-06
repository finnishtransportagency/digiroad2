# Base image
FROM python:3.6

RUN apt-get update \
&& apt-get install -y libnss3-dev

# Install the test project libraries
RUN	pip install robotframework-selenium2library

RUN wget https://chromedriver.storage.googleapis.com/74.0.3729.6/chromedriver_linux64.zip \
  && unzip chromedriver_linux64.zip \
  && rm chromedriver_linux64.zip \
  && mv chromedriver /usr/local/bin \
  && chmod +x /usr/local/bin/chromedriver