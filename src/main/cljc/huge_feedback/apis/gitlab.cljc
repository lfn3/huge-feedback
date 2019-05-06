(ns huge-feedback.apis.gitlab
  (:require [huge-feedback.apis.http :as http]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]))

(s/def ::token string?)
(s/def ::project-id int?)
(s/def ::base-url string?)
(s/def ::config (s/keys :req [::token ::project-id ::base-url]))

(defn valid-config? [config]
  ;TODO: use api to check we can actually make a request that requires auth
  (s/valid? ::config config))

(s/fdef valid-config? :args (s/cat :config ::config))

;What is this nonsense?
(def link-header-name #?(:clj "Link"
                         :cljs "link"))

(defn get-next-link-header-value [resp]
  (let [link-header (get-in resp [:headers link-header-name])
        next-link (-> link-header
                      (str/split #",")
                      (->>
                        (filter #(str/ends-with? %1 "rel=\"next\""))
                        (first)))]
    (when next-link
      (last (re-find #"<(http.+)>; rel=\"next\"" next-link)))))

(defn paginate-until-we-find-at-least-one-master-build [resp]
  (let [does-not-contain-master-pipeline? (->> resp
                                       :body
                                       (map :ref)
                                       (filter (partial = "master"))
                                       (seq)
                                       (nil?)
                                       (not))]
    (when does-not-contain-master-pipeline?
      (get-next-link-header-value resp))))

(defn masters [pipelines-by-ids]
  (->> pipelines-by-ids
       (vals)
       (filter (comp (partial = "master") :ref))
       (sort-by :id)))

(defn latest-master [pipelines-by-ids]
  (last (masters pipelines-by-ids)))

(defn pipelines->by-id [pipelines]
  (->> pipelines
       (map (juxt :id identity))
       (into {})))

;In priority order
(def job-states ["failed" "running" "pending" "success" "canceled" "skipped" "manual"])
(def job-states-map (->> job-states
                         (map-indexed (fn [idx val] [val idx]))
                         (into {})))

(defn get-projects [base-url handler]
  (http/get (str base-url "/projects") handler))

(defn get-project [base-url id handler]
  (http/get (str base-url "/projects/" id) handler))

(defn get-pipelines [base-url project-id token handler]
  (http/get (str base-url "/projects/" project-id "/pipelines?private_token=" token) handler))

(defn get-pipelines-including-at-least-one-master-build [base-url project-id token handler]
  (http/paginated-get (str base-url "/projects/" project-id "/pipelines?private_token=" token)
                      paginate-until-we-find-at-least-one-master-build
                      handler))

(defn get-pipeline [base-url project-id pipeline-id token handler]
  (http/get (str base-url "/projects/" project-id "/pipelines/" pipeline-id "?private_token=" token) handler))

(defn get-jobs [base-url project-id token handler]
  (http/get (str base-url "/projects/" project-id "/jobs?private_token=" token) handler))

(defn get-jobs-for-pipeline [base-url project-id pipeline-id token handler]
  (http/paginated-get (str base-url "/projects/" project-id "/pipelines/" pipeline-id "/jobs?private_token=" token)
                      get-next-link-header-value
                      handler))

(defn get-user-projects [base-url user-id token handler]
  (http/get (str base-url "/users/" user-id "/projects?private_token=" token) handler))

(defn stage-ordering [jobs]
  (->> jobs
       (sort-by :id)
       (map :stage)
       (distinct)))

(defn reduce-jobs-status [jobs]
  (->> jobs
       (sort-by (comp job-states-map :status))
       (first)))

(defn only-latest-runs [jobs]
  (->> jobs
       (reduce (fn [acc job] (let [other (get acc (:name job))]
                               (if (and (:id other) (< (:id job) (:id other)))
                                 acc
                                 (assoc acc (:name job) job))))
               {})
       (vals)))

(defn stage-state [jobs]
  (->> jobs
       (map #(select-keys %1 [:stage :status :name :id]))
       (group-by :stage)
       (map (fn [[k v]] [k (->> v
                                (only-latest-runs)
                                (reduce-jobs-status))]))
       (into {})))