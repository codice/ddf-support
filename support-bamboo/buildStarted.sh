#!/bin/bash
# Update the GitHub PR status to in-progress. Should be run at the start of the plan after ddf-support has been checked out.

echo "Notifying GitHub that build has started"

data='{"state": "pending", "context": "bamboo", "description": "The Bamboo build is in progress", "target_url": "'"${bamboo_codice_bamboo_url}/browse/${bamboo_planKey}-${bamboo_buildNumber}\"}"
curl -H "Authorization: token ${bamboo_github_api_password}" --request POST --data "${data}" ${bamboo_github_api_url}/repos/codice/${bamboo_repo_name}/statuses/${bamboo_pull_sha} > /dev/null
