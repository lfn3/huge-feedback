(ns huge-feedback.jobs
  (:require [re-frame.core :as rf]
            [huge-feedback.util :as util]
            [huge-feedback.apis.gitlab :as gitlab]))

(defn canonical-stage-ordering [jobs-by-pipeline-id]
  (->> jobs-by-pipeline-id
       (vals)
       (map (comp gitlab/stage-ordering vals))
       (sort-by count)
       (last)))

(defn job-table-header
  "Returns a collection of tuples stage names and collection of job names in that stage"
  [{:keys [jobs] :as db}]
  (let [flattened-jobs (->> jobs (vals) (mapcat vals))
        stage-ordering (canonical-stage-ordering jobs)
        job-names-by-stage (->> flattened-jobs
                                (group-by :stage)
                                (map (fn [[k vs]] [k (->> vs
                                                          (map :name)
                                                          (into (sorted-set)))]))
                                (into {}))]
    (prn stage-ordering)
   (->> stage-ordering
        (map (fn [s] [s (get job-names-by-stage s)])))))

(rf/reg-sub :job-table-header job-table-header)

(defn get-job-names-by-stage [{:keys [jobs] :as db}] (->> jobs (vals) (mapcat vals)))

(defn header []
  [:thead
   [:tr
    (for [[sn job-names] @(rf/subscribe [:job-table-header])]
      [:th {:col-span (count job-names)} sn])]])

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
     [:a.block {:href (:web_url job)}])])

(defn row [table-header jobs]
  [:tr
   (for [job (add-missing-jobs table-header (vals jobs))]
     [cell job])])

(rf/reg-sub :all-jobs-by-pipeline :jobs)


(defn body []
  [:tbody
   (for [[_ jobs] @(rf/subscribe [:all-jobs-by-pipeline])]
       [row @(rf/subscribe [:job-table-header]) jobs])])

(defn panel []
  [:div
   [:table.job-status
    [header]
    [body]]])