#!/bin/bash
aws ssm start-session --target i-0db4491ede0959d4b --document-name AWS-StartPortForwardingSession --parameters portNumber=22,localPortNumber=2222 --profile vaylaapp
