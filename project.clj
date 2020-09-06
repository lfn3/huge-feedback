(defproject huge-feedback "0.2.0"
  :dependencies [;clj
                 [org.clojure/clojure "1.10.0"]
                 [cheshire "5.8.1"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [mount "0.1.16"]

                 ;cljc
                 [bidi "2.1.5"]
                 [cljs-ajax "0.8.0"]

                 ;cljs
                 [org.clojure/clojurescript "1.10.339"]
                 [re-frame "1.0.0"]]

  :source-paths ["src/main/clj" "src/main/cljc" "src/main/cljs"]
  :test-paths ["src/test/clj"]

  :cljsbuild {:builds [{:source-paths ["src/main/cljs" "src/main/cljc"]
                        :compiler     {:output-to "target/resources/public/cljs-out/dev-main.js"
                                       :output-dir "target/resources/public/cljs-out"
                                       :asset-path "cljs-out"

                                       :main         huge-feedback.core}}]}
  ;:hooks [leiningen.cljsbuild]

  :resource-paths ["src/main/resources" "target/resources"]
  :clean-targets ^{:protect false} [:compile-path :target-path]

  :profiles {:uberjar {:aot :all
                       :prep-tasks ["compile" ["cljsbuild" "once"]]}
             :dev {:source-paths ["src/dev/clj"]
                   :dependencies [[lein-cljsbuild "1.1.8"]
                                  [nrepl "0.6.0"]
                                  [com.bhauman/figwheel-main "0.2.11"]
                                  [cider/piggieback "0.4.0"]
                                  [day8.re-frame/re-frame-10x "0.7.0"]
                                  [binaryage/devtools "1.0.2"]
                                  [org.eclipse.jetty.websocket/websocket-server "9.4.12.v20180830"]
                                  [org.eclipse.jetty.websocket/websocket-servlet "9.4.12.v20180830"]]}}

  :main huge-feedback.core

  :plugins [[lein-cljsbuild "1.1.8"]])
