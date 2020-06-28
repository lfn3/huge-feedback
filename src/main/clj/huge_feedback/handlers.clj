(ns huge-feedback.handlers
  (:require [ring.util.response :as resp]
            [huge-feedback.apis.gitlab]
            [huge-feedback.apis.http :as http]
            [clojure.tools.reader.edn :as edn]
            [ring.util.response]
            [clojure.java.io :as io]
            [huge-feedback.config :as config])
  (:import (java.io PushbackReader)))

(defn index [_]
  (resp/resource-response "index.html" {:root "public"}))

(defn proxy-request [{:keys [body]}]
  (let [p (promise)
        handler (fn [resp] (deliver p resp))
        body (edn/read (PushbackReader. (io/reader body)))]
    (http/execute (-> body
                      (assoc ::http/proxy? false)
                      (assoc :handler handler)))
    (resp/response (str @p))))

(def app-db (atom nil))

(defn jobs-by-id-from-test-resources []
  (->> (io/file "src/test/resources/jobs.edn")
       (io/reader)
       (PushbackReader.)
       (edn/read)
       (group-by (comp :id :pipeline))))

(defn app-state-from-test-resources []
  {:jobs (jobs-by-id-from-test-resources)
   })

(defn populate-app-db-from-test-resources! []
  (reset! app-db (app-state-from-test-resources)))

(defn cached-app-db []
  (if-let [db @app-db]
    (resp/response db)
    (resp/status "App db not populated" 503)))

(defn resources [request]
  (let [target (:uri request)]
    (or (resp/resource-response target {:root "public"})
        (resp/file-response target {:root "target/public"})
        (resp/not-found "Not found"))))

(defn config-edn [_request] (resp/response (str config/local-config)))