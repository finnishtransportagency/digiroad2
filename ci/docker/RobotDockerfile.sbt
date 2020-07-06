FROM python:3

RUN python3 -m pip install robotframework
RUN python3 -m pip install --upgrade robotframework-seleniumlibrary
RUN python3 -m pip install jproperties

export PATH="$PATH:/path/to/chromedriver_directory"