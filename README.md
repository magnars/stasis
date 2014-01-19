# <img align="right" src="stasis.png"> stasis

A Clojure library of tools for developing static web sites.

## Install

Add `[stasis "0.6.1"]` to `:dependencies` in your `project.clj`.

## Another static site framework? Why?

Well, that's exactly it. I didn't want to use a framework. I don't
like the restrained feeling I get when using them. I prefer coding
things over messing around with configuration files.

I want to

- code my own pages
- set up my own configuration
- choose my own templating library
- create my own damn stylesheets

**Statis only offers a few functions that are pretty useful when
creating static web sites.**

No more. There are no batteries included.

If you want a framework that makes it really quick and easy to create
a blog, you should take a look at these:

- [misaki](https://github.com/liquidz/misaki) is a Jekyll inspired static site generator in Clojure.
- [Madness](http://algernon.github.io/madness/) is a static site generator, based on Enlive and Bootstrap.
- [Static](http://nakkaya.com/static.html) is a simple static site generator written in Clojure.
- [Ecstatic](http://samrat.me/ecstatic/) creates static web pages and blog posts from Hiccup templates and Markdown.
- [incise](https://github.com/RyanMcG/incise) is an extensible static site generator written in Clojure.

They generally come with a folder where you put your blog posts in
some templating language, and a set of configuration options about how
to set up your blog. They might generate some code for you to tweak.

## Usage

Stasis works with a map of pages:

```clj
(def pages {"/index.html" "<h1>Welcome!</h1>"})
```

The basic use case is to serve these live on a local server while
developing - and then exporting them as static pages to deploy on some
server.

#### Serving live pages locally

Stasis can create a Ring handler to serve your pages.

```clj
(ns example
  (:require [stasis.core :as stasis]))

(def app (stasis/serve-pages pages))
```

Like with any Ring app, you point to your `app` in `project.clj`:

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

and run `lein build-site` on the command line. No need for a lein
plugin.

#### Even more lively live pages

Let's say you want to dynamically determine the set of pages - maybe
based on files in a folder. You'll want those to show up without
restarting.

To be fully live, instead pass `serve-pages` a `get-pages` function:

```clj
(defn get-pages []
  (merge {"/index.html" "<h1>Welcome!</h1>"}
         (get-product-pages)
         (get-people-pages)))

(def app (stasis/serve-pages get-pages))
```

#### Do I have to build every single page for each request?

No. That's potentially quite a lot of parsing for a large site. You
might also want to pass along some context to each page.

```
(def pages {"/index.html" (fn [context] (str "<h1>Welcome to " (:uri context) "!</h1>"))})
```

Since we're dynamically building everything for each request, having a
function around the contents means you don't have to build out the
entire site contents every time.

The `context` equals the `request` when it's served live as a Ring
app, and as such contains the given `:uri`. Stasis' `export-pages`
makes sure to add `:uri` to the context too.

You can also pass in configuration options that is included on the
`context`:

```
(def app (stasis/serve-pages get-pages {:my-config "options"}))

(stasis/export-pages pages target-dir {:my-config "options"})
```

These will then be available when rendering your page.

Finally, there are some Ring middlewares that put values on the
request to be used in rendering. This supports that. Read on:

## But what about stylesheets, images and javascript?

Yeah, Stasis doesn't really concern itself with that, since it doesn't
have to.

In its simplest form, you can add some JavaScript and CSS to the map
of pages. It'll be served and exported just fine. Which is good if you
want to dynamically create some JSON, for instance.

But for truly static assets, I recommend a frontend optimization
library. You can use any asset lib that hooks into Ring and lets you
export the optimized assets to disk.

I use [Optimus](https://github.com/magnars/optimus). To get you
started, here's an example:

```clj
(ns example
  (:require [ring.middleware.content-type :refer [wrap-content-type]]
            [stasis.core :as stasis]
            [optimus.prime :as optimus]
            [optimus.assets :as assets]
            [optimus.optimizations :as optimizations]
            [optimus.strategies :refer [serve-live-assets]]
            [optimus.export]
            [example.app :refer [get-pages target-dir]]))

(defn get-assets []
  (assets/load-assets "public" ["/styles/all.css"
                                #"/photos/.*\.jpg"]))

(def app (-> (stasis/serve-pages get-pages)
             (optimus/wrap get-assets optimizations/all serve-live-assets)
             wrap-content-type))

(defn export []
  (let [assets (optimizations/all (get-assets) {})]
    (stasis/empty-directory! target-dir)
    (optimus.export/save-assets assets target-dir)
    (stasis/export-pages (get-pages) target-dir {:optimus-assets assets})))
```

I create a function to get all the assets, and then add the Optimus
Ring middleware to my app. I want to serve assets live, but still have
them optimized - this lets my dev environment be as similar to prod as
possible.

Then I simply tell Optimus to export its assets into the same target
dir as Stasis.

Notice that I add `:optimus-assets` to the config map passed to
`stasis/export-pages`, which will then be available on the `context`
map passed to each page-generating function. This mirrors what
`optimus/wrap` does on the live Ring server, and allows for linking to
assets by their original path.

That's all the detail I'll go into here, but you can read more about
all the ways Optimus helps you with frontend performance optimization
in its extensive [README](https://github.com/magnars/optimus).

## So, what else does Stasis have to offer?

This is about everything you need to start building static sites. But
Stasis does come with a few more tools.

### `slurp-directory`

You'll probably create a folder to hold a list of pages, posts,
products or people at some point. Read them all in with `slurp-directory`.

```clj
(def articles (slurp-directory "resources/articles/" #"\.md$"))
```

This gives us a map `{"/relative/path.md" "file contents"}`. The
relative path can be useful if we're creating URLs based on file
names.

Here's another example:

```clj
(def products (->> (slurp-directory "resources/products/" #"\.edn$")
                   (vals)
                   (map read-string)))
```

This matches all edn-files in `resources/products/`, slurps in their
contents and transforms it to a list of Clojure data structures.

## Q & A

### Why won't my enlive templates update when I edit them?

Your template definitions are reloaded only when you touch the
code in that namespace. Ring is unaware of the dependency on the
template files.

There are some tricks
[in this thread](https://github.com/cgrand/enlive/issues/6). I prefer
to place my templates in a separate namespace, and do this:

```clj
(defn reload-templates []
  (require 'example.templates :reload))
```

And then call that in my `get-pages` function.

### How do I create an RSS feed for my blog?

No worries, it's just a bit of XML. Here's a working snippet from
whattheemacsd.com to create an Atom feed:

```clj
(ns what-the-emacsd.rss
  (:require [clojure.data.xml :as xml]))

(defn- entry [post]
  [:entry
   [:title (:name post)]
   [:updated (:date post)]
   [:author [:name "Magnar Sveen"]]
   [:link {:href (str "http://whattheemacsd.com" (:path post))}]
   [:id (str "urn:whattheemacsd-com:feed:post:" (:name post))]
   [:content {:type "html"} (:content post)]])

(defn atom-xml [posts]
  (xml/emit-str
   (xml/sexp-as-element
    [:feed {:xmlns "http://www.w3.org/2005/Atom"}
     [:id "urn:whattheemacsd-com:feed"]
     [:updated (-> posts first :date)]
     [:title {:type "text"} "What the .emacs.d!?"]
     [:link {:rel "self" :href "http://whattheemacsd.com/atom.xml"}]
     (map entry posts)])))
```

There's even a library to create RSS for you here:
[clj-rss](https://github.com/yogthos/clj-rss).

## License

Copyright © 2014 Magnar Sveen

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
