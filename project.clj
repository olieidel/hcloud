(defproject io.eidel/hcloud "1.0.0"
  :author "Oliver Eidel <http://www.eidel.io>"
  :description "Clojure library for the Hetzner Cloud API."
  :url "https://github.com/olieidel/hcloud"
  :license {:name         "MIT License"
            :url          "https://opensource.org/licenses/MIT"
            :distribution :repo}

  :dependencies
  [[org.clojure/clojure "1.9.0"]
   [clj-http "3.9.1"]
   [cheshire "5.8.1"]
   [camel-snake-kebab "0.4.0"]]

  :profiles
  {:dev
   {:dependencies
    [[http-kit "2.3.0"]
     [org.flatland/ordered "1.5.6"]]}}

  :plugins
  [[lein-ancient "0.6.15"]
   [lein-codox "0.10.4"]]

  :codox {:metadata {:doc/format :markdown}})
