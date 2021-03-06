(ns huge-feedback.handlers
  (:require [ring.util.response :as resp]
            [huge-feedback.apis.gitlab :as gitlab]
            [huge-feedback.apis.http :as http]
            [clojure.tools.reader.edn :as edn]
            [ring.util.response]
            [clojure.java.io :as io]
            [huge-feedback.config :as config]
            [clojure.set :as set])
  (:import (java.io PushbackReader)))

(defn index [_]
  (resp/resource-response "index.html" {:root "public"}))

(defn proxy-request [{:keys [body]}]
  (let [p (promise)
        handler (fn [resp] (deliver p resp))
        body (edn/read (PushbackReader. (io/reader body)))
        xformed-req (-> body
                        (gitlab/merge-config-keys (::gitlab/config config/local-config))
                        (set/rename-keys {::gitlab/uri :uri})
                        (assoc ::http/proxy? false)
                        (assoc :handler handler))]
    (http/execute xformed-req)
    (resp/response (str @p))))

(defn resources [request]
  (let [target (:uri request)]
    (or (resp/resource-response target {:root "public"})
        (resp/file-response target {:root "target/public"})
        (resp/not-found "Not found"))))

(defn config-edn [_request]
  (-> config/local-config
      (assoc-in [::gitlab/config ::gitlab/token] ::gitlab/from-proxy)
      (str)
      (resp/response)))