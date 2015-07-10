#!/bin/bash
# Request current build status from Bamboo and update the GitHub PR status accordingly

body=$(curl --user ddf-admin:${bamboo_admin_password} https://codice-ddf.atlassian.net/builds/rest/api/latest/result/${bamboo_planKey}-JOB1-${bamboo_buildNumber}?buildstate)
buildStatus=$(echo "${body}" | sed -e 's,.*<buildState>\([^<]*\)</buildState>.*,\1,g')
echo ${buildStatus}
echo "https://codice-ddf.atlassian.net/builds/browse/${bamboo_planKey}-${bamboo_buildNumber}"

if [ "${buildStatus}" != "Successful" ]; then
	curl -H "Authorization: token ${bamboo_github_api_password}" --request POST --data '{"state": "failure", "context": "bamboo", "description": "The Bamboo build could not complete due to an error!", "target_url": "https://codice-ddf.atlassian.net/builds/browse/${bamboo_planKey}-${bamboo_buildNumber}"}' https://api.github.com/repos/codice/ddf-libs/statuses/${bamboo_pull_sha}
fi