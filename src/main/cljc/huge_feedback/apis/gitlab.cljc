(ns huge-feedback.apis.gitlab
  (:require [huge-feedback.apis.http :as http]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [ajax.json]
            [ajax.ring]))

(s/def ::token string?)
(s/def ::project-id int?)
(s/def ::base-url string?)
(s/def ::config (s/keys :req [::token ::project-id ::base-url]))

(defn build-gitlab-request [uri method config handler & [params]]
  (cond-> {:uri          (str (::base-url config) "/" uri "?private_token=" (::token config))
           :method       method
           :handler      handler
           ::http/format ::http/json}
          params (assoc :params params)))

(s/fdef build-gitlab-request
        :args (s/cat :uri string? :method ::http/method :config ::config :handler fn? :params coll?)
        :ret ::http/req-map)

(defn test-request [config handler]
  (build-gitlab-request "todos" "GET" config handler))

;What is this nonsense?
(def link-header-name #?(:clj  "Link"
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
                                               (nil?))]
    (when does-not-contain-master-pipeline?
      (get-next-link-header-value resp))))

(defn pipelines->by-id [pipelines]
  (->> pipelines
       (map (juxt :id identity))
       (into {})))

;In priority order
(def job-states ["failed" "running" "pending" "success" "canceled" "skipped" "manual"])
(def job-states-map (->> job-states
                         (map-indexed (fn [idx val] [val idx]))
                         (into {})))

(defn get-pipelines-including-at-least-one-master-build [{:keys [::project-id] :as config} handler]
  (http/with-paginator-handler (build-gitlab-request (str "projects/" project-id "/pipelines")
                                                     "GET"
                                                     config
                                                     (fn [[ok? {:keys [body]}]] (when ok? (handler body))))
                               paginate-until-we-find-at-least-one-master-build))

(defn get-jobs-for-pipeline [pipeline-id {:keys [::project-id] :as config} handler]
  (http/with-paginator-handler (build-gitlab-request (str "projects/" project-id "/pipelines/" pipeline-id "/jobs")
                                                     "GET"
                                                     config
                                                     (fn [[ok? {:keys [body]}]] (when ok? (handler body))))
                               get-next-link-header-value))

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