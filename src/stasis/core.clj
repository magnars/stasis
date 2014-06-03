(ns stasis.core
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [stasis.class-path :refer [file-paths-on-class-path]]
            [ring.util.codec :refer [url-decode]])
  (:import [java.io File]
           [java.util.regex Pattern]))

(defn- normalize-uri [^String uri]
  (let [decoded-uri (url-decode uri)]
    (cond
     (.endsWith decoded-uri ".html") decoded-uri
     (.endsWith decoded-uri "/") (str decoded-uri "index.html")
     (re-find #"/[^./]+$" decoded-uri) (str decoded-uri "/index.html")
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

(defn serve-pages [get-pages & [options]]
  (let [get-pages (if (map? get-pages) ;; didn't pass a fn, just a map of pages
                    (fn [] get-pages)
                    get-pages)]
    (ensure-valid-paths (keys (get-pages)))
    (fn [request]
      (if-not (statically-servable-uri? (:uri request))
        {:status 301, :headers {"Location" (str (:uri request) "/")}}
        (let [request (update-in request [:uri] normalize-uri)
              pages (normalize-page-uris (get-pages))]
          (ensure-valid-paths (keys pages))
          (if-let [get-page (pages (:uri request))]
            (serve-page get-page (merge request options))
            not-found))))))

(defn- create-folders [path]
  (.mkdirs (.getParentFile (io/file path))))

(defn export-pages [pages target-dir & [options]]
  (ensure-valid-paths (keys pages))
  (let [target-dir (when target-dir (str/replace target-dir #"/$" ""))]
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

(defn slurp-directory [dir regexp]
  (let [dir (io/as-file dir)
        path-len (count (get-path dir))
        path-from-dir #(subs (get-path %) path-len)]
    (->> (file-seq dir)
         (remove emacs-file?)
         (filter #(re-find regexp (path-from-dir %)))
         (map (juxt path-from-dir slurp))
         (into {}))))

(defn- chop-up-to [^String prefix ^String s]
  (subs s (+ (.indexOf s prefix)
             (count prefix))))

(defn slurp-resources [dir regexp]
  (->> (file-paths-on-class-path)
       (filter (fn [^String s] (.contains s (str dir "/"))))
       (filter #(re-find regexp %))
       (remove #(emacs-file-artefact? (chop-up-to dir %)))
       (map (juxt #(chop-up-to dir %)
                  #(slurp (io/resource %))))
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

(defn merge-page-sources [sources]
  (->> sources guard-against-collisions vals (apply merge)))
