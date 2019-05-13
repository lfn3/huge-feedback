(ns huge-feedback.apis.huge-feedback
  (:require [ajax.edn]
            [ajax.ring]
            [huge-feedback.apis.http :as http]))

(defn get-config [handler]
  {:uri "/config.edn"
   :method "GET"
   ::http/format ::http/local
   ::http/proxy? false
   :handler handler})
