ARG FROM_IMAGE=robotframework/rfdocker:3.1.2-slimbuster
FROM $FROM_IMAGE

RUN pip install --no-cache-dir robotframework==3.1.1
RUN pip install --no-cache-dir robotframework-selenium2library==3.0.0
RUN pip install --no-cache-dir robotframework-seleniumlibrary==3.3.1
RUN pip install --no-cache-dir selenium==3.141.0
RUN pip install jproperties