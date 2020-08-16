(ns huge-feedback.job-utils
  (:require [huge-feedback.apis.gitlab :as gitlab]))

(defn canonical-stage-ordering [jobs]
  (->> jobs
       (map (comp gitlab/stage-ordering vals))
       (sort-by count)
       (last)))

(defn add-mr [mrs {:keys [ref] :as pipeline}]
  (if-let [mr-id (gitlab/get-mr-iid-from-ref ref)]
    (assoc pipeline :merge-request (get mrs mr-id))
    pipeline))