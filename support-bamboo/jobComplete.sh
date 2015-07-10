#!/bin/bash
# Check if job failed and, if so, update the GitHub PR status accordingly. Should be run at the end of every job.

if [ "${bamboo_inject_jobResult}" != "Success" ]; then
	echo "The build could not complete due to an error. Notifying GitHub of build failure"
	
	data='{"state": "failure", "context": "bamboo", "description": "The Bamboo build could not complete due to an error!", "target_url": "'"${bamboo_codice_bamboo_url}/browse/${bamboo_planKey}-${bamboo_buildNumber}\"}"
    curl -H "Authorization: token ${bamboo_github_api_password}" --request POST --data "${data}" ${bamboo_github_api_url}/repos/codice/${bamboo_repo_name}/statuses/${bamboo_pull_sha} > /dev/null
fi
