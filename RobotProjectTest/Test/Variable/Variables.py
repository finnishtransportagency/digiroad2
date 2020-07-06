from jproperties import Properties

# Get keys.properties values
configs = Properties()
with open('conf/dev/keys.properties', 'rb') as config_file:
    configs.load(config_file)

# Defining multiple variables
ENV_URL = 'https://devtest.liikennevirasto.fi/digiroad/'
USERNAME = configs.get("robotframework.username").data
PASSWORD = configs.get("robotframework.password").data


