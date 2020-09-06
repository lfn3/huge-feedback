(ns huge-feedback.core
  (:require [huge-feedback.routes :as routes]
            [reagent.dom :as rg-dom]
            [re-frame.core :as rf]
            [huge-feedback.util :as util]
            [huge-feedback.apis.huge-feedback]
            [huge-feedback.config :as config]
            [huge-feedback.pipelines :as pipelines]
            [huge-feedback.pipeline-detail :as pipeline-detail]
            [huge-feedback.job-detail :as job-detail]))

(defn handle-serverside-config-response [[ok? resp]]
  (when-let [cfg (and ok? (= 200 (:status resp)) (:body resp))]
    (rf/dispatch [:set-config cfg])                         ; We set it so it can be seen in the ui
    (config/validate-then-set-config cfg)))                 ; And then validate it

(defn reset-fx []
  (let [active-panel (routes/parse-url (-> js/window (.-location) (.-pathname)))]
    {:db           {:active-panel         active-panel
                    ::config/config-state ::config/requesting}
     ;TODO: pull in a saved config from localstorage?
     :ajax-request (huge-feedback.apis.huge-feedback/get-config handle-serverside-config-response)}))

(rf/reg-event-fx :initialize
  (fn [{:keys [db]} _]
    (if (empty? db)
      (reset-fx)
      {})))

(rf/reg-event-fx :reset
  (fn [_ _] (reset-fx)))

(defmulti active-panel :handler)

(defmethod active-panel ::routes/index [_] [pipelines/panel])
(defmethod active-panel ::routes/pipeline-detail [_] [pipeline-detail/panel])
(defmethod active-panel ::routes/job-detail [_] [job-detail/panel])
(defmethod active-panel :config [_] [config/panel])

(defmethod active-panel :default [& args]
  [:div [:h3 "Couldn't find handler for "]
   (util/display-html-debug args)])

(rf/reg-sub :active-panel #(get %1 :active-panel))

(rf/reg-sub :pipelines (fn [db _] (:pipelines db)))

(defn add-pipelines [db [_ pipelines-by-id]]
  (->> pipelines-by-id
       (map (fn [[k v]] [k (select-keys v #{:id :iid :sha :ref :stage :web_url})]))
       (update db :pipelines merge )))

(rf/reg-event-db :pipelines add-pipelines)

(defn add-jobs [db [_ pipeline-id jobs]]
  (->> jobs
       (map (fn [v] (assoc v :pipeline-id (get-in v [:pipeline :id]))))
       (map (fn [v] (select-keys v #{:id :stage :status :name :duration :pipeline-id :web_url})))
       (map (juxt :id identity))
       (into {})
       (update-in db [:jobs pipeline-id] merge)))

(rf/reg-event-db :jobs add-jobs)

(defn add-merge-requests [db [_ merge-request-id mr]]
  (assoc-in db [:merge-requests merge-request-id] mr))

(rf/reg-event-db :merge-requests add-merge-requests)

(defn menu-item [elem route active-panel]
  (let [inner (routes/link-for elem route)]
    (if (= route (:handler active-panel))
      [:li.active inner]
      [:li inner])))

(def panels {::routes/index           [:span "Pipelines"]
             ::routes/pipeline-detail [:span "Pipeline Detail"]
             ::routes/job-detail      [:span "Job Detail"]
             :config                  [:span "Config" [config/status-icon]]})

(defn header [active-panel]
  [:nav.main
   [:ul
    (for [[route name] panels]
      ^{:key route} [menu-item name route active-panel])]])

(defn app []
  (let [p @(rf/subscribe [:active-panel])]
    [:div
     [header p]
     [:main
      [active-panel p]]]))

(defn main []
  (enable-console-print!)

  (rf/dispatch-sync [:initialize])

  (rg-dom/render [app] (js/document.getElementById "app"))

  (set! (.-onpopstate js/window) routes/handle-pop-state)

  (when (nil? (-> js/window (.-history) (.-state)))
    (.replaceState (.-history js/window)
                   (pr-str {:handler routes/index-key})
                   "")))

(main)
