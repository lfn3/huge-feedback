(ns huge-feedback.dev
  (:require [nrepl.server :as nrepl]
            [figwheel.main.api]
            [cider.piggieback]
            [mount.core :as mount]
            [huge-feedback.core :as core]
            [clojure.java.io :as io]
            [huge-feedback.config :as config]))

(def fig-build-id "dev")

(defn cljs-repl [] (figwheel.main.api/cljs-repl fig-build-id))

(defn figwheel-config [port]
  {:id      fig-build-id
   :options {:main            'huge-feedback.core
             :closure-defines {"re_frame.trace.trace_enabled_QMARK_" true}
             :preloads        ['day8.re-frame-10x.preload]}
   :config  {:watch-dirs ["src/main/cljs" "src/main/cljc"]
             :target-dir "target/resources"
             :mode       :serve
             :css-dirs   ["src/main/resources/public"]
             :open-url   (str "http://localhost:" port \/)}})

(mount/defstate ^{:on-reload :noop} figwheel
                :start (figwheel.main.api/start (figwheel-config (core/get-port)))
                :stop (figwheel.main.api/stop fig-build-id))

(mount/defstate ^{:on-reload :noop} nrepl
                :start (nrepl/start-server :port 7888 :handler (nrepl/default-handler #'cider.piggieback/wrap-cljs-repl))
                :stop (nrepl/stop-server nrepl))

(defn cljs-repl []
  (figwheel.main.api/cljs-repl fig-build-id))

(def config-path "src\\dev\\clj\\huge_feedback\\config.edn")

(defn start [] (mount/start-with-args config-path))