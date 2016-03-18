# Description:
#   Listens for GitHub webhook pull request notifications and triggers Bamboo builds.
#
# Configuration:
#   bamboo_user
#   bamboo_pass
#   github_api_token

url = require('url')
pathUtil = require('path')
queryString = require('querystring')
actionTypes = ['opened', 'synchronize', 'reopened', 'closed']
eventTypes = ['pull_request']

prPlanName = "DDFPR-ALL"
masterPlanName = "DDF-MSTRINC"

bambooUser = process.env.bamboo_user
bambooPassword = process.env.bamboo_pass
gitHubApiToken = process.env.github_api_token

module.exports = (robot) ->

    # Extracts 'bamboo' request parameter. Fails request if missing.
    getBambooUrl = (request, response) ->
        query = queryString.parse(url.parse(request.url).query)
        bambooUrl = query.bamboo

        if !bambooUrl?
            console.log "[ERROR] Bad request. Missing 'bamboo' parameter in query string"
            response.writeHead 400
            response.end "Missing parameters in query string\n"

        return bambooUrl

    # Determines whether a pr build or master build is needed.
    getPlanName = (eventPayload) ->
        closed = eventPayload.action is "closed"
        merged = eventPayload.pull_request.merged
        if closed and merged then return masterPlanName else return prPlanName


    # Removes filename from path.
    removeFileName = (path) ->
        return pathUtil.dirname(path)


    # Reduce function that removes duplicate paths from list.
    removeDuplicatePaths = (paths, path) ->
        if path not in paths then paths.concat(path) else paths


    # Gets the list of changed paths from a list of changes files.
    # The returned list is sorted in reverse order and doesn't contain any duplicate paths.
    getChangedPaths = (files) ->
        changedPaths = (removeFileName(obj.filename) for obj in files when obj.status isnt "removed")
        changedPaths = changedPaths.reduce(removeDuplicatePaths, [])
        changedPaths.sort().reverse();

        console.log "[INFO] Changed files:"
        console.log changedPaths

        return changedPaths


    # Computes the list of modules that have changed and need to be rebuilt.
    computeChangedModules = (modules, changedPaths) ->
        console.log "[INFO] PR modules:"
        console.log modules

        modulesToBuild = []

        for changedPath in changedPaths
            for module in modules when changedPath.match("^" + module + ".*")
                if module not in modulesToBuild then modulesToBuild.push(module)
                break;

        console.log "[INFO] Modules to build:"
        console.log modulesToBuild

        return modulesToBuild


    # Checks if a Bamboo plan has been successfully submitted.
    isSubmitSuccessful = (responseBody) ->
        return !responseBody.hasOwnProperty("status-code")


    # Updates the GitHub PR status.
    updateGitHubStatus = (url, state, description, targetUrl) ->
        robot.http(url)
            .header('Authorization', "token #{gitHubApiToken}")
            .post(JSON.stringify({
                      "state": state,
                      "context": "bamboo",
                      "description": description,
                      "target_url": targetUrl
                  }))


    # Submits Bamboo build request.
    submitBuildRequest = (bambooUrl, eventPayload, modulesToBuild, planName) ->
        bambooQuery = "#{bambooUrl}/rest/api/latest/queue/#{planName}?" +
            queryString.stringify({
                "bamboo.variable.pull_ref": eventPayload.pull_request.head.ref,
                "bamboo.variable.pull_sha": eventPayload.pull_request.head.sha,
                "bamboo.variable.pull_num": eventPayload.number,
                "bamboo.variable.git_repo_url": eventPayload.repository.clone_url,
                "bamboo.variable.modules": modulesToBuild.join()
            })

        console.log "[DEBUG] Bamboo Query: #{bambooQuery}"

        robot.http(bambooQuery).auth(bambooUser, bambooPassword).header('Accept', 'application/json')
            .header('X-Atlassian-Token', 'no-check')
            .post() (error, response, body) ->
                if error
                    console.log "[ERROR] Failed to submit Bamboo build request: #{error}"
                    updateGitHubStatus(eventPayload.pull_request.statuses_url,
                        "failure", "Failed to submit Bamboo build request: #{error}", "")
                    return

                jsonBody = JSON.parse body

                if isSubmitSuccessful(jsonBody)
                    updateGitHubStatus(eventPayload.pull_request.statuses_url,
                        "pending", "A Bamboo build has been queued",
                        "#{bambooUrl}/browse/#{jsonBody.buildResultKey}")
                else
                    console.log "[ERROR] Failed to submit Bamboo build, request: #{body}"
                    updateGitHubStatus(eventPayload.pull_request.statuses_url,
                        "failure", "#{jsonBody.message}", "#{bambooUrl}/browse/#{planName}")


    # Retrieves list of Maven modules for current PR
    retrieveMavenModules = (gitHubUrl, sha, statusUrl, callback) ->
        robot.http("#{gitHubUrl}/git/trees/#{sha}?recursive=1")
            .get() (error, response, body) ->
                if error
                    console.log "[ERROR] Failed to retrieve list of directories from GitHub: #{error}"
                    updateGitHubStatus(statusUrl, "failure",
                        "Failed to retrieve list of directories from GitHub: #{error}", "")
                    return

                rateLimitRemaining = parseInt response.headers['x-ratelimit-remaining']

                if rateLimitRemaining and rateLimitRemaining < 1
                    console.log "[ERROR] Failed to retrieve list of directories from GitHub: Rate Limit hit."
                    updateGitHubStatus(statusUrl, "failure",
                        "Failed to retrieve list of directories from GitHub: Rate Limit hit.", "")
                    return

                gitTree = JSON.parse body

                if gitTree.truncated == "true" or !gitTree.tree?
                    console.log "[ERROR] Failed to retrieve all directories from GitHub."
                    updateGitHubStatus(statusUrl, "failure",
                        "Failed to retrieve all directories from GitHub.", "")
                    return

                # Build the list of modules by looking for all the pom.xml files that are not under
                # a "resources" directory.
                modules = (
                    removeFileName(obj.path) for obj in gitTree.tree when obj.path? and
                        obj.path.match(".*/pom.xml$") and not obj.path.match(".*/resources/.*")
                )

                # Need to reverse sort Maven modules to make sure the most specific modules are
                # found first when building the list of modules to build.
                # Example:
                #   Maven modules in reverse order: [ a/c/d, a/c, a/b/c, a/b, a ]
                #   Paths changed: [ a/b/x/y/z, a/c/d/x/y/z ]
                #   Modules to rebuild: [ a/b, a/c/d ]
                modules.sort().reverse()

                callback(modules)


    # Retrieves list of changed Maven modules for current PR
    retrieveChangedModules = (gitHubUrl, prNumber, modules, statusUrl, callback) ->
        robot.http("#{gitHubUrl}/pulls/#{prNumber}/files?per_page=100")
            .get() (error, response, body) ->
                if error
                    console.log "[ERROR] Failed to retrieve changed files for PR: #{error}"
                    updateGitHubStatus(statusUrl, "failure",
                        "Failed to retrieve changed files for PR: #{error}", "")
                    return

                if response.headers.link
                    # We need to paginate so there's more than 100 changed files in the PR.
                    # Run a full build.
                    console.log "[INFO] More than 100 changed files in the PR. Building all modules."
                    modulesToBuild = ["."]
                else
                    changedPaths = getChangedPaths(JSON.parse body)
                    modulesToBuild = computeChangedModules(modules, changedPaths)

                callback(modulesToBuild)


    # Request handler used to delay the processing of the incoming events.
    # Needed to avoid race condition where GitHub can't find the sha when requesting the git/trees.
    delayHandler = (seconds) ->
        (request, response, next) -> setTimeout(next, seconds * 1000)


    # Registers Hubot for GitHub Webhook POST requests
    robot.router.post "/hubot/trigger-bamboo", delayHandler(5), (request, response) ->
        console.log "---"
        console.log "[INFO] Request received: #{request.url}"

        bambooUrl = getBambooUrl(request, response)

        if !bambooUrl?
            return

        eventPayload = request.body
        actionType = eventPayload.action
        eventType = request.headers["x-github-event"]

        if eventType not in eventTypes or actionType not in actionTypes
            return

        sha = eventPayload.pull_request.head.sha
        gitHubUrl = eventPayload.repository.url
        prNumber = eventPayload.number
        statusUrl = eventPayload.pull_request.statuses_url
        prPlanName = getPlanName(eventPayload)

        try
            console.log "[INFO] Processing #{actionType}/#{eventType} in repo #{eventPayload.pull_request.html_url}."

            retrieveMavenModules(gitHubUrl, sha, statusUrl, (modules) ->
                retrieveChangedModules(gitHubUrl, prNumber, modules, statusUrl, (modulesToBuild) ->
                    submitBuildRequest(bambooUrl, eventPayload, modulesToBuild, prPlanName)))

        catch error
            console.log "[ERROR] Failed to submit PR build!"
            console.log error.stack
            console.log "[ERROR] Event payload:"
            console.log JSON.stringify eventPayload

        response.end "OK"
