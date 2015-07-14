#!/bin/bash
# Checkout code from the PR branch. Forces clean build

if [ -d "${repo_name}" ]; then
	echo "Cleaning directory ${bamboo_build_working_directory}/${repo_name}"
	rm -rf ${repo_name}
fi

echo "Initializing empty git repository in ${bamboo_build_working_directory}/${bamboo_repo_name}/.git"
mkdir ${bamboo_repo_name}
cd ${bamboo_repo_name}
git init

echo "Fetching '+refs/pull/${bamboo_pull_num}/head' from '${bamboo_repository_git_repositoryUrl}'. Will try to do a shallow fetch."
git remote add origin ${bamboo_repository_git_repositoryUrl}
git fetch --depth 1 origin +refs/pull/${bamboo_pull_num}/head

echo "Checking out revision ${bamboo_pull_sha}"
git checkout FETCH_HEAD
