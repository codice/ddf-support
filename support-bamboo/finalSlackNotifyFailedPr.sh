#!/usr/bin/env bash

# On a failed automatic PR bamboo build, notifies specified slack channel with links to the PR and the failed build
# Requires that 2 parameters be passed in:
# $1 = Name of Slack channel to post to preceeded by a # sign, or its ID.
# $2 = The URL of the pre-configured Slack webhook.
# For examples, see README.md under ddf-support/support-bamboo

if [ "${bamboo_inject_jobResult}" != "Success" ]; then

	echo "The build could not complete due to an error. Notifying Slack of build failure"
    curl -X POST --data-urlencode "payload={
        \"text\": \"Build Failure in <${bamboo_resultsUrl}|${bamboo_planName} - #${bamboo_buildNumber}>\nFailed PR: <${bamboo_github_ddf_url}/pull/${bamboo_pull_num}|${bamboo_pull_ref}>\",
        \"channel\": \"$1\",
        \"username\": \"bamboo\",
        \"icon_emoji\": \":bamboo:\"
    }" $2
    if [ $? != 0 ]; then
        echo "[ERROR] curl command to Bamboo API failed with an exit code of $?. No Slack message was sent."
        exit 0
    fi

fi
