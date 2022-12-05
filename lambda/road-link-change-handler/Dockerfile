FROM public.ecr.aws/lambda/nodejs:18

COPY package*.json ${LAMBDA_TASK_ROOT}/
RUN npm install
COPY . ${LAMBDA_TASK_ROOT}

# Compile typescript code
RUN npm run compile

# Run unit tests
# RUN npm run test

# Remove unnecessary files from image
RUN npm prune --production
RUN npm run post-compile

# Move dist directory contents to root and remove empty directory
RUN mv dist/* ${LAMBDA_TASK_ROOT} && rmdir dist

CMD ["index.handler"]