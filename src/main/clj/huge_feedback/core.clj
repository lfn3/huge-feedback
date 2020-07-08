(ns huge-feedback.core
  (:require [bidi.ring]
            [mount.core :as mount]
            [ring.adapter.jetty :as jetty]
            [huge-feedback.routes :as routes]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [huge-feedback.config :as config])
  (:import (java.io PushbackReader))
  (:gen-class))

(def handler
  (bidi.ring/make-handler routes/serverside-routes routes/serverside-handler-map))

(def default-port 80)

(mount/defstate server
  :start (jetty/run-jetty handler {:port  (or (::config/server-port config/local-config) default-port)
                                   :join? false})
  :stop (.stop server))

(defn -main [& [config-path]]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. #(mount/stop)))
  (mount/start-with-args config-path #'server #'config/local-config)
  (.join server))