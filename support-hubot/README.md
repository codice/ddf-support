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

# Configuring a Hubot for GitHub-Bamboo Integration

## Prerequisites

* [Node.js and npm](https://github.com/nodesource/distributions#installation-instructions "Installation")
  
## Setting up a default Hubot

1. Install yeoman and the hubot-generator

    ```
    npm install -g yo generator-hubot
    ```  

2. Create a new directory where the hubot will be installed. Navigate inside and create a new instance of hubot

    ```
    mkdir githubBambooBot
    cd githubBambooBot
    yo hubot
    ```
    
    The generator will prompt you to provide a few details about the bot: owner, name, description, and adapter. The choice of adapter doesn't matter for the purposes of this guide, and can be left as default. The generator will then initialize a default hubot with the provided information.

## Installing the custom Hubot module

1. Create a new directory `hubot-bamboo-prbuild` and download the files from `ddf-support/support-hubot/hubot-bamboo-prbuild` inside
    
    ```
    mkdir hubot-bamboo-prbuild
    cd hubot-bamboo-prbuild
    wget https://raw.githubusercontent.com/codice/ddf-support/master/support-hubot/hubot-bamboo-prbuild/index.coffee
    wget https://raw.githubusercontent.com/codice/ddf-support/master/support-hubot/hubot-bamboo-prbuild/package.json
    ```
    
2. Navigate to the directory where the hubot was initialized and install the custom module

    ```
    cd ~/path/to/hubot
    npm install ~/path/to/hubot-bamboo-prbuild
    ```
    
    This will install the module in the `node_modules` directory. Code for the GitHub-Bamboo integration can be found in `node_modules/hubot-bamboo-prbuild/index.coffee`
3. Add hubot-bamboo-prbuild to the hubot's `external-script.json` file
    
    ```diff
    [
      "hubot-diagnostics",
      "hubot-help",
      "hubot-google-images",
      "hubot-google-translate",
      "hubot-pugme",
      "hubot-maps",
      "hubot-redis-brain",
      "hubot-rules",
      "hubot-shipit",
    +  "hubot-bamboo-prbuild"
    ]
    ```
    
4. Start the bot. This must be done from the hubot's installation directory
    
    ```
    ./bin/hubot
    ```
    

## Start Hubot in a Tmux session

Hubot must be running constantly to intercept GitHub webhooks and pass them on to Bamboo. It is convenient to start it in a Tmux session which can then be detached and run in the background. When running the hubot on a remote server, this is necessary to ensure that it continues to run after terminating the session in which it was started.

1. Install Tmux
    
    ```
    npm install -g tmux
    ```
    
2. Download `hubotinit.sh` from ddf-support and place it in the hubot installation directory
    
    ```
    wget https://raw.githubusercontent.com/codice/ddf-support/master/support-hubot/hubotinit.sh
    ```
    
    This script takes care of creating a new tmux session with a hubot instance inside. It also injects variables from a `vars.config` file into the hubot environment.

## Create Hubot variables file

Authentication is necessary to use both the GitHub api and the Bamboo REST api, so Bamboo credentials and a GitHub api key must be passed to the hubot for it to work. The hubot-bamboo-prbuild module expects that these will be available as environment variables when it is started. Inject these variables into the hubot environment as follows:

1. Create a `vars.config` file in the hubot installation directory.
2. Add the following variable assignments to the file:
    
    ```
    bamboo_user=<bamboo_username>
    bamboo_pass=<bamboo_password>
    github_api_token=<github_api_token>
    ```
    
    Each line of the file should have exactly one variable assignment, formatted as shown. These variables will then be injected into the hubot environment by the `hubotinit.sh` script.

**NOTE:** This guide assumes that the hubot will be run on a remote server that requires SSH authentication to connect, so that it is safe to store your GitHub api key and Bamboo credentials in the hubot installation directory.