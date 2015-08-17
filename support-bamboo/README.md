<!--
/*
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
-->

This directory contains files used to configure CI builds in Bamboo

## Setting up a Bamboo Plan Using Scripts

These scripts are used to integrate Bamboo CI builds with GitHub pull requests. They allow Bamboo to checkout the code changes corresponding to a pull request (PR) and update the status of the build on the GitHub page. They should be used as follows:

1. Create a separate plan for GitHub PR builds.
2. Add as many stages as needed for the build and as many jobs as needed in each stage
3. Within each job, unless specified otherwise, add the following tasks in the order shown:
    * Source Code Checkout -- checkout ddf-support  
    * Script -- run buildStarted.sh to update GitHub build status to in-progress. Only needed in first job of the build  
    * Script -- run checkoutRepo.sh to checkout the correct PR  
    * Any tasks needed for the build
    * Inject Bamboo variables -- Load variable jobResult from the job.properties file. Will only run if previous tasks succeeded  
      `Final Tasks`
    * Script -- run jobComplete.sh. If variable jobResult was not injected, sends a failure notice to the PR status on GitHub
4. Choose one of the following methods to notify GitHub of build success:
    * If the final stage of the build has **more than one job**, add a Notification stage at the end of the build plan and give it a job with the following tasks:
        * Source Code Checkout -- checkout ddf-support  
        * Script -- run planComplete.sh to send a success notice to the PR status on GitHub  
    * If the final stage of the build has **exactly one job**, replace the final task in the job that ran jobComplete.sh with the following task:
        * Script -- run finalJobComplete.sh. This will send a success notice to the GitHub PR status if the jobResult variable was injected. Otherwise, it will send a failure notice.

**Note:** These scripts require a number of variables that are not present in a default Bamboo build. To make the plan run properly, use the Bamboo REST api `queue/{projectKey}-{buildKey}` endpoint to trigger a custom build, and have hubot pass in the following variables:
  * pull_num: The number used to identify the PR
  * pull_sha: The hash of the commit that needs to be built
  * git_repo_url: The base repository to which the PR code changes will be merged  
  * submodule: The specific submodule to be built for a PR build
  
  To queue a pull request build for ddf-catalog, where bamboo is hosted at `https://bamboohost:8085/builds` and has a project with projectKey=`DDFPR` and buildKey=`CAT`, use the following request: 
  
   ```
   curl -i -X POST https://<username>:<password>@bamboohost:8085/builds/rest/api/latest/queue/DDFPR-CAT?bamboo.variable.pull_num=42&bamboo.variable.pull_sha=da39a3ee5e6b4b0d3255bfef95601890afd80709&bamboo.variable.git_repo_url=https://github.com/codice/ddf-support.git"
   ```

**Note:** In addition to the above Hubot variables, the Slack notification scripts also require that 2 arguments are passed in under the "Argument" field in the bamboo "Script" task. Both arguments must be within separate quotations:
    1. The first argument is the channel name preceeded by a # sign, or just a channel ID. (ex, "#channel-name" or "Q81U5HJJ1")
    2. The second argument must the the URL to the pre-configured Slack webhook. Refer to Slack's documentation for more information about setting up an incoming webhook on Slack.

## Configuring Maven Builds Using settings.xml

This file is used to configure Maven with necessary credentials for downloading artifacts during the build, and to point it to the release and snapshot repositories where those artifacts are stored. It should be used as follows:

1. Create a Bamboo Maven task
2. In the Goal field, specify the settings file using the '-s' flag in addition to the goal that Maven should run

    ```
    -s ${bamboo.build.working.directory}/ddf-support/support-bamboo/settings.xml clean install
    ```
    
3. In the Environment variables field, specify the password that Maven should use for the code repositories

    ```
    NEXUS_PASSWORD=${bamboo.ddf.password}
    ```
