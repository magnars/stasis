(ns user
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.pprint :refer (pprint pp)]
            [clojure.repl :refer :all]
            [print.foo :refer :all]))

(defmacro dump-locals []
  `(clojure.pprint/pprint
    ~(into {} (map (fn [l] [`'~l l]) (reverse (keys &env))))))

