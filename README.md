# <img align="right" src="logos/stasis-square.png" width="115" height="115"> Stasis [![Build Status](https://secure.travis-ci.org/magnars/stasis.png)](http://travis-ci.org/magnars/stasis)

A Clojure library of tools for developing static web sites.

### Breaking change in 2.0

- **Stasis now only accepts paths that end in a file extension or a slash `/`.**

  Stasis exports paths without a file extension as directories with an
  `index.html` file. Most web servers will respond to the slash-less request
  with a redirect to the URL including a slash. This redirect is entirely
  avoidable by just linking to the right URL in the first place.

  This change should help you avoid these needless redirects, increasing the
  speed of your site further.

## Install

Add `[stasis "2.5.0"]` to `:dependencies` in your `project.clj`.

Please note that this project uses [Semantic Versioning](http://semver.org/).
There will be no breaking changes without a major version increase. There's also
a [change log](#change-log).

## Another static site framework? Why?

Well, that's exactly it. I didn't want to use a framework. I don't like the
restrained feeling I get when using them. I prefer coding things over messing
around with configuration files.

I want to

- code my own pages
- set up my own configuration
- choose my own templating library

**Statis is a collection of functions that are useful when creating
static web sites.**

No more. There are no batteries included.

If you want a framework that makes it really quick and easy to create
a blog, you should take a look at these:

- [misaki](https://github.com/liquidz/misaki) is a Jekyll inspired static site generator in Clojure.
- [Madness](http://algernon.github.io/madness/) is a static site generator, based on Enlive and Bootstrap.
- [Static](http://nakkaya.com/static.html) is a simple static site generator written in Clojure.
- [Ecstatic](http://samrat.me/ecstatic/) creates static web pages and blog posts from Hiccup templates and Markdown.
- [incise](https://github.com/RyanMcG/incise) is an extensible static site generator written in Clojure.
- [Cryogen](http://cryogenweb.org/) is a static sites generator written in Clojure

They generally come with a folder where you put your blog posts in
some templating language, and a set of configuration options about how
to set up your blog. They often generate code for you to tweak.

## Usage

The core of Stasis is two functions: `serve-pages` and `export-pages`.
Both take a map from path to contents:

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

Add [Ring](https://github.com/ring-clojure/ring) as a dependecy and
[Lein-Ring](https://github.com/weavejester/lein-ring) as a plugin, and point
Ring to your `app` in `project.clj`.

```clj
:ring {:handler example/app}
```

and start it with `lein ring server-headless`.

#### Exporting the pages

To export, just give Stasis some pages and a target directory:

```clj
(defn export []
  (stasis/empty-directory! target-dir)
  (stasis/export-pages pages target-dir))
```

In this example we're also emptying the target-dir first, to ensure
old pages are removed.

When you've got this function, you can create an alias for leiningen:

```clj
:aliases {"build-site" ["run" "-m" "example/export"]}
```

and run `lein build-site` on the command line. No need for a lein
plugin.

#### Livelier live pages

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

No. That's potentially quite a lot of parsing for a large site.

```clj
(def pages
  {"/index.html" (fn [context] (str "<h1>Welcome to " (:uri context) "!</h1>"))})
```

Since we're dynamically building everything for each request, having a
function around the contents means you don't have to build out the
entire site every time.

Stasis passes a `context` to each page. When it's served live as a
Ring app the `context` is actually the Ring request, and as such
contains the given `:uri`. Stasis' `export-pages` makes sure to add
`:uri` to the context too.

You can also pass in configuration options that are included on the
`context`:

```clj
(defn my-config {:some "options"})

(def app (stasis/serve-pages get-pages my-config))

(stasis/export-pages pages target-dir my-config)
```

These are then available when rendering your page.

Finally, some Ring middlewares put values on the request to be used in
rendering. This supports that. Read on:

## But what about stylesheets, images and javascript?

Yeah, Stasis doesn't really concern itself with that, since it doesn't
have to.

In its simplest form, you can add some JavaScript and CSS to the map
of pages. It'll be served and exported just fine. Which is good if you
want to dynamically create some JSON, for instance.

But for your CSS, JavaScript and images, I recommend a frontend
optimization library. You can use any asset lib that hooks into Ring
and lets you export the optimized assets to disk.

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
  (let [assets (optimizations/all (get-assets) {})
        pages (get-pages)]
    (stasis/empty-directory! target-dir)
    (optimus.export/save-assets assets target-dir)
    (stasis/export-pages pages target-dir {:optimus-assets assets})))
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

**Fun fact:** This is a valid code:

```clj
(def app (serve-pages (slurp-directory "resources/pages/" #"\.html$")))
```

It would serve all .html files in that folder, matching the URL
structure to files on disk.

Like [`slurp`](https://clojuredocs.org/clojure.core/slurp),
`slurp-directory` can also receive optional arguments such as `:encoding`:

```clj
(slurp-directory "resources/articles/" #"\.md$" :encoding "UTF-8")
```

### `slurp-resources`

Just like `slurp-directory`, except it reads off the class path
instead of directly from disk. For performance reasons the `.m2`
folder is excluded. Open an issue if that causes you pain.

### `merge-page-sources`

You might have several sources for pages that need to be merged into
the final `pages` map. Wouldn't it be nice if someone told you about
conflicting URLs?

```clj
(defn create-pages [content]
  (merge-page-sources
   {:person-pages (create-person-pages (:people content))
    :article-pages (create-article-pages (:articles content))
    :general-pages (create-general-pages content)}))

(defn get-pages []
  (create-pages (load-content)))
```

So `merge-page-sources` takes a map. The values are the page-maps to
merge. The keys in the map are only used for error reporting:

```
URL conflicts between :article-pages and :general-pages: #{"/about.html"}
```

### `report-differences`

This entirely side-effecty function takes an old and a new map of strings,
compares them and reports the differences to standard out.

Here's a code example from one of my projects:

```clj
(def export-dir "./dist")

(defn- load-export-dir []
  (stasis/slurp-directory export-dir #"\.[^.]+$"))

(defn export
  "Export the entire site as flat files to the export-dir."
  []
  (let [old-files (load-export-dir)] ;; 1.
    (stasis/empty-directory! export-dir)
    (stasis/export-pages (get-pages) export-dir)
    (println)
    (println "Export complete:")
    (stasis/report-differences old-files (load-export-dir)) ;; 2.
    (println)))
```

1. We slurp the old directory into memory before emptying it

2. After we're done exporting, pass in old and new files to print the report.

This prints something along these lines (in glorious ansi color):

```
Export complete:
- 260 unchanged files.
- 2 changed files:
    - /index.html
    - /troubleshooting/index.html
- 1 removed file:
    - /verified-hash/index.html
- 1 added file:
    - /verified-hashes/index.html
```

### Dependent pages

Instead of returning a string of html for your pages, you may return a map:

```clj
{:contents "the page contents",
 :dependent-pages {"/uri" "dependent page contents"}}
```

These dependent pages are then also served and exported.

#### Why would I need that?

Most often, you don't.

However: When creating a page, you may find that you want to extract pieces of
the page into yet new pages. Say you want to do some optimizations and extract
inline JavaScript from pages and serve them as cacheable separate files.

Dependent pages gives you the opportunity to do this without cluttering up your
page-creation logic. When the page containing JavaScript is loaded, you may
alter the generated page to use a script tag that links to a separate file, and
then return a page map where `:dependent-pages` contains the page (of
JavaScript) that contains the extracted inline source.

## Q & A

### Can I avoid the .html endings on my pages?

Yes. Stasis will handle URLs like `/projects/clojure/` by creating
`projects` and `clojure` folders, and placing an `index.html` in it.

Beware of the implications tho. You suddenly have multiple valid URLs
to the same page, which is not good for your standing with Google.
There's also the case of `/projects/clojure` vs `/projects/clojure/`
that might trip you up.

It'll probably take some wrangling of your web server to get this
in pristine shape.

### Are there any full fledged examples to look at?

[Christian Johansen](https://github.com/cjohansen/) wrote
[Building static sites in Clojure with Stasis](http://cjohansen.no/building-static-sites-in-clojure-with-stasis),
which is an excellent starting point.

If you just want some code to look at, check these out:

- [whattheemacsd.com](http://whattheemacsd.com/)
  [(source)](https://github.com/magnars/what-the-emacsd)

  Uses [Enlive](https://github.com/cgrand/enlive) for templating, and
  [Optimus](https://github.com/magnars/optimus) for frontend
  optimization.

- [sinonjs.org](http://sinonjs.org) [(source)](https://github.com/sinonjs/sinon-docs)

  Uses a combination of
  [Hiccup](https://github.com/weavejester/hiccup),
  [Enlive](https://github.com/cgrand/enlive) and Markdown (via
  [Cegdown](https://github.com/Raynes/cegdown)) to build the pages,
  [Optimus](https://github.com/magnars/optimus) for frontend
  optimization, and Pygments (via
  [Clygments](https://github.com/bfontaine/clygments)) for code
  highlighting.

- [augustl.com](http://augustl.com) [(source)](https://github.com/augustl/augustl.com)

  Uses a home brewed HTML based templating system, Pygments (via
  [Clygments](https://github.com/bfontaine/clygments)) for syntax highlighting,
  and [Optimus](https://github.com/magnars/optimus) for the asset pipeline.

- [bryan.codes](http://bryan.codes/) [(source)](https://github.com/gilbertw1/blog-gen)

  Parses markdown table syntax to create metadata and tags for its blog posts.
  Clever! Uses many of the same technologies listed above.

- [lepo.io](https://lepo.io/) [(source)](https://github.com/Lepovirta/lepo)

  Uses [Selmer](https://github.com/yogthos/Selmer) and
  [Hiccup](https://github.com/weavejester/hiccup) for building the pages, and
  [Optimus](https://github.com/magnars/optimus) for building assets.
  
- [sneakycode.net](http://sneakycode.net/) [(source)](https://github.com/sneakypeet/sneakycode.net)

  Parses Markdown as well as hiccup defined in edn files. 
  Edn pages and posts can define rendering functions that are aware of the entire site for maximum flexibility.

Got an open source site written in Stasis? Do let me know, and I'll
add it here!

### Why won't my Enlive templates update when I edit them?

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

No worries, it's just a bit of XML generation. Here's a working
snippet from [whattheemacsd.com](http://whattheemacsd.com/) to create
an Atom feed:

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

If this seems like too much, well, maybe you're using the wrong static
site library. But anyway, there's even a library to create RSS for you
here: [clj-rss](https://github.com/yogthos/clj-rss).

### Is Stasis stable?

Yes. This project uses [Semantic Versioning](http://semver.org/). There will be
no breaking changes without a major version increase. And we've got plenty of
tests to keep us in line.

### Again, why not use one of the existing frameworks?

I think the existing frameworks are great if they fit your style.
Stasis imposes no styles. There are very few decisions made for you -
no markdown vs asciidoc, no enlive vs hiccup. No configuration
options. You have to - and get to - make those yourself.

So, yeah ... I think Stasis would be a great starting point if you
want to create the 6th static site framework to go in that list at the
top. :-)

## Change log

#### From 2.4 to 2.5

- Binary files can now be served by Stasis. (Stephen Starkey)

#### From 2.3 to 2.4

- Dependencies bumped ahead four years

#### From 2.2 to 2.3

- `slurp-directory` and `slurp-resources` now take options like
  [`slurp`](https://clojuredocs.org/clojure.core/slurp).

#### From 2.1 to 2.2

- Add support for [dependent pages](#dependent-pages)

#### From 2.0 to 2.1

- Add `report-differences`

#### From 1.1 to 2.0

- **Stasis now only accepts paths that end in a file extension or a slash `/`.**

  Stasis exports paths without a file extension as directories with an
  `index.html` file. Most web servers will respond to the slash-less request
  with a redirect to the URL including a slash. This redirect is entirely
  avoidable by just linking to the right URL in the first place.

  This change should help you avoid these needless redirects, increasing the
  speed of your site further.

#### From 1.0 to 1.1

- Add `slurp-resources`
- Ensure page paths are absolute (Cesar BP)
- Fix an issue with running Stasis on Windows (Oak Nauhygon)

## Contributors

- [Oak Nauhygon](https://github.com/nauhygon) fixed an issue with running Stasis on Windows.
- [Cesar BP](https://github.com/cesarbp) made sure that page URLs are absolute.
- [Christian Johansen](https://github.com/cjohansen) handled encoded URIs coming in to the local server.
- [Mark Hudnall](https://github.com/landakram) improved `slurp-directory` and `slurp-resources`
- [Stephen Starkey](https://github.com/coreagile) added support for binary files.

Thanks!

## Contribute

Yes, please do. And add tests for your feature or fix, or I'll
certainly break it later.

#### Running the tests

`lein midje` will run all tests.

`lein midje namespace.*` will run only tests beginning with "namespace.".

`lein midje :autotest` will run all the tests indefinitely. It sets up a
watcher on the code files. If they change, only the relevant tests will be
run again.

## License

Copyright © 2014 Magnar Sveen

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
