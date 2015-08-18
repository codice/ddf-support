#!/usr/bin/env bash

# On a failed master build, iterate through the plan's list of parents to find relevant commits and post a message to Slack with commits included in the message
# Requires that 2 parameters be passed in:
#   $1 = Name of Slack channel to post to preceeded by a # sign, or its ID.
#   $2 = The URL of the pre-configured Slack webhook.
# For examples, see README.md under ddf-support/support-bamboo

bamboo_planKey="DDF-DIST"
bamboo_buildNumber="58"

get_atlassian() {
    curl "https://codice.atlassian.net/builds/rest/api/latest/result/$1"
    if [ $? != 0 ]; then
        echo "[ERROR] curl command to Bamboo API failed with an exit code of $?. No Slack message was sent."
        exit 0
    fi
}

if [ "${bamboo_inject_jobResult}" != "Success" ]; then

    echo "The build could not complete due to an error. Notifying Slack of build failure"
    commitCount=`get_atlassian "$bamboo_planKey-$bamboo_buildNumber" | sed 's/.*changes size=\"//' | sed 's/\".*//'`

    # If no changes, iterate to parent node
    if [ ${commitCount} == "0" ] && [[ ${commitCount} =~ ^[0-9]+$ ]]; then
        # parses out the buildReason from the XML
        lastParent="${bamboo_planKey}-${bamboo_buildNumber}"
        parent=`get_atlassian ${lastParent} | sed 's/.*\<buildReason//' | sed 's/.*&quot;&gt;//' | sed 's/&lt;.*//'`

        # loop until at the root node (note: the root node is one above the root plan)
        while [[ ${parent} =~ ^DDF-.*-[0-9]+$ ]]; do
            lastParent=${parent}
            parent=`get_atlassian ${lastParent} | sed 's/.*\<buildReason//' | sed 's/.*&quot;&gt;//' | sed 's/&lt;.*//'`
            echo "Checking ${lastParent}..."
        done
        commitCount=`get_atlassian ${lastParent}  | sed 's/.*changes size=\"//' | sed 's/\".*//'`
    fi

    # If still no commits, blame manual run
    if [ ${commitCount} == "0" ]; then
        curl -X POST --data-urlencode \
            "payload={
                \"text\": \":warning: *Build Failure on Master* - <${bamboo_resultsUrl}|${bamboo_planName} - #${bamboo_buildNumber}>\n\`\`\`Manual Build by ${parent}\`\`\`\",
                \"channel\": \"$1\",
                \"username\": \"bamboo\",
                \"icon_emoji\": \":bamboo:\"
            }" $2
        if [ $? != 0 ]; then
            echo "[ERROR] curl command to Slack failed with an exit code of $?. Slack message may have sent incorrectly. commitCount: ${commitCount}"
            exit 0
        fi

   # If there are commits, display offending commits
    elif [[ ${commitCount} =~ ^[0-9]+$ ]]; then
        commitMessages=`git log --pretty=format:'%h %an %s' -n ${commitCount}`
        curl -X POST --data-urlencode \
            "payload={
                \"text\": \":warning: *Build Failure on Master* - <${bamboo_resultsUrl}|${bamboo_planName} - #${bamboo_buildNumber}> - Commits:\n\`\`\`${commitMessages}\`\`\`\",
                \"channel\": \"$1\",
                \"username\": \"bamboo\",
                \"icon_emoji\": \":bamboo:\"
            }" $2
        if [ $? != 0 ]; then
            echo "[ERROR] curl command to Slack failed with an exit code of $?. Slack message may have sent incorrectly. commitCount: ${commitCount}"
            exit 0
        fi
    # If blank or not an integer, somthing went wrong
    else
        echo "[ERROR] commitCount \"$commitCount\" is not an integer. No Slack message was sent."
    fi

fi