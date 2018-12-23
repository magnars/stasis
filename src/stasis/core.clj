(ns stasis.core
  (:require [clansi.core :as ansi]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [ring.util.codec :refer [url-decode]]
            [stasis.class-path :refer [file-paths-on-class-path]])
  (:import [java.io File]
           [java.util.regex Pattern]))

(defn- normalize-uri [^String uri]
  (let [decoded-uri (url-decode uri)]
    (cond
     (.endsWith decoded-uri ".html") decoded-uri
     (.endsWith decoded-uri "/") (str decoded-uri "index.html")
     :else decoded-uri)))

(defn- statically-servable-uri? [^String uri]
  (or (.endsWith uri "/")
      (not (re-find #"/[^./]+$" uri))))

(defn- normalize-page-uris [pages]
  (zipmap (map normalize-uri (keys pages))
          (vals pages)))

(def fsep (java.io.File/separator))

(def fsep-regex (java.util.regex.Pattern/quote fsep))

(defn- normalize-path [^String path]
  (if (= fsep "/")
    path
    (.replaceAll path fsep-regex "/")))

(defn- get-path [#^File path]
  (normalize-path (.getPath path)))

(defn- assoc-if [m assoc? k v]
  (if assoc? (assoc m k v) m))

(defn- realize-page [pageish request] ;; a pageish is either a page, or a function that creates a page
  (if (or (string? pageish) ;; a page is either a string (of html) or a map
          (map? pageish))
    pageish
    (pageish request)))

(defn- serve-page [page uri]
  (-> {:status 200
       :body (if (map? page) (:contents page) page)}
      (assoc-if (.endsWith uri ".html") :headers {"Content-Type" "text/html"})))

(def not-found
  {:status 404
   :body "<h1>Page not found</h1>"
   :headers {"Content-Type" "text/html"}})

(defn- ensure-absolute-paths [paths]
  "Validates that the paths (the keys) of the pages are absolute paths,
   so that ring can serve them properly."
  (let [errors (->> paths
                    (remove #(re-find #"^/" %)))]
    (when (seq errors)
      (throw (ex-info (str "The following pages must have absolute paths: "
                           (pr-str errors))
                      {:errors errors})))))

(defn- ensure-statically-servable-paths [paths]
  "Validates that the paths (the keys) of the pages either end in a file extension or a slash,
   so that they can be served properly as static files."
  (let [errors (->> paths
                    (remove statically-servable-uri?))]
    (when (seq errors)
      (throw (ex-info (str "The following page paths must end in a slash: "
                           (pr-str errors))
                      {:errors errors})))))

(defn- ensure-valid-paths [paths]
  (ensure-absolute-paths paths)
  (ensure-statically-servable-paths paths))

(defn- try-serving-dependent-page [request pages known-dependent-pages fallback]
  (if-let [host-page-uri (@known-dependent-pages (:uri request))] ;; known-dependent-pages is a an atom of a map from (dependent) uri to host-page-uri
    (if-let [host-pageish (pages host-page-uri)]
      (let [host-page (realize-page host-pageish request)]
        (if-let [dependent-page (and (map? host-page)
                                     (get-in host-page [:dependent-pages (:uri request)]))]
          (serve-page dependent-page (:uri request))
          (fallback)))
      (fallback))
    (fallback)))

(defn- populate-known-dependent-pages [uri page known-dependent-pages]
  (when (and (map? page) (:dependent-pages page))
    (swap! known-dependent-pages merge (zipmap (keys (:dependent-pages page))
                                               (repeat uri)))))

(defn- serve-after-finding-all-dependent-pages [request pages known-dependent-pages]
  (doseq [[uri pageish] pages]
    (populate-known-dependent-pages uri (realize-page pageish (assoc request :uri uri)) known-dependent-pages))
  (try-serving-dependent-page request pages known-dependent-pages (fn [] not-found)))

(defn serve-pages [get-pages & [options]]
  (let [get-pages (if (map? get-pages) ;; didn't pass a fn, just a map of pages
                    (fn [] get-pages)
                    get-pages)
        known-dependent-pages (atom {})] ;; map from (dependent) uri to host-page-uri
    (fn [request]
      (if-not (statically-servable-uri? (:uri request))
        {:status 301, :headers {"Location" (str (:uri request) "/")}}
        (let [request (-> request
                          (update-in [:uri] normalize-uri)
                          (merge options))
              pages (normalize-page-uris (get-pages))]
          (ensure-valid-paths (keys pages))
          (if-let [pageish (pages (:uri request))] ;; a pageish is either a page, or a function that creates a page
            (let [page (realize-page pageish request)]
              (populate-known-dependent-pages (:uri request) page known-dependent-pages)
              (serve-page page (:uri request)))
            (try-serving-dependent-page request pages known-dependent-pages
                                        (partial serve-after-finding-all-dependent-pages request pages known-dependent-pages))))))))

(defn- create-folders [path]
  (.mkdirs (.getParentFile (io/file path))))

(defn export-page [uri pageish target-dir options]
  (let [uri (normalize-uri uri)
        path (str target-dir uri)
        page (realize-page pageish (assoc options :uri uri))]
    (create-folders path)
    (if (string? page)
      (spit path page)
      (do
        (spit path (:contents page))
        (doseq [[uri page] (:dependent-pages page)]
          (export-page uri page target-dir options))))))

(defn export-pages [pages target-dir & [options]]
  (ensure-valid-paths (keys pages))
  (let [target-dir (when target-dir (str/replace target-dir #"/$" ""))]
    (doseq [[uri pageish] pages]
      (export-page uri pageish target-dir options))))

(defn- delete-file-recursively [f]
  (if (.isDirectory f)
    (doseq [child (.listFiles f)]
      (delete-file-recursively child)))
  (io/delete-file f))

(defn empty-directory! [f]
  (let [f (io/file f)]
    (if (.isDirectory f)
      (doseq [child (.listFiles f)]
        (delete-file-recursively child))
      (if (.exists f)
        (throw (Exception. (str (get-path f) " is not a directory.")))))))

(defn- just-the-filename [^String path]
  (last (str/split path #"/")))

(defn- emacs-file-artefact? [^String path]
  (let [filename (just-the-filename path)]
    (or (.startsWith filename ".#")
        (and (.startsWith filename "#")
             (.endsWith filename "#")))))

(defn- emacs-file? [^File file]
  (-> file get-path emacs-file-artefact?))

(defn slurp-directory
  "Returns a map of paths to file contents in the `dir`. `dir` should be
  accessible via `clojure.java.io/as-file`. `regexp` will be used to filter the
  files. `opts` are passed to `slurp` to enable specification of encoding and
  buffer-size etc."
  [dir regexp & opts]
  (let [dir (io/as-file dir)
        path-len (count (get-path dir))
        path-from-dir #(subs (get-path %) path-len)]
    (->> (file-seq dir)
         (remove emacs-file?)
         (filter #(re-find regexp (path-from-dir %)))
         (map (juxt path-from-dir #(apply slurp % opts)))
         (into {}))))

(defn- chop-up-to [^String prefix ^String s]
  (subs s (+ (.indexOf s prefix)
             (count prefix))))

(defn slurp-resources
  "Returns a map of paths to file contents in the `dir` found on the resource
  path. `regexp` will be used to filter the files. `opts` are passed to `slurp`
  to enable specification of encoding and buffer-size etc."
  [dir regexp & opts]
  (->> (file-paths-on-class-path)
       (filter (fn [^String s] (.contains s (str dir "/"))))
       (filter #(re-find regexp %))
       (remove #(emacs-file-artefact? (chop-up-to dir %)))
       (map (juxt #(chop-up-to dir %)
                  #(apply slurp (io/resource %) opts)))
       (into {})))

(defn- guard-against-collisions [pages]
  (doseq [k1 (keys pages)
          k2 (keys pages)]
    (when-not (= k1 k2)
      (let [collisions (set/intersection (set (map normalize-uri (keys (k1 pages))))
                                         (set (map normalize-uri (keys (k2 pages)))))]
        (when-not (empty? collisions)
          (throw (Exception. (str "URL conflicts between " k1 " and " k2 ": " collisions)))))))
  pages)

(defn merge-page-sources
  "Merges collections of pages ensuring every path only occurs once across all
  collections.

  Takes a map of collection name to page collection, and returns a map of path
  to content. The collection names are only used for error reporting.

  For example,

      (merge-page-sources
       {:person-pages (create-person-pages)
        :article-pages (create-article-pages)
        :general-pages (create-general-pages)})"
  [sources]
  (->> sources guard-against-collisions vals (apply merge)))

(defn- is-changed? [old new path]
  (not= (get old path)
        (get new path)))

(defn diff-maps [old new]
  (let [added (set/difference (set (keys new)) (set (keys old)))
        removed (set/difference (set (keys old)) (set (keys new)))
        remaining (set/difference (set (keys old)) added removed)
        is-changed? (partial is-changed? old new)]
    {:added added
     :removed removed
     :changed (set (filter is-changed? remaining))
     :unchanged (set (remove is-changed? remaining))}))

(defn- print-heading [s entries color]
  (let [num (count entries)]
    (println (ansi/style (format s num (if (= 1 num) "file" "files")) color))))

(defn report-differences [old new]
  (let [{:keys [added removed changed unchanged]} (diff-maps old new)]
    (if (and (empty? removed)
             (empty? changed)
             (empty? unchanged))
      (print-heading "- First export! Created %s %s." added :green)
      (do
        (when (seq unchanged)
          (print-heading "- %s unchanged %s." unchanged :cyan))
        (when (seq changed)
          (print-heading "- %s changed %s:" changed :yellow)
          (doseq [path (sort changed)] (println "    -" path)))
        (when (seq removed)
          (print-heading "- %s removed %s:" removed :red)
          (doseq [path (sort removed)] (println "    -" path)))
        (when (seq added)
          (print-heading "- %s added %s:" added :green)
          (doseq [path (sort added)] (println "    -" path)))))))
