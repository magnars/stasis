FROM eclipse-temurin:21

# Install dependencies
RUN apt-get update && apt-get install -y \
  git \
  nodejs \
  nginx

# Get leiningen
ENV LEIN_ROOT 1
RUN curl -s https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein > \
    /usr/local/bin/lein \
 && chmod 0755 /usr/local/bin/lein \
 && lein upgrade

# Set up nginx

ADD files/nginx.conf /etc/nginx/nginx.conf
ADD files/site.conf /etc/nginx/sites-enabled/site.conf
RUN useradd -r -U nginx

# Add site building machinations

ADD files/build-site.sh /root/build-site.sh
ADD files/build-queue.js /root/build-queue.js
RUN mkdir -p /var/www/site /stasis \
 && chmod a+x /root/build-site.sh

# Kick it off

ADD files/bootstrap.sh /root/bootstrap.sh

CMD ["/root/bootstrap.sh"]

EXPOSE 8000
