(ns huge-feedback.apis.gitlab
  (:require [huge-feedback.apis.http :as http]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [ajax.json]
            [ajax.ring]
            [clojure.tools.reader.edn :as edn]
            [re-frame.core :as rf]
            [clojure.walk :as walk]
            [clojure.set :as set]))

(s/def ::token string?)
(s/def ::project-id int?)
(s/def ::base-url string?)
(def known-keywords #{::token ::project-id ::base-url})
(s/def ::config (s/keys :req [::token ::project-id ::base-url]))
(s/def ::uri (s/coll-of (s/or :keyword known-keywords :str string?)))
(s/def ::req-map (s/keys :req [::http/format]
                         :opt [::http/proxy?]
                         :req-un [::http/handler ::http/method ::uri]))

(defn build-gitlab-request [uri method handler & [params]]
  (cond-> {::uri         (concat [::base-url "/"] uri ["?private_token=" ::token])
           :method       method
           :handler      handler
           ::http/format ::http/json}
          params (assoc :params params)))


(s/fdef build-gitlab-request
        :args (s/cat :uri ::uri :method ::http/method :handler fn? :params coll?)
        :ret ::req-map)

(defn get-next-link-header-value [resp]
  (let [link-header (or (get-in resp [:headers "Link"])
                        (get-in resp [:headers "link"]))    ;;Parsed as title case java side, and lowercase js side
        next-link (-> link-header
                      (str/split #",")
                      (->>
                        (filter #(str/ends-with? %1 "rel=\"next\""))
                        (first)))]
    (when next-link
      (last (re-find #"<(http.+)>; rel=\"next\"" next-link)))))

(defn is-master-pipeline? [pipeline] (-> pipeline :ref (= "master")))

(defn get-next-page-url-if-no-master-pipelines [resp]
  (let [does-not-contain-master-pipeline? (->> resp
                                               :body
                                               (some is-master-pipeline?)
                                               (not))]
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

(defn get-pipelines-for-project [handler]
  (build-gitlab-request ["projects/" ::project-id "/pipelines"]
                        "GET"
                        handler))

(defn get-jobs-for-pipeline [pipeline-id handler]
  (build-gitlab-request ["projects/" ::project-id "/pipelines/" pipeline-id "/jobs"]
                        "GET"
                        handler))

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

(def mr-id-from-ref-regex #"refs/merge-requests/(\d+)/\w+")

(defn get-mr-iid-from-ref [ref]
  (when-let [[[_ mr-id]] (->> ref (re-seq mr-id-from-ref-regex))]
    (edn/read-string mr-id)))

(s/fdef get-mr-iid-from-ref
  :args (s/cat :ref string?)
  :ret (s/nilable int?))

(defn stage-state [jobs]
  (->> jobs
       (map #(select-keys %1 [:stage :status :name :id]))
       (group-by :stage)
       (map (fn [[k v]] [k (->> v
                                (only-latest-runs)
                                (reduce-jobs-status))]))
       (into {})))

(defn get-merge-request [mr-iid handler]
  (build-gitlab-request ["projects/" ::project-id "/merge_requests/" mr-iid]
                        "GET"
                        (fn [[ok? {:keys [body]}]] (when ok? (handler body)))))

(defn get-merge-requests [handler]
  (build-gitlab-request ["projects/" ::project-id "/merge_requests"]
                        "GET"
                        (fn [[ok? {:keys [body]}]] (when ok? (handler body)))))

(defn get-mr-pipelines [mr-id handler]
  (build-gitlab-request ["projects/" ::project-id "/merge_requests/" mr-id "/pipelines"]
                        "GET"
                        (fn [[ok? {:keys [body]}]] (when ok? (handler body)))))

(defn get-jobs [handler]
  (build-gitlab-request ["projects/" ::project-id "/jobs/"]
                        "GET"
                        (fn [[ok? {:keys [body]}]] (when ok? (handler body)))))

(defn get-mr-for-pipeline [pipeline handler]
  (when-let [mr-iid (-> pipeline :ref (get-mr-iid-from-ref))]
    (get-merge-request mr-iid handler)))


(defn with-proxy [req-map config]
  (assoc req-map ::http/proxy? (:huge-feedback.core/use-cors-proxy? config)))

(defn replace-with-config-item [config item]
  (if (known-keywords item)
    (get config item)
    item))

(defn merge-config-keys [req-map config]
  (walk/prewalk (partial replace-with-config-item config) req-map))

(s/fdef merge-config-keys
        :args (s/cat :req-map ::req-map :config ::config)
        :ret ::http/req-map)

(defn gitlab-req->http-req [req-map config]
  (-> req-map
      (merge-config-keys config)
      (set/rename-keys {::uri :uri})
      (update :uri str/join)))

(defn test-request [config handler]
  "Here we pass the config and assemble the request directly"
  (-> (build-gitlab-request "todos" "GET" handler)
      (gitlab-req->http-req (::config config))
      (with-proxy config)))

(defn gitlab-request-event-fx [{:keys [db] :as cofx} [_ req-map]]
  (let [config (:huge-feedback.config/config db)
        transformed-req (-> req-map
                            (gitlab-req->http-req (::config config))
                            (with-proxy config))]
    (assoc cofx :ajax-request transformed-req)))

(rf/reg-event-fx :gitlab-request gitlab-request-event-fx)