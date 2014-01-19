(ns stasis.core
  (:require [clojure.java.io :as io]
            [clojure.set :as set]))

(defn- normalize-uri [#^String uri]
  (cond
   (.endsWith uri ".html") uri
   (.endsWith uri "/") (str uri "index.html")
   (re-find #"/[^./]+$" uri) (str uri "/index.html")
   :else uri))

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
  (doseq [[uri get-page] pages]
    (let [uri (normalize-uri uri)
          path (str target-dir uri)]
      (create-folders path)
      (->> (if (string? get-page)
             get-page ;; didn't pass a fn, just the page contents
             (get-page (assoc options :uri uri)))
           (spit path)))))

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

(defn slurp-directory [dir regexp]
  (let [dir (io/as-file dir)
        path-from-dir #(subs (.getPath %) (count (.getPath dir)))]
    (->> (file-seq dir)
         (filter #(re-find regexp (path-from-dir %)))
         (map (juxt path-from-dir slurp))
         (into {}))))

(defn- guard-against-collisions [pages]
  (doseq [k1 (keys pages)
          k2 (keys pages)]
    (when-not (= k1 k2)
      (let [collisions (set/intersection (set (keys (k1 pages)))
                                         (set (keys (k2 pages))))]
        (when-not (empty? collisions)
          (throw (Exception. (str "URL conflicts between " k1 " and " k2 ": " collisions)))))))
  pages)

(defn merge-page-sources [sources]
  (->> sources guard-against-collisions vals (apply merge)))
