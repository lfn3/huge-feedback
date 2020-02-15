(ns huge-feedback.core
  (:require [huge-feedback.routes :as routes]
            [reagent.core :as rg]
            [re-frame.core :as rf]
            [huge-feedback.util :as util]
            [huge-feedback.apis.gitlab :as gitlab]
            [huge-feedback.apis.http]
            [huge-feedback.apis.huge-feedback]
            [clojure.tools.reader.edn :as edn]
            [huge-feedback.config :as config]
            [huge-feedback.pipelines :as pipelines]
            [huge-feedback.apis.http :as http]))

(defn parse-serverside-config-response [[ok? resp]]
  (when (and ok? (= 200 (:status resp)))
    (:body resp)))

(defn reset-fx []
  (let [active-panel (routes/parse-url (-> js/window (.-location) (.-pathname)))]
    {:db           {:active-panel active-panel
                    ::config/config-state ::config/requesting}
     ;TODO: pull in a saved config from localstorage?
     :ajax-request (huge-feedback.apis.huge-feedback/get-config #(-> %1
                                                                     (parse-serverside-config-response)
                                                                     (config/validate-then-set-config)))}))

(rf/reg-event-fx :initialize
  (fn [{:keys [db]} _]
    (if (empty? db)
      (reset-fx)
      {})))

(rf/reg-event-fx :reset
  (fn [_ _] (reset-fx)))

(defmulti active-panel :handler)

(defmethod active-panel ::routes/index [_]
  [:div
   [config/status true]
   [pipelines/panel]])

(defmethod active-panel :config [_]
  [config/panel])

(defmethod active-panel :default [& args]
  [:div [:h3 "Couldn't find handler for "]
   (util/display-html-debug args)])

(rf/reg-sub :stage-state-for-pipeline
  (fn [{:keys [jobs]} [_ pipeline-id]]
    (let [jobs (-> jobs
                   (get pipeline-id)
                   (vals))
          stage-order (->> (gitlab/stage-ordering jobs)
                           (map-indexed (fn [idx val] [val idx]))
                           (into {}))
          key-fn (comp stage-order key)]
      (sort-by key-fn (gitlab/stage-state jobs)))))

(rf/reg-sub :active-panel
  #(get %1 :active-panel))

(defn get-all-stage-names [{:keys [jobs] :as db}]
  (->> jobs
       (vals)
       (mapcat vals)
       (map :stage)
       (distinct)
       (into #{})))

(rf/reg-sub :all-stage-names get-all-stage-names)

(defn get-job-names-by-stage [{:keys [jobs] :as db}]
  (->> jobs (vals) (mapcat vals)))

(rf/reg-event-db :pipelines
  (fn [db [_ pipelines-by-id]]
    (-> db
        (update :pipelines merge pipelines-by-id)           ;TODO: this will grow without bound.
        (update :jobs #(->> %1
                            (filter (fn [[k _]] (get pipelines-by-id k)))
                            (into {}))))))

(rf/reg-event-db :jobs
  (fn [db [_ pipeline-id jobs]]
    (update-in db [:jobs pipeline-id] merge (->> jobs
                                                 (map (juxt :id identity))
                                                 (into {})))))

(defn app []
  (active-panel @(rf/subscribe [:active-panel])))

(def refresh-interval-ms 60000)

(rf/reg-event-db :next-poll-id
  (fn [db [_ id]] (assoc db :next-poll-id id)))

(rf/reg-sub :next-poll-id
  (fn [{:keys [next-poll-id]}] next-poll-id))

(rf/reg-sub :config
  (fn [db] (get-in db [::config/config])))

(rf/reg-sub :gitlab-config
  (fn [db] (get-in db [::config/config ::gitlab/config])))

(rf/reg-event-db :config
  (fn [db [_ config]]
    (assoc-in db [::config/config ::gitlab/config] config)))

(defn with-proxy [config req-map]
  (assoc req-map ::http/proxy? (:huge-feedback.core/use-cors-proxy? config)))

(defn dispatch-req [gitlab-config req]
  (rf/dispatch [:ajax-request (with-proxy gitlab-config req)]))

(defn poll-jobs [gitlab-config pipelines]
  (->> pipelines
       (map :id)
       (map (fn [pipeline-id] (dispatch-req gitlab-config
                                            (gitlab/get-jobs-for-pipeline pipeline-id
                                                                          gitlab-config
                                                                          (fn [resp] (rf/dispatch [:jobs pipeline-id resp]))))))
       (dorun)))

(defn get-pipelines [config]
  (dispatch-req config
                (gitlab/get-pipelines-including-at-least-one-master-build
                  (::gitlab/config config)
                  (fn [[_ {:keys [body]}]]
                    (rf/dispatch [:pipelines (gitlab/pipelines->by-id body)])
                    (poll-jobs (::gitlab/config config) body)))))

(defn poll-gitlab-once []
  (when (= ::config/valid (first @(rf/subscribe [:config-state])))
    (get-pipelines @(rf/subscribe [:config]))))

;TODO: make this more selective - only look for jobs that are in a non-terminal state?
;TODO: And just look at the last page of a pipeline's jobs (where any new jobs will go)
;TODO: since this is getting rate limited.
(defn continuously-poll-gitlab []
  (js/clearTimeout @(rf/subscribe [:next-poll-id]))
  (poll-gitlab-once)
  (rf/dispatch [:next-poll-id (js/setTimeout continuously-poll-gitlab refresh-interval-ms)]))

(defn main []
  (enable-console-print!)

  (rf/dispatch-sync [:initialize])

  (rg/render [app] (js/document.getElementById "app"))

  (set! (.-onpopstate js/window) routes/handle-pop-state)
  #_(continuously-poll-gitlab)

  (when (nil? (-> js/window (.-history) (.-state)))
    (.replaceState (.-history js/window)
                   (pr-str {:handler routes/index-key})
                   "")))

(main)
