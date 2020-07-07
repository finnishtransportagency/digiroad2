FROM supersoftware/robotframework

RUN pip install --no-cache-dir robotframework-seleniumlibrary==3.3.1
RUN pip install jproperties