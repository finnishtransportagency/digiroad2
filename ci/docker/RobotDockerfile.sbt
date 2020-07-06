FROM jafil/robotframework:latest

RUN python3 -m pip install --upgrade robotframework-seleniumlibrary
RUN python3 -m pip install jproperties