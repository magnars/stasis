# <img align="right" src="stasis.png"> stasis

A Clojure library of tools for developing static web sites.

## Install

Add `[stasis "0.3.0"]` to `:dependencies` in your `project.clj`.

## Another static site generator? Why?

I didn't want a static site *framework*, I wanted a *library*. If
you want a framework that makes it really quick and easy to create a
blog, you should take a look at these:

- [misaki](https://github.com/liquidz/misaki) is a Jekyll inspired static site generator in Clojure.
- [Madness](http://algernon.github.io/madness/) is a static site generator, based on Enlive and Bootstrap.
- [Static](http://nakkaya.com/static.html) is a simple static site generator written in Clojure.
- [Ecstatic](http://samrat.me/ecstatic/) creates static web pages and blog posts from Hiccup templates and Markdown.
- [incise](https://github.com/RyanMcG/incise) is an extensible static site generator written in Clojure.

They generally come with a folder where you put your blog posts in
some template language, and a set of configuration options about how
to set up your blog.

This is not that. This is just a set of functions that are pretty
useful when creating static web sites.

## Usage

Stasis works with a map of pages:

```clj
(def pages {"/index.html" (fn [request] {:body "<h1>Welcome!</h1>"})})
```

The basic use case is to serve these live on a local server while
developing - and then exporting them as static pages to deploy on some
server.

#### Serving live pages locally

Stasis creates a Ring handler to serve your pages.

```clj
(ns example
  (:require [stasis.core :as stasis]))

(def app (stasis/serve-pages pages))
```

So like with any Ring app, you point to this app var in `project.clj`:

```clj
:ring {:handler example/app}
```

and start it with `lein ring server-headless`.

#### Exporting the pages

To export pages, just give Stasis some pages and a target directory:

```clj
(defn export []
  (stasis/delete-directory! target-dir)
  (stasis/export-pages pages target-dir))
```

In this example we're also deleting the target-dir first, to ensure
old pages are removed.

When you've got this function, you can create an alias for leiningen:

```clj
:aliases {"build-site" ["run" "-m" "example/export"]}
```

and run `lein build-site` on the command line.

## License

Copyright © 2014 Magnar Sveen

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
