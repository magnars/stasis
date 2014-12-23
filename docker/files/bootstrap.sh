#!/bin/sh

if [ -d "/stasis/site/build" ]; then
    echo "Found pre-built site, publishing."
    cp -R /stasis/site/build /var/www/site/current
    ln -s /var/www/site/current /var/www/site/live
fi

echo "Starting nginx and build queue."
service nginx start \
    && nodejs /root/build-queue.js
