FROM ubuntu:latest

ARG JENKINS_UID=1000
RUN useradd -u ${JENKINS_UID} jenkins

RUN apt-get update \
&& apt-get install -y build-essential libssl-dev libffi-dev python-dev \
  python-pip python-dev gcc phantomjs firefox \
xvfb zip wget ca-certificates ntpdate \
libnss3-dev libxss1 libappindicator3-1 libindicator7 gconf-service libgconf-2-4 libpango1.0-0 xdg-utils fonts-liberation \
  && rm -rf /var/lib/apt/lists/

RUN pip install asn1crypto
RUN pip install backports.functools-lru-cache
RUN pip install bcrypt
RUN pip install beautifulsoup4
RUN pip install certifi
RUN pip install cffi
RUN pip install chardet
RUN pip install cryptography
RUN pip install enum34
RUN pip install et-xmlfile
RUN pip install future
RUN pip install idna
RUN pip install ipaddress
RUN pip install jdcal
RUN pip install jsonpatch
RUN pip install jsonpointer
RUN pip install natsort
RUN pip install ndg-httpsclient
RUN pip install openpyxl
RUN pip install paramiko
RUN pip install Pillow
RUN pip install pyasn1
RUN pip install pycparser
RUN pip install PyNaCl
RUN pip install pyOpenSSL
RUN pip install PyYAML
RUN pip install requests
RUN pip install robotframework
RUN pip install robotframework-angularjs
RUN pip install robotframework-excellibrary
RUN pip install robotframework-httplibrary
RUN pip install robotframework-requests
RUN pip install -U robotframework-selenium2library
RUN pip install -U robotframework-seleniumlibrary
RUN pip install robotframework-sshlibrary
RUN pip install robotframework-testrail
RUN pip install robotframework-xvfb
RUN pip install scp
RUN pip install selenium
RUN pip install six
RUN pip install soupsieve
RUN pip install urllib3
RUN pip install waitress
RUN pip install WebOb
RUN pip install WebTest
RUN pip install xlrd
RUN pip install xlutils
RUN pip install xlwt
RUN pip install xvfbwrapper

RUN wget https://github.com/mozilla/geckodriver/releases/download/v0.24.0/geckodriver-v0.24.0-linux64.tar.gz \
  && tar xvzf geckodriver-*.tar.gz \
  && rm geckodriver-*.tar.gz \
  && mv geckodriver /usr/local/bin \
  && chmod a+x /usr/local/bin/geckodriver
RUN wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
  && dpkg -i google-chrome*.deb \
  && rm google-chrome*.deb
RUN wget https://chromedriver.storage.googleapis.com/74.0.3729.6/chromedriver_linux64.zip \
  && unzip chromedriver_linux64.zip \
  && rm chromedriver_linux64.zip \
  && mv chromedriver /usr/local/bin \
  && chmod +x /usr/local/bin/chromedriver

RUN headless_browser=True,
RUN nogui=True

USER jenkins