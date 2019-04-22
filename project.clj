(defproject huge-feedback "0.1.0-SNAPSHOT"
  :dependencies [;clj
                 [org.clojure/clojure "1.10.0"]
                 [cheshire "5.8.1"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [bidi "2.1.4"]
                 [mount "0.1.16"]
                 [clj-http "3.9.1"]

                 ;cljc
                 [bidi "2.1.5"]
                 [cljs-ajax "0.8.0"]

                 ;cljs
                 [org.clojure/clojurescript "1.10.339"]
                 [re-frame "0.10.6"]

                 ;dev
                 [com.bhauman/figwheel-main "0.1.9"]
                 [day8.re-frame/re-frame-10x "0.3.3"]
                 [org.eclipse.jetty.websocket/websocket-server "9.4.12.v20180830"]
                 [org.eclipse.jetty.websocket/websocket-servlet "9.4.12.v20180830"]]

  :source-paths ["src/main/clj" "src/main/cljc" "src/main/cljs"]
  :test-paths ["src/test/clj"]

  :cljsbuild {:builds [{:source-paths ["src/main/cljs" "src/main/cljc"]
                        :compiler     {:output-to "target/public/cljs-out/dev-main.js"
                                       :output-dir "target/public/cljs-out"
                                       :asset-path "cljs-out"

                                       :main         huge-feedback.core}}]}

  :resource-paths ["target" "src/main/resources"]

  :plugins [[lein-cljsbuild "1.1.7"]])
