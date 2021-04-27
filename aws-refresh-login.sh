#!/bin/bash
# Before running this script you must authenticate through Väylä SAML:
#
# python3 aws/login/vaylaAssumeRoleAWSCLI.py --username <Your Väylä username> --account 475079312496 --role DigiroadOthAdmin --region eu-west-1
#
python aws/login/vaylaAssumeRoleAWSCLI.py --refresh true
