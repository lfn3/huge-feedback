(ns huge-feedback.handlers
  (:require [ring.util.response :as resp]))

(defn index [_]
  (resp/resource-response "index.html" {:root "public"}))

(defn resources [request]
  (let [target (:uri request)]
    (or (resp/resource-response target {:root "public"})
        (resp/file-response target {:root "target/public"})
        (resp/not-found "Not found"))))