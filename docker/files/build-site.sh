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
            cp -R /stasis/site/build /var/www/site/next
            mv /var/www/site/current /var/www/site/old
            mv /var/www/site/next /var/www/site/current
            ln -s /var/www/site/current /var/www/site/live
            rm -rf /var/www/site/old
            log "Done!"
        else
            log "Build failed, aborting."
        fi
    else
        log "Site is up to date."
    fi

    rm in-progress.tmp
fi
