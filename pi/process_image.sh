#!/bin/bash

if [ $ARGUMENT ]; then
    if [[ $ARGUMENT =~ .+\.[jpg|JPG] ]]
    then
	echo "Parsing $ARGUMENT"
        exif --extract-thumbnail $ARGUMENT | sed -n '5p;16p;22p;23p;24p;25p;27p;30p;35p;36p;43p;44p;50p' > latest_exif.txt
	mv "$ARGUMENT.modified.jpeg" "thumb-$ARGUMENT"
    elif [[ $ARGUMENT =~ .+\.[NEF|nef] ]]
    then
	echo "Removing $ARGUMENT"
    	rm "$ARGUMENT"
    fi
fi
