(ns stasis.core
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [ring.util.codec :refer [url-decode]])
  (:import [java.io File]))

(defn- normalize-uri [#^String uri]
  (let [decoded-uri (url-decode uri)]
      (cond
       (.endsWith decoded-uri ".html") decoded-uri
       (.endsWith decoded-uri "/") (str decoded-uri "index.html")
       (re-find #"/[^./]+$" decoded-uri) (str decoded-uri "/index.html")
       :else decoded-uri)))

(defn- normalize-page-uris [pages]
  (zipmap (map normalize-uri (keys pages))
          (vals pages)))

(defn- assoc-if [m assoc? k v]
  (if assoc? (assoc m k v) m))

(defn- serve-page [get-page request]
  (-> {:status 200
       :body (if (string? get-page)
               get-page ;; didn't pass a fn, just the page contents
               (get-page request))}
      (assoc-if (.endsWith (:uri request) ".html") :headers {"Content-Type" "text/html"})))

(def not-found
  {:status 404
   :body "<h1>Page not found</h1>"
   :headers {"Content-Type" "text/html"}})

(defn serve-pages [get-pages & [options]]
  (let [get-pages (if (map? get-pages) ;; didn't pass a fn, just a map of pages
                    (fn [] get-pages)
                    get-pages)]
    (fn [request]
      (let [pages (normalize-page-uris (get-pages))
            request (update-in request [:uri] normalize-uri)]
        (if-let [get-page (pages (:uri request))]
          (serve-page get-page (merge request options))
          not-found)))))

(defn- create-folders [path]
  (.mkdirs (.getParentFile (io/file path))))

(defn export-pages [pages target-dir & [options]]
  (let [target-dir (if-not (re-find #"/$" target-dir)
                     (str target-dir "/")
                     target-dir)]
    (doseq [[uri get-page] pages]
      (let [uri (normalize-uri uri)
            path (str target-dir uri)]
        (create-folders path)
        (->> (if (string? get-page)
               get-page ;; didn't pass a fn, just the page contents
               (get-page (assoc options :uri uri)))
             (spit path))))))

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
        (throw (Exception. (str f " is not a directory.")))))))

(defn- just-the-filename [#^String path]
  (last (str/split path (re-pattern (java.io.File/separator)))))

(defn- emacs-file-artefact? [#^File path]
  (let [filename (just-the-filename (.getPath path))]
    (or (.startsWith filename ".#")
        (and (.startsWith filename "#")
             (.endsWith filename "#")))))

(defn slurp-directory [dir regexp]
  (let [dir (io/as-file dir)
        path-from-dir #(subs (.getPath %) (count (.getPath dir)))]
    (->> (file-seq dir)
         (remove emacs-file-artefact?)
         (filter #(re-find regexp (path-from-dir %)))
         (map (juxt path-from-dir slurp))
         (into {}))))

(defn- guard-against-collisions [pages]
  (doseq [k1 (keys pages)
          k2 (keys pages)]
    (when-not (= k1 k2)
      (let [collisions (set/intersection (set (map normalize-uri (keys (k1 pages))))
                                         (set (map normalize-uri (keys (k2 pages)))))]
        (when-not (empty? collisions)
          (throw (Exception. (str "URL conflicts between " k1 " and " k2 ": " collisions)))))))
  pages)

(defn merge-page-sources [sources]
  (->> sources guard-against-collisions vals (apply merge)))
