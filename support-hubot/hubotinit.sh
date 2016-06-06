#!/bin/bash
# Create a new tmux session and start a new hubot instance inside.
# Configure a cron job that runs this script on reboot to automate hubot initialization.

SESSIONNAME="hubot"
SCRIPTPATH=$( cd $(dirname $0) ; pwd -P )
tmux has-session -t $SESSIONNAME 2> /dev/null

if [ $? -ne 0 ]; then
  FILE="${SCRIPTPATH}/vars.config"     # File where hubot environment variables are stored
  VARS=$(cat $FILE | tr '\n' ' ')      # Capture hubot environment variables

  tmux new-session -s $SESSIONNAME -d
  tmux send-keys -t $SESSIONNAME "cd $SCRIPTPATH" C-m

  # Start hubot with the environment variables from $VARS
  tmux send-keys -t $SESSIONNAME "env $VARS ./bin/hubot" C-m
else
  echo Session $SESSIONNAME  already exists
fi