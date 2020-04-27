@Library('github.com/connexta/cx-pipeline-library@master') _
@Library('github.com/connexta/github-utils-shared-library@master') __

pipeline {
    agent {
        node {
            label 'linux-small'
            customWorkspace "/jenkins/workspace/${JOB_NAME}/${BUILD_NUMBER}"
        }
    }
    options {
        buildDiscarder(logRotator(numToKeepStr:'25'))
        disableConcurrentBuilds()
        timestamps()
        skipDefaultCheckout()
    }
    triggers {
        /*
          Restrict nightly builds to master branch, all others will be built on change only.
          Note: The BRANCH_NAME will only work with a multi-branch job using the github-branch-source
        */
        cron(BRANCH_NAME == "master" ? "@weekly" : "")
		pollSCM("H/10 * * * *")
    }
    environment {
        DOCS = 'distribution/docs'
        ITESTS = 'distribution/test/itests/test-itests-ddf'
        LARGE_MVN_OPTS = '-Xmx8192M -Xss128M -XX:+CMSClassUnloadingEnabled -XX:+UseConcMarkSweepGC '
        DISABLE_DOWNLOAD_PROGRESS_OPTS = '-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn '
        LINUX_MVN_RANDOM = '-Djava.security.egd=file:/dev/./urandom'
        COVERAGE_EXCLUSIONS = '**/test/**/*,**/itests/**/*,**/*Test*,**/sdk/**/*,**/*.js,**/node_modules/**/*,**/jaxb/**/*,**/wsdl/**/*,**/nces/sws/**/*,**/*.adoc,**/*.txt,**/*.xml'
        GITHUB_USERNAME = 'codice'
        GITHUB_TOKEN = credentials('github-api-cred')
        GITHUB_REPONAME = 'ddf-support'
    }
    stages {
        stage('Setup') {
            steps {
                dockerd {}
                slackSend color: 'good', message: "STARTED: ${JOB_NAME} ${BUILD_NUMBER} ${BUILD_URL}"
                postCommentIfPR("Internal build has been started, your results will be available at build completion.", "${GITHUB_USERNAME}", "${GITHUB_REPONAME}", "${GITHUB_TOKEN}")
            }
        }

        // Checkout the repository
        stage('Checkout repo') {
            steps {
                retry(3) {
                    checkout([$class: 'GitSCM', branches: [[name: "refs/heads/${BRANCH_NAME}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'WipeWorkspace'], [$class: 'LocalBranch', localBranch: "${BRANCH_NAME}"], [$class: 'CloneOption', timeout: 30, shallow: true]], submoduleCfg: [], userRemoteConfigs: scm.userRemoteConfigs ])
                }
            }
        }

        stage('Full Build Except Itests') {
            when {
                expression { env.CHANGE_ID == null } 
            }
            options {
                timeout(time: 3, unit: 'HOURS')
            }
            steps {
                withMaven(maven: 'maven-latest', globalMavenSettingsConfig: 'default-global-settings', mavenSettingsConfig: 'phxSettings', mavenOpts: '${LARGE_MVN_OPTS} ${LINUX_MVN_RANDOM}') {
                    sh 'mvn clean install -B -DskipITs -pl !$ITESTS $DISABLE_DOWNLOAD_PROGRESS_OPTS'
                }
            }
        }

        /*
          Deploy stage will only be executed for deployable branches. These include master and any patch branch matching M.m.x format (i.e. 2.10.x, 2.9.x, etc...).
          It will also only deploy in the presence of an environment variable JENKINS_ENV = 'prod'. This can be passed in globally from the jenkins master node settings.
        */
        stage('Deploy') {
            when {
                allOf {
                    expression { env.CHANGE_ID == null }
                    expression { env.BRANCH_NAME ==~ /((?:\d*\.)?\d*\.x|master)/ }
                    environment name: 'JENKINS_ENV', value: 'prod'
                }
            }
            steps{
                withMaven(maven: 'maven-latest', jdk: 'jdk8-latest', globalMavenSettingsConfig: 'phxOfficeGlobalSettings', mavenSettingsConfig: 'codice-maven-settings', mavenOpts: '${LINUX_MVN_RANDOM}') {
                    sh 'mvn deploy -nsu -DskipTests=true -Dpmd.skip=true -Dfindbugs.skip=true -Dcheckstyle.skip=true -DretryFailedDeploymentCount=10 $DISABLE_DOWNLOAD_PROGRESS_OPTS'
                }
            }
        }

        stage ('SonarCloud') {
            when {
                // Currently Sonar is not run for pull requests
                expression { env.CHANGE_ID == null }
            }
            environment {
                SONARQUBE_GITHUB_TOKEN = credentials('SonarQubeGithubToken')
                SONAR_TOKEN = credentials('sonarqube-token')
            }
            steps {
                withMaven(maven: 'maven-latest', jdk: 'jdk8-latest', globalMavenSettingsConfig: 'phxOfficeGlobalSettings', mavenSettingsConfig: 'phxSettings', mavenOpts: '${LARGE_MVN_OPTS} ${LINUX_MVN_RANDOM}') {
                            sh 'mvn clean -q -B -DskipTests=true -Dfindbugs.skip=true -Dcheckstyle.skip=true org.jacoco:jacoco-maven-plugin:prepare-agent package sonar:sonar -Dsonar.host.url=https://sonarcloud.io -Dsonar.login=$SONAR_TOKEN  -Dsonar.organization=codice -Dsonar.projectKey=ddf-support:master -Dsonar.organization=codice -Dsonar.exclusions=${COVERAGE_EXCLUSIONS} -pl !$DOCS,!$ITESTS $DISABLE_DOWNLOAD_PROGRESS_OPTS'

                }
            }
        }
    }
    post {
        always{
            postCommentIfPR("Build ${currentBuild.currentResult} See the job results in [legacy Jenkins UI](${BUILD_URL}) or in [Blue Ocean UI](${BUILD_URL}display/redirect).", "${GITHUB_USERNAME}", "${GITHUB_REPONAME}", "${GITHUB_TOKEN}")
        }
        success {
            slackSend color: 'good', message: "SUCCESS: ${JOB_NAME} ${BUILD_NUMBER}"
        }
        failure {
            slackSend color: '#ea0017', message: "FAILURE: ${JOB_NAME} ${BUILD_NUMBER}. See the results here: ${BUILD_URL}"
        }
        unstable {
            slackSend color: '#ffb600', message: "UNSTABLE: ${JOB_NAME} ${BUILD_NUMBER}. See the results here: ${BUILD_URL}"
        }
        cleanup {
            catchError(buildResult: null, stageResult: 'FAILURE') {
                echo '...Cleaning up workspace'
                cleanWs()
                sh 'rm -rf ~/.m2/repository'
                wrap([$class: 'MesosSingleUseSlave']) {
                    sh 'echo "...Shutting down Jenkins slave: `hostname`"'
                }
            }
        }
    }
}
