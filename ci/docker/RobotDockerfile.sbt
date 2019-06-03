FROM ubuntu:latest

RUN apt-get update \
&& apt-get install -y build-essential libssl-dev libffi-dev python-dev \
  python-pip python-dev gcc phantomjs firefox \
xvfb zip wget ca-certificates ntpdate \
libnss3-dev libxss1 libappindicator3-1 libindicator7 gconf-service libgconf-2-4 libpango1.0-0 xdg-utils fonts-liberation \
  && rm -rf /var/lib/apt/lists/

RUN pip install asn1crypto==0.24.0
RUN pip install backports.functools-lru-cache==1.5
RUN pip install bcrypt==3.1.6
RUN pip install beautifulsoup4==4.7.1
RUN pip install certifi==2019.3.9
RUN pip install cffi==1.12.3
RUN pip install chardet==3.0.4
RUN pip install cryptography==2.6.1
RUN pip install enum34==1.1.6
RUN pip install et-xmlfile==1.0.1
RUN pip install future==0.16.0
RUN pip install idna==2.8
RUN pip install ipaddress==1.0.22
RUN pip install jdcal==1.4.1
RUN pip install jsonpatch==1.23
RUN pip install jsonpointer==2.0
RUN pip install natsort==6.0.0
RUN pip install ndg-httpsclient==0.5.1
RUN pip install openpyxl==2.6.2
RUN pip install paramiko==2.4.2
RUN pip install Pillow==6.0.0
RUN pip install pyasn1==0.4.5
RUN pip install pycparser==2.19
RUN pip install PyNaCl==1.3.0
RUN pip install pyOpenSSL==19.0.0
RUN pip install PyYAML==5.1
RUN pip install requests==2.21.0
RUN pip install robotframework==3.1.1
RUN pip install robotframework-angularjs==0.0.9
RUN pip install robotframework-excellibrary==0.0.2
RUN pip install robotframework-httplibrary==0.4.2
RUN pip install robotframework-requests==0.5.0
RUN pip install robotframework-selenium2library==3.0.0
RUN pip install robotframework-seleniumlibrary==3.3.1
RUN pip install robotframework-sshlibrary==3.3.0
RUN pip install robotframework-testrail==1.0.4
RUN pip install robotframework-xvfb==1.2.2
RUN pip install scp==0.13.2
RUN pip install selenium==3.141.0
RUN pip install six==1.12.0
RUN pip install soupsieve==1.9.1
RUN pip install urllib3==1.25.2
RUN pip install waitress==1.3.0
RUN pip install WebOb==1.8.5
RUN pip install WebTest==2.0.33
RUN pip install xlrd==1.2.0
RUN pip install xlutils==2.0.0
RUN pip install xlwt==1.3.0
RUN pip install xvfbwrapper==0.2.9

RUN wget https://github.com/mozilla/geckodriver/releases/download/v0.24.0/geckodriver-v0.24.0-linux64.tar.gz \
  && tar xvzf geckodriver-*.tar.gz \
  && rm geckodriver-*.tar.gz \
  && mv geckodriver /usr/local/bin \
  && chmod a+x /usr/local/bin/geckodriver
RUN wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
  && dpkg -i google-chrome*.deb \
  && rm google-chrome*.deb
RUN wget https://chromedriver.storage.googleapis.com/2.26/chromedriver_linux64.zip \
  && unzip chromedriver_linux64.zip \
  && rm chromedriver_linux64.zip \
  && mv chromedriver /usr/local/bin \
  && chmod +x /usr/local/bin/chromedriver