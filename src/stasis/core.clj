(ns stasis.core
  (:require [clojure.java.io :as io]))

(defn- normalize-uri [#^String uri]
  (cond
   (.endsWith uri ".html") uri
   (.endsWith uri "/") (str uri "index.html")
   :else (str uri "/index.html")))

(defn- normalize-page-uris [pages]
  (zipmap (map normalize-uri (keys pages))
          (vals pages)))

(defn- serve-page [page request]
  {:status 200
   :body (page request)
   :headers {"Content-Type" "text/html"}})

(def not-found
  {:status 404
   :body "<h1>Page not found</h1>"
   :headers {"Content-Type" "text/html"}})

(defn serve-pages [pages]
  (let [pages (normalize-page-uris pages)]
    (fn [request]
      (let [uri (normalize-uri (:uri request))]
        (if-let [page (pages uri)]
          (serve-page page (assoc request :uri uri))
          not-found)))))

(defn- create-folders [path]
  (.mkdirs (.getParentFile (io/file path))))

(defn export-pages [pages target-dir options]
  (doseq [[uri get-page] pages]
    (let [uri (normalize-uri uri)
          path (str target-dir uri)]
      (create-folders path)
      (spit path (get-page (assoc options :uri uri))))))

(defn- delete-file-recursively [f]
  (let [f (io/file f)]
    (if (.isDirectory f)
      (doseq [child (.listFiles f)]
        (delete-file-recursively child)))
    (io/delete-file f)))

(defn delete-directory! [f]
  (if (.isDirectory (io/file f))
    (delete-file-recursively f)
    (throw (Exception. (str f " is not a directory.")))))
