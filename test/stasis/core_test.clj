(ns stasis.core-test
  (:require [stasis.core :refer :all]
            [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [test-with-files.core :refer [with-files with-tmp-dir tmp-dir public-dir]]))

(fact
 "Stasis creates a Ring handler to serve your pages."

 (let [app (serve-pages {"/page.html" "The page contents"})]

   (app {:uri "/page.html"}) => {:status 200
                                 :body "The page contents"
                                 :headers {"Content-Type" "text/html"}}

   (app {:uri "/missing.html"}) => {:status 404
                                    :body "<h1>Page not found</h1>"
                                    :headers {"Content-Type" "text/html"}}))

(fact
 "You can pass in a `get-pages` function too, if you need to determine
  the set of pages dynamically and want them to be properly live."

 (let [get-pages (fn [] {"/page.html" "The page contents"})
       app (serve-pages get-pages)]

   (app {:uri "/page.html"}) => {:status 200
                                 :body "The page contents"
                                 :headers {"Content-Type" "text/html"}}))

(fact
 "A page can be a function too, which is passed its context with :uri in."

 (let [app (serve-pages {"/page.html" (fn [ctx] (str "I'm serving " (:uri ctx)))})]

   (:body (app {:uri "/page.html"})) => "I'm serving /page.html"))

(fact
 "If you use paths without .html, it serves them as directories with
  an index.html."

 (let [app (serve-pages {"/page/" (fn [ctx] (str "I'm serving " (:uri ctx)))})]

   (:body (app {:uri "/page/index.html"})) => "I'm serving /page/index.html"
   (:body (app {:uri "/page/"})) => "I'm serving /page/index.html"
   (:body (app {:uri "/page"})) => "I'm serving /page/index.html"))

(fact
 "If you use paths with strange characters, like { and }, it transparently
  decodes incoming URLs"

 (let [app (serve-pages {"/page/{thing-a-majig}" (fn [ctx] (str "I'm serving " (:uri ctx)))})]

   (:body (app {:uri "/page/%7Bthing-a-majig%7D"})) => "I'm serving /page/{thing-a-majig}/index.html"))

(fact
 "You can pass along config options to serve-pages that will be
  included on each request."

 (let [app (serve-pages {"/page" (fn [ctx] (str "Config: " (:config ctx)))}
                        {:config "Passed!"})]

   (:body (app {:uri "/page"})) => "Config: Passed!"))

(fact
 "You can serve other types of assets too."

 (let [app (serve-pages {"/page-details.js" (fn [ctx] (str "alert('" (:uri ctx) "');"))})]

   (app {:uri "/page-details.js"}) => {:status 200
                                       :body "alert('/page-details.js');"}))

(fact
 "Stasis exports pages to your directory of choice."

 (with-tmp-dir
   (export-pages {"/page/index.html" "The contents"}
                 tmp-dir)
   (slurp (str tmp-dir "/page/index.html")) => "The contents"))

(fact
 "Stasis adds the :uri to the context for exported pages."

 (with-tmp-dir
   (export-pages {"/page" (fn [ctx] (str "I'm serving " (:uri ctx)))}
                 tmp-dir)
   (slurp (str tmp-dir "/page/index.html")) => "I'm serving /page/index.html"))

(fact
 "You can add more information to the context if you want. Like
  configuration options, or optimus assets."

 (with-tmp-dir
   (export-pages {"/page" (fn [ctx] (str "I got " (:conf ctx)))}
                 tmp-dir {:conf "served"})
   (slurp (str tmp-dir "/page/index.html")) => "I got served"))

(with-tmp-dir
  (export-pages {"/folder/page.html" (fn [ctx] "Contents")} tmp-dir {})

  (fact
   "You can't accidentaly empty a file with empty-directory!"

   (empty-directory! (str tmp-dir "/folder/page.html"))
   => (throws Exception (str tmp-dir "/folder/page.html is not a directory.")))

  (fact
   "But it's really easy emptying an entire folder of files. Be careful."

   (empty-directory! (str tmp-dir "/folder"))

   (io/as-file (str tmp-dir "/folder/page.html")) => #(not (.exists %))
   (io/as-file (str tmp-dir "/folder")) => #(.exists %))

  (fact
   "Emptying non-existing folders is a-o-k. It's, like, extra empty, dude."

   (empty-directory! (str tmp-dir "/missing"))))

(with-files [["/texts/banana.txt" "Banana"]
             ["/texts/apple.txt" "Apple"]
             ["/texts/fruit.txt" "Fruit"]
             ["/texts/irrelevant.md" "Left out"]
             ["/texts/.#emacs.txt" "Temp files are cool beans"]]

  (fact (slurp-directory (str tmp-dir "/texts") #"\.txt$")
        => {"/banana.txt" "Banana"
            "/apple.txt" "Apple"
            "/fruit.txt" "Fruit"}))

(with-files [["/texts/fruit/banana.txt" "Banana"]
             ["/texts/fruit/apple.txt" "Apple"]
             ["/texts/vegetables/cucumber.txt" "Cucumber"]]

  (fact (slurp-directory (str tmp-dir "/texts") #"\.txt$")
        => {"/fruit/banana.txt" "Banana"
            "/fruit/apple.txt" "Apple"
            "/vegetables/cucumber.txt" "Cucumber"}))

(fact "It merges pages from several sources"
      (merge-page-sources {:general-pages {"/people.html" "People"}
                           :article-pages {"/folks.html" "Folks"}})
      => {"/people.html" "People"
          "/folks.html" "Folks"})

(fact "Colliding urls are not tolerated when merging."

      (merge-page-sources {:general-pages {"/people.html" ""
                                           "/about.html" ""}
                           :article-pages {"/people.html" ""
                                           "/folks.html" ""}})
      => (throws Exception "URL conflicts between :article-pages and :general-pages: #{\"/people.html\"}")

      (merge-page-sources {:person-pages {"/magnars.html" ""
                                          "/finnjoh.html" ""}
                           :article-pages {"/magnars.html" ""
                                           "/finnjoh.html" ""}
                           :general-pages {"/people.html" ""}})
      => (throws Exception "URL conflicts between :article-pages and :person-pages: #{\"/magnars.html\" \"/finnjoh.html\"}"))

(fact "It detects clashes for non-normalized URLs too."

      (merge-page-sources {:general-pages {"/" ""}
                           :article-pages {"/index.html" ""}})
      => (throws Exception "URL conflicts between :article-pages and :general-pages: #{\"/index.html\"}"))

(with-files [["/valid.edn" "{:valid \"EDN\"}"]
             ["/invalid.edn" "{:syntactically \"Broken\""]
             ["/multiple-root-forms.edn" "{:first \"Form\"} #{:second}"]]

  (fact "It loads edn files"

        (load-edn (str public-dir "/valid.edn")) => {:valid "EDN"}
        (load-edn (str public-dir "/invalid.edn")) => (throws Exception)
        (load-edn (str public-dir "/multiple-root-forms.edn")) => (throws Exception (str "File " public-dir "/multiple-root-forms.edn should contain only a single root form, but had 2 forms."))
        (load-edn (str public-dir "/non-existent.edn")) => (throws Exception)))
