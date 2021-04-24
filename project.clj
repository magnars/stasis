(defproject stasis "2.5.1"
  :description "A library of tools for creating static websites."
  :url "http://github.com/magnars/stasis"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[ring/ring-codec "1.1.2"]
                 [narkisr/clansi "1.2.0"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.10.1"]
                                  [digest "1.4.9"]
                                  [midje "1.9.9"]
                                  [test-with-files "0.1.1"]]
                   :plugins [[lein-ancient "0.6.15"]
                             [lein-midje "3.2.1"]]
                   :source-paths ["dev"]}})
