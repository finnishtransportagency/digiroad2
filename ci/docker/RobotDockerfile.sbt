FROM ppodgorsek/robot-framework

RUN pip install robotframework-selenium2library

RUN apt-get install -y libglib2.0-0=2.50.3-2 \
  libnss3=2:3.26.2-1.1+deb9u1 \
  libgconf-2-4=3.2.6-4+b1 \
  libfontconfig1=2.11.0-6.7+b1