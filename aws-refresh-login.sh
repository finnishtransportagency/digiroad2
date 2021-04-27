#!/bin/bash
# Before running this script you must authenticate through Väylä SAML:
#
# python3 aws/login/vaylaAssumeRoleAWSCLI.py --username <Your Väylä username> --account 783354560127 --role ViiteAdmin --region eu-west-1
#
python3 aws/login/vaylaAssumeRoleAWSCLI.py --refresh true
