#!/bin/bash
set -e

if [ -z "$release_version" ]
then
  echo -n "release version: "
  read release_version
fi

if [ -z "$built_by" ]
then
  echo -n "built by: "
  read built_by
fi

mvn versions:set -DgenerateBackupPoms=false -DnewVersion=$release_version

git diff

echo -n "Proceed? [y/N] "
read -n 1 proceed
echo

if [[ $proceed != "Y" && $proceed != "y" ]]; then
  exit 1
fi

git add -u
git commit -m "Release version $release_version"


rm -rf ~/.m2/repository/org/glowroot/instrumentation_
mv ~/.m2/repository/org/glowroot/instrumentation ~/.m2/repository/org/glowroot/instrumentation_ || true

commit=$(git rev-parse HEAD)

USERNAME=$built_by mvn clean deploy -Pjavadoc \
                                    -Prelease \
                                    -Dbuild.commit=$commit
