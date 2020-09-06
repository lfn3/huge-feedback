(ns huge-feedback.core
  (:require [bidi.ring]
            [mount.core :as mount]
            [ring.adapter.jetty :as jetty]
            [huge-feedback.routes :as routes]
            [huge-feedback.config :as config])
  (:gen-class))

(def handler
  (bidi.ring/make-handler routes/serverside-routes routes/serverside-handler-map))

(def default-port 80)

(defn get-port []
  (or (::config/server-port config/local-config) default-port))

(mount/defstate server
  :start (jetty/run-jetty handler {:port (get-port)
                                   :join? false})
  :stop (.stop server))

(defn -main [& [config-path]]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. #(mount/stop)))
  (mount/start-with-args config-path #'server #'config/local-config)
  (.join server))