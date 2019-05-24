(ns huge-feedback.handlers
  (:require [ring.util.response :as resp]
            [huge-feedback.apis.gitlab]
            [huge-feedback.apis.http :as http]
            [clojure.tools.reader.edn :as edn]
            [ring.util.response]
            [clojure.java.io :as io])
  (:import (java.io PushbackReader)))

(defn index [_]
  (resp/resource-response "index.html" {:root "public"}))

(defn proxy-request [{:keys [body]}]
  (let [p (promise)
        handler (fn [resp]
                  (prn "Got response " resp)
                  (deliver p resp))
        body (edn/read (PushbackReader. (io/reader body)))]
    (prn "Proxying request " body)
    (http/execute (-> body
                      (assoc ::http/proxy? false)
                      (assoc :handler handler)))
    (ring.util.response/response (str @p))))

(defn resources [request]
  (let [target (:uri request)]
    (or (resp/resource-response target {:root "public"})
        (resp/file-response target {:root "target/public"})
        (resp/not-found "Not found"))))