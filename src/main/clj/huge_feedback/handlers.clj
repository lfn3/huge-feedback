(ns huge-feedback.handlers
  (:require [ring.util.response :as resp]
            [huge-feedback.apis.gitlab]))

(defn index [_]
  (resp/resource-response "index.html" {:root "public"}))

(def allowed-ns #{'huge-feedback.apis.gitlab})
(def allowed-ns-strs (->> allowed-ns
                          (map str)
                          (into #{})))

(defn proxy-request [{:keys [f args]}]
  (if (allowed-ns-strs (namespace f))
    ;Manual application of `http.sync`
    (let [p (promise)]
      (apply (resolve f) (concat args [(fn [resp] (deliver p resp))]))
      @p)
    (resp/status (str "Not allowed to proxy " f) 400)))

(defn resources [request]
  (let [target (:uri request)]
    (or (resp/resource-response target {:root "public"})
        (resp/file-response target {:root "target/public"})
        (resp/not-found "Not found"))))