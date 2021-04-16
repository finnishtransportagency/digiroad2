grunt  && ^
sbt assembly && ^
docker build -f aws\fargate\Dockerfile -t digiroad2:latest .

REM for testing docker image in AWS, all real deployment through AWS CodeBuild
echo "Building Docker image"
./aws-build-image.sh
echo "Logging in to AWS ECR with Docker"
aws ecr get-login-password --profile vaylaapp --region eu-west-1 | docker login --username AWS --password-stdin "ecr storage here"
echo "Tagging image"
docker tag digiroad2:latest "ecr storage here"/digiroad2:latest
echo "Pushing image"
docker push 783354560127.dkr.ecr.eu-west-1.amazonaws.com/digiroad2:latest