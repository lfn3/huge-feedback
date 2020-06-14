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
            [huge-feedback.jobs :as jobs]
            [huge-feedback.apis.http :as http]
            [clojure.set :as set]))

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
(defmethod active-panel ::routes/jobs [_] [jobs/panel])
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

(defn menu-item [item active-panel]
  (let [inner (if (= item active-panel)
                [:li.active (name item)]
                [:li item])]
    (routes/link-for inner item)))

(def panels [::routes/index
             ::routes/jobs
             :config])

(defn header [active-panel]
  [:header
   [:ul
    (for [p panels]
      ^{:key (name p)} [menu-item p active-panel])]
   [config/status]])

(defn app []
  (let [p @(rf/subscribe [:active-panel])]
    [:div
     [header p]
     [active-panel p]]))

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
       (gitlab/get-jobs-for-pipeline pipeline-id )
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
    (let [target-num-pipelines (get @(rf/subscribe [:config]) ::config/num-pipelines-to-show)
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
  (when (= ::config/valid (first @(rf/subscribe [:config-state])))
    (get-pipelines)))

;TODO: make this more selective - only look for jobs that are in a non-terminal state?
;TODO: And just look at the last page of a pipeline's jobs (where any new jobs will go)
;TODO: since this is getting rate limited.
(defn continuously-poll-gitlab []
  (when-let [poll-id @(rf/subscribe [:next-poll-id])]
    (js/clearTimeout poll-id))
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
