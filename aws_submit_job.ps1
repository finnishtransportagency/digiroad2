#batchRunType
#batchAction
#assetForValidation
#trafficSignGroup

# if command does not work, check aws version and update it to at least 2.1.14 or latest

# Ad hoc enviroments:
# DEV-adhoc
# QA-adhoc

# UpdateIncompleteLink
aws batch submit-job --profile vaylaapp --job-definition <definition> --job-name <name> --job-queue <name> --container-overrides "environment=[{name=batchRunType,value=UpdateIncompleteLinkList}]"

# DataFixture
aws batch submit-job --profile vaylaapp --job-definition <definition>  --job-name <name> --job-queue <name> --container-overrides "environment=[{name=batchRunType,value=DataFixture},{name=batchAction,value=<action>}]"

# DataFixture With $trafficSignGroup
aws batch submit-job --profile vaylaapp --job-definition <definition>  --job-name <name> --job-queue <name> --container-overrides "environment=[{name=batchRunType,value=DataFixture},{name=batchAction,value=<action>},{name=trafficSignGroup,value=<trafficSignGroup>}]"

# AssetValidatorProcess
aws batch submit-job --profile vaylaapp --job-definition <definition>  --job-name <name> --job-queue <name> --container-overrides "environment=[{name=batchRunType,value=AssetValidatorProcess},{name=assetForValidation,value=<asset>}]"

# LinearAssetUpdater
aws batch submit-job --profile vaylaapp --job-definition <definition>  --job-name <name> --job-queue <name> --container-overrides "environment=[{name=batchRunType,value=LinearAssetUpdater},{name=assetToUpdate,value=<asset>}]"