(defproject stasis "0.2.0"
  :description "A library of tools for creating static websites."
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :profiles {:dev {:dependencies [[midje "1.6.0"]
                                  [print-foo "0.4.2"]
                                  [test-with-files "0.1.0"]]
                   :plugins [[lein-midje "3.1.3"]]
                   :source-paths ["dev"]}})
