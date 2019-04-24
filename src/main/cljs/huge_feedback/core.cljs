(ns huge-feedback.core
  (:require [huge-feedback.routes :as routes]
            [reagent.core :as rg]
            [re-frame.core :as rf]
            [huge-feedback.util :as util]
            [huge-feedback.apis.gitlab :as gitlab]))

(rf/reg-event-db :initialize
  (fn [db _]
    (if (empty? db)
      {:active-panel {:handler :index}}
      db)
    ;TODO: delete this - it clears app state on figwheel reload.
    ; which is useful for now
    {:active-panel {:handler :index}}))

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
  [:div
   (if-let [master @(rf/subscribe [:latest "master"])]
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
     [:p "Fetching pipelines..."])])

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
          key-fn (comp stage-order key)
          stage-state (sort-by key-fn (gitlab/stage-state jobs))]
          stage-state)))

(rf/reg-sub :active-panel
  #(get %1 :active-panel))

(rf/reg-event-db :pipelines
  (fn [db [_ pipelines-by-id]]
    (update db :pipelines merge pipelines-by-id)))

(rf/reg-event-db :jobs
  (fn [db [_ pipeline-id jobs]]
    (update-in db [:jobs pipeline-id] merge (->> jobs
                                                 (map (juxt :id identity))
                                                 (into {})))))

(defn app []
  (active-panel @(rf/subscribe [:active-panel])))

;TODO: remove the nasty (ref-fx instead?)
(defn nasty-side-effecty-pipelines-handler [pipelines]
  (->> pipelines
       (map :id)
       (map (fn [pipeline-id] (gitlab/get-jobs-for-pipeline gitlab/public-base-url
                                                            gitlab/project-id
                                                            pipeline-id
                                                            gitlab/token
                                                            (fn [resp] (rf/dispatch [:jobs pipeline-id resp])))))
       (dorun))

  (rf/dispatch [:pipelines (gitlab/pipelines->by-id pipelines)]))

(defn main []
  (enable-console-print!)

  (rf/dispatch-sync [:initialize])
  (rf/dispatch-sync [:set-active-panel (routes/parse-url (-> js/window (.-location) (.-pathname)))])

  (rg/render [app] (js/document.getElementById "app"))

  (set! (.-onpopstate js/window) routes/handle-pop-state)

  (gitlab/get-pipelines-including-at-least-one-master-build gitlab/public-base-url
                                                            gitlab/project-id
                                                            gitlab/token
                                                            nasty-side-effecty-pipelines-handler)

  (when (nil? (-> js/window (.-history) (.-state)))
    (.replaceState (.-history js/window)
                   (pr-str {:handler :index})
                   "")))

(main)
