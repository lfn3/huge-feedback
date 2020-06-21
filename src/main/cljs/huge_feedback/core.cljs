(ns huge-feedback.core
  (:require [huge-feedback.routes :as routes]
            [reagent.core :as rg]
            [re-frame.core :as rf]
            [huge-feedback.util :as util]
            [huge-feedback.apis.huge-feedback]
            [huge-feedback.config :as config]
            [huge-feedback.pipelines :as pipelines]
            [huge-feedback.pipeline-detail :as pipeline-detail]))

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

(defmethod active-panel ::routes/index [_] [pipelines/panel])
(defmethod active-panel ::routes/pipeline-detail [_] [pipeline-detail/panel])
(defmethod active-panel :config [_] [config/panel])

(defmethod active-panel :default [& args]
  [:div [:h3 "Couldn't find handler for "]
   (util/display-html-debug args)])

(rf/reg-sub :active-panel #(get %1 :active-panel))

(rf/reg-sub :pipelines (fn [db _] (:pipelines db)))

(rf/reg-event-db :pipelines
  (fn [db [_ pipelines-by-id]]
    (-> db
        (update :pipelines merge pipelines-by-id)           ;TODO: this will grow without bound.
        #_(update :jobs #(->> %1
                            (filter (fn [[k _]] (get pipelines-by-id k)))
                            (into {}))))))

(rf/reg-event-db :jobs
  (fn [db [_ pipeline-id jobs]]
    (update-in db [:jobs pipeline-id] merge (->> jobs
                                                 (map (juxt :id identity))
                                                 (into {})))))

(rf/reg-event-db :merge-requests
  (fn [db [_ merge-request-id mr]]
    (assoc-in db [:merge-requests merge-request-id] mr)))

(defn menu-item [name route active-panel]
  (let [inner (if (= route active-panel)
                [:li.active name]
                [:li name])]
    (routes/link-for inner route)))

(def panels {::routes/index "Pipelines"
             ::routes/pipeline-detail "Pipeline Detail"
             :config "Config"})

(defn header [active-panel]
  [:header
   [:ul
    (for [[route name] panels]
      ^{:key route} [menu-item name route active-panel])]
   [config/status]])

(defn app []
  (let [p @(rf/subscribe [:active-panel])]
    [:div
     [header p]
     [active-panel p]]))

(defn main []
  (enable-console-print!)

  (rf/dispatch-sync [:initialize])

  (rg/render [app] (js/document.getElementById "app"))

  (set! (.-onpopstate js/window) routes/handle-pop-state)

  (when (nil? (-> js/window (.-history) (.-state)))
    (.replaceState (.-history js/window)
                   (pr-str {:handler routes/index-key})
                   "")))

(main)
