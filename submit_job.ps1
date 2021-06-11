#batchRunType
#batchAction
#assetForValidation
#tierekisteriAction
#tierekisteriAsset
#trafficSignGroup

# UpdateIncompleteLink
aws batch submit-job --profile vaylaapp --job-definition GenericBatch --job-name <name> --job-queue AdHoc --container-overrides "environment=[{name=batchRunType,value=UpdateIncompleteLinkList}]"

# DataFixture
aws batch submit-job --profile vaylaapp --job-definition GenericBatch --job-name <name> --job-queue AdHoc --container-overrides "environment=[{name=batchRunType,value=DataFixture},{name=batchAction,value=<action>}]"

# DataFixture With $trafficSignGroup
aws batch submit-job --profile vaylaapp --job-definition GenericBatch --job-name <name> --job-queue AdHoc --container-overrides "environment=[{name=batchRunType,value=DataFixture},{name=batchAction,value=<action>},{name=trafficSignGroup,value=<trafficSignGroup>}]"

# AssetValidatorProcess
aws batch submit-job --profile vaylaapp --job-definition GenericBatch --job-name <name> --job-queue AdHoc --container-overrides "environment=[{name=batchRunType,value=AssetValidatorProcess},{name=assetForValidation,value=<asset>}]"

# TierekisteriDataImporter
aws batch submit-job --profile vaylaapp --job-definition GenericBatch --job-name <name> --job-queue AdHoc --container-overrides "environment=[{name=batchRunType,value=TierekisteriDataImporter},{name=tierekisteriAction,value=<action>},{name=tierekisteriAsset,value=<Asset>},{name=trafficSignGroup,value=<trafficSignGroup>}]"
