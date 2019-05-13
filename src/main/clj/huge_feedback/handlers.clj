(ns huge-feedback.handlers
  (:require [ring.util.response :as resp]
            [huge-feedback.apis.gitlab]
            [huge-feedback.apis.http :as http]))

(defn index [_]
  (resp/resource-response "index.html" {:root "public"}))

(def destination->req-builder-fn {:gitlab huge-feedback.apis.gitlab/build-gitlab-request})

(defn launch-req [req-map handler]
  (-> req-map
      (assoc :handler handler)
      (http/execute)))

(defn proxy-request [{:keys [destination] :as req-map}]
  (let [p (promise)
        handler (fn [resp] (deliver p resp))
        req-map ((destination->req-builder-fn destination) req-map)]
    (launch-req req-map handler)
    @p))

(defn resources [request]
  (let [target (:uri request)]
    (or (resp/resource-response target {:root "public"})
        (resp/file-response target {:root "target/public"})
        (resp/not-found "Not found"))))