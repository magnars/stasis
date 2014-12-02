#!/bin/sh

timestamp() {
    date -u +"%Y-%m-%dT%H:%M:%SZ"
}

log () {
    echo $1
    echo "$(timestamp): $1" >> /stasis/build.log
}

if [ ! -d "/var/www/site/current" ]; then
    changed=1
fi

cd /stasis/site

if [ -f "in-progress.tmp" ]; then
    log "Build in progress, aborting"
else
    touch in-progress.tmp

    git pull | grep -q -v 'Already up-to-date.' && changed=1

    if [ $changed ]; then
        log "Building"
        /stasis/build.sh && built=1
        if [ $built ]; then
            log "Publishing"
            rm -rf /var/www/site/current
            ln -s /stasis/site/build /var/www/site/current
            log "Done!"
        else
            log "Build failed, aborting."
        fi
    else
        log "Site is up to date."
    fi

    rm in-progress.tmp
fi
