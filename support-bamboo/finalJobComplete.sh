#!/bin/bash
# Update GitHub PR with the build status. Should be run as a final task in the last job of the build. Since jobs run in parallel, this requires that the job is the only job in its stage.

if [ "${bamboo_inject_jobResult}" != "Success" ]; then
	echo "The build could not complete due to an error. Notifying GitHub of build failure"
	
	data='{"state": "failure", "context": "bamboo", "description": "The Bamboo build could not complete due to an error!", "target_url": "'"${bamboo_codice_bamboo_url}/browse/${bamboo_planKey}-${bamboo_buildNumber}\"}"
    curl -H "Authorization: token ${bamboo_github_api_password}" --request POST --data "${data}" ${bamboo_github_api_url}/repos/codice/${bamboo_repo_name}/statuses/${bamboo_pull_sha} > /dev/null
else
	echo "The build has completed. Notifying GitHub of build success"

	data='{"state": "success", "context": "bamboo", "description": "The Bamboo build passed", "target_url": "'"${bamboo_codice_bamboo_url}/browse/${bamboo_planKey}-${bamboo_buildNumber}\"}"
	curl -H "Authorization: token ${bamboo_github_api_password}" --request POST --data "${data}" ${bamboo_github_api_url}/repos/codice/${bamboo_repo_name}/statuses/${bamboo_pull_sha} > /dev/null
fi
