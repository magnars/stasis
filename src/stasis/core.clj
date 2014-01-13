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

(defn- serve-page [get-page request]
  {:status 200
   :body (-> request get-page :body)
   :headers {"Content-Type" "text/html"}})

(def not-found
  {:status 404
   :body "<h1>Page not found</h1>"
   :headers {"Content-Type" "text/html"}})

(defn serve-pages [pages]
  (let [pages (normalize-page-uris pages)]
    (fn [request]
      (let [uri (normalize-uri (:uri request))]
        (if-let [get-page (pages uri)]
          (serve-page get-page (assoc request :uri uri))
          not-found)))))

(defn- create-folders [path]
  (.mkdirs (.getParentFile (io/file path))))

(defn export-pages [pages target-dir options]
  (doseq [[uri get-page] pages]
    (let [uri (normalize-uri uri)
          path (str target-dir uri)]
      (create-folders path)
      (->> (get-page (assoc options :uri uri))
           :body
           (spit path)))))

(defn- delete-file-recursively [f]
  (if (.isDirectory f)
    (doseq [child (.listFiles f)]
      (delete-file-recursively child)))
  (io/delete-file f))

(defn delete-directory! [f]
  (let [file (io/file f)]
    (if (.isDirectory file)
      (delete-file-recursively file)
      (if (.exists file)
        (throw (Exception. (str f " is not a directory.")))))))
