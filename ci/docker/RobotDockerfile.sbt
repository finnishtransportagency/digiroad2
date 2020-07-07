FROM python:3.7-alpine3.9

ENV SCREEN_WIDTH 1280
ENV SCREEN_HEIGHT 720
ENV SCREEN_DEPTH 16
ENV DEPS="\
  chromium \
  chromium-chromedriver \
  udev \
  xvfb \
  "

RUN pip install --no-cache-dir robotframework==3.1.1
RUN pip install --no-cache-dir robotframework-selenium2library==3.0.0
RUN pip install --no-cache-dir robotframework-seleniumlibrary==3.3.1
RUN pip install --no-cache-dir selenium==3.141.0
RUN pip install jproperties

RUN apk update && apk upgrade \
&& echo@latest - stable http :// nl.alpinelinux.org / alpine / latest - stable / community >> / etc / apk / repositories \
&& echo@latest - stable http :// nl.alpinelinux.org / alpine / latest - stable / main >> / etc / apk / repositories \
&& apk add -- no - cache \
  chromium@latest - stable \
  harfbuzz@latest - stable \ <--- New !nss@latest - stable \
  && rm -rf / var / lib / apt / lists /* \
  /var/cache/apk/* \
  /usr/share/man \
  /tmp/*