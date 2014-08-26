if [ "$1" == "" ] ; then
 echo "Please specify candlepin-deps git repo location."
 exit
fi

export M2_REPO="$1/repo"

function flatten ()
{
    cd $M2_REPO
    rm -rf *.jar
    # move all the jars to the top
    find . -name '*.jar' | xargs -n 1 -I jarfile mv jarfile .
    # remove all the directories
    find . -type d | cut -c 3- | xargs -n 1 rm -rf
    cd ..
    echo "candlepin deps located in $M2_REPO"
}

buildr nocheckstyle=1 artifacts
flatten
