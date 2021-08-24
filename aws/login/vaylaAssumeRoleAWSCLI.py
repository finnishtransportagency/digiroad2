#!/usr/bin/python3

import argparse
import logging
import getpass
import requests
from bs4 import BeautifulSoup
import re
from urllib.parse import urlparse
import sys
import boto3
import xml.etree.ElementTree as ET
import base64
import os
import configparser
import pytz

parser = argparse.ArgumentParser(formatter_class=argparse.RawDescriptionHelpFormatter,
    description='A command line tool to get temp AWS CLI credentials to Väylävirasto\'s AWS accounts via Väylävirasto\'s centralized access management. Creates a profile (or profiles) to AWS CLI credentials.',
    epilog='''Examples:
    python vaylaAssumeRoleAWSCLI.py --username LX181335 --account 500995478191 --role AppAdmin
    python vaylaAssumeRoleAWSCLI.py --refresh true
    ''')
parser.add_argument('--username', '-u', help='Your username to Väylävirasto\'s centralized access management. If not given, will be asked later.', default='None')
parser.add_argument('--account', '-a', help='AWS account number that you want to get credentials to. Defaults to IAM account (117531223221).', default='117531223221')
parser.add_argument('--role', '-r', help='The role in the AWS account, that you want to get credentials to. Not needed, if you just want to sign in to the IAM account.', default='None')
parser.add_argument('--output', '-o', help='The output format how AWS CLI results are formatted. Defaults to json.', default='json', choices=['json', 'yaml', 'text', 'table'])
parser.add_argument('--region', help='The region. Defaults to eu-west-1 (Ireland).', default='eu-west-1')
parser.add_argument('--duration', '-d', help='The duration of the IAM account role credentials. Defaults 43200 (12h).', default=43200)
parser.add_argument('--refresh', help='Refreshes the credentials for the application AWS account (Not the IAM account). Defaults to false, true is the other option. Note, that this option should be used as the only option (if used).', choices=['true', 'false'], default='false')
parser.add_argument('--profile', help='Profile name in the credentials file. Defaults to vaylaapp.', default='vaylaapp')
args = parser.parse_args()

# Color setting for Windows
if sys.platform.lower() == "win32":
    os.system('color')
# Group of Different functions for different styles
class style():
    BLACK = lambda x: '\033[30m' + str(x)
    RED = lambda x: '\033[31m' + str(x)
    GREEN = lambda x: '\033[32m' + str(x)
    YELLOW = lambda x: '\033[33m' + str(x)
    BLUE = lambda x: '\033[34m' + str(x)
    MAGENTA = lambda x: '\033[35m' + str(x)
    CYAN = lambda x: '\033[36m' + str(x)
    WHITE = lambda x: '\033[37m' + str(x)
    UNDERLINE = lambda x: '\033[4m' + str(x)
    RESET = lambda x: '\033[0m' + str(x)

##########################################################################
# Variables

# region: The AWS region that this script will connect to for all API calls
region = vars(args)['region']

# output format: The AWS CLI output format that will be configured in the
# saml profile (affects subsequent CLI calls)
outputformat = vars(args)['output']

# Duration of the IAM account credentials
iamDuration = vars(args)['duration']

# Refresh action
refresh = vars(args)['refresh']

# Section name for the credentials file profile/section
appSection = vars(args)['profile']

# SSL certificate verification: Whether or not strict certificate
# verification is done, False should only be used for dev/test
sslverification = True

# idpentryurl: The initial url that starts the authentication process.
idpentryurl = 'https://testisso.liikennevirasto.fi/oamfed/idp/initiatesso?providerid=AWSVaylaIAMAccount'

# AWS credentials file
awsconfigfilename = os.path.expanduser("~") + os.sep + '.aws' + os.sep + 'credentials'

# Uncomment to enable low level debugging
#logging.basicConfig(level=logging.DEBUG)
#logging.basicConfig(level=logging.DEBUG, filename='vaylaAssumeRoleAWSCLI.log', filemode='a', format='%(name)s - %(levelname)s - %(message)s')
logging.basicConfig(level=logging.WARNING)
logger = logging.getLogger('clisso')
##########################################################################

def write_credentials(response, profileName, outputformat, region, assumedArn, filename):
    # Write the AWS STS token into the AWS credential file

    # Read in the existing config file
    config = configparser.RawConfigParser()
    config.read(filename)

    # Put the credentials into a saml specific section instead of clobbering
    # the default credentials
    if not config.has_section(profileName):
        config.add_section(profileName)

    config.set(profileName, 'output', outputformat)
    config.set(profileName, 'region', region)
    config.set(profileName, 'aws_access_key_id', response['Credentials']['AccessKeyId'])
    config.set(profileName, 'aws_secret_access_key', response['Credentials']['SecretAccessKey'])
    config.set(profileName, 'aws_session_token', response['Credentials']['SessionToken'])
    if (profileName != 'vaylaiam'):
        config.set(profileName, 'assumedarn', assumedArn)

    # Write the updated config file
    with open(filename, 'w+') as configfile:
        config.write(configfile)

    expirationDatetime = response['Credentials']['Expiration']
    expirationDatetimeEET = expirationDatetime.astimezone(pytz.timezone("Europe/Helsinki"))

    # Give the user some basic info as to what has just happened
    print('----------------------------------------------------------------')
    print('Your new access key pair for assumed role {0} has been'.format(assumedArn))
    print('stored in the AWS configuration file {0} under the {1} profile.'.format(filename, profileName))
    print('Note that it will expire at {0}.'.format(expirationDatetimeEET.strftime("%d.%m.%Y %X %Z")))
    if (profileName == 'vaylaiam'):
        print('After this time, you may safely rerun this script to refresh your access key pair.')
        print (style.RED('Please also note, that you probably have minimal rights to this account, as it is only used for login purposes!') + style.RESET(''))
    else:
        print('After this time, you may refresh the credential by running python vaylaAssumeRoleAWSCLI.py --refresh true --profile {0}'.format(profileName))
    print('To use this credential, call the AWS CLI with the --profile option (e.g. aws s3 ls --profile {0}).'.format(profileName))
    print('----------------------------------------------------------------')


# Use vaylaiam profile to refresh application profile credentials and exit
def refresh_credentials(filename, appSectionName, regionParam, outputformatParam, awsconfigfilenameParam):
    logger.debug('*************** refresh credentials start ****************')

    # Read in the existing config file
    config = configparser.RawConfigParser()
    config.read(filename)

    # Check that the iam section exists
    iamSection = 'vaylaiam'
    if not config.has_section(iamSection):
        sys.exit(style.RED('No profile ' + iamSection + ' found in credentials file ' + filename + '. You propably should login first!') + style.RESET(''))

    # Check that the app section exists
    if not config.has_section(appSectionName):
        sys.exit(style.RED('No profile ' + appSectionName + ' found in credentials file ' + filename + '. You propably should login first!') + style.RESET(''))

    # 
    session = boto3.session.Session(region_name=regionParam, profile_name=iamSection)
    client = session.client('sts')
    response = client.assume_role(
        RoleArn=config[appSectionName]["assumedarn"],
        RoleSessionName=appSectionName,
        DurationSeconds=3600
    )
    write_credentials(response, appSectionName,
                        outputformatParam, regionParam,
                        config[appSectionName]["assumedarn"],
                        awsconfigfilenameParam)


    logger.debug('*************** refresh credentials stop  ****************')
    sys.exit(0)


if (refresh == 'true'):
    refresh_credentials(awsconfigfilename, appSection, region, outputformat, awsconfigfilename)

# Get the federated credentials from the user
username = vars(args)['username']
if (username == 'None'):
    print("Username:", end=' ')
    username = input()
password = getpass.getpass()

# Initiate session handler
session = requests.Session()

# Programmatically get the SAML assertion
# Opens the initial IdP url and follows all of the HTTP302 redirects, and
# gets the resulting login page
formresponse = session.get(idpentryurl, verify=sslverification)
# Capture the idpauthformsubmiturl, which is the final url after all the 302s
idpauthformsubmiturl = formresponse.url

# Parse the response and extract all the necessary values
# in order to build a dictionary of all of the form values the IdP expects
formsoup = BeautifulSoup(formresponse.text, features="html.parser")
logger.debug('*************** page start ('+idpauthformsubmiturl+') ****************')
logger.debug(formsoup)
logger.debug('*************** page end   ****************')
payload = {}

for inputtag in formsoup.find_all(re.compile('(INPUT|input)')):
    name = inputtag.get('name','')
    value = inputtag.get('value','')
    if "user" in name.lower():
        #Make an educated guess that this is the right field for the username
        payload[name] = username
    elif "email" in name.lower():
        #Some IdPs also label the username field as 'email'
        payload[name] = username
    elif "pass" in name.lower():
        #Make an educated guess that this is the right field for the password
        payload[name] = password
    else:
        #Simply populate the parameter with the existing value (picks up hidden fields in the login form)
        payload[name] = value
# In this form the action contains the whole URL
for inputtag in formsoup.find_all(re.compile('(FORM|form)')):
    idpauthformsubmiturl = inputtag.get('action')

# Performs the submission of the IdP login form with the above post data
response = session.post(idpauthformsubmiturl, data=payload, verify=sslverification)

# Overwrite and delete the credential variables, just for safety
username = '##############################################'
password = '##############################################'
del username
del password

# Parse the response and extract all the necessary values
# in order to build a dictionary of all of the form values from the SSO form
formsoup = BeautifulSoup(response.text, features="html.parser")
logger.debug('*************** page start ('+idpauthformsubmiturl+') ****************')
logger.debug(formsoup)
logger.debug('*************** page end   ****************')
payload = {}

for inputtag in formsoup.find_all(re.compile('(INPUT|input)')):
    name = inputtag.get('name','')
    value = inputtag.get('value','')
    if ("sfassb" in name.lower()):
        payload[name] = 'true'
    else:
        #Simply populate the parameter with the existing value (picks up hidden fields in the login form)
        payload[name] = value

logger.debug('*************** payload start ****************')
logger.debug(payload)
logger.debug('*************** payload end   ****************')

# Determine the next URL
idpauthformsubmiturl = ''
for inputtag in formsoup.find_all(re.compile('(FORM|form)')):
    action = inputtag.get('action')
    if action:
        parsedurl = urlparse(idpentryurl)
        idpauthformsubmiturl = parsedurl.scheme + "://" + parsedurl.netloc + action

if (idpauthformsubmiturl == ''):
    print('Could not determine SSO form URL!')
    sys.exit(0)

# Submit the MFA selection to get the MFA token via email or SMS
response = session.post(idpauthformsubmiturl, data=payload, verify=sslverification)
formsoup = BeautifulSoup(response.text, features="html.parser")
logger.debug('*************** page start ('+idpauthformsubmiturl+') ****************')
logger.debug(formsoup)
logger.debug('*************** page end   ****************')

# Now the user should get a SMS with a pin code
# Next we'll ask for that:
print("Pin code from the SMS message:", end=' ')
smspin = input()

# Modify parameters
payload['passcode'] = smspin
payload['sfaSSb'] = 'false'
payload.pop('sfaRecp')

logger.debug('*************** payload start ****************')
logger.debug(payload)
logger.debug('*************** payload end   ****************')

# Submit the MFA form again with the pin
response = session.post(idpauthformsubmiturl, data=payload, verify=sslverification)

# Parse response
formsoup = BeautifulSoup(response.text, features="html.parser")
logger.debug('*************** page start ('+idpauthformsubmiturl+') ****************')
logger.debug(formsoup)
logger.debug('*************** page end   ****************')
payload = {}

# If response contains "loginFailed", Pin was wrong
if ('loginFailed' in formsoup):
    logger.info('Invalid One time pin!')
    sys.exit(0)

assertion = ''

# Parse form, that contain SAML
for inputtag in formsoup.find_all(re.compile('(INPUT|input)')):
    name = inputtag.get('name','')
    value = inputtag.get('value','')
    payload[name] = value
    logger.debug('Name:' + name + '\nValue:' + value  + '\n')
    # Look for the SAMLResponse attribute of the input tag (determined by
    # analyzing the debug print lines above)
    if(name == 'SAMLResponse'):
        assertion = value

# In this form the action contains the whole URL
for inputtag in formsoup.find_all(re.compile('(FORM|form)')):
    idpauthformsubmiturl = inputtag.get('action')

# Submit the form towards AWS
logger.debug('*************** Posting towards AWS '+idpauthformsubmiturl+' ****************')
response = session.post(idpauthformsubmiturl, data=payload, verify=sslverification)

# Parse response
soup = BeautifulSoup(response.text, features="html.parser")
logger.debug('*************** page start ('+idpauthformsubmiturl+') ****************')
logger.debug(soup)
logger.debug('*************** page end   ****************')
payload = {}

# Better error handling is required for production use.
if (assertion == ''):
    #TODO: Insert valid error checking/handling
    print('Response did not contain a valid SAML assertion')
    sys.exit(0)

# Parse the returned assertion and extract the authorized roles
awsroles = []
root = ET.fromstring(base64.b64decode(assertion))
for saml2attribute in root.iter('{urn:oasis:names:tc:SAML:2.0:assertion}Attribute'):
    if (saml2attribute.get('Name') == 'https://aws.amazon.com/SAML/Attributes/Role'):
        for saml2attributevalue in saml2attribute.iter('{urn:oasis:names:tc:SAML:2.0:assertion}AttributeValue'):
            awsroles.append(saml2attributevalue.text)

# Note the format of the attribute value should be role_arn,principal_arn
# but lots of blogs list it as principal_arn,role_arn so let's reverse
# them if needed
for awsrole in awsroles:
    chunks = awsrole.split(',')
    if'saml-provider' in chunks[0]:
        newawsrole = chunks[1] + ',' + chunks[0]
        index = awsroles.index(awsrole)
        awsroles.insert(index, newawsrole)
        awsroles.remove(awsrole)

# If I have more than one role, ask the user which one they want,
# otherwise just proceed
print("")
if len(awsroles) > 1:
    i = 0
    print("Please choose the role you would like to assume:")
    for awsrole in awsroles:
        print('[', i, ']: ', awsrole.split(',')[0])
        i += 1
    print("Selection: ", end=' ')
    selectedroleindex = input()

    # Basic sanity check of input
    if int(selectedroleindex) > (len(awsroles) - 1):
        print('You selected an invalid role index, please try again')
        sys.exit(0)

    role_arn = awsroles[int(selectedroleindex)].split(',')[0]
    principal_arn = awsroles[int(selectedroleindex)].split(',')[1]
else:
    role_arn = awsroles[0].split(',')[0]
    principal_arn = awsroles[0].split(',')[1]

# Use the assertion to get an AWS STS token using Assume Role with SAML
# conn = boto.sts.connect_to_region(region)
# token = conn.assume_role_with_saml(role_arn, principal_arn, assertion)
session = boto3.session.Session(region_name=region)
client = session.client('sts')
response = client.assume_role_with_saml(
    RoleArn=role_arn,
    PrincipalArn=principal_arn,
    SAMLAssertion=assertion,
    DurationSeconds=iamDuration
)

logger.debug('role_arn: {0}\nprincipal_arn: {1}\nassertion: {2}\nresponse: {3}\n'.format(role_arn, principal_arn, assertion, response))

# Write the IAM account credentials
profile='vaylaiam'
write_credentials(response, profile, outputformat, region, role_arn, awsconfigfilename)

# Assume role to other account if wanted
accountToAssumeTo = vars(args)['account']
role = vars(args)['role']
if (accountToAssumeTo != '117531223221') & (role != 'None'):
    session = boto3.session.Session(region_name=region, profile_name=profile)
    client = session.client('sts')
    #arn = 'arn:aws:iam::' + accountToAssumeTo + ':role/' + role
    arn = 'arn:aws:iam::{0}:role/{1}'.format(accountToAssumeTo, role)
    response = client.assume_role(
        RoleArn=arn,
        RoleSessionName='vayla' + role,
        DurationSeconds=3600
    )
    write_credentials(response, appSection, outputformat, region, arn, awsconfigfilename)

