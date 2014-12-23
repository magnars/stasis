# Running Stasis on Docker

Start by creating the image:

```
cd docker
docker build -t stasis .
```

The image expects two volumes to be mounted:

- `/stasis/site` - the folder where your site's code is located.
- `/stasis/build.sh` - a shell script that will build your site

My build.sh looks like this:

```
#!/bin/sh
lein with-profile zclj run -m zombieclj-no.web/export
```

Standing in the project root, I can run:

```
docker run -p 8080:8000 -it -v `pwd`:/stasis/site -v `pwd`/build-zclj.sh:/stasis/build.sh stasis
```

To trigger a build, I can then `curl dockerhost:8080/site/build`

**Please note**: Your export directory must be `build` in the project root.

## Huh, isn't Stasis for static sites? What is this for?

Good point!

This Docker image will do a couple things for you:

- It sets max expiration headers for your CSS, JS, images etc. Make sure you're
  using cache busters, via eg. [Optimus](https://github.com/magnars/optimus).

- It sets up a build queue. It can be pinged at `/site/build`, and will
  pull the latest version with git, then build your site.

## Assumptions

- You're using git for version control
- You're adding cache busters to your resource URLs
- You're building the site out to `./build/`
