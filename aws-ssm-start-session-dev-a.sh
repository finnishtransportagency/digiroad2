#!/bin/bash
aws ssm start-session --target i-0e2e146d3e05c139b --document-name AWS-StartPortForwardingSession --parameters portNumber=22,localPortNumber=2222 --profile vaylaapp
