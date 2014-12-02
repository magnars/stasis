#!/bin/sh

ln -s /stasis/site/build /var/www/site/current \
    && service nginx start \
    && nodejs /root/build-queue.js
