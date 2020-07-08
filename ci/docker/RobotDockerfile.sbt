FROM ppodgorsek/robot-framework

RUN pip3 install --no-cache-dir requests
RUN pip3 install --no-cache-dir jproperties==2.1.0