(ns stasis.core-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [digest]
            [stasis.core :as sut]
            [test-with-files.core :refer [public-dir tmp-dir with-files with-tmp-dir]]))

(defn noop [_] nil)

(deftest serve-pages-test
  (testing "Stasis creates a Ring handler to serve your pages."
    (is (= (let [app (sut/serve-pages {"/page.html" "The page contents"})]
             (app {:uri "/page.html"}))
           {:status 200
            :body "The page contents"
            :headers {"Content-Type" "text/html"}})))

  (testing "Serves 404 for missing page"
    (is (= (let [app (sut/serve-pages {"/page.html" "The page contents"})]
             (app {:uri "/missing.html"}))
           {:status 404
            :body "<h1>Page not found</h1>"
            :headers {"Content-Type" "text/html"}})))

  (testing "You can pass in a `get-pages` function too, if you need to determine
            the set of pages dynamically and want them to be properly live."
    (is (= (let [get-pages (fn [] {"/page.html" "The page contents"})
                 app (sut/serve-pages get-pages)]

             (app {:uri "/page.html"}))
           {:status 200
            :body "The page contents"
            :headers {"Content-Type" "text/html"}})))

  (testing "A page can be a function too, which is passed its context with :uri in."
    (is (= (let [app (sut/serve-pages {"/page.html" (fn [ctx] (str "I'm serving " (:uri ctx)))})]
             (:body (app {:uri "/page.html"})))
           "I'm serving /page.html")))

  (testing "When the :stasis/ignore-nil-pages? value in the options map is false,
            requests for pages with nil values should throw an exception"
    (let [app1 (sut/serve-pages {"/page.html" nil})]
      (is (thrown-with-msg? Exception #"Page value is unexpectedly nil" (app1 {:uri "/page.html"}))))
    (let [app2 (sut/serve-pages {"/page.html" noop})]
      (is (thrown-with-msg? Exception #"Page value is unexpectedly nil" (app2 {:uri "/page.html"})))))

  (testing "When the :stasis/ignore-nil-pages? value in the options map is true,
            requests for pages with nil values will be ignored and the response
            status will be 404"
    (let [app1 (sut/serve-pages {"/page.html" nil} {:stasis/ignore-nil-pages? true})]
      (is (= (app1 {:uri "/page.html"})
             {:status 404
              :body "<h1>Page not found</h1>"
              :headers {"Content-Type" "text/html"}})))
    (let [app2 (sut/serve-pages {"/page.html" noop} {:stasis/ignore-nil-pages? true})]
      (is (= (app2 {:uri "/page.html"})
             {:status 404
              :body "<h1>Page not found</h1>"
              :headers {"Content-Type" "text/html"}}))))

  (testing "If you use paths without .html, it serves them as directories with
            an index.html."
    (let [app (sut/serve-pages {"/page/" (fn [ctx] (str "I'm serving " (:uri ctx)))})]
      (is (= (:body (app {:uri "/page/index.html"})) "I'm serving /page/index.html"))
      (is (= (:body (app {:uri "/page/"})) "I'm serving /page/index.html"))
      (is (= (app {:uri "/page"}) {:status 301, :headers {"Location" "/page/"}}))))

  (testing "Paths without .html or an ending slash is prohibited, because such URLs
            slow your site down with needless redirects."
    (is (thrown-with-msg? Exception #"The following page paths must end in a slash: \(\"/not-ok\"\)"
                          ((sut/serve-pages {"/ok.html" noop
                                             "/ok/" noop
                                             "/not-ok" noop})
                           {:uri "/"}))))

  (testing "It forces pages paths to be absolute paths."
    (is (thrown-with-msg?
         Exception #"The following pages must have absolute paths: \(\"foo.html\"\)"
         ((sut/serve-pages {"foo.html" "bar"}) {:uri "/"})))
    (is (thrown-with-msg?
         Exception #"The following pages must have absolute paths: \(\"foo.html\"\)"
         (sut/export-pages {"foo.html" "bar"} nil))))

  (testing "If you use paths with strange characters, like { and }, it transparently
            decodes incoming URLs"
    (is (= (let [app (sut/serve-pages {"/page/{thing-a-majig}/" (fn [ctx] (str "I'm serving " (:uri ctx)))})]
             (:body (app {:uri "/page/%7Bthing-a-majig%7D/"})))
           "I'm serving /page/{thing-a-majig}/index.html")))

  (testing "You can pass along config options to serve-pages that will be
            included on each request."
    (is (= (let [app (sut/serve-pages {"/page/" (fn [ctx] (str "Config: " (:config ctx)))}
                                      {:config "Passed!"})]
             (:body (app {:uri "/page/"})))
           "Config: Passed!")))

  (testing "You can serve other types of assets too."
    (let [pages {"/page-details.js"
                 (fn [ctx] (str "alert('" (:uri ctx) "');"))

                 ;;See <https://en.wikipedia.org/wiki/Standard_test_image>
                 "/jellybeans.png"
                 (fn [_] (io/file "dev-resources" "4.1.07.png"))}
          app (sut/serve-pages pages)]

      (is (= (app {:uri "/page-details.js"})
             {:status 200
              :body "alert('/page-details.js');"}))

      (is (= (-> (app {:uri "/jellybeans.png"}) :body .getName) "4.1.07.png"))

      (is (= (with-tmp-dir
               (sut/export-pages pages tmp-dir)

               ;; Calculated using:
               ;; $ md5sum dev-resources/4.1.07.png
               ;; d84246a8ba02c2e3ee87b07813596d68  dev-resources/4.1.07.png
               (->> "jellybeans.png" (io/file tmp-dir) digest/md5))
             "d84246a8ba02c2e3ee87b07813596d68"))))

  (testing "When creating a page, you might realize that other dependent pages are
            needed as well. Stasis helps you out by allowing a page to be a map of
            {:contents, :dependent-pages}."
    (let [pages {"/" (fn [_] {:contents "Hello"
                              :dependent-pages {"/dependent.html" "Hi there"}})
                 "/other.html" {:contents "Yo"
                                :dependent-pages {"/other-dependent.html" "Wazzup"}}}
          app (sut/serve-pages pages)]

      (is (= (app {:uri "/"})
             {:status 200
              :body "Hello"
              :headers {"Content-Type" "text/html"}}))

      (is (= (app {:uri "/dependent.html"})
             {:status 200
              :body "Hi there"
              :headers {"Content-Type" "text/html"}}))

      (is (= (app {:uri "/other-dependent.html"})
             {:status 200
              :body "Wazzup"
              :headers {"Content-Type" "text/html"}}))

      (with-tmp-dir
        (sut/export-pages pages tmp-dir)

        (is (= (slurp (str tmp-dir "/index.html")) "Hello"))
        (is (= (slurp (str tmp-dir "/dependent.html")) "Hi there"))
        (is (= (slurp (str tmp-dir "/other-dependent.html")) "Wazzup")))))

  (testing "Stasis exports pages to your directory of choice."
    (is (= (with-tmp-dir
             (sut/export-pages {"/page/index.html" "The contents"}
                               tmp-dir)
             (slurp (str tmp-dir "/page/index.html")))
           "The contents")))

  (testing "Stasis adds the :uri to the context for exported pages."
    (is (= (with-tmp-dir
             (sut/export-pages {"/page/" (fn [ctx] (str "I'm serving " (:uri ctx)))}
                               tmp-dir)
             (slurp (str tmp-dir "/page/index.html")))
           "I'm serving /page/index.html")))

  (testing "Stasis will throw an exception when exporting a page with a nil value"
    (is (thrown-with-msg?
         Exception #"Page value is unexpectedly nil"
         (with-tmp-dir (sut/export-pages {"/page/" nil} tmp-dir))))

    (is (thrown-with-msg?
         Exception #"Page value is unexpectedly nil"
         (with-tmp-dir (sut/export-pages {"/page/" noop} tmp-dir)))))

  (testing "Stasis will not export a page with a nil value when :stasis/ignore-nil-pages? is true in the options map"
    (with-tmp-dir
      (is (nil? (sut/export-pages {"/page/" nil} tmp-dir {:stasis/ignore-nil-pages? true})))
      (is (not (.exists (io/file tmp-dir "page/index.html")))))

    (with-tmp-dir
      (is (nil? (sut/export-pages {"/page/" noop} tmp-dir {:stasis/ignore-nil-pages? true})))
      (is (not (.exists (io/file tmp-dir "page/index.html"))))))

  (testing "You can add more information to the context if you want. Like
            configuration options, or optimus assets."
    (is (= (with-tmp-dir
             (sut/export-pages {"/page/" (fn [ctx] (str "I got " (:conf ctx)))}
                               tmp-dir {:conf "served"})
             (slurp (str tmp-dir "/page/index.html")))
           "I got served")))

  (testing "You can't accidentaly empty a file with empty-directory!"
    (with-tmp-dir
      (sut/export-pages {"/folder/page.html" (fn [ctx] "Contents")} tmp-dir {})
      (is (thrown-with-msg?
           Exception (re-pattern (str tmp-dir "/folder/page.html is not a directory."))
           (sut/empty-directory! (str tmp-dir "/folder/page.html"))))))

  (testing "But it's really easy emptying an entire folder of files. Be careful."
    (with-tmp-dir
      (sut/export-pages {"/folder/page.html" (fn [ctx] "Contents")} tmp-dir {})
      (sut/empty-directory! (str tmp-dir "/folder"))

      (is (not (.exists (io/as-file (str tmp-dir "/folder/page.html")))))
      (is (.exists (io/as-file (str tmp-dir "/folder"))))))

  (testing "Emptying non-existing folders is a-o-k. It's, like, extra empty, dude."
    (is (nil? (with-tmp-dir
                (sut/export-pages {"/folder/page.html" (fn [ctx] "Contents")} tmp-dir {})
                (sut/empty-directory! (str tmp-dir "/missing"))))))

  (testing "Slurps text files"
    (is (= (with-files [["/texts/banana.txt" "Banana"]
                        ["/texts/apple.txt" "Apple"]
                        ["/texts/fruit.txt" "Fruit"]
                        ["/texts/irrelevant.md" "Left out"]
                        ["/texts/.#emacs.txt" "Temp files are cool beans"]]
             (sut/slurp-directory (str tmp-dir "/texts") #"\.txt$"))
           {"/banana.txt" "Banana"
            "/apple.txt" "Apple"
            "/fruit.txt" "Fruit"})))

  (testing "Slurps public dir"
    (is (= (with-files [["/texts/banana.txt" "Banana"]
                        ["/texts/apple.txt" "Apple"]
                        ["/texts/fruit.txt" "Fruit"]
                        ["/texts/irrelevant.md" "Left out"]
                        ["/texts/.#emacs.txt" "Temp files are cool beans"]]


             (sut/slurp-resources (str public-dir "/texts") #"\.txt$"))
           {"/banana.txt" "Banana"
            "/apple.txt" "Apple"
            "/fruit.txt" "Fruit"})))

  (testing "Slurps directory"
    (is (= (with-files [["/texts/fruit/banana.txt" "Banana"]
                        ["/texts/fruit/apple.txt" "Apple"]
                        ["/texts/vegetables/cucumber.txt" "Cucumber"]]
             (sut/slurp-directory (str tmp-dir "/texts") #"\.txt$"))
           {"/fruit/banana.txt" "Banana"
            "/fruit/apple.txt" "Apple"
            "/vegetables/cucumber.txt" "Cucumber"})))

  (testing "It merges pages from several sources"
    (is (= (sut/merge-page-sources {:general-pages {"/people.html" "People"}
                                    :article-pages {"/folks.html" "Folks"}})
           {"/people.html" "People"
            "/folks.html" "Folks"})))

  (testing "Colliding urls are not tolerated when merging."
    (is (thrown-with-msg?
         Exception #"URL conflicts between :article-pages and :general-pages: #\{\"/people.html\"\}"
         (sut/merge-page-sources {:general-pages {"/people.html" ""
                                                  "/about.html" ""}
                                  :article-pages {"/people.html" ""
                                                  "/folks.html" ""}})))

    (is (thrown-with-msg?
         Exception #"URL conflicts between :article-pages and :person-pages: #\{\"/magnars.html\" \"/finnjoh.html\"\}"
         (sut/merge-page-sources {:person-pages {"/magnars.html" ""
                                                 "/finnjoh.html" ""}
                                  :article-pages {"/magnars.html" ""
                                                  "/finnjoh.html" ""}
                                  :general-pages {"/people.html" ""}}))))

  (testing "It detects clashes for non-normalized URLs too."
    (is (thrown-with-msg?
         Exception #"URL conflicts between :article-pages and :general-pages: #\{\"/index.html\"\}"
         (sut/merge-page-sources {:general-pages {"/" ""}
                                  :article-pages {"/index.html" ""}}))))

  (testing "It finds differences between maps (used by report-differences)."
    (is (= (sut/diff-maps {"/texts/fruit/banana.txt" "Banana"
                           "/texts/fruit/apple.txt" "Apple"
                           "/texts/vegetables/cucumber.txt" "Cucumber"}
                          {"/texts/fruit/banana.txt" "Banana"
                           "/texts/fruit/apple.txt" "Apple!"
                           "/texts/vegetables/tomato.txt" "Tomato"})
           {:removed #{"/texts/vegetables/cucumber.txt"}
            :added #{"/texts/vegetables/tomato.txt"}
            :changed #{"/texts/fruit/apple.txt"}
            :unchanged #{"/texts/fruit/banana.txt"}}))))
