#!/bin/bash
# Update the GitHub PR status to success. Should be run in its own stage at the end of the plan.

echo "The build has completed. Notifying GitHub of build success"

data='{"state": "success", "context": "bamboo", "description": "The Bamboo build passed", "target_url": "'"${bamboo_codice_bamboo_url}/browse/${bamboo_planKey}-${bamboo_buildNumber}\"}"
curl -H "Authorization: token ${bamboo_github_api_password}" --request POST --data "${data}" ${bamboo_github_api_url}/repos/codice/${bamboo_repo_name}/statuses/${bamboo_pull_sha} > /dev/null
