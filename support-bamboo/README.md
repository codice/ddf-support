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
3. Within each job, add the following tasks in the order shown:
  * Source Code Checkout -- checkout ddf-support
  * Script -- run buildStarted.sh to update GitHub build status to in-progress. Only needed in first job of the build
  * Script -- run checkoutRepo.sh to checkout the correct PR
  * Any tasks needed for the build
  * Inject Bamboo variables -- Load variable jobResult from the job.properties file. Will only run if previous tasks succeeded  

    `Final Tasks`
  * Script -- run jobComplete.sh. If variable jobResult was not injected, sends a failure notice to the PR status on GitHub
4. Add a Notification stage at the end of the build plan and give it a job with the following tasks
  * Source Code Checkout -- checkout ddf-support
  * Script -- run planComplete.sh to send a success notice to the PR status on GitHub

**Note:** These scripts require a number of variables that are not present in a default Bamboo build. To make the plan run properly, use the Bamboo REST api `/queue/{projectKey}-{buildKey}-{buildNumber}` endpoint to trigger a custom build, and pass in the following variables:
* pull_num: The number used to identify the PR
* pull_sha: The hash of the commit that needs to be built

## Configuring Maven Builds Using settings.xml

This file is used to configure Maven with necessary credentials for downloading artifacts during the build, and to point it to the release and snapshot repositories where those artifacts are stored. It should be used as follows:

1. Create a Bamboo Maven task
2. In the Goal field, specify the settings file using the '-s' flag in addition to the goal that Maven should run

  `-s ${bamboo.build.working.directory}/ddf-support/support-bamboo/settings.xml clean install`

3. In the Environment variables field, specify the password that Maven should use for the code repositories

  `NEXUS_PASSWORD=${bamboo.ddf.password}`
