(ns stasis.core-test
  (:require [stasis.core :refer :all]
            [midje.sweet :refer :all]
            [clojure.java.io :as io]
            digest
            [test-with-files.core :refer [with-files with-tmp-dir tmp-dir public-dir]]))

(defn noop [_] nil)

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
 "When the :stasis/ignore-nil-pages? value in the options map is false, requests
 for pages with nil values should throw an exception"
 (let [app1 (serve-pages {"/page.html" nil})
       app2 (serve-pages {"/page.html" noop})]

   (app1 {:uri "/page.html"}) => (throws Exception "Page value is unexpectedly nil")

   (app2 {:uri "/page.html"}) => (throws Exception "Page value is unexpectedly nil")))

(fact
 "When the :stasis/ignore-nil-pages? value in the options map is true, requests
 for pages with nil values will be ignored and the response status will be 404"
 (let [app1 (serve-pages {"/page.html" nil} {:stasis/ignore-nil-pages? true})
       app2 (serve-pages {"/page.html" noop} {:stasis/ignore-nil-pages? true})]

   (app1 {:uri "/page.html"}) => {:status 404
                                  :body "<h1>Page not found</h1>"
                                  :headers {"Content-Type" "text/html"}}

   (app2 {:uri "/page.html"}) => {:status 404
                                  :body "<h1>Page not found</h1>"
                                  :headers {"Content-Type" "text/html"}}))

(fact
 "If you use paths without .html, it serves them as directories with
  an index.html."

 (let [app (serve-pages {"/page/" (fn [ctx] (str "I'm serving " (:uri ctx)))})]

   (:body (app {:uri "/page/index.html"})) => "I'm serving /page/index.html"
   (:body (app {:uri "/page/"})) => "I'm serving /page/index.html"
   (app {:uri "/page"}) => {:status 301, :headers {"Location" "/page/"}}))

(fact
 "Paths without .html or an ending slash is prohibited, because such URLs slow
  your site down with needless redirects."

 ((serve-pages {"/ok.html" noop
                "/ok/" noop
                "/not-ok" noop})
  {:uri "/"}) => (throws Exception "The following page paths must end in a slash: (\"/not-ok\")"))

(fact
 "It forces pages paths to be absolute paths."
 ((serve-pages {"foo.html" "bar"}) {:uri "/"}) => (throws Exception "The following pages must have absolute paths: (\"foo.html\")")
 (export-pages {"foo.html" "bar"} nil) => (throws Exception "The following pages must have absolute paths: (\"foo.html\")"))

(fact
 "If you use paths with strange characters, like { and }, it transparently
  decodes incoming URLs"

 (let [app (serve-pages {"/page/{thing-a-majig}/" (fn [ctx] (str "I'm serving " (:uri ctx)))})]

   (:body (app {:uri "/page/%7Bthing-a-majig%7D/"})) => "I'm serving /page/{thing-a-majig}/index.html"))

(fact
 "You can pass along config options to serve-pages that will be
  included on each request."

 (let [app (serve-pages {"/page/" (fn [ctx] (str "Config: " (:config ctx)))}
                        {:config "Passed!"})]

   (:body (app {:uri "/page/"})) => "Config: Passed!"))

(fact
 "You can serve other types of assets too."

 (let [pages {"/page-details.js"
              (fn [ctx] (str "alert('" (:uri ctx) "');"))

              ;;See <https://en.wikipedia.org/wiki/Standard_test_image>
              "/jellybeans.png"
              (fn [_] (io/file "dev-resources" "4.1.07.png"))}
       app (serve-pages pages)]

   (app {:uri "/page-details.js"}) => {:status 200
                                       :body "alert('/page-details.js');"}

   (-> (app {:uri "/jellybeans.png"}) :body .getName) => "4.1.07.png"

   (with-tmp-dir
     (export-pages pages tmp-dir)

     ;; Calculated using:
     ;; $ md5sum dev-resources/4.1.07.png
     ;; d84246a8ba02c2e3ee87b07813596d68  dev-resources/4.1.07.png
     (->> "jellybeans.png" (io/file tmp-dir) digest/md5)
     =>
     "d84246a8ba02c2e3ee87b07813596d68")))

(fact
 "When creating a page, you might realize that other dependent pages are needed as
  well. Stasis helps you out by allowing a page to be a map of {:contents, :dependent-pages}."

 (let [pages {"/" (fn [_] {:contents "Hello"
                           :dependent-pages {"/dependent.html" "Hi there"}})
              "/other.html" {:contents "Yo"
                             :dependent-pages {"/other-dependent.html" "Wazzup"}}}
       app (serve-pages pages)]

   (app {:uri "/"}) => {:status 200
                        :body "Hello"
                        :headers {"Content-Type" "text/html"}}

   (app {:uri "/dependent.html"}) => {:status 200
                                      :body "Hi there"
                                      :headers {"Content-Type" "text/html"}}

   (app {:uri "/other-dependent.html"}) => {:status 200
                                            :body "Wazzup"
                                            :headers {"Content-Type" "text/html"}}

   (with-tmp-dir
     (export-pages pages tmp-dir)

     (slurp (str tmp-dir "/index.html")) => "Hello"
     (slurp (str tmp-dir "/dependent.html")) => "Hi there"
     (slurp (str tmp-dir "/other-dependent.html")) => "Wazzup")))

(fact
 "Stasis exports pages to your directory of choice."

 (with-tmp-dir
   (export-pages {"/page/index.html" "The contents"}
                 tmp-dir)
   (slurp (str tmp-dir "/page/index.html")) => "The contents"))

(fact
 "Stasis adds the :uri to the context for exported pages."

 (with-tmp-dir
   (export-pages {"/page/" (fn [ctx] (str "I'm serving " (:uri ctx)))}
                 tmp-dir)
   (slurp (str tmp-dir "/page/index.html")) => "I'm serving /page/index.html"))

(fact
 "Stasis will throw an exception when exporting a page with a nil value"

 (with-tmp-dir
   (export-pages {"/page/" nil} tmp-dir) => (throws Exception "Page value is unexpectedly nil"))

 (with-tmp-dir
   (export-pages {"/page/" noop} tmp-dir) => (throws Exception "Page value is unexpectedly nil")))

(fact
 "Stasis will not export a page with a nil value when :stasis/ignore-nil-pages? is true in the options map"

 (with-tmp-dir
   (export-pages {"/page/" nil} tmp-dir {:stasis/ignore-nil-pages? true}) => nil
   (.exists (io/file tmp-dir "page/index.html")) => false)

 (with-tmp-dir
   (export-pages {"/page/" noop} tmp-dir {:stasis/ignore-nil-pages? true}) => nil
   (.exists (io/file tmp-dir "page/index.html")) => false))

(fact
 "You can add more information to the context if you want. Like
  configuration options, or optimus assets."

 (with-tmp-dir
   (export-pages {"/page/" (fn [ctx] (str "I got " (:conf ctx)))}
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
            "/fruit.txt" "Fruit"})

  (fact (slurp-resources (str public-dir "/texts") #"\.txt$")
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

(fact "It finds differences between maps (used by report-differences)."

      (diff-maps {"/texts/fruit/banana.txt" "Banana"
                  "/texts/fruit/apple.txt" "Apple"
                  "/texts/vegetables/cucumber.txt" "Cucumber"}
                 {"/texts/fruit/banana.txt" "Banana"
                  "/texts/fruit/apple.txt" "Apple!"
                  "/texts/vegetables/tomato.txt" "Tomato"})

      => {:removed #{"/texts/vegetables/cucumber.txt"}
          :added #{"/texts/vegetables/tomato.txt"}
          :changed #{"/texts/fruit/apple.txt"}
          :unchanged #{"/texts/fruit/banana.txt"}})
