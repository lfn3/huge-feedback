(ns huge-feedback.core
  (:require [bidi.ring]
            [mount.core :as mount]
            [ring.adapter.jetty :as jetty]
            [figwheel.main.api]
            [huge-feedback.routes :as routes]))


(def handler
  (bidi.ring/make-handler routes/serverside-routes routes/serverside-handler-map))

(mount/defstate server
  :start (jetty/run-jetty handler {:port  3000
                                   :join? false})
  :stop (.stop server))

(def figwheel-config {:mode     :serve
                      :open-url "http://localhost:3000/"})

(mount/defstate ^{:on-reload :noop} figwheel
  :start (figwheel.main.api/start figwheel-config "dev")
  :stop (figwheel.main.api/stop "dev"))
