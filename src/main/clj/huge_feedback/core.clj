(ns huge-feedback.core
  (:require [bidi.ring]
            [mount.core :as mount]
            [ring.adapter.jetty :as jetty]
            [huge-feedback.routes :as routes]
            [clojure.java.io :as io]
            [clojure.edn :as edn])
  (:import (java.io PushbackReader))
  (:gen-class))

(def handler
  (bidi.ring/make-handler routes/serverside-routes routes/serverside-handler-map))

(mount/defstate server
  :start (jetty/run-jetty handler {:port  3000
                                   :join? false})
  :stop (.stop server))

(defn read-local-config []
  (->> "public/config.edn"
       (io/resource)
       (io/reader)
       (PushbackReader.)
       (edn/read)))

(mount/defstate local-config
  :start (read-local-config))


(defn -main [& args]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. #(mount/stop)))
  (mount/start #'server)
  (.join server))