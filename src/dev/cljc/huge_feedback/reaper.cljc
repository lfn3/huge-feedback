(ns huge-feedback.reaper
  (:require [huge-feedback.apis.gitlab :as gitlab]
            [huge-feedback.apis.http :as http]
            [huge-feedback.config :as config]))

(defn merge-requests []
  (-> (gitlab/build-gitlab-request ["projects/" ::gitlab/project-id "/merge_requests"]
                                   "GET"
                                   identity)
      (gitlab/gitlab-req->http-req (::gitlab/config config/local-config))
      (gitlab/with-proxy (::gitlab/config config/local-config))
      (http/execute-sync)))

(defn mr-pipelines [full-mr-response]
  (->> full-mr-response
       (last)
       :body
       (pmap (fn [{:keys [iid]}] [iid (-> iid
                                          (gitlab/get-mr-pipelines identity)
                                          (gitlab/gitlab-req->http-req (::gitlab/config config/local-config))
                                          (gitlab/with-proxy (::gitlab/config config/local-config))
                                          (http/execute-sync))]))))

(defn mr-pipelines-to-cancel-on-mr [[mr-id pipelines]]
  (->> pipelines
       (filter (comp (partial = "running") :status))))

(defn mr-pipelines-to-cancel []
  (->> (merge-requests)
       (mr-pipelines)
       (mapcat mr-pipelines-to-cancel-on-mr)))
