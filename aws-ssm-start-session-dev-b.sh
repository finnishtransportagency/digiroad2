#!/bin/bash
aws ssm start-session --target i-0fb88e1cb61f3ea35 --document-name AWS-StartPortForwardingSession --parameters portNumber=22,localPortNumber=2223 --profile vaylaapp
