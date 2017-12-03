#!/bin/bash

#
# squash commits after a point
#

set -e -u

point=ab16ace64881ea0ba67f7f70f7fcbd832a7b7b7f

git reset --soft $point

git add -A

git commit -m "develop"

git push --force
