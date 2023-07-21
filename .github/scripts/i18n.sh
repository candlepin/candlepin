#!/bin/bash -x

evalrc() {
  if [ "$1" -ne "0" ]; then
    echo "$2"
    exit $1
  fi
}

#Execute the gettext task
./gradlew gettext
evalrc $? "Gettext was not successful for branch $GIT_BRANCH."

files=$(git diff | egrep -v -e '^( |\+#|\-#|@@|\+\+\+|\-\-\-|diff|index)' -e 'X-Generator' -e 'POT-Creation-Date')

if [ ! -z "$files" ]; then

#Code to commit the updated template file
git add po/keys.pot
evalrc $? "Git add file was not successful for branch $GIT_BRANCH."

echo "Committing and pushing the template file."
git -c "user.name=$GIT_AUTHOR_NAME" -c "user.email=$GIT_AUTHOR_EMAIL" commit -m "updated po/keys.pot template"
evalrc $? "Git commit was not successful for branch $GIT_BRANCH."

git push https://${GITHUB_TOKEN}@github.com/candlepin/candlepin $GIT_BRANCH
evalrc $? "Git push was not successful for branch $GIT_BRANCH."

fi