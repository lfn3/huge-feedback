(ns huge-feedback.core
  (:require [huge-feedback.routes :as routes]
            [reagent.core :as rg]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [huge-feedback.util :as util]
            [huge-feedback.apis.gitlab :as gitlab]
            [huge-feedback.apis.http]
            [huge-feedback.apis.huge-feedback]
            [clojure.tools.reader.edn :as edn]
            [huge-feedback.config :as config]
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

;TODO: add count of jobs pending/running/passed/failed?
(defn pipeline-stage-html [[stage-name {:keys [status]}]]
  [:div {:class (str "pipeline-stage " status)} stage-name])

(defn pipeline-html [pipeline]
  [:div
   [:a.block {:href (:web_url pipeline)}
    [:div (str (:ref pipeline) \@ (:sha pipeline))]
    [:div.pipeline
     (for [stage-state @(rf/subscribe [:stage-state-for-pipeline (:id pipeline)])]
       (pipeline-stage-html stage-state))]]])

(defn mini-pipeline-html [pipeline]
  [:div
   [:a.block {:href (:web_url pipeline)}
    [:div.pipeline.mini
     (for [stage-state @(rf/subscribe [:stage-state-for-pipeline (:id pipeline)])]
       (pipeline-stage-html stage-state))]]])

(defmulti active-panel :handler)

(defmethod active-panel ::routes/index [_]
  (let [master @(rf/subscribe [:latest "master"])]
    [:div
     [config/status true]
     (if master
       [:div
        [pipeline-html master]
        (for [pipeline @(rf/subscribe [:rest "master"])]
          [mini-pipeline-html pipeline])
        (for [ref (->> @(rf/subscribe [:refs])
                       (filter (partial not= "master")))]
          [:div
           [pipeline-html @(rf/subscribe [:latest ref])]
           (for [rest @(rf/subscribe [:rest ref])]
             [mini-pipeline-html rest])])]
       [:p "Fetching pipelines..."])]))

(defmethod active-panel :config [_]
  [config/panel])

(defmethod active-panel :default [& args]
  [:div [:h3 "Couldn't find handler for "]
   (util/display-html-debug args)])

(rf/reg-sub :latest
  (fn [{:keys [pipelines]} [_ ref]]
    (->> pipelines
         (vals)
         (filter (comp (partial = ref) :ref))
         (sort-by :id)
         (last))))

(rf/reg-sub :rest
  (fn [{:keys [pipelines]} [_ ref]]
    (->> pipelines
         (vals)
         (filter (comp (partial = ref) :ref))
         (sort-by :id)
         (reverse)
         (rest))))

(rf/reg-sub :non-master
  (fn [{:keys [pipelines]}] (->> pipelines
                                 (vals)
                                 (filter (comp (partial not= "master") :ref)))))

(rf/reg-sub :refs
  (fn [{:keys [pipelines]}] (->> pipelines
                                 (vals)
                                 (map :ref)
                                 (distinct))))

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

(rf/reg-sub :pipelines
  (fn [{:keys [pipelines]}] (vals pipelines)))

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

(defn poll-jobs [gitlab-config pipelines]
  (->> pipelines
       (map :id)
       (map (fn [pipeline-id] (rf/dispatch [:ajax-request (gitlab/get-jobs-for-pipeline pipeline-id
                                                                                        gitlab-config
                                                                                        (fn [resp] (rf/dispatch [:jobs pipeline-id resp])))])))
       (dorun)))

(defn with-proxy [config req-map]
  (assoc req-map ::http/proxy? (:huge-feedback.core/use-cors-proxy? config)))

(defn get-pipelines [config]
  (http/execute
    (with-proxy config
                (gitlab/get-pipelines-including-at-least-one-master-build
                  (::gitlab/config config)
                  (fn [resp]
                    (rf/dispatch [:pipelines (gitlab/pipelines->by-id resp)])
                    (poll-jobs (::gitlab/config config) resp))))))

;TODO: make this more selective - only look for jobs that are in a non-terminal state?
;TODO: And just look at the last page of a pipeline's jobs (where any new jobs will go)
;TODO: since this is getting rate limited.
(defn poll-gitlab []
  (js/clearTimeout @(rf/subscribe [:next-poll-id]))
  (when (= ::config/valid (first @(rf/subscribe [:config-state])))
    (get-pipelines @(rf/subscribe [:config])))
  (rf/dispatch [:next-poll-id (js/setTimeout poll-gitlab refresh-interval-ms)]))

(defn main []
  (enable-console-print!)

  (rf/dispatch-sync [:initialize])

  (rg/render [app] (js/document.getElementById "app"))

  (set! (.-onpopstate js/window) routes/handle-pop-state)
  #_(poll-gitlab)

  (when (nil? (-> js/window (.-history) (.-state)))
    (.replaceState (.-history js/window)
                   (pr-str {:handler routes/index-key})
                   "")))

(main)
