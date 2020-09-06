(ns huge-feedback.config
  (:require
    [clojure.tools.reader.edn :as edn]
    [huge-feedback.apis.gitlab :as gitlab]
    [re-frame.core :as rf]
    [reagent.core :as r]
    [clojure.spec.alpha :as s]
    [huge-feedback.gitlab-polling]))

(defn handle-test-response [[ok? resp] config]
  (if ok?
    (do (rf/dispatch-sync [:set-config config])
        (huge-feedback.gitlab-polling/continuously-poll-gitlab))
    (rf/dispatch [:invalid-config (str "Got error when attempting test request to gitlab: " \newline resp)])))

(s/def ::use-cors-proxy? boolean?)
(s/def ::num-pipelines-to-show int?)

(s/def ::config (s/keys :req [::gitlab/config
                              ::use-cors-proxy?
                              ::num-pipelines-to-show]))

(defn validate-then-set-config [config]
  (cond
    (nil? config) (rf/dispatch [:invalid-config "Default config not available"])
    (s/valid? ::config config) (do
                                 (rf/dispatch [:validating-config config])
                                 (rf/dispatch [:ajax-request (gitlab/test-request config #(handle-test-response %1 config))]))
    :default (rf/dispatch [:invalid-config (s/explain-str ::config config)])))

(defn text-editor [initial-value on-save & [validate]]
  (let [state (r/atom {:input initial-value :valid? true})]
    (fn []
      [:form
       [:textarea {:class         (if (:valid? @state) "" "invalid")
                   :on-change     #(->> %1 (.-target) (.-value) (swap! state assoc :input))
                   :default-value initial-value}]
       [:input {:type     "submit"
                :on-click #(let [{:keys [input]} @state]
                             (.preventDefault %1)
                             (if (or (nil? validate) (validate input))
                               (on-save input)
                               (swap! state assoc :valid? false)))}]])))

(rf/reg-sub :config-state
  (fn [{:keys [::config-state ::config-message]}]
    [config-state config-message]))

(defn status-icon []
  (let [[state] @(rf/subscribe [:config-state])
        icon (case state
               ::requesting "⚙️"
               ::validating "⚙️"
               ::invalid "❌"
               ::valid "✔️")]
    [:span.config-status-icon (str \space icon)]))

(defn status []
  (fn []
    (let [[state message] @(rf/subscribe [:config-state])]
      [:div.config-status
       (case state
         ::requesting [:div [:p "Getting default config"]]
         ::invalid [:div.warning
                    [:p "Configuration is invalid: "]
                    [:p (str message)]]
         ::validating [:div [:p "Validating configuration..."]]
         ::valid [:div [:p "Config ok"]])])))

(defn panel []
  [:div
   [status]
   [text-editor (str @(rf/subscribe [:config]))
    #(rf/dispatch [:config (edn/read-string %1)])
    #(try (let [config (edn/read-string %1)]
            (validate-then-set-config config))
          (catch js/Error e
            (rf/dispatch [:invalid-config (.-message e)])
            false))]])

(rf/reg-event-db :validating-config
  (fn [db [_ config]] (assoc db ::config-state ::validating)))

(rf/reg-event-db :set-config
  (fn [{:keys [::config-state] :as db} [_ config]]
    (-> db
        (assoc ::config config)
        (assoc ::config-state ::valid)
        (dissoc ::config-message))))

(rf/reg-event-db :invalid-config
  (fn [db [_ message]]
    (-> db
        (assoc ::config-state ::invalid)
        (assoc ::config-message message))))

(rf/reg-event-db :config
  (fn [db [_ config]]
    (assoc-in db [::config ::gitlab/config] config)))

(rf/reg-sub :config
  (fn [db] (get-in db [::config])))

(rf/reg-sub :gitlab-config
  (fn [db] (get-in db [::config ::gitlab/config])))