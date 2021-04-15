grunt  && ^
sbt assembly && ^
docker build -f aws\fargate\Dockerfile -t digiroad2:latest .

REM for testing docker image in AWS
echo "Building Docker image"
./aws-build-image.sh
echo "Logging in to AWS ECR with Docker"
aws ecr get-login-password --profile vaylaapp --region eu-west-1 | docker login --username AWS --password-stdin 783354560127.dkr.ecr.eu-west-1.amazonaws.com
echo "Tagging image"
docker tag viite:latest 783354560127.dkr.ecr.eu-west-1.amazonaws.com/digiroad2:latest
echo "Pushing image"
docker push 783354560127.dkr.ecr.eu-west-1.amazonaws.com/digiroad2:latest