#!/bin/bash

if [ $ARGUMENT ]; then
    if [[ $ARGUMENT =~ .+\.[jpg|JPG] ]]
    then
        #DIRNAME=$(dirname "$ARGUMENT")
        #BASENAME=$(basename "$ARGUMENT")
        #NEWFILENAME="$DIRNAME/my_new_folder/$BASENAME"
        #mv "$ARGUMENT" "$NEWFILENAME"
	echo "$ARGUMENT"
    elif [[ $ARGUMENT =~ .+\.[NEF|nef] ]]
    then
	echo "$ARGUMENT"
    	rm "$ARGUMENT"
    fi
fi
