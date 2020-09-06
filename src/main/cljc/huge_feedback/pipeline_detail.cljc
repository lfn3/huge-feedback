(ns huge-feedback.pipeline-detail
  (:require [re-frame.core :as rf]
            [huge-feedback.job-utils :as job-utils]
            [clojure.set :as set]))

(defn job-table-header
  "Returns a collection of tuples stage names and collection of job names in that stage"
  [{:keys [jobs] :as _db}]
  (let [flattened-jobs (->> jobs (vals) (mapcat vals))
        stage-ordering (job-utils/canonical-stage-ordering (vals jobs))
        job-names-by-stage (->> flattened-jobs
                                (group-by :stage)
                                (map (fn [[k vs]] [k (->> vs
                                                          (map :name)
                                                          (into (sorted-set)))]))
                                (into {}))]
   (->> stage-ordering
        (map (fn [s] [s (get job-names-by-stage s)])))))

(rf/reg-sub :job-table-header job-table-header)

(defn header [job-table-header]
  (let [total-jobs (->> job-table-header (map last) (map count) (reduce + 0))]
   [:thead
    [:tr
     [:th "MR"]
     [:th "Pipeline"]
     [:th {:col-span total-jobs} "Stage"]]
    [:tr
     [:th]
     (for [[sn job-names] job-table-header]
       ^{:key sn} [:th {:col-span (count job-names)} sn])]]))

(defn map-by [key-fn coll]
  (->> coll (map (fn [x] [(key-fn x) x])) (into {})))

(defn add-missing-jobs [table-header jobs]
  (let [jobs-by-name (->> jobs (map-by :name))]
   (->> table-header
        (mapcat last)
        (map #(get jobs-by-name %1 {:name %1
                                    :status "not-created"})))))

(defn cell [job]
  [:td {:class (:status job)
        :title (:name job)}
   (if (not= "not-created" (:status job))
     [:a.block {:href (:web_url job) :target "_blank"}])])

(defn mr-header [{:keys [ref merge-request] :as _pipeline}]
  (let [is-master? (= ref "master")
        ]
    [:th (if is-master? "mstr" [:a {:href (:web_url merge-request) :target "_blank"}
                                (get merge-request :id)])]))

(defn row [table-header {:keys [id web_url] :as pipeline} jobs]
  [:tr
   [mr-header pipeline]
   [:th [:a {:href web_url :target "_blank"} id]]
   (for [job (->> jobs (vals) (add-missing-jobs table-header))]
     ^{:key (:name job)} [cell job])])

(defn body [job-table-header jobs-by-pipelines]
  (if jobs-by-pipelines
   [:tbody
    (for [[{:keys [id] :as pipeline} jobs] (reverse (sort-by (comp :id key) jobs-by-pipelines))]
      ^{:key id} [row job-table-header pipeline jobs])]
   [:tbody]))

(defn all-jobs-by-pipeline [{:keys [jobs pipelines merge-requests] :as _db}]
  (->> pipelines
       (set/rename-keys jobs)
       (map (fn [[k v]] [(job-utils/add-mr merge-requests k) v]))
       (into {})))

(rf/reg-sub :all-jobs-by-pipeline all-jobs-by-pipeline)

(defn panel []
  (let [jth @(rf/subscribe [:job-table-header])
        jobs-by-pipelines @(rf/subscribe [:all-jobs-by-pipeline])]
    [:div
     [:table.job-status
      [header jth]
      [body jth jobs-by-pipelines]]]))