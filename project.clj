(defproject
  reformer
  "0.1.0-SNAPSHOT"
  :description
  "FIXME: write description"
  :ring
  {:handler reformer.handler/app,
   :init reformer.handler/init,
   :destroy reformer.handler/destroy}
  :cljsbuild
  {:builds
   [{:source-paths ["src-cljs"],
     :id "dev",
     :compiler
     {:output-dir "resources/public/js/",
      :optimizations :none,
      :output-to "resources/public/js/app.js",
      :source-map true,
      :pretty-print true}}
    {:source-paths ["src-cljs"],
     :id "release",
     :compiler
     {:closure-warnings {:non-standard-jsdoc :off},
      :optimizations :advanced,
      :output-to "resources/public/js/app.js",
      :output-wrapper false,
      :pretty-print false}}]}
  :plugins
  [[lein-ring "0.8.10"]
   [lein-environ "0.5.0"]
   [lein-ancient "0.5.5"]
   [lein-cljsbuild "1.0.3"]]
  :url
  "http://example.com/FIXME"
  :profiles
  {:uberjar {:aot :all},
   :production
   {:ring
    {:open-browser? false, :stacktraces? false, :auto-reload? false}},
   :dev
   {:dependencies
    [[ring-mock "0.1.5"]
     [ring/ring-devel "1.3.1"]
     [pjstadig/humane-test-output "0.6.0"]],
    :injections
    [(require 'pjstadig.humane-test-output)
     (pjstadig.humane-test-output/activate!)],
    :env {:dev true}}}
  :main
  reformer.core
  :jvm-opts
  ["-server"]
  :dependencies
  [[markdown-clj "0.9.53"]
   [lib-noir "0.8.9"]
   [http-kit "2.1.18"]
   [reagent "0.4.2"]
   [formative  "0.8.8"]
   [com.datomic/datomic-free "0.9.4899"]
   [datomic-schema "1.1.0"]
   [com.stuartsierra/component "0.2.2"]
   [json-html "0.2.2"]
   [aprint "0.1.0"]
   [org.clojure/core.match "0.2.2"]
   [org.clojure/core.async "0.1.346.0-17112a-alpha"]
   [reagent-forms  "0.2.3"]
   [prone "0.6.0"]
   [cljs-ajax "0.3.0"]
   [noir-exception "0.2.2"]
   [com.taoensso/timbre "3.3.1"]
   [com.taoensso/tower "3.0.1"]
   [secretary "1.2.0"]
   [selmer "0.7.1"]
   [org.clojure/clojure "1.6.0"]
   [org.clojure/clojurescript "0.0-2342"]
   [environ "1.0.0"]
   [ring-server "0.3.1"]
   [im.chit/cronj "1.4.2"]]
  :repl-options
  {:init-ns reformer.repl}
  :min-lein-version "2.0.0")
