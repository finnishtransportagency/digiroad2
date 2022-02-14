#!/bin/bash
aws ssm start-session --target i-0ae1539fc66fdf98b --document-name AWS-StartPortForwardingSession --parameters portNumber=22,localPortNumber=2222 --profile vaylaapp
