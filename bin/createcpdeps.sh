if [ "$1" == "" ] ; then
 echo "Please specify candlepin-deps git repo location."
 exit
fi

deps_dir=$1

function flatten ()
{
    pushd $M2_REPO > /dev/null
    rm -rf *.jar
    # move all the jars to the top
    find . -name '*.jar' | xargs -n 1 -I jarfile mv jarfile .
    # remove all the directories
    find . -type d | cut -c 3- | xargs -n 1 rm -rf
    popd > /dev/null
    echo "candlepin deps located in $M2_REPO"
}

function create_project_deps ()
{
    cd $1
    buildr nocheckstyle=1 candlepin:$1:local_artifacts
    cd ..
}

# abort on errors
set -e

# create the deps for each project
for proj in "server" "gutterball" "common"; do
    export M2_REPO="$deps_dir/repo/$proj"
    create_project_deps $proj
    flatten
done
