(ns huge-feedback.core
  (:require [bidi.ring]
            [mount.core :as mount]
            [ring.adapter.jetty :as jetty]
            [figwheel.main.api]
            [huge-feedback.routes :as routes]
            [nrepl.server :as nrepl]
            [cider.piggieback]
            [clojure.java.io :as io]
            [clojure.edn :as edn])
  (:import (java.io PushbackReader)))

(def handler
  (bidi.ring/make-handler routes/serverside-routes routes/serverside-handler-map))

(mount/defstate server
  :start (jetty/run-jetty handler {:port  3000
                                   :join? false})
  :stop (.stop server))

(def fig-build-id "dev")

(defn cljs-repl [] (figwheel.main.api/cljs-repl fig-build-id))

(def figwheel-config {:id      fig-build-id
                      :options {:main            'huge-feedback.core
                                :closure-defines {"re_frame.trace.trace_enabled_QMARK_" true}
                                :preloads        ['day8.re-frame-10x.preload]}
                      :config  {:watch-dirs ["src/main/cljs" "src/main/cljc"]
                                :mode       :serve
                                :open-url   "http://localhost:3000/"}})

(defn read-local-config []
  (->> "public/config.edn"
       (io/resource)
       (io/reader)
       (PushbackReader.)
       (edn/read)))

(mount/defstate local-config
  :start (read-local-config))

(mount/defstate ^{:on-reload :noop} figwheel
  :start (figwheel.main.api/start figwheel-config)
  :stop (figwheel.main.api/stop fig-build-id))

(mount/defstate ^{:on-reload :noop} nrepl
  :start (nrepl/start-server :port 7888 :handler (nrepl/default-handler #'cider.piggieback/wrap-cljs-repl))
  :stop (nrepl/stop-server nrepl))

(defn cljs-repl []
  (figwheel.main.api/cljs-repl fig-build-id))

(defn -main [& args]
  (mount/start))