(ns huge-feedback.gitlab-polling
  (:require [re-frame.core :as rf]
            [huge-feedback.apis.gitlab :as gitlab]
            [clojure.set :as set]))

(def refresh-interval-ms 60000)

(rf/reg-event-db :next-poll-id
                 (fn [db [_ id]] (assoc db :next-poll-id id)))

(rf/reg-sub :next-poll-id
            (fn [{:keys [next-poll-id]}] next-poll-id))

(defn dispatch-gl-req [req]
  (rf/dispatch [:gitlab-request req]))

(defn make-job-resp-handler [pipeline-id]
  (fn job-resp-handler [[_ {:keys [body] :as resp}]]
    (rf/dispatch [:jobs pipeline-id body])
    (when-let [next-page-url (gitlab/get-next-link-header-value resp)]
      (-> (gitlab/get-jobs-for-pipeline pipeline-id job-resp-handler)
          (assoc ::gitlab/uri [next-page-url])
          (dispatch-gl-req)))))

(defn poll-jobs-for-pipeline [pipeline-id]
  (->> (make-job-resp-handler pipeline-id)
       (gitlab/get-jobs-for-pipeline pipeline-id)
       (dispatch-gl-req)))

(defn poll-jobs [pipelines]
  (->> pipelines
       (map :id)
       (map poll-jobs-for-pipeline)
       (dorun)))

(defn get-merge-requests [pipelines]
  (->> pipelines
       (map (fn [pipeline]
              (gitlab/get-mr-for-pipeline pipeline
                                          (fn [resp] (rf/dispatch [:merge-requests (:iid resp) resp])))))
       (filter identity)                                    ; filter out nils
       (map (fn [req-map] (dispatch-gl-req req-map)))
       (dorun)))

(defn pipeline-resp-handler [[_ {:keys [body] :as resp}]]
  (let [pipelines-by-id (gitlab/pipelines->by-id body)]
    (rf/dispatch [:pipelines pipelines-by-id])
    (get-merge-requests body)
    (poll-jobs body)
    (let [target-num-pipelines (get @(rf/subscribe [:config]) :huge-feedback.config/num-pipelines-to-show)
          current-pipeline-ids (set/union (keys pipelines-by-id) (keys @(rf/subscribe [:pipelines])))
          current-num-pipelines (count current-pipeline-ids)]
      (when-let [next-page-url (and (< current-num-pipelines target-num-pipelines)
                                    (gitlab/get-next-link-header-value resp))]
        (-> (gitlab/get-pipelines-for-project pipeline-resp-handler)
            (assoc ::gitlab/uri [next-page-url])
            (dispatch-gl-req))))))

(defn get-pipelines []
  (-> pipeline-resp-handler
      (gitlab/get-pipelines-for-project)
      (dispatch-gl-req)))

(defn poll-gitlab-once []
  (when (= :huge-feedback.config/valid (first @(rf/subscribe [:config-state])))
    (get-pipelines)))

#?(:cljs (do
           ; TODO: this could be made a bit more reliable.
           (defn cancel-polling []
             (when-let [poll-id @(rf/subscribe [:next-poll-id])]
               (js/clearTimeout poll-id)))

           ;TODO: make this more selective - only look for jobs that are in a non-terminal state?
           ;TODO: And just look at the last page of a pipeline's jobs (where any new jobs will go)
           ;TODO: since this is getting rate limited.
           (defn continuously-poll-gitlab []
             (cancel-polling)
             (poll-gitlab-once)
             (rf/dispatch [:next-poll-id (js/setTimeout continuously-poll-gitlab refresh-interval-ms)]))))
