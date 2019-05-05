(ns huge-feedback.core
  (:require [huge-feedback.routes :as routes]
            [reagent.core :as rg]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [huge-feedback.util :as util]
            [huge-feedback.apis.gitlab :as gitlab]
            [clojure.tools.reader.edn :as edn]))

(rf/reg-event-db :initialize
  (fn [db _]
    (if (empty? db)
      {:active-panel {:handler routes/index-key}
       ::gitlab/config {::gitlab/base-url   "https://gitlab.com/api/v4"
                        ::gitlab/project-id 13083
                        ::gitlab/token      ""}}            ;TODO: prompt users to fill this out?
      db)))

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

(defn text-editor [initial-value on-save & [validate]]
  (let [state (r/atom {:input initial-value :valid? true})]
    (fn []
      [:form
       (util/display-html-debug @state)
       [:textarea {:class         (if (:valid? @state) "" "invalid")
                   :on-change     #(->> %1 (.-target) (.-value) (swap! state assoc :input))
                   :default-value initial-value}]
       [:input {:type     "submit"
                :on-click #(let [{:keys [input]} @state]
                             (.preventDefault %1)
                             (if (or (nil? validate) (validate input))
                               (on-save input)
                               (swap! state assoc :valid? false)))}]])))

(defmulti active-panel :handler)

(defmethod active-panel ::routes/index [_]
  (let [master @(rf/subscribe [:latest "master"])]
    [:div
     (routes/link-for "Config" :config)
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
  [:div
   [text-editor (str @(rf/subscribe [:config]))
    #(rf/dispatch [:config (edn/read-string %1)])
    #(try (let [val (edn/read-string %1)]
            (gitlab/valid-config? val))
          (catch js/Error e
            false))]])

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
        (assoc :pipelines pipelines-by-id)
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
  (fn [{:keys [::gitlab/config]}] config))

(rf/reg-event-db :config
  (fn [db [_ config]]
    (assoc db ::gitlab/config config)))

(defn poll-jobs [{:keys [::gitlab/base-url ::gitlab/project-id ::gitlab/token]} pipelines]
  (->> pipelines
       (map :id)
       (map (fn [pipeline-id] (gitlab/get-jobs-for-pipeline base-url
                                                            project-id
                                                            pipeline-id
                                                            token
                                                            (fn [resp] (rf/dispatch [:jobs pipeline-id resp])))))
       (dorun)))

;TODO: make this more selective - only look for jobs that are in a non-terminal state?
;TODO: And just look at the last page of a pipeline's jobs (where any new jobs will go)
;TODO: since this is getting rate limited.
(defn poll-gitlab []
  (js/clearTimeout @(rf/subscribe [:next-poll-id]))
  (let [{:keys [::gitlab/base-url ::gitlab/project-id ::gitlab/token] :as config} @(rf/subscribe [:config])]

    (gitlab/get-pipelines-including-at-least-one-master-build base-url
                                                              project-id
                                                              token
                                                              (fn [resp]
                                                                (rf/dispatch [:pipelines (gitlab/pipelines->by-id resp)])
                                                                (poll-jobs config resp))))

  (rf/dispatch [:next-poll-id (js/setTimeout poll-gitlab refresh-interval-ms)]))

(defn main []
  (enable-console-print!)

  (rf/dispatch-sync [:initialize])
  (rf/dispatch-sync [:set-active-panel (routes/parse-url (-> js/window (.-location) (.-pathname)))])

  (rg/render [app] (js/document.getElementById "app"))

  (set! (.-onpopstate js/window) routes/handle-pop-state)
  (poll-gitlab)

  (when (nil? (-> js/window (.-history) (.-state)))
    (.replaceState (.-history js/window)
                   (pr-str {:handler routes/index-key})
                   "")))

(main)
