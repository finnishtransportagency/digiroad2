# Updates parameters type to SecureString and parameter value to the given value.
# NOTE! Remember to fill the --value parameter with the password you want to set to the parameter.

# Works with Windows cmd (copy and paste).
# Run one at a time.

# --profile: profile name in the credentials file
# --name: Name of the parameter we want to update
# --type: Type of the parameter (we want to update it from String -> SecureString)
# --value: Value to be given to the parameter (i.e. the secret)
aws ssm put-parameter --region eu-west-1 --profile vaylaapp --overwrite --name "/prod/bonecp.password" --type "SecureString" --value ""
aws ssm put-parameter --region eu-west-1 --profile vaylaapp --overwrite --name "/prod/apikey/viite" --type "SecureString" --value ""
aws ssm put-parameter --region eu-west-1 --profile vaylaapp --overwrite --name "/prod/apikey/viitekehysmuunnin" --type "SecureString" --value ""
aws ssm put-parameter --region eu-west-1 --profile vaylaapp --overwrite --name "/prod/apikey/mml_map" --type "SecureString" --value ""
aws ssm put-parameter --region eu-west-1 --profile vaylaapp --overwrite --name "/prod/ses_username" --type "SecureString" --value ""
aws ssm put-parameter --region eu-west-1 --profile vaylaapp --overwrite --name "/prod/ses_password" --type "SecureString" --value ""
aws ssm put-parameter --region eu-west-1 --profile vaylaapp --overwrite --name "/prod/googlemapapi.client_id" --type "SecureString" --value ""
aws ssm put-parameter --region eu-west-1 --profile vaylaapp --overwrite --name "/prod/googlemapapi.crypto_key" --type "SecureString" --value ""
aws ssm put-parameter --region eu-west-1 --profile vaylaapp --overwrite --name "/prod/vvhRest.password" --type "SecureString" --value ""
aws ssm put-parameter --region eu-west-1 --profile vaylaapp --overwrite --name "/prod/apikey/kgv.apikey" --type "SecureString" --value ""
aws ssm put-parameter --region eu-west-1 --profile vaylaapp --overwrite --name "/prod/apikey/vallu.apikey" --type "SecureString" --value ""
